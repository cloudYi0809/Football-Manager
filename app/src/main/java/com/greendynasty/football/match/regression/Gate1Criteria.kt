package com.greendynasty.football.match.regression

/**
 * Gate1 验收标准（T02 方案 §十一）
 *
 * 6 项验收标准，全部通过即视为 Gate1 达标：
 * 1. 场均进球 2.4-2.7
 * 2. 主场胜率 44%-48%
 * 3. 大比分频率 < 3%
 * 4. 0-0 概率 7%-10%
 * 5. xG 与实际进球相关度 > 0.7
 * 6. 无 10-0 比分
 *
 * 严格按 T02 方案阈值，不放宽。
 */
object Gate1Criteria {

    /** 场均进球下限 */
    private const val AVG_GOALS_MIN = 2.4
    /** 场均进球上限 */
    private const val AVG_GOALS_MAX = 2.7
    /** 主场胜率下限 */
    private const val HOME_WIN_RATE_MIN = 0.44
    /** 主场胜率上限 */
    private const val HOME_WIN_RATE_MAX = 0.48
    /** 大比分频率上限 */
    private const val BIG_SCORE_PROB_MAX = 0.03
    /** 0-0 概率下限 */
    private val ZERO_ZERO_PROB_MIN = 0.07
    /** 0-0 概率上限 */
    private val ZERO_ZERO_PROB_MAX = 0.10
    /** xG 相关度下限 */
    private const val XG_CORRELATION_MIN = 0.7

    /**
     * 评估统计报告是否满足全部 Gate1 标准。
     *
     * @param summary 批量模拟统计快照
     * @return 逐项评估结果与总体结论
     */
    fun evaluate(summary: MatchStatisticsSummary): Gate1Result {
        val criteria = listOf(
            CriterionResult(
                name = "场均进球",
                actual = summary.avgGoalsPerMatch,
                expected = "$AVG_GOALS_MIN-$AVG_GOALS_MAX",
                passed = summary.avgGoalsPerMatch in AVG_GOALS_MIN..AVG_GOALS_MAX
            ),
            CriterionResult(
                name = "主场胜率",
                actual = summary.homeWinRate,
                expected = "${(HOME_WIN_RATE_MIN * 100).toInt()}%-${(HOME_WIN_RATE_MAX * 100).toInt()}%",
                passed = summary.homeWinRate in HOME_WIN_RATE_MIN..HOME_WIN_RATE_MAX
            ),
            CriterionResult(
                name = "大比分频率",
                actual = summary.bigScoreProb,
                expected = "< ${(BIG_SCORE_PROB_MAX * 100).toInt()}%",
                passed = summary.bigScoreProb < BIG_SCORE_PROB_MAX
            ),
            CriterionResult(
                name = "0-0 概率",
                actual = summary.zeroZeroProb,
                expected = "${(ZERO_ZERO_PROB_MIN * 100).toInt()}%-${(ZERO_ZERO_PROB_MAX * 100).toInt()}%",
                passed = summary.zeroZeroProb in ZERO_ZERO_PROB_MIN..ZERO_ZERO_PROB_MAX
            ),
            CriterionResult(
                name = "xG 相关度",
                actual = summary.xgGoalsCorrelation,
                expected = "> $XG_CORRELATION_MIN",
                passed = summary.xgGoalsCorrelation > XG_CORRELATION_MIN
            ),
            CriterionResult(
                name = "无 10-0 比分",
                actual = if (summary.hasTenZeroScore) "出现" else "未出现",
                expected = "未出现",
                passed = !summary.hasTenZeroScore
            )
        )

        val allPassed = criteria.all { it.passed }
        return Gate1Result(
            criteria = criteria,
            passedCount = criteria.count { it.passed },
            totalCount = criteria.size,
            allPassed = allPassed
        )
    }
}

/**
 * 单项验收结果。
 */
data class CriterionResult(
    /** 标准名称 */
    val name: String,
    /** 实际值 */
    val actual: Any,
    /** 期望范围（文字） */
    val expected: String,
    /** 是否通过 */
    val passed: Boolean
)

/**
 * Gate1 总体验收结果。
 */
data class Gate1Result(
    /** 逐项结果 */
    val criteria: List<CriterionResult>,
    /** 通过项数 */
    val passedCount: Int,
    /** 总项数 */
    val totalCount: Int,
    /** 是否全部通过 */
    val allPassed: Boolean
) {
    /** 未通过项 */
    val failedCriteria: List<CriterionResult>
        get() = criteria.filter { !it.passed }
}
