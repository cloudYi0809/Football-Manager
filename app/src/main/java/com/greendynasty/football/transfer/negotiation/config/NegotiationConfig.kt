package com.greendynasty.football.transfer.negotiation.config

/**
 * T11 报价谈判模块配置（V0.2 `07_经济通胀_身价_工资模型.md` + V0.1 `09_转会_合同_经纪人系统.md`）。
 *
 * 严格依据：
 * - V0.1 09 §四 卖方评估 6 因子公式 + 心理价位 4 因子公式
 * - V0.1 09 §五 球员加盟意愿 8 因子公式
 * - V0.1 09 §六 合同谈判 + 9 基础 + 7 特殊 + 5 角色承诺
 * - V0.2 07 §五/六/七/九 身价与工资模型
 *
 * 所有参数集中配置化，便于调参不改架构（铁律）。
 *
 * 心理价位公式：
 * ```
 * psychological_price = base_value × importance_multiplier × contract_multiplier × potential_multiplier
 * ```
 *
 * 接受概率公式：
 * ```
 * P = price_ratio × 0.35 + contract_remain × 0.15 + leave_desire × 0.15
 *   + financial_pressure × 0.15 + importance_reverse × 0.15 + club_relation × 0.05
 * ```
 *
 * 加盟意愿公式：
 * ```
 * W = club_rep × 0.20 + wage × 0.20 + playing_chance × 0.15 + euro × 0.15
 *   + league × 0.10 + ambition × 0.10 + adaptation × 0.05 + agent_relation × 0.05
 * ```
 */
data class NegotiationConfig(
    /** 心理价位参数 */
    val psychologicalPrice: PsychologicalPriceParams = PsychologicalPriceParams(),
    /** 卖方评估参数 */
    val sellerEvaluation: SellerEvaluationParams = SellerEvaluationParams(),
    /** 球员加盟意愿参数 */
    val playerWillingness: PlayerWillingnessParams = PlayerWillingnessParams(),
    /** 个人条款（合同）谈判参数 */
    val contract: ContractParams = ContractParams(),
    /** 经纪人参数 */
    val agent: AgentParams = AgentParams(),
    /** 报价参数 */
    val offer: OfferParams = OfferParams(),
    /** 关系影响参数 */
    val relationshipImpact: RelationshipImpactParams = RelationshipImpactParams(),
    /** 体检参数 */
    val medical: MedicalParams = MedicalParams()
) {
    companion object {
        /** 默认配置（V0.2 推荐参数） */
        val DEFAULT = NegotiationConfig()
    }
}

/**
 * 心理价位参数（V0.1 09 §四.1）。
 *
 * 心理价位 = 市场价值 × 重要性系数 × 合同系数 × 潜力系数
 */
data class PsychologicalPriceParams(
    /** 球员重要性价格系数（KEY=1.50 / STARTER=1.20 / ROTATION=1.00 / BACKUP=0.80 / LISTED=0.60） */
    val importancePriceMultiplier: Map<String, Double> = mapOf(
        "KEY" to 1.50,
        "STARTER" to 1.20,
        "ROTATION" to 1.00,
        "BACKUP" to 0.80,
        "LISTED" to 0.60
    ),
    /** 最低转会费（防止 0 元报价） */
    val minTransferFee: Int = 100_000,
    /** 潜力系数：U19 高成长空间加价 */
    val potentialU19HighGap: Double = 1.40,
    /** U22 中等成长空间加价 */
    val potentialU22MidGap: Double = 1.20,
    /** U24 低成长空间加价 */
    val potentialU24LowGap: Double = 1.10,
    /** 默认潜力系数 */
    val potentialDefault: Double = 1.00,
    /** 潜力差阈值（PA - CA） */
    val potentialGapHigh: Int = 20,
    val potentialGapMid: Int = 15,
    val potentialGapLow: Int = 10
)

/**
 * 卖方评估参数（V0.1 09 §四）。
 */
data class SellerEvaluationParams(
    /** 接受阈值：概率 ≥ 此值直接接受 */
    val acceptThreshold: Double = 0.70,
    /** 拒绝阈值：概率 ≤ 此值且已达上限时拒绝 */
    val rejectThreshold: Double = 0.25,
    /** 最大谈判轮次（达到上限后卖方不再还价，直接拒绝） */
    val maxNegotiationRounds: Int = 5,
    /** 还价让步幅度（每轮让步比例基数） */
    val counterConcessionRate: Double = 0.10,
    /** 还价插值因子（在玩家报价与心理价位之间的偏向） */
    val counterLerpFactor: Double = 0.65,
    /** 还价随机扰动范围（增加 AI 不确定性） */
    val counterRandomJitter: Double = 0.05,
    /** 球员离队意愿中士气因子映射 */
    val leaveDesireMoraleLow: Double = 0.9,    // morale < 30
    val leaveDesireMoraleMid: Double = 0.6,    // morale < 50
    val leaveDesireMoraleNormal: Double = 0.3,  // morale < 70
    val leaveDesireMoraleHigh: Double = 0.1,
    /** 替补/潜力股离队意愿加成 */
    val leaveDesireBackupBonus: Double = 0.6,
    val leaveDesireStarterBonus: Double = 0.1,
    /** 财政压力阈值（工资/收入比） */
    val financialPressureHigh: Double = 0.85,   // → 1.0
    val financialPressureMid: Double = 0.70,    // → 0.6
    val financialPressureLow: Double = 0.55,    // → 0.3
    val financialPressureNormal: Double = 0.1,
    /** 默认俱乐部关系值（无数据时） */
    val defaultClubRelation: Double = 0.5,
    /** 耐心每轮扣减 */
    val patienceLossPerRound: Int = 15,
    /** 玩家撤回时卖方关系扣减 */
    val sellerRelationLossOnWithdraw: Int = 3
)

