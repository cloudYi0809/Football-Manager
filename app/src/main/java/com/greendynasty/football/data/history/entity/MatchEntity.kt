package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 比赛表（history.db 只读）
 * 存储历史真实比赛赛程与比分，以及模拟比分（用于对比）。
 */
@Entity(
    tableName = "match",
    indices = [
        Index(value = ["match_date"]),
        Index(value = ["season_id", "competition_id"]),
        Index(value = ["home_club_id", "match_date"]),
        Index(value = ["away_club_id", "match_date"])
    ]
)
data class MatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "match_id")
    val matchId: Int = 0,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "competition_id")
    val competitionId: Int,

    @ColumnInfo(name = "match_date")
    val matchDate: String,

    @ColumnInfo(name = "home_club_id")
    val homeClubId: Int,

    @ColumnInfo(name = "away_club_id")
    val awayClubId: Int,

    @ColumnInfo(name = "home_score_real")
    val homeScoreReal: Int?,

    @ColumnInfo(name = "away_score_real")
    val awayScoreReal: Int?,

    @ColumnInfo(name = "home_score_sim")
    val homeScoreSim: Int?,

    @ColumnInfo(name = "away_score_sim")
    val awayScoreSim: Int?,

    @ColumnInfo(name = "status")
    val status: String = "scheduled",

    @ColumnInfo(name = "is_historical")
    val isHistorical: Int = 1,

    @ColumnInfo(name = "match_stats_json")
    val matchStatsJson: String?
)
