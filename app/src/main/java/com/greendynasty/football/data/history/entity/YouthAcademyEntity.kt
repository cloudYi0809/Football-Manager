package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 青训学院表（history.db 只读）
 * 存储俱乐部青训学院的等级、招募范围、教练质量等。
 */
@Entity(tableName = "youth_academy")
data class YouthAcademyEntity(
    @PrimaryKey
    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "youth_level")
    val youthLevel: Int = 50,

    @ColumnInfo(name = "training_level")
    val trainingLevel: Int = 50,

    @ColumnInfo(name = "recruitment_range")
    val recruitmentRange: String?,

    @ColumnInfo(name = "academy_reputation")
    val academyReputation: Int = 50,

    @ColumnInfo(name = "academy_style")
    val academyStyle: String?,

    @ColumnInfo(name = "monthly_cost")
    val monthlyCost: Int = 0,

    @ColumnInfo(name = "u18_coach_quality")
    val u18CoachQuality: Int = 50,

    @ColumnInfo(name = "u21_coach_quality")
    val u21CoachQuality: Int = 50
)
