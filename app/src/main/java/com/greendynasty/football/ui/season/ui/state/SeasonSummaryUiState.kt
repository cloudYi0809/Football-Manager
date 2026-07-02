package com.greendynasty.football.ui.season.ui.state

import com.greendynasty.football.season.summary.SeasonSummary

/**
 * T19 赛季总结页 UI 状态（5 种完备状态）。
 *
 * - [Loading]：数据加载中
 * - [Empty]：无已归档赛季
 * - [Error]：数据加载错误
 * - [Locked]：功能未解锁（如未加载存档）
 * - [Normal]：展示归档赛季列表 + 选中赛季摘要
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class SeasonSummaryUiState {

    /** 加载中 */
    data object Loading : SeasonSummaryUiState()

    /** 空数据：无已归档赛季 */
    data class Empty(val reason: String = "暂无已归档赛季") : SeasonSummaryUiState()

    /** 错误 */
    data class Error(val message: String) : SeasonSummaryUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : SeasonSummaryUiState()

    /** 正常：展示归档赛季列表 + 选中赛季摘要 */
    data class Normal(
        val archivedSeasons: List<ArchivedSeasonDisplay>,
        val selectedSummary: SeasonSummary?,
        val selectedIndex: Int
    ) : SeasonSummaryUiState()
}

/**
 * 归档赛季展示模型（UI 层使用，列表项）
 */
data class ArchivedSeasonDisplay(
    val archiveId: Int,
    val seasonId: Int,
    val seasonLabel: String,
    val createdAt: String,
    val isSelected: Boolean
)
