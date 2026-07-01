package com.greendynasty.football.ui.schedule.model

/**
 * 积分榜视图类型
 * - OVERALL：总战绩（默认）
 * - HOME：仅主场战绩
 * - AWAY：仅客场战绩
 */
enum class StandingViewType {
    OVERALL,
    HOME,
    AWAY
}

/**
 * 积分榜条目（UI 模型，含主场/客场拆分用于切换视图）
 *
 * 该模型在内存中计算，由 [com.greendynasty.football.ui.schedule.generator.LeagueTableCalculator]
 * 从已完赛比赛实时累计；持久化时仅保存 overall 字段至 [com.greendynasty.football.data.save.entity.SaveLeagueTableEntity]。
 *
 * 排序规则：积分 > 净胜球 > 进球数 > 名称（详见 [StandingComparator]）。
 */
data class LeagueTableEntry(
    val clubId: Int,
    val clubName: String,
    val rank: Int = 0,

    // 总战绩
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val goalDifference: Int = 0,
    val points: Int = 0,

    // 主场战绩
    val homePlayed: Int = 0,
    val homeWon: Int = 0,
    val homeDrawn: Int = 0,
    val homeLost: Int = 0,
    val homeGoalsFor: Int = 0,
    val homeGoalsAgainst: Int = 0,

    // 客场战绩
    val awayPlayed: Int = 0,
    val awayWon: Int = 0,
    val awayDrawn: Int = 0,
    val awayLost: Int = 0,
    val awayGoalsFor: Int = 0,
    val awayGoalsAgainst: Int = 0,

    /** 近 N 场战绩，如 "WWDWL"（最新在最左） */
    val form: String? = null
) {
    /** 按视图类型返回精简条目，便于 UI 直接渲染 */
    fun forView(view: StandingViewType): LeagueTableEntryView = when (view) {
        StandingViewType.OVERALL -> LeagueTableEntryView(
            clubId = clubId, clubName = clubName, rank = rank,
            played = played, won = won, drawn = drawn, lost = lost,
            goalsFor = goalsFor, goalsAgainst = goalsAgainst,
            goalDifference = goalDifference, points = points, form = form
        )
        StandingViewType.HOME -> LeagueTableEntryView(
            clubId = clubId, clubName = clubName, rank = rank,
            played = homePlayed, won = homeWon, drawn = homeDrawn, lost = homeLost,
            goalsFor = homeGoalsFor, goalsAgainst = homeGoalsAgainst,
            goalDifference = homeGoalsFor - homeGoalsAgainst,
            points = homeWon * 3 + homeDrawn, form = null
        )
        StandingViewType.AWAY -> LeagueTableEntryView(
            clubId = clubId, clubName = clubName, rank = rank,
            played = awayPlayed, won = awayWon, drawn = awayDrawn, lost = awayLost,
            goalsFor = awayGoalsFor, goalsAgainst = awayGoalsAgainst,
            goalDifference = awayGoalsFor - awayGoalsAgainst,
            points = awayWon * 3 + awayDrawn, form = null
        )
    }
}

/**
 * 精简积分榜条目，用于 UI 渲染单视图
 */
data class LeagueTableEntryView(
    val clubId: Int,
    val clubName: String,
    val rank: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int,
    val form: String?
)
