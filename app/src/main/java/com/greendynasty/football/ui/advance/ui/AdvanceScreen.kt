package com.greendynasty.football.ui.advance.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.api.MatchResultSummary
import com.greendynasty.football.ui.advance.state.AdvanceUiState
import com.greendynasty.football.ui.advance.viewmodel.AdvanceViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 推进页入口 Composable（T07 方案 §三）
 *
 * 结构：
 * 1. 顶部：当前日期 + 4 个推进按钮（单日/下一场/月底/7天后）
 * 2. 中部：今日比赛结果列表
 * 3. 底部：事件流列表（按优先级排序）
 *
 * 处理 3 种 UI 状态：Loading / Ready / Error。
 */
@Composable
fun AdvanceScreen(
    viewModel: AdvanceViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is AdvanceUiState.Loading -> LoadingView()
                is AdvanceUiState.Error -> ErrorView(state)
                is AdvanceUiState.Ready -> ReadyView(state, viewModel)
            }
        }
    }
}

/**
 * 推进中视图
 */
@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "加载存档中...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 错误视图
 */
@Composable
private fun ErrorView(state: AdvanceUiState.Error) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "推进失败",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "当前日期：${state.currentDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 就绪视图（主界面）
 */
@Composable
private fun ReadyView(
    state: AdvanceUiState.Ready,
    viewModel: AdvanceViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 日期 + 推进按钮区
        item {
            AdvanceControlPanel(state = state, viewModel = viewModel)
        }

        // 2. 今日比赛结果
        if (state.matches.isNotEmpty()) {
            item {
                SectionHeader(title = "今日比赛（${state.matches.size}）")
            }
            items(state.matches) { match ->
                MatchResultCard(match = match)
            }
        }

        // 3. 事件流
        if (state.events.isNotEmpty()) {
            item {
                SectionHeader(title = "事件流（${state.events.size}）")
            }
            items(state.events) { event ->
                EventCard(event = event)
            }
        }

        // 4. 空状态
        if (state.matches.isEmpty() && state.events.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无事件，点击上方按钮推进游戏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 推进控制面板（日期 + 4 个按钮）
 */
@Composable
private fun AdvanceControlPanel(
    state: AdvanceUiState.Ready,
    viewModel: AdvanceViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 当前日期
            Text(
                text = state.currentDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // 耗时信息
            if (state.lastDurationMs > 0) {
                Text(
                    text = "上次推进耗时：${state.lastDurationMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            // 4 个推进按钮（2x2 网格）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 单日推进（主按钮）
                Button(
                    onClick = viewModel::advanceOneDay,
                    enabled = !state.isAdvancing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("推进一天")
                }

                // 下一场
                OutlinedButton(
                    onClick = viewModel::advanceToNextMatch,
                    enabled = !state.isAdvancing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下一场")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 休息到月底
                OutlinedButton(
                    onClick = viewModel::advanceToEndOfMonth,
                    enabled = !state.isAdvancing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("到月底")
                }

                // 指定日期（V1 简化：固定推进 7 天）
                OutlinedButton(
                    onClick = { viewModel.advanceToDate(state.currentDate.plusDays(7)) },
                    enabled = !state.isAdvancing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("7天后")
                }
            }

            // 推进中指示
            if (state.isAdvancing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "推进中...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 分区标题
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/**
 * 比赛结果卡片
 */
@Composable
private fun MatchResultCard(match: MatchResultSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (match.isPlayerMatch) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (match.isPlayerMatch) 2.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.homeClubName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (match.isPlayerMatch) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${match.homeScore} - ${match.awayScore}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = match.awayClubName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (match.isPlayerMatch) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
            }
            if (match.isPlayerMatch) {
                Text(
                    text = "★ 玩家比赛",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 事件卡片
 */
@Composable
private fun EventCard(event: AdvanceEvent) {
    val priorityColor = when (event.priority) {
        EventPriority.URGENT -> MaterialTheme.colorScheme.error
        EventPriority.HIGH -> MaterialTheme.colorScheme.primary
        EventPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        EventPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val typeLabel = when (event.type) {
        AdvanceEventType.TRAINING_COMPLETE -> "训练"
        AdvanceEventType.CONDITION_CHANGE -> "体能"
        AdvanceEventType.INJURY_RECOVERED -> "伤病恢复"
        AdvanceEventType.INJURY_OCCURRED -> "伤病"
        AdvanceEventType.MORALE_CHANGE -> "士气"
        AdvanceEventType.SCOUT_REPORT_READY -> "球探"
        AdvanceEventType.YOUTH_PROMOTED -> "青训"
        AdvanceEventType.TRANSFER_COMPLETED -> "转会完成"
        AdvanceEventType.TRANSFER_OFFER_RECEIVED -> "转会报价"
        AdvanceEventType.AI_ACTION -> "AI行动"
        AdvanceEventType.MATCH_PLAYED -> "比赛"
        AdvanceEventType.HISTORICAL_EVENT -> "历史事件"
        AdvanceEventType.CONTRACT_EXPIRED -> "合同到期"
        AdvanceEventType.RETIREMENT_ANNOUNCED -> "退役"
        AdvanceEventType.NEWS_PUBLISHED -> "新闻"
        AdvanceEventType.BOARD_REVIEW -> "董事会"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 优先级色条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = event.priority.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
