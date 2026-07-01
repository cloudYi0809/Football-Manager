package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 赛季表（history.db 只读）
 * 存储历史赛季信息，如 2002/03 赛季的起止日期和标签。
 */
@Entity(tableName = "season")
data class SeasonEntity(
    @PrimaryKey
    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "year_start")
    val yearStart: Int,

    @ColumnInfo(name = "year_end")
    val yearEnd: Int,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "start_date")
    val startDate: String?,

    @ColumnInfo(name = "end_date")
    val endDate: String?,

    @ColumnInfo(name = "is_historical")
    val isHistorical: Int = 1
)
