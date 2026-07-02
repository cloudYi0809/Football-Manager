package com.greendynasty.football.transfer.config

/**
 * T10 转会搜索模块配置（V0.2 经济模型 / 推荐算法参数）。
 *
 * 严格依据：
 * - V0.1 `09_转会_合同_经纪人系统.md` §二 搜索功能
 * - V0.2 `07_经济通胀_身价_工资模型.md` §五/六/九 身价与工资模型
 *
 * 所有参数集中配置化，便于调参不改架构（铁律）。
 *
 * @property searchTimeoutMs 搜索响应超时阈值（铁律 ≤1 秒）
 * @property defaultPageSize 默认分页大小
 * @property maxComparePlayers 球员对比上限（2-3 人）
 * @property maxWatchlistSize 观察名单上限
 * @property economy 转会经济模型参数
 * @property recommend 推荐算法参数
 * @property window 转会窗口参数
 */
data class TransferConfig(
    val searchTimeoutMs: Long = 1000L,
    val defaultPageSize: Int = 50,
    val maxComparePlayers: Int = 3,
    val minComparePlayers: Int = 2,
    val maxWatchlistSize: Int = 25,
    val economy: EconomyParams = EconomyParams(),
    val recommend: RecommendParams = RecommendParams(),
    val window: WindowParams = WindowParams()
) {
    companion object {
        /** 默认配置（V0.2 推荐参数） */
        val DEFAULT = TransferConfig()
    }
}

/**
 * V0.2 经济模型参数（§五/六/七/九）。
 *
 * 身价公式简化为：
 * ```
 * base_value = ability_value_curve(CA) * age_multiplier(age) * potential_multiplier(PA, age)
 *            * position_multiplier(position) * contract_multiplier(remainingYears)
 *            * economy_index(year)
 * ```
 *
 * @property abilityBaseAmount CA=50 时的基础身价（2002 基准）
 * @property abilityGrowthRate CA 价值曲线指数增长率（V0.2 §五 pow(1.075, CA-50)）
 * @property wageBaseAmount CA=50 主力周薪基准（2002 基准）
 * @property economyIndexBase 2002 年经济指数基准（=1.00）
 */
data class EconomyParams(
    val abilityBaseAmount: Int = 500_000,
    val abilityGrowthRate: Double = 1.075,
    val wageBaseAmount: Int = 5_000,
    val economyIndexBase: Double = 1.00,
    val freeAgentSigningFeeRatio: Double = 0.15
)

/**
 * 推荐算法参数（V0.2 §四 球员推荐）。
 *
 * 推荐度 = w_pos * 位置匹配 + w_age * 年龄 + w_ca * 能力 + w_pa * 潜力
 *        + w_value * 身价合理性 + w_style * 战术匹配
 *
 * @property weightPosition 位置匹配权重
 * @property weightAge 年龄权重
 * @property weightCa 当前能力权重
 * @property weightPa 潜力权重
 * @property weightValue 身价合理性权重
 * @property weightStyle 战术风格匹配权重
 * @property weakPositionBonus 球队薄弱位置加成（0-100）
 * @property maxRecommendCount 推荐列表上限
 */
data class RecommendParams(
    val weightPosition: Double = 0.25,
    val weightAge: Double = 0.10,
    val weightCa: Double = 0.25,
    val weightPa: Double = 0.20,
    val weightValue: Double = 0.10,
    val weightStyle: Double = 0.10,
    val weakPositionBonus: Double = 15.0,
    val maxRecommendCount: Int = 20
)

/**
 * 转会窗口参数（V0.1 09 §六）。
 *
 * @property summerStartMonth 夏窗开始月（7 月）
 * @property summerEndMonth 夏窗结束月（8 月）
 * @property summerEndDay 夏窗结束日（31 日）
 * @property winterStartMonth 冬窗开始月（1 月）
 * @property winterEndMonth 冬窗结束月（1 月）
 * @property winterEndDay 冬窗结束日（31 日）
 * @property summerClosingDays 夏窗截止日阈值（剩余 ≤3 天进入 CLOSING_SOON）
 * @property winterClosingDays 冬窗截止日阈值（剩余 ≤2 天进入 CLOSING_SOON）
 */
data class WindowParams(
    val summerStartMonth: Int = 7,
    val summerStartDay: Int = 1,
    val summerEndMonth: Int = 8,
    val summerEndDay: Int = 31,
    val winterStartMonth: Int = 1,
    val winterStartDay: Int = 1,
    val winterEndMonth: Int = 1,
    val winterEndDay: Int = 31,
    val summerClosingDays: Int = 3,
    val winterClosingDays: Int = 2
)
