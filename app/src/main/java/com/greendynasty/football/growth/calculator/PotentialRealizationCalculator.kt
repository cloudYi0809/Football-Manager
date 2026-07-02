package com.greendynasty.football.growth.calculator

import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.PotentialUpdate
import com.greendynasty.football.growth.model.classifyGrowthPhase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 潜力兑现率计算器（V0.2 §潜力兑现率 8 因子公式）
 *
 * 8 因子：职业态度 / 野心 / 士气 / 出场时间 / 训练质量 / 俱乐部设施 / 伤病 / 年龄。
 * 兑现率低 → PA 下调（防小妖全部满潜）；重伤后额外降 PA（V0.2 §十）。
 *
 * 月度更新流程：
 * 1. 计算 8 因子 realization_score（0-1）
 * 2. 兑现率低于 0.5 时下调 current_pa
 * 3. 重伤 6 个月内每月扣 PA（递减）
 */
class PotentialRealizationCalculator(private val config: GrowthConfig) {

    /**
     * 计算 8 因子潜力兑现率。
     *
     * @return 兑现率 0-1（越高越可能兑现潜力）
     */
    fun calculate(input: GrowthInput): Double {
        val attrs = input.attributes

        // 1. 职业态度（0-100 → 0-1）
        val professionalism = (attrs.professionalism / 100.0).coerceIn(0.0, 1.0)

        // 2. 野心（0-100 → 0-1）
        val ambition = (attrs.ambition / 100.0).coerceIn(0.0, 1.0)

        // 3. 士气（0-100 → 0-1）
        val morale = (input.moraleValue / 100.0).coerceIn(0.0, 1.0)

        // 4. 出场时间（minutes_ratio 0-1）
        val playingTime = input.monthlyPlayingTime?.minutesRatio ?: 0.0

        // 5. 训练质量（0-1）
        val trainingQuality = input.monthlyTraining?.trainingQualityScore ?: 0.5

        // 6. 俱乐部设施（0-1）
        val clubFacility = input.clubTrainingQuality

        // 7. 伤病影响（健康 1.0，受伤降低，频繁伤病更低）
        val injury = calculateInjuryFactor(input)

        // 8. 年龄因子（成长期高，衰退期低）
        val age = calculateAgeFactor(input.age)

        return (professionalism * config.realizationWeightProfessionalism +
            ambition * config.realizationWeightAmbition +
            morale * config.realizationWeightMorale +
            playingTime * config.realizationWeightPlayingTime +
            trainingQuality * config.realizationWeightTrainingQuality +
            clubFacility * config.realizationWeightClubFacility +
            injury * config.realizationWeightInjury +
            age * config.realizationWeightAge).coerceIn(0.0, 1.0)
    }

    /**
     * 根据兑现率调整 PA。
     *
     * - 兑现率 >= 0.7：PA 不变
     * - 兑现率 0.5-0.7：PA 不变（观望）
     * - 兑现率 < 0.5：PA 向下调整（initialPa × realization × 0.9）
     *
     * @param input 成长输入
     * @param realization 兑现率
     * @return 调整后的 PA
     */
    fun adjustPotentialPa(input: GrowthInput, realization: Double): Int {
        val currentPa = input.player.currentPa
        val initialPa = input.attributes.pa

        return when {
            // 兑现率高，保持 PA
            realization >= 0.5 -> currentPa
            // 兑现率低，PA 向 initialPa × realization × 0.9 靠拢
            else -> (initialPa * realization * 0.9).toInt()
                .coerceAtLeast(input.player.currentCa) // PA 不低于 CA
                .coerceAtMost(currentPa) // 只降不升（兑现率月度复查）
        }
    }

    /**
     * 月度更新潜力兑现（含重伤 PA 惩罚）。
     *
     * V0.2 §十 重大伤病持续影响：ACL/跟腱等重伤恢复后 6 个月内每月再扣 PA（递减）。
     */
    fun updateMonthly(input: GrowthInput): PotentialUpdate {
        val realization = calculate(input)
        val adjustedPa = adjustPotentialPa(input, realization)
        val majorInjuryPenalty = calculateMajorInjuryPaPenalty(input)

        val paBefore = input.player.currentPa
        val paAfter = (adjustedPa - majorInjuryPenalty)
            .coerceAtLeast(input.player.currentCa)
            .coerceAtLeast(1)

        return PotentialUpdate(
            realizationScore = realization,
            paBefore = paBefore,
            paAfter = paAfter,
            paDelta = paAfter - paBefore,
            majorInjuryPaPenalty = majorInjuryPenalty
        )
    }

    /**
     * 重大伤病 PA 惩罚（V0.2 §十）。
     * ACL/跟腱等重伤恢复后 6 个月内每月扣 PA（递减）。
     */
    private fun calculateMajorInjuryPaPenalty(input: GrowthInput): Int {
        val recentMajor = input.injuryHistory.filter {
            it.severity >= 3 && it.injuryType in config.majorInjuryTypes
        }
        if (recentMajor.isEmpty()) return 0

        return recentMajor.sumOf { injury ->
            val monthsSince = runCatching {
                ChronoUnit.MONTHS.between(
                    LocalDate.parse(injury.startDate),
                    input.executionDate
                )
            }.getOrDefault(0L)
            when {
                monthsSince <= 1 -> config.majorInjuryPaPenaltyMonth1
                monthsSince <= 3 -> config.majorInjuryPaPenaltyMonth3
                monthsSince <= 6 -> config.majorInjuryPaPenaltyMonth6
                else -> 0
            }
        }
    }

    /** 伤病对兑现率的影响因子（健康 1.0，当前受伤 0.3，频繁伤病 0.5） */
    private fun calculateInjuryFactor(input: GrowthInput): Double {
        if (input.activeInjury != null) return 0.3
        val recentInjuryCount = input.injuryHistory.size
        return when {
            recentInjuryCount == 0 -> 1.0
            recentInjuryCount <= 2 -> 0.8
            recentInjuryCount <= 4 -> 0.6
            else -> 0.4
        }
    }

    /** 年龄对兑现率的影响（成长期高，衰退期低） */
    private fun calculateAgeFactor(age: Int): Double = when {
        age <= 20 -> 1.0
        age <= 23 -> 0.9
        age <= 27 -> 0.7
        age <= 30 -> 0.4
        else -> 0.2
    }
}
