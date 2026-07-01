package com.greendynasty.football.data.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球员搜索索引表（cache.db，可重建）
 * 用于转会市场搜索球员的快速索引，包含标准化名称、分词、CA、年龄、位置等。
 * 可通过 history.db 和 save.db 重建。
 */
@Entity(
    tableName = "player_search_index",
    indices = [
        Index(value = ["normalized_name"]),
        Index(value = ["current_club_id"]),
        Index(value = ["position"]),
        Index(value = ["current_ca"])
    ]
)
data class PlayerSearchIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "normalized_name")
    val normalizedName: String, // 小写去重

    @ColumnInfo(name = "search_tokens")
    val searchTokens: String, // 分词后空格分隔

    @ColumnInfo(name = "current_club_id")
    val currentClubId: Int?,

    @ColumnInfo(name = "current_ca")
    val currentCa: Int,

    @ColumnInfo(name = "age")
    val age: Int,

    @ColumnInfo(name = "position")
    val position: String,

    @ColumnInfo(name = "market_value")
    val marketValue: Int
)
