package com.greendynasty.football.economy.config

/**
 * T17 经济通胀模型配置（V0.2 `07_经济通胀_身价_工资模型.md`）。
 *
 * 严格依据 V0.2 §二-§十一，所有参数集中配置化，便于调参不改架构（铁律）。
 *
 * 涵盖：
 * - §二 时代通胀系数（固定表 + 2030+ 架空增长）
 * - §三 9 大联赛商业系数
 * - §四 俱乐部财力 6 因子权重
 * - §五-§八 球员身价 6 因子（CA 价值曲线 / 年龄 / 合同 / 位置 / 声望 / 表现）
 * - §九 工资 6 因子（与 T12 WageCalculator 对齐）
 * - §十 财政安全线 4 档阈值
 *
 * 与 T10 [com.greendynasty.football.transfer.config.EconomyParams] / T12 [com.greendynasty.football.transfer.contract.config.ContractRenewalConfig]
 * 关系：T17 作为经济模型的权威实现，参数与 T10/T12 保持一致以避免脱节（铁律 §十三.4）。
 *
 * @property economyIndex 时代通胀参数
 * @property leagueEconomy 9 大联赛商业参数
 * @property financialPower 俱乐部财力权重
 * @property abilityCurve CA 价值曲线参数
 * @property ageMultiplier 年龄系数参数
 * @property contractMultiplier 合同剩余系数
 * @property performanceMultiplier 表现修正参数
 * @property positionMultiplier 位置身价系数
 * @property wage 工资模型参数
 * @property financialHealth 财政安全线阈值
 */
data class EconomyConfig(
    val economyIndex: EconomyIndexParams = EconomyIndexParams(),
    val leagueEconomy: LeagueEconomyParams = LeagueEconomyParams(),
    val financialPower: FinancialPowerWeights = FinancialPowerWeights(),
    val abilityCurve: AbilityCurveParams = AbilityCurveParams(),
    val ageMultiplier: AgeMultiplierParams = AgeMultiplierParams(),
    val contractMultiplier: ContractMultiplierParams = ContractMultiplierParams(),
    val performanceMultiplier: PerformanceMultiplierParams = PerformanceMultiplierParams(),
    val positionMultiplier: Map<String, Double> = defaultPositionMultiplier,
    val wage: WageParams = WageParams(),
    val financialHealth: FinancialHealthParams = FinancialHealthParams()
) {
    companion object {
        /** 默认配置（V0.2 推荐参数） */
        val DEFAULT = EconomyConfig()

        /** V0.2 §五 位置身价系数（前锋溢价 / 门将折价） */
        val defaultPositionMultiplier: Map<String, Double> = mapOf(
            "ST" to 1.20, "CF" to 1.20,
            "LW" to 1.15, "RW" to 1.15,
            "AM" to 1.10,
            "CM" to 1.00,
            "DM" to 0.95,
            "CB" to 0.90,
            "LB" to 0.85, "RB" to 0.85,
            "GK" to 0.75
        )
    }
}

/**
 * V0.2 §二 时代通胀系数参数。
 *
 * - 1992-2030 固定系数表（线性插值）
 * - 2030+ 架空增长（默认年增长 3%）
 */
data class EconomyIndexParams(
    /** 基准年份（2002 = 1.00） */
    val baseYear: Int = 2002,
    /** 基准指数 */
    val baseIndex: Double = 1.00,
    /** 2030 后架空增长年化速率（默认 3%） */
    val futureGrowthBaseRate: Double = 0.03,
    /** 2030 年指数锚点 */
    val year2030Index: Double = 5.00,
    /** 1992-2030 固定系数表（年份 → 指数） */
    val fixedTable: Map<Int, Double> = mapOf(
        1992 to 0.35, 1995 to 0.45, 1998 to 0.65,
        2002 to 1.00, 2006 to 1.25, 2010 to 1.60,
        2014 to 2.20, 2017 to 3.20, 2020 to 3.60,
        2024 to 4.20, 2030 to 5.00
    )
)

