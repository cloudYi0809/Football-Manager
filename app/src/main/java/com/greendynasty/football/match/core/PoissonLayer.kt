package com.greendynasty.football.match.core

import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.model.XGResult
import kotlin.math.exp
import kotlin.random.Random

/**
 * Layer 3 泊松进球层（V0.2 04 §六）
 *
 * 用泊松分布生成双方进球数，并应用极端比分抑制。
 *
 * 公式：P(k goals) = (λ^k × e^-λ) / k!，λ = xG
 * 采样采用 Knuth 算法，可复现（依赖外部注入的 [Random]）。
 */
class PoissonLayer(private val config: MatchConfig = MatchConfig.DEFAULT) {

    /**
     * 生成主客队进球数。
     *
     * @param xg XGLayer 产出的预期进球
     * @param random 可复现随机源（由 MatchSimulator 从 randomSeed 派生）
     * @return (homeGoals, awayGoals)
     */
    fun simulate(xg: XGResult, random: Random): Pair<Int, Int> {
        // 1. 泊松采样（V0.2 04 §六）
        var homeGoals = poissonSample(xg.homeXg, random)
        var awayGoals = poissonSample(xg.awayXg, random)

        // 2. 极端比分抑制（V0.2 04 §六）
        // 极端比赛（rare_extreme_match）不抑制，允许大比分
        if (!xg.isExtremeMatch) {
            homeGoals = applyExtremeDampening(homeGoals, random)
            awayGoals = applyExtremeDampening(awayGoals, random)
        }

        return homeGoals to awayGoals
    }

    /**
     * 泊松采样：Knuth 算法
     *
     * P(k) = (λ^k × e^-λ) / k!
     * 通过累积随机数直到小于 L = e^-λ 来确定 k。
     */
    private fun poissonSample(lambda: Double, random: Random): Int {
        // λ 上限保护：避免大 λ 导致算法退化
        val safeLambda = lambda.coerceIn(0.0, 10.0)
        if (safeLambda <= 0.0) return 0

        val l = exp(-safeLambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > l)
        return k - 1
    }

    /**
     * 极端比分抑制（V0.2 04 §六）
     *
     * - 单队 5 球以上概率乘 0.65
     * - 单队 6 球以上概率乘 0.40
     * - 单队 7 球以上概率乘 0.20
     *
     * 实现方式：以对应概率将进球数减 1。
     */
    private fun applyExtremeDampening(goals: Int, random: Random): Int {
        if (goals < 5) return goals
        val dampenProb = when (goals) {
            5 -> config.extremeDampen5
            6 -> config.extremeDampen6
            else -> config.extremeDampen7Plus
        }
        // 概率性抑制：以 dampenProb 概率减 1，否则保留
        return if (random.nextDouble() < dampenProb) goals - 1 else goals
    }
}
