package com.greendynasty.football.economy.calculator

import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.index.EconomyIndexService
import com.greendynasty.football.economy.league.LeagueEconomyService
import com.greendynasty.football.economy.model.WageBreakdown
import com.greendynasty.football.transfer.contract.config.ContractRenewalConfig
import com.greendynasty.football.transfer.contract.wage.WageCalculator as T12WageCalculator
import kotlin.math.pow

/**
 * T17 工资计算器（V0.2 §九 6 因子乘积）。
 *
 * 任务约束："复用 T12 [WageCalculator] 的工资公式，T17 提供经济系数输入"
 *
 * 工资公式（V0.2 §九）：
 * ```
 * expected_wage = wage_base_by_CA(CA)
 *               × club_reputation_factor      // 俱乐部声望系数
 *               × league_wage_multiplier       // 联赛系数（T17 联赛商业系数）
 *               × economy_index                // 经济指数系数（T17 提供）
 *               × agent_greed_multiplier       // 经纪人贪婪系数
 *               × squad_role_factor            // 队内角色系数
 * ```
 *
 * 与 T12 [com.greendynasty.football.transfer.contract.wage.WageCalculator] 的关系：
 * - T12 5 因子：wage_base × 声望 × 联赛 × 经济 × 角色（经济指数硬编码分段表）
 * - T17 6 因子：在 T12 基础上拆出 agent_greed_multiplier（V0.2 §九 原始公式 6 因子）
 * - T17 经济系数由 [EconomyIndexService] 提供（优先读 save.db，支持数据包覆盖）
 * - T17 联赛系数由 [LeagueEconomyService] 提供（含增长曲线 + 通胀归一化）
 * - T17 复用 T12 的 [T12WageCalculator.wageBaseByCa] 计算 wage_base（确保 2002 基准一致）
 *
 * 协程规范：所有 DB 查询使用 suspend。
 *
 * @property indexService 时代通胀服务（提供 economy_index）
 * @property leagueService 联赛商业服务（提供 league_wage_multiplier）
 * @property t12Calculator T12 工资计算器（复用 wageBaseByCa）
 * @property config 经济配置
 */
class WageCalculator(
    private val indexService: EconomyIndexService,
    private val leagueService: LeagueEconomyService,
    private val t12Calculator: T12WageCalculator = T12WageCalculator(ContractRenewalConfig.DEFAULT),
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    /**
     * 计算球员期望周薪（V0.2 §九 6 因子乘积）。
     *
     * @param player 球员存档状态（含 CA / 队内角色）
     * @param clubReputation 俱乐部声望 0-100
     * @param clubLeagueId 俱乐部所在联赛标识（如 "EPL"）
     * @param currentYear 当前年份
     * @param agentId 经纪人 ID（可选，用于推导贪婪系数）
     * @return 工资计算明细
     */
    suspend fun calculate(
        player: SavePlayerStateEntity,
        clubReputation: Int,
        clubLeagueId: String,
        currentYear: Int,
        agentId: String? = null
    ): WageBreakdown {
        val ca = player.currentCa.coerceIn(0, 200)

        // 1. wage_base_by_CA（复用 T12 WageCalculator.wageBaseByCa）
        val wageBase = t12Calculator.wageBaseByCa(ca)

        // 2. 俱乐部声望系数（与 T12 WageParams.clubReputationFactorFor 对齐）
        val clubReputationFactor = config.wage.clubReputationFactorFor(clubReputation)

        // 3. 联赛工资系数（T17 联赛商业系数，含增长曲线 + 通胀归一化）
        val leagueEconomyMultiplier = leagueService.getMultiplier(clubLeagueId, currentYear)

        // 4. 经济指数系数（T17 提供，优先读 save.db economy_index 表）
        val economyFactor = indexService.getIndex(currentYear)

        // 5. 经纪人贪婪系数（V1 简化：1.0-1.15）
        val agentGreedMultiplier = calculateAgentGreed(agentId)

        // 6. 队内角色系数（与 T12 WageParams.squadRoleFactor 对齐）
        val squadRole = player.squadRole ?: "starter"
        val squadRoleFactor = config.wage.squadRoleFactor[squadRole]
            ?: config.wage.defaultSquadRoleFactor

        // 6 因子乘积
        val wage = wageBase *
            clubReputationFactor *
            leagueEconomyMultiplier *
            economyFactor *
            agentGreedMultiplier *
            squadRoleFactor
        val finalWage = wage.toInt().coerceAtLeast(config.wage.minWage)

        return WageBreakdown(
            expectedWage = finalWage,
            wageBase = wageBase,
            clubReputationFactor = clubReputationFactor,
            leagueEconomyMultiplier = leagueEconomyMultiplier,
            economyFactor = economyFactor,
            agentGreedMultiplier = agentGreedMultiplier,
            squadRoleFactor = squadRoleFactor
        )
    }

    /**
     * 经纪人贪婪系数（V0.2 §九 agent_greed_multiplier）。
     *
     * V1 简化：基于 agentId 的 hashCode 推导 1.0-1.15 之间的稳定值，
     * 同一经纪人始终返回相同系数（避免随机抖动）。
     */
    private fun calculateAgentGreed(agentId: String?): Double {
        if (agentId.isNullOrBlank()) return config.wage.defaultAgentGreed
        val hashMod = (agentId.hashCode().rem(15) + 15) % 15 // 0-14
        return 1.0 + hashMod / 100.0 // 1.00 - 1.14
    }
}
