package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 压缩后的比赛事件表（save.db，V0.2 §七.2）
 *
 * 赛季归档时将 [SaveMatchEntity.matchStatsJson] 中的详细事件压缩为本表记录：
 * - 仅保留比分 / xG / 进球 / 红黄牌 / Top N 评分
 * - 压缩后清空原 [SaveMatchEntity.matchStatsJson] 字段以回收空间
 *
 * (save_id, match_id) 唯一标识一场压缩比赛。
 *
 * T19 新增。
 */
@Entity(
    tableName = "compressed_match",
    indices = [
        Index(value = ["save_id", "match_id"], unique = true),
        Index(value = ["save_id", "season_id"])
    ]
)
data class CompressedMatchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0, // 自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "season_id")
    val seasonId: Int, // 赛季 ID

    @ColumnInfo(name = "match_id")
    val matchId: Int, // 关联 save_match.match_id

    @ColumnInfo(name = "home_club_id")
    val homeClubId: Int, // 主场俱乐部 ID

    @ColumnInfo(name = "away_club_id")
    val awayClubId: Int, // 客场俱乐部 ID

    @ColumnInfo(name = "home_score")
    val homeScore: Int, // 主场比分

    @ColumnInfo(name = "away_score")
    val awayScore: Int, // 客场比分

    @ColumnInfo(name = "home_xg")
    val homeXg: Double = 0.0, // 主场 xG

    @ColumnInfo(name = "away_xg")
    val awayXg: Double = 0.0, // 客场 xG

    @ColumnInfo(name = "summary_json")
    val summaryJson: String, // 压缩事件 JSON（进球+红黄牌+Top N 评分）

    @ColumnInfo(name = "created_at")
    val createdAt: String // 归档时间
)
