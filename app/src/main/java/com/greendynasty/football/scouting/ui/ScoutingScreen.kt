@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.scouting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.scouting.model.BudgetLevel
import com.greendynasty.football.scouting.model.ScoutReportLevel
import com.greendynasty.football.scouting.model.ScoutRegionCode
import com.greendynasty.football.scouting.model.ScoutTaskType
import com.greendynasty.football.scouting.model.ScoutWithKnowledge
import com.greendynasty.football.scouting.model.YouthTournament
import com.greendynasty.football.scouting.ui.state.ScoutingTab
import com.greendynasty.football.scouting.ui.viewmodel.ScoutingViewModel

/**
 * T14 球探中心页入口 Composable（V0.2 08 §六 + T14 方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → 球探中心"进入。
 *
 * 5 个 Tab：
 * - 球探列表：雇佣/解雇/派遣任务
 * - 进行中任务：取消任务 / 查看进度
 * - 最新报告：报告卡片 → 详情页
 * - 青年赛事事件：事件列表
 * - 观察名单（V1 复用 T10，本 Tab 暂未实现）
 *
 * @param saveId 存档 ID
 * @param clubId 俱乐部 ID
 */
@Composable
fun ScoutingScreen(
    saveId: Int,
    clubId: Int,
    modifier: Modifier = Modifier,
    viewModel: ScoutingViewModel = viewModel(
        factory = ScoutingViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application,
            saveId,
            clubId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val showDispatchDialog by viewModel.showDispatchDialog.collectAsState()
    val dispatchForm by viewModel.dispatchForm.collectAsState()

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
            // Tab 切换栏
            TabRow(selectedTabIndex = currentTab.ordinal) {
                ScoutingTab.values().forEach { tab ->
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
                    ScoutingTab.SCOUTS -> ScoutListTab(
                        scouts = uiState.scouts,
                        isLoading = uiState.isLoading,
                        onDispatch = viewModel::showDispatchDialog,
                        onRelease = viewModel::releaseScout
                    )
                    ScoutingTab.TASKS -> ActiveTaskListTab(
                        tasks = uiState.activeTasks,
                        onCancel = viewModel::cancelTask
                    )
                    ScoutingTab.REPORTS -> LatestReportsTab(
                        reports = uiState.recentReports,
                        selectedDetail = uiState.selectedReportDetail,
                        onSelectReport = viewModel::selectReport,
                        onBack = viewModel::clearSelectedReport,
                        onSetRecommendation = viewModel::setScoutRecommendation
                    )
                    ScoutingTab.EVENTS -> EventsTab(
                        events = uiState.recentEvents,
                        onMarkRead = viewModel::markEventRead
                    )
                    ScoutingTab.WATCHLIST -> WatchListPlaceholder()
                }
            }
        }
    }

    // 派遣任务弹窗
    if (showDispatchDialog) {
        ScoutTaskDispatchDialog(
            form = dispatchForm,
            onFormChange = viewModel::updateDispatchForm,
            onDispatch = viewModel::dispatchTask,
            onDismiss = viewModel::dismissDispatchDialog
        )
    }
}

// =================================================================
// Tab 1: 球探列表
// =================================================================

@Composable
private fun ScoutListTab(
    scouts: List<ScoutWithKnowledge>,
    isLoading: Boolean,
    onDispatch: (ScoutWithKnowledge) -> Unit,
    onRelease: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "球探列表（${scouts.size} 名）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (scouts.isEmpty() && !isLoading) {
            EmptyCard(
                title = "暂无雇佣球探",
                description = "请在历史球探池中雇佣球探（V1 暂未实现雇佣入口）"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(scouts, key = { it.hired.hiredId }) { scout ->
                ScoutCard(scout, onDispatch = { onDispatch(scout) }, onRelease = { onRelease(scout.hired.hiredId) })
            }
        }
    }
}

@Composable
private fun ScoutCard(
    scout: ScoutWithKnowledge,
    onDispatch: () -> Unit,
    onRelease: () -> Unit
) {
    val hired = scout.hired
    val statusColor = when (hired.status) {
        "IDLE" -> Color(0xFF4CAF50)
        "ON_TASK" -> Color(0xFFFF9800)
        "RESTING" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = scout.scout.name.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scout.scout.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${scout.scout.nationality ?: "—"} · ${scout.scout.age ?: "?"} 岁 · ${scout.scout.networkLevel} 人脉",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = hired.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 地区知识摘要（显示前 5 个地区的知识值）
            Text(
                text = "地区知识（前 5）",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                scout.regionKnowledge.take(5).forEach { rk ->
                    val regionDisplay = ScoutRegionCode.fromCode(rk.regionCode)?.displayName ?: rk.regionCode
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = regionDisplay.take(2),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${rk.knowledgeValue}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDispatch,
                    modifier = Modifier.weight(1f),
                    enabled = hired.status == "IDLE"
                ) { Text("派遣任务") }
                OutlinedButton(
                    onClick = onRelease,
                    modifier = Modifier.weight(1f)
                ) { Text("解雇") }
            }
        }
    }
}

// =================================================================
// Tab 2: 进行中任务
// =================================================================

