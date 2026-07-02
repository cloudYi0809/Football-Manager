package com.greendynasty.football.scouting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T14 球探报告表（save.db，V0.2 08 §四）。
 *
 * 与现有 T13 `scout_report` 表并存，互不影响：
 * - T13 scout_report：基础报告（knowledgeLevel 1-5 + CA/PA 估值）
 * - T14 scout_report_detail：5 级报告（按观察天数解锁，含性格/隐藏标签/签约难度/真实 PA 等）
 *
 * 5 级报告信息解锁（V0.2 08 §四）：
 * - L1 初次发现：姓名/年龄/地区/位置/初步特点
 * - L2 粗略报告：CA/PA 区间 ±12 + 优势 + 风险
 * - L3 标准报告：较窄 CA/PA ±7 + 性格 + 签约难度
 * - L4 深度报告：成长速度 + 隐藏标签 + 适配战术
 * - L5 完全掌握：真实 PA + 伤病倾向 + 职业态度
 *
 * 一名球员在同一俱乐部由同一球探观察只有一份报告（按 observationDays 升级）。
 */
@Entity(
    tableName = "scout_report_detail",
    indices = [
        Index(value = ["save_id", "player_id", "club_id"], unique = true),
        Index(value = ["save_id", "hired_id"]),
        Index(value = ["save_id", "report_level"]),
        Index(value = ["save_id", "created_date"]),
        Index(value = ["save_id", "task_id"])
    ]
)
data class SaveScoutReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "report_id")
    val reportId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 被考察球员 ID（关联 history.player / save_player_state）。 */
    @ColumnInfo(name = "player_id")
    val playerId: Int,

    /** 关联 save.scout_hired.hired_id。 */
    @ColumnInfo(name = "hired_id")
    val hiredId: Int,

    /** 球探 ID（冗余便于查询）。 */
    @ColumnInfo(name = "scout_id")
    val scoutId: Int,

    /** 关联 scout_task.task_id。 */
    @ColumnInfo(name = "task_id")
    val taskId: Int,

    /** 派遣任务的俱乐部 ID。 */
    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 报告等级 1-5（见 ScoutReportLevel 枚举）。 */
    @ColumnInfo(name = "report_level")
    val reportLevel: Int = 1,

    /** 地区代码（任务区域）。 */
    @ColumnInfo(name = "region_code")
    val regionCode: String,

    // ==================== 等级 1：初次发现（始终可见） ====================

    @ColumnInfo(name = "player_name")
    val playerName: String,

    @ColumnInfo(name = "player_age")
    val playerAge: Int,

    @ColumnInfo(name = "player_position")
    val playerPosition: String,

    @ColumnInfo(name = "player_region")
    val playerRegion: String,

    /** 初步特点（JSON 数组，如 ["速度型","左脚"]）。 */
    @ColumnInfo(name = "initial_traits")
    val initialTraits: String? = null,

    // ==================== 等级 2：粗略报告 ====================

    @ColumnInfo(name = "ca_range_low")
    val caRangeLow: Int? = null,

    @ColumnInfo(name = "ca_range_high")
    val caRangeHigh: Int? = null,

    @ColumnInfo(name = "pa_range_low")
    val paRangeLow: Int? = null,

    @ColumnInfo(name = "pa_range_high")
    val paRangeHigh: Int? = null,

    /** 优势（JSON 数组）。 */
    @ColumnInfo(name = "strengths")
    val strengths: String? = null,

    /** 风险（JSON 数组）。 */
    @ColumnInfo(name = "risks")
    val risks: String? = null,

    // ==================== 等级 3：标准报告 ====================

    @ColumnInfo(name = "ca_narrow_low")
    val caNarrowLow: Int? = null,

    @ColumnInfo(name = "ca_narrow_high")
    val caNarrowHigh: Int? = null,

    @ColumnInfo(name = "pa_narrow_low")
    val paNarrowLow: Int? = null,

    @ColumnInfo(name = "pa_narrow_high")
    val paNarrowHigh: Int? = null,

    /** 性格标签。 */
    @ColumnInfo(name = "personality")
    val personality: String? = null,

    /** 签约难度 1-100。 */
    @ColumnInfo(name = "sign_difficulty")
    val signDifficulty: Int? = null,

    // ==================== 等级 4：深度报告 ====================

    /** 成长速度：极慢/慢/中/快/极快。 */
    @ColumnInfo(name = "growth_speed")
    val growthSpeed: String? = null,

    /** 隐藏标签（JSON 数组，如 ["大赛型","领袖"]）。 */
    @ColumnInfo(name = "hidden_tags")
    val hiddenTags: String? = null,

    /** 适配战术（JSON 字符串）。 */
    @ColumnInfo(name = "tactical_fit")
    val tacticalFit: String? = null,

    // ==================== 等级 5：完全掌握 ====================

    /** 真实潜力 PA（解锁真实值）。 */
    @ColumnInfo(name = "real_pa")
    val realPa: Int? = null,

    /** 伤病倾向 1-100。 */
    @ColumnInfo(name = "injury_proneness")
    val injuryProneness: Int? = null,

    /** 职业态度 1-100。 */
    @ColumnInfo(name = "professionalism")
    val professionalism: Int? = null,

    // ==================== 通用 ====================

    /** 是否历史新星（T15 衔接，0/1）。 */
    @ColumnInfo(name = "is_historical_prospect")
    val isHistoricalProspect: Int = 0,

    /** 球探推荐等级 0-100（玩家可手动标记）。 */
    @ColumnInfo(name = "scout_recommendation")
    val scoutRecommendation: Int = 0,

    /** 报告创建日期。 */
    @ColumnInfo(name = "created_date")
    val createdDate: String,

    /** 报告最后更新日期。 */
    @ColumnInfo(name = "last_updated_date")
    val lastUpdatedDate: String,

    /** 累计观察天数（决定可升级等级）。 */
    @ColumnInfo(name = "observation_days")
    val observationDays: Int = 0
)
