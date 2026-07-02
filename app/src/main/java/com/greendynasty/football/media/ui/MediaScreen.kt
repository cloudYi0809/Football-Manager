@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.greendynasty.football.media.model.AnswerStyle
import com.greendynasty.football.media.model.MediaImpact
import com.greendynasty.football.media.model.MediaInterviewEntity
import com.greendynasty.football.media.model.MediaNewsEntity
import com.greendynasty.football.media.model.NewsCategory
import com.greendynasty.football.media.model.OpinionLevel
import com.greendynasty.football.media.ui.state.MediaTab
import com.greendynasty.football.media.ui.state.MediaUiState
import com.greendynasty.football.media.ui.viewmodel.MediaViewModel

/**
 * T24 媒体页入口 Composable（V0.2 + T24 任务要求 + 实现方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → 媒体中心"进入。
 *
 * 4 个 Tab：
 * 1. 新闻列表：最近新闻 + 未读数 + 分类过滤
 * 2. 新闻详情：单条新闻完整内容
 * 3. 采访页：当前采访 + 问题 + 选项
 * 4. 舆论仪表盘：舆论值 + 等级 + 球迷支持度修正
 */
@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = viewModel(
        factory = MediaViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果消息
    LaunchedEffect((uiState as? MediaUiState.Normal)?.message) {
        (uiState as? MediaUiState.Normal)?.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 影响弹窗
    val lastImpact = (uiState as? MediaUiState.Normal)?.lastImpact
    if (lastImpact != null) {
        ImpactDialog(impact = lastImpact, onDismiss = viewModel::dismissImpact)
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
                is MediaUiState.Loading -> LoadingView()
                is MediaUiState.Locked -> EmptyView(state.reason)
                is MediaUiState.Empty -> EmptyView(state.reason)
                is MediaUiState.Error -> EmptyView(state.message)
                is MediaUiState.Normal -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MediaTabRow(currentTab, viewModel::switchTab, hasUnread = state.unreadCount > 0)
                        when (currentTab) {
                            MediaTab.NEWS -> NewsListTab(
                                state = state,
                                onSelectNews = viewModel::selectNews,
                                onMarkAllRead = viewModel::markAllNewsAsRead,
                                onSetFilter = viewModel::setNewsFilter
                            )
                            MediaTab.NEWS_DETAIL -> NewsDetailTab(
                                state = state,
                                onBack = { viewModel.switchTab(MediaTab.NEWS) }
                            )
                            MediaTab.INTERVIEW -> InterviewTab(
                                state = state,
                                onStart = viewModel::startInterview,
                                onAnswer = viewModel::answerQuestion,
                                onSkip = viewModel::skipInterview
                            )
                            MediaTab.OPINION -> OpinionDashboardTab(state = state)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Tab Row ====================

@Composable
private fun MediaTabRow(
    currentTab: MediaTab,
    onSwitchTab: (MediaTab) -> Unit,
    hasUnread: Boolean
) {
    TabRow(selectedTabIndex = currentTab.ordinal) {
        MediaTab.values().forEach { tab ->
            Tab(
                selected = currentTab == tab,
                onClick = { onSwitchTab(tab) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tab.title)
                        if (tab == MediaTab.NEWS && hasUnread) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                }
            )
        }
    }
}

// ==================== 1. 新闻列表 Tab ====================

@Composable
private fun NewsListTab(
    state: MediaUiState.Normal,
    onSelectNews: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onSetFilter: (NewsCategory?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 未读数 + 标记已读按钮 + 分类过滤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "未读 ${state.unreadCount} 条",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onMarkAllRead) {
                Text("全部已读")
            }
        }

        // 分类过滤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.filterCategory == null,
                onClick = { onSetFilter(null) },
                label = { Text("全部") }
            )
            NewsCategory.values().forEach { category ->
                FilterChip(
                    selected = state.filterCategory == category.name,
                    onClick = { onSetFilter(category) },
                    label = { Text(category.label) }
                )
            }
        }

        if (state.recentNews.isEmpty()) {
            EmptyView("暂无新闻")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.recentNews) { news ->
                    NewsCard(
                        news = news,
                        onClick = { onSelectNews(news.newsId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsCard(news: MediaNewsEntity, onClick: () -> Unit) {
    val isUnread = news.isRead == 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = news.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = news.newsDate,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· ${news.outletName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            ImportanceStars(importance = news.importance)
        }
    }
}

@Composable
private fun ImportanceStars(importance: Int) {
    Row {
        repeat(5) { i ->
            Text(
                text = if (i < importance) "★" else "☆",
                color = if (i < importance) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ==================== 2. 新闻详情 Tab ====================

@Composable
private fun NewsDetailTab(
    state: MediaUiState.Normal,
    onBack: () -> Unit
) {
    val news = state.selectedNews
    if (news == null) {
        EmptyView("请从新闻列表选择一条查看")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 返回按钮
        OutlinedButton(onClick = onBack) { Text("返回列表") }
        Spacer(Modifier.height(12.dp))

        // 重要性 + 媒体来源 + 日期
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImportanceStars(news.importance)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${news.outletName} · ${news.newsDate}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        AssistChip(
            onClick = {},
            label = { Text(NewsCategory.fromValue(news.category).label) }
        )

        Spacer(Modifier.height(12.dp))

        // 标题
        Text(
            text = news.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // 正文
        Text(
            text = news.body,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        // 关联实体
        if (news.relatedPlayerId != null || news.relatedClubId != null) {
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("关联信息", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (news.relatedPlayerId != null) {
                        Text("球员 ID：${news.relatedPlayerId}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (news.relatedClubId != null) {
                        Text("俱乐部 ID：${news.relatedClubId}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (news.relatedMatchId != null) {
                        Text("比赛 ID：${news.relatedMatchId}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ==================== 3. 采访 Tab ====================

@Composable
private fun InterviewTab(
    state: MediaUiState.Normal,
    onStart: (Long) -> Unit,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit
) {
    val currentInterview = state.currentInterview
    val currentQuestion = state.currentQuestion

    if (currentInterview == null) {
        // 显示待处理采访列表
        if (state.activeInterviews.isEmpty()) {
            EmptyView("暂无待处理采访")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.activeInterviews) { interview ->
                    PendingInterviewCard(
                        interview = interview,
                        onStart = { onStart(interview.interviewId) }
                    )
                }
            }
        }
        return
    }

    if (currentQuestion == null) {
        EmptyView("采访已完成")
        return
    }

    // 进行中的采访：显示当前问题 + 选项
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 进度
            val questionIds = currentInterview.questionIds.split(",").filter { it.isNotBlank() }
            val total = questionIds.size.coerceAtLeast(1)
            val current = (currentInterview.currentQuestionIndex + 1).coerceAtMost(total)
            Text(
                text = "问题 $current / $total",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = current.toFloat() / total,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // 问题文本
            Card {
                Text(
                    text = currentQuestion.questionText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("请选择回答：", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            // 选项列表
            currentQuestion.options.forEach { option ->
                AnswerOptionItem(
                    optionText = option.text,
                    style = option.style,
                    onClick = { onAnswer(option.optionId) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // 跳过按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSkip) {
                Text("跳过采访（舆论 -10）")
            }
        }
    }
}

@Composable
private fun PendingInterviewCard(
    interview: MediaInterviewEntity,
    onStart: () -> Unit
) {
    val scenarioLabel = when (interview.scenario) {
        "PRE_MATCH" -> "赛前采访"
        "POST_MATCH" -> "赛后采访"
        else -> interview.scenario
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(scenarioLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "调度日期：${interview.scheduledDate}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = onStart) { Text("开始") }
        }
    }
}

@Composable
private fun AnswerOptionItem(
    optionText: String,
    style: AnswerStyle,
    onClick: () -> Unit
) {
    val borderColor = when (style) {
        AnswerStyle.NEUTRAL -> MaterialTheme.colorScheme.outline
        AnswerStyle.CONFIDENT -> MaterialTheme.colorScheme.primary
        AnswerStyle.HUMBLE -> MaterialTheme.colorScheme.tertiary
        AnswerStyle.AGGRESSIVE -> MaterialTheme.colorScheme.error
        AnswerStyle.DEFLECT -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "[${style.label}]",
                style = MaterialTheme.typography.bodySmall,
                color = borderColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(optionText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ==================== 4. 舆论仪表盘 Tab ====================

@Composable
private fun OpinionDashboardTab(state: MediaUiState.Normal) {
    val opinion = state.opinion
    val value = opinion?.opinionValue ?: 50
    val level = state.opinionLevel
    val modifier = state.fanSupportModifier

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 舆论值大数字 + 等级
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = opinionColor(value).copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = opinionColor(value)
                )
                Text(
                    text = level.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = opinionColor(value)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "球迷支持度修正：${if (modifier >= 0) "+" else ""}$modifier / 月",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 进度条
        Text("舆论值 0-100", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = value / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = opinionColor(value)
        )

        Spacer(Modifier.height(24.dp))

        // 等级区间说明
        Text("舆论等级区间", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OpinionLevel.values().forEach { lv ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(opinionColor(lv.range.first))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${lv.label}（${lv.range.first}-${lv.range.last}）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "球迷 ${if (lv.fanSupportModifier >= 0) "+" else ""}${lv.fanSupportModifier}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 历史统计
        if (opinion != null) {
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("历史统计", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("历史峰值：${opinion.peakValue}", style = MaterialTheme.typography.bodySmall)
                    Text("历史谷值：${opinion.troughValue}", style = MaterialTheme.typography.bodySmall)
                    Text("累计新闻：${opinion.totalNewsCount}", style = MaterialTheme.typography.bodySmall)
                    Text("正面新闻：${opinion.positiveNewsCount}", style = MaterialTheme.typography.bodySmall)
                    Text("负面新闻：${opinion.negativeNewsCount}", style = MaterialTheme.typography.bodySmall)
                    opinion.lastInteractionDate?.let {
                        Text("最近互动：$it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** 根据舆论值返回对应颜色。 */
private fun opinionColor(value: Int): Color = when {
    value >= 80 -> Color(0xFF4CAF50) // 绿
    value >= 60 -> Color(0xFF8BC34A) // 浅绿
    value >= 40 -> Color(0xFFFFC107) // 黄
    value >= 20 -> Color(0xFFFF9800) // 橙
    else -> Color(0xFFF44336) // 红
}

// ==================== 影响弹窗 ====================

@Composable
private fun ImpactDialog(impact: MediaImpact, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("采访影响") },
        text = {
            Column {
                ImpactLine("球员士气", impact.moraleDelta)
                ImpactLine("球迷满意度", impact.fanSatisfactionDelta)
                ImpactLine("董事会满意度", impact.boardSatisfactionDelta)
                ImpactLine("媒体舆论", impact.opinionDelta)
                ImpactLine("俱乐部声望", impact.reputationDelta)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        }
    )
}

@Composable
private fun ImpactLine(label: String, delta: Int) {
    if (delta == 0) return
    val sign = if (delta > 0) "+" else ""
    val color = if (delta > 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$sign$delta", color = color, fontWeight = FontWeight.Bold)
    }
}

// ==================== 通用视图 ====================

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
