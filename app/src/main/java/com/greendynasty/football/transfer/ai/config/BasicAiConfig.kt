package com.greendynasty.football.transfer.ai.config

/**
 * T13 AI 基础转会配置（V0.2 `05_AI俱乐部决策模型.md` 基础版）。
 *
 * 严格依据 V0.2 §四/§五/§七 基础版公式：
 * - 位置需求评分（6 因子，固定权重）
 * - 转会目标评分（9 因子，固定权重，不区分俱乐部画像）
 * - 卖人决策（6 因子，无特殊规则）
 * - 报价策略（统一 80% 市值）
 * - 防崩坏 3 条约束
 *
 * 铁律：基础版不区分俱乐部画像类型，所有参数集中配置化，便于调参不改架构。
 * T18 完整版在此基础上扩展画像、动态权重、多策略等能力。
 *
 * @property targetScoreWeights 转会目标 9 因子权重
 * @property needScoreWeights 位置需求 6 因子权重
 * @property sellScoreWeights 卖人决策 6 因子权重
 * @property offer 报价参数
 * @property constraints 防崩坏约束参数
 * @property thresholds 决策阈值
 * @property search 候选搜索参数
 * @property randomness 随机扰动参数（AI 不确定性）
 * @property expectedCa 基础版期望 CA（T18 才按俱乐部类型区分）
 * @property standardPositions 标准位置列表
 */
data class BasicAiConfig(
    /** 转会目标评分 9 因子权重（V0.2 §五 基础版固定权重） */
    val targetScoreWeights: TargetScoreWeights = TargetScoreWeights(),
    /** 位置需求评分 6 因子权重（V0.2 §四 基础版） */
    val needScoreWeights: NeedScoreWeights = NeedScoreWeights(),
    /** 卖人决策 6 因子权重（V0.2 §七 基础版） */
    val sellScoreWeights: SellScoreWeights = SellScoreWeights(),
    /** 报价参数 */
    val offer: OfferParams = OfferParams(),
    /** 防崩坏约束（基础版 3 条） */
    val constraints: ConstraintParams = ConstraintParams(),
    /** 决策阈值 */
    val thresholds: ThresholdParams = ThresholdParams(),
    /** 候选搜索参数 */
    val search: SearchParams = SearchParams(),
    /** 随机扰动参数（为 AI 决策引入合理不确定性） */
    val randomness: RandomnessParams = RandomnessParams(),
    /** 基础版期望 CA = 75（T18 才按俱乐部类型区分） */
    val expectedCa: Int = 75,
    /** 标准位置列表（用于阵容短板分析） */
    val standardPositions: List<String> = listOf(
        "GK", "RB", "CB", "LB", "DM", "CM", "AM", "RW", "LW", "ST", "CF"
    )
) {
    companion object {
        /** 默认配置（V0.2 基础版推荐参数） */
        val DEFAULT = BasicAiConfig()
    }
}

/**
 * 转会目标评分 9 因子权重（V0.2 §五 基础版固定权重，不按俱乐部类型调整）。
 *
 * target_score =
 *   position_need * w1 + current_ability_fit * w2 + potential_fit * w3
 * + price_value * w4 + wage_affordability * w5 + age_fit * w6
 * + tactical_fit * w7 + nationality_fit * w8 + commercial_value * w9
 */
data class TargetScoreWeights(
    val positionNeed: Double = 0.25,
    val currentAbilityFit: Double = 0.20,
    val potentialFit: Double = 0.15,
    val priceValue: Double = 0.15,
    val wageAffordability: Double = 0.10,
    val ageFit: Double = 0.05,
    val tacticalFit: Double = 0.05,
    val nationalityFit: Double = 0.03,
    val commercialValue: Double = 0.02
)

/**
 * 位置需求评分 6 因子权重（V0.2 §四 基础版）。
 *
 * position_need_score =
 *   starter_gap * w1 + backup_gap * w2 + average_age_risk * w3
 * + injury_risk * w4 + contract_expiry_risk * w5 + tactical_importance * w6
 */
data class NeedScoreWeights(
    val starterGap: Double = 0.40,
    val backupGap: Double = 0.20,
    val averageAgeRisk: Double = 0.15,
    val injuryRisk: Double = 0.10,
    val contractExpiryRisk: Double = 0.10,
    val tacticalImportance: Double = 0.05
)

/**
 * 卖人决策 6 因子权重（V0.2 §七 基础版，无特殊规则）。
 *
 * sell_score =
 *   offer_value_ratio * w1 + player_unhappy * w2 + contract_expiry_risk * w3
 * + squad_depth_cover * w4 + financial_pressure * w5 + age_decline_risk * w6
 */
data class SellScoreWeights(
    val offerValueRatio: Double = 0.25,
    val playerUnhappy: Double = 0.20,
    val contractExpiryRisk: Double = 0.15,
    val squadDepthCover: Double = 0.15,
    val financialPressure: Double = 0.15,
    val ageDeclineRisk: Double = 0.10
)

/** 报价参数（基础版统一 80% 市值） */
data class OfferParams(
    /** 初始报价比例（统一 80% 市值，T18 才按俱乐部类型区分 4 种策略） */
    val initialOfferRatio: Double = 0.80,
    /** 最大谈判轮次 */
    val maxNegotiationRounds: Int = 2,
    /** 报价上浮随机扰动范围（±比例，增加 AI 不确定性） */
    val offerJitter: Double = 0.05
)

/** 防崩坏约束（基础版 3 条，T18 扩展到 7 条） */
data class ConstraintParams(
    /** 每窗最大交易数量 */
    val maxTransfersPerWindow: Int = 5,
    /** 非急需位置的最低目标评分门槛 */
    val nonUrgentPositionMinScore: Double = 75.0
)

/** 决策阈值 */
data class ThresholdParams(
    /** 卖人评分阈值（≥ 此值则卖） */
    val sellThreshold: Double = 60.0,
    /** 需求评分行动阈值（> 此值才尝试买入） */
    val needScoreActionThreshold: Double = 50.0,
    /** 目标评分行动阈值（非急需位置需 ≥ 此值） */
    val targetScoreActionThreshold: Double = 75.0,
    /** 只处理急需位置的需求评分下限 */
    val needScoreFilterThreshold: Double = 30.0
)

/** 候选搜索参数 */
data class SearchParams(
    /** 每个位置最大候选数量 */
    val maxCandidatesPerPosition: Int = 20
)

/** 随机扰动参数（为 AI 决策引入合理不确定性，非确定性规则） */
data class RandomnessParams(
    /** 目标评分随机扰动范围（±比例） */
    val targetScoreJitter: Double = 0.02,
    /** 卖人评分随机扰动范围（±比例） */
    val sellScoreJitter: Double = 0.03,
    /** 单日执行转会的概率（控制交易频率，避免每日都交易） */
    val dailyTransferProbability: Double = 0.6,
    /** 卖人决策触发概率（控制卖人频率） */
    val sellDecisionProbability: Double = 0.4
)
