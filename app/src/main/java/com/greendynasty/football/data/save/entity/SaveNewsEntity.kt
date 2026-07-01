package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存档新闻表（save.db）
 * 记录游戏中的新闻消息，如转会新闻、伤病新闻、赛季事件等。
 */
@Entity(
    tableName = "save_news",
    indices = [
        Index(value = ["save_id", "news_date"]),
        Index(value = ["save_id", "is_read"])
    ]
)
data class SaveNewsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "news_id")
    val newsId: Int = 0, // 新闻自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "news_date")
    val newsDate: String?, // 新闻日期（游戏内日期）

    @ColumnInfo(name = "title")
    val title: String?, // 新闻标题

    @ColumnInfo(name = "body")
    val body: String?, // 新闻正文

    @ColumnInfo(name = "news_type")
    val newsType: String?, // 新闻类型：transfer / injury / match / event

    @ColumnInfo(name = "related_player_id")
    val relatedPlayerId: Int?, // 关联球员 ID

    @ColumnInfo(name = "related_club_id")
    val relatedClubId: Int?, // 关联俱乐部 ID

    @ColumnInfo(name = "is_read")
    val isRead: Int = 0 // 是否已读：0 = 未读，1 = 已读
)
