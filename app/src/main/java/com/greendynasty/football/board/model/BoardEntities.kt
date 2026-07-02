package com.greendynasty.football.board.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T22 董事会模块实体集合（save.db）。
 *
 * 严格依据 `/Users/yi/Desktop/足球经理/开发文档/T22_董事会_实现方案.md` §三 数据模型。
 *
 * 共 6 张表：
 * 1. [SeasonTargetEntity] - 赛季目标（5 类目标 + 评估结果）
 * 2. [LongTermGoalEntity] - 长期目标（3 年/5 年规划）
 * 3. [BoardSatisfactionEntity] - 满意度快照（8 因子分项 + 综合）
 * 4. [BoardConfidenceEntity] - 董事会信心值（0-100，影响解雇风险）
 * 5. [BudgetRequestEntity] - 预算申请记录（6 类申请 + 审批结果）
 * 6. [BoardEventEntity] - 董事会事件记录（8 类事件 + 玩家响应）
 *
 * saveId 类型与 [com.greendynasty.football.data.save.entity.SaveClubStateEntity] 一致使用 Int。
 */

// ==================== 1. 赛季目标 ====================

/**
 * 赛季目标表（save.db）。
 *
 * 每俱乐部每赛季一条记录，包含 5 类目标（联赛排名/杯赛/欧战/财政/青训），
 * 由 [com.greendynasty.football.board.objective.SeasonObjectiveSetter] 在赛季初生成，
 * 由 [com.greendynasty.football.board.objective.ObjectiveProgressEvaluator] 在赛季末评估。
 *
 * @property leaguePositionTarget 联赛排名目标（1=冠军，4=前 4，17=保级）
 * @property leaguePositionImportance 核心/次要（CORE / SECONDARY，影响解雇判定）
 * @property cupTarget 杯赛目标：GROUP_STAGE / QUARTER_FINAL / SEMI_FINAL / FINAL / WIN
 * @property europeanTarget 欧战目标：NONE / GROUP_STAGE / ROUND_OF_16 / QUARTER_FINAL / SEMI_FINAL / FINAL / WIN
 * @property financialWageRatioTarget 财政目标：工资/收入比上限（如 0.70）
 * @property youthPromotionTarget 青训目标：本季提拔青训球员数（如 2）
 * @property evaluationStatus 评估状态：PENDING / ACHIEVED / PARTIALLY / FAILED
 * @property evaluationScore 综合评分 0-100
 */
@Entity(
    tableName = "board_season_target",
    indices = [
        Index(value = ["save_id", "club_id", "season_id"], unique = true),
        Index(value = ["save_id", "club_id"])
    ]
)
data class SeasonTargetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "set_at")
    val setAt: String, // ISO_LOCAL_DATE，目标设定日期

    // 5 类赛季目标
    @ColumnInfo(name = "league_position_target")
    val leaguePositionTarget: Int, // 联赛排名目标（1=冠军，4=前 4，17=保级）

    @ColumnInfo(name = "league_position_importance")
    val leaguePositionImportance: String, // CORE / SECONDARY

    @ColumnInfo(name = "cup_target")
    val cupTarget: String, // GROUP_STAGE / QUARTER_FINAL / SEMI_FINAL / FINAL / WIN

    @ColumnInfo(name = "cup_importance")
    val cupImportance: String, // CORE / SECONDARY

    @ColumnInfo(name = "european_target")
    val europeanTarget: String, // NONE / GROUP_STAGE / ROUND_OF_16 / QUARTER_FINAL / SEMI_FINAL / FINAL / WIN

    @ColumnInfo(name = "european_importance")
    val europeanImportance: String, // CORE / SECONDARY

    @ColumnInfo(name = "financial_wage_ratio_target")
    val financialWageRatioTarget: Double, // 工资/收入比上限（如 0.70）

    @ColumnInfo(name = "financial_importance")
    val financialImportance: String, // CORE / SECONDARY

    @ColumnInfo(name = "youth_promotion_target")
    val youthPromotionTarget: Int, // 本季提拔青训球员数

    // 评估结果（赛季末填）
    @ColumnInfo(name = "evaluation_status")
    val evaluationStatus: String = "PENDING", // PENDING / ACHIEVED / PARTIALLY / FAILED

    @ColumnInfo(name = "evaluation_score")
    val evaluationScore: Double = 0.0, // 0-100

    @ColumnInfo(name = "evaluated_at")
    val evaluatedAt: String? = null
)

// ==================== 2. 长期目标 ====================

