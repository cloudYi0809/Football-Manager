package com.greendynasty.football.media.model

/**
 * T24 媒体配置（V0.2 + T24 任务要求 + 实现方案 §八 media_config.json）。
 *
 * 严格依据 V0.2 算法文档，所有可调参数集中配置化，便于调参不改架构（铁律）。
 *
 * 涵盖：
 * - 新闻生成参数（每日上限 / 概率 / 单事件上限）
 * - 重要性分级权重
 * - 时效性策略（1-3 天 TTL）
 * - 采访参数（每场问题数 / 跳过惩罚）
 * - 5 种回答风格基础影响
 * - 上下文调整因子（危机 / 争议 / 赛前）
 * - 舆论值阈值与衰减参数
 * - 单条新闻影响上限（避免媒体盖过比赛）
 *
 * @property newsGeneration 新闻生成参数
 * @property expiration 时效性参数
 * @property interview 采访参数
 * @property answerImpactBase 5 种回答风格基础影响
 * @property answerContextMultipliers 上下文调整因子
 * @property opinion 舆论值参数
 * @property maxImpactPerNews 单条新闻单维度影响上限（默认 5）
 */
data class MediaConfig(
    val newsGeneration: NewsGenerationParams = NewsGenerationParams(),
    val expiration: ExpirationParams = ExpirationParams(),
    val interview: InterviewParams = InterviewParams(),
    val answerImpactBase: Map<AnswerStyle, MediaImpact> = defaultAnswerImpactBase,
    val answerContextMultipliers: AnswerContextMultipliers = AnswerContextMultipliers(),
    val opinion: OpinionParams = OpinionParams(),
    val maxImpactPerNews: Int = 5
) {
    companion object {
        /** 默认配置（V0.2 推荐参数）。 */
        val DEFAULT = MediaConfig()

        /**
         * 5 种回答风格基础影响（V0.2 + T24 实现方案 §五.2）。
         *
         * NEUTRAL:    morale=0,  fan=0,  board=0,  opinion=0,  reputation=0
         * CONFIDENT:  morale=+2, fan=+3, board=+1, opinion=+1, reputation=+1
         * HUMBLE:     morale=+1, fan=+1, board=+2, opinion=+2, reputation=0
         * AGGRESSIVE: morale=-1, fan=-2, board=-3, opinion=-5, reputation=-1
         * DEFLECT:    morale=0,  fan=0,  board=0,  opinion=-1, reputation=0
         */
        val defaultAnswerImpactBase: Map<AnswerStyle, MediaImpact> = mapOf(
            AnswerStyle.NEUTRAL to MediaImpact(0, 0, 0, 0, 0),
            AnswerStyle.CONFIDENT to MediaImpact(2, 3, 1, 1, 1),
            AnswerStyle.HUMBLE to MediaImpact(1, 1, 2, 2, 0),
            AnswerStyle.AGGRESSIVE to MediaImpact(-1, -2, -3, -5, -1),
            AnswerStyle.DEFLECT to MediaImpact(0, 0, 0, -1, 0)
        )
    }
}

/**
 * 新闻生成参数（V0.2 + T24 实现方案 §八 news_generation）。
 *
 * @property minDailyNews 每日最少新闻数（不足时补充背景新闻）
 * @property maxDailyNews 每日最多新闻数（超过按重要性截断）
 * @property maxNewsPerEvent 单事件最多新闻数（避免刷屏）
 * @property generateProbSameLeague 同联赛事件生成概率
 * @property generateProbCrossLeague 跨联赛事件生成概率
 * @property generateProbOther 其他事件生成概率
 * @property highProfileEvents 高曝光事件类型列表（跨联赛也较高概率上新闻）
 */
data class NewsGenerationParams(
    val minDailyNews: Int = 3,
    val maxDailyNews: Int = 30,
    val maxNewsPerEvent: Int = 2,
    val generateProbSameLeague: Double = 0.6,
    val generateProbCrossLeague: Double = 0.3,
    val generateProbOther: Double = 0.1,
    val highProfileEvents: List<String> = listOf(
        "TRANSFER_COMPLETED_TOP",
        "CHAMPIONS_LEAGUE_MATCH",
        "MANAGER_SACKED",
        "CAPITAL_TAKEOVER",
        "DERBY_MATCH",
        "TITLE_WON"
    )
)

/**
 * 时效性参数（V0.2 + T24 实现方案 §八 expiration_policy）。
 *
 * 不同重要性对应不同 TTL，过期新闻每日清理。
 */
data class ExpirationParams(
    val defaultTtlDays: Int = 2,
    val importance5TtlDays: Int = 3,
    val importance4TtlDays: Int = 3,
    val importance3TtlDays: Int = 2,
    val importance2TtlDays: Int = 1,
    val importance1TtlDays: Int = 1,
    val cleanupBatchSize: Int = 100
)

/**
 * 采访参数（V0.2 + T24 实现方案 §八 press_conference）。
 *
 * @property questionsPerInterview 每场采访问题数
 * @property optionsPerQuestion 每个问题选项数
 * @property preMatchLeadDays 赛前采访提前天数（1-3 天）
 * @property skipPenaltyOpinionDelta 跳过采访的舆论惩罚
 */
data class InterviewParams(
    val questionsPerInterview: Int = 4,
    val optionsPerQuestion: Int = 4,
    val preMatchLeadDays: IntRange = 1..3,
    val skipPenaltyOpinionDelta: Int = -10
)

/**
 * 上下文调整因子（V0.2 + T24 实现方案 §八 answer_context_multipliers）。
 *
 * 不同场景下对回答影响的放大系数：
 * - CRISIS_AGGRESSIVE：危机采访中激进回答代价更高
 * - CONTROVERSY_HUMBLE：争议话题中谦虚回答收益更高
 * - PRE_MATCH_CONFIDENT：赛前采访中自信回答收益更高
 */
data class AnswerContextMultipliers(
    val crisisAggressive: Double = 1.5,
    val controversyHumble: Double = 1.3,
    val preMatchConfident: Double = 1.2
)

/**
 * 舆论值参数（V0.2 + T24 任务要求 §核心要点 4）。
 *
 * @property initialValue 初始舆论值（新存档）
 * @property minValue 最小值
 * @property maxValue 最大值
 * @property decayThresholdDays 久未互动阈值（≥N 天未互动开始衰减）
 * @property dailyDecayAmount 每日衰减量
 * @property minDecayValue 衰减下限（避免无限下降到 0）
 * @property winStreakDeltaPerMatch 连胜每场舆论 +
 * @property loseStreakDeltaPerMatch 连败每场舆论 -
 * @property scandalDelta 丑闻曝光舆论 -
 * @property excellentThreshold 极佳阈值
 * @property goodThreshold 良好阈值
 * @property poorThreshold 较差阈值
 */
data class OpinionParams(
    val initialValue: Int = 50,
    val minValue: Int = 0,
    val maxValue: Int = 100,
    val decayThresholdDays: Int = 7,
    val dailyDecayAmount: Int = 1,
    val minDecayValue: Int = 20,
    val winStreakDeltaPerMatch: Int = 1,
    val loseStreakDeltaPerMatch: Int = -1,
    val scandalDelta: Int = -7,
    val excellentThreshold: Int = 80,
    val goodThreshold: Int = 60,
    val poorThreshold: Int = 20
)
