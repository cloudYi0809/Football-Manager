package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 蝴蝶效应事件表（save.db，V0.2）
 * 记录因玩家行为导致的蝴蝶效应事件，如改变了某球员的历史转会路径。
 * 每个事件可产生多级影响节点（ButterflyImpactNodeEntity）。
 */
@Entity(
    tableName = "butterfly_event",
    indices = [Index(value = ["save_id", "status"])]
)
data class ButterflyEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String, // 事件唯一标识（UUID）

    @ColumnInfo(name = "save_id")
    val saveId: String, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "trigger_type")
    val triggerType: String, // 触发类型：transfer_interrupted / player_development / injury_change

    @ColumnInfo(name = "source_player_id")
    val sourcePlayerId: Int?, // 源球员 ID

    @ColumnInfo(name = "source_club_id")
    val sourceClubId: Int?, // 源俱乐部 ID

    @ColumnInfo(name = "expected_club_id")
    val expectedClubId: Int?, // 原本应该去的俱乐部

    @ColumnInfo(name = "trigger_date")
    val triggerDate: String, // 触发日期

    @ColumnInfo(name = "importance")
    val importance: Int, // 重要度 1-5

    @ColumnInfo(name = "impact_budget")
    val impactBudget: Int, // 影响力预算（控制传播范围）

    @ColumnInfo(name = "max_depth")
    val maxDepth: Int = 3, // 最大传播深度

    @ColumnInfo(name = "status")
    val status: String = "pending", // 状态：pending / processing / resolved / expired

    @ColumnInfo(name = "summary")
    val summary: String? // 事件摘要描述
)
