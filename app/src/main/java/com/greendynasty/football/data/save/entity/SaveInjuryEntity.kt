package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球员伤病记录表（save.db）
 *
 * 记录存档中球员的伤病情况，包括伤病类型、预计复出日期、严重程度、恢复进度、
 * 治疗方案、复发信息、永久影响结算标记等。
 *
 * T08 伤病系统在 V0.1 schema 基础上扩展 V0.2 字段（恢复进度 / 强行复出 / 复发 / 永久影响）。
 * 所有新增字段均带默认值，保持与 T02 MatchDayExecutor 既有调用兼容。
 *
 * 严重度 [severity] 采用 Int 编码：1=MINOR / 2=MODERATE / 3=MAJOR / 4=CAREER_THREATENING
 * （详见 [com.greendynasty.football.injury.model.InjurySeverity]）。
 */
@Entity(
    tableName = "save_injury",
    indices = [
        Index(value = ["save_id", "player_id"]),
        Index(value = ["save_id", "club_id", "status"]),
        Index(value = ["save_id", "player_id", "status"]),
        Index(value = ["source_injury_id"])
    ]
)
data class SaveInjuryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "injury_id")
    val injuryId: Int = 0, // 伤病记录自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "player_id")
    val playerId: Int, // 球员 ID

    @ColumnInfo(name = "injury_type")
    val injuryType: String, // 伤病类型 code（如 STRAIN_MUSCLE / ACL_TEAR）

    @ColumnInfo(name = "start_date")
    val startDate: String, // 伤病开始日期 yyyy-MM-dd

    @ColumnInfo(name = "expected_return_date")
    val expectedReturnDate: String, // 预计复出日期

    @ColumnInfo(name = "severity")
    val severity: Int, // 严重程度 1-4（1轻/2中/3重/4职业威胁）

    @ColumnInfo(name = "recurrence_risk")
    val recurrenceRisk: Int = 0, // 复发风险 0-100

    @ColumnInfo(name = "status")
    val status: String = "active", // 状态：active/recovering/returned_early/recovered/recurred/archived

    // —— T08 V0.2 新增字段 ——
    @ColumnInfo(name = "club_id")
    val clubId: Int? = null, // 受伤时所属俱乐部

    @ColumnInfo(name = "source")
    val source: String = "MATCH_CONTACT", // 来源：MATCH_CONTACT/MATCH_NON_CONTACT/TRAINING/FATIGUE/RECURRENCE

    @ColumnInfo(name = "actual_return_date")
    val actualReturnDate: String? = null, // 实际复出日（恢复后回填）

    @ColumnInfo(name = "treatment_type")
    val treatmentType: String = "STANDARD", // 治疗方案：CONSERVATIVE/STANDARD/SURGERY/EXTERNAL_EXPERT

    @ColumnInfo(name = "recovery_progress")
    val recoveryProgress: Int = 0, // 恢复进度 0-100

    @ColumnInfo(name = "recovery_total_days")
    val recoveryTotalDays: Int = 0, // 总恢复天数

    @ColumnInfo(name = "recovery_elapsed_days")
    val recoveryElapsedDays: Int = 0, // 已恢复天数

    @ColumnInfo(name = "is_forced_return")
    val isForcedReturn: Boolean = false, // 是否强行复出

    @ColumnInfo(name = "is_recurrence")
    val isRecurrence: Boolean = false, // 是否为复发伤

    @ColumnInfo(name = "source_injury_id")
    val sourceInjuryId: Int? = null, // 复发源伤病 ID

    @ColumnInfo(name = "permanent_impact_applied")
    val permanentImpactApplied: Boolean = false, // 永久影响是否已结算

    @ColumnInfo(name = "match_id")
    val matchId: Long? = null, // 比赛中受伤时的比赛 ID

    @ColumnInfo(name = "match_minute")
    val matchMinute: Int? = null, // 受伤分钟

    @ColumnInfo(name = "notes")
    val notes: String? = null // 备注（"强行复出导致复发"等）
)
