package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 联赛经济画像表（save.db，V0.2）
 * 记录各联赛的经济参数，用于 AI 俱乐部预算和身价计算。
 */
@Entity(tableName = "league_economy_profile")
data class LeagueEconomyProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "league_id")
    val leagueId: Int, // 联赛 ID

    @ColumnInfo(name = "base_multiplier")
    val baseMultiplier: Double, // 基础乘数（身价/预算基线）

    @ColumnInfo(name = "growth_rate")
    val growthRate: Double, // 年增长率

    @ColumnInfo(name = "volatility")
    val volatility: Double = 0.0, // 波动率

    @ColumnInfo(name = "notes")
    val notes: String? // 备注
)
