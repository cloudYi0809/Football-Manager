package com.greendynasty.football.editor.repository

import android.content.ContentValues
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import com.greendynasty.football.editor.model.EditMode
import com.greendynasty.football.editor.model.EditOperation
import com.greendynasty.football.editor.model.EditOperationType
import com.greendynasty.football.editor.model.EditReport
import com.greendynasty.football.editor.model.EditTargetTable
import com.greendynasty.football.editor.model.EditableClub
import com.greendynasty.football.editor.model.EditableEntity
import com.greendynasty.football.editor.model.EditableMatch
import com.greendynasty.football.editor.model.EditablePlayer
import com.greendynasty.football.editor.validator.BalanceChecker
import com.greendynasty.football.editor.validator.EditValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 编辑器统一保存入口（对应实现方案 §四.1 DataEditorManager）。
 *
 * 职责：
 * 1. [save]：保存草稿 → 校验 → 平衡性检查 → 写入 history.db（事务） → 记录变更历史
 * 2. [undo]：撤销最近一次操作（执行逆操作 → 标记已撤销）
 * 3. [redo]：重做最近一次撤销（执行原操作 → 标记已重做）
 * 4. [getHistory]：查询变更历史
 *
 * 关键设计：
 * - **事务保证**：每次保存通过 [HistoryDatabase.withTransaction] 包裹，
 *   任一步骤失败自动回滚，history.db 保持一致性。
 * - **只读库写入**：history.db 默认 `PRAGMA query_only = ON`，
 *   写入前后由 [HistoryDbWriter.openForWrite] / [closeAndRestoreReadOnly] 管理。
 * - **撤销/重做**：通过 [ChangeLogService] 维护内存栈，逆操作同样走事务写入。
 *
 * @param historyDb 历史库
 * @param validator 编辑校验器
 * @param balanceChecker 平衡性检查器
 * @param changeLog 变更记录服务
 * @param editMode 编辑模式（V1 默认 OFFLINE）
 */
