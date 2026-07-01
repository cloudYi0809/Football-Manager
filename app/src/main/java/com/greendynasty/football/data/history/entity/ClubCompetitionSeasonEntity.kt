package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 俱乐部-赛事-赛季关联表（history.db 只读）
 * 记录某赛季某俱乐部参加了哪些赛事。
 */
@Entity(
    tableName = "club_competition_season",
    indices = [
        Index(value = ["season_id", "club_id"]),
        Index(value = ["competition_id", "season_id"])
    ]
)
data class ClubCompetitionSeasonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "competition_id")
    val competitionId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int
)
