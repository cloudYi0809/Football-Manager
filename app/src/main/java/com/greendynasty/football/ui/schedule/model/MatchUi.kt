package com.greendynasty.football.ui.schedule.model

/**
 * 比赛行 UI 模型（聚合 save_match + 俱乐部名称 + 赛事名称）
 *
 * 直接驱动 [com.greendynasty.football.ui.schedule.ui.MatchRow] 渲染。
 */
data class MatchUi(
    val matchId: Int,
    val matchDate: String,         // ISO 日期 YYYY-MM-DD
    val round: Int,                // 联赛轮次 / 杯赛阶段序号
    val competitionId: Int,
    val competitionShortName: String,
    val homeClubId: Int,
    val homeClubName: String,
    val awayClubId: Int,
    val awayClubName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: MatchStatus,
    val isPlayerMatch: Boolean,
    val stage: String? = null      // 杯赛阶段标识（联赛为 null）
)

/**
 * 杯赛对阵 UI 模型（聚合 save_cup_tie + 俱乐部名称）
 */
data class CupTieUi(
    val tieId: String,
    val stage: CupStage,
    val stageOrder: Int,
    val slotIndex: Int,
    val homeClubId: Int?,
    val homeClubName: String?,
    val awayClubId: Int?,
    val awayClubName: String?,
    val isTwoLegged: Boolean,
    val aggregateHomeScore: Int?,
    val aggregateAwayScore: Int?,
    val winnerClubId: Int?,
    val nextTieId: String?
)

/**
 * 杯赛某一阶段的对阵组（用于 bracket 面板按阶段渲染）
 */
data class CupStageUi(
    val stage: CupStage,
    val ties: List<CupTieUi>
)
