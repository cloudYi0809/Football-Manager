package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存档比赛表（save.db）
 * 记录玩家存档内的比赛赛程与比分，与 history.db 中的历史真实比赛区分。
 * 包含玩家参与的比赛以及 AI 俱乐部之间的比赛，用于赛季推进和积分榜计算。
 */
@Entity(
    tableName = "save_match",
    indices = [
        Index(value = ["save_id", "season_id"]),
        Index(value = ["save_id", "competition_id"]),
        Index(value = ["save_id", "match_date"]),
        Index(value = ["save_id", "home_club_id"]),
        Index(value = ["save_id", "away_club_id"]),
        Index(value = ["save_id", "status"])
    ]
)
data class SaveMatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "match_id")
    val matchId: Int = 0, // 比赛自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "season_id")
    val seasonId: Int, // 赛季 ID

    @ColumnInfo(name = "competition_id")
    val competitionId: Int, // 赛事 ID

    @ColumnInfo(name = "match_date")
    val matchDate: String, // 比赛日期（游戏内日期）

    @ColumnInfo(name = "match_round")
    val matchRound: Int? = null, // 比赛轮次

    @ColumnInfo(name = "home_club_id")
    val homeClubId: Int, // 主场俱乐部 ID

    @ColumnInfo(name = "away_club_id")
    val awayClubId: Int, // 客场俱乐部 ID

    @ColumnInfo(name = "home_score")
    val homeScore: Int? = null, // 主场比分

    @ColumnInfo(name = "away_score")
    val awayScore: Int? = null, // 客场比分

    @ColumnInfo(name = "status")
    val status: String = "scheduled", // 状态：scheduled / in_progress / finished / cancelled

    @ColumnInfo(name = "is_player_match")
    val isPlayerMatch: Int = 0, // 是否玩家俱乐部参与：1 = 是

    @ColumnInfo(name = "match_stats_json")
    val matchStatsJson: String? = null, // 比赛统计 JSON（进球/红黄牌等）

    @ColumnInfo(name = "created_at")
    val createdAt: String? = null, // 创建时间

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null // 最后更新时间
)
