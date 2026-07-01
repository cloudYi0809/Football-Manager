package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史事件表（history.db 只读）
 * 存储可触发的历史事件，包括触发条件、选项、效果等（JSON 配置）。
 */
@Entity(tableName = "historical_event")
data class HistoricalEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "event_type")
    val eventType: String?,

    @ColumnInfo(name = "trigger_date")
    val triggerDate: String?,

    @ColumnInfo(name = "trigger_conditions_json")
    val triggerConditionsJson: String?,

    @ColumnInfo(name = "choices_json")
    val choicesJson: String?,

    @ColumnInfo(name = "effects_json")
    val effectsJson: String?,

    @ColumnInfo(name = "is_historical")
    val isHistorical: Int = 1
)
