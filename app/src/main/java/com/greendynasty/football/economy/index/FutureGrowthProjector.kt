package com.greendynasty.football.economy.index

import com.greendynasty.football.economy.config.EconomyConfig
import kotlin.math.pow

/**
 * V0.2 §二 2030 后架空增长投影器。
 *
 * 严格依据 V0.2 §二："2030 后进入架空增长，默认年增长 2%-4%，可受全球经济事件影响"。
 *
 * V1 简化（铁律 §十五 V1 范围）：
 * - 固定 3% 年化增长（[EconomyIndexParams.futureGrowthBaseRate]）
 * - 不接入动态全球经济事件（推迟至 V2）
 *
 * 红线（§十三.1）：2030 年顶级转会费必须在 1-3 亿区间，故 3% 增长率不可过高。
 *
 * @property config 经济配置
 */
class FutureGrowthProjector(private val config: EconomyConfig = EconomyConfig.DEFAULT) {

    /**
     * 从 fromYear 推导至 toYear 的经济指数。
     *
     * @param fromYear 起始年份（通常为 2030）
     * @param toYear 目标年份（> fromYear）
     * @return 目标年份的经济指数
     */
    fun project(fromYear: Int, toYear: Int): Double {
        val baseIndex = config.economyIndex.year2030Index
        if (toYear <= fromYear) return baseIndex

        val growthRate = config.economyIndex.futureGrowthBaseRate
        val years = toYear - fromYear
        return baseIndex * (1.0 + growthRate).pow(years)
    }

    /**
     * V2 钩子：未来可加入全球经济事件调整（如 2008 金融危机 / 2020 疫情）。
     * V1 固定返回 0.0（无调整）。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getRandomEventAdjustment(year: Int): Double = 0.0
}
