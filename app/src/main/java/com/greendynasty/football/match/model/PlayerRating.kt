package com.greendynasty.football.match.model

/**
 * 球员赛后评分（V0.2 04 §十一）
 *
 * 评分基础 6.5，按进球 / 助攻 / 关键传球 / 抢断 / 拦截 / 扑救加分，
 * 按失误 / 红牌 / 乌龙扣分，最终 clamp 到 4.0-10.0。
 */
data class PlayerRating(
    /** 球员 ID */
    val playerId: String,
    /** 赛后综合评分 4.0-10.0 */
    val rating: Double,
    /** 评分明细 */
    val breakdown: PlayerRatingBreakdown
)

/**
 * 球员评分明细
 *
 * 记录各项加分扣分，便于调试与展示。
 */
data class PlayerRatingBreakdown(
    /** 进球数 */
    val goals: Int,
    /** 助攻数 */
    val assists: Int,
    /** 关键传球数（危险进攻事件） */
    val keyPasses: Int,
    /** 抢断成功数 */
    val tacklesSuccess: Int,
    /** 拦截数 */
    val interceptions: Int,
    /** 扑救数（仅门将） */
    val saves: Int,
    /** 失误导致进球 */
    val errorsLeadingToGoal: Int,
    /** 红牌数 */
    val redCard: Int,
    /** 乌龙球 */
    val ownGoal: Int,
    /** 球队结果加分（胜/平/负） */
    val teamResultBonus: Double,
    /** 位置进球权重 */
    val positionGoalWeight: Double
)
