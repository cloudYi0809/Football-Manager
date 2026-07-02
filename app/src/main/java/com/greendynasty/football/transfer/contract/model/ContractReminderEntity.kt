package com.greendynasty.football.transfer.contract.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 合同到期提醒表（save.db）。
 *
 * T12.3 由 T07 每日推进触发检查：合同剩余 ≤12/6/1 个月时生成提醒。
 * 每个球员每档提醒只生成一次（业务层校验 isHandled + level 去重）。
 *
 * 提醒级别 [ReminderLevel]：
 * - EARLY_WARNING：12 个月（建议续约）
 * - URGENT：6 个月（Bosman 可接触，需尽快续约）
 * - FINAL：1 个月（最后机会）
 * - EXPIRED：已到期（变自由球员）
 */
@Entity(
    tableName = "contract_reminder",
    indices = [
        Index(value = ["save_id", "club_id", "is_handled"]),
        Index(value = ["save_id", "player_id"]),
        Index(value = ["save_id", "level", "is_handled"])
    ]
)
data class ContractReminderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "reminder_id")
    val reminderId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 提醒级别 [ReminderLevel.name] */
    @ColumnInfo(name = "level")
    val level: String,

    /** 合同剩余月数 */
    @ColumnInfo(name = "months_remaining")
    val monthsRemaining: Int,

    /** 触发日期（游戏内日期） */
    @ColumnInfo(name = "trigger_date")
    val triggerDate: String,

    /** 是否已处理（玩家点击 / 续约发起 / 到期） */
    @ColumnInfo(name = "is_handled")
    val isHandled: Boolean = false,

    /** 建议动作（RENEW / LIST / RELEASE / EVALUATE） */
    @ColumnInfo(name = "recommended_action")
    val recommendedAction: String = "RENEW",

    /** 是否触发球员主动要求续约 */
    @ColumnInfo(name = "player_demand_triggered")
    val playerDemandTriggered: Boolean = false
)
