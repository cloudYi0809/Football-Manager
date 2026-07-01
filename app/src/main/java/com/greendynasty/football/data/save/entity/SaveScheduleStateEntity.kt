package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存档赛程生成状态（save.db）
 *
 * 记录某存档某赛季的赛程生成元数据：是否已生成、生成时间、比赛总数、
 * 联赛轮次数、杯赛/欧战/国家队是否生成等。用于防止重复生成及 ScheduleService 状态查询。
 *
 * T06 新增：按 T06 实现方案 §二.2 新增。
 */
@Entity(
    tableName = "save_schedule_state",
    indices = [Index(value = ["save_id", "season_id"], unique = true)]
)
data class SaveScheduleStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    /** 生成时间 ISO 字符串 */
    @ColumnInfo(name = "generated_at")
    val generatedAt: String,

    /** 已生成的比赛总数 */
    @ColumnInfo(name = "match_count")
    val matchCount: Int,

    /** 联赛轮次数（如 20 队 = 38 轮） */
    @ColumnInfo(name = "league_rounds")
    val leagueRounds: Int,

    /** 杯赛是否已生成：1=是，0=否 */
    @ColumnInfo(name = "cup_generated")
    val cupGenerated: Int = 0,

    /** 欧战是否已生成：1=是，0=否 */
    @ColumnInfo(name = "euro_generated")
    val euroGenerated: Int = 0,

    /** 国家队是否已生成：1=是，0=否 */
    @ColumnInfo(name = "national_generated")
    val nationalGenerated: Int = 0,

    /** 黑名单日期 JSON 数组（国际比赛日/休赛日，"YYYY-MM-DD"） */
    @ColumnInfo(name = "blackout_dates_json")
    val blackoutDatesJson: String? = null
)
