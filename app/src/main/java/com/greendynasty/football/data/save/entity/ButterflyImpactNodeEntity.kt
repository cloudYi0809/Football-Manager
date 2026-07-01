package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 蝴蝶效应影响节点表（save.db，V0.2）
 * 记录蝴蝶效应事件的多级影响传播节点，按深度逐级展开。
 */
@Entity(
    tableName = "butterfly_impact_node",
    indices = [
        Index(value = ["event_id", "depth"]),
        Index(value = ["target_club_id"]),
        Index(value = ["target_player_id"])
    ]
)
data class ButterflyImpactNodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "node_id")
    val nodeId: String, // 节点唯一标识（UUID）

    @ColumnInfo(name = "event_id")
    val eventId: String, // 所属事件 ID

    @ColumnInfo(name = "depth")
    val depth: Int, // 传播深度：0 = 直接影响，1+ = 间接影响

    @ColumnInfo(name = "impact_type")
    val impactType: String, // 影响类型：transfer_change / performance_change / morale_change

    @ColumnInfo(name = "target_club_id")
    val targetClubId: Int?, // 受影响俱乐部 ID

    @ColumnInfo(name = "target_player_id")
    val targetPlayerId: Int?, // 受影响球员 ID

    @ColumnInfo(name = "impact_strength")
    val impactStrength: Double, // 影响强度 0.0-1.0

    @ColumnInfo(name = "status")
    val status: String = "pending", // 状态：pending / applied / expired

    @ColumnInfo(name = "result_summary")
    val resultSummary: String? // 影响结果摘要
)
