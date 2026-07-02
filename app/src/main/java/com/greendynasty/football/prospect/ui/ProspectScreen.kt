@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.prospect.ui

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
import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.model.ProspectPathEventType
import com.greendynasty.football.prospect.repository.ProspectStatistics
import com.greendynasty.football.prospect.repository.ProspectViewItem
import com.greendynasty.football.prospect.ui.state.ProspectTab
import com.greendynasty.football.prospect.ui.viewmodel.ProspectViewModel

/**
 * T15 历史新星池页入口 Composable（V0.2 08 §六 + T15 方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → 历史新星池"进入。
 *
 * 3 个 Tab：
 * - 已发现：玩家球探已发现的新星列表
 * - 全部活跃：所有 ACTIVE + DISCOVERED + DEFAULT_PATH 状态新星
 * - 统计：池大小 / 蝴蝶触发数 等汇总信息
 *
 * 点击列表项 → 详情视图（路径时间轴：成长事件 + 转会 + 蝴蝶标记）
 *
 * @param saveId 存档 ID
 * @param saveUuid 存档 UUID（蝴蝶事件 save_id 字段用）
 */
@Composable
fun ProspectScreen(
    saveId: Int,
    saveUuid: String,
    modifier: Modifier = Modifier,
    viewModel: ProspectViewModel = viewModel(
        factory = ProspectViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application,
            saveId,
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
            // 若选中详情 → 显示详情视图
            val selected = uiState.selectedProspect
            if (selected != null) {
                ProspectDetailHeader(
                    prospect = selected,
                    onBack = viewModel::clearSelectedProspect
                )
                ProspectPathTimeline(
                    events = uiState.pathEvents,
                    modifier = Modifier.fillMaxSize()
                )
                return@Column
            }

            // Tab 切换栏
            TabRow(selectedTabIndex = currentTab.ordinal) {
                ProspectTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { viewModel.switchTab(tab) },
                        text = { Text(tab.title) }
                    )
                }
            }

            // 内容区
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentTab) {
                    ProspectTab.DISCOVERED -> DiscoveredProspectsTab(
                        prospects = uiState.discoveredProspects,
                        isLoading = uiState.isLoading,
                        onSelect = viewModel::selectProspect
                    )
                    ProspectTab.ACTIVE -> ActiveProspectsTab(
                        prospects = uiState.allActiveProspects,
                        isLoading = uiState.isLoading,
                        onSelect = viewModel::selectProspect
                    )
                    ProspectTab.STATISTICS -> StatisticsTab(
                        statistics = uiState.statistics,
                        poolSize = uiState.poolSize,
                        onRefresh = viewModel::loadStatistics,
                        onTriggerSimulation = viewModel::triggerMonthlySimulation
                    )
                }
            }
        }
    }
}

// =================================================================
// Tab 1: 已发现新星列表
// =================================================================

@Composable
private fun DiscoveredProspectsTab(
    prospects: List<ProspectViewItem>,
    isLoading: Boolean,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "已发现历史新星（${prospects.size} 名）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (prospects.isEmpty() && !isLoading) {
            EmptyCard(
                title = "暂无已发现历史新星",
                description = "派遣球探到高产地区（巴西/阿根廷/法国等）可发现历史新星"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(prospects, key = { it.prospectId }) { prospect ->
                ProspectCard(prospect, onClick = { onSelect(prospect.prospectId) })
            }
        }
    }
}

// =================================================================
// Tab 2: 全部活跃新星
// =================================================================

@Composable
private fun ActiveProspectsTab(
    prospects: List<ProspectViewItem>,
    isLoading: Boolean,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "全部活跃新星（${prospects.size} 名）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (prospects.isEmpty() && !isLoading) {
            EmptyCard(
                title = "暂无活跃新星",
                description = "随着游戏日期推进，历史新星会按真实出道日期激活"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(prospects, key = { it.prospectId }) { prospect ->
                ProspectCard(prospect, onClick = { onSelect(prospect.prospectId) })
            }
        }
    }
}

// =================================================================
// Tab 3: 统计
// =================================================================

@Composable
private fun StatisticsTab(
    statistics: ProspectStatistics,
    poolSize: Int,
    onRefresh: () -> Unit,
    onTriggerSimulation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "历史新星池统计",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticRow("历史新星池总数", "$poolSize 名")
                StatisticRow("已激活新星", "${statistics.totalActivated} 名")
                StatisticRow("可发现中（ACTIVE）", "${statistics.activeCount} 名")
                StatisticRow("已发现（DISCOVERED）", "${statistics.discoveredCount} 名")
                StatisticRow("提前签约（SIGNED_EARLY）", "${statistics.signedEarlyCount} 名")
                StatisticRow(
                    "蝴蝶效应触发",
                    "${statistics.butterflyTriggeredCount} 次",
                    highlight = statistics.butterflyTriggeredCount > 0
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) { Text("刷新统计") }
            OutlinedButton(onClick = onTriggerSimulation) { Text("模拟月度路径") }
        }

        EmptyCard(
            title = "V1 范围说明",
            description = "蝴蝶效应 V1 简化：仅标记 + 通知，完整因果链由 T20/T21 实现"
        )
    }
}

