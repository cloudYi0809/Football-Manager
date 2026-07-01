package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球探报告表（save.db）
 * 记录球探提交的球员考察报告，包括 CA/PA 估值范围、优缺点、推荐等级等。
 */
@Entity(
    tableName = "scout_report",
    indices = [
        Index(value = ["save_id", "player_id"]),
        Index(value = ["save_id", "scout_id"])
    ]
)
data class ScoutReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "report_id")
    val reportId: Int = 0, // 报告自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "scout_id")
    val scoutId: Int, // 球探 ID

    @ColumnInfo(name = "player_id")
    val playerId: Int, // 被考察球员 ID

    @ColumnInfo(name = "assignment_id")
    val assignmentId: Int?, // 关联任务 ID

    @ColumnInfo(name = "report_date")
    val reportDate: String?, // 报告日期

    @ColumnInfo(name = "knowledge_level")
    val knowledgeLevel: Int = 1, // 考察深度 1-5

    @ColumnInfo(name = "estimated_ca_min")
    val estimatedCaMin: Int?, // 估值 CA 下限

    @ColumnInfo(name = "estimated_ca_max")
    val estimatedCaMax: Int?, // 估值 CA 上限

    @ColumnInfo(name = "estimated_pa_min")
    val estimatedPaMin: Int?, // 估值 PA 下限

    @ColumnInfo(name = "estimated_pa_max")
    val estimatedPaMax: Int?, // 估值 PA 上限

    @ColumnInfo(name = "strengths")
    val strengths: String?, // 优点描述

    @ColumnInfo(name = "weaknesses")
    val weaknesses: String?, // 缺点描述

    @ColumnInfo(name = "risk_notes")
    val riskNotes: String?, // 风险提示

    @ColumnInfo(name = "recommendation_level")
    val recommendationLevel: String? // 推荐等级：A+ / A / B / C / D
)
