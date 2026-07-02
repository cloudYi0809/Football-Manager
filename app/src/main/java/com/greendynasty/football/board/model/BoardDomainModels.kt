package com.greendynasty.football.board.model

/**
 * T22 董事会模块领域模型集合。
 *
 * 包含业务计算用的不可变 data class：
 * - [SeasonTargetEvaluation] 赛季目标评估结果
 * - [DismissalDecision] 解雇判定结果
 * - [BudgetRequestResult] 预算申请审批结果
 * - [TargetLoweringResult] 目标降低申请结果
 * - [BoardExpectationSummary] 董事会期望摘要
 * - [ObjectiveProgress] 单项目标实时进度
 * - [BoardFeedback] 董事会决策反馈
 *
 * 与 Entity 的区别：Entity 是 Room 持久化层，这里是业务计算中间结果。
 */

/**
 * 赛季目标评估结果（V0.2 11 §四 + T22 方案 §四.2）。
 *
 * @property seasonId 赛季 ID
 * @property overallScore 综合评分 0-100
 * @property status ACHIEVED / PARTIALLY / FAILED
 * @property leagueScore 联赛目标评分
 * @property cupScore 杯赛目标评分
 * @property europeanScore 欧战目标评分
 * @property financialScore 财政目标评分
 * @property youthScore 青训目标评分
 * @property coreGoalFailed 是否有核心目标未达成（任意核心目标 < 50 分）
 */
data class SeasonTargetEvaluation(
    val seasonId: Int,
    val overallScore: Double,
    val status: String,
    val leagueScore: Double,
    val cupScore: Double,
    val europeanScore: Double,
    val financialScore: Double,
    val youthScore: Double,
    val coreGoalFailed: Boolean
) {
    companion object {
        fun empty() = SeasonTargetEvaluation(
            seasonId = 0,
            overallScore = 0.0,
            status = "PENDING",
            leagueScore = 0.0,
            cupScore = 0.0,
            europeanScore = 0.0,
            financialScore = 0.0,
            youthScore = 0.0,
            coreGoalFailed = false
        )
    }
}

/**
 * 解雇判定结果（V0.2 05 §九 + T22 方案 §四.6）。
 *
 * 4 档警告等级 + 紧急解雇标志。
 *
 * @property shouldDismiss 是否应解雇
 * @property warningLevel NONE / WARNING / ULTIMATUM / DISMISS
 * @property reason 解雇/警告原因文案
 * @property consecutiveCoreFailedSeasons 连续未达成核心目标赛季数
 * @property isEmergency 是否紧急解雇（不受缓冲约束）
 */
data class DismissalDecision(
    val shouldDismiss: Boolean,
    val warningLevel: DismissalLevel,
    val reason: String,
    val consecutiveCoreFailedSeasons: Int = 0,
    val isEmergency: Boolean = false
) {
    companion object {
        fun none() = DismissalDecision(false, DismissalLevel.NONE, "", 0, false)
    }
}

/** 4 档解雇警告等级。 */
enum class DismissalLevel(val label: String) {
    NONE("无警告"),
    WARNING("警告"),
    ULTIMATUM("最后通牒"),
    DISMISS("解雇");
}

/**
 * 预算申请审批结果（V0.2 + T22 方案 §四.4）。
 *
 * @property status APPROVED / REJECTED / NEGOTIATED
 * @property approvedAmount 实际批准金额
 * @property boardResponse 董事会回复文案
 * @property satisfactionImpact 满意度影响（申请即使批准也小扣满意度 -1）
 */
data class BudgetRequestResult(
    val status: String,
    val approvedAmount: Int,
    val boardResponse: String,
    val satisfactionImpact: Int
) {
    companion object {
        fun rejectedDueToCooldown(remainingDays: Int) = BudgetRequestResult(
            status = "REJECTED",
            approvedAmount = 0,
            boardResponse = "董事会拒绝了你的请求：上次申请被拒后尚在冷却期内（剩余 $remainingDays 天）。",
            satisfactionImpact = 0
        )
    }
}

/**
 * 目标降低申请结果。
 *
 * @property allowed 是否允许降低
 * @property reason 不允许时返回原因
 * @property newLeaguePositionTarget 降低后的联赛目标（如前 4 → 前 6）
 * @property satisfactionImpact 满意度影响（-5）
 */
data class TargetLoweringResult(
    val allowed: Boolean,
    val reason: String,
    val newLeaguePositionTarget: Int? = null,
    val satisfactionImpact: Int = 0
)

