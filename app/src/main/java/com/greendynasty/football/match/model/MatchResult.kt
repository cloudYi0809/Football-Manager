package com.greendynasty.football.match.model

/**
 * 比赛最终结果
 *
 * 由 MatchSimulator 串联四层流水线产出，
 * 包含比分 / xG / 事件流 / 球员评分 / 统计 / 伤病 / 牌 / 校准标记。
 */
data class MatchResult(
    /** 比赛 ID */
    val matchId: String,
    /** 主队进球数 */
    val homeScore: Int,
    /** 客队进球数 */
    val awayScore: Int,
    /** 主队预期进球 */
    val homeXg: Double,
    /** 客队预期进球 */
    val awayXg: Double,
    /** 全场事件流（按分钟升序） */
    val events: List<MatchEvent>,
    /** 球员赛后评分：playerId -> PlayerRating */
    val playerRatings: Map<String, PlayerRating>,
    /** 主队统计 */
    val homeStats: MatchStatistics,
    /** 客队统计 */
    val awayStats: MatchStatistics,
    /** 伤病记录 */
    val injuries: List<InjuryRecord>,
    /** 牌记录 */
    val cards: List<CardRecord>,
    /** 是否经过极端比分校准 */
    val calibrated: Boolean
)

/**
 * 球队比赛统计（V0.2 06 §十三）
 */
data class MatchStatistics(
    /** 控球率 0.0-1.0 */
    val possession: Double,
    /** 射门数 */
    val shots: Int,
    /** 射正数 */
    val shotsOnTarget: Int,
    /** 角球数 */
    val corners: Int,
    /** 犯规数 */
    val fouls: Int,
    /** 黄牌数 */
    val yellowCards: Int,
    /** 红牌数 */
    val redCards: Int,
    /** 传球成功率 0.0-1.0 */
    val passAccuracy: Double
)

/**
 * 伤病记录
 */
data class InjuryRecord(
    val playerId: String,
    val teamSide: TeamSide,
    val minute: Int,
    /** 伤病严重程度（天数估算） */
    val severityDays: Int,
    val description: String
)

/**
 * 牌记录
 */
data class CardRecord(
    val playerId: String,
    val teamSide: TeamSide,
    val minute: Int,
    /** YELLOW / RED */
    val cardType: String
)
