package com.greendynasty.football.ui.growth.ui.state

import com.greendynasty.football.growth.model.GrowthEventEntity
import com.greendynasty.football.growth.model.GrowthSnapshotEntity
import com.greendynasty.football.growth.model.MonthlyGrowthResult
import com.greendynasty.football.growth.model.PlayerGrowthSummary

/**
 * 成长中心页 UI 状态（6 种完备状态，对齐 InjuryUiState 模式）。
 *
 * - [Loading]：数据加载中
 * - [Empty]：无成长记录（新存档首月）
 * - [Error]：数据加载错误
 * - [Normal]：展示月结报告 + 球员成长列表 + 事件流
 * - [Locked]：功能未解锁（未加载存档）
 * - [Warning]：有异常成长事件但仍展示列表
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class GrowthUiState {

    /** 加载中 */
    data object Loading : GrowthUiState()

    /** 空数据：无成长快照 */
    data class Empty(val reason: String = "暂无成长记录") : GrowthUiState()

    /** 错误 */
    data class Error(val message: String) : GrowthUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : GrowthUiState()

    /** 正常：展示月结报告 + 球员成长 + 事件流 */
    data class Normal(
        val lastResult: MonthlyGrowthResult?,
        val topGrowers: List<PlayerGrowthSummary>,
        val topDecliners: List<PlayerGrowthSummary>,
        val recentEvents: List<GrowthEventEntity>
    ) : GrowthUiState()

    /** 警告：有 CRITICAL 级事件 */
    data class Warning(
        val message: String,
        val lastResult: MonthlyGrowthResult?,
        val topGrowers: List<PlayerGrowthSummary>,
        val topDecliners: List<PlayerGrowthSummary>,
        val recentEvents: List<GrowthEventEntity>
    ) : GrowthUiState()
}

/**
 * 球员成长详情展示模型（单球员页面用）
 */
data class PlayerGrowthDetail(
    val playerId: Int,
    val playerName: String,
    val snapshots: List<GrowthSnapshotEntity>,
    val events: List<GrowthEventEntity>
)

/**
 * 成长事件展示模型（UI 层使用，附球员姓名）
 */
data class GrowthEventDisplay(
    val event: GrowthEventEntity,
    val playerName: String,
    val severityDisplayName: String,
    val typeDisplayName: String
)
