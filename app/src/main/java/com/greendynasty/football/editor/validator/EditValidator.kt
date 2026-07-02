package com.greendynasty.football.editor.validator

import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.editor.model.EditableClub
import com.greendynasty.football.editor.model.EditableEntity
import com.greendynasty.football.editor.model.EditableMatch
import com.greendynasty.football.editor.model.EditablePlayer
import com.greendynasty.football.editor.model.EditTargetTable
import java.time.LocalDate
import java.time.Period

/**
 * 编辑校验错误。
 *
 * @property field 字段名
 * @property message 错误描述
 * @property code 错误码（REQUIRED_MISSING / RANGE_OVER / ENUM_INVALID / FK_NOT_FOUND / UNIQUE_DUPLICATE / DATE_FORMAT_INVALID / FK_INVALID）
 */
data class ValidationError(val field: String, val message: String, val code: String)

/** 编辑校验警告（不阻塞保存）。 */
data class ValidationWarning(val field: String, val message: String)

/**
 * 编辑校验结果。
 *
 * 复用 T01 [com.greendynasty.football.data.importer.validator.DataValidator] 的校验语义
 * （必填 / 范围 / 外键 / 唯一 / 枚举 / 日期格式 6 类规则），但直接作用于
 * [com.greendynasty.football.editor.model.EditableEntity] 草稿对象而非 CsvRow。
 */
data class EditValidationResult(
    val errors: MutableList<ValidationError> = mutableListOf(),
    val warnings: MutableList<ValidationWarning> = mutableListOf()
) {
    /** 校验是否通过（无 ERROR）。 */
    val isValid: Boolean get() = errors.isEmpty()

    fun addError(field: String, message: String, code: String) = errors.add(ValidationError(field, message, code))
    fun addWarning(field: String, message: String) = warnings.add(ValidationWarning(field, message))

    fun summary(): String = "错误 ${errors.size} / 警告 ${warnings.size}"
}

/**
 * 编辑校验器（复用 T01 校验规则 + 编辑专用规则）。
 *
 * 校验顺序：必填 → 枚举 → 范围 → 外键 → 唯一。
 * 任一 ERROR 阻止保存；WARNING 不阻塞。
 *
 * @param historyDb 用于外键存在性检查与唯一性检查
 */
class EditValidator(private val historyDb: HistoryDatabase) {

    /** 合法位置枚举（V0.1 03_页面结构 §10） */
    private val validPositions = setOf("GK", "RB", "LB", "CB", "DM", "CM", "AM", "RW", "LW", "ST")

    /** 合法惯用脚枚举 */
    private val validFeet = setOf("Left", "Right", "Both")

    /**
     * 校验可编辑 Entity。
     * @param editable 草稿态
     * @param targetTable 目标表
     */
    suspend fun validate(editable: EditableEntity, targetTable: EditTargetTable): EditValidationResult {
        val result = EditValidationResult()
        when (targetTable) {
            EditTargetTable.PLAYER -> validatePlayer(editable as EditablePlayer, result)
            EditTargetTable.CLUB -> validateClub(editable as EditableClub, result)
            EditTargetTable.MATCH -> validateMatch(editable as EditableMatch, result)
            EditTargetTable.PLAYER_ATTRIBUTES -> { /* 属性随 PLAYER 一起校验 */ }
        }
        return result
    }

    // ==================== 球员校验 ====================

    private suspend fun validatePlayer(editable: EditablePlayer, result: EditValidationResult) {
        val p = editable.draft
        // 1. 必填
        if (p.realName.isBlank()) result.addError("real_name", "球员姓名不能为空", "REQUIRED_MISSING")
        if (p.nationality.isNullOrBlank()) result.addError("nationality", "国籍不能为空", "REQUIRED_MISSING")
        if (p.primaryPosition.isNullOrBlank()) result.addError("primary_position", "主要位置不能为空", "REQUIRED_MISSING")
        // 2. 枚举
        if (p.primaryPosition != null && p.primaryPosition !in validPositions)
            result.addError("primary_position", "位置枚举非法：${p.primaryPosition}", "ENUM_INVALID")
        if (p.preferredFoot != null && p.preferredFoot !in validFeet)
            result.addError("preferred_foot", "惯用脚枚举非法：${p.preferredFoot}", "ENUM_INVALID")
        // 3. 范围
        p.height?.let { if (it !in 150..220) result.addError("height", "身高范围 150-220：$it", "RANGE_OVER") }
        p.weight?.let { if (it !in 50..110) result.addError("weight", "体重范围 50-110：$it", "RANGE_OVER") }
        if (p.retireAgeBase !in 30..42) result.addError("retire_age_base", "退役年龄范围 30-42：${p.retireAgeBase}", "RANGE_OVER")
        p.birthDate?.let {
            val age = calcAge(it)
            if (age !in 14..50) result.addError("birth_date", "年龄范围 14-50：$age", "RANGE_OVER")
        }
        // 4. 属性范围 + CA/PA
        for ((_, attr) in editable.attributesDraft) {
            validateAttributes(attr, p.primaryPosition, result)
        }
        // 5. 唯一性（新增时检查 ID 不重复）
        if (editable.isNew) {
            if (historyDb.playerDao().getPlayer(p.playerId) != null)
                result.addError("player_id", "球员 ID 重复：${p.playerId}", "UNIQUE_DUPLICATE")
        }
    }

