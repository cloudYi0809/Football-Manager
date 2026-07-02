package com.greendynasty.football.transfer.contract.config

/**
 * T12 合同续约模块配置（V0.2 `07_经济通胀_身价_工资模型.md` + V0.1 `09_转会_合同_经纪人系统.md`）。
 *
 * 严格依据：
 * - V0.2 07 §九 工资模型：wage = wage_base × 声望 × 联赛 × 经济 × 队内角色
 * - V0.1 09 §六 合同条款系统（复用 T11：9 基础 + 7 特殊 + 5 角色）
 * - V0.1 09 §三 Bosman 预合同规则（剩 6 个月可接触）
 *
 * 所有参数集中配置化，便于调参不改架构（铁律）。
 *
 * T12.1 工资公式（5 因子乘积）：
 * ```
 * expected_wage = wage_base_by_CA(CA)
 *               × club_reputation_factor
 *               × league_factor
 *               × economy_factor
 *               × squad_role_factor
 * ```
 *
 * T12.4 经纪人佣金：
 * ```
 * agent_commission = annual_wage × commission_rate × agent_greed_adjust
 * ```
 */
data class ContractRenewalConfig(
    /** T12.1 工资计算参数 */
    val wage: WageParams = WageParams(),
    /** T12.2 续约谈判参数（简化 1-2 回合） */
    val negotiation: RenewalNegotiationParams = RenewalNegotiationParams(),
    /** T12.3 合同到期处理参数 */
    val expiry: ExpiryParams = ExpiryParams(),
    /** T12.4 经纪人佣金参数 */
    val commission: CommissionParams = CommissionParams(),
    /** 续约意愿参数 */
    val willingness: WillingnessParams = WillingnessParams(),
    /** 报价参数 */
    val offer: OfferParams = OfferParams()
) {
    companion object {
        /** 默认配置（V0.2 推荐参数） */
        val DEFAULT = ContractRenewalConfig()
    }
}

/**
 * T12.1 工资计算参数（5 因子乘积）。
 *
 * wage_base × 声望系数 × 联赛系数 × 经济系数 × 队内角色系数
 */
data class WageParams(
    /** CA=50 时的基础周薪（2002 基准） */
    val wageBaseAmount: Int = 5_000,
    /** CA 价值曲线指数增长率（与 T10 EconomyEstimator 一致） */
    val abilityGrowthRate: Double = 1.05,
    /** 俱乐部声望工资系数映射（0-100 声望 → 系数） */
    val clubReputationFactor: Map<String, Double> = mapOf(
        "elite" to 1.50,      // 声望 85-100（豪门）
        "strong" to 1.25,     // 声望 70-84
        "average" to 1.00,    // 声望 50-69
        "weak" to 0.80,       // 声望 30-49
        "minnow" to 0.65      // 声望 0-29
    ),
    /** 联赛工资系数（V0.2 07 §九 league_factor） */
    val leagueFactor: Map<String, Double> = mapOf(
        "EPL" to 1.40,
        "LaLiga" to 1.30,
        "SerieA" to 1.20,
        "Bundesliga" to 1.15,
        "Ligue1" to 1.05,
        "Eredivisie" to 0.80,
        "PrimeiraLiga" to 0.70,
        "Brasileirao" to 0.55,
        "Argentino" to 0.50
    ),
    /** 默认联赛工资系数 */
    val defaultLeagueFactor: Double = 0.75,
    /** 队内角色工资系数（V0.2 07 §九 squad_role_factor） */
    val squadRoleFactor: Map<String, Double> = mapOf(
        "key_player" to 1.40,
        "starter" to 1.20,
        "rotation" to 0.90,
        "backup" to 0.65,
        "prospect" to 0.35,
        "listed" to 0.50
    ),
    /** 默认队内角色工资系数 */
    val defaultSquadRoleFactor: Double = 1.00,
    /** 经济指数基准（2002=1.0，与 T10 EconomyEstimator 对齐） */
    val economyIndexBase: Double = 1.00
) {
    /** 按声望值解析工资系数 */
    fun clubReputationFactorFor(reputation: Int): Double {
        return when {
            reputation >= 85 -> clubReputationFactor["elite"]
            reputation >= 70 -> clubReputationFactor["strong"]
            reputation >= 50 -> clubReputationFactor["average"]
            reputation >= 30 -> clubReputationFactor["weak"]
            else -> clubReputationFactor["minnow"]
        } ?: 1.00
    }
}

/**
 * T12.2 续约谈判参数（简化为单回合或双回合，复用 T11 PersonalTermsNegotiator）。
 */
