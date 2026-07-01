package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存档积分榜表（save.db）
 * 记录存档中各联赛/赛事的积分榜，按 (save_id, season_id, competition_id, club_id) 唯一标识。
 * 每场比赛结束后实时更新，避免每次查询都重新计算。
 */
@Entity(
    tableName = "save_league_table",
    indices = [
        Index(value = ["save_id", "season_id", "competition_id", "club_id"], unique = true),
        Index(value = ["save_id", "season_id", "competition_id", "position"]),
        Index(value = ["save_id", "club_id"])
    ]
)
data class SaveLeagueTableEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0, // 自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "season_id")
    val seasonId: Int, // 赛季 ID

    @ColumnInfo(name = "competition_id")
    val competitionId: Int, // 赛事 ID

    @ColumnInfo(name = "club_id")
    val clubId: Int, // 俱乐部 ID

    @ColumnInfo(name = "position")
    val position: Int = 0, // 当前排名

    @ColumnInfo(name = "played")
    val played: Int = 0, // 已赛场次

    @ColumnInfo(name = "won")
    val won: Int = 0, // 胜场

    @ColumnInfo(name = "drawn")
    val drawn: Int = 0, // 平场

    @ColumnInfo(name = "lost")
    val lost: Int = 0, // 负场

    @ColumnInfo(name = "goals_for")
    val goalsFor: Int = 0, // 进球数

    @ColumnInfo(name = "goals_against")
    val goalsAgainst: Int = 0, // 失球数

    @ColumnInfo(name = "goal_difference")
    val goalDifference: Int = 0, // 净胜球

    @ColumnInfo(name = "points")
    val points: Int = 0, // 积分

    @ColumnInfo(name = "form")
    val form: String? = null, // 近 N 场战绩，如 "WWDWL"

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null // 最后更新时间
)
