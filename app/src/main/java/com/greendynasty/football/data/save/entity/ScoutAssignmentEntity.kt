package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球探任务表（save.db）
 * 记录玩家指派球探的 scouting 任务，包括地区、位置、年龄范围、预算等级等。
 */
@Entity(
    tableName = "scout_assignment",
    indices = [
        Index(value = ["save_id", "scout_id"]),
        Index(value = ["save_id", "status"])
    ]
)
data class ScoutAssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "assignment_id")
    val assignmentId: Int = 0, // 任务自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "scout_id")
    val scoutId: Int, // 球探 ID（关联 history.scout）

    @ColumnInfo(name = "region_code")
    val regionCode: String?, // 考察地区代码

    @ColumnInfo(name = "task_type")
    val taskType: String?, // 任务类型：region / player / competition

    @ColumnInfo(name = "target_position")
    val targetPosition: String?, // 目标位置过滤

    @ColumnInfo(name = "min_age")
    val minAge: Int?, // 年龄下限

    @ColumnInfo(name = "max_age")
    val maxAge: Int?, // 年龄上限

    @ColumnInfo(name = "budget_level")
    val budgetLevel: String?, // 预算等级：low / medium / high

    @ColumnInfo(name = "start_date")
    val startDate: String?, // 任务开始日期

    @ColumnInfo(name = "end_date")
    val endDate: String?, // 任务结束日期

    @ColumnInfo(name = "status")
    val status: String = "active", // 状态：active / completed / cancelled

    @ColumnInfo(name = "progress")
    val progress: Int = 0 // 进度 0-100
)