data class RenewalNegotiationParams(
    /** 最大谈判轮次（任务要求简化为 1-2 回合） */
    val maxRounds: Int = 2,
    /** 球员接受阈值（综合分 ≥ 此值接受） */
    val playerAcceptThreshold: Double = 0.60,
    /** 球员拒绝阈值（≤ 此值直接拒绝） */
    val playerRejectThreshold: Double = 0.25,
    /** 每轮耐心扣减 */
    val patienceLossPerRound: Int = 15,
    /** 合同剩余议价权：剩 ≤6 个月可接触其他俱乐部，议价权高 */
    val bargainingPower6m: Double = 1.15,
    /** 合同剩余议价权：剩 ≤12 个月 */
    val bargainingPower12m: Double = 1.08,
    /** 合同剩余议价权：长合同 */
    val bargainingPowerLong: Double = 1.00,
    /** 涨薪条款对球员评分加成（续约特有：表现达标自动涨薪） */
    val performanceRaiseBonus: Double = 0.20,
    /** 退役条款对球员评分扣减（续约特有：老将一年一签） */
    val veteranClausePenalty: Double = -0.10,
    /** 青训保护条款对球员评分加成 */
    val academyProtectionBonus: Double = 0.15,
    /** 涨薪 ≥30% 时的评分加成 */
    val highRaiseBonus: Double = 0.20,
    /** 涨薪 ≥15% 时的评分加成 */
    val mediumRaiseBonus: Double = 0.10,
    /** 降薪时的评分扣减 */
    val payCutPenalty: Double = -0.15
)

/**
 * T12.3 合同到期处理参数。
 */
data class ExpiryParams(
    /** 提醒档位：12 个月早期预警 */
    val earlyWarningMonths: Int = 12,
    /** 提醒档位：6 个月紧急（Bosman 可接触） */
    val urgentMonths: Int = 6,
    /** 提醒档位：1 个月最后机会 */
    val finalMonths: Int = 1,
    /** 预合同接触触发月数（Bosman 规则） */
    val preContractContactMonths: Int = 6,
    /** 预合同接触概率基数 */
    val preContractContactProbabilityBase: Double = 0.40,
    /** 自动挂牌概率（合同到期未续约） */
    val autoListProbability: Double = 0.50,
    /** 释放球员后加入自由球员池 */
    val releaseToFreeAgentPool: Boolean = true
)

/**
 * T12.4 经纪人佣金参数。
 *
 * agent_commission = annual_wage × commission_rate × agent_greed_adjust
 *
 * - annual_wage = weekly_wage × 52
 * - commission_rate：按合同年限递增（长约佣金高）
 * - agent_greed_adjust：贪婪型经纪人佣金上浮
 */
data class CommissionParams(
    /** 基础佣金比例（年化工资的比例） */
    val baseCommissionRate: Double = 0.10,
    /** 按合同年限的佣金系数 */
    val yearsCommissionFactor: Map<Int, Double> = mapOf(
        1 to 0.05,
        2 to 0.08,
        3 to 0.10,
        4 to 0.12,
        5 to 0.15
    ),
    /** 默认年限佣金系数 */
    val defaultYearsCommissionFactor: Double = 0.10,
    /** 经纪人贪婪度佣金调整（按 style 字段） */
    val agentStyleAdjust: Map<String, Double> = mapOf(
        "GREEDY" to 0.20,
        "STAR" to 0.15,
        "DISRUPTIVE" to 0.10,
        "RELATIONAL" to -0.05,
        "ACADEMY" to -0.05
    ),
    /** 默认经纪人风格佣金调整 */
    val defaultAgentStyleAdjust: Double = 0.0,
    /** 无经纪人时的佣金（0） */
    val noAgentCommission: Int = 0,
    /** 最低佣金（防止 0 元） */
    val minCommission: Int = 10_000,
    /** 最高佣金上限（防止失控） */
    val maxCommission: Int = 50_000_000
)

/**
 * 续约意愿参数（简化版，用于判定球员是否愿意续约）。
 */
data class WillingnessParams(
    /** 最低续约意愿（低于此值球员拒绝续约） */
    val minWillingnessToNegotiate: Double = 0.30,
    /** 球员野心对续约意愿的扣减系数（野心高 → 续约意愿降低） */
    val ambitionPenaltyFactor: Double = 0.005,
    /** 同国籍适应满分 */
    val sameNationAdaptation: Double = 1.0,
    /** 同语言区适应分 */
    val sameLanguageAdaptation: Double = 0.7,
    /** 异语言区适应分 */
    val differentLanguageAdaptation: Double = 0.4
)

/** 报价参数 */
data class OfferParams(
    /** 报价有效期天数 */
    val validityDays: Int = 7
)
