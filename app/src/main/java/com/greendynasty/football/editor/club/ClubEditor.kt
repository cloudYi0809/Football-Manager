package com.greendynasty.football.editor.club

import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.editor.model.EditableClub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 设施类型（训练 / 青训 / 医疗）。
 *
 * V1 简化：医疗等级复用 club.finance_level 字段（与实现方案 §四.3 一致）。
 */
enum class FacilityType { TRAINING, YOUTH, MEDICAL }

/**
 * 俱乐部编辑器（基础信息 + 声望 + 财政 + 设施）。
 *
 * 职责：
 * - [loadEditable]：读取俱乐部原始数据构建草稿态
 * - [createNew]：创建新俱乐部草稿
 * - [updateBasicInfo]：修改名称/国家/城市/成立年份/球场等基础信息
 * - [updateReputation]：修改声望
 * - [updateFinance]：修改财政等级
 * - [updateFacility]：修改训练/青训/医疗设施等级
 * - [markDeleted]：标记草稿为待删除
 *
 * @param historyDb 历史库
 */
class ClubEditor(private val historyDb: HistoryDatabase) {

    /**
     * 加载俱乐部草稿态。
     * @param clubId 俱乐部 ID
     * @throws IllegalArgumentException 俱乐部不存在
     */
    suspend fun loadEditable(clubId: Int): EditableClub = withContext(Dispatchers.IO) {
        val club = historyDb.clubDao().getClub(clubId)
            ?: throw IllegalArgumentException("俱乐部不存在：$clubId")
        EditableClub(original = club, draft = club.copy())
    }

    /**
     * 创建新俱乐部草稿。
     * @param newClubId 新俱乐部 ID
     */
    fun createNew(newClubId: Int): EditableClub {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val newClub = ClubEntity(
            clubId = newClubId,
            sourceId = null,
            clubName = "",
            country = null,
            city = null,
            foundedYear = null,
            reputation = 50,
            stadiumName = null,
            stadiumCapacity = null,
            trainingLevel = 50,
            youthLevel = 50,
            financeLevel = 50,
            logoPath = null,
            kitPath = null,
            createdAt = now,
            updatedAt = now
        )
        return EditableClub(original = null, draft = newClub)
    }

    /**
     * 修改基础信息字段。
     * @param field 字段名（club_name / country / city / founded_year / stadium_name / stadium_capacity）
     */
    fun updateBasicInfo(editable: EditableClub, field: String, value: Any?): EditableClub {
        val d = editable.draft
        val updated = when (field) {
            "club_name" -> d.copy(clubName = value as String)
            "country" -> d.copy(country = value as String?)
            "city" -> d.copy(city = value as String?)
            "founded_year" -> d.copy(foundedYear = value as Int?)
            "stadium_name" -> d.copy(stadiumName = value as String?)
            "stadium_capacity" -> d.copy(stadiumCapacity = value as Int?)
            else -> throw IllegalArgumentException("不支持的字段：$field")
        }
        return editable.copy(draft = updated)
    }

    /** 修改声望（1-100）。 */
    fun updateReputation(editable: EditableClub, reputation: Int): EditableClub =
        editable.copy(draft = editable.draft.copy(reputation = reputation))

    /** 修改财政等级（1-100，V1 简化映射到 club.finance_level）。 */
    fun updateFinance(editable: EditableClub, financeLevel: Int): EditableClub =
        editable.copy(draft = editable.draft.copy(financeLevel = financeLevel))

    /**
     * 修改设施等级。
     * @param facilityType 设施类型
     * @param level 等级（1-100）
     */
    fun updateFacility(editable: EditableClub, facilityType: FacilityType, level: Int): EditableClub {
        val updated = when (facilityType) {
            FacilityType.TRAINING -> editable.draft.copy(trainingLevel = level)
            FacilityType.YOUTH -> editable.draft.copy(youthLevel = level)
            FacilityType.MEDICAL -> editable.draft.copy(financeLevel = level) // V1 简化：医疗复用 finance_level
        }
        return editable.copy(draft = updated)
    }

    /** 标记草稿为待删除。 */
    fun markDeleted(editable: EditableClub): EditableClub = editable.copy(isDeleted = true)
}
