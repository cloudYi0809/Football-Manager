@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.divergence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
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
import com.greendynasty.football.butterfly.model.ButterflyEventCategory
import com.greendynasty.football.divergence.archive.DivergenceArchiveEntity
import com.greendynasty.football.divergence.model.DivergenceTimelineItem
import com.greendynasty.football.divergence.model.ImportanceLevel
import com.greendynasty.football.divergence.model.NoReplacementRecord
import com.greendynasty.football.divergence.ui.state.DivergenceTab
import com.greendynasty.football.divergence.ui.state.DivergenceUiState
import com.greendynasty.football.divergence.ui.state.FILTER_OPTIONS
import com.greendynasty.football.divergence.ui.state.FilterOption
import com.greendynasty.football.divergence.ui.viewmodel.DivergenceViewModel

/**
 * T21 历史分歧时间线页入口 Composable（任务 T21.3：时间线 UI）。
 *
 * 二级页面，由首页"快捷入口 → 历史分歧"进入。
 *
 * 4 个 Tab：
 * - 时间线：当前赛季分歧事件（支持筛选）
 * - 归档：历史赛季归档的分歧记录
 * - 无替代："历史分歧未产生重大替代"记录
 * - 详情：选中事件的详情视图
 *
 * @param saveUuid 存档 UUID
 */
@Composable
fun DivergenceTimelineScreen(
    saveUuid: String,
    modifier: Modifier = Modifier,
    viewModel: DivergenceViewModel = viewModel(
        factory = DivergenceViewModel.factory(
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
                DivergenceTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = {
                            if (tab == DivergenceTab.TIMELINE) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.switchTab(tab)
                            }
                        },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (currentTab) {
                DivergenceTab.TIMELINE -> TimelineTab(
                    uiState = uiState,
                    onRefresh = viewModel::loadAll,
                    onSelectItem = viewModel::selectTimelineItem,
                    onApplyFilter = viewModel::applyFilter,
                    onClearFilter = viewModel::clearFilter
                )
                DivergenceTab.ARCHIVE -> ArchiveTab(
                    uiState = uiState,
                    onSelectArchive = viewModel::selectArchive
                )
                DivergenceTab.NO_REPLACEMENT -> NoReplacementTab(
                    uiState = uiState
                )
                DivergenceTab.DETAIL -> DetailTab(
                    uiState = uiState,
                    onBack = viewModel::clearSelection
                )
            }
        }
    }
}

// ==================== 时间线 Tab ====================

@Composable
private fun TimelineTab(
    uiState: DivergenceUiState,
    onRefresh: () -> Unit,
    onSelectItem: (String) -> Unit,
    onApplyFilter: (com.greendynasty.football.divergence.model.DivergenceFilter) -> Unit,
    onClearFilter: () -> Unit
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
        // 统计摘要
        item {
            SummaryCard(
                totalCount = uiState.totalCount,
                noReplacementCount = uiState.noReplacementCount,
                withReplacementCount = uiState.withReplacementCount
            )
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
                OutlinedButton(onClick = onClearFilter, modifier = Modifier.weight(1f)) {
                    Text("清除筛选")
                }
            }
        }

        // 筛选条
        item {
            FilterChipsRow(
                currentFilter = uiState.filter,
                onApplyFilter = onApplyFilter
            )
        }

        // 时间线标题
        item {
            Text(
                text = "分歧时间线（${uiState.timelineItems.size} / ${uiState.totalCount}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 时间线列表
        if (uiState.timelineItems.isEmpty()) {
            item { EmptyTimelineCard() }
        } else {
            items(uiState.timelineItems, key = { it.log.eventId }) { item ->
                TimelineItemCard(item = item, onClick = { onSelectItem(item.log.eventId) })
            }
        }
    }
}

// ==================== 归档 Tab ====================

@Composable
private fun ArchiveTab(
    uiState: DivergenceUiState,
    onSelectArchive: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = "历史归档（${uiState.archivedDivergences.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.archivedDivergences.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("暂无归档记录", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "赛季归档时将自动保存分歧记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.archivedDivergences, key = { it.archiveId }) { archive ->
                ArchiveCard(archive = archive, onClick = { onSelectArchive(archive.archiveId) })
            }
        }
    }
}

// ==================== 无替代 Tab ====================

@Composable
private fun NoReplacementTab(uiState: DivergenceUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = "历史分歧未产生重大替代（${uiState.noReplacementRecords.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "以下蝴蝶事件触发后未产生连锁反应（无替代转会 / 无财政调整等）。" +
                        "V0.2 §七：允许\"未产生重大替代\"，玩家仍可理解为何没有后续影响。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.noReplacementRecords.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "暂无\"无重大替代\"记录",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.noReplacementRecords, key = { it.eventId }) { record ->
                NoReplacementCard(record = record)
            }
        }
    }
}

// ==================== 详情 Tab ====================

