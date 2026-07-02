package com.greendynasty.football.growth.calculator

import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthFactorBreakdown
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.GrowthPhase
import com.greendynasty.football.growth.model.GrowthResult
import kotlin.random.Random

/**
 * 成长计算器（T0Y 10 因子月度成长公式实现，T09.1）
 *
 * 严格依据 V0.2 §球员成长公式：10 因子加权评分 × 年龄系数 → CA 月度增量。
 *
 * 10 因子：
 * 1. 训练质量（training_quality）：月均训练质量 0-1
 * 2. 出场时间（playing_time）：minutes_ratio 映射 0-1
 * 3. 导师加成（mentor）：0-0.1 → 归一化 0-1
 * 4. 俱乐部设施（club_facility）：训练质量 0-1
 * 5. 天赋（talent）：(PA-CA)/30 0-1，剩余潜力越大成长越快
 * 6. 年龄（age）：1.0（年龄效应通过 ageBase 系数施加）
 * 7. 伤病（injury）：健康 1.0，受伤 0.2，重伤历史降低
 * 8. 士气（morale）：morale/100 0-1
 * 9. 随机（random）：-0.3 ~ 0.3
 * 10. 国家人才池（national_pool）：bonus/100 0-1
 *
 * 公式：caDelta = ageBase × weightedScore × SCALE_FACTOR
 *
 * 不重写 T0Y 公式，仅做参数映射与调用。本类即为 T0Y 公式的运行时实现。
 *
 * @param config 成长配置
 * @param ageTable 7 档年龄表
 * @param minutesCalculator 出场时间计算器
 */
