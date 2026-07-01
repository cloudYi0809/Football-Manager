package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 性能日志表（save.db，V0.2）
 * 记录关键操作（每日推进、比赛模拟、转会处理）的耗时和内存占用。
 * 用于长程性能监控和优化。
 */
@Entity(
    tableName = "perf_log",
    indices = [Index(value = ["save_id", "action_type"])]
)
data class PerfLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0, // 日志自增主键

    @ColumnInfo(name = "save_id")
    val saveId: String?, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "log_date")
    val logDate: String, // 记录日期

    @ColumnInfo(name = "action_type")
    val actionType: String, // 操作类型：daily_advance / match_sim / transfer / save_load

    @ColumnInfo(name = "duration_ms")
    val durationMs: Int, // 耗时（毫秒）

    @ColumnInfo(name = "memory_mb")
    val memoryMb: Int?, // 内存占用（MB）

    @ColumnInfo(name = "db_size_mb")
    val dbSizeMb: Double?, // 数据库体积（MB）

    @ColumnInfo(name = "extra_json")
    val extraJson: String? // 额外信息 JSON
)