/**
 * 球员加盟意愿参数（V0.1 09 §五 8 因子公式）。
 */
data class PlayerWillingnessParams(
    /** 联赛吸引力（V0.2 §五） */
    val leagueAttractiveness: Map<String, Double> = mapOf(
        "EPL" to 0.95,
        "LaLiga" to 0.90,
        "SerieA" to 0.85,
        "Bundesliga" to 0.80,
        "Ligue1" to 0.70,
        "Eredivisie" to 0.55,
        "PrimeiraLiga" to 0.50,
        "Brasileirao" to 0.45,
        "Argentino" to 0.40
    ),
    /** 默认联赛吸引力 */
    val defaultLeagueAttractiveness: Double = 0.5,
    /** 球员接受阈值（综合意愿 ≥ 此值接受合同） */
    val playerAcceptThreshold: Double = 0.60,
    /** 球员拒绝阈值（≤ 此值直接拒绝） */
    val playerRejectThreshold: Double = 0.25,
    /** 同位置竞争惩罚：2+ 主力 */
    val competitionPenaltyHigh: Double = 0.3,
    /** 同位置竞争惩罚：1 主力 */
    val competitionPenaltyLow: Double = 0.1,
    /** 默认经纪人关系值 */
    val defaultAgentRelation: Double = 0.5
)

/**
 * 合同（个人条款）谈判参数（V0.1 09 §六）。
 *
 * 9 项基础条款：周薪/年限/签字费/佣金/出场/进球/助攻/零封/忠诚
 * 7 项特殊条款：解约金/降级解约/欧冠涨薪/年度涨薪/续约选项/回购/二次分成
 * 5 档角色承诺：KEY_PLAYER/STARTER/ROTATION/BACKUP/ACADEMY_DEV
 */
data class ContractParams(
    val minYears: Int = 1,
    val maxYears: Int = 5,
    /** 经纪人佣金比例（相对转会费） */
    val agentCommissionRate: Double = 0.10,
    /** 球员条款评分接受阈值 */
    val playerAcceptThreshold: Double = 0.60,
    /** 球员条款评分拒绝阈值 */
    val playerRejectThreshold: Double = 0.25,
    /** 经纪人还价时签字费上浮比例 */
    val counterBonusMultiplier: Double = 1.20,
    /** 经纪人还价时佣金上浮比例 */
    val counterCommissionMultiplier: Double = 1.15,
    /** 球员偏好的合同年限区间（高分） */
    val preferredYearsRange: IntRange = 3..5,
    /** 球员次偏好年限区间（中分） */
    val secondaryYearsRange: IntRange = 2..6
)

/**
 * 经纪人参数（V0.1 09 §七 5 型经纪人）。
 */
data class AgentParams(
    /** 5 型经纪人贪婪系数（影响期望工资上浮） */
    val greedMultiplier: Map<String, Double> = mapOf(
        "GREEDY" to 0.20,
        "STAR" to 0.15,
        "DISRUPTIVE" to 0.10,
        "RELATIONAL" to -0.05,
        "ACADEMY" to -0.05
    ),
    /** 经纪人接受阈值 */
    val acceptThreshold: Double = 0.70,
    /** 经纪人拒绝阈值 */
    val rejectThreshold: Double = 0.25,
    /** 搅局型触发 walkAway 的 disruption 阈值 */
    val walkAwayDisruptionThreshold: Int = 60,
    /** 成交后关系值提升 */
    val relationGainOnDeal: Int = 5,
    /** 谈判破裂后关系值扣减 */
    val relationLossOnCollapse: Int = 5,
    /** 默认贪婪度基准（属性归一基准） */
    val greedBaseline: Int = 50,
    val negotiationBaseline: Int = 50
)

/** 报价参数 */
data class OfferParams(
    /** 报价有效期天数 */
    val validityDays: Int = 7,
    /** 同一球员最大活跃报价数 */
    val maxActiveOffersPerPlayer: Int = 1
)

/** 关系影响参数（V0.1 09 §三 谈判破裂流程） */
data class RelationshipImpactParams(
    /** 玩家撤回且球员意愿高时，球员关系扣减 */
    val playerRelationLossOnWithdraw: Int = 10,
    /** 谈判破裂时经纪人关系扣减 */
    val agentRelationLossOnCollapse: Int = 5,
    /** 谈判拖沓时卖方俱乐部关系扣减 */
    val sellerClubRelationLossOnDelay: Int = 3
)

/** 体检参数（V0.1 09 §三 第 9 步） */
data class MedicalParams(
    /** 基础失败率 */
    val baseFailRate: Double = 0.03,
    /** 带条件通过率 */
    val conditionRate: Double = 0.07,
    /** 最大失败率上限 */
    val maxFailRate: Double = 0.25,
    /** 老将年龄阈值（体检失败率上浮） */
    val ageDeclineThreshold: Int = 32,
    /** 老将失败率加成 */
    val ageDeclineBonus: Double = 0.05,
    /** 近期重伤失败率加成 */
    val recentInjuryBonus: Double = 0.10,
    /** 高伤病风险阈值 */
    val highInjuryRiskThreshold: Int = 70
)