/**
 * 长期目标表（save.db）。
 *
 * 每俱乐部可有多条长期目标（3 年/5 年规划），按 goalType 区分。
 * 月度更新 currentMetric，到期后判定 ACHIEVED / FAILED / EXPIRED。
 *
 * @property goalType REPUTATION_RISE / STADIUM_EXPANSION / YOUTH_FACILITY_UPGRADE / COMMERCIAL_GROWTH / TROPHY_WIN
 * @property targetYear 目标年（3 年或 5 年后）
 * @property startMetric 起始指标值（如起始声望 60）
 * @property targetMetric 目标指标值（如目标声望 75）
 * @property currentMetric 当前指标值（每月更新）
 * @property progressPercent 进度百分比 0-100
 * @property status ACTIVE / ACHIEVED / FAILED / EXPIRED
 */
@Entity(
    tableName = "board_long_term_goal",
    indices = [Index(value = ["save_id", "club_id"])]
)
data class LongTermGoalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "goal_type")
    val goalType: String, // REPUTATION_RISE / STADIUM_EXPANSION / YOUTH_FACILITY_UPGRADE / COMMERCIAL_GROWTH / TROPHY_WIN

    @ColumnInfo(name = "target_year")
    val targetYear: Int, // 目标年（3 年或 5 年后）

    @ColumnInfo(name = "start_year")
    val startYear: Int,

    @ColumnInfo(name = "start_metric")
    val startMetric: Double, // 起始指标值（如起始声望 60）

    @ColumnInfo(name = "target_metric")
    val targetMetric: Double, // 目标指标值（如目标声望 75）

    @ColumnInfo(name = "current_metric")
    val currentMetric: Double, // 当前指标值（每月更新）

    @ColumnInfo(name = "progress_percent")
    val progressPercent: Double = 0.0, // 进度百分比 0-100

    @ColumnInfo(name = "status")
    val status: String = "ACTIVE" // ACTIVE / ACHIEVED / FAILED / EXPIRED
)

// ==================== 3. 满意度快照 ====================

/**
 * 董事会满意度快照表（save.db）。
 *
 * 每月一次 + 事件触发时写入，记录 8 因子分项评分 + 综合满意度。
 * 用于 UI 历史曲线展示与解雇判定依据。
 *
 * 8 因子：联赛成绩 / 杯赛成绩 / 财政 / 球迷满意度 / 转会市场 / 青训发展 / 更衣室稳定 / 经理声望
 */
@Entity(
    tableName = "board_satisfaction_snapshot",
    indices = [Index(value = ["save_id", "club_id", "snapshot_date"])]
)
data class BoardSatisfactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "snapshot_date")
    val snapshotDate: String, // ISO_LOCAL_DATE（每月一次 + 事件触发时）

    // 8 因子分项评分（0-100）
    @ColumnInfo(name = "league_performance_score")
    val leaguePerformanceScore: Double,

    @ColumnInfo(name = "cup_performance_score")
    val cupPerformanceScore: Double,

    @ColumnInfo(name = "financial_score")
    val financialScore: Double,

    @ColumnInfo(name = "fan_satisfaction_score")
    val fanSatisfactionScore: Double,

    @ColumnInfo(name = "transfer_market_score")
    val transferMarketScore: Double,

    @ColumnInfo(name = "youth_development_score")
    val youthDevelopmentScore: Double,

    @ColumnInfo(name = "dressing_room_stability_score")
    val dressingRoomStabilityScore: Double,

    @ColumnInfo(name = "manager_personal_reputation_score")
    val managerPersonalReputationScore: Double,

    // 综合满意度（0-100）
    @ColumnInfo(name = "overall_satisfaction")
    val overallSatisfaction: Double,

    @ColumnInfo(name = "satisfaction_level")
    val satisfactionLevel: String, // EXCELLENT / GOOD / ACCEPTABLE / POOR / CRITICAL

    @ColumnInfo(name = "trend_direction")
    val trendDirection: String // RISING / STABLE / FALLING
)

// ==================== 4. 董事会信心值 ====================

/**
 * 董事会信心值表（save.db）。
 *
 * 每俱乐部每赛季一条记录，记录信心值 0-100 与警告等级。
 * 信心值变化有合理阈值（如连续 3 场不胜 -5，夺冠 +20）。
 *
 * 与 [BoardSatisfactionEntity] 的区别：
 * - 满意度：8 因子综合评分（月度快照，记录历史曲线）
 * - 信心值：累积缓冲值（赛季内动态调整，影响解雇风险）
 *
 * @property confidenceValue 信心值 0-100（100=完全信任，0=即将解雇）
 * @property warningLevel 警告等级：NONE / WARNING / ULTIMATUM / DISMISS
 * @property consecutiveCoreFailedSeasons 连续未达成核心目标赛季数
 * @property lastUpdatedLastUpdated 最后更新日期
 */
