package com.greendynasty.football.growth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 成长事件表（save.db，T09 方案 §三.5）
 *
 * 永久保留 6 类成长事件流，供 T24 媒体系统生成"小妖崛起"新闻。
 */
@Entity(
    tableName = "growth_event",
    indices = [
        Index(value = ["save_id", "player_id", "trigger_date"]),
        Index(value = ["save_id", "event_type", "trigger_date"]),
        Index(value = ["save_id", "club_id", "trigger_date"])
    ]
)
data class GrowthEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "trigger_date")
    val triggerDate: String, // 月结日 yyyy-MM-dd

    @ColumnInfo(name = "event_type")
    val eventType: String, // 见 GrowthEventType

    @ColumnInfo(name = "severity")
    val severity: String, // INFO/WARN/CRITICAL

    @ColumnInfo(name = "title")
    val title: String, // 玩家可见标题

    @ColumnInfo(name = "description")
    val description: String, // 玩家可见描述

    @ColumnInfo(name = "ca_at_trigger")
    val caAtTrigger: Int,

    @ColumnInfo(name = "pa_at_trigger")
    val paAtTrigger: Int,

    @ColumnInfo(name = "metric_value")
    val metricValue: Double, // 触发指标值（如月成长值）

    @ColumnInfo(name = "threshold")
    val threshold: Double, // 触发阈值

    @ColumnInfo(name = "related_player_id")
    val relatedPlayerId: Int? = null, // 导师等关联球员

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false // 玩家是否已读
)
