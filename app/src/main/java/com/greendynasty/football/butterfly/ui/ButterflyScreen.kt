@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.butterfly.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.butterfly.model.ButterflyEventStatus
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.model.ButterflyImpactType
import com.greendynasty.football.butterfly.model.DeviationLevel
import com.greendynasty.football.butterfly.model.DeviationReport
import com.greendynasty.football.butterfly.repository.ButterflyEventViewItem
import com.greendynasty.football.butterfly.ui.state.ButterflyTab
import com.greendynasty.football.butterfly.ui.viewmodel.ButterflyViewModel

/**
 * T20 蝴蝶效应页入口 Composable（任务要求 8：事件列表 + 详情 + 偏差仪表盘）。
 *
 * 二级页面，由首页"快捷入口 → 蝴蝶效应"进入。
 *
 * 2 个 Tab：
 * - 总览：偏差仪表盘 + 事件列表
 * - 详情：选中事件的影响节点列表
 *
 * @param saveUuid 存档 UUID（butterfly_event.save_id 字段用）
 */
@Composable
fun ButterflyScreen(
    saveUuid: String,
    modifier: Modifier = Modifier,
    viewModel: ButterflyViewModel = viewModel(
        factory = ButterflyViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application,
            saveUuid
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果消息
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = currentTab.ordinal) {
                ButterflyTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = {
                            if (tab == ButterflyTab.OVERVIEW) {
                                viewModel.clearSelectedEvent()
                            } else {
                                viewModel.switchTab(tab)
                            }
                        },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (currentTab) {
                ButterflyTab.OVERVIEW -> OverviewTab(
                    uiState = uiState,
                    onRefresh = viewModel::loadAll,
                    onSelectEvent = viewModel::selectEvent,
                    onProcessPending = viewModel::processPendingEvents
                )
                ButterflyTab.DETAIL -> DetailTab(
                    uiState = uiState,
                    onBack = viewModel::clearSelectedEvent
                )
            }
        }
    }
}

// ==================== 总览 Tab ====================

@Composable
private fun OverviewTab(
    uiState: com.greendynasty.football.butterfly.ui.state.ButterflyUiState,
    onRefresh: () -> Unit,
    onSelectEvent: (String) -> Unit,
    onProcessPending: () -> Unit
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        // 偏差仪表盘
        item {
            DeviationDashboard(report = uiState.deviationReport)
        }

        // 操作按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("刷新")
                }
                if (uiState.pendingCount > 0) {
                    OutlinedButton(onClick = onProcessPending, modifier = Modifier.weight(1f)) {
                        Text("处理 ${uiState.pendingCount} 待处理")
                    }
                }
            }
        }

        // 事件列表标题
        item {
            Text(
                text = "蝴蝶事件列表（${uiState.events.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 事件列表
        if (uiState.events.isEmpty()) {
            item {
                EmptyEventCard()
            }
        } else {
            items(uiState.events, key = { it.event.eventId }) { viewItem ->
                EventCard(viewItem = viewItem, onClick = { onSelectEvent(viewItem.event.eventId) })
            }
        }
    }
}

// ==================== 详情 Tab ====================

