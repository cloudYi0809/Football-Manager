package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球探表（history.db 只读）
 * 存储球探的姓名、国籍、能力值（判断当前能力、判断潜力、谈判等）。
 */
@Entity(
    tableName = "scout",
    indices = [Index(value = ["current_club_id"])]
)
data class ScoutEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "scout_id")
    val scoutId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "nationality")
    val nationality: String?,

    @ColumnInfo(name = "age")
    val age: Int?,

    @ColumnInfo(name = "current_club_id")
    val currentClubId: Int?,

    @ColumnInfo(name = "judging_current_ability")
    val judgingCurrentAbility: Int = 50,

    @ColumnInfo(name = "judging_potential")
    val judgingPotential: Int = 50,

    @ColumnInfo(name = "adaptability")
    val adaptability: Int = 50,

    @ColumnInfo(name = "negotiation")
    val negotiation: Int = 50,

    @ColumnInfo(name = "network_level")
    val networkLevel: Int = 50,

    @ColumnInfo(name = "reputation")
    val reputation: Int = 50,

    @ColumnInfo(name = "salary")
    val salary: Int = 0
)