@Composable
private fun ActiveTaskListTab(
    tasks: List<com.greendynasty.football.scouting.model.ScoutTaskItem>,
    onCancel: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "进行中任务（${tasks.size}）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (tasks.isEmpty()) {
            EmptyCard(
                title = "暂无进行中任务",
                description = "请在球探列表中派遣任务（30/60/90 天周期）"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.task.taskId }) { taskItem ->
                TaskCard(taskItem, onCancel = { onCancel(taskItem.task.taskId) })
            }
        }
    }
}

@Composable
private fun TaskCard(
    item: com.greendynasty.football.scouting.model.ScoutTaskItem,
    onCancel: () -> Unit
) {
    val task = item.task
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${item.scoutName} · ${item.taskTypeDisplay}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.regionDisplay} · ${task.durationDays} 天 · ${task.budgetLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "已发现 ${item.discoveredCount} 人",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            Text(
                text = "进度 ${item.progressPercent}% · 剩余 ${item.remainingDays} 天",
                style = MaterialTheme.typography.labelSmall
            )
            LinearProgressIndicator(
                progress = item.progressPercent / 100f,
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("取消任务") }
            }
        }
    }
}

// =================================================================
// Tab 3: 最新报告
// =================================================================

@Composable
private fun LatestReportsTab(
    reports: List<com.greendynasty.football.scouting.data.SaveScoutReportEntity>,
    selectedDetail: com.greendynasty.football.scouting.model.ScoutReportDetail?,
    onSelectReport: (Int) -> Unit,
    onBack: () -> Unit,
    onSetRecommendation: (Int, Int) -> Unit
) {
    if (selectedDetail != null) {
        ReportDetailScreen(
            detail = selectedDetail,
            onBack = onBack,
            onSetRecommendation = { level -> onSetRecommendation(selectedDetail.report.reportId, level) }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "最新报告（${reports.size}）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (reports.isEmpty()) {
            EmptyCard(
                title = "暂无球探报告",
                description = "派遣任务后，球探会发现球员并生成报告"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reports, key = { it.reportId }) { report ->
                ReportCard(report, onClick = { onSelectReport(report.reportId) })
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: com.greendynasty.football.scouting.data.SaveScoutReportEntity,
    onClick: () -> Unit
) {
    val level = ScoutReportLevel.fromLevel(report.reportLevel)
    val levelColor = when (report.reportLevel) {
        1 -> MaterialTheme.colorScheme.outline
        2 -> MaterialTheme.colorScheme.tertiary
        3 -> Color(0xFF2196F3)
        4 -> Color(0xFFFF9800)
        5 -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = report.playerName.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.playerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${report.playerPosition} · ${report.playerAge} 岁 · ${report.playerRegion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Surface(
                color = levelColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "L${report.reportLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ReportDetailScreen(
    detail: com.greendynasty.football.scouting.model.ScoutReportDetail,
    onBack: () -> Unit,
    onSetRecommendation: (Int) -> Unit
) {
    val report = detail.report
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 头部
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${report.playerName} · ${detail.currentLevelDisplay}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 等级 1：初次发现（始终可见）
        SectionCard(title = "等级 1 - 初次发现") {
            DetailRow("姓名", report.playerName)
            DetailRow("年龄", "${report.playerAge} 岁")
            DetailRow("位置", report.playerPosition)
            DetailRow("地区", report.playerRegion)
            DetailRow("初步特点", report.initialTraits ?: "—")
            DetailRow("历史新星", if (report.isHistoricalProspect == 1) "是" else "否")
        }

        // 等级 2-5：按等级解锁
        if (report.reportLevel >= 2) {
            SectionCard(title = "等级 2 - 粗略报告") {
                DetailRow("CA 区间", "${report.caRangeLow ?: "—"} - ${report.caRangeHigh ?: "—"}")
                DetailRow("PA 区间", "${report.paRangeLow ?: "—"} - ${report.paRangeHigh ?: "—"}")
                DetailRow("优势", report.strengths ?: "—")
                DetailRow("风险", report.risks ?: "—")
            }
        } else {
            LockedSection("等级 2 - 粗略报告", "需累计观察 ${detail.nextLevelThreshold} 天")
        }

        if (report.reportLevel >= 3) {
            SectionCard(title = "等级 3 - 标准报告") {
                DetailRow("较窄 CA", "${report.caNarrowLow ?: "—"} - ${report.caNarrowHigh ?: "—"}")
                DetailRow("较窄 PA", "${report.paNarrowLow ?: "—"} - ${report.paNarrowHigh ?: "—"}")
                DetailRow("性格", report.personality ?: "—")
                DetailRow("签约难度", "${report.signDifficulty ?: "—"} / 100")
            }
        } else if (report.reportLevel >= 2) {
            LockedSection("等级 3 - 标准报告", "需累计观察 ${detail.nextLevelThreshold} 天")
        }

        if (report.reportLevel >= 4) {
            SectionCard(title = "等级 4 - 深度报告") {
                DetailRow("成长速度", report.growthSpeed ?: "—")
                DetailRow("隐藏标签", report.hiddenTags ?: "—")
                DetailRow("适配战术", report.tacticalFit ?: "—")
            }
        } else if (report.reportLevel >= 3) {
            LockedSection("等级 4 - 深度报告", "需累计观察 ${detail.nextLevelThreshold} 天")
        }

        if (report.reportLevel >= 5) {
            SectionCard(title = "等级 5 - 完全掌握") {
                DetailRow("真实 PA", "${report.realPa ?: "—"}")
                DetailRow("伤病倾向", "${report.injuryProneness ?: "—"} / 100")
                DetailRow("职业态度", "${report.professionalism ?: "—"} / 100")
            }
        } else if (report.reportLevel >= 4) {
            LockedSection("等级 5 - 完全掌握", "需累计观察 ${detail.nextLevelThreshold} 天 + 球探潜力判断 ≥ 15")
        }

        // 球探推荐
        SectionCard(title = "球探推荐（手动标记）") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 25, 50, 75, 100).forEach { level ->
                    FilterChip(
                        selected = report.scoutRecommendation == level,
                        onClick = { onSetRecommendation(level) },
                        label = { Text(if (level == 0) "未标记" else "$level") }
                    )
                }
            }
        }

        // 球探信息
        SectionCard(title = "球探信息") {
            DetailRow("球探", detail.scoutName)
            DetailRow("观察天数", "${report.observationDays} 天")
            DetailRow("创建日期", report.createdDate)
            DetailRow("最后更新", report.lastUpdatedDate)
        }
    }
}

// =================================================================
// Tab 4: 青年赛事事件
// =================================================================

@Composable
private fun EventsTab(
    events: List<com.greendynasty.football.scouting.model.ScoutEventItem>,
    onMarkRead: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "青年赛事事件（${events.size}）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (events.isEmpty()) {
            EmptyCard(
                title = "暂无事件",
                description = "派遣青年赛事观察任务，赛事举办月可触发事件"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.event.eventId }) { eventItem ->
                EventCard(eventItem, onClick = { onMarkRead(eventItem.event.eventId) })
            }
        }
    }
}

