package com.greendynasty.football.prospect.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T15 历史新星路径事件表（save.db，V0.2 08 §三 + 06 §二.1）。
 *
 * 记录历史新星激活后的所有路径事件，构成"成长路径时间轴"：
 * - ACTIVATED：新星被激活（可被发现）
 * - DISCOVERED：被球探发现
 * - TRANSFER：按历史路径完成转会
 * - CA_PA_PROGRESS：每月 CA/PA 推进
 * - EARLY_SIGNED：被玩家提前签约
 * - AI_SIGNED：被 AI 俱乐部签约
 * - PATH_INTERRUPTED：默认路径被打断
 * - BUTTERFLY_TRIGGERED：触发蝴蝶效应（V1 简化：仅标记）
 *
 * UI 在新星详情页按 event_date 升序展示时间轴，构成"蝴蝶路径图"。
 *
 * @param isDefaultPath 1=按历史路径发生，0=玩家/AI 干预后发生（用于区分蝴蝶分支）
 */
@Entity(
    tableName = "prospect_path_event",
    indices = [
        Index(value = ["save_id", "prospect_id"]),
        Index(value = ["save_id", "event_date"]),
        Index(value = ["save_id", "event_type"])
    ]
)
data class ProspectPathEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "prospect_id")
    val prospectId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    /** 事件类型：见 [com.greendynasty.football.prospect.model.ProspectPathEventType]。 */
    @ColumnInfo(name = "event_type")
    val eventType: String,

    /** 事件发生日期（yyyy-MM-dd）。 */
    @ColumnInfo(name = "event_date")
    val eventDate: String,

    /** 转出俱乐部 ID（仅 TRANSFER 事件），nullable。 */
    @ColumnInfo(name = "from_club_id")
    val fromClubId: Int? = null,

    /** 转入俱乐部 ID（TRANSFER / EARLY_SIGNED / AI_SIGNED 事件），nullable。 */
    @ColumnInfo(name = "to_club_id")
    val toClubId: Int? = null,

    /** 转会费（TRANSFER 事件），nullable。 */
    @ColumnInfo(name = "transfer_fee")
    val transferFee: Int? = null,

    /** CA 推进前数值（CA_PA_PROGRESS 事件），nullable。 */
    @ColumnInfo(name = "ca_before")
    val caBefore: Int? = null,

    /** CA 推进后数值（CA_PA_PROGRESS 事件），nullable。 */
    @ColumnInfo(name = "ca_after")
    val caAfter: Int? = null,

    /** PA 推进前数值（CA_PA_PROGRESS 事件），nullable。 */
    @ColumnInfo(name = "pa_before")
    val paBefore: Int? = null,

    /** PA 推进后数值（CA_PA_PROGRESS 事件），nullable。 */
    @ColumnInfo(name = "pa_after")
    val paAfter: Int? = null,

    /** 1=按历史路径发生，0=玩家/AI 干预后发生。 */
    @ColumnInfo(name = "is_default_path")
    val isDefaultPath: Int = 1,

    /** 事件摘要（UI 展示）。 */
    @ColumnInfo(name = "summary")
    val summary: String?
)
