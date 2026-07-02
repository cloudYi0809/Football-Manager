package com.greendynasty.football.economy.league

import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.config.GrowthType
import com.greendynasty.football.economy.config.LeagueBaseData

/**
 * V0.2 §三 9 大联赛商业系数固定表（回退用）。
 *
 * 严格依据 V0.2 `07_经济通胀_身价_工资模型.md` §三：
 *
 * | 联赛 | 基础系数 | 增长类型 | 特征 |
 * |------|---------|---------|------|
 * | EPL | 1.10 | FAST | 后续增长最快 |
 * | LaLiga | 1.00 | TOP_HEAVY | 豪门集中 |
 * | SerieA | 1.05 | DECLINING | 2000 前后强，后续可能下滑 |
 * | Bundesliga | 0.85 | STABLE | 稳定、工资更克制 |
 * | Ligue1 | 0.70 | CAPITAL_EVENT | 后续资本事件可提升 |
 * | Eredivisie | 0.45 | EXPORT | 青训出口 |
 * | PrimeiraLiga | 0.40 | SELLING | 黑店体系 |
 * | Brasileirao | 0.35 | TALENT_EXPORT | 输出天才 |
 * | Argentino | 0.30 | TALENT_EXPORT | 输出天才 |
 *
 * 增长曲线（V0.2 §三）：
 * - FAST：1.0 + (year - 2002) × 0.02（年增 2%）
 * - TOP_HEAVY：1.0 + (year - 2002) × 0.015
 * - DECLINING：2005 前 = 1.05；2005 后 = 1.05 - (year - 2005) × 0.01
 * - STABLE：1.0 + (year - 2002) × 0.01
 * - CAPITAL_EVENT：2011 前 = 1.0；2011 后 = 1.0 + (year - 2011) × 0.03
 * - EXPORT / SELLING：1.0 + (year - 2002) × 0.005
 * - TALENT_EXPORT：1.0（持平）
 *
 * 与 DB 表 league_economy_profile 的关系：
 * - DB 表存储 baseMultiplier + growthRate（粗粒度）
 * - 固定表额外提供 growthType（细粒度曲线），用于 DB 缺失时回退
 * - DB 优先；DB 缺失时使用固定表
 *
 * @property config 经济配置
 */
class LeagueEconomyTable(private val config: EconomyConfig = EconomyConfig.DEFAULT) {

    /**
     * 获取联赛基础系数。
     *
     * @param leagueId 联赛标识（如 "EPL"）
     * @return 基础系数（未配置联赛返回 [LeagueEconomyParams.defaultBaseMultiplier]）
     */
    fun getBaseMultiplier(leagueId: String): Double =
        config.leagueEconomy.leagueData[leagueId]?.baseMultiplier
            ?: config.leagueEconomy.defaultBaseMultiplier

    /**
     * 获取联赛增长类型。
     *
     * @param leagueId 联赛标识
     * @return 增长类型（未配置联赛返回 STABLE）
     */
    fun getGrowthType(leagueId: String): GrowthType =
        config.leagueEconomy.leagueData[leagueId]?.growthType ?: GrowthType.STABLE

    /**
     * 获取联赛增长曲线函数（年份 → 增长系数）。
     *
     * @param leagueId 联赛标识
     * @return 增长曲线函数
     */
    fun getGrowthCurve(leagueId: String): (Int) -> Double {
        val data: LeagueBaseData? = config.leagueEconomy.leagueData[leagueId]
        return when (data?.growthType) {
            GrowthType.FAST -> { year -> 1.0 + (year - 2002) * 0.02 }
            GrowthType.TOP_HEAVY -> { year -> 1.0 + (year - 2002) * 0.015 }
            GrowthType.DECLINING -> { year ->
                if (year < 2005) 1.05
                else (1.05 - (year - 2005) * 0.01).coerceAtLeast(0.50)
            }
            GrowthType.STABLE -> { year -> 1.0 + (year - 2002) * 0.01 }
            GrowthType.CAPITAL_EVENT -> { year ->
                if (year < 2011) 1.0
                else 1.0 + (year - 2011) * 0.03
            }
            GrowthType.EXPORT -> { year -> 1.0 + (year - 2002) * 0.005 }
            GrowthType.SELLING -> { year -> 1.0 + (year - 2002) * 0.005 }
            GrowthType.TALENT_EXPORT -> { _ -> 1.0 }
            null -> { _ -> 1.0 }
        }
    }

    /**
     * 获取联赛名称（用于 UI 展示）。
     */
    fun getLeagueName(leagueId: String): String = when (leagueId) {
        "EPL" -> "英超"
        "LaLiga" -> "西甲"
        "SerieA" -> "意甲"
        "Bundesliga" -> "德甲"
        "Ligue1" -> "法甲"
        "Eredivisie" -> "荷甲"
        "PrimeiraLiga" -> "葡超"
        "Brasileirao" -> "巴甲"
        "Argentino" -> "阿甲"
        else -> leagueId
    }
}