@Composable
private fun DetailTab(
    uiState: com.greendynasty.football.butterfly.ui.state.ButterflyUiState,
    onBack: () -> Unit
) {
    val detail = uiState.selectedEventDetail
    if (detail == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("请从总览选择一个事件查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        // 返回按钮
        item {
            TextButton(onClick = onBack) {
                Text("← 返回列表")
            }
        }

        // 事件信息卡片
        item {
            EventInfoCard(viewItem = detail)
        }

        // 影响节点列表
        item {
            Text(
                text = "影响节点（${detail.impactNodes.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (detail.impactNodes.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "该事件尚未生成影响节点",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(detail.impactNodes, key = { it.nodeId }) { node ->
                ImpactNodeCard(node = node)
            }
        }
    }
}

// ==================== 偏差仪表盘 ====================

@Composable
private fun DeviationDashboard(report: DeviationReport?) {
    if (report == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "加载偏差度量中...",
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "历史偏差度量",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 偏差值 + 等级
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 圆形进度指示器
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(deviationColor(report.level))
                ) {
                    Text(
                        text = "${report.totalDeviation}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Column {
                    Text(
                        text = report.level.display,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = deviationColor(report.level)
                    )
                    Text(
                        text = "/ 100",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // 预算使用情况
            Text(
                text = "预算使用",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 事件预算
            BudgetBar(
                label = "事件",
                used = report.eventCount,
                max = report.maxEvents
            )

            // 节点预算
            BudgetBar(
                label = "影响节点",
                used = report.nodeCount,
                max = report.maxNodes
            )

            // 总重要度
            Text(
                text = "总重要度：${report.totalImportance}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetBar(label: String, used: Int, max: Int) {
    val progress = if (max > 0) used.toFloat() / max.toFloat() else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$used / $max",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

// ==================== 事件卡片 ====================

@Composable
private fun EventCard(viewItem: ButterflyEventViewItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类标签
                Surface(
                    color = categoryColor(viewItem.event.category),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = viewItem.categoryDisplay,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // 状态标签
                Surface(
                    color = statusColor(viewItem.event.status),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = viewItem.statusDisplay,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 事件摘要
            Text(
                text = viewItem.event.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            // 元信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = viewItem.event.triggerDate.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "重要度 ${viewItem.event.importance} · 节点 ${viewItem.impactNodeCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyEventCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无蝴蝶事件",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "玩家干预历史（如签走历史新星）时将触发蝴蝶事件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 事件详情卡片 ====================

@Composable
private fun EventInfoCard(viewItem: ButterflyEventViewItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "事件信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Divider()

            InfoRow("分类", viewItem.categoryDisplay)
            InfoRow("触发类型", viewItem.triggerTypeDisplay)
            InfoRow("状态", viewItem.statusDisplay)
            InfoRow("触发日期", viewItem.event.triggerDate.toString())
            InfoRow("重要度", "${viewItem.event.importance} / 100")
            InfoRow("影响预算", "${viewItem.event.impactBudget}")
            InfoRow("源球员", viewItem.sourcePlayerName ?: "—")
            InfoRow("源俱乐部", viewItem.sourceClubName ?: "—")
            InfoRow("预期俱乐部", viewItem.expectedClubName ?: "—")

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "事件摘要",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = viewItem.event.summary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== 影响节点卡片 ====================

@Composable
private fun ImpactNodeCard(node: ButterflyImpactNode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 影响类型标签
                Surface(
                    color = impactTypeColor(node.impactType),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = node.impactType.display,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // 深度标签
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "深度 ${node.depth}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 影响结果摘要
            Text(
                text = node.resultSummary ?: "（无摘要）",
                style = MaterialTheme.typography.bodyMedium
            )

            // 元信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "强度 ${node.impactStrength.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (node.status.code == "success") "✓ 已生效" else node.status.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (node.status.code == "success") Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 配色工具 ====================

/** 偏差等级配色。 */
private fun deviationColor(level: DeviationLevel): Color = when (level) {
    DeviationLevel.MINIMAL -> Color(0xFF4CAF50) // green
    DeviationLevel.LOW -> Color(0xFF8BC34A) // lime
    DeviationLevel.MODERATE -> Color(0xFFFFC107) // amber
    DeviationLevel.HIGH -> Color(0xFFFF9800) // orange
    DeviationLevel.CRITICAL -> Color(0xFFF44336) // red
}

/** 事件分类配色。 */
private fun categoryColor(category: com.greendynasty.football.butterfly.model.ButterflyEventCategory): Color =
    when (category) {
        com.greendynasty.football.butterfly.model.ButterflyEventCategory.TRANSFER -> Color(0xFF2196F3) // blue
        com.greendynasty.football.butterfly.model.ButterflyEventCategory.MATCH -> Color(0xFF9C27B0) // purple
        com.greendynasty.football.butterfly.model.ButterflyEventCategory.INJURY -> Color(0xFFF44336) // red
        com.greendynasty.football.butterfly.model.ButterflyEventCategory.HONOR -> Color(0xFFFFD700) // gold
        com.greendynasty.football.butterfly.model.ButterflyEventCategory.RETIREMENT -> Color(0xFF607D8B) // grey
    }

/** 事件状态配色。 */
private fun statusColor(status: ButterflyEventStatus): Color = when (status) {
    ButterflyEventStatus.PENDING -> Color(0xFFFFC107) // amber
    ButterflyEventStatus.PROCESSING -> Color(0xFF2196F3) // blue
    ButterflyEventStatus.COMPLETED -> Color(0xFF4CAF50) // green
    ButterflyEventStatus.ARCHIVED -> Color(0xFF9E9E9E) // grey
}

/** 影响类型配色。 */
private fun impactTypeColor(type: ButterflyImpactType): Color = when (type) {
    ButterflyImpactType.TRANSFER_REPLACEMENT -> Color(0xFF2196F3) // blue
    ButterflyImpactType.FINANCIAL_IMPACT -> Color(0xFF4CAF50) // green
    ButterflyImpactType.CAREER_PATH_SHIFT -> Color(0xFFFF9800) // orange
    ButterflyImpactType.CLUB_STRATEGY_SHIFT -> Color(0xFF9C27B0) // purple
    ButterflyImpactType.NATIONAL_TEAM_SHIFT -> Color(0xFF607D8B) // grey
}
