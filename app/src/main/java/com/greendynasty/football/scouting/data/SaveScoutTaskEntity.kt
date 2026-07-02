package com.greendynasty.football.scouting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T14 球探任务表（save.db，V0.2 08 §三.4）。
 *
 * 与现有 T13 `scout_assignment` 表并存，互不影响：
 * - T13 scout_assignment：基础任务（progress 0-100）
 * - T14 scout_task：扩展任务（duration/elapsed/budget/age/youth_tournament 等完整字段）
 *
 * 8 种任务类型见 [com.greendynasty.football.scouting.model.ScoutTaskType]。
 *
 * 状态机：
 * - PENDING（待开始）→ 推进开始 → IN_PROGRESS
 * - IN_PROGRESS → 到期 → COMPLETED
 * - IN_PROGRESS → 取消 → CANCELLED
 */
@Entity(
    tableName = "scout_task",
    indices = [
        Index(value = ["save_id", "status"]),
        Index(value = ["save_id", "hired_id"]),
        Index(value = ["save_id", "region_code"]),
        Index(value = ["save_id", "club_id"])
    ]
)
data class SaveScoutTaskEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "task_id")
    val taskId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 派遣任务的俱乐部 ID。 */
    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 关联 save.scout_hired.hired_id。 */
    @ColumnInfo(name = "hired_id")
    val hiredId: Int,

    /** 球探 ID（冗余便于查询）。 */
    @ColumnInfo(name = "scout_id")
    val scoutId: Int,

    /** 任务类型：见 ScoutTaskType 枚举（8 种）。 */
    @ColumnInfo(name = "task_type")
    val taskType: String,

    /** 15 地区之一（青年赛事任务对应赛事地区）。 */
    @ColumnInfo(name = "region_code")
    val regionCode: String,

    /** 目标位置（位置搜索用，如 ST/CM），nullable。 */
    @ColumnInfo(name = "target_position")
    val targetPosition: String? = null,

    /** 年龄范围下限。 */
    @ColumnInfo(name = "age_min")
    val ageMin: Int = 15,

    /** 年龄范围上限。 */
    @ColumnInfo(name = "age_max")
    val ageMax: Int = 35,

    /** 任务周期天数：30 / 60 / 90。 */
    @ColumnInfo(name = "duration_days")
    val durationDays: Int,

    /** 预算等级：LOW / MEDIUM / HIGH。 */
    @ColumnInfo(name = "budget_level")
    val budgetLevel: String,

    /** 任务开始日期（yyyy-MM-dd）。 */
    @ColumnInfo(name = "start_date")
    val startDate: String,

    /** 任务结束日期（startDate + durationDays）。 */
    @ColumnInfo(name = "end_date")
    val endDate: String,

    /** 已推进天数（每日推进时 +1）。 */
    @ColumnInfo(name = "elapsed_days")
    val elapsedDays: Int = 0,

    /** 任务状态：PENDING / IN_PROGRESS / COMPLETED / CANCELLED。 */
    @ColumnInfo(name = "status")
    val status: String = "IN_PROGRESS",

    /** 俱乐部观察任务的目标俱乐部 ID，nullable。 */
    @ColumnInfo(name = "target_club_id")
    val targetClubId: Int? = null,

    /** 青年赛事观察任务的赛事 ID（见 YouthTournament 枚举），nullable。 */
    @ColumnInfo(name = "youth_tournament_id")
    val youthTournamentId: String? = null,

    /** 上次产出报告日期，nullable。 */
    @ColumnInfo(name = "last_report_date")
    val lastReportDate: String? = null,

    /** 已产出报告数。 */
    @ColumnInfo(name = "report_count")
    val reportCount: Int = 0
)
