package com.greendynasty.football.prospect.ui.state

import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.repository.ProspectStatistics
import com.greendynasty.football.prospect.repository.ProspectViewItem

/**
 * T15 历史新星池页 UI 状态（V0.2 08 §六 + T15 方案 §六）。
 *
 * 3 个 Tab：
 * - 已发现：玩家球探已发现的新星（DISCOVERED / DEFAULT_PATH / SIGNED_EARLY）
 * - 全部活跃：所有 ACTIVE + DISCOVERED + DEFAULT_PATH 状态新星
 * - 详情视图：选中某新星后展示路径时间轴
 */
data class ProspectUiState(
    val isLoading: Boolean = false,
    val statistics: ProspectStatistics = ProspectStatistics(
        totalActivated = 0,
        activeCount = 0,
        discoveredCount = 0,
        signedEarlyCount = 0,
        butterflyTriggeredCount = 0
    ),
    val poolSize: Int = 0,
    val discoveredProspects: List<ProspectViewItem> = emptyList(),
    val allActiveProspects: List<ProspectViewItem> = emptyList(),
    val selectedProspect: ProspectViewItem? = null,
    val pathEvents: List<ProspectPathEventEntity> = emptyList(),
    val message: String? = null
)

/**
 * 3 个 Tab 类型。
 */
enum class ProspectTab(val title: String) {
    DISCOVERED("已发现"),
    ACTIVE("全部活跃"),
    STATISTICS("统计")
}
