package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 存档元数据表（save.db，V0.2）
 * 每个存档对应一条记录，记录游戏版本、schema 版本、数据包信息、当前日期等。
 * save_id 为 UUID 字符串，作为存档的唯一标识。
 */
@Entity(tableName = "save_manifest")
data class SaveManifestEntity(
    @PrimaryKey
    @ColumnInfo(name = "save_id")
    val saveId: String, // 存档唯一标识（UUID）

    @ColumnInfo(name = "game_version")
    val gameVersion: String, // 游戏版本号，如 "0.2.0"

    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int, // 存档 schema 版本号，用于迁移

    @ColumnInfo(name = "data_pack_id")
    val dataPackId: String?, // 数据包 ID（关联 history.db 的 data_pack_manifest）

    @ColumnInfo(name = "data_pack_version")
    val dataPackVersion: String?, // 数据包版本

    @ColumnInfo(name = "current_date")
    val currentDate: String?, // 游戏内当前日期，如 "2003-05-30"

    @ColumnInfo(name = "created_at")
    val createdAt: String, // 存档创建时间（现实时间）

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: String, // 上次游玩时间（现实时间）

    @ColumnInfo(name = "last_checkpoint_id")
    val lastCheckpointId: String? // 最近一次检查点 ID
)
