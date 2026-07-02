package com.greendynasty.football.transfer.ai.model

/**
 * T13 AI 基础转会数据模型集合（V0.2 `05_AI俱乐部决策模型.md` 基础版）。
 *
 * 包含：
 * - [AiTransferResult] / [AiTransferAction] / [AiActionType]：转会执行结果
 * - [PositionNeed] / [NeedScoreBreakdown]：阵容短板分析
 * - [TransferTarget] / [TargetScoreBreakdown]：转会目标评分
 * - [SellDecision]：卖人决策
 * - [PlayerCandidate]：候选球员轻量信息
 * - [ClubFinancialState]：俱乐部财政状态
 * - [ConstraintViolation]：防崩坏约束违反
 * - [AiOffer]：AI 生成的报价
 */

/**
 * AI 转会执行结果（单俱乐部维度）。
 *
 * @property clubId 俱乐部 ID
 * @property actions 本次执行的转会动作列表
 * @property budgetUsed 已花费预算（买入总支出）
 * @property budgetEarned 已获得预算（卖出总收入）
 * @property budgetRemaining 剩余预算
 */
data class AiTransferResult(
    val clubId: Int,
    val actions: List<AiTransferAction>,
    val budgetUsed: Int,
    val budgetEarned: Int,
    val budgetRemaining: Int
)

/**
 * 单笔 AI 转会动作。
 *
 * @property type 动作类型
 * @property playerId 球员 ID
 * @property targetClubId 目标俱乐部 ID（买入时为买方，卖出时为买方可空）
 * @property fee 转会费
 * @property reason 决策理由
 */
data class AiTransferAction(
    val type: AiActionType,
    val playerId: Int,
    val targetClubId: Int?,
    val fee: Int,
    val reason: String
)

/** AI 转会动作类型 */
enum class AiActionType(val label: String) {
    /** 买入 */
    BUY("买入"),
    /** 卖出 */
    SELL("卖出"),
    /** 外租 */
    LOAN_OUT("外租"),
    /** 释放 */
    RELEASE("释放")
}

/**
 * 位置需求评分（V0.2 §四 6 因子）。
 *
 * @property position 位置代码（GK/CB/CM/ST...）
 * @property needScore 需求评分 0-100（越高越急需补强）
 * @property starterGap 主力缺口（>0 缺口，0 刚好，<0 富余）
 * @property backupGap 替补缺口
 * @property averageAgeRisk 年龄风险 0-1
 * @property injuryRisk 伤病风险 0-1
 * @property contractExpiryRisk 合同到期风险 0-1
 * @property tacticalImportance 战术重要性（基础版固定 0.5）
 */
data class PositionNeed(
    val position: String,
    val needScore: Double,
    val starterGap: Double,
    val backupGap: Double,
    val averageAgeRisk: Double,
    val injuryRisk: Double,
    val contractExpiryRisk: Double,
    val tacticalImportance: Double
)

/**
 * 转会目标评分（V0.2 §五 9 因子，基础版固定权重）。
 *
 * @property playerId 球员 ID
 * @property targetScore 目标评分 0-100
 * @property scoreBreakdown 9 因子分解
 * @property estimatedValue 估算市场价值
 * @property psychologicalPrice 卖方心理价位（T11 PlayerValueEstimator）
 * @property maxOffer AI 最大报价
 * @property expectedWage 期望周薪
 * @property isAffordable 是否在预算可承受范围内
 */
data class TransferTarget(
    val playerId: Int,
    val targetScore: Double,
    val scoreBreakdown: TargetScoreBreakdown,
    val estimatedValue: Int,
    val psychologicalPrice: Int,
    val maxOffer: Int,
    val expectedWage: Int,
    val isAffordable: Boolean
)

/**
 * 转会目标 9 因子评分分解（V0.2 §五）。
 */
data class TargetScoreBreakdown(
    val positionNeedScore: Double,
    val currentAbilityFit: Double,
    val potentialFit: Double,
    val priceValue: Double,
    val wageAffordability: Double,
    val ageFit: Double,
    val tacticalFit: Double,
    val nationalityFit: Double,
    val commercialValue: Double
)

/**
 * 卖人决策结果（V0.2 §七 6 因子，基础版无特殊规则）。
 *
 * @property playerId 球员 ID
 * @property sellScore 卖人评分 0-100
 * @property shouldSell 是否应该卖
 * @property reason 决策理由
 */
data class SellDecision(
    val playerId: Int,
    val sellScore: Double,
    val shouldSell: Boolean,
    val reason: String
)

/**
 * 候选球员轻量信息（由 [com.greendynasty.football.transfer.ai.target.AiTargetFinder] 聚合）。
 *
 * 用于目标评分与报价生成，避免传递完整实体。
 */
data class PlayerCandidate(
    val playerId: Int,
    val playerName: String,
    val position: String,
    val age: Int,
    val nationality: String,
    val currentCa: Int,
    val potentialPa: Int,
    val currentClubId: Int?,
    val marketValue: Int,
    val wage: Int,
    val contractUntil: String?,
    val reputation: Int
)

/**
 * 俱乐部财政状态（用于目标评分与报价计算）。
 *
 * @property transferBudgetRemaining 剩余转会预算
 * @property wageBudgetRemaining 剩余工资预算
 * @property wageToIncomeRatio 工资/收入比（>0.85 高财政压力）
 * @property balance 当前余额
 */
data class ClubFinancialState(
    val transferBudgetRemaining: Int,
    val wageBudgetRemaining: Int,
    val wageToIncomeRatio: Double,
    val balance: Int
)

/**
 * 防崩坏约束违反（基础版 3 条）。
 *
 * @property code 违反代码：WINDOW_LIMIT / BUDGET / POSITION_PRIORITY
 * @property message 描述信息
 */
data class ConstraintViolation(
    val code: String,
    val message: String
)

/**
 * AI 生成的报价。
 *
 * @property playerId 目标球员 ID
 * @property fee 转会费
 * @property wage 周薪
 * @property contractYears 合同年限
 * @property psychologicalPrice 卖方心理价位（T11 估价）
 * @property marketValue 市场价值
 */
data class AiOffer(
    val playerId: Int,
    val fee: Int,
    val wage: Int,
    val contractYears: Int,
    val psychologicalPrice: Int,
    val marketValue: Int
)
