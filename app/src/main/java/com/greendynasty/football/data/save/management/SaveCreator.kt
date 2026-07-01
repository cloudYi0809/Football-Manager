package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseConfig
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.cache.CacheRebuilder
import com.greendynasty.football.data.migration.SchemaVersion
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveManifestEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.data.save.entity.SaveWorldStateEntity
import com.greendynasty.football.data.save.management.model.SaveCreateResult
import com.greendynasty.football.data.save.management.model.SaveMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 存档创建器
 *
 * 负责新存档的完整创建流程（V0.2 §三 + T03 方案）：
 * 1. 生成 saveId (UUID)
 * 2. 创建 save.db 文件
 * 3. 初始化 save_manifest + save_world_state
 * 4. 初始化俱乐部状态（从 history.db 复制基础数据）
 * 5. 初始化球员状态（从 history.db 复制当前阵容）
 * 6. 初始化 cache.db（重建缓存索引）
 * 7. 创建首个 checkpoint（MIGRATION 类型，作为初始存档基线）
 * 8. 写入存档元信息 JSON（save_{saveId}.meta.json）
 *
 * @param context 应用上下文
 * @param databaseManager 三库管理入口
 */
class SaveCreator(
    private val context: Context,
    private val databaseManager: DatabaseManager
) {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 创建新存档
     *
     * @param request 创建存档请求
     * @return 创建结果（Success 携带 saveId / Failure 携带错误信息）
     */
    suspend fun create(request: CreateSaveRequest): SaveCreateResult = withContext(Dispatchers.IO) {
        try {
            val saveId = UUID.randomUUID().toString()
            val now = dateTimeFormatter.format(LocalDateTime.now())
            val startDate = DatabaseConfig.DEFAULT_START_DATE
            val seasonId = DatabaseConfig.DEFAULT_START_SEASON_ID

            // 1. 打开（创建）存档数据库
            databaseManager.openSave(saveId)
            val saveDb = databaseManager.getSaveDatabase()

            // 2. 写入 save_manifest（存档元数据）
            saveDb.saveManifestDao().insert(
                SaveManifestEntity(
                    saveId = saveId,
                    gameVersion = GAME_VERSION,
                    schemaVersion = SchemaVersion.CURRENT,
                    dataPackId = null,
                    dataPackVersion = null,
                    currentDate = startDate,
                    createdAt = now,
                    lastPlayedAt = now,
                    lastCheckpointId = null
                )
            )

            // 3. 写入 save_world_state（世界状态）
            val configJson = json.encodeToString(ModeConfig.serializer(), request.modeConfig)
            saveDb.saveWorldStateDao().insert(
                SaveWorldStateEntity(
                    saveName = request.saveName,
                    currentDate = startDate,
                    currentSeasonId = seasonId,
                    managerClubId = request.managerClubId,
                    mode = DatabaseConfig.DEFAULT_MODE,
                    scenarioId = request.scenarioId,
                    configJson = configJson,
                    createdAt = now,
                    updatedAt = now
                )
            )
            // 读取自增主键 saveId（Int，存档内隔离用）
            val worldState = saveDb.saveWorldStateDao().get()
                ?: error("save_world_state 写入后读取失败")
            val internalSaveId = worldState.saveId

            // 4. 从 history.db 复制初始俱乐部/球员状态
            copyInitialStatesFromHistory(saveDb, internalSaveId, request, seasonId)

            // 5. 初始化 cache.db（重建缓存）
            try {
                CacheRebuilder(databaseManager).rebuildAll()
            } catch (e: Exception) {
                // cache 重建失败不影响存档创建（cache 可后续重建）
                Log.w("SaveCreator", "cache 重建失败（不影响存档创建）：${e.message}")
            }

            // 6. 创建首个 checkpoint（MIGRATION 类型，作为初始存档基线）
            try {
                CheckpointManager(context, databaseManager).createCheckpoint(
                    saveId, CheckpointType.MIGRATION
                )
            } catch (e: Exception) {
                Log.w("SaveCreator", "初始 checkpoint 创建失败（不影响存档创建）：${e.message}")
            }

            // 7. 写入存档元信息 JSON（双写，便于列表快速读取）
            val meta = SaveMeta(
                saveId = saveId,
                saveName = request.saveName,
                gameVersion = GAME_VERSION,
                createdAt = now,
                lastSavedAt = now,
                gameDate = startDate,
                managerClubId = request.managerClubId,
                scenarioId = request.scenarioId,
                schemaVersion = SchemaVersion.CURRENT,
                fileSizeBytes = getSaveDbSize(saveId),
                isLoaded = true
            )
            writeMetaJson(saveId, meta)

            Log.d("SaveCreator", "存档创建成功：saveId=$saveId, name=${request.saveName}")
            SaveCreateResult.Success(saveId)
        } catch (e: Exception) {
            Log.e("SaveCreator", "存档创建失败：${e.message}", e)
            // 清理半成品存档
            try {
                databaseManager.getSaveDatabaseOrNull()?.let { databaseManager.closeSave() }
            } catch (_: Exception) {
            }
            SaveCreateResult.Failure("创建存档失败：${e.message}", e)
        }
    }

    /**
     * 从 history.db 复制初始球员/俱乐部状态到 save.db
     *
     * - 俱乐部状态：仅复制加载范围内的活跃俱乐部
     * - 球员状态：复制活跃俱乐部在开档赛季的阵容成员
     *
     * @param saveDb 存档数据库
     * @param internalSaveId 存档内隔离 ID（save_world_state 自增主键）
     * @param request 创建请求
     * @param seasonId 开档赛季 ID
     */
    private suspend fun copyInitialStatesFromHistory(
        saveDb: SaveDatabase,
        internalSaveId: Int,
        request: CreateSaveRequest,
        seasonId: Int
    ) {
        val historyDb = databaseManager.getHistoryDatabase()
        val activeClubIds = if (request.loadRange.activeClubs.isNotEmpty()) {
            request.loadRange.activeClubs
        } else {
            listOf(request.managerClubId)
        }

        // 俱乐部状态
        activeClubIds.forEach { clubId ->
            val club = historyDb.clubDao().getClub(clubId) ?: return@forEach
            saveDb.saveClubStateDao().insert(
                SaveClubStateEntity(
                    saveId = internalSaveId,
                    clubId = clubId,
                    balance = club.financeLevel * 1_000_000,
                    transferBudget = club.financeLevel * 500_000,
                    wageBudget = club.financeLevel * 200_000,
                    reputation = club.reputation,
                    boardSatisfaction = 60,
                    fanSatisfaction = 60,
                    dressingRoomMorale = 60
                )
            )
        }

        // 球员状态：按俱乐部赛季阵容复制
        val playerStates = mutableListOf<SavePlayerStateEntity>()
        activeClubIds.forEach { clubId ->
            val memberships = historyDb.squadMembershipDao()
                .getSquadByClub(seasonId, clubId)
                .first()
            memberships.forEach { membership ->
                val attributes = historyDb.playerDao()
                    .getAttributes(membership.playerId, seasonId)
                val ca = attributes?.ca ?: 50
                val pa = attributes?.pa ?: 50
                playerStates.add(
                    SavePlayerStateEntity(
                        saveId = internalSaveId,
                        playerId = membership.playerId,
                        currentClubId = membership.clubId,
                        loanClubId = if (membership.isLoan == 1) membership.loanFromClubId else null,
                        currentCa = ca,
                        currentPa = pa,
                        condition = 100,
                        morale = 60,
                        injuryStatus = "healthy",
                        injuryUntil = null,
                        contractUntil = membership.contractUntil,
                        wage = membership.wage,
                        marketValue = membership.marketValue,
                        careerStatus = "active",
                        squadRole = membership.squadRole
                    )
                )
            }
        }
        // 批量写入球员状态
        if (playerStates.isNotEmpty()) {
            // 分批写入，避免单次事务过大
            playerStates.chunked(BATCH_SIZE).forEach { batch ->
                saveDb.savePlayerStateDao().insertAll(batch)
            }
        }
        Log.d("SaveCreator", "初始状态复制完成：${activeClubIds.size} 俱乐部, ${playerStates.size} 球员")
    }

    /** 获取存档数据库文件大小（字节） */
    private fun getSaveDbSize(saveId: String): Long {
        val dbFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")
        return dbFile.length()
    }

    /** 写入存档元信息 JSON：filesDir/saves/save_{saveId}.meta.json */
    private fun writeMetaJson(saveId: String, meta: SaveMeta) {
        val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
        metaFile.writeText(json.encodeToString(SaveMeta.serializer(), meta))
    }

    companion object {
        /** 当前游戏版本号（与 build.gradle.kts versionName 保持一致） */
        private const val GAME_VERSION = "0.1.0"

        /** 球员状态批量写入批次大小 */
        private const val BATCH_SIZE = 500
    }
}
