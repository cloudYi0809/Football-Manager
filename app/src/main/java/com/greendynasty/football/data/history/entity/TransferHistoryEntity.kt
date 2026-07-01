package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史转会记录表（history.db 只读）
 * 记录历史真实转会，包括转会费、类型、是否被蝴蝶效应打断等。
 */
@Entity(
    tableName = "transfer_history",
    indices = [
        Index(value = ["player_id"]),
        Index(value = ["transfer_date"])
    ]
)
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transfer_id")
    val transferId: Int = 0,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "from_club_id")
    val fromClubId: Int?,

    @ColumnInfo(name = "to_club_id")
    val toClubId: Int?,

    @ColumnInfo(name = "transfer_date")
    val transferDate: String,

    @ColumnInfo(name = "fee")
    val fee: Int = 0,

    @ColumnInfo(name = "transfer_type")
    val transferType: String?,

    @ColumnInfo(name = "season_id")
    val seasonId: Int?,

    @ColumnInfo(name = "is_historical")
    val isHistorical: Int = 1,

    @ColumnInfo(name = "was_interrupted")
    val wasInterrupted: Int = 0,

    @ColumnInfo(name = "notes")
    val notes: String?
)
