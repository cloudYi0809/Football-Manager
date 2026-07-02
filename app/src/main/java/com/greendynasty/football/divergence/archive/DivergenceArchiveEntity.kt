package com.greendynasty.football.divergence.archive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T21 分歧归档实体（save.db，任务 T21.4：分歧归档 + 查询）。
 *
 * 赛季归档时将当前赛季的蝴蝶事件归档到此表，保留分歧全貌供玩家历史查询。
 * 归档策略遵循 T19 SeasonArchiver 模式：season_archive 在 save.db，本表同库。
 *
 * 与 [com.greendynasty.football.data.save.entity.ButterflyEventEntity] 的区别：
 * - butterfly_event：当前赛季活跃事件（可修改状态）
 * - divergence_archive：历史归档事件（只读，按赛季索引）
 *
 * @param archiveId 归档记录唯一标识（UUID）
 * @param saveId 存档 UUID
 * @param seasonId 归档的赛季 ID
 * @param eventId 关联的蝴蝶事件 ID（butterfly_event.event_id）
 * @param triggerDate 触发日期
 * @param category 事件分类（ButterflyEventCategory.code）
 * @param triggerType 触发类型（ButterflyTriggerType.code）
 * @param importance 重要度 0-100
 * @param originalPath 原路径描述
 * @param currentPath 当前路径描述
 * @param impactSummary 影响摘要
 * @param divergenceText 分歧提示文案
 * @param hasMajorReplacement 是否有重大替代（0=否，1=是）
 * @param archivedAt 归档时间
 */
@Entity(
    tableName = "divergence_archive",
    indices = [
        Index(value = ["save_id", "season_id"]),
        Index(value = ["event_id"]),
        Index(value = ["category"]),
        Index(value = ["trigger_date"])
    ]
)
data class DivergenceArchiveEntity(
    @PrimaryKey
    @ColumnInfo(name = "archive_id")
    val archiveId: String,

    @ColumnInfo(name = "save_id")
    val saveId: String,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "event_id")
    val eventId: String,

    @ColumnInfo(name = "trigger_date")
    val triggerDate: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "trigger_type")
    val triggerType: String,

    @ColumnInfo(name = "importance")
    val importance: Int,

    @ColumnInfo(name = "original_path")
    val originalPath: String,

    @ColumnInfo(name = "current_path")
    val currentPath: String,

    @ColumnInfo(name = "impact_summary")
    val impactSummary: String,

    @ColumnInfo(name = "divergence_text")
    val divergenceText: String,

    @ColumnInfo(name = "has_major_replacement")
    val hasMajorReplacement: Int,

    @ColumnInfo(name = "archived_at")
    val archivedAt: String
)
