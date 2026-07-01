package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 俱乐部存档状态表（save.db）
 * 记录俱乐部在存档中的当前状态：预算、排名、满意度等。
 * 每个俱乐部在每个存档中只有一条状态记录。
 */
@Entity(
    tableName = "save_club_state",
    indices = [Index(value = ["save_id", "club_id"], unique = true)]
)
data class SaveClubStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0, // 自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "club_id")
    val clubId: Int, // 俱乐部 ID（关联 history.club）

    @ColumnInfo(name = "balance")
    val balance: Int = 0, // 当前余额（含转会+工资预算）

    @ColumnInfo(name = "transfer_budget")
    val transferBudget: Int = 0, // 转会预算

    @ColumnInfo(name = "wage_budget")
    val wageBudget: Int = 0, // 工资预算（周薪上限）

    @ColumnInfo(name = "reputation")
    val reputation: Int = 50, // 俱乐部声望 0-100

    @ColumnInfo(name = "board_satisfaction")
    val boardSatisfaction: Int = 50, // 董事会满意度 0-100

    @ColumnInfo(name = "fan_satisfaction")
    val fanSatisfaction: Int = 50, // 球迷满意度 0-100

    @ColumnInfo(name = "dressing_room_morale")
    val dressingRoomMorale: Int = 50 // 更衣室士气 0-100
)
