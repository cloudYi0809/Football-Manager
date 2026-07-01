package com.greendynasty.football.data.cache.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 图片路径缓存表（cache.db，可重建）
 * 缓存球员头像、俱乐部 logo、球衣等图片的本地路径，避免重复查找。
 */
@Entity(
    tableName = "image_path_cache",
    indices = [
        Index(value = ["entity_type", "entity_id"], unique = true),
        Index(value = ["image_type"])
    ]
)
data class ImagePathCacheEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "entity_type")
    val entityType: String, // player / club / competition

    @ColumnInfo(name = "entity_id")
    val entityId: Int,

    @ColumnInfo(name = "image_type")
    val imageType: String, // portrait / logo / kit

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
