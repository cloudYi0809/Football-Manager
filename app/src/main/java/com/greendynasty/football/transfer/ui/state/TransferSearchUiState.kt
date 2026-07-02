package com.greendynasty.football.transfer.ui.state

import com.greendynasty.football.transfer.model.CompareResult
import com.greendynasty.football.transfer.model.PlayerRecommendation
import com.greendynasty.football.transfer.model.TransferSearchResult
import com.greendynasty.football.transfer.model.WatchlistEntry
import com.greendynasty.football.transfer.window.TransferWindowState

/**
 * 转会市场页 UI 状态（5 种完备状态）。
 *
 * - [Loading]：数据加载中
 * - [Empty]：无搜索结果
 * - [Error]：数据加载错误
 * - [Normal]：正常展示搜索/推荐结果
 * - [Locked]：功能未解锁（未加载存档）
 */
sealed class TransferSearchUiState {

    /** 加载中 */
    data object Loading : TransferSearchUiState()

    /** 空数据 */
    data class Empty(val reason: String = "暂无搜索结果") : TransferSearchUiState()

    /** 错误 */
    data class Error(val message: String) : TransferSearchUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : TransferSearchUiState()

    /** 正常：展示搜索结果列表 */
    data class Normal(
        val results: List<TransferSearchResult>,
        val windowState: TransferWindowState,
        val totalCount: Int = results.size
    ) : TransferSearchUiState()
}

/**
 * 推荐页 UI 状态。
 */
sealed class RecommendUiState {
    data object Loading : RecommendUiState()
    data class Empty(val reason: String = "暂无推荐球员") : RecommendUiState()
    data class Error(val message: String) : RecommendUiState()
    data class Locked(val reason: String = "请先加载存档") : RecommendUiState()
    data class Normal(
        val recommendations: List<PlayerRecommendation>,
        val weakPositions: Set<String>
    ) : RecommendUiState()
}

/**
 * 对比页 UI 状态。
 */
sealed class CompareUiState {
    data object Idle : CompareUiState()
    data object Loading : CompareUiState()
    data class Error(val message: String) : CompareUiState()
    data class Normal(val result: CompareResult) : CompareUiState()
}

/**
 * 观察名单 UI 状态。
 */
sealed class WatchlistUiState {
    data object Loading : WatchlistUiState()
    data class Empty(val reason: String = "观察名单为空，去搜索球员吧") : WatchlistUiState()
    data class Normal(val entries: List<WatchlistEntry>) : WatchlistUiState()
}

/** 转会市场页 Tab */
enum class TransferTab(val label: String) {
    SEARCH("搜索"),
    RECOMMEND("推荐"),
    COMPARE("对比"),
    WATCHLIST("观察名单");

    companion object {
        val DEFAULT = SEARCH
    }
}
