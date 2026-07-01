package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 经纪人表（history.db 只读）
 * 存储球员经纪人的信息，包括贪婪度、谈判能力、媒体影响力等。
 */
@Entity(tableName = "agent")
data class AgentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "agent_id")
    val agentId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "nationality")
    val nationality: String?,

    @ColumnInfo(name = "greed")
    val greed: Int = 50,

    @ColumnInfo(name = "negotiation")
    val negotiation: Int = 50,

    @ColumnInfo(name = "media_influence")
    val mediaInfluence: Int = 50,

    @ColumnInfo(name = "relationship_level")
    val relationshipLevel: Int = 50,

    @ColumnInfo(name = "style")
    val style: String?
)
