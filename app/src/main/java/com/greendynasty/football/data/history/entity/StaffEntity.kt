package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 教练/员工表（history.db 只读）
 * 存储教练、队医、分析师等俱乐部工作人员的信息。
 */
@Entity(
    tableName = "staff",
    indices = [Index(value = ["current_club_id"])]
)
data class StaffEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "staff_id")
    val staffId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "role")
    val role: String?,

    @ColumnInfo(name = "nationality")
    val nationality: String?,

    @ColumnInfo(name = "age")
    val age: Int?,

    @ColumnInfo(name = "current_club_id")
    val currentClubId: Int?,

    @ColumnInfo(name = "ability")
    val ability: Int = 50,

    @ColumnInfo(name = "potential")
    val potential: Int = 50,

    @ColumnInfo(name = "reputation")
    val reputation: Int = 50,

    @ColumnInfo(name = "salary")
    val salary: Int = 0,

    @ColumnInfo(name = "contract_until")
    val contractUntil: String?,

    @ColumnInfo(name = "attributes_json")
    val attributesJson: String?
)
