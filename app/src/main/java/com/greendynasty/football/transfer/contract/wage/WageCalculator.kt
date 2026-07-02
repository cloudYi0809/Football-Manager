package com.greendynasty.football.transfer.contract.wage

import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.contract.config.ContractRenewalConfig
import kotlin.math.pow

/**
 * T12.1 工资计算器（V0.2 `07_经济通胀_身价_工资模型.md` §九）。
 *
 * 严格依据 V0.2 §九 5 因子乘积公式：
 * ```
 * expected_wage = wage_base_by_CA(CA)
 *               × club_reputation_factor      // 俱乐部声望系数
 *               × league_factor                // 联赛系数
 *               × economy_factor               // 经济指数系数
 *               × squad_role_factor            // 队内角色系数
 * ```
 *
 * 与 T10 [com.greendynasty.football.transfer.search.EconomyEstimator.estimateExpectedWage] 的关系：
 * - T10 经济估算器用于"新签约球员"的工资期望（侧重 CA × 角色 × 位置 × 经济指数）
 * - T12.1 工资计算器用于"续约"场景，强调俱乐部声望 × 联赛吸引力 × 经济指数 × 队内角色
 *
 * 严格按 V0.2 算法文档实现，只调参不改架构，所有参数配置化（铁律）。
 *
 * @property config 工资计算配置
 */
