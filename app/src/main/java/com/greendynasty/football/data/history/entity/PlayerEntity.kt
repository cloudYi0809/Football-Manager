package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球员基础信息表（history.db 只读）
 * 存储球员的真实姓名、国籍、位置、出生日期等基础数据。
 */
@Entity(
    tableName = "player",
    indices = [
        Index(value = ["real_name"]),
        Index(value = ["nationality"]),
        Index(value = ["primary_position"])
    ]
)
data class PlayerEntity(
    @PrimaryKey
    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "source_id")
    val sourceId: String?,

    @ColumnInfo(name = "real_name")
    val realName: String,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "birth_date")
    val birthDate: String?,

    @ColumnInfo(name = "nationality")
    val nationality: String?,

    @ColumnInfo(name = "second_nationality")
    val secondNationality: String?,

    @ColumnInfo(name = "height")
    val height: Int?,

    @ColumnInfo(name = "weight")
    val weight: Int?,

    @ColumnInfo(name = "preferred_foot")
    val preferredFoot: String?,

    @ColumnInfo(name = "primary_position")
    val primaryPosition: String?,

    @ColumnInfo(name = "secondary_positions")
    val secondaryPositions: String?,

    @ColumnInfo(name = "personality")
    val personality: String?,

    @ColumnInfo(name = "retire_age_base")
    val retireAgeBase: Int = 35,

    @ColumnInfo(name = "portrait_path")
    val portraitPath: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)
