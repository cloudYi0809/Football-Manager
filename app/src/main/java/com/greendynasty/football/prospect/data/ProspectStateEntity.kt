package com.greendynasty.football.prospect.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T15 历史新星存档状态表（save.db，V0.2 08 §三 + 06 §二.1）。
 *
 * 与 history.historical_prospect_pool（只读模板）独立：
 * - history.historical_prospect_pool：历史新星的固有配置（player_id / 可发现日期 / 默认路径 JSON 等）
 * - save.prospect_state：玩家存档中的新星运行时状态（激活/发现/签约状态、当前路径、蝴蝶标记等）
 *
 * 状态机（V0.2 06 §二.1）：
 * ```
 * PENDING ──时间到──→ ACTIVE ──球探发现──→ DISCOVERED ──玩家签约──→ SIGNED_EARLY
 *                              │                          │
 *                              ├──默认路径──→ DEFAULT_PATH─┤
 *                              │                          │
 *                              └──AI 签约──→ SIGNED_EARLY (AI)
 * ```
 *
 * 蝴蝶效应 V1 简化：仅标记 `butterfly_triggered` + 关联 `butterfly_event_id`，
 * 完整因果链由 T20/T21 实现。
 *
 * 一个历史新星（prospectId）在每个存档中只有一条状态记录。
 */
@Entity(
    tableName = "prospect_state",
    indices = [
        Index(value = ["save_id", "prospect_id"], unique = true),
        Index(value = ["save_id", "status"]),
        Index(value = ["save_id", "region_code"]),
        Index(value = ["save_id", "player_id"])
    ]
)
data class ProspectStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 关联 history.historical_prospect_pool.prospect_id。 */
    @ColumnInfo(name = "prospect_id")
    val prospectId: Int,

    /** 关联 history.player.player_id。 */
    @ColumnInfo(name = "player_id")
    val playerId: Int,

    /** 当前状态：见 [com.greendynasty.football.prospect.model.ProspectStatus]。 */
    @ColumnInfo(name = "status")
    val status: String,

    /** 激活日期（玩家游戏内日期，yyyy-MM-dd），未激活为 null。 */
    @ColumnInfo(name = "activated_date")
    val activatedDate: String? = null,

    /** 首次发现的俱乐部 ID，未发现为 null。 */
    @ColumnInfo(name = "discovered_by_club_id")
    val discoveredByClubId: Int? = null,

    /** 首次发现日期（yyyy-MM-dd），未发现为 null。 */
    @ColumnInfo(name = "discovered_date")
    val discoveredDate: String? = null,

    /** 当前成长路径："default"（默认历史路径）/ "interrupted"（被打断）/ "ai_signed"（被 AI 签走）。 */
    @ColumnInfo(name = "current_path")
    val currentPath: String = "default",

    /** 上次路径事件执行日期（yyyy-MM-dd），用于按月推进判定。 */
    @ColumnInfo(name = "last_path_event_date")
    val lastPathEventDate: String? = null,

    /** 是否已触发蝴蝶效应（0/1）。 */
    @ColumnInfo(name = "butterfly_triggered")
    val butterflyTriggered: Int = 0,

    /** 关联 butterfly_event.event_id（V1 简化：仅记录事件 ID，不传播）。 */
    @ColumnInfo(name = "butterfly_event_id")
    val butterflyEventId: String? = null,

    /** 冗余地区代码，便于按地区查询。 */
    @ColumnInfo(name = "region_code")
    val regionCode: String,

    /** 当前 CA 快照（V1 简化：每月由路径模拟器更新）。 */
    @ColumnInfo(name = "current_ca")
    val currentCa: Int = 0,

    /** 当前 PA 快照（V1 简化：与历史 PA 一致，伤病可触发下降）。 */
    @ColumnInfo(name = "current_pa")
    val currentPa: Int = 0,

    /** 当前所属俱乐部 ID（V1 简化：跟随历史路径或玩家签约）。 */
    @ColumnInfo(name = "current_club_id")
    val currentClubId: Int? = null
)
