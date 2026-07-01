package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 俱乐部基础信息表（history.db 只读）
 * 存储俱乐部名称、国家、城市、球场、设施等级等基础数据。
 */
@Entity(
    tableName = "club",
    indices = [Index(value = ["country"])]
)
data class ClubEntity(
    @PrimaryKey
    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "source_id")
    val sourceId: String?,

    @ColumnInfo(name = "club_name")
    val clubName: String,

    @ColumnInfo(name = "country")
    val country: String?,

    @ColumnInfo(name = "city")
    val city: String?,

    @ColumnInfo(name = "founded_year")
    val foundedYear: Int?,

    @ColumnInfo(name = "reputation")
    val reputation: Int = 50,

    @ColumnInfo(name = "stadium_name")
    val stadiumName: String?,

    @ColumnInfo(name = "stadium_capacity")
    val stadiumCapacity: Int?,

    @ColumnInfo(name = "training_level")
    val trainingLevel: Int = 50,

    @ColumnInfo(name = "youth_level")
    val youthLevel: Int = 50,

    @ColumnInfo(name = "finance_level")
    val financeLevel: Int = 50,

    @ColumnInfo(name = "logo_path")
    val logoPath: String?,

    @ColumnInfo(name = "kit_path")
    val kitPath: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)
