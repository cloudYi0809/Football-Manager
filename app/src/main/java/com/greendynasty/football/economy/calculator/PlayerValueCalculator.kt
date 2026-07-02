package com.greendynasty.football.economy.calculator

import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.index.EconomyIndexService
import com.greendynasty.football.economy.league.LeagueEconomyService
import com.greendynasty.football.economy.model.PlayerValuation
import com.greendynasty.football.economy.model.ValuationBreakdown
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import kotlin.math.pow
import java.time.LocalDate
import java.time.Period

/**
 * T17 球员身价计算器（V0.2 §五-§八 6 因子乘积模型）。
 *
 * 任务约束：身价 = base_value × 通胀指数 × 联赛系数 × 年龄系数 × CA 系数 × 合同系数
 *
 * 完整公式（V0.2 §五）：
 * ```
 * base_value = ability_value_curve(CA)
 *            × potential_multiplier(PA, age)
 *            × age_multiplier(age, PA)
 *            × position_multiplier(position)
 *            × reputation_multiplier(reputation)
 *
 * currentValue = base_value
 *              × league_visibility_multiplier(league, year)   // 联赛系数
 *              × economy_index(year)                          // 通胀指数
 *              × contract_multiplier(remaining)               // 合同系数
 *              × performance_multiplier(player)               // 表现修正
 * ```
 *
 * 6 主因子（任务约束）：
 * 1. CA 系数（ability_value_curve + potential_multiplier）
 * 2. 年龄系数（age_multiplier）
 * 3. 合同系数（contract_multiplier）
 * 4. 联赛系数（league_visibility_multiplier）
 * 5. 通胀指数（economy_index）
 * 6. base_value 内的复合修正（position × reputation）
 *
 * 表现修正（performance_multiplier）作为 currentValue 的调整项，不计入 6 主因子。
 *
 * 与 T11 [com.greendynasty.football.transfer.negotiation.estimator.PlayerValueEstimator] 的关系：
 * - T11 侧重"卖方心理价位"（importance × contract × potential 4 因子修正）
 * - T17 侧重"市场公允身价"（6 因子乘积，更完整）
 * - 任务约束："复用 T11 PlayerValueEstimator 的身价估算逻辑，若有冲突 T17 作为权威实现"
 * - 故 T17 公式为权威，T11 在其 base_value 上做心理价位修正（T11 内部已调用 T10 EconomyEstimator 计算 base_value）
 *
 * @property indexService 时代通胀服务
 * @property leagueService 联赛商业服务
 * @property config 经济配置
 */