@Entity(
    tableName = "board_confidence",
    indices = [Index(value = ["save_id", "club_id", "season_id"], unique = true)]
)
data class BoardConfidenceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "confidence_value")
    val confidenceValue: Int, // 信心值 0-100（100=完全信任，0=即将解雇）

    @ColumnInfo(name = "warning_level")
    val warningLevel: String = "NONE", // NONE / WARNING / ULTIMATUM / DISMISS

    @ColumnInfo(name = "consecutive_core_failed_seasons")
    val consecutiveCoreFailedSeasons: Int = 0, // 连续未达成核心目标赛季数

    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: String? = null // 最后更新日期 ISO_LOCAL_DATE
)

// ==================== 5. 预算申请 ====================

/**
 * 预算申请记录表（save.db）。
 *
 * 玩家提交预算申请后由 [com.greendynasty.football.board.repository.BoardRepository] 写入，
 * 审批结果由审批策略计算（基于满意度 + 财政健康度）。
 *
 * @property requestType TRANSFER_BUDGET / WAGE_BUDGET / YOUTH_FACILITY / TRAINING_FACILITY / MEDICAL_FACILITY / STADIUM_EXPANSION
 * @property status PENDING / APPROVED / REJECTED / NEGOTIATED
 * @property approvedAmount 实际批准金额（可能低于申请）
 * @property cooldownDaysRemaining 被拒后冷却剩余天数
 */
@Entity(
    tableName = "board_budget_request",
    indices = [Index(value = ["save_id", "club_id", "status"])]
)
data class BudgetRequestEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "request_date")
    val requestDate: String,

    @ColumnInfo(name = "request_type")
    val requestType: String, // TRANSFER_BUDGET / WAGE_BUDGET / YOUTH_FACILITY / TRAINING_FACILITY / MEDICAL_FACILITY / STADIUM_EXPANSION

    @ColumnInfo(name = "requested_amount")
    val requestedAmount: Int, // 申请金额

    @ColumnInfo(name = "justification")
    val justification: String, // 玩家填写的理由（V1 简化为枚举）

    @ColumnInfo(name = "current_satisfaction")
    val currentSatisfaction: Double, // 申请时的满意度快照

    @ColumnInfo(name = "current_financial_health")
    val currentFinancialHealth: String, // 申请时的财政健康度

    // 审批结果
    @ColumnInfo(name = "status")
    val status: String = "PENDING", // PENDING / APPROVED / REJECTED / NEGOTIATED

    @ColumnInfo(name = "approved_amount")
    val approvedAmount: Int = 0, // 实际批准金额（可能低于申请）

    @ColumnInfo(name = "board_response")
    val boardResponse: String? = null, // 董事会回复文案

    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: String? = null,

    @ColumnInfo(name = "cooldown_days_remaining")
    val cooldownDaysRemaining: Int = 0 // 被拒后冷却剩余天数
)

// ==================== 6. 董事会事件 ====================

/**
 * 董事会事件记录表（save.db）。
 *
 * 8 类事件：SEASON_START_MEETING / MID_SEASON_REVIEW / SEASON_END_SUMMARY /
 * TARGET_MISSED_WARNING / DISMISSAL_THREAT / BOARD_RESHUFFLE / CAPITAL_INJECTION / TAKEOVER_RUMOR
 *
 * @property severity INFO / WARNING / CRITICAL
 * @property playerActionRequired 玩家是否需要响应
 * @property impactSummary 影响摘要（如"满意度 -10"）
 */
@Entity(
    tableName = "board_event",
    indices = [Index(value = ["save_id", "club_id", "event_date"])]
)
data class BoardEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "event_date")
    val eventDate: String,

    @ColumnInfo(name = "event_type")
    val eventType: String, // SEASON_START_MEETING / MID_SEASON_REVIEW / SEASON_END_SUMMARY / TARGET_MISSED_WARNING / DISMISSAL_THREAT / BOARD_RESHUFFLE / CAPITAL_INJECTION / TAKEOVER_RUMOR

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "related_season_id")
    val relatedSeasonId: Int? = null,

    @ColumnInfo(name = "severity")
    val severity: String = "INFO", // INFO / WARNING / CRITICAL

    @ColumnInfo(name = "player_action_required")
    val playerActionRequired: Boolean = false, // 玩家是否需要响应

    @ColumnInfo(name = "player_response")
    val playerResponse: String? = null, // 玩家回应（如接受目标 / 拒绝目标 / 提出反驳）

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: String? = null,

    @ColumnInfo(name = "impact_summary")
    val impactSummary: String? = null // 影响摘要（如"满意度 -10"）
)
