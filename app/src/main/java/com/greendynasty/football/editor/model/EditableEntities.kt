package com.greendynasty.football.editor.model

import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity

/**
 * 字段差异（草稿与原值对比结果）。
 */
data class FieldDiff(
    val fieldName: String,
    val oldValue: Any?,
    val newValue: Any?,
    val isModified: Boolean
)

/**
 * 可编辑 Entity 草稿态基接口。
 *
 * 编辑器不直接修改数据库，而是在内存中维护「草稿态」Entity，
 * 用户确认保存后才写入。草稿态附加 [isModified] / [isDeleted] / [isNew] 元信息。
 */
interface EditableEntity {
    val originalId: Any
    val isModified: Boolean
    val isDeleted: Boolean
    val isNew: Boolean
    fun diff(): List<FieldDiff>
}

/**
 * 球员草稿态（基础信息 + 属性 + CA/PA）。
 *
 * @property original 原始 Entity（null 表示新增）
 * @property draft 当前草稿值
 * @property attributesDraft 属性草稿，key 为 seasonId
 */
data class EditablePlayer(
    val original: PlayerEntity?,
    val draft: PlayerEntity,
    val attributesDraft: Map<Int, PlayerAttributesEntity> = emptyMap(),
    override val isDeleted: Boolean = false
) : EditableEntity {
    override val originalId: Int get() = draft.playerId
    override val isNew: Boolean get() = original == null
    override val isModified: Boolean
        get() = original == null || original != draft || attributesModified() || isDeleted

    private fun attributesModified(): Boolean = attributesDraft.values.any { it.id == 0 }

    override fun diff(): List<FieldDiff> {
        if (original == null) return listOf(FieldDiff("__new__", null, draft, true))
        val diffs = mutableListOf<FieldDiff>()
        if (original.realName != draft.realName) diffs += FieldDiff("real_name", original.realName, draft.realName, true)
        if (original.nationality != draft.nationality) diffs += FieldDiff("nationality", original.nationality, draft.nationality, true)
        if (original.primaryPosition != draft.primaryPosition) diffs += FieldDiff("primary_position", original.primaryPosition, draft.primaryPosition, true)
        if (original.birthDate != draft.birthDate) diffs += FieldDiff("birth_date", original.birthDate, draft.birthDate, true)
        if (original.height != draft.height) diffs += FieldDiff("height", original.height, draft.height, true)
        if (original.weight != draft.weight) diffs += FieldDiff("weight", original.weight, draft.weight, true)
        if (original.preferredFoot != draft.preferredFoot) diffs += FieldDiff("preferred_foot", original.preferredFoot, draft.preferredFoot, true)
        if (original.retireAgeBase != draft.retireAgeBase) diffs += FieldDiff("retire_age_base", original.retireAgeBase, draft.retireAgeBase, true)
        return diffs
    }
}

/**
 * 俱乐部草稿态（基础信息 + 声望 + 财政 + 设施）。
 */
data class EditableClub(
    val original: ClubEntity?,
    val draft: ClubEntity,
    override val isDeleted: Boolean = false
) : EditableEntity {
    override val originalId: Int get() = draft.clubId
    override val isNew: Boolean get() = original == null
    override val isModified: Boolean
        get() = original == null || original != draft || isDeleted

    override fun diff(): List<FieldDiff> {
        if (original == null) return listOf(FieldDiff("__new__", null, draft, true))
        val diffs = mutableListOf<FieldDiff>()
        if (original.clubName != draft.clubName) diffs += FieldDiff("club_name", original.clubName, draft.clubName, true)
        if (original.country != draft.country) diffs += FieldDiff("country", original.country, draft.country, true)
        if (original.city != draft.city) diffs += FieldDiff("city", original.city, draft.city, true)
        if (original.reputation != draft.reputation) diffs += FieldDiff("reputation", original.reputation, draft.reputation, true)
        if (original.foundedYear != draft.foundedYear) diffs += FieldDiff("founded_year", original.foundedYear, draft.foundedYear, true)
        if (original.trainingLevel != draft.trainingLevel) diffs += FieldDiff("training_level", original.trainingLevel, draft.trainingLevel, true)
        if (original.youthLevel != draft.youthLevel) diffs += FieldDiff("youth_level", original.youthLevel, draft.youthLevel, true)
        if (original.financeLevel != draft.financeLevel) diffs += FieldDiff("finance_level", original.financeLevel, draft.financeLevel, true)
        return diffs
    }
}

/**
 * 比赛草稿态（比赛日期 + 对阵 + 比分）。
 */
data class EditableMatch(
    val original: MatchEntity?,
    val draft: MatchEntity,
    override val isDeleted: Boolean = false
) : EditableEntity {
    override val originalId: Int get() = draft.matchId
    override val isNew: Boolean get() = original == null
    override val isModified: Boolean
        get() = original == null || original != draft || isDeleted

    override fun diff(): List<FieldDiff> {
        if (original == null) return listOf(FieldDiff("__new__", null, draft, true))
        val diffs = mutableListOf<FieldDiff>()
        if (original.matchDate != draft.matchDate) diffs += FieldDiff("match_date", original.matchDate, draft.matchDate, true)
        if (original.homeClubId != draft.homeClubId) diffs += FieldDiff("home_club_id", original.homeClubId, draft.homeClubId, true)
        if (original.awayClubId != draft.awayClubId) diffs += FieldDiff("away_club_id", original.awayClubId, draft.awayClubId, true)
        if (original.homeScoreReal != draft.homeScoreReal) diffs += FieldDiff("home_score_real", original.homeScoreReal, draft.homeScoreReal, true)
        if (original.awayScoreReal != draft.awayScoreReal) diffs += FieldDiff("away_score_real", original.awayScoreReal, draft.awayScoreReal, true)
        return diffs
    }
}