class PlayerValueCalculator(
    private val indexService: EconomyIndexService,
    private val leagueService: LeagueEconomyService,
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    /**
     * 计算球员完整身价估值（V0.2 §五-§八 6 因子乘积 + 表现修正）。
     *
     * @param player 球员存档状态（含 CA/PA/合同/角色等）
     * @param birthDate 出生日期（ISO yyyy-MM-dd，用于计算年龄）
     * @param primaryPosition 主要位置（如 "ST" / "CM" / "GK"）
     * @param reputation 球员声望 0-100（V1 简化：从 CA 推导或外部传入，0-100）
     * @param clubLeagueId 所属俱乐部联赛标识（如 "EPL"）
     * @param currentDate 当前游戏日期
     * @param currentYear 当前年份
     * @param expectedWage 期望周薪（由 WageCalculator 计算，避免循环依赖）
     * @return 球员身价估值
     */
    suspend fun calculate(
        player: SavePlayerStateEntity,
        birthDate: String?,
        primaryPosition: String?,
        reputation: Int,
        clubLeagueId: String,
        currentDate: LocalDate,
        currentYear: Int,
        expectedWage: Int
    ): PlayerValuation {
        val ca = player.currentCa
        val pa = player.currentPa
        val age = computeAge(birthDate, currentDate)

        // 1. CA 价值曲线（V0.2 §五 ability_value_curve）
        val abilityValue = abilityValueCurve(ca)

        // 2. 潜力乘数（V0.2 §五 potential_multiplier）
        val potentialMultiplier = calculatePotentialMultiplier(pa, ca, age)

        // 3. 年龄系数（V0.2 §六 age_multiplier）
        val ageMultiplier = calculateAgeMultiplier(age, pa)

        // 4. 位置系数（V0.2 §五 position_multiplier）
        val positionMultiplier = config.positionMultiplier[primaryPosition] ?: 1.0

        // 5. 声望系数（V0.2 §五 reputation_multiplier）
        val reputationMultiplier = calculateReputationMultiplier(reputation)

        // 6. 联赛可见度（V0.2 §五 league_visibility_multiplier = 联赛商业系数）
        val leagueVisibilityMultiplier = leagueService.getMultiplier(clubLeagueId, currentYear)

        // 7. 时代系数（V0.2 §五 economy_index）
        val economyIndex = indexService.getIndex(currentYear)

        // 8. 合同剩余影响（V0.2 §七 contract_multiplier）
        val contractMultiplier = calculateContractMultiplier(player.contractUntil, currentDate)

        // base_value（6 主因子的前 5 个 + 复合修正）
        val baseValue = (abilityValue *
            potentialMultiplier *
            ageMultiplier *
            positionMultiplier *
            reputationMultiplier *
            leagueVisibilityMultiplier *
            economyIndex *
            contractMultiplier).toInt().coerceAtLeast(50_000)

        // 9. 表现修正（V0.2 §八 performance_multiplier，V1 简化默认 1.0）
        val performanceMultiplier = calculatePerformanceMultiplier(player)
        val currentValue = (baseValue * performanceMultiplier).toInt().coerceAtLeast(50_000)

        return PlayerValuation(
            playerId = player.playerId,
            baseValue = baseValue,
            currentValue = currentValue,
            expectedWage = expectedWage,
            breakdown = ValuationBreakdown(
                abilityValue = abilityValue,
                ageMultiplier = ageMultiplier,
                contractMultiplier = contractMultiplier,
                leagueVisibilityMultiplier = leagueVisibilityMultiplier,
                economyIndex = economyIndex,
                positionMultiplier = positionMultiplier,
                reputationMultiplier = reputationMultiplier,
                potentialMultiplier = potentialMultiplier,
                performanceMultiplier = performanceMultiplier
            )
        )
    }

    /**
     * V0.2 §五 CA 价值曲线。
     *
     * `ability_value_curve = base_amount * pow(growthRate, CA - 50)`
     *
     * CA=50 → base_amount（2002 基准 50 万）
     * CA=80 → 50万 × 1.075^30 ≈ 476万
     * CA=100 → 50万 × 1.075^50 ≈ 2059万
     */
    fun abilityValueCurve(ca: Int): Double {
        val normalizedCa = ca.coerceIn(0, 200)
        val params = config.abilityCurve
        return params.baseAmount * params.growthRate.pow(normalizedCa - 50)
    }

    /**
     * V0.2 §五 潜力乘数（年轻球员 PA-CA 差距价值）。
     *
     * - U21 且 gap > 15：1.0 + gap × 0.02（最高约 +60%）
     * - U24 且 gap > 10：1.0 + gap × 0.01
     * - 其他：1.0
     */
    fun calculatePotentialMultiplier(pa: Int, ca: Int, age: Int): Double {
        val potentialGap = (pa - ca).coerceAtLeast(0)
        return when {
            age <= 21 && potentialGap > 15 -> 1.0 + potentialGap * 0.02
            age <= 24 && potentialGap > 10 -> 1.0 + potentialGap * 0.01
            else -> 1.0
        }
    }

    /**
     * V0.2 §六 年龄系数。
     *
     * | 年龄 | 系数区间 |
     * |------|---------|
     * | 16-18 | 0.60-1.20，受潜力影响大 |
     * | 19-22 | 1.10-1.50 |
     * | 23-27 | 1.20-1.60 |
     * | 28-30 | 1.00-1.30 |
     * | 31-33 | 0.65-0.95 |
     * | 34+   | 0.20-0.60 |
     */
    fun calculateAgeMultiplier(age: Int, potentialPa: Int): Double {
        val params = config.ageMultiplier
        val raw = when (age) {
            in 16..18 -> {
                if (potentialPa > params.u19HighPotentialThreshold) {
                    params.u19HighPotentialValue
                } else {
                    params.u18BaseValue + (potentialPa - 50) * params.u18PotentialSlope
                }
            }
            in 19..22 -> 1.10 + (potentialPa - 50) * 0.008
            in 23..27 -> 1.20 + (potentialPa - 50) * 0.008
            in 28..30 -> 1.00 + (potentialPa - 50) * 0.006
            in 31..33 -> 0.65 + (potentialPa - 50) * 0.006
            else -> {
                if (age <= 36) 0.20 + (potentialPa - 50) * 0.008
                else 0.15
            }
        }
        return raw.coerceIn(params.minValue, params.maxValue)
    }

    /**
     * V0.2 §五 声望系数。
     *
     * `reputation_multiplier = 0.5 + reputation / 100.0`（0.5 - 1.5）
     */
    fun calculateReputationMultiplier(reputation: Int): Double {
        val normalized = reputation.coerceIn(0, 100)
        return 0.5 + normalized / 100.0
    }

    /**
     * V0.2 §七 合同剩余系数。
     *
     * | 剩余 | 系数 |
     * |------|------|
     * | ≥ 4 年 | 1.20 |
     * | 3 年 | 1.10 |
     * | 2 年 | 1.00 |
     * | 1 年 | 0.70 |
     * | 6 个月 | 0.40 |
     * | 自由球员 | 0.0 |
     */
    fun calculateContractMultiplier(contractUntil: String?, currentDate: LocalDate): Double {
        if (contractUntil.isNullOrBlank()) return config.contractMultiplier.freeAgent

        val endDate = runCatching { LocalDate.parse(contractUntil.take(10)) }.getOrNull()
            ?: return config.contractMultiplier.freeAgent
        if (endDate.isBefore(currentDate)) return config.contractMultiplier.freeAgent

        val period = Period.between(currentDate, endDate)
        val yearsRemaining = period.years.toDouble() + period.months.toDouble() / 12.0
        val params = config.contractMultiplier
        return when {
            yearsRemaining >= 4 -> params.years4Plus
            yearsRemaining >= 3 -> params.years3
            yearsRemaining >= 2 -> params.years2
            yearsRemaining >= 1 -> params.years1
            yearsRemaining >= 0.5 -> params.halfYear
            else -> params.freeAgent
        }
    }

    /**
     * V0.2 §八 表现修正（V1 简化）。
     *
     * V1 默认 1.0（无表现数据），V2 可接入近 5 场 form / 赛季 stats / 奖项 / 国家队 / 伤病等 6 子项。
     *
     * 限制范围：0.65 ≤ performance_multiplier ≤ 1.60
     */
    fun calculatePerformanceMultiplier(@Suppress("UNUSED_PARAMETER") player: SavePlayerStateEntity): Double {
        return config.performanceMultiplier.defaultValue
    }

    /** 计算球员年龄 */
    private fun computeAge(birthDate: String?, currentDate: LocalDate): Int {
        if (birthDate.isNullOrBlank()) return 18
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrDefault(18)
    }
}
