package com.greendynasty.football.data.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 统计缓存表（cache.db，可重建）
 * 缓存各类统计数据，如射手榜、助攻榜等，按 cache_key 区分。
 */
@Entity(
    tableName = "stats_cache",
    indices = [Index(value = ["cache_key"], unique = true)]
)
data class StatsCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "cache_key")
    val cacheKey: String, // 如 "scorer_list_EPL_2003"

    @ColumnInfo(name = "stats_json")
    val statsJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
