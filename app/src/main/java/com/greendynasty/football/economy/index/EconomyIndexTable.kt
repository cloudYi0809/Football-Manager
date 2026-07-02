package com.greendynasty.football.economy.index

import com.greendynasty.football.economy.config.EconomyConfig

/**
 * V0.2 §二 时代通胀系数固定表（1992-2030）。
 *
 * 严格依据 V0.2 `07_经济通胀_身价_工资模型.md` §二 固定系数表：
 * - 1992 = 0.35
 * - 1995 = 0.45
 * - 1998 = 0.65
 * - 2002 = 1.00（基准）
 * - 2006 = 1.25
 * - 2010 = 1.60
 * - 2014 = 2.20
 * - 2017 = 3.20
 * - 2020 = 3.60
 * - 2024 = 4.20
 * - 2030 = 5.00
 *
 * 表中未列出的年份采用线性插值。
 *
 * 与 T10 [com.greendynasty.football.transfer.search.EconomyEstimator.economyIndex] 的分段表互为补充：
 * - T10 使用分段常数（粗粒度）
 * - T17 使用线性插值（细粒度，更贴合 V0.2 §二"年代增长曲线"描述）
 * - 两者在节点年份完全一致，T17 作为权威实现（任务约束 §"复用 T11 PlayerValueEstimator ... 若有冲突 T17 作为权威实现"）
 *
 * @property config 经济配置
 */
class EconomyIndexTable(private val config: EconomyConfig = EconomyConfig.DEFAULT) {

    /**
     * 获取指定年份的固定指数（线性插值）。
     *
     * @param year 年份
     * @return 经济指数（2002 = 1.00）
     */
    fun getFixedIndex(year: Int): Double {
        val table = config.economyIndex.fixedTable
        val keys = table.keys.sorted()

        // 早于最早节点：取最早值
        if (year <= keys.first()) return table[keys.first()]!!
        // 晚于最晚节点：取最晚值（2030+ 由 FutureGrowthProjector 处理）
        if (year >= keys.last()) return table[keys.last()]!!

        // 找到 year 所在区间 [lower, upper]
        val lower = keys.last { it <= year }
        val upper = keys.first { it >= year }
        if (lower == upper) return table[lower]!!

        val lowerValue = table[lower]!!
        val upperValue = table[upper]!!
        val ratio = (year - lower).toDouble() / (upper - lower)
        return lowerValue + (upperValue - lowerValue) * ratio
    }

    /**
     * 获取基准年份指数（2002 = 1.00），用于联赛系数归一化。
     */
    fun getBaseYearIndex(): Double =
        config.economyIndex.fixedTable[config.economyIndex.baseYear] ?: config.economyIndex.baseIndex
}