class WageCalculator(
    private val config: ContractRenewalConfig = ContractRenewalConfig.DEFAULT
) {

    /**
     * 计算球员续约时的期望周薪（V0.2 §九 5 因子乘积）。
     *
     * @param player 球员存档状态（含 CA / 队内角色）
     * @param clubEntity 俱乐部基础信息（含声望 / 国家）
     * @param saveClubState 俱乐部存档状态（含声望）
     * @param leagueId 俱乐部所在联赛 ID（如 "EPL"）
     * @param currentYear 当前年份（用于经济指数）
     * @return 期望周薪（整数，最低 1000）
     */
    fun calculateExpectedWage(
        player: SavePlayerStateEntity,
        clubEntity: ClubEntity,
        saveClubState: SaveClubStateEntity?,
        leagueId: String?,
        currentYear: Int
    ): WageBreakdown {
        // 1. wage_base_by_CA：CA=50 时基准周薪，按指数曲线增长
        val ca = player.currentCa.coerceIn(0, 200)
        val wageBase = wageBaseByCa(ca)

        // 2. 俱乐部声望系数（优先用存档声望，回退到历史库声望）
        val reputation = saveClubState?.reputation ?: clubEntity.reputation
        val clubReputationFactor = config.wage.clubReputationFactorFor(reputation)

        // 3. 联赛系数
        val leagueFactor = leagueId?.let { config.wage.leagueFactor[it] }
            ?: config.wage.defaultLeagueFactor

        // 4. 经济指数系数（与 T10 EconomyEstimator.economyIndex 对齐）
        val economyFactor = economyIndex(currentYear)

        // 5. 队内角色系数
        val squadRole = player.squadRole ?: "starter"
        val squadRoleFactor = config.wage.squadRoleFactor[squadRole]
            ?: config.wage.defaultSquadRoleFactor

        // 5 因子乘积
        val wage = wageBase * clubReputationFactor * leagueFactor * economyFactor * squadRoleFactor
        val finalWage = wage.toInt().coerceAtLeast(1_000)

        return WageBreakdown(
            expectedWage = finalWage,
            wageBase = wageBase,
            clubReputationFactor = clubReputationFactor,
            leagueFactor = leagueFactor,
            economyFactor = economyFactor,
            squadRoleFactor = squadRoleFactor,
            reputation = reputation,
            leagueId = leagueId ?: "UNKNOWN",
            squadRole = squadRole
        )
    }

    /**
     * CA=50 时的基准周薪，按指数曲线增长（V0.2 §九）。
     *
     * `wage_base = wageBaseAmount × pow(abilityGrowthRate, CA - 50)`
     */
    fun wageBaseByCa(ca: Int): Double {
        val normalizedCa = ca.coerceIn(0, 200)
        return config.wage.wageBaseAmount * config.wage.abilityGrowthRate.pow(normalizedCa - 50)
    }

    /**
     * 经济指数（V0.2 §二 时代通胀系数，2002 基准 = 1.00）。
     *
     * 与 [com.greendynasty.football.transfer.search.EconomyEstimator.economyIndex] 完全对齐，
     * 确保续约工资与身价估算使用同一套经济指数。
     */
    fun economyIndex(year: Int): Double {
        val baseYear = 2002
        return when {
            year <= 1992 -> 0.35
            year in 1993..1994 -> 0.40
            year in 1995..1997 -> 0.55
            year in 1998..2001 -> 0.80
            year == baseYear -> config.wage.economyIndexBase
            year in 2003..2005 -> 1.10
            year in 2006..2009 -> 1.40
            year in 2010..2013 -> 1.90
            year in 2014..2016 -> 2.70
            year in 2017..2019 -> 3.40
            year in 2020..2023 -> 3.90
            year in 2024..2029 -> 4.50
            year == 2030 -> 5.00
            else -> {
                val yearsAfter2030 = year - 2030
                5.00 * (1.03).pow(yearsAfter2030)
            }
        }
    }

    /**
     * 计算球员续约时的"要求工资"（在期望工资基础上，按角色重要性 + 合同剩余月数调整）。
     *
     * 简化版续约要求公式：
     * ```
     * demands_wage = expected_wage × importance_multiplier × contract_remain_multiplier
     * ```
     *
     * - 关键球员要求更高工资
     * - 合同剩余少（议价权高）要求更高
     *
     * @param expectedWage 期望工资（来自 [calculateExpectedWage]）
     * @param squadRole 队内角色
     * @param monthsRemaining 合同剩余月数
     * @return 要求工资
     */
    fun calculateDemandsWage(
        expectedWage: Int,
        squadRole: String?,
        monthsRemaining: Int
    ): Int {
        // 重要性修正：关键球员要求更高
        val importanceMultiplier = when (squadRole?.lowercase()) {
            "key_player", "key", "core" -> 1.30
            "starter", "first_team" -> 1.15
            "rotation" -> 1.00
            "backup" -> 0.85
            "prospect", "youth" -> 0.70
            "listed", "transfer_listed" -> 0.65
            else -> 1.00
        }

        // 合同剩余修正：剩少 → 议价权高（可接触其他俱乐部）
        val contractRemainMultiplier = when {
            monthsRemaining <= 6 -> config.negotiation.bargainingPower6m
            monthsRemaining <= 12 -> config.negotiation.bargainingPower12m
            else -> config.negotiation.bargainingPowerLong
        }

        val demands = expectedWage * importanceMultiplier * contractRemainMultiplier
        return demands.toInt().coerceAtLeast(expectedWage)
    }
}

/**
 * 工资计算明细（5 因子分解，用于 UI 展示与单测）。
 */
data class WageBreakdown(
    /** 最终期望周薪 */
    val expectedWage: Int,
    /** wage_base_by_CA */
    val wageBase: Double,
    /** 俱乐部声望系数 */
    val clubReputationFactor: Double,
    /** 联赛系数 */
    val leagueFactor: Double,
    /** 经济指数系数 */
    val economyFactor: Double,
    /** 队内角色系数 */
    val squadRoleFactor: Double,
    /** 俱乐部声望值（0-100） */
    val reputation: Int,
    /** 联赛 ID */
    val leagueId: String,
    /** 队内角色 */
    val squadRole: String
) {
    /** 格式化展示 */
    override fun toString(): String =
        "期望周薪 $expectedWage = base(${wageBase.toInt()}) × rep($clubReputationFactor) × " +
            "league($leagueFactor) × eco($economyFactor) × role($squadRoleFactor)"
}
