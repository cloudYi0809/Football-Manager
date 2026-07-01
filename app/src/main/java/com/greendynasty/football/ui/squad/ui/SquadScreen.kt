package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.ui.squad.model.PlayerAction
import com.greendynasty.football.ui.squad.ui.state.SquadUiState
import com.greendynasty.football.ui.squad.viewmodel.SquadViewModel

/**
 * 阵容页入口 Composable。
 *
 * 结构：[SquadTabBar] + [SquadFilterBar] + [SquadListPanel]（或状态视图）。
 * 处理 6 种 UI 状态：loading / empty / error / normal / locked / warning。
 *
 * 交互：
 * - 切换梯队、筛选、排序、搜索 → ViewModel 响应式刷新
 * - 长按球员行 → [PlayerActionSheet]
 * - 单击球员行 → [onNavigateToDetail]
 *
 * @param viewModel 阵容页 ViewModel
 * @param onNavigateToDetail 跳转球员详情回调
 */
@Composable
fun SquadScreen(
    viewModel: SquadViewModel = viewModel(),
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val currentSort by viewModel.currentSort.collectAsState()
    val query by viewModel.query.collectAsState()
    val actionSheetPlayerId by viewModel.actionSheetPlayerId.collectAsState()
    val selectedPlayerId by viewModel.selectedPlayerId.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 导航事件消费
    LaunchedEffect(selectedPlayerId) {
        selectedPlayerId?.let {
            onNavigateToDetail(it)
            viewModel.consumeNavigation()
        }
    }

    // 操作结果消息
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    // 二次确认对话框
    var pendingAction by remember { mutableStateOf<PlayerAction?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 顶部梯队切换
            SquadTabBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.switchTab(it) }
            )

            // 2. 筛选/排序条
            SquadFilterBar(
                query = query,
                filter = currentFilter,
                sort = currentSort,
                onQueryChange = { viewModel.search(it) },
                onFilterChange = { viewModel.applyFilter(it) },
                onSortChange = { viewModel.applySort(it) }
            )

            // 3. 列表 / 状态视图
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is SquadUiState.Loading -> LoadingView()
                    is SquadUiState.Empty -> EmptyView(state.reason)
                    is SquadUiState.Error -> ErrorView(state.message)
                    is SquadUiState.Locked -> LockedView(state.reason)
                    is SquadUiState.Warning -> {
                        Column {
                            WarningBanner(state.message)
                            SquadListPanel(
                                players = state.players,
                                currentSort = currentSort,
                                onSortChange = { viewModel.applySort(it) },
                                onPlayerClick = { viewModel.onPlayerClick(it) },
                                onPlayerLongClick = { viewModel.onPlayerLongClick(it) }
                            )
                        }
                    }
                    is SquadUiState.Normal -> SquadListPanel(
                        players = state.players,
                        currentSort = currentSort,
                        onSortChange = { viewModel.applySort(it) },
                        onPlayerClick = { viewModel.onPlayerClick(it) },
                        onPlayerLongClick = { viewModel.onPlayerLongClick(it) }
                    )
                }
            }
        }
    }

    // 长按操作弹层
    if (actionSheetPlayerId != null) {
        PlayerActionSheet(
            onActionClick = { action ->
                if (action.needConfirm) {
                    pendingAction = action
                } else {
                    viewModel.performAction(action)
                }
            },
            onDismiss = { viewModel.consumeActionSheet() }
        )
    }

    // 二次确认
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("确认操作") },
            text = { Text("确定要执行「${action.displayName}」吗？此操作可能影响球队状态。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performAction(action)
                    pendingAction = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            }
        )
    }
}

// ==================== 状态视图 ====================

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text("加载阵容中…", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun EmptyView(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun LockedView(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🔒",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = reason,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⚠ $message",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
