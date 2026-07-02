@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.board.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.greendynasty.football.board.model.BoardConfidenceEntity
import com.greendynasty.football.board.model.BoardEventEntity
import com.greendynasty.football.board.model.BoardExpectationSummary
import com.greendynasty.football.board.model.BoardFeedback
import com.greendynasty.football.board.model.BoardSatisfactionEntity
import com.greendynasty.football.board.model.BudgetRequestEntity
import com.greendynasty.football.board.model.DismissalDecision
import com.greendynasty.football.board.model.DismissalLevel
import com.greendynasty.football.board.model.LongTermGoalEntity
import com.greendynasty.football.board.model.ObjectiveProgress
import com.greendynasty.football.board.model.SeasonTargetEntity
import com.greendynasty.football.board.ui.state.BoardTab
import com.greendynasty.football.board.ui.state.BoardUiState
import com.greendynasty.football.board.ui.viewmodel.BoardViewModel

/**
 * T22 董事会页入口 Composable（V0.2 11 §四 + T22 方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → 董事会"进入。
 *
 * 5 个 Tab：
 * 1. 赛季目标：5 类目标 + 实时进度
 * 2. 长期目标：3 年/5 年规划
 * 3. 满意度：8 因子分项 + 历史曲线
 * 4. 预算申请：申请记录 + 提交表单
 * 5. 董事会评价：反馈文案 + 最近事件
 *
 * 解雇警告：当 [BoardUiState.Normal.dismissalWarning] 非 null 时弹窗提示。
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    viewModel: BoardViewModel = viewModel(
        factory = BoardViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果消息
    LaunchedEffect((uiState as? BoardUiState.Normal)?.message) {
        (uiState as? BoardUiState.Normal)?.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 解雇警告弹窗
    (uiState as? BoardUiState.Normal)?.dismissalWarning?.let { warning ->
        DismissalWarningDialog(warning = warning, onDismiss = viewModel::dismissWarning)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is BoardUiState.Loading -> LoadingView()
                is BoardUiState.Locked -> EmptyView(state.reason)
                is BoardUiState.Empty -> EmptyView(state.reason)
                is BoardUiState.Error -> EmptyView(state.message)
                is BoardUiState.Normal -> BoardContent(
                    state = state,
                    currentTab = currentTab,
                    onSwitchTab = viewModel::switchTab,
                    onGenerateTargets = viewModel::generateSeasonTargets,
                    onRefreshProgress = viewModel::refreshProgress
                )
            }
        }
    }
}

// ==================== 主内容区 ====================

@Composable
private fun BoardContent(
    state: BoardUiState.Normal,
    currentTab: BoardTab,
    onSwitchTab: (BoardTab) -> Unit,
    onGenerateTargets: () -> Unit,
    onRefreshProgress: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：信心值仪表盘 + 期望摘要
        BoardHeader(state)

        // Tab 切换栏
        TabRow(selectedTabIndex = currentTab.ordinal) {
            BoardTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { onSwitchTab(tab) },
                    text = { Text(tab.title) }
                )
            }
        }

        // Tab 内容
        when (currentTab) {
            BoardTab.SEASON_TARGET -> SeasonTargetTab(
                state = state,
                onGenerateTargets = onGenerateTargets,
                onRefreshProgress = onRefreshProgress
            )
            BoardTab.LONG_TERM -> LongTermGoalTab(state.longTermGoals)
            BoardTab.SATISFACTION -> SatisfactionTab(state)
            BoardTab.BUDGET -> BudgetRequestTab(state.budgetRequests)
            BoardTab.FEEDBACK -> FeedbackTab(state.feedback, state.recentEvents)
        }
    }
}

// ==================== 顶部 Header（信心值仪表盘 + 期望摘要） ====================

@Composable
private fun BoardHeader(state: BoardUiState.Normal) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "董事会信心值",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                state.confidence?.let { confidence ->
                    Text(
                        text = "${confidence.confidenceValue} / 100",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = confidenceValueColor(confidence.confidenceValue)
                    )
                } ?: Text("未初始化", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 信心值进度条
            state.confidence?.let { confidence ->
                LinearProgressIndicator(
                    progress = confidence.confidenceValue / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = confidenceValueColor(confidence.confidenceValue),
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 警告等级
            state.confidence?.let { confidence ->
                val warningColor = warningLevelColor(confidence.warningLevel)
                if (confidence.warningLevel != "NONE") {
                    Surface(
                        color = warningColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "⚠ ${warningLevelLabel(confidence.warningLevel)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = warningColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 期望摘要
            state.expectation?.let { expectation ->
                Spacer(modifier = Modifier.height(8.dp))
                ExpectationSummaryRow(expectation)
            }
        }
    }
}

@Composable
private fun ExpectationSummaryRow(expectation: BoardExpectationSummary) {
    Column {
        Text(
            text = "董事会期望",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoChip(label = "野心", value = "${expectation.ambition}")
            InfoChip(label = "耐心", value = "${expectation.patience}")
            InfoChip(label = "期望排名", value = "第 ${expectation.expectedLeaguePosition}")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoChip(label = "财政风格", value = expectation.financialStyle)
            InfoChip(label = "工资比上限", value = "${(expectation.wageRatioTarget * 100).toInt()}%")
            InfoChip(label = "杯赛期望", value = expectation.expectedCupRound)
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ==================== Tab 1: 赛季目标 ====================

@Composable
private fun SeasonTargetTab(
    state: BoardUiState.Normal,
    onGenerateTargets: () -> Unit,
    onRefreshProgress: () -> Unit
) {
    val target = state.seasonTarget
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(12.dp)
    ) {
        if (target == null) {
            EmptyCard(
                title = "本赛季尚未设定目标",
                body = "赛季初由董事会设定 5 类目标（联赛排名 / 杯赛 / 欧战 / 财政 / 青训）",
                actionText = "生成赛季目标",
                onAction = onGenerateTargets
            )
            return
        }

        // 5 类目标卡片
        SeasonTargetCard("联赛排名", importance = target.leaguePositionImportance,
            targetText = "目标排名：第 ${target.leaguePositionTarget}")
        SeasonTargetCard("杯赛", importance = target.cupImportance,
            targetText = "目标轮次：${target.cupTarget}")
        SeasonTargetCard("欧战", importance = target.europeanImportance,
            targetText = "目标轮次：${target.europeanTarget}")
        SeasonTargetCard("财政平衡", importance = target.financialImportance,
            targetText = "工资/收入比上限：${(target.financialWageRatioTarget * 100).toInt()}%")
        SeasonTargetCard("青训发展", importance = "SECONDARY",
            targetText = "本季提拔青训球员：${target.youthPromotionTarget} 人")

        Spacer(modifier = Modifier.height(12.dp))

        // 评估结果
        if (target.evaluationStatus != "PENDING") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = evaluationStatusColor(target.evaluationStatus).copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "赛季评估结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "状态：${evaluationStatusLabel(target.evaluationStatus)}")
                    Text(text = "综合评分：${target.evaluationScore.toInt()} / 100")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 实时进度
        if (state.objectiveProgress.isNotEmpty()) {
            Text(
                text = "实时进度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.objectiveProgress.forEach { progress -> ObjectiveProgressRow(progress) }
        } else {
            OutlinedButton(onClick = onRefreshProgress, modifier = Modifier.fillMaxWidth()) {
                Text("刷新目标进度")
            }
        }
    }
}

@Composable
private fun SeasonTargetCard(title: String, importance: String, targetText: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = targetText, style = MaterialTheme.typography.bodyMedium)
            }
            ImportanceBadge(importance)
        }
    }
}

@Composable
private fun ImportanceBadge(importance: String) {
    val isCore = importance == "CORE"
    Surface(
        color = (if (isCore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = if (isCore) "核心" else "次要",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ObjectiveProgressRow(progress: ObjectiveProgress) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = progress.targetType, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = progressStatusLabel(progress.status), style = MaterialTheme.typography.labelSmall, color = progressStatusColor(progress.status))
            }
            Text(text = "目标：${progress.targetValue}", style = MaterialTheme.typography.bodySmall)
            Text(text = "当前：${progress.currentValue}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = (progress.progressPercent / 100f).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

// ==================== Tab 2: 长期目标 ====================

@Composable
private fun LongTermGoalTab(goals: List<LongTermGoalEntity>) {
    if (goals.isEmpty()) {
        EmptyView("暂无长期目标")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(goals) { goal ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = longTermGoalTypeLabel(goal.goalType), fontWeight = FontWeight.Bold)
                        Text(text = "目标年：${goal.targetYear}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(text = "起始：${goal.startMetric.toInt()}  →  目标：${goal.targetMetric.toInt()}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "当前：${goal.currentMetric.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (goal.progressPercent / 100f).toFloat(),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                    Text(text = "进度：${goal.progressPercent.toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ==================== Tab 3: 满意度详情 ====================

@Composable
private fun SatisfactionTab(state: BoardUiState.Normal) {
    val snapshot = state.latestSatisfaction
    if (snapshot == null) {
        EmptyView("暂无满意度数据（月初自动生成）")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 综合满意度
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("综合满意度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${snapshot.overallSatisfaction.toInt()} / 100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = satisfactionColor(snapshot.overallSatisfaction))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("等级：${satisfactionLevelLabel(snapshot.satisfactionLevel)}", style = MaterialTheme.typography.bodySmall)
                Text("趋势：${trendLabel(snapshot.trendDirection)}", style = MaterialTheme.typography.bodySmall)
            }
        }

        // 8 因子分项
        Text("8 因子分项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ScoreRow("联赛成绩", snapshot.leaguePerformanceScore)
        ScoreRow("杯赛成绩", snapshot.cupPerformanceScore)
        ScoreRow("财政状况", snapshot.financialScore)
        ScoreRow("球迷满意度", snapshot.fanSatisfactionScore)
        ScoreRow("转会市场", snapshot.transferMarketScore)
        ScoreRow("青训发展", snapshot.youthDevelopmentScore)
        ScoreRow("更衣室稳定", snapshot.dressingRoomStabilityScore)
        ScoreRow("经理声望", snapshot.managerPersonalReputationScore)

        Spacer(modifier = Modifier.height(8.dp))
        // 历史曲线（简化：最近 12 个月列表）
        if (state.satisfactionHistory.size > 1) {
            Text("历史趋势（最近 ${state.satisfactionHistory.size} 期）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.satisfactionHistory.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = item.snapshotDate, style = MaterialTheme.typography.bodySmall)
                    Text(text = "${item.overallSatisfaction.toInt()}", style = MaterialTheme.typography.bodySmall, color = satisfactionColor(item.overallSatisfaction))
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = "${score.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = satisfactionColor(score))
        Spacer(modifier = Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = (score / 100f).toFloat(),
            modifier = Modifier.width(80.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = satisfactionColor(score)
        )
    }
}

// ==================== Tab 4: 预算申请 ====================

@Composable
private fun BudgetRequestTab(requests: List<BudgetRequestEntity>) {
    if (requests.isEmpty()) {
        EmptyView("暂无预算申请记录")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(requests) { request ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = budgetTypeLabel(request.requestType), fontWeight = FontWeight.Bold)
                        Text(text = request.requestDate, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(text = "申请：${request.requestedAmount} €", style = MaterialTheme.typography.bodySmall)
                    Text(text = "批准：${request.approvedAmount} €", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = budgetStatusColor(request.status))
                    Text(text = "状态：${budgetStatusLabel(request.status)}", style = MaterialTheme.typography.labelSmall)
                    request.boardResponse?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ==================== Tab 5: 董事会评价 ====================

@Composable
private fun FeedbackTab(feedback: BoardFeedback?, events: List<BoardEventEntity>) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        // 董事会反馈
        if (feedback != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = feedbackColor(feedback.tone).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = feedback.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = feedbackColor(feedback.tone))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = feedback.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 最近事件
        Text(text = "最近董事会事件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text(text = "暂无事件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            events.take(20).forEach { event ->
                EventRow(event)
            }
        }
    }
}

@Composable
private fun EventRow(event: BoardEventEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = event.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = event.eventDate, style = MaterialTheme.typography.labelSmall)
            }
            Text(text = event.body, style = MaterialTheme.typography.bodySmall)
            event.impactSummary?.let {
                Text(text = "影响：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==================== 解雇警告弹窗 ====================

@Composable
private fun DismissalWarningDialog(warning: DismissalDecision, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (warning.warningLevel) {
                    DismissalLevel.WARNING -> "⚠ 董事会警告"
                    DismissalLevel.ULTIMATUM -> "⚠ 最后通牒"
                    DismissalLevel.DISMISS -> "❌ 解雇通知"
                    DismissalLevel.NONE -> "董事会通知"
                },
                color = if (warning.warningLevel == DismissalLevel.NONE) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error
            )
        },
        text = { Text(warning.reason) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我已知悉")
            }
        }
    )
}

// ==================== 通用视图 ====================

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("正在加载董事会数据…")
        }
    }
}

@Composable
private fun EmptyView(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyCard(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}

// ==================== 颜色与文案工具 ====================

private fun confidenceValueColor(value: Int): Color = when {
    value >= 65 -> Color(0xFF2E7D32) // 绿
    value >= 40 -> Color(0xFFF57F17) // 黄
    value >= 25 -> Color(0xFFE65100) // 橙
    else -> Color(0xFFC62828) // 红
}

private fun warningLevelColor(level: String): Color = when (level) {
    "WARNING" -> Color(0xFFF57F17)
    "ULTIMATUM" -> Color(0xFFE65100)
    "DISMISS" -> Color(0xFFC62828)
    else -> Color(0xFF2E7D32)
}

private fun warningLevelLabel(level: String): String = when (level) {
    "WARNING" -> "警告：连续未达成核心目标"
    "ULTIMATUM" -> "最后通牒：再不改善将被解雇"
    "DISMISS" -> "解雇风险：即将被解雇"
    else -> ""
}

private fun satisfactionColor(score: Double): Color = when {
    score >= 80 -> Color(0xFF2E7D32)
    score >= 65 -> Color(0xFF558B2F)
    score >= 50 -> Color(0xFFF57F17)
    score >= 35 -> Color(0xFFE65100)
    else -> Color(0xFFC62828)
}

private fun satisfactionLevelLabel(level: String): String = when (level) {
    "EXCELLENT" -> "优秀"
    "GOOD" -> "良好"
    "ACCEPTABLE" -> "可接受"
    "POOR" -> "糟糕"
    "CRITICAL" -> "危急"
    else -> level
}

private fun trendLabel(trend: String): String = when (trend) {
    "RISING" -> "↑ 上升"
    "FALLING" -> "↓ 下降"
    else -> "→ 稳定"
}

private fun evaluationStatusColor(status: String): Color = when (status) {
    "ACHIEVED" -> Color(0xFF2E7D32)
    "PARTIALLY" -> Color(0xFFF57F17)
    "FAILED" -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}

private fun evaluationStatusLabel(status: String): String = when (status) {
    "ACHIEVED" -> "已达成"
    "PARTIALLY" -> "部分达成"
    "FAILED" -> "未达成"
    "PENDING" -> "待评估"
    else -> status
}

private fun progressStatusLabel(status: String): String = when (status) {
    "ON_TRACK" -> "进展顺利"
    "AT_RISK" -> "有风险"
    "BEHIND" -> "落后"
    else -> status
}

private fun progressStatusColor(status: String): Color = when (status) {
    "ON_TRACK" -> Color(0xFF2E7D32)
    "AT_RISK" -> Color(0xFFF57F17)
    "BEHIND" -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}

private fun longTermGoalTypeLabel(type: String): String = when (type) {
    "REPUTATION_RISE" -> "声望提升"
    "STADIUM_EXPANSION" -> "球场扩建"
    "YOUTH_FACILITY_UPGRADE" -> "青训设施升级"
    "COMMERCIAL_GROWTH" -> "商业增长"
    "TROPHY_WIN" -> "夺冠目标"
    else -> type
}

private fun budgetTypeLabel(type: String): String = when (type) {
    "TRANSFER_BUDGET" -> "转会预算"
    "WAGE_BUDGET" -> "工资预算"
    "YOUTH_FACILITY" -> "青训设施"
    "TRAINING_FACILITY" -> "训练设施"
    "MEDICAL_FACILITY" -> "医疗设施"
    "STADIUM_EXPANSION" -> "球场扩建"
    else -> type
}

private fun budgetStatusLabel(status: String): String = when (status) {
    "PENDING" -> "待审批"
    "APPROVED" -> "已批准"
    "REJECTED" -> "已拒绝"
    "NEGOTIATED" -> "部分批准"
    else -> status
}

private fun budgetStatusColor(status: String): Color = when (status) {
    "APPROVED" -> Color(0xFF2E7D32)
    "NEGOTIATED" -> Color(0xFFF57F17)
    "REJECTED" -> Color(0xFFC62828)
    "PENDING" -> Color(0xFF757575)
    else -> Color(0xFF757575)
}

private fun feedbackColor(tone: String): Color = when (tone) {
    "POSITIVE" -> Color(0xFF2E7D32)
    "NEUTRAL" -> Color(0xFFF57F17)
    "NEGATIVE" -> Color(0xFFE65100)
    "CRITICAL" -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}
