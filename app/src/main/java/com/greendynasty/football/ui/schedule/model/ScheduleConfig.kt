package com.greendynasty.football.ui.schedule.model

/**
 * 杯赛阶段配置
 *
 * @param stage 阶段标识：round_of_32 / round_of_16 / quarter / semi / final
 * @param stageOrder 阶段排序 1..5
 * @param isTwoLegged 是否双回合
 * @param intervalDays 距上一轮间隔天数
 */
data class CupStageConfig(
    val stage: String,
    val stageOrder: Int,
    val isTwoLegged: Boolean,
    val intervalDays: Int
)

/**
 * 赛程生成配置（参数化，对应 schedule_config.json）
 *
 * 所有赛程/积分榜相关参数集中在此，修改不需要重新编译算法逻辑。
 * 严格依据 T06 实现方案 §八 schedule_config.json 默认值。
 */
data class ScheduleConfig(
    // ===== 联赛 =====
    val defaultLeagueRounds: Int = 38,
    val defaultTeamCount: Int = 20,
    val leagueRoundIntervalDays: Int = 7,
    val maxConsecutiveHomeAway: Int = 3,
    val adjustMaxAttempts: Int = 50,
    val pointsForWin: Int = 3,
    val pointsForDraw: Int = 1,
    val pointsForLoss: Int = 0,
    val useAwayGoalsTiebreaker: Boolean = true,
    val promotionZoneSize: Int = 3,
    val relegationZoneSize: Int = 3,

    // ===== 杯赛 =====
    val cupUsePenaltyShootout: Boolean = true,
    val seedByReputation: Boolean = true,
    val defaultCupStages: List<CupStageConfig> = listOf(
        CupStageConfig("round_of_32", 1, isTwoLegged = false, intervalDays = 14),
        CupStageConfig("round_of_16", 2, isTwoLegged = false, intervalDays = 14),
        CupStageConfig("quarter", 3, isTwoLegged = true, intervalDays = 14),
        CupStageConfig("semi", 4, isTwoLegged = true, intervalDays = 14),
        CupStageConfig("final", 5, isTwoLegged = false, intervalDays = 21)
    ),

    // ===== 性能 =====
    val scheduleGenerateTargetMs: Long = 500L,
    val tableUpdateTargetMs: Long = 100L,
    val tableCacheTtlMinutes: Int = 5,
    val matchListPageSize: Int = 50
) {
    companion object {
        /** 默认配置实例（单例风格，避免重复创建） */
        val DEFAULT = ScheduleConfig()
    }
}
