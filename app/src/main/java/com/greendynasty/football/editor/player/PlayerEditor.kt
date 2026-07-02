package com.greendynasty.football.editor.player

import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.editor.model.EditablePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 球员编辑器（基础信息 + 属性 + CA/PA）。
 *
 * 职责：
 * - [loadEditable]：从 history.db 读取球员原始数据 + 最新赛季属性，构建草稿态
 * - [createNew]：创建新球员草稿（ID 由调用方传入，编辑器预留 100000+ 段）
 * - [updateBasicInfo]：修改基础信息字段（姓名/国籍/位置/生日/身高/体重/惯用脚/退役年龄）
 * - [updateAttribute]：修改属性字段（CA/PA + 30+ 技术身体精神门将属性）
 * - [markDeleted]：标记草稿为待删除
 *
 * 编辑器不直接写库，只维护草稿态；实际写入由
 * [com.greendynasty.football.editor.repository.EditorRepository] 统一执行。
 *
 * @param historyDb 历史库（只读，用于加载原始数据）
 */
class PlayerEditor(private val historyDb: HistoryDatabase) {

    /**
     * 加载球员草稿态。
     * @param playerId 球员 ID
     * @throws IllegalArgumentException 球员不存在
     */
    suspend fun loadEditable(playerId: Int): EditablePlayer = withContext(Dispatchers.IO) {
        val player = historyDb.playerDao().getPlayer(playerId)
            ?: throw IllegalArgumentException("球员不存在：$playerId")
        val latest = historyDb.playerDao().getLatestAttributes(playerId)
        val attrs = if (latest != null) mapOf(latest.seasonId to latest) else emptyMap()
        EditablePlayer(original = player, draft = player.copy(), attributesDraft = attrs)
    }

    /**
     * 创建新球员草稿。
     * @param newPlayerId 新球员 ID（建议取当前最大 ID +1，预留 100001+ 段给编辑器新增）
     */
    fun createNew(newPlayerId: Int): EditablePlayer {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val newPlayer = PlayerEntity(
            playerId = newPlayerId,
            sourceId = null,
            realName = "",
            displayName = null,
            birthDate = null,
            nationality = "",
            secondNationality = null,
            height = null,
            weight = null,
            preferredFoot = "Right",
            primaryPosition = "CM",
            secondaryPositions = null,
            personality = null,
            retireAgeBase = 35,
            portraitPath = null,
            createdAt = now,
            updatedAt = now
        )
        return EditablePlayer(original = null, draft = newPlayer, attributesDraft = emptyMap())
    }

    /**
     * 修改基础信息字段。
     * @param field 字段名（real_name / nationality / primary_position / birth_date / height / weight / preferred_foot / retire_age_base）
     * @param value 新值（类型按字段而定，可为 null）
     */
    fun updateBasicInfo(editable: EditablePlayer, field: String, value: Any?): EditablePlayer {
        val d = editable.draft
        val updated = when (field) {
            "real_name" -> d.copy(realName = value as String)
            "nationality" -> d.copy(nationality = value as String?)
            "primary_position" -> d.copy(primaryPosition = value as String?)
            "birth_date" -> d.copy(birthDate = value as String?)
            "height" -> d.copy(height = value as Int?)
            "weight" -> d.copy(weight = value as Int?)
            "preferred_foot" -> d.copy(preferredFoot = value as String?)
            "retire_age_base" -> d.copy(retireAgeBase = value as Int)
            else -> throw IllegalArgumentException("不支持的字段：$field")
        }
        return editable.copy(draft = updated)
    }

    /**
     * 修改属性字段（CA/PA + 30+ 属性）。
     *
     * 若当前赛季无属性草稿，自动创建默认属性（全 50，门将属性 0）。
     *
     * @param seasonId 赛季 ID
     * @param field 属性字段名（ca / pa / shooting / ... / gk_one_on_one）
     * @param value 属性值
     */
    fun updateAttribute(editable: EditablePlayer, seasonId: Int, field: String, value: Int): EditablePlayer {
        val attrs = editable.attributesDraft[seasonId] ?: createDefaultAttributes(editable.draft.playerId, seasonId)
        val updated = when (field) {
            "ca" -> attrs.copy(ca = value)
            "pa" -> attrs.copy(pa = value)
            "shooting" -> attrs.copy(shooting = value)
            "finishing" -> attrs.copy(finishing = value)
            "long_shots" -> attrs.copy(longShots = value)
            "passing" -> attrs.copy(passing = value)
            "crossing" -> attrs.copy(crossing = value)
            "dribbling" -> attrs.copy(dribbling = value)
            "technique" -> attrs.copy(technique = value)
            "first_touch" -> attrs.copy(firstTouch = value)
            "pace" -> attrs.copy(pace = value)
            "acceleration" -> attrs.copy(acceleration = value)
            "strength" -> attrs.copy(strength = value)
            "stamina" -> attrs.copy(stamina = value)
            "balance" -> attrs.copy(balance = value)
            "agility" -> attrs.copy(agility = value)
            "jumping" -> attrs.copy(jumping = value)
            "defending" -> attrs.copy(defending = value)
            "tackling" -> attrs.copy(tackling = value)
            "marking" -> attrs.copy(marking = value)
            "positioning" -> attrs.copy(positioning = value)
            "heading" -> attrs.copy(heading = value)
            "vision" -> attrs.copy(vision = value)
            "decision" -> attrs.copy(decision = value)
            "composure" -> attrs.copy(composure = value)
            "leadership" -> attrs.copy(leadership = value)
            "work_rate" -> attrs.copy(workRate = value)
            "teamwork" -> attrs.copy(teamwork = value)
            "gk_diving" -> attrs.copy(gkDiving = value)
            "gk_reflexes" -> attrs.copy(gkReflexes = value)
            "gk_handling" -> attrs.copy(gkHandling = value)
            "gk_positioning" -> attrs.copy(gkPositioning = value)
            "gk_one_on_one" -> attrs.copy(gkOneOnOne = value)
            else -> throw IllegalArgumentException("不支持的属性字段：$field")
        }
        val newMap = editable.attributesDraft + (seasonId to updated)
        return editable.copy(attributesDraft = newMap)
    }

    /** 标记草稿为待删除。 */
    fun markDeleted(editable: EditablePlayer): EditablePlayer = editable.copy(isDeleted = true)

    /** 创建默认属性（全 50，门将属性 0）。 */
    private fun createDefaultAttributes(playerId: Int, seasonId: Int) = PlayerAttributesEntity(
        playerId = playerId,
        seasonId = seasonId,
        ca = 50,
        pa = 50
    )
}
