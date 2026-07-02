package com.greendynasty.football.economy.calculator

import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.league.LeagueEconomyService
import com.greendynasty.football.economy.model.ClubFinancialState

/**
 * T17 俱乐部财力计算器（V0.2 §四 6 因子加权）。
 *
 * 严格依据 V0.2 §四 公式：
 * ```
 * club_financial_power =
 *   club_reputation       × 0.25
 * + league_economy        × 0.25
 * + stadium_income        × 0.15
 * + commercial_income     × 0.15
 * + owner_investment      × 0.15
 * + recent_success        × 0.05
 * ```
 *
 * 结果归一化到 0-100 整数，用于 T18 AI 转会决策（财力越高买人意愿越强）。
 *
 * @property leagueService 联赛商业服务
 * @property config 经济配置
 */
class ClubFinancialPowerCalculator(
    private val leagueService: LeagueEconomyService,
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    /**
     * 计算俱乐部财力分值（0-100）。
     *
     * @param club 俱乐部基础信息（含声望 / 国家）
     * @param financial 俱乐部财政状态
     * @param leagueId 联赛标识
     * @param year 年份
     * @return 财力分值（0-100）
     */
    suspend fun calculate(
        club: ClubEntity,
        financial: ClubFinancialState,
        leagueId: String,
        year: Int
    ): Int {
        // 1. 俱乐部声望（归一化 0-1）
        val clubReputation = club.reputation.coerceIn(0, 100) / 100.0

        // 2. 联赛经济系数（归一化：英超 2020 ≈ 1.10 × 增长 × 通胀 / 1.0，约 4，除以 2.0 折算 0-1）
        val leagueEconomyRaw = leagueService.getMultiplier(leagueId, year)
        val leagueEconomy = (leagueEconomyRaw / 2.0).coerceIn(0.0, 1.0)

        // 3. 门票收入（归一化：5000 万满分）
        val stadiumIncome = normalizeIncome(financial.stadiumIncome)

        // 4. 商业收入（归一化：5000 万满分）
        val commercialIncome = normalizeIncome(financial.commercialIncome)

        // 5. 老板投入（归一化：5000 万满分）
        val ownerInvestment = normalizeIncome(financial.ownerInvestment)

        // 6. 近 3 年战绩（已是 0-1）
        val recentSuccess = financial.recentSuccess.coerceIn(0.0, 1.0)

        val w = config.financialPower
        val power = (
            clubReputation * w.reputation +
                leagueEconomy * w.leagueEconomy +
                stadiumIncome * w.stadiumIncome +
                commercialIncome * w.commercialIncome +
                ownerInvestment * w.ownerInvestment +
                recentSuccess * w.recentSuccess
            ) * 100

        return power.toInt().coerceIn(0, 100)
    }

    /**
     * 收入归一化：5000 万为满分 1.0。
     */
    private fun normalizeIncome(income: Int): Double {
        return (income / 50_000_000.0).coerceIn(0.0, 1.0)
    }
}
