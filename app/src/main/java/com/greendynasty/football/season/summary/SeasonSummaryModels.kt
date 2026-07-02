package com.greendynasty.football.season.summary

import kotlinx.serialization.Serializable

/**
 * T19 赛季摘要数据模型（V0.2 §七.1）
 *
 * 赛季结束后生成完整摘要并序列化为 JSON 存入 `season_archive.summary_json`，
 * 用于历史赛季查询（旧赛季只读访问）。
 *
 * 所有字段均可序列化，使用 kotlinx.serialization。
 */
@Serializable
data class SeasonSummary(
    /** 赛季 ID */
    val seasonId: Int,
    /** 赛季标签，如 "2002/03" */
    val seasonLabel: String,
    /** 联赛积分榜（按联赛分组） */
    val leagueStandings: List<LeagueStandingSummary>,
    /** 射手榜 Top N */
    val topScorers: List<ScorerListSummary>,
    /** 助攻榜 Top N */
    val topAssists: List<ScorerListSummary>,
    /** 转会汇总 */
    val transfers: TransferSummary,
    /** 玩家俱乐部财政摘要 */
    val managerClubFinancial: ClubFinancialSummary,
    /** 赛季奖项 */
    val awards: List<AwardSummary>,
    /** 升级俱乐部 ID 列表 */
    val promotions: List<Int>,
    /** 降级俱乐部 ID 列表 */
    val relegations: List<Int>
)

/**
 * 联赛积分榜摘要
 */
@Serializable
data class LeagueStandingSummary(
    val leagueId: String,
    val leagueName: String,
    val standings: List<StandingEntry>
)

/**
 * 积分榜单条目
 */
@Serializable
data class StandingEntry(
    val position: Int,
    val clubId: Int,
    val clubName: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
) {
    /** 净胜球 */
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

/**
 * 射手榜 / 助攻榜单条目（统一结构）
 */
@Serializable
data class ScorerListSummary(
    val playerId: Int,
    val playerName: String,
    val clubId: Int?,
    val clubName: String,
    val goals: Int,
    val matches: Int
)

/**
 * 转会汇总
 */
@Serializable
data class TransferSummary(
    /** 完成转会总数 */
    val totalTransfers: Int,
    /** 转会费总额 */
    val totalFee: Long,
    /** 单笔最高转会费 */
    val maxFee: Int,
    /** Top 10 转会记录 */
    val topTransfers: List<TransferRecord>
)

/**
 * 单条转会记录摘要
 */
@Serializable
data class TransferRecord(
    val playerId: Int,
    val playerName: String,
    val fromClubId: Int?,
    val fromClubName: String,
    val toClubId: Int?,
    val toClubName: String,
    val fee: Int,
    val date: String
)

/**
 * 俱乐部财政摘要（玩家俱乐部）
 */
@Serializable
data class ClubFinancialSummary(
    val clubId: Int,
    val clubName: String,
    val balance: Int,
    val transferBudget: Int,
    val wageBudget: Int,
    val boardSatisfaction: Int,
    val fanSatisfaction: Int
)

/**
 * 赛季奖项
 */
@Serializable
data class AwardSummary(
    /** 奖项类型：golden_boot / golden_ball / best_assistant / best_young_player 等 */
    val awardType: String,
    /** 奖项名称（中文） */
    val awardName: String,
    /** 获奖球员 ID */
    val playerId: Int,
    /** 获奖球员姓名 */
    val playerName: String,
    /** 获奖俱乐部 ID */
    val clubId: Int?,
    /** 关键数据（如进球数、助攻数） */
    val statValue: Int
)
