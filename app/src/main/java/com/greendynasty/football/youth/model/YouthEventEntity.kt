package com.greendynasty.football.youth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T16 青训事件表（save.db，V0.1 08 §二.6 + T16 方案 §三.6）。
 *
 * 6 种事件：黄金一代 / 豪门挖角 / 教练推荐 / 态度下降 / 国青入选 / 要求职业合同。
 * 事件状态：PENDING → RESOLVED_ACCEPTED / RESOLVED_REJECTED / EXPIRED。
 *
 * 玩家在青训学院页查看待处理事件并选择选项（choices JSON），系统应用效果并更新状态。
 */
@Entity(
    tableName = "youth_event",
    indices = [
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "trigger_date"]),
        Index(value = ["save_id", "event_type"]),
        Index(value = ["save_id", "status"])
    ]
)
data class YouthEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 事件类型：见 [YouthEventType]。 */
    @ColumnInfo(name = "event_type")
    val eventType: String,

    /** 关联青训球员 ID（黄金一代为空，群体事件）。 */
    @ColumnInfo(name = "youth_player_id")
    val youthPlayerId: Int? = null,

    /** 黄金一代关联球员 ID 列表（逗号分隔）。 */
    @ColumnInfo(name = "related_player_ids")
    val relatedPlayerIds: String? = null,

    /** 触发日期（yyyy-MM-dd）。 */
    @ColumnInfo(name = "trigger_date")
    val triggerDate: String,

    /** 重要度 1-5。 */
    @ColumnInfo(name = "importance")
    val importance: Int = 3,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    /** 选项 JSON 数组（[{"choice_id":"...","label":"...","effects":{...}}]）。 */
    @ColumnInfo(name = "choices")
    val choices: String = "[]",

    /** 事件状态：PENDING / RESOLVED_ACCEPTED / RESOLVED_REJECTED / EXPIRED。 */
    @ColumnInfo(name = "status")
    val status: String = "PENDING",

    @ColumnInfo(name = "resolved_date")
    val resolvedDate: String? = null,

    /** 处理结果摘要（UI 展示）。 */
    @ColumnInfo(name = "outcome_summary")
    val outcomeSummary: String? = null
)

/**
 * T16 青训投资升级记录表（save.db，T16 方案 §三.7）。
 *
 * 记录每次投资升级（青训等级 / 训练设施 / 招募范围 / U18 教练 / U21 教练）的明细，
 * 用于成本递增计算与历史回溯。
 */
@Entity(
    tableName = "youth_academy_investment",
    indices = [
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "club_id", "invest_date"])
    ]
)
data class YouthAcademyInvestmentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "investment_id")
    val investmentId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 升级字段：见 [InvestmentField]。 */
    @ColumnInfo(name = "invest_field")
    val investField: String,

    @ColumnInfo(name = "level_before")
    val levelBefore: Int,

    @ColumnInfo(name = "level_after")
    val levelAfter: Int,

    @ColumnInfo(name = "cost")
    val cost: Int,

    @ColumnInfo(name = "invest_date")
    val investDate: String
)
