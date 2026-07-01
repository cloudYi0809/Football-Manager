package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 存档世界状态表（save.db）
 * 记录当前游戏的全局状态：当前日期、当前赛季、玩家执教俱乐部、游戏模式等。
 * 每个存档只有一条当前世界状态记录。
 */
@Entity(tableName = "save_world_state")
data class SaveWorldStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "save_id")
    val saveId: Int = 0, // 自增主键（存档内唯一）

    @ColumnInfo(name = "save_name")
    val saveName: String, // 存档名称（玩家可见）

    @ColumnInfo(name = "current_date")
    val currentDate: String, // 游戏内当前日期

    @ColumnInfo(name = "current_season_id")
    val currentSeasonId: Int, // 当前赛季 ID（关联 history.season）

    @ColumnInfo(name = "manager_club_id")
    val managerClubId: Int, // 玩家执教的俱乐部 ID

    @ColumnInfo(name = "mode")
    val mode: String?, // 游戏模式：career / sandbox

    @ColumnInfo(name = "scenario_id")
    val scenarioId: String?, // 剧本 ID（挑战剧本用）

    @ColumnInfo(name = "config_json")
    val configJson: String?, // 开关设置 JSON（如是否启用真实转会）

    @ColumnInfo(name = "created_at")
    val createdAt: String?, // 创建时间

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? // 最后更新时间
)
