package com.greendynasty.football.butterfly.ui.state

import com.greendynasty.football.butterfly.model.DeviationReport
import com.greendynasty.football.butterfly.repository.ButterflyEventViewItem

/**
 * T20 蝴蝶效应页 UI 状态（任务要求 8：事件列表 + 详情 + 偏差仪表盘）。
 *
 * 3 个视图区域：
 * - **偏差仪表盘**：历史偏差度量 0-100 + 等级 + 预算使用情况
 * - **事件列表**：蝴蝶事件列表（按触发日期倒序）
 * - **事件详情**：选中某事件后展示影响节点列表
 *
 * @param isLoading 加载中
 * @param deviationReport 偏差度量报告（仪表盘数据源）
 * @param events 蝴蝶事件列表
 * @param selectedEventDetail 选中的事件详情（含影响节点）
 * @param pendingCount 待处理事件数
 * @param message 操作反馈消息
 */
data class ButterflyUiState(
    val isLoading: Boolean = false,
    val deviationReport: DeviationReport? = null,
    val events: List<ButterflyEventViewItem> = emptyList(),
    val selectedEventDetail: ButterflyEventViewItem? = null,
    val pendingCount: Int = 0,
    val message: String? = null
)

/**
 * 蝴蝶效应页 Tab 类型。
 */
enum class ButterflyTab(val title: String) {
    /** 偏差仪表盘 + 事件列表。 */
    OVERVIEW("总览"),
    /** 事件详情（影响节点列表）。 */
    DETAIL("详情")
}
