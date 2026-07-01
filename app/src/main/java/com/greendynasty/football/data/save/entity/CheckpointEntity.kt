package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存档检查点表（save.db，V0.2）
 * 记录存档的备份检查点，支持 4 种类型：
 * - light: 每 30 天轻量检查点
 * - season: 每赛季结束检查点
 * - migration: 迁移前备份检查点
 * - user: 用户手动备份检查点
 */
@Entity(
    tableName = "checkpoint",
    indices = [
        Index(value = ["save_id", "checkpoint_type"]),
        Index(value = ["save_id", "checkpoint_date"])
    ]
)
data class CheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "checkpoint_id")
    val checkpointId: String, // 检查点唯一标识（UUID）

    @ColumnInfo(name = "save_id")
    val saveId: String, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "checkpoint_type")
    val checkpointType: String, // 检查点类型：light / season / migration / user

    @ColumnInfo(name = "checkpoint_date")
    val checkpointDate: String, // 检查点对应游戏内日期

    @ColumnInfo(name = "file_path")
    val filePath: String, // 备份文件路径

    @ColumnInfo(name = "checksum")
    val checksum: String?, // 备份文件校验和（MD5）

    @ColumnInfo(name = "created_at")
    val createdAt: String? // 检查点创建时间（现实时间）
)
