package com.greendynasty.football.ui.tactics.data

import com.greendynasty.football.match.api.PlayerAttributes

/**
 * 球员信息聚合（含位置与状态，供战术页使用）。
 *
 * 聚合 history.db 球员基础信息与 save.db 球员状态，
 * 供战术页首发选择 / 拖拽换位 / 位置适配度计算使用。
 *
 * @property playerId 球员 ID
 * @property name 展示姓名
 * @property position 主要位置（GK/CB/LB/RB/DM/CM/AM/LW/RW/ST/CF）
 * @property ca 当前能力值
 * @property pa 潜力值
 * @property condition 体能 0-100
 * @property morale 士气 0-100
 * @property preferredFoot 惯用脚（left/right/both）
 * @property positionFit 当前位置适配度 0-100（由 [com.greendynasty.football.ui.tactics.algorithm.PositionFitChecker] 计算）
 * @property isInjured 是否伤病
 * @property isSuspended 是否停赛（红牌停赛）
 * @property attributes 球员属性（用于战术熟练度计算），可空
 * @property secondaryPositions 副位置列表
 * @property shirtNumber 球衣号码
 */
data class PlayerWithPosition(
    val playerId: Int,
    val name: String,
    val position: String,
    val ca: Int,
    val pa: Int,
    val condition: Int,
    val morale: Int,
    val preferredFoot: String,
    val positionFit: Int = 0,
    val isInjured: Boolean = false,
    val isSuspended: Boolean = false,
    val attributes: PlayerAttributes? = null,
    val secondaryPositions: List<String> = emptyList(),
    val shirtNumber: Int? = null
) {

    /** 是否可出场（无伤病且无停赛） */
    val isAvailable: Boolean
        get() = !isInjured && !isSuspended

    /** 体能状态摘要 */
    val conditionText: String
        get() = when {
            condition >= 80 -> "体能充沛"
            condition >= 60 -> "体能良好"
            condition >= 40 -> "体能一般"
            else -> "体能不足"
        }

    /** 位置展示名（中文） */
    val positionLabel: String
        get() = com.greendynasty.football.ui.tactics.model.FormationDefinition
            .roleLabelOf(
                runCatching {
                    com.greendynasty.football.match.api.Position.valueOf(position)
                }.getOrDefault(com.greendynasty.football.match.api.Position.CM)
            )
}
