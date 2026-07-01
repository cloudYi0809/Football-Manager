package com.greendynasty.football.ui.tactics.model

import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.api.TacticStyle

/**
 * 完整战术设置（V0.1 03 §3 战术页）。
 *
 * 聚合阵型、战术风格、参数、角色分配、首发 11 人与替补席。
 * 是战术页的核心状态对象，由 [com.greendynasty.football.ui.tactics.viewmodel.TacticsViewModel] 持有。
 *
 * @property clubId 俱乐部 ID
 * @property formation 阵型（复用 T02 [Formation] 枚举）
 * @property style 战术风格（复用 T02 [TacticStyle] 枚举）
 * @property parameters 战术参数
 * @property playerRoles 球员角色分配
 * @property starting11 首发 11 人槽位（按阵型位置顺序）
 * @property substitutes 替补席
 */
data class TacticalSetup(
    val clubId: Int = DEFAULT_CLUB_ID,
    val formation: Formation = Formation.F433,
    val style: TacticStyle = TacticStyle.POSSESSION,
    val parameters: TacticalParameters = TacticalParameters.DEFAULT,
    val playerRoles: PlayerRoleAssignment = PlayerRoleAssignment.DEFAULT,
    val starting11: List<PlayerSlot> = emptyList(),
    val substitutes: List<PlayerSlot> = emptyList()
) {

    /** 首发 11 人数（已分配球员的槽位） */
    val filledStartingCount: Int
        get() = starting11.count { it.playerId != null }

    /** 替补人数 */
    val filledSubstituteCount: Int
        get() = substitutes.count { it.playerId != null }

    /** 首发 11 人是否已满 */
    val isStartingComplete: Boolean
        get() = starting11.size == STARTING_SIZE && starting11.all { it.playerId != null }

    companion object {
        /** 首发人数 */
        const val STARTING_SIZE = 11

        /** 替补席上限 */
        const val SUBSTITUTES_MAX = 7

        /** 默认俱乐部 ID */
        const val DEFAULT_CLUB_ID = 1

        /** 默认战术设置 */
        val DEFAULT = TacticalSetup()
    }
}

/**
 * 球员槽位（阵型位置与球员的绑定）。
 *
 * @property slotId 槽位 ID（1-11，对应阵型位置顺序）
 * @property playerId 球员 ID，null 表示空槽位
 * @property position 场上位置（复用 T02 [Position] 枚举）
 * @property roleLabel 位置角色展示名（如"门将"/"中后卫"/"左边锋"）
 * @property positionFitScore 位置适配度 0-100
 */
data class PlayerSlot(
    val slotId: Int,
    val playerId: Int? = null,
    val position: Position,
    val roleLabel: String,
    val positionFitScore: Int = 0
) {

    /** 槽位是否已分配球员 */
    val isFilled: Boolean
        get() = playerId != null

    /** 适配度等级 */
    val fitLevel: FitLevel
        get() = when {
            positionFitScore >= 90 -> FitLevel.PERFECT
            positionFitScore >= 70 -> FitLevel.GOOD
            positionFitScore >= 40 -> FitLevel.FAIR
            else -> FitLevel.POOR
        }
}
