package com.greendynasty.football.match.regression

import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.model.MatchResult
import kotlin.math.sqrt

/**
 * 统计报告（T02 方案 §十一 Gate1 回归验收）
 *
 * 汇总批量模拟结果，计算 Gate1 验收所需的全部统计指标：
 * - 场均进球
 * - 主场胜率 / 平局率 / 客场胜率
 * - 大比分概率（总进球 ≥ 6）
 * - 0-0 概率
 * - 极端比分频率（单队进球 ≥ extremeScoreThreshold）
 * - xG 与实际进球的 Pearson 相关度
 * - 是否出现 10-0 比分
 */
class StatisticsReport(
    private val config: MatchConfig = MatchConfig.DEFAULT
) {

    /**
     * 汇总统计结果。
     *
     * @param results 批量比赛结果
     * @return 统计指标快照
     */
    fun summarize(results: List<MatchResult>): MatchStatisticsSummary {
        if (results.isEmpty()) {
            return MatchStatisticsSummary(
                matchCount = 0,
                avgGoalsPerMatch = 0.0,
                homeWinRate = 0.0,
                drawRate = 0.0,
                awayWinRate = 0.0,
                bigScoreProb = 0.0,
                zeroZeroProb = 0.0,
                extremeScoreFrequency = 0.0,
                avgHomeXg = 0.0,
                avgAwayXg = 0.0,
                xgGoalsCorrelation = 0.0,
                maxHomeScore = 0,
                maxAwayScore = 0,
                hasTenZeroScore = false
            )
        }

        val n = results.size
        var totalGoals = 0
        var homeWins = 0
        var draws = 0
        var awayWins = 0
        var bigScoreCount = 0
        var zeroZeroCount = 0
        var extremeCount = 0
        var totalHomeXg = 0.0
        var totalAwayXg = 0.0
        var maxHomeScore = 0
        var maxAwayScore = 0
        var hasTenZero = false

        // xG 与实际进球配对，用于相关度计算
        val xgGoalsPairs = mutableListOf<Pair<Double, Int>>()

        for (r in results) {
            totalGoals += r.homeScore + r.awayScore

            when {
                r.homeScore > r.awayScore -> homeWins++
                r.homeScore < r.awayScore -> awayWins++
                else -> draws++
            }

            // 大比分：总进球 ≥ 6
            if (r.homeScore + r.awayScore >= 6) bigScoreCount++

            // 0-0
            if (r.homeScore == 0 && r.awayScore == 0) zeroZeroCount++

            // 极端比分：单队进球 ≥ extremeScoreThreshold
            if (r.homeScore >= config.extremeScoreThreshold ||
                r.awayScore >= config.extremeScoreThreshold
            ) extremeCount++

            // 10-0 检测（任意方向）
            if ((r.homeScore == 10 && r.awayScore == 0) ||
                (r.awayScore == 10 && r.homeScore == 0)
            ) hasTenZero = true

            totalHomeXg += r.homeXg
            totalAwayXg += r.awayXg
            maxHomeScore = maxOf(maxHomeScore, r.homeScore)
            maxAwayScore = maxOf(maxAwayScore, r.awayScore)

            xgGoalsPairs.add(r.homeXg to r.homeScore)
            xgGoalsPairs.add(r.awayXg to r.awayScore)
        }

        return MatchStatisticsSummary(
            matchCount = n,
            avgGoalsPerMatch = totalGoals.toDouble() / n,
            homeWinRate = homeWins.toDouble() / n,
            drawRate = draws.toDouble() / n,
            awayWinRate = awayWins.toDouble() / n,
            bigScoreProb = bigScoreCount.toDouble() / n,
            zeroZeroProb = zeroZeroCount.toDouble() / n,
            extremeScoreFrequency = extremeCount.toDouble() / n,
            avgHomeXg = totalHomeXg / n,
            avgAwayXg = totalAwayXg / n,
            xgGoalsCorrelation = pearsonCorrelation(xgGoalsPairs),
            maxHomeScore = maxHomeScore,
            maxAwayScore = maxAwayScore,
            hasTenZeroScore = hasTenZero
        )
    }

    /**
     * Pearson 相关系数。
     *
     * 衡量 xG 与实际进球的线性相关度，Gate1 要求 > 0.7。
     */
    private fun pearsonCorrelation(pairs: List<Pair<Double, Int>>): Double {
        if (pairs.size < 2) return 0.0
        val xs = pairs.map { it.first }
        val ys = pairs.map { it.second.toDouble() }
        val meanX = xs.average()
        val meanY = ys.average()

        var numerator = 0.0
        var sumSqX = 0.0
        var sumSqY = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            numerator += dx * dy
            sumSqX += dx * dx
            sumSqY += dy * dy
        }
        val denominator = sqrt(sumSqX * sumSqY)
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}

/**
 * 比赛统计指标快照。
 */
data class MatchStatisticsSummary(
    /** 比赛场数 */
    val matchCount: Int,
    /** 场均进球（主+客） */
    val avgGoalsPerMatch: Double,
    /** 主场胜率 0.0-1.0 */
    val homeWinRate: Double,
    /** 平局率 0.0-1.0 */
    val drawRate: Double,
    /** 客场胜率 0.0-1.0 */
    val awayWinRate: Double,
    /** 大比分概率（总进球 ≥ 6） */
    val bigScoreProb: Double,
    /** 0-0 概率 */
    val zeroZeroProb: Double,
    /** 极端比分频率（单队 ≥ extremeScoreThreshold） */
    val extremeScoreFrequency: Double,
    /** 场均主队 xG */
    val avgHomeXg: Double,
    /** 场均客队 xG */
    val avgAwayXg: Double,
    /** xG 与实际进球的 Pearson 相关度 */
    val xgGoalsCorrelation: Double,
    /** 单场主队最高进球 */
    val maxHomeScore: Int,
    /** 单场客队最高进球 */
    val maxAwayScore: Int,
    /** 是否出现 10-0 比分 */
    val hasTenZeroScore: Boolean
)