class EditorRepository(
    private val historyDb: HistoryDatabase,
    private val validator: EditValidator = EditValidator(historyDb),
    private val balanceChecker: BalanceChecker = BalanceChecker(),
    private val changeLog: ChangeLogService = ChangeLogService(),
    private val editMode: EditMode = EditMode.OFFLINE
) {

    /** history.db 写入器（复用 T01 基础设施） */
    private val writer: HistoryDbWriter = HistoryDbWriter(historyDb)

    /**
     * 保存草稿到 history.db（事务）。
     *
     * 流程：
     * 1. 草稿未修改 → 直接返回成功（0 计数）
     * 2. 数据校验 → 失败则返回错误报告
     * 3. 平衡性检查 → 警告附加到报告（不阻塞）
     * 4. openForWrite → withTransaction 执行写入 → closeAndRestoreReadOnly
     * 5. 记录 EditOperation 到 ChangeLogService
     *
     * @param editable 草稿态 Entity
     * @param targetTable 目标表
     * @return 编辑结果报告
     */
    suspend fun save(editable: EditableEntity, targetTable: EditTargetTable): EditReport =
        withContext(Dispatchers.IO) {
            val report = EditReport()

            // 1. 未修改直接返回
            if (!editable.isModified) {
                return@withContext report.ok()
            }

            // 2. 数据校验
            val validation = validator.validate(editable, targetTable)
            if (!validation.isValid) {
                validation.errors.forEach { report.fail("${it.field}: ${it.message}") }
                return@withContext report
            }

            // 3. 平衡性检查（仅警告）
            val balanceWarnings = balanceChecker.check(editable, targetTable)
            balanceWarnings.forEach { report.warnings.add("[${it.severity}] ${it.field}: ${it.message}") }

            // 4. 事务写入
            try {
                writer.openForWrite()
                historyDb.withTransaction {
                    when (targetTable) {
                        EditTargetTable.PLAYER -> savePlayer(editable as EditablePlayer, report)
                        EditTargetTable.CLUB -> saveClub(editable as EditableClub, report)
                        EditTargetTable.MATCH -> saveMatch(editable as EditableMatch, report)
                        EditTargetTable.PLAYER_ATTRIBUTES -> { /* 由 PLAYER 路径统一处理 */ }
                    }
                }
                writer.closeAndRestoreReadOnly()
            } catch (e: Exception) {
                // 异常时尝试恢复只读模式，避免遗留写入模式
                runCatching { writer.closeAndRestoreReadOnly() }
                report.fail("写入失败：${e.message}")
                return@withContext report
            }

            // 5. 记录变更历史
            val operation = buildOperation(editable, targetTable)
            changeLog.record(operation)

            report.ok()
        }

    /**
     * 撤销最近一次操作。
     *
     * 从 [ChangeLogService] 弹出操作，执行其逆操作（事务），失败时推回栈。
     *
     * @return 编辑结果报告（无可撤销时 success=true 且计数为 0）
     */
    suspend fun undo(): EditReport = withContext(Dispatchers.IO) {
        val report = EditReport()
        val op = changeLog.popForUndo() ?: return@withContext report.ok()

        try {
            writer.openForWrite()
            historyDb.withTransaction {
                executeReverse(op, report)
            }
            writer.closeAndRestoreReadOnly()
            changeLog.markUndone(op)
            report.ok()
        } catch (e: Exception) {
            runCatching { writer.closeAndRestoreReadOnly() }
            changeLog.pushBack(op)
            report.fail("撤销失败：${e.message}")
        }
    }

    /**
     * 重做最近一次撤销。
     *
     * @return 编辑结果报告（无可重做时 success=true 且计数为 0）
     */
    suspend fun redo(): EditReport = withContext(Dispatchers.IO) {
        val report = EditReport()
        val op = changeLog.popForRedo() ?: return@withContext report.ok()

        try {
            writer.openForWrite()
            historyDb.withTransaction {
                executeForward(op, report)
            }
            writer.closeAndRestoreReadOnly()
            changeLog.markRedone(op)
            report.ok()
        } catch (e: Exception) {
            runCatching { writer.closeAndRestoreReadOnly() }
            report.fail("重做失败：${e.message}")
        }
    }

    /** 查询变更历史（最新在前）。 */
    fun getHistory(): List<EditOperation> = changeLog.getHistory()

    /** 可撤销步数。 */
    fun getUndoableCount(): Int = changeLog.getUndoableCount()

    /** 可重做步数。 */
    fun getRedoableCount(): Int = changeLog.getRedoableCount()

    // ==================== 内部：写入逻辑 ====================

    /** 球员保存（INSERT/UPDATE/DELETE）。 */
    private suspend fun savePlayer(editable: EditablePlayer, report: EditReport) {
        val db = historyDb.openHelper.writableDatabase
        if (editable.isDeleted) {
            // 删除球员 + 关联属性
            db.delete("player", "player_id = ?", arrayOf(editable.originalId.toString()))
            db.delete("player_attributes", "player_id = ?", arrayOf(editable.originalId.toString()))
            report.deletedCount++
            return
        }
        // 新增或更新（CONFLICT_REPLACE）
        writer.insertPlayers(listOf(editable.draft))
        if (editable.isNew) report.insertedCount++ else report.updatedCount++
        // 属性保存
        if (editable.attributesDraft.isNotEmpty()) {
            writer.insertPlayerAttributes(editable.attributesDraft.values.toList())
        }
    }

    /** 俱乐部保存。 */
    private suspend fun saveClub(editable: EditableClub, report: EditReport) {
        val db = historyDb.openHelper.writableDatabase
        if (editable.isDeleted) {
            db.delete("club", "club_id = ?", arrayOf(editable.originalId.toString()))
            report.deletedCount++
            return
        }
        writer.insertClubs(listOf(editable.draft))
        if (editable.isNew) report.insertedCount++ else report.updatedCount++
    }

    /** 比赛保存。 */
    private suspend fun saveMatch(editable: EditableMatch, report: EditReport) {
        val db = historyDb.openHelper.writableDatabase
        if (editable.isDeleted) {
            db.delete("match", "match_id = ?", arrayOf(editable.originalId.toString()))
            report.deletedCount++
            return
        }
        writer.insertMatches(listOf(editable.draft))
        if (editable.isNew) report.insertedCount++ else report.updatedCount++
    }

    // ==================== 内部：撤销/重做执行 ====================

    /** 执行逆操作（INSERT→DELETE / DELETE→INSERT 旧值 / UPDATE→UPDATE 旧值）。 */
    private fun executeReverse(op: EditOperation, report: EditReport) {
        val db = historyDb.openHelper.writableDatabase
        when (op.operationType) {
            EditOperationType.INSERT -> {
                // 撤销新增 = 删除
                db.delete(op.targetTable.tableName, "${idColumn(op.targetTable)} = ?", arrayOf(op.targetId))
                report.deletedCount++
            }
            EditOperationType.DELETE -> {
                // 撤销删除 = 重新插入旧值
                @Suppress("UNCHECKED_CAST")
                insertEntity(op.targetTable, op.oldValue, db)
                report.insertedCount++
            }
            EditOperationType.UPDATE -> {
                // 撤销更新 = 写回旧值
                @Suppress("UNCHECKED_CAST")
                insertEntity(op.targetTable, op.oldValue, db)
                report.updatedCount++
            }
        }
    }

    /** 执行原操作（重做）。 */
    private fun executeForward(op: EditOperation, report: EditReport) {
        val db = historyDb.openHelper.writableDatabase
        when (op.operationType) {
            EditOperationType.INSERT -> {
                @Suppress("UNCHECKED_CAST")
                insertEntity(op.targetTable, op.newValue, db)
                report.insertedCount++
            }
            EditOperationType.DELETE -> {
                db.delete(op.targetTable.tableName, "${idColumn(op.targetTable)} = ?", arrayOf(op.targetId))
                report.deletedCount++
            }
            EditOperationType.UPDATE -> {
                @Suppress("UNCHECKED_CAST")
                insertEntity(op.targetTable, op.newValue, db)
                report.updatedCount++
            }
        }
    }

    /** 按 targetTable 调用对应的 ContentValues 插入（CONFLICT_REPLACE）。 */
    private fun insertEntity(table: EditTargetTable, value: Any?, db: SupportSQLiteDatabase) {
        if (value == null) return
        val cv = when (table) {
            EditTargetTable.PLAYER -> playerToContentValues(value as PlayerEntity)
            EditTargetTable.CLUB -> clubToContentValues(value as ClubEntity)
            EditTargetTable.MATCH -> matchToContentValues(value as MatchEntity)
            EditTargetTable.PLAYER_ATTRIBUTES -> playerAttrToContentValues(value as PlayerAttributesEntity)
        }
        db.insert(table.tableName, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    /** 构造 EditOperation（根据草稿状态判断操作类型）。 */
    private fun buildOperation(editable: EditableEntity, targetTable: EditTargetTable): EditOperation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val targetId = editable.originalId.toString()
        return when {
            editable.isDeleted -> EditOperation(
                operationType = EditOperationType.DELETE,
                targetTable = targetTable,
                targetId = targetId,
                oldValue = extractOriginal(editable),
                newValue = null,
                timestamp = timestamp,
                editMode = editMode
            )
            editable.isNew -> EditOperation(
                operationType = EditOperationType.INSERT,
                targetTable = targetTable,
                targetId = targetId,
                oldValue = null,
                newValue = extractDraft(editable),
                timestamp = timestamp,
                editMode = editMode
            )
            else -> EditOperation(
                operationType = EditOperationType.UPDATE,
                targetTable = targetTable,
                targetId = targetId,
                oldValue = extractOriginal(editable),
                newValue = extractDraft(editable),
                timestamp = timestamp,
                editMode = editMode
            )
        }
    }

    /** 提取草稿值（按类型）。 */
    private fun extractDraft(editable: EditableEntity): Any? = when (editable) {
        is EditablePlayer -> editable.draft
        is EditableClub -> editable.draft
        is EditableMatch -> editable.draft
        else -> null
    }

    /** 提取原始值（按类型）。 */
    private fun extractOriginal(editable: EditableEntity): Any? = when (editable) {
        is EditablePlayer -> editable.original
        is EditableClub -> editable.original
        is EditableMatch -> editable.original
        else -> null
    }

    /** 目标表对应主键列名。 */
    private fun idColumn(table: EditTargetTable): String = when (table) {
        EditTargetTable.PLAYER -> "player_id"
        EditTargetTable.CLUB -> "club_id"
        EditTargetTable.MATCH -> "match_id"
        EditTargetTable.PLAYER_ATTRIBUTES -> "id"
    }

    // ==================== ContentValues 转换（与 HistoryDbWriter 对齐） ====================

    private fun playerToContentValues(p: PlayerEntity) = ContentValues().apply {
        put("player_id", p.playerId)
        put("source_id", p.sourceId)
        put("real_name", p.realName)
        put("display_name", p.displayName)
        put("birth_date", p.birthDate)
        put("nationality", p.nationality)
        put("second_nationality", p.secondNationality)
        put("height", p.height)
        put("weight", p.weight)
        put("preferred_foot", p.preferredFoot)
        put("primary_position", p.primaryPosition)
        put("secondary_positions", p.secondaryPositions)
        put("personality", p.personality)
        put("retire_age_base", p.retireAgeBase)
        put("portrait_path", p.portraitPath)
        put("created_at", p.createdAt)
        put("updated_at", p.updatedAt)
    }

    private fun clubToContentValues(c: ClubEntity) = ContentValues().apply {
        put("club_id", c.clubId)
        put("source_id", c.sourceId)
        put("club_name", c.clubName)
        put("country", c.country)
        put("city", c.city)
        put("founded_year", c.foundedYear)
        put("reputation", c.reputation)
        put("stadium_name", c.stadiumName)
        put("stadium_capacity", c.stadiumCapacity)
        put("training_level", c.trainingLevel)
        put("youth_level", c.youthLevel)
        put("finance_level", c.financeLevel)
        put("logo_path", c.logoPath)
        put("kit_path", c.kitPath)
        put("created_at", c.createdAt)
        put("updated_at", c.updatedAt)
    }

    private fun matchToContentValues(m: MatchEntity) = ContentValues().apply {
        if (m.matchId != 0) put("match_id", m.matchId)
        put("season_id", m.seasonId)
        put("competition_id", m.competitionId)
        put("match_date", m.matchDate)
        put("home_club_id", m.homeClubId)
        put("away_club_id", m.awayClubId)
        put("home_score_real", m.homeScoreReal)
        put("away_score_real", m.awayScoreReal)
        put("home_score_sim", m.homeScoreSim)
        put("away_score_sim", m.awayScoreSim)
        put("status", m.status)
        put("is_historical", m.isHistorical)
        put("match_stats_json", m.matchStatsJson)
    }

    private fun playerAttrToContentValues(a: PlayerAttributesEntity) = ContentValues().apply {
        put("player_id", a.playerId)
        put("season_id", a.seasonId)
        put("ca", a.ca)
        put("pa", a.pa)
        put("shooting", a.shooting)
        put("finishing", a.finishing)
        put("long_shots", a.longShots)
        put("passing", a.passing)
        put("crossing", a.crossing)
        put("dribbling", a.dribbling)
        put("technique", a.technique)
        put("first_touch", a.firstTouch)
        put("pace", a.pace)
        put("acceleration", a.acceleration)
        put("strength", a.strength)
        put("stamina", a.stamina)
        put("balance", a.balance)
        put("agility", a.agility)
        put("jumping", a.jumping)
        put("defending", a.defending)
        put("tackling", a.tackling)
        put("marking", a.marking)
        put("positioning", a.positioning)
        put("heading", a.heading)
        put("vision", a.vision)
        put("decision", a.decision)
        put("composure", a.composure)
        put("leadership", a.leadership)
        put("work_rate", a.workRate)
        put("teamwork", a.teamwork)
        put("injury_proneness", a.injuryProneness)
        put("big_match", a.bigMatch)
        put("consistency", a.consistency)
        put("professionalism", a.professionalism)
        put("ambition", a.ambition)
        put("loyalty", a.loyalty)
        put("gk_diving", a.gkDiving)
        put("gk_reflexes", a.gkReflexes)
        put("gk_handling", a.gkHandling)
        put("gk_positioning", a.gkPositioning)
        put("gk_one_on_one", a.gkOneOnOne)
    }
}
