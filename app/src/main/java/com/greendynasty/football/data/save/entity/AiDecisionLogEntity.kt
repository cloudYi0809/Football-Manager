package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 决策日志表（save.db，V0.2）
 * 记录 AI 俱乐部的每次决策，包括决策类型、目标球员、评分、预算变化等。
 * 用于 AI 行为可追溯和回归测试。
 */
@Entity(
    tableName = "ai_decision_log",
    indices = [
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "decision_date"])
    ]
)
data class AiDecisionLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0, // 日志自增主键

    @ColumnInfo(name = "save_id")
    val saveId: String, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "club_id")
    val clubId: Int, // 决策俱乐部 ID

    @ColumnInfo(name = "decision_date")
    val decisionDate: String, // 决策日期（游戏内）

    @ColumnInfo(name = "decision_type")
    val decisionType: String, // 决策类型：transfer_buy / transfer_sell / loan / contract_renew / youth_promote

    @ColumnInfo(name = "target_player_id")
    val targetPlayerId: Int?, // 目标球员 ID

    @ColumnInfo(name = "score")
    val score: Double?, // 决策评分（0-100）

    @ColumnInfo(name = "reason")
    val reason: String?, // 决策理由描述

    @ColumnInfo(name = "budget_before")
    val budgetBefore: Int?, // 决策前预算

    @ColumnInfo(name = "budget_after")
    val budgetAfter: Int?, // 决策后预算

    @ColumnInfo(name = "result")
    val result: String? // 执行结果：success / failed / skipped
)
