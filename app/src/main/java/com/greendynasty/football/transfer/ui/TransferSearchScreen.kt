package com.greendynasty.football.transfer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.transfer.ui.state.TransferTab
import com.greendynasty.football.transfer.viewmodel.TransferSearchViewModel
import com.greendynasty.football.transfer.window.TransferWindowStatus

/**
 * 转会市场页入口 Composable（V0.1 09 §二）。
 *
 * 结构：
 * - 转会窗状态横幅（开窗 / 截止日 / 关窗）
 * - 4 Tab 切换：搜索 / 推荐 / 对比 / 观察名单
 * - 各 Tab 内容：
 *   - 搜索：[SearchFilterPanel] + [SearchResultList]
 *   - 推荐：[RecommendPanel]
 *   - 对比：[PlayerCompareView]
 *   - 观察名单：[WatchlistView]
 *
 * @param viewModel 转会市场 ViewModel
 */
@Composable
fun TransferSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: TransferSearchViewModel = viewModel(
        factory = TransferSearchViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val searchUiState by viewModel.searchUiState.collectAsState()
    val recommendUiState by viewModel.recommendUiState.collectAsState()
    val compareUiState by viewModel.compareUiState.collectAsState()
    val watchlistUiState by viewModel.watchlistUiState.collectAsState()
    val windowState by viewModel.windowState.collectAsState()
    val compareSelection by viewModel.compareSelection.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果消息
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 转会窗状态横幅
            TransferWindowBanner(state = windowState)

            // 2. Tab 切换栏
            TransferTabBar(
                selectedTab = currentTab,
                onTabSelected = { viewModel.switchTab(it) }
            )

            // 3. 各 Tab 内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (currentTab) {
                    TransferTab.SEARCH -> SearchTabContent(
                        filter = currentFilter,
                        searchUiState = searchUiState,
                        compareSelection = compareSelection,
                        onApplyFilter = { viewModel.applyFilter(it) },
                        onResetFilter = { viewModel.resetFilter() },
                        onToggleWatchlist = { viewModel.toggleWatchlist(it) },
                        onToggleCompare = { viewModel.toggleCompareSelection(it) }
                    )
                    TransferTab.RECOMMEND -> RecommendPanel(
                        state = recommendUiState
                    )
                    TransferTab.COMPARE -> PlayerCompareView(
                        state = compareUiState,
                        compareSelection = compareSelection,
                        onPerformCompare = { viewModel.performCompare() },
                        onClearSelection = { viewModel.clearCompareSelection() }
                    )
                    TransferTab.WATCHLIST -> WatchlistView(
                        state = watchlistUiState,
                        onRemove = { viewModel.toggleWatchlist(it) }
                    )
                }
            }
        }
    }
}

/** 搜索 Tab 内容：筛选面板 + 搜索结果列表 */
@Composable
private fun SearchTabContent(
    filter: com.greendynasty.football.transfer.model.TransferSearchFilter,
    searchUiState: com.greendynasty.football.transfer.ui.state.TransferSearchUiState,
    compareSelection: Set<Int>,
    onApplyFilter: (com.greendynasty.football.transfer.model.TransferSearchFilter) -> Unit,
    onResetFilter: () -> Unit,
    onToggleWatchlist: (Int) -> Unit,
    onToggleCompare: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 筛选面板（可折叠区，V1 简化为常驻显示）
        SearchFilterPanel(
            filter = filter,
            onApply = onApplyFilter,
            onReset = onResetFilter
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 搜索结果列表（height 自适应，受父级 verticalScroll 控制）
        SearchResultList(
            state = searchUiState,
            compareSelection = compareSelection,
            onToggleWatchlist = onToggleWatchlist,
            onToggleCompare = onToggleCompare
        )
    }
}

/**
 * 转会窗状态横幅（V0.1 09 §六）。
 *
 * - OPEN：绿色背景，显示窗口类型 + 剩余天数
 * - CLOSING_SOON：橙色背景，显示截止日倒计时警告
 * - CLOSED：灰色背景，提示关窗
 */
@Composable
private fun TransferWindowBanner(state: com.greendynasty.football.transfer.window.TransferWindowState) {
    val (bgColor, contentColor, icon) = when (state.status) {
        TransferWindowStatus.OPEN -> Triple(
            Color(0xFF2E7D32),
            Color.White,
            "✅"
        )
        TransferWindowStatus.CLOSING_SOON -> Triple(
            Color(0xFFEF6C00),
            Color.White,
            "⏰"
        )
        TransferWindowStatus.CLOSED -> Triple(
            Color(0xFF616161),
            Color.White,
            "🔒"
        )
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.displayText,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (state.canMakeOffer) "可报价" else "禁止报价",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.9f)
            )
        }
    }
}

/** 4 Tab 切换栏 */
@Composable
private fun TransferTabBar(
    selectedTab: TransferTab,
    onTabSelected: (TransferTab) -> Unit
) {
    val tabs = TransferTab.values()
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 0.dp
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.label,
                        fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}