@Composable
private fun DetailTab(
    uiState: DivergenceUiState,
    onBack: () -> Unit
) {
    val timelineItem = uiState.selectedTimelineItem
    val archive = uiState.selectedArchive

    if (timelineItem == null && archive == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("请从列表选择一个事件查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("← 返回列表")
            }
        }

        if (timelineItem != null) {
            item { TimelineDetailCard(item = timelineItem) }
        } else if (archive != null) {
            item { ArchiveDetailCard(archive = archive) }
        }
    }
}

// ==================== 卡片组件 ====================

@Composable
private fun SummaryCard(
    totalCount: Int,
    noReplacementCount: Int,
    withReplacementCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "分歧统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Divider()
            InfoRow("总分歧事件", "$totalCount")
            InfoRow("有重大替代", "$withReplacementCount")
            InfoRow("无重大替代", "$noReplacementCount")
        }
    }
}

@Composable
private fun FilterChipsRow(
    currentFilter: com.greendynasty.football.divergence.model.DivergenceFilter,
    onApplyFilter: (com.greendynasty.football.divergence.model.DivergenceFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FILTER_OPTIONS.forEach { option ->
            val isSelected = isFilterSelected(currentFilter, option)
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newFilter = if (isSelected) {
                        com.greendynasty.football.divergence.model.DivergenceFilter.NONE
                    } else {
                        com.greendynasty.football.divergence.model.DivergenceFilter(
                            category = option.category,
                            importanceLevel = option.importanceLevel
                        )
                    }
                    onApplyFilter(newFilter)
                },
                label = { Text(option.label) }
            )
        }
    }
}

private fun isFilterSelected(
    current: com.greendynasty.football.divergence.model.DivergenceFilter,
    option: FilterOption
): Boolean {
    return current.category == option.category && current.importanceLevel == option.importanceLevel
}

@Composable
private fun TimelineItemCard(item: DivergenceTimelineItem, onClick: () -> Unit) {
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
                    color = categoryColor(item.log.category),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.categoryDisplay,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // 重要度标签
                Surface(
                    color = importanceColor(item.importanceLevel),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.importanceLevel.display,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 分歧提示文案
            Text(
                text = item.log.divergenceText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3
            )

            // 路径对比
            Text(
                text = "原路径：${item.log.originalPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "当前路径：${item.log.currentPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 影响摘要 + 日期
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.log.impactSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.log.hasMajorReplacement)
                        Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArchiveCard(archive: DivergenceArchiveEntity, onClick: () -> Unit) {
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "赛季 ${archive.seasonId}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = archive.triggerDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = archive.divergenceText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Text(
                text = if (archive.hasMajorReplacement == 1) "✓ 有重大替代"
                    else "— 无重大替代",
                style = MaterialTheme.typography.bodySmall,
                color = if (archive.hasMajorReplacement == 1)
                    Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoReplacementCard(record: NoReplacementRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = record.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "原因：${record.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "触发日期：${record.triggerDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimelineDetailCard(item: DivergenceTimelineItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "分歧详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Divider()

            InfoRow("分类", item.categoryDisplay)
            InfoRow("触发日期", item.formattedDate)
            InfoRow("重要度", "${item.log.importance} / 100")
            InfoRow("源球员", item.sourcePlayerName ?: "—")
            InfoRow("源俱乐部", item.sourceClubName ?: "—")
            InfoRow("预期俱乐部", item.expectedClubName ?: "—")
            InfoRow("有重大替代", if (item.log.hasMajorReplacement) "是" else "否")

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "分歧提示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.log.divergenceText,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "路径对比",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "原路径：${item.log.originalPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "当前路径：${item.log.currentPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "影响摘要",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.log.impactSummary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ArchiveDetailCard(archive: DivergenceArchiveEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "归档详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Divider()

            InfoRow("赛季", "${archive.seasonId}")
            InfoRow("触发日期", archive.triggerDate)
            InfoRow("分类", archive.category)
            InfoRow("重要度", "${archive.importance} / 100")
            InfoRow("有重大替代", if (archive.hasMajorReplacement == 1) "是" else "否")
            InfoRow("归档时间", archive.archivedAt)

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "分歧提示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = archive.divergenceText,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "路径对比",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "原路径：${archive.originalPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "当前路径：${archive.currentPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "影响摘要",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = archive.impactSummary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmptyTimelineCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无分歧事件",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "玩家干预历史（如签走历史新星）时将产生分歧记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

// ==================== 配色工具 ====================

/** 事件分类配色。 */
private fun categoryColor(category: ButterflyEventCategory): Color = when (category) {
    ButterflyEventCategory.TRANSFER -> Color(0xFF2196F3) // blue
    ButterflyEventCategory.MATCH -> Color(0xFF9C27B0) // purple
    ButterflyEventCategory.INJURY -> Color(0xFFF44336) // red
    ButterflyEventCategory.HONOR -> Color(0xFFFFD700) // gold
    ButterflyEventCategory.RETIREMENT -> Color(0xFF607D8B) // grey
}

/** 重要度等级配色。 */
private fun importanceColor(level: ImportanceLevel): Color = when (level) {
    ImportanceLevel.LOW -> Color(0xFF4CAF50) // green
    ImportanceLevel.MEDIUM -> Color(0xFFFFC107) // amber
    ImportanceLevel.HIGH -> Color(0xFFF44336) // red
}