/**
 * 董事会期望摘要（基于俱乐部声望 / 财政 / 历史成绩）。
 *
 * 由 [com.greendynasty.football.board.expectation.BoardExpectationManager] 计算。
 *
 * @property clubId 俱乐部 ID
 * @property ambition 董事会野心 0-100（与 ClubProfile.ambition 协调）
 * @property patience 董事会耐心 0-100
 * @property financialStyle CONSERVATIVE / BALANCED / AGGRESSIVE
 * @property wageRatioTarget 工资/收入比上限
 * @property expectedLeaguePosition 期望联赛排名
 * @property expectedCupRound 期望杯赛轮次
 */
data class BoardExpectationSummary(
    val clubId: Int,
    val ambition: Int,
    val patience: Int,
    val financialStyle: String,
    val wageRatioTarget: Double,
    val expectedLeaguePosition: Int,
    val expectedCupRound: String
)

/**
 * 单项目标实时进度（赛季中评估）。
 *
 * @property targetType 目标类型 LEAGUE / CUP / EUROPEAN / FINANCIAL / YOUTH
 * @property targetValue 目标值
 * @property currentValue 当前值
 * @property progressPercent 完成概率 0-100
 * @property importance CORE / SECONDARY
 * @property status ON_TRACK / AT_RISK / BEHIND
 */
data class ObjectiveProgress(
    val targetType: String,
    val targetValue: String,
    val currentValue: String,
    val progressPercent: Double,
    val importance: String,
    val status: String
)

/**
 * 董事会决策反馈（V0.2 + T22 方案 §六 UI）。
 *
 * 4 档反馈：满意 / 一般 / 不满 / 解雇警告。
 *
 * @property level EXCELLENT / GOOD / ACCEPTABLE / POOR / CRITICAL
 * @property title 反馈标题
 * @property body 反馈正文
 * @property tone 语气：POSITIVE / NEUTRAL / NEGATIVE / CRITICAL
 */
data class BoardFeedback(
    val level: String,
    val title: String,
    val body: String,
    val tone: String
)

/**
 * 6 类预算申请类型枚举（V0.2 + T22 方案 §四.4）。
 */
enum class BudgetRequestType(val label: String) {
    TRANSFER_BUDGET("转会预算"),
    WAGE_BUDGET("工资预算"),
    YOUTH_FACILITY("青训设施"),
    TRAINING_FACILITY("训练设施"),
    MEDICAL_FACILITY("医疗设施"),
    STADIUM_EXPANSION("球场扩建");

    /** 是否为设施类申请（设施类有 0.85 惩罚 + 等级上限） */
    fun isFacilityType(): Boolean = this in listOf(YOUTH_FACILITY, TRAINING_FACILITY, MEDICAL_FACILITY, STADIUM_EXPANSION)
}

/**
 * 8 类董事会事件类型枚举（V0.2 + T22 方案 §四.5）。
 */
enum class BoardEventType(val label: String) {
    SEASON_START_MEETING("赛季初会议"),
    MID_SEASON_REVIEW("赛季中评估"),
    SEASON_END_SUMMARY("赛季末总结"),
    TARGET_MISSED_WARNING("目标未达成警告"),
    DISMISSAL_THREAT("解雇威胁"),
    BOARD_RESHUFFLE("董事会换届"),
    CAPITAL_INJECTION("老板注资"),
    TAKEOVER_RUMOR("被收购传闻")
}

/**
 * 5 档满意度等级枚举（V0.2 11 §四）。
 */
enum class SatisfactionLevel(val label: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    ACCEPTABLE("可接受"),
    POOR("糟糕"),
    CRITICAL("危急");

    companion object {
        /** 根据综合满意度分值判定等级 */
        fun fromScore(score: Double, config: SatisfactionLevelThresholds): SatisfactionLevel = when {
            score >= config.excellentMin -> EXCELLENT
            score >= config.goodMin -> GOOD
            score >= config.acceptableMin -> ACCEPTABLE
            score >= config.poorMin -> POOR
            else -> CRITICAL
        }
    }
}

/**
 * 5 类赛季目标类型枚举。
 */
enum class SeasonTargetType(val label: String) {
    LEAGUE("联赛排名"),
    CUP("杯赛"),
    EUROPEAN("欧战"),
    FINANCIAL("财政平衡"),
    YOUTH("青训发展")
}

/**
 * 赛季末处理结果（赛季末评估 + 解雇判定）。
 */
data class SeasonEndResult(
    val evaluation: SeasonTargetEvaluation,
    val dismissalDecision: DismissalDecision
)
