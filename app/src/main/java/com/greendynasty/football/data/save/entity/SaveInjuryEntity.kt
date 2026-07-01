package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球员伤病记录表（save.db）
 * 记录存档中球员的伤病情况，包括伤病类型、预计复出日期、严重程度等。
 */
@Entity(
    tableName = "save_injury",
    indices = [Index(value = ["save_id", "player_id"])]
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
    val injuryType: String, // 伤病类型（如 muscle_strain / fracture）

    @ColumnInfo(name = "start_date")
    val startDate: String, // 伤病开始日期

    @ColumnInfo(name = "expected_return_date")
    val expectedReturnDate: String, // 预计复出日期

    @ColumnInfo(name = "severity")
    val severity: Int, // 严重程度 1-3

    @ColumnInfo(name = "recurrence_risk")
    val recurrenceRisk: Int = 0, // 复发风险 0-100

    @ColumnInfo(name = "status")
    val status: String = "active" // 状态：active / recovered / chronic
)