@Composable
private fun EventCard(
    item: com.greendynasty.football.scouting.model.ScoutEventItem,
    onClick: () -> Unit
) {
    val event = item.event
    val typeColor = when (event.eventType) {
        "YOUTH_HAT_TRICK" -> Color(0xFFFF9800)
        "BIG_CLUB_RUSH" -> Color(0xFFE91E63)
        "VALUE_SURGE" -> Color(0xFF4CAF50)
        "SCOUT_STRONG_RECOMMEND" -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (event.read == 0)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = typeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.eventTypeDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.eventDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (event.read == 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.error, shape = CircleShape) {
                        Box(modifier = Modifier.size(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// =================================================================
// Tab 5: 观察名单（V1 占位）
// =================================================================

@Composable
private fun WatchListPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EmptyCard(
            title = "观察名单（V1 暂未实现）",
            description = "V2 将复用 T10 转会搜索的观察名单组件"
        )
    }
}

// =================================================================
// 派遣任务弹窗
// =================================================================

@Composable
private fun ScoutTaskDispatchDialog(
    form: com.greendynasty.football.scouting.ui.state.DispatchFormState,
    onFormChange: ((com.greendynasty.football.scouting.ui.state.DispatchFormState) -> com.greendynasty.football.scouting.ui.state.DispatchFormState) -> Unit,
    onDispatch: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("派遣球探 - ${form.scoutName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 任务类型选择（8 种）
                Text("任务类型", style = MaterialTheme.typography.labelLarge)
                com.greendynasty.football.scouting.model.ScoutTaskType.values().forEachIndexed { index, type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = form.taskTypeIndex == index,
                            onClick = { onFormChange { it.copy(taskTypeIndex = index) } }
                        )
                        Text(type.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 地区选择（15 地区）
                Text("地区", style = MaterialTheme.typography.labelLarge)
                com.greendynasty.football.scouting.model.ScoutRegionCode.values().forEach { region ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = form.regionCode == region.code,
                            onClick = { onFormChange { it.copy(regionCode = region.code) } }
                        )
                        Text(region.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 位置搜索 → 输入位置
                if (form.taskTypeIndex == 1) { // POSITION_SEARCH
                    OutlinedTextField(
                        value = form.targetPosition,
                        onValueChange = { v -> onFormChange { it.copy(targetPosition = v) } },
                        label = { Text("目标位置（如 ST/CM）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 周期（30/60/90 天）
                Text("周期（天）", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90).forEach { days ->
                        FilterChip(
                            selected = form.durationDays == days,
                            onClick = { onFormChange { it.copy(durationDays = days) } },
                            label = { Text("$days 天") }
                        )
                    }
                }

                // 预算（低/中/高）
                Text("预算", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BudgetLevel.values().forEachIndexed { index, level ->
                        FilterChip(
                            selected = form.budgetLevelIndex == index,
                            onClick = { onFormChange { it.copy(budgetLevelIndex = index) } },
                            label = { Text(level.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDispatch) { Text("派遣") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LockedSection(title: String, hint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🔒 $title",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