class GrowthCalculator(
    private val config: GrowthConfig,
    private val ageTable: AgeBasedGrowthTable = AgeBasedGrowthTable,
    private val minutesCalculator: MinutesRatioCalculator = MinutesRatioCalculator(config)
) {

    /**
     * 计算球员月度成长（FULL / ACTIVE 范围完整 10 因子计算）。
     *
     * @param input 聚合输入
     * @return 成长结果（含 CA 变化 + 属性变化 + 10 因子分解）
     */
    fun calculate(input: GrowthInput): GrowthResult {
        val caBefore = input.player.currentCa

        // 1. 计算 10 因子（均归一化到 0-1，除随机因子为 -0.3~0.3）
        val factors = calculateFactors(input)

        // 2. 加权评分（0-1）
        val weightedScore = factors.weightedScore(config).coerceIn(0.0, 1.5)

        // 3. 年龄系数（-0.6 ~ 1.5）
        val ageBase = ageTable.getGrowthFactor(input.growthPhase)

        // 4. CA 月度增量：ageBase × weightedScore × SCALE
        //    SCALE=2.0 使 17 岁满成长因子下 caDelta ≈ 1.3×0.8×2 ≈ 2
        val rawDelta = ageBase * weightedScore * SCALE_FACTOR
        var caDelta = rawDelta.toInt()

        // 5. 衰退期负增量向下取整（-0.6 × 0.8 × 2 = -0.96 → -1）
        if (rawDelta < 0 && caDelta == 0) caDelta = -1

        // 6. 随机停滞保护（每月一定概率不成长，防小妖全部满潜）
        if (caDelta > 0 && shouldRandomStagnate(input)) {
            caDelta = 0
        }

        val caAfter = (caBefore + caDelta).coerceAtLeast(1)

        // 7. 属性变化（CA 增长时分配属性点，衰退期强制身体下降）
        val attributeChanges = calculateAttributeChanges(input, caDelta)

        // 8. 兑现率（简化：由 PotentialRealizationCalculator 单独计算，此处用占位）
        val realizationScore = estimateRealization(input)

        val notes = mutableListOf<String>()
        if (caDelta > 0) notes.add("CA +$caDelta")
        else if (caDelta < 0) notes.add("CA $caDelta（衰退）")
        if (input.activeInjury != null) notes.add("伤病期间成长降权")
        if (caDelta == 0 && input.age < config.declineAgeStart) notes.add("本月无成长")

        return GrowthResult(
            playerId = input.player.playerId,
            caBefore = caBefore,
            caAfter = caAfter,
            caDelta = caDelta,
            attributeChanges = attributeChanges,
            factors = factors,
            realizationScore = realizationScore,
            notes = notes
        )
    }

    /**
     * LIGHT 范围简化计算（仅 CA 微调，不计算属性细节）。
     *
     * 性能优先：单球员 ≤2ms。
     */
    fun calculateLight(input: GrowthInput): GrowthResult {
        val caBefore = input.player.currentCa
        val ageBase = ageTable.getGrowthFactor(input.growthPhase)

        val minutesRatio = input.monthlyPlayingTime?.minutesRatio ?: 0.0
        val playingFactor = minutesCalculator.mapRatioToFactor(minutesRatio)

        val caDelta = (ageBase * playingFactor * config.lightGrowthMultiplier).toInt()
        val caAfter = (caBefore + caDelta).coerceIn(1, input.player.currentPa)

        return GrowthResult(
            playerId = input.player.playerId,
            caBefore = caBefore,
            caAfter = caAfter,
            caDelta = caDelta,
            attributeChanges = emptyMap(),
            factors = GrowthFactorBreakdown(
                trainingQuality = 0.0,
                playingTime = playingFactor,
                mentor = 0.0,
                clubFacility = 0.0,
                talent = 0.0,
                age = ageBase,
                injury = 1.0,
                morale = 0.5,
                random = 0.0,
                nationalPool = 0.5
            ),
            realizationScore = 0.5,
            notes = listOf("外租月度汇总（LIGHT）")
        )
    }

    // ==================== 10 因子计算 ====================

    /** 计算 10 因子分解 */
    private fun calculateFactors(input: GrowthInput): GrowthFactorBreakdown {
        // 1. 训练质量（0-1）
        val trainingQuality = input.monthlyTraining?.trainingQualityScore
            ?: config.activeDefaultTacticalFit

        // 2. 出场时间（minutes_ratio 映射 0-1）
        val minutesRatio = input.monthlyPlayingTime?.minutesRatio ?: 0.0
        val playingTime = minutesCalculator.mapRatioToFactor(minutesRatio)

        // 3. 导师加成（0-0.1 → 归一化 0-1）
        val mentor = (input.mentorEffect / 0.1).coerceIn(0.0, 1.0)

        // 4. 俱乐部设施（0-1）
        val clubFacility = input.clubTrainingQuality

        // 5. 天赋（剩余潜力，PA-CA 越大成长越快）
        val talentGap = (input.player.currentPa - input.player.currentCa).coerceAtLeast(0)
        val talent = (talentGap / 30.0).coerceIn(0.0, 1.0)

        // 6. 年龄（年龄效应通过 ageBase 施加，此处用阶段序数 0-1）
        val age = input.growthPhase.ordinal.toDouble() / 6.0

        // 7. 伤病（健康 1.0，受伤 0.2，重伤历史降低）
        val injury = calculateInjuryFactor(input)

        // 8. 士气（0-1）
        val morale = (input.moraleValue / 100.0).coerceIn(0.0, 1.0)

        // 9. 随机（-0.3 ~ 0.3）
        val random = Random.nextDouble(-0.3, 0.3)

        // 10. 国家人才池（0-1）
        val nationalPool = (input.nationTalentPoolBonus / 100.0).coerceIn(0.0, 1.0)

        return GrowthFactorBreakdown(
            trainingQuality = trainingQuality,
            playingTime = playingTime,
            mentor = mentor,
            clubFacility = clubFacility,
            talent = talent,
            age = age,
            injury = injury,
            morale = morale,
            random = random,
            nationalPool = nationalPool
        )
    }

    /** 伤病对成长的影响因子（健康 1.0，受伤 0.2，频繁伤病 0.5）—— T08 集成 */
    private fun calculateInjuryFactor(input: GrowthInput): Double {
        // 当前活跃伤病：成长大幅降权
        if (input.activeInjury != null) {
            return when (input.activeInjury.severity) {
                4 -> 0.0   // 职业威胁伤：完全不成长
                3 -> 0.1   // 重伤：几乎不成长
                2 -> 0.2   // 中度伤：二成成长
                else -> 0.5 // 轻伤：五成成长
            }
        }
        // 频繁伤病历史降低成长
        val recentCount = input.injuryHistory.size
        return when {
            recentCount == 0 -> 1.0
            recentCount <= 2 -> 0.85
            recentCount <= 4 -> 0.7
            else -> 0.5
        }
    }

    /**
     * 计算属性变化。
     * - CA 增长时：按训练侧重分配 1-2 点属性
     * - 衰退期（32+）：强制 pace/acceleration 下降
     */
    private fun calculateAttributeChanges(input: GrowthInput, caDelta: Int): Map<String, Int> {
        val changes = mutableMapOf<String, Int>()

        // CA 增长时按训练侧重分配属性点
        if (caDelta > 0) {
            val focus = input.monthlyTraining?.trainingFocus ?: "BALANCED"
            val points = if (caDelta >= 2) 2 else 1
            when (focus) {
                "SHOOTING" -> {
                    changes["shooting"] = (changes["shooting"] ?: 0) + points
                    changes["finishing"] = (changes["finishing"] ?: 0) + 1
                }
                "PASSING" -> {
                    changes["passing"] = (changes["passing"] ?: 0) + points
                    changes["vision"] = (changes["vision"] ?: 0) + 1
                }
                "FITNESS" -> {
                    changes["stamina"] = (changes["stamina"] ?: 0) + points
                    changes["strength"] = (changes["strength"] ?: 0) + 1
                }
                "DEFENDING" -> {
                    changes["defending"] = (changes["defending"] ?: 0) + points
                    changes["tackling"] = (changes["tackling"] ?: 0) + 1
                }
                else -> {
                    // BALANCED：均匀分配到弱项
                    changes["stamina"] = (changes["stamina"] ?: 0) + 1
                }
            }
        }

        // 衰退期强制身体属性下降（32+）
        if (input.age >= config.forceDeclineAgeStart) {
            changes["pace"] = (changes["pace"] ?: 0) - config.forceDeclinePaceDelta
            changes["acceleration"] = (changes["acceleration"] ?: 0) - config.forceDeclineAccDelta
        }

        return changes.filter { it.value != 0 }
    }

    /** 随机停滞判定（防小妖全部满潜） */
    private fun shouldRandomStagnate(input: GrowthInput): Boolean {
        // 衰退期不触发停滞（衰退是自然的）
        if (input.age >= config.declineAgeStart) return false
        // 已达潜力 90% 不触发
        if (input.player.currentCa >= input.player.currentPa * config.potentialFulfilledRatio) return false
        return Random.nextDouble() < config.randomStagnationProbability
    }

    /** 简易兑现率估算（精确计算由 PotentialRealizationCalculator 完成） */
    private fun estimateRealization(input: GrowthInput): Double {
        val professionalism = input.attributes.professionalism / 100.0
        val playingTime = input.monthlyPlayingTime?.minutesRatio ?: 0.0
        return ((professionalism * 0.5 + playingTime * 0.5)).coerceIn(0.0, 1.0)
    }

    companion object {
        /** CA 增量缩放系数（调参使 17 岁满成长因子下 caDelta ≈ 2） */
        private const val SCALE_FACTOR = 2.0
    }
}