/**
 * V0.2 §三 9 大联赛商业系数参数（基础系数 + 增长类型）。
 *
 * 增长曲线类型见 [GrowthType]。
 */
data class LeagueEconomyParams(
    /** 9 大联赛基础数据：leagueId → (baseMultiplier, growthType) */
    val leagueData: Map<String, LeagueBaseData> = defaultLeagueData,
    /** 未配置联赛的默认基础系数 */
    val defaultBaseMultiplier: Double = 0.50
) {
    companion object {
        val defaultLeagueData: Map<String, LeagueBaseData> = mapOf(
            "EPL" to LeagueBaseData(1.10, GrowthType.FAST),
            "LaLiga" to LeagueBaseData(1.00, GrowthType.TOP_HEAVY),
            "SerieA" to LeagueBaseData(1.05, GrowthType.DECLINING),
            "Bundesliga" to LeagueBaseData(0.85, GrowthType.STABLE),
            "Ligue1" to LeagueBaseData(0.70, GrowthType.CAPITAL_EVENT),
            "Eredivisie" to LeagueBaseData(0.45, GrowthType.EXPORT),
            "PrimeiraLiga" to LeagueBaseData(0.40, GrowthType.SELLING),
            "Brasileirao" to LeagueBaseData(0.35, GrowthType.TALENT_EXPORT),
            "Argentino" to LeagueBaseData(0.30, GrowthType.TALENT_EXPORT)
        )
    }
}

/** 联赛基础数据（基础系数 + 增长类型） */
data class LeagueBaseData(
    val baseMultiplier: Double,
    val growthType: GrowthType
)

/**
 * V0.2 §三 联赛增长曲线类型。
 *
 * 不同联赛随年代演变的差异化曲线（英超增长最快 / 意甲 2005 后下滑 / 法甲 2011 资本入主等）。
 */
enum class GrowthType {
    /** 英超：后续增长最快（年增 2%） */
    FAST,
    /** 西甲：豪门集中（年增 1.5%） */
    TOP_HEAVY,
    /** 意甲：2005 后下滑 */
    DECLINING,
    /** 德甲：稳定（年增 1%） */
    STABLE,
    /** 法甲：2011 后资本入主（年增 3%） */
    CAPITAL_EVENT,
    /** 荷甲：青训出口（年增 0.5%） */
    EXPORT,
    /** 葡超：黑店体系（年增 0.5%） */
    SELLING,
    /** 巴甲/阿甲：输出天才（持平） */
    TALENT_EXPORT
}

/**
 * V0.2 §四 俱乐部财力 6 因子权重（和为 1.0）。
 */
data class FinancialPowerWeights(
    val reputation: Double = 0.25,
    val leagueEconomy: Double = 0.25,
    val stadiumIncome: Double = 0.15,
    val commercialIncome: Double = 0.15,
    val ownerInvestment: Double = 0.15,
    val recentSuccess: Double = 0.05
)

/**
 * V0.2 §五 CA 价值曲线参数。
 *
 * `ability_value_curve = base_amount * pow(growthRate, CA - 50)`
 */
data class AbilityCurveParams(
    /** CA=50 时基础身价（2002 基准） */
    val baseAmount: Int = 500_000,
    /** 指数增长率（与 T10 EconomyEstimator 对齐 = 1.075） */
    val growthRate: Double = 1.075
)

/**
 * V0.2 §六 年龄系数参数。
 *
 * 不同年龄段系数区间（受潜力 PA 影响的斜率）。
 */
