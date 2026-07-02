package com.greendynasty.football.growth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 月度出场时间表（save.db，T09 方案 §三.4）
 *
 * T02 比赛引擎每场赛后更新出场统计，月结前由聚合器汇总为本条记录。
 * minutes_ratio = minutesPlayed / maxPossibleMinutes，影响成长速度。
 */
@Entity(
    tableName = "monthly_playing_time",
    indices = [
        Index(value = ["save_id", "player_id", "month"], unique = true),
        Index(value = ["save_id", "club_id", "month"])
    ]
)
data class MonthlyPlayingTimeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "record_id")
    val recordId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "month")
    val month: String, // yyyy-MM

    @ColumnInfo(name = "matches_played")
    val matchesPlayed: Int,

    @ColumnInfo(name = "matches_started")
    val matchesStarted: Int, // 首发

    @ColumnInfo(name = "matches_subbed")
    val matchesSubbed: Int, // 替补出场

    @ColumnInfo(name = "minutes_played")
    val minutesPlayed: Int,

    @ColumnInfo(name = "max_possible_minutes")
    val maxPossibleMinutes: Int, // 本月可上场最大分钟数

    @ColumnInfo(name = "minutes_ratio")
    val minutesRatio: Double, // minutesPlayed / maxPossibleMinutes

    @ColumnInfo(name = "avg_rating")
    val avgRating: Double? = null, // 月均赛后评分

    @ColumnInfo(name = "goals")
    val goals: Int = 0,

    @ColumnInfo(name = "assists")
    val assists: Int = 0,

    @ColumnInfo(name = "competition_breakdown_json")
    val competitionBreakdownJson: String? = null // {"league":270,"cup":90}
)
