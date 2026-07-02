package com.greendynasty.football.ui.growth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.growth.model.GrowthEventEntity
import com.greendynasty.football.growth.model.PlayerGrowthSummary
import com.greendynasty.football.ui.growth.ui.state.GrowthUiState
import com.greendynasty.football.ui.growth.viewmodel.GrowthViewModel

/**
 * 成长中心页（T09 成长月结模块 UI 入口）
 *
 * 轻量级记录页设计：
 * - 顶部：本月成长摘要卡片（Top growers + Top decliners）
 * - 中部：成长事件流（6 类事件，按日期倒序）
 * - 底部：操作提示
 */
@Composable
fun GrowthScreen(
    viewModel: GrowthViewModel = viewModel(factory = GrowthViewModel.factory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果提示
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is GrowthUiState.Loading -> LoadingView(padding)
            is GrowthUiState.Locked -> EmptyView(padding, state.reason)
            is GrowthUiState.Empty -> EmptyView(padding, state.reason)
            is GrowthUiState.Error -> ErrorView(padding, state.message)
            is GrowthUiState.Normal -> NormalView(
                padding = padding,
                topGrowers = state.topGrowers,
                topDecliners = state.topDecliners,
                recentEvents = state.recentEvents,
                onMarkRead = viewModel::markEventsRead
            )
            is GrowthUiState.Warning -> NormalView(
                padding = padding,
                topGrowers = state.topGrowers,
                topDecliners = state.topDecliners,
                recentEvents = state.recentEvents,
                onMarkRead = viewModel::markEventsRead,
                warningMessage = state.message
            )
        }
    }
}

// ==================== 视图组件 ====================

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(padding: PaddingValues, reason: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = reason, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(padding: PaddingValues, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NormalView(
    padding: PaddingValues,
    topGrowers: List<PlayerGrowthSummary>,
    topDecliners: List<PlayerGrowthSummary>,
    recentEvents: List<GrowthEventEntity>,
    onMarkRead: (List<Int>) -> Unit,
    warningMessage: String? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 警告条
        if (warningMessage != null) {
            item {
                WarningBanner(warningMessage)
            }
        }

        // Top Growers
        if (topGrowers.isNotEmpty()) {
            item {
                SectionTitle("成长之星 Top ${topGrowers.size}")
            }
            items(topGrowers) { summary ->
                GrowthSummaryCard(summary, isGrower = true)
            }
        }

        // Top Decliners
        if (topDecliners.isNotEmpty()) {
            item {
                SectionTitle("状态下滑 Top ${topDecliners.size}")
            }
            items(topDecliners) { summary ->
                GrowthSummaryCard(summary, isGrower = false)
            }
        }

        // 事件流
        if (recentEvents.isNotEmpty()) {
            item {
                SectionTitle("成长事件流")
            }
            items(recentEvents) { event ->
                GrowthEventCard(event)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        onMarkRead(recentEvents.filter { !it.isRead }.map { it.eventId })
                    }) {
                        Text("全部标记已读")
                    }
                }
            }
        }

        // 底部占位
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun WarningBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFFFCDD2),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "⚠ $message",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB71C1C)
        )
    }
}

@Composable
private fun GrowthSummaryCard(summary: PlayerGrowthSummary, isGrower: Boolean) {
    val cardColor = if (isGrower) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val deltaColor = if (isGrower) Color(0xFF2E7D32) else Color(0xFFB71C1C)
    val deltaText = if (summary.caDelta > 0) "+${summary.caDelta}" else "${summary.caDelta}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.playerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${summary.age}岁 · ${summary.position} · ${summary.rangeTier.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "CA ${summary.caBefore} → ${summary.caAfter}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.titleLarge,
                    color = deltaColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GrowthEventCard(event: GrowthEventEntity) {
    val (bgColor, textColor) = when (event.severity) {
        "CRITICAL" -> Color(0xFFFFCDD2) to Color(0xFFB71C1C)
        "WARN" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        else -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                if (!event.isRead) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFF4081),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "新",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "${event.triggerDate} · CA ${event.caAtTrigger}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
