package com.greendynasty.football.economy.league

import com.greendynasty.football.data.save.dao.EconomyIndexDao
import com.greendynasty.football.data.save.entity.LeagueEconomyProfileEntity
import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.index.EconomyIndexService
import com.greendynasty.football.economy.index.EconomyIndexTable
import com.greendynasty.football.economy.model.LeagueEconomySnapshot

/**
 * T17 联赛商业系数服务（V0.2 §三）。
 *
 * 职责：
 * 1. 从 save.db 的 league_economy_profile 表读取 9 联赛的商业系数（base_multiplier + growth_rate）
 * 2. 当存档表无对应联赛时，回退到 [LeagueEconomyTable] 固定表
 * 3. 计算"联赛系数 = baseMultiplier × growthCurve(year) × (economyIndex / economyIndex(2002))"
 *
 * V0.2 §三 联赛系数公式：
 * ```
 * league_economy_multiplier = base_multiplier × growth_curve(year) × (economy_index(year) / economy_index(2002))
 * ```
 *
 * 该系数同时用于：
 * - 身价公式第 4 因子（league_visibility_multiplier）
 * - 工资公式第 3 因子（league_wage_multiplier）
 * - 俱乐部财力公式第 2 因子（league_economy）
 *
 * 协程规范：所有 DB 查询使用 suspend。
 *
 * @property dao 经济指数 DAO（含联赛画像 CRUD）
 * @param table 固定表回退
 * @property indexService 时代通胀服务（提供 economyIndex）
 * @property config 经济配置
 */
class LeagueEconomyService(
    private val dao: EconomyIndexDao,
    private val table: LeagueEconomyTable = LeagueEconomyTable(),
    private val indexService: EconomyIndexService,
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    /**
     * 获取联赛商业系数（V0.2 §三 完整公式）。
     *
     * 优先级：
     * 1. save.db league_economy_profile 表（base_multiplier + growth_rate）
     * 2. 固定表回退（base + growthType 曲线）
     *
     * @param leagueId 联赛标识（如 "EPL"）
     * @param year 年份
     * @return 联赛商业系数
     */
    suspend fun getMultiplier(leagueId: String, year: Int): Double {
        val base = getBaseMultiplier(leagueId)
        val growthCurve = getGrowthCurveValue(leagueId, year)
        val economyIndex = indexService.getIndex(year)
        val baseYearIndex = indexService.getBaseYearIndex()
        val economyRatio = if (baseYearIndex > 0) economyIndex / baseYearIndex else 1.0
        return base * growthCurve * economyRatio
    }

    /**
     * 同步版本（不读 DB）：仅使用固定表计算联赛系数。
     */
    fun getMultiplierSync(leagueId: String, year: Int): Double {
        val base = table.getBaseMultiplier(leagueId)
        val growthCurve = table.getGrowthCurve(leagueId)(year)
        val economyIndex = indexService.getIndexSync(year)
        val baseYearIndex = EconomyIndexTable(config).getBaseYearIndex()
        val economyRatio = if (baseYearIndex > 0) economyIndex / baseYearIndex else 1.0
        return base * growthCurve * economyRatio
    }

    /**
     * 获取联赛基础系数。
     */
    suspend fun getBaseMultiplier(leagueId: String): Double {
        // 1. 优先读 DB（league_economy_profile 表的主键 league_id 是 Int，需要做 ID 转换）
        val dbProfile = findDbProfile(leagueId)
        if (dbProfile != null && dbProfile.baseMultiplier > 0.0) {
            return dbProfile.baseMultiplier
        }
        // 2. 回退固定表
        return table.getBaseMultiplier(leagueId)
    }

    /**
     * 获取联赛增长曲线值（指定年份）。
     */
    suspend fun getGrowthCurveValue(leagueId: String, year: Int): Double {
        // 1. 优先用 DB 的 growth_rate（线性增长：1.0 + growthRate × (year - 2002)）
        val dbProfile = findDbProfile(leagueId)
        if (dbProfile != null && dbProfile.growthRate > 0.0) {
            val yearsSince2002 = (year - 2002).coerceAtLeast(0)
            return (1.0 + dbProfile.growthRate * yearsSince2002).coerceAtLeast(0.10)
        }
        // 2. 回退固定表 growthType 曲线
        return table.getGrowthCurve(leagueId)(year)
    }

    /**
     * 获取联赛完整快照（用于 UI 展示）。
     *
     * @param leagueId 联赛标识
     * @param year 年份
     * @return 联赛经济快照
     */
    suspend fun getSnapshot(leagueId: String, year: Int): LeagueEconomySnapshot {
        val dbProfile = findDbProfile(leagueId)
        val base = dbProfile?.baseMultiplier ?: table.getBaseMultiplier(leagueId)
        val growthRate = dbProfile?.growthRate ?: config.leagueEconomy.leagueData[leagueId]
            ?.let { defaultGrowthRateFor(it.growthType) } ?: 0.01
        val growthType = table.getGrowthType(leagueId).name
        val multiplier = getMultiplier(leagueId, year)
        return LeagueEconomySnapshot(
            leagueId = leagueId,
            leagueName = table.getLeagueName(leagueId),
            baseMultiplier = base,
            growthRate = growthRate,
            growthType = growthType,
            multiplier = multiplier,
            source = if (dbProfile != null) "db" else "fixed"
        )
    }

    /**
     * 获取全部 9 联赛快照（用于 UI 列表展示）。
     *
     * @param year 年份
     * @return 联赛经济快照列表（按商业系数降序）
     */
    suspend fun getAllSnapshots(year: Int): List<LeagueEconomySnapshot> {
        return config.leagueEconomy.leagueData.keys.map { leagueId ->
            getSnapshot(leagueId, year)
        }.sortedByDescending { it.multiplier }
    }

    // ==================== 内部工具 ====================

    /** 查找 DB 中的联赛画像（league_economy_profile 表 league_id 是 Int 主键） */
    private suspend fun findDbProfile(leagueId: String): LeagueEconomyProfileEntity? {
        // 联赛标识（"EPL" / "LaLiga" ...）与 DB 主键（Int）的映射：
        // V0.2 设计中 league_id 通常是 Int 编号，但本模块对外用字符串便于阅读。
        // 兼容两种情况：若 leagueId 可解析为 Int，直接查；否则跳过 DB 用固定表回退。
        val numericId = leagueId.toIntOrNull() ?: return null
        return runCatching { dao.getLeagueProfile(numericId) }.getOrNull()
    }

    /** 增长类型 → 默认 growthRate（用于快照展示） */
    private fun defaultGrowthRateFor(type: com.greendynasty.football.economy.config.GrowthType): Double =
        when (type) {
            com.greendynasty.football.economy.config.GrowthType.FAST -> 0.02
            com.greendynasty.football.economy.config.GrowthType.TOP_HEAVY -> 0.015
            com.greendynasty.football.economy.config.GrowthType.DECLINING -> -0.01
            com.greendynasty.football.economy.config.GrowthType.STABLE -> 0.01
            com.greendynasty.football.economy.config.GrowthType.CAPITAL_EVENT -> 0.03
            com.greendynasty.football.economy.config.GrowthType.EXPORT -> 0.005
            com.greendynasty.football.economy.config.GrowthType.SELLING -> 0.005
            com.greendynasty.football.economy.config.GrowthType.TALENT_EXPORT -> 0.0
        }
}
