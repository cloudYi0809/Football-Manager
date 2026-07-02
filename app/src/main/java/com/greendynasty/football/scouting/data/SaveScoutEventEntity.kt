package com.greendynasty.football.scouting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T14 球探事件表（save.db，V0.2 08 §七 青年赛事发现）。
 *
 * 记录青年赛事期间触发的 4 类事件：
 * - YOUTH_HAT_TRICK：小妖帽子戏法
 * - BIG_CLUB_RUSH：豪门争夺
 * - VALUE_SURGE：身价暴涨
 * - SCOUT_STRONG_RECOMMEND：球探强烈推荐
 *
 * 事件由 [com.greendynasty.football.scouting.core.YouthTournamentScanner] 产出，
 * 通过 [com.greendynasty.football.scouting.ScoutingService.advanceDaily] 返回给 T07。
 */
@Entity(
    tableName = "scout_event",
    indices = [
        Index(value = ["save_id", "event_date"]),
        Index(value = ["save_id", "hired_id"]),
        Index(value = ["save_id", "read"])
    ]
)
data class SaveScoutEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 关联 save.scout_hired.hired_id。 */
    @ColumnInfo(name = "hired_id")
    val hiredId: Int,

    /** 关联 scout_task.task_id，nullable。 */
    @ColumnInfo(name = "task_id")
    val taskId: Int? = null,

    /** 事件类型：YOUTH_HAT_TRICK / BIG_CLUB_RUSH / VALUE_SURGE / SCOUT_STRONG_RECOMMEND。 */
    @ColumnInfo(name = "event_type")
    val eventType: String,

    /** 关联球员 ID（如有），nullable。 */
    @ColumnInfo(name = "player_id")
    val playerId: Int? = null,

    /** 青年赛事 ID（见 YouthTournament 枚举），nullable。 */
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String? = null,

    /** 事件发生日期（yyyy-MM-dd）。 */
    @ColumnInfo(name = "event_date")
    val eventDate: String,

    /** 事件摘要（用于新闻展示）。 */
    @ColumnInfo(name = "summary")
    val summary: String,

    /** 是否已读（0/1）。 */
    @ColumnInfo(name = "read")
    val read: Int = 0
)
