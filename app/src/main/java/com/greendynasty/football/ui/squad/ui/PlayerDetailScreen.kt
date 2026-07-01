package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.squad.data.PlayerDetail
import com.greendynasty.football.ui.squad.model.PlayerAction
import com.greendynasty.football.ui.squad.ui.state.PlayerDetailUiState
import com.greendynasty.football.ui.squad.viewmodel.PlayerDetailViewModel

/**
 * 球员详情页。
 *
 * 结构：
 * - [TopAppBar]：返回 + 球员姓名
 * - 顶部操作栏：7 种 [PlayerAction]
 * - [ScrollableTabRow]：10 个模块横向 Tab
 * - 内容区：当前选中模块的 Section
 *
 * @param playerId 球员 ID
 * @param viewModel 球员详情 ViewModel
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    playerId: Int,
    viewModel: PlayerDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playerId) {
        viewModel.loadPlayer(playerId)
    }
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    var pendingAction by remember { mutableStateOf<PlayerAction?>(null) }
    var selectedSection by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState as? PlayerDetailUiState.Normal)?.detail?.basicInfo?.name
                            ?: "球员详情"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部操作栏（7 种操作）
            PlayerDetailActionBar(
                onActionClick = { action ->
                    if (action.needConfirm) pendingAction = action
                    else viewModel.performAction(playerId, action)
                }
            )

            when (val state = uiState) {
                is PlayerDetailUiState.Loading -> LoadingBox()
                is PlayerDetailUiState.Error -> ErrorBox(state.message)
                is PlayerDetailUiState.Normal -> {
                    // 10 模块横向 Tab
                    ScrollableTabRow(
                        selectedTabIndex = selectedSection,
                        edgePadding = 0.dp
                    ) {
                        DetailSection.values().forEachIndexed { index, section ->
                            Tab(
                                selected = selectedSection == index,
                                onClick = { selectedSection = index },
                                text = { Text(section.displayName) }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DetailSectionContent(
                            section = DetailSection.values()[selectedSection],
                            detail = state.detail
                        )
                    }
                }
            }
        }
    }

    // 二次确认
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("确认操作") },
            text = { Text("确定要执行「${action.displayName}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performAction(playerId, action)
                    pendingAction = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            }
        )
    }
}

/** 顶部 7 操作栏 */
@Composable
private fun PlayerDetailActionBar(onActionClick: (PlayerAction) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = PlayerAction.LIST_ACTIONS, key = { it.name }) { action ->
            FilledTonalButton(onClick = { onActionClick(action) }) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.displayName,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = " ${action.displayName}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

/** 详情页 10 个模块 */
private enum class DetailSection(val displayName: String) {
    BASIC("基础信息"),
    ATTRIBUTES("属性面板"),
    POSITION_FIT("位置适应"),
    GROWTH_CURVE("成长曲线"),
    SEASON_STATS("赛季数据"),
    CONTRACT("合同信息"),
    TRANSFER_HISTORY("转会记录"),
    INJURY_HISTORY("伤病记录"),
    SCOUT_REPORT("球探报告"),
    TRAINING("训练计划")
}

/** 按当前 Tab 渲染对应模块 */
@Composable
private fun DetailSectionContent(section: DetailSection, detail: PlayerDetail) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        when (section) {
            DetailSection.BASIC -> BasicInfoSection(detail.basicInfo)
            DetailSection.ATTRIBUTES -> AttributesSection(detail)
            DetailSection.POSITION_FIT -> PositionFitSection(detail.positionFit)
            DetailSection.GROWTH_CURVE -> GrowthCurveSection(detail.growthCurve)
            DetailSection.SEASON_STATS -> SeasonStatsSection(detail.seasonStats)
            DetailSection.CONTRACT -> ContractInfoSection(detail.contract)
            DetailSection.TRANSFER_HISTORY -> TransferHistorySection(detail.transferHistory)
            DetailSection.INJURY_HISTORY -> InjuryHistorySection(detail.injuryHistory)
            DetailSection.SCOUT_REPORT -> ScoutReportSection(detail.scoutReport)
            DetailSection.TRAINING -> TrainingPlanSection(detail.trainingPlan)
        }
    }
}
