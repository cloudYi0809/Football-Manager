package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 赛事表（history.db 只读）
 * 存储联赛/杯赛的基本信息，如英超、欧冠等。
 */
@Entity(tableName = "competition")
data class CompetitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "competition_id")
    val competitionId: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "country")
    val country: String?,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "reputation")
    val reputation: Int = 50,

    @ColumnInfo(name = "level")
    val level: Int = 1,

    @ColumnInfo(name = "rules_json")
    val rulesJson: String?
)