data class AgeMultiplierParams(
    /** 系数下限 */
    val minValue: Double = 0.10,
    /** 系数上限 */
    val maxValue: Double = 1.60,
    /** U19 高潜判定阈值 */
    val u19HighPotentialThreshold: Int = 85,
    /** U19 高潜系数 */
    val u19HighPotentialValue: Double = 1.20,
    /** U18 系数基数 */
    val u18BaseValue: Double = 0.60,
    /** U18 PA 斜率 */
    val u18PotentialSlope: Double = 0.012
)

/**
 * V0.2 §七 合同剩余系数。
 *
 * | 剩余 | 系数 |
 * |---|---|
 * | ≥ 4 年 | 1.20 |
 * | 3 年 | 1.10 |
 * | 2 年 | 1.00 |
 * | 1 年 | 0.70 |
 * | 6 个月 | 0.40 |
 * | 自由球员 | 0.0 |
 */
data class ContractMultiplierParams(
    val years4Plus: Double = 1.20,
    val years3: Double = 1.10,
    val years2: Double = 1.00,
    val years1: Double = 0.70,
    val halfYear: Double = 0.40,
    val freeAgent: Double = 0.0
)

/**
 * V0.2 §八 表现修正参数。
 */
data class PerformanceMultiplierParams(
    val minValue: Double = 0.65,
    val maxValue: Double = 1.60,
    /** V1 简化：默认无表现数据时返回 1.0 */
    val defaultValue: Double = 1.0
)

/**
 * V0.2 §九 工资模型参数（与 T12 WageParams 对齐）。
 *
 * `expected_wage = wage_base × 声望 × 联赛 × 经济 × 经纪人 × 队内角色`
 */
data class WageParams(
    /** CA=50 时基础周薪（2002 基准，与 T12 WageParams.wageBaseAmount 一致） */
    val wageBaseAmount: Int = 5_000,
    /** CA 工资指数增长率（与 T12 WageParams.abilityGrowthRate 一致） */
    val abilityGrowthRate: Double = 1.05,
    /** 最低周薪 */
    val minWage: Int = 1_000,
    /** 队内角色工资系数（与 T12 WageParams.squadRoleFactor 对齐） */
    val squadRoleFactor: Map<String, Double> = mapOf(
        "key_player" to 1.40,
        "starter" to 1.20,
        "rotation" to 0.90,
        "backup" to 0.65,
        "prospect" to 0.35,
        "listed" to 0.50
    ),
    /** 默认队内角色系数 */
    val defaultSquadRoleFactor: Double = 1.00,
    /** 俱乐部声望工资系数（与 T12 WageParams.clubReputationFactor 对齐） */
    val clubReputationFactor: Map<String, Double> = mapOf(
        "elite" to 1.50,
        "strong" to 1.25,
        "average" to 1.00,
        "weak" to 0.80,
        "minnow" to 0.65
    ),
    /** 默认俱乐部声望系数 */
    val defaultClubReputationFactor: Double = 1.00,
    /** 默认经纪人贪婪系数（V1 简化 1.0-1.15） */
    val defaultAgentGreed: Double = 1.0
) {
    /** 按声望值解析工资系数（与 T12 WageParams.clubReputationFactorFor 对齐） */
    fun clubReputationFactorFor(reputation: Int): Double {
        return when {
            reputation >= 85 -> clubReputationFactor["elite"]
            reputation >= 70 -> clubReputationFactor["strong"]
            reputation >= 50 -> clubReputationFactor["average"]
            reputation >= 30 -> clubReputationFactor["weak"]
            else -> clubReputationFactor["minnow"]
        } ?: defaultClubReputationFactor
    }
}

/**
 * V0.2 §十 财政安全线阈值（工资/收入比）。
 *
 * | 比率 | 等级 |
 * |---|---|
 * | < 55% | 健康 |
 * | 55%-70% | 可接受 |
 * | 70%-85% | 风险 |
 * | > 85% | 高危 |
 */
data class FinancialHealthParams(
    val healthyThreshold: Double = 0.55,
    val acceptableThreshold: Double = 0.70,
    val riskThreshold: Double = 0.85
)
