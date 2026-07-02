package com.greendynasty.football.board.model

/**
 * T22 董事会配置（V0.2 05 §九 换帅逻辑 + 11 §四 董事会 AI + T22 方案 §八）。
 *
 * 严格依据 V0.2 算法文档，所有可调参数集中配置化，便于调参不改架构（铁律）。
 *
 * 涵盖：
 * - 8 因子满意度权重（默认 0.20/0.10/0.15/0.15/0.10/0.10/0.10/0.10）
 * - 4 种董事会性格权重微调
 * - 5 档满意度等级阈值
 * - 5 类赛季目标评估分数表
 * - 4 档解雇机制阈值
 * - 6 类预算申请审批比例矩阵
 * - 8 类董事会事件触发概率
 * - 信心值变化阈值（连续 3 场不胜 -5，夺冠 +20）
 *
 * @property satisfactionWeights 8 因子满意度权重
 * @property personalityAdjustments 4 种董事会性格权重微调
 * @property satisfactionLevels 5 档满意度等级阈值
 * @property seasonTarget 赛季目标评估参数
 * @property dismissal 解雇机制参数
 * @property budgetRequest 预算申请参数
 * @property events 董事会事件参数
 * @property confidence 信心值变化阈值参数
 */
data class BoardConfig(
    val satisfactionWeights: SatisfactionWeights = SatisfactionWeights(),
    val personalityAdjustments: Map<String, SatisfactionWeights> = defaultPersonalityAdjustments,
    val satisfactionLevels: SatisfactionLevelThresholds = SatisfactionLevelThresholds(),
    val seasonTarget: SeasonTargetParams = SeasonTargetParams(),
    val dismissal: DismissalParams = DismissalParams(),
    val budgetRequest: BudgetRequestParams = BudgetRequestParams(),
    val events: BoardEventParams = BoardEventParams(),
    val confidence: ConfidenceParams = ConfidenceParams(),
    val longTermGoal: LongTermGoalParams = LongTermGoalParams(),
    val targetLowering: TargetLoweringParams = TargetLoweringParams()
) {
    companion object {
        /** 默认配置（V0.2 推荐参数） */
        val DEFAULT = BoardConfig()

        /** 4 种董事会性格权重微调（V0.2 11 §四） */
        val defaultPersonalityAdjustments: Map<String, SatisfactionWeights> = mapOf(
            "PATIENT" to SatisfactionWeights(
                league = -0.05, youth = 0.03, dressingRoom = 0.02
            ),
            "AMBITIOUS" to SatisfactionWeights(
                league = 0.05, youth = -0.03, cup = 0.02, financial = -0.04
            ),
            "RUTHLESS" to SatisfactionWeights(
                league = 0.05, dressingRoom = -0.03, fan = 0.02, financial = -0.04
            ),
            "HANDS_OFF" to SatisfactionWeights(
                league = -0.03, financial = 0.05, cup = -0.02
            )
        )
    }
}

/**
 * 8 因子满意度权重（默认值严格依据 V0.2 11 §四）。
 *
 * 总和应为 1.0（性格调整后可能略微偏离，最终通过 coerceIn(0,100) 保证结果合法）。
 */
data class SatisfactionWeights(
    val league: Double = 0.20, // 联赛成绩
    val cup: Double = 0.10, // 杯赛成绩
    val financial: Double = 0.15, // 财政状况
    val fan: Double = 0.15, // 球迷满意度
    val transfer: Double = 0.10, // 转会市场表现
    val youth: Double = 0.10, // 青训发展
    val dressingRoom: Double = 0.10, // 更衣室稳定
    val managerReputation: Double = 0.10 // 经理个人声望
)

/**
 * 5 档满意度等级阈值（V0.2 11 §四）。
 */
data class SatisfactionLevelThresholds(
    val excellentMin: Double = 80.0, // EXCELLENT
    val goodMin: Double = 65.0, // GOOD
    val acceptableMin: Double = 50.0, // ACCEPTABLE
    val poorMin: Double = 35.0, // POOR
    val criticalMin: Double = 0.0 // CRITICAL
)

/**
 * 赛季目标评估参数（V0.2 11 §四 + T22 方案 §八）。
 */
data class SeasonTargetParams(
    // 核心/次要目标权重
    val coreWeight: Double = 0.7,
    val secondaryWeight: Double = 0.3,
    // 状态阈值
    val achievedThreshold: Double = 85.0,
    val partiallyThreshold: Double = 60.0,
    // 联赛排名评估分数
    val leagueAchieved: Double = 100.0,
    val leagueMissBy1: Double = 70.0,
    val leagueMissBy2: Double = 50.0,
    val leagueMissBy3To4: Double = 30.0,
    val leagueMissBy5Plus: Double = 10.0,
    // 杯赛评估分数
    val cupAchieved: Double = 100.0,
    val cupMissBy1Round: Double = 60.0,
    val cupMissBy2Rounds: Double = 40.0,
    val cupEarlyExit: Double = 20.0,
    // 财政评估分数
    val financialAchieved: Double = 100.0,
    val financialExceedBy5pct: Double = 70.0,
    val financialExceedBy10pct: Double = 50.0,
    val financialExceedBy20pct: Double = 30.0,
    val financialExceedBy30pctPlus: Double = 10.0,
    // 青训评估分数
    val youthAchieved: Double = 100.0,
    val youthMissBy1: Double = 60.0,
    val youthZeroWhenTargetPositive: Double = 20.0,
    val youthPartial: Double = 40.0,
    // 无欧战目标默认分
    val europeanNoneScore: Double = 80.0
)