    private fun validateAttributes(
        attr: PlayerAttributesEntity,
        primaryPosition: String?,
        result: EditValidationResult
    ) {
        if (attr.ca !in 1..200) result.addError("ca", "CA 范围 1-200：${attr.ca}", "RANGE_OVER")
        if (attr.pa !in 1..200) result.addError("pa", "PA 范围 1-200：${attr.pa}", "RANGE_OVER")
        if (attr.pa < attr.ca) result.addWarning("pa", "PA(${attr.pa}) < CA(${attr.ca})，潜力低于当前能力")
        // 常规属性 1-100
        val normalAttrs = listOf(
            attr.shooting, attr.finishing, attr.longShots, attr.passing, attr.crossing,
            attr.dribbling, attr.technique, attr.firstTouch, attr.pace, attr.acceleration,
            attr.strength, attr.stamina, attr.balance, attr.agility, attr.jumping,
            attr.defending, attr.tackling, attr.marking, attr.positioning, attr.heading,
            attr.vision, attr.decision, attr.composure, attr.leadership, attr.workRate, attr.teamwork,
            attr.injuryProneness, attr.bigMatch, attr.consistency,
            attr.professionalism, attr.ambition, attr.loyalty
        )
        normalAttrs.forEach { v ->
            if (v !in 1..100) result.addError("attribute", "属性范围 1-100：$v", "RANGE_OVER")
        }
        // 非门将不应有门将属性
        if (primaryPosition != null && primaryPosition != "GK") {
            val gkSum = attr.gkDiving + attr.gkReflexes + attr.gkHandling + attr.gkPositioning + attr.gkOneOnOne
            if (gkSum > 50) result.addWarning("gk_attributes", "非门将位置有门将属性（合计 $gkSum）")
        }
    }

    // ==================== 俱乐部校验 ====================

    private suspend fun validateClub(editable: EditableClub, result: EditValidationResult) {
        val c = editable.draft
        if (c.clubName.isBlank()) result.addError("club_name", "俱乐部名称不能为空", "REQUIRED_MISSING")
        if (c.reputation !in 1..100) result.addError("reputation", "声望范围 1-100：${c.reputation}", "RANGE_OVER")
        if (c.trainingLevel !in 1..100) result.addError("training_level", "训练等级范围 1-100：${c.trainingLevel}", "RANGE_OVER")
        if (c.youthLevel !in 1..100) result.addError("youth_level", "青训等级范围 1-100：${c.youthLevel}", "RANGE_OVER")
        if (c.financeLevel !in 1..100) result.addError("finance_level", "财政等级范围 1-100：${c.financeLevel}", "RANGE_OVER")
        c.foundedYear?.let { if (it !in 1800..2025) result.addError("founded_year", "成立年份范围 1800-2025：$it", "RANGE_OVER") }
        if (editable.isNew && historyDb.clubDao().getClub(c.clubId) != null)
            result.addError("club_id", "俱乐部 ID 重复：${c.clubId}", "UNIQUE_DUPLICATE")
    }

    // ==================== 比赛校验 ====================

    private suspend fun validateMatch(editable: EditableMatch, result: EditValidationResult) {
        val m = editable.draft
        // 日期格式
        if (m.matchDate.isBlank()) result.addError("match_date", "比赛日期不能为空", "REQUIRED_MISSING")
        else runCatching { LocalDate.parse(m.matchDate) }.onFailure {
            result.addError("match_date", "比赛日期格式非法：${m.matchDate}", "DATE_FORMAT_INVALID")
        }
        // 主客队不能相同
        if (m.homeClubId == m.awayClubId) result.addError("home_club_id", "主客队不能相同", "FK_INVALID")
        // 外键
        if (historyDb.clubDao().getClub(m.homeClubId) == null)
            result.addError("home_club_id", "主队俱乐部不存在：${m.homeClubId}", "FK_NOT_FOUND")
        if (historyDb.clubDao().getClub(m.awayClubId) == null)
            result.addError("away_club_id", "客队俱乐部不存在：${m.awayClubId}", "FK_NOT_FOUND")
        // 比分范围
        m.homeScoreReal?.let { if (it !in 0..30) result.addError("home_score_real", "比分范围 0-30：$it", "RANGE_OVER") }
        m.awayScoreReal?.let { if (it !in 0..30) result.addError("away_score_real", "比分范围 0-30：$it", "RANGE_OVER") }
    }

    /** 计算年龄（基于当前日期） */
    private fun calcAge(birthDate: String): Int = try {
        val birth = LocalDate.parse(birthDate)
        Period.between(birth, LocalDate.now()).years
    } catch (_: Exception) {
        25
    }
}
