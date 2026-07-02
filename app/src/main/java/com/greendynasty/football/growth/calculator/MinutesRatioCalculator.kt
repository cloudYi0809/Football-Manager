package com.greendynasty.football.growth.calculator

import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthFactorBreakdown
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.GrowthResult
import com.greendynasty.football.growth.model.GrowthPhase
import kotlin.random.Random

/**
 * 出场时间统计器（T09.2）
 *
 * 计算 minutes_ratio（本月出场分钟 / 可能最大分钟），并映射为成长因子。
 * 出场时间直接影响成长速度：长期替补成长受限（V0.2 §球员成长公式）。
 */
class MinutesRatioCalculator(private val config: GrowthConfig) {

    /**
     * 将 minutes_ratio 映射为出场时间成长因子（0-1）。
     *
     * - ratio >= 0.65 → 1.0（主力，满成长）
     * - ratio >= 0.35 → 0.7（轮换，七成成长）
     * - ratio >= 0.15 → 0.4（替补，四成成长）
     * - ratio < 0.15 → 0.1（边缘，几乎不成长）
     */
    fun mapRatioToFactor(minutesRatio: Double): Double = when {
        minutesRatio >= 0.65 -> 1.0
        minutesRatio >= 0.35 -> 0.7
        minutesRatio >= 0.15 -> 0.4
        else -> 0.1
    }

    /**
     * 计算球员本月出场时间比例。
     *
     * @param minutesPlayed 本月实际出场分钟
     * @param maxPossibleMinutes 本月可上场最大分钟数（比赛数 × 90）
     * @return minutes_ratio 0-1
     */
    fun calculateRatio(minutesPlayed: Int, maxPossibleMinutes: Int): Double {
        if (maxPossibleMinutes <= 0) return 0.0
        return (minutesPlayed.toDouble() / maxPossibleMinutes).coerceIn(0.0, 1.0)
    }

    /**
     * 获取本月默认最大可上场分钟数。
     * V1 简化：按配置默认值（4 场 × 90 分钟 = 360）。
     */
    fun getDefaultMaxPossibleMinutes(): Int = config.defaultMaxPossibleMinutes
}