/**
 * 4 档解雇机制参数（V0.2 05 §九 + T22 方案 §八）。
 *
 * - warning: 连续 2 赛季核心目标未达成
 * - ultimatum: 连续 3 赛季
 * - dismiss: 连续 4 赛季
 * - 紧急解雇：更衣室/财政/球迷极端情况
 */
data class DismissalParams(
    val warningTriggerSeasons: Int = 2,
    val ultimatumTriggerSeasons: Int = 3,
    val dismissTriggerSeasons: Int = 4,
    val dismissalLookbackSeasons: Int = 4,
    val emergencyDressingRoomMoraleThreshold: Int = 20,
    val emergencyWageRatioThreshold: Double = 1.0,
    val emergencyFanSatisfactionThreshold: Int = 15,
    val lowSatisfactionWarningThreshold: Double = 25.0
)

/**
 * 6 类预算申请参数（V0.2 + T22 方案 §八）。
 */
data class BudgetRequestParams(
    val cooldownDays: Int = 30,
    val maxBudgetRequestIncomeRatio: Double = 0.50, // 不超过年收入 50%
    val facilityPenaltyRatio: Double = 0.85, // 设施类申请额外 15% 惩罚
    val maxFacilityLevel: Int = 5,
    // 审批比例矩阵
    val approvalSat80HealthHealthy: Double = 1.00,
    val approvalSat80HealthAcceptable: Double = 0.80,
    val approvalSat65HealthHealthy: Double = 0.80,
    val approvalSat65HealthAcceptable: Double = 0.60,
    val approvalSat50: Double = 0.50,
    val approvalBelowThreshold: Double = 0.0
)

/**
 * 8 类董事会事件参数（V0.2 + T22 方案 §八）。
 */
data class BoardEventParams(
    // 老板注资
    val capitalInjectionMinOwnerWealth: Int = 80,
    val capitalInjectionProbabilityHealthy: Double = 0.05,
    val capitalInjectionProbabilityHighRisk: Double = 0.15,
    // 被收购传闻
    val takeoverRumorMinRisk: Int = 70,
    val takeoverRumorProbability: Double = 0.10,
    // 董事会换届
    val boardReshuffleProbabilityPerSeason: Double = 0.05,
    val boardReshuffleAmbitionDeltaRange: IntRange = -10..10,
    val boardReshufflePatienceDeltaRange: IntRange = -10..10,
    // 赛季中评估日期（1 月 1 日）
    val midSeasonReviewMonth: Int = 1,
    val midSeasonReviewDay: Int = 1
)

/**
 * 信心值变化阈值参数（任务要求：连续 3 场不胜 -5，夺冠 +20）。
 *
 * 信心值 0-100，影响解雇风险。
 */
data class ConfidenceParams(
    /** 赛季初默认信心值 */
    val initialConfidence: Int = 60,
    // 比赛结果影响（单场）
    val winDelta: Int = 1,
    val drawDelta: Int = 0,
    val loseDelta: Int = -2,
    // 连续不胜惩罚（连续 3 场不胜 -5）
    val consecutiveWinlessThreshold: Int = 3,
    val consecutiveWinlessDelta: Int = -5,
    // 赛季末成就奖励
    val winLeagueTitleDelta: Int = 20, // 夺冠 +20
    val achieveCoreGoalDelta: Int = 10, // 达成核心目标 +10
    val partialAchieveDelta: Int = 0, // 部分达成 0
    val failCoreGoalDelta: Int = -15, // 核心目标失败 -15
    // 警告等级阈值（信心值低于此值触发对应等级）
    val warningThreshold: Int = 40, // < 40 触发 WARNING
    val ultimatumThreshold: Int = 25, // < 25 触发 ULTIMATUM
    val dismissThreshold: Int = 10 // < 10 触发 DISMISS
)

/**
 * 长期目标参数（V0.2 + T22 方案 §八）。
 */
data class LongTermGoalParams(
    val reputationRise3yrDelta: Int = 15,
    val stadiumExpansion5yrCapacityDeltaPct: Double = 0.30,
    val youthFacilityUpgrade3yrLevels: Int = 2,
    val commercialGrowth5yrPct: Double = 0.50,
    val trophyWin5yrCount: Int = 1
)

/**
 * 目标降低申请参数（V0.2 + T22 方案 §八）。
 */
data class TargetLoweringParams(
    val seasonProgressMaxRatio: Double = 0.33, // 仅赛季前 1/3 阶段允许
    val satisfactionPenalty: Int = -5, // 满意度惩罚 -5
    val tierDown: Int = 1 // 每次降低 1 档
)