@Composable
private fun StatisticRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFFFF5722) else MaterialTheme.colorScheme.primary
        )
    }
}

// =================================================================
// 新星卡片
// =================================================================

@Composable
private fun ProspectCard(
    prospect: ProspectViewItem,
    onClick: () -> Unit
) {
    val statusColor = when (prospect.status) {
        "ACTIVE" -> Color(0xFF4CAF50)
        "DISCOVERED" -> Color(0xFF2196F3)
        "DEFAULT_PATH" -> Color(0xFFFF9800)
        "SIGNED_EARLY" -> Color(0xFFE91E63)
        "COMPLETED" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像首字母
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (prospect.butterflyTriggered) Color(0xFFFF5722)
                            else MaterialTheme.colorScheme.primary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = prospect.playerName.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prospect.playerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append(prospect.primaryPosition ?: "—")
                            append(" · ")
                            append(if (prospect.age > 0) "${prospect.age} 岁" else "—")
                            append(" · ")
                            append(prospect.nationality ?: "—")
                            append(" · ")
                            append(prospect.regionCode)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = prospect.statusDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CA/PA + 俱乐部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CA ${prospect.currentCa} / PA ${prospect.currentPa}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "俱乐部 #${prospect.currentClubId ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 标签
            if (prospect.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    prospect.tags.take(3).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 蝴蝶标记
            if (prospect.butterflyTriggered) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFFFF5722).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "⚠ 已触发蝴蝶效应",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF5722),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("查看路径 →")
            }
        }
    }
}

// =================================================================
// 详情视图：路径时间轴
// =================================================================

@Composable
private fun ProspectDetailHeader(
    prospect: ProspectViewItem,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 返回列表") }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = prospect.playerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "传奇等级 ${prospect.legendLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("位置：").append(prospect.primaryPosition ?: "—").append("  ")
                    append("年龄：").append(if (prospect.age > 0) "${prospect.age}" else "—").append("  ")
                    append("地区：").append(prospect.regionCode).append("\n")
                    append("CA：").append(prospect.currentCa).append("  ")
                    append("PA：").append(prospect.currentPa).append("  ")
                    append("俱乐部：#").append(prospect.currentClubId ?: "—").append("\n")
                    append("状态：").append(prospect.statusDisplay).append("  ")
                    append("路径：").append(prospect.currentPath).append("\n")
                    append("可发现日期：").append(prospect.discoverableFrom).append("  ")
                    append("成名年：").append(prospect.defaultBreakthroughYear)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProspectPathTimeline(
    events: List<ProspectPathEventEntity>,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无路径事件\n随着游戏推进，新星会按历史路径成长",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "成长路径时间轴（${events.size} 个事件）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(events, key = { it.eventId }) { event ->
            PathEventCard(event)
        }
    }
}

@Composable
private fun PathEventCard(event: ProspectPathEventEntity) {
    val eventType = ProspectPathEventType.fromCode(event.eventType)
    val color = when (eventType) {
        ProspectPathEventType.ACTIVATED -> Color(0xFF4CAF50)
        ProspectPathEventType.DISCOVERED -> Color(0xFF2196F3)
        ProspectPathEventType.TRANSFER -> Color(0xFFFF9800)
        ProspectPathEventType.CA_PA_PROGRESS -> MaterialTheme.colorScheme.tertiary
        ProspectPathEventType.EARLY_SIGNED -> Color(0xFFE91E63)
        ProspectPathEventType.AI_SIGNED -> Color(0xFF9C27B0)
        ProspectPathEventType.PATH_INTERRUPTED -> Color(0xFFFF5722)
        ProspectPathEventType.BUTTERFLY_TRIGGERED -> Color(0xFFFF5722)
        else -> MaterialTheme.colorScheme.outline
    }
    val isDefaultPath = event.isDefaultPath == 1
    val pathTag = if (isDefaultPath) "历史路径" else "蝴蝶分支"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // 时间轴圆点
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
                    .align(Alignment.Top)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = eventType?.display ?: event.eventType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Surface(
                        color = (if (isDefaultPath) Color(0xFF4CAF50) else Color(0xFFFF5722))
                            .copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = pathTag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = event.eventDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (!event.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.summary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 详细字段
                val detail = buildString {
                    if (event.fromClubId != null || event.toClubId != null) {
                        append("俱乐部：").append(event.fromClubId ?: "—")
                        append(" → ").append(event.toClubId ?: "—")
                        if (event.transferFee != null) {
                            append("（转会费 ").append(event.transferFee).append("）")
                        }
                        append("\n")
                    }
                    if (event.caBefore != null || event.caAfter != null) {
                        append("CA：").append(event.caBefore).append(" → ").append(event.caAfter)
                        append("  ")
                    }
                    if (event.paBefore != null || event.paAfter != null) {
                        append("PA：").append(event.paBefore).append(" → ").append(event.paAfter)
                    }
                }
                if (detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// =================================================================
// 通用组件
// =================================================================

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Suppress("unused")
@Composable
private fun SectionDivider() {
    Divider(modifier = Modifier.padding(vertical = 8.dp))
}
