package com.greendynasty.football.data.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 积分榜缓存表（cache.db，可重建）
 * 缓存各联赛/赛事的积分榜，按 cache_key 区分（如 "league_table_EPL_2003"）。
 */
@Entity(
    tableName = "ranking_cache",
    indices = [Index(value = ["cache_key"], unique = true)]
)
data class RankingCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "cache_key")
    val cacheKey: String, // 如 "league_table_EPL_2003"

    @ColumnInfo(name = "ranking_json")
    val rankingJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @ColumnInfo(name = "expires_at")
    val expiresAt: String?
)
