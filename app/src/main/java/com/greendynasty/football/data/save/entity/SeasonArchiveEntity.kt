package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 赛季归档表（save.db，V0.2）
 * 每赛季结束后归档该赛季的完整数据（JSON 摘要），用于历史查询和回放。
 * 控制 save_player_state 表只保留当前赛季，旧数据归档到此表。
 */
@Entity(
    tableName = "season_archive",
    indices = [Index(value = ["save_id", "season_id"])]
)
data class SeasonArchiveEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "archive_id")
    val archiveId: Int = 0, // 归档自增主键

    @ColumnInfo(name = "save_id")
    val saveId: String, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "season_id")
    val seasonId: Int, // 赛季 ID

    @ColumnInfo(name = "archive_type")
    val archiveType: String, // 归档类型：full / summary

    @ColumnInfo(name = "summary_json")
    val summaryJson: String, // 赛季摘要 JSON（积分榜/射手榜/转会记录等）

    @ColumnInfo(name = "created_at")
    val createdAt: String // 归档创建时间
)
