package com.greendynasty.football.ui.schedule.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.ui.schedule.model.ScheduleTab
import com.greendynasty.football.ui.schedule.ui.state.ScheduleUiState
import com.greendynasty.football.ui.schedule.viewmodel.ScheduleViewModel

/**
 * 赛程页入口 Composable。
 *
 * 结构：[ScheduleTabBar] + 内容区（根据 Tab 切换不同面板）。
 * - 我的赛程：[MatchListPanel] 平铺玩家俱乐部未来比赛
 * - 联赛：[MatchListPanel]（按轮次分组）+ [StandingViewToggle] + [LeagueTablePanel]
 * - 杯赛：[CupBracketPanel]
 *
 * 处理 4 种 UI 状态：loading / empty / error / ready。
 *
 * @param viewModel 赛程页 ViewModel
 */
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val standingView by viewModel.standingView.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果消息
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 顶部 Tab 切换
            ScheduleTabBar(
                selectedTab = selectedTab,
                onTabSelected = viewModel::onTabSelected
            )

            // 2. 内容区
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is ScheduleUiState.Loading -> LoadingView()
                    is ScheduleUiState.Empty -> EmptyScheduleView(state.reason)
                    is ScheduleUiState.Error -> ErrorView(state.message)
                    is ScheduleUiState.Ready -> when (selectedTab) {
                        ScheduleTab.MY_SCHEDULE -> MatchListPanel(
                            matches = state.myMatches,
                            playerClubId = state.playerClubId
                        )
                        ScheduleTab.LEAGUE -> Column(modifier = Modifier.fillMaxSize()) {
                            StandingViewToggle(
                                selected = standingView,
                                onChanged = viewModel::onStandingViewChanged
                            )
                            // 比赛列表（按轮次分组）
                            if (state.leagueMatchesByRound.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MatchListPanel(
                                        matchesByRound = state.leagueMatchesByRound,
                                        playerClubId = state.playerClubId
                                    )
                                }
                            }
                            // 积分榜（底部固定区域）
                            if (state.leagueTable.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LeagueTablePanel(
                                        table = state.leagueTable,
                                        playerClubId = state.playerClubId,
                                        view = standingView,
                                        promotionZoneSize = state.promotionZoneSize,
                                        relegationZoneSize = state.relegationZoneSize
                                    )
                                }
                            }
                        }
                        ScheduleTab.CUP -> CupBracketPanel(
                            stages = state.cupStages,
                            playerClubId = state.playerClubId
                        )
                    }
                }
            }
        }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Text(
                text = "加载赛程中…",
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
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
