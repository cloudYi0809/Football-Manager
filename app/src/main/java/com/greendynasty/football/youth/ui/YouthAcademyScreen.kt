@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.youth.ui

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
import com.greendynasty.football.youth.model.AcademyStyle
import com.greendynasty.football.youth.model.InvestmentField
import com.greendynasty.football.youth.repository.YouthAcademyViewItem
import com.greendynasty.football.youth.repository.YouthAcademyStatistics
import com.greendynasty.football.youth.repository.YouthPlayerViewItem
import com.greendynasty.football.youth.ui.state.YouthTab
import com.greendynasty.football.youth.ui.viewmodel.YouthViewModel

/**
 * T16 青训学院页入口 Composable（V0.1 08 §二 + T16 方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → 青训学院"进入。
 *
 * 5 个 Tab：
 * - 学院概览：青训等级 / 设施 / 招募 / 声望 / 风格 / 预算 / 统计
 * - U18 球员：14-17 岁梯队
 * - U21 球员：18-21 岁梯队
 * - 青训报告：最近事件
 * - 投资风格：投资升级 + 风格切换
 *
 * 点击球员卡 → 详情视图（属性 + 标签 + 操作按钮）
 *
 * @param saveId 存档 ID
 * @param clubId 俱乐部 ID
 */
@Composable
fun YouthAcademyScreen(
    saveId: Int,
    clubId: Int,
    modifier: Modifier = Modifier,
    viewModel: YouthViewModel = viewModel(
        factory = YouthViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application,
            saveId,
            clubId
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
            val selected = uiState.selectedPlayer
            if (selected != null) {
                YouthPlayerDetailHeader(
                    player = selected,
                    onBack = viewModel::clearSelectedPlayer,
                    onSignProContract = {
                        viewModel.signProContract(selected.youthPlayerId, wage = 2000, years = 3)
                    },
                    onPromote = { viewModel.promoteToFirstTeam(selected.youthPlayerId) },
                    onLoan = { viewModel.loanOut(selected.youthPlayerId) },
                    onRelease = { viewModel.release(selected.youthPlayerId) },
                    onSetKeyProspect = { viewModel.setKeyProspect(selected.youthPlayerId, !selected.isKeyProspect) }
                )
                return@Column
            }

            // Tab 切换栏
            TabRow(selectedTabIndex = currentTab.ordinal) {
                YouthTab.entries.forEach { tab ->
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
                    YouthTab.OVERVIEW -> OverviewTab(
                        academy = uiState.academy,
                        statistics = uiState.statistics,
                        clubBalance = uiState.clubBalance,
                        isLoading = uiState.isLoading,
                        onTriggerMonthly = viewModel::triggerMonthlyProcess
                    )
                    YouthTab.U18 -> PlayersTab(
                        title = "U18 球员（14-17 岁）",
                        players = uiState.u18Players,
                        isLoading = uiState.isLoading,
                        onSelect = viewModel::selectPlayer
                    )
                    YouthTab.U21 -> PlayersTab(
                        title = "U21 球员（18-21 岁）",
                        players = uiState.u21Players,
                        isLoading = uiState.isLoading,
                        onSelect = viewModel::selectPlayer
                    )
                    YouthTab.EVENTS -> EventsTab(events = uiState.recentEvents)
                    YouthTab.INVEST -> InvestTab(
                        academy = uiState.academy,
                        clubBalance = uiState.clubBalance,
                        onInvest = viewModel::invest,
                        onChangeStyle = viewModel::changeStyle
                    )
                }
            }
        }
    }
}

// =================================================================
// Tab 1: 学院概览
// =================================================================

@Composable
private fun OverviewTab(
    academy: YouthAcademyViewItem?,
    statistics: YouthAcademyStatistics,
    clubBalance: Int,
    isLoading: Boolean,
    onTriggerMonthly: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "青训学院概览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (academy != null) {
            // 学院基本信息卡
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "学院配置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Divider()
                    StatisticRow("青训等级", "${academy.youthLevel} / 100")
                    LevelBar(academy.youthLevel)
                    StatisticRow("训练设施", "${academy.trainingFacility} / 100")
                    LevelBar(academy.trainingFacility)
                    StatisticRow("招募范围", academy.recruitmentRange.displayName)
                    StatisticRow("青训声望", "${academy.academyReputation} / 100")
                    LevelBar(academy.academyReputation)
                    StatisticRow("青训风格", "${academy.academyStyle.displayName}（${academy.academyStyle.description}）")
                    StatisticRow("月度预算", "${academy.monthlyBudget} €")
                    StatisticRow("U18 教练质量", "${academy.u18CoachQuality} / 100")
                    LevelBar(academy.u18CoachQuality)
                    StatisticRow("U21 教练质量", "${academy.u21CoachQuality} / 100")
                    LevelBar(academy.u21CoachQuality)
                    StatisticRow("国家人才池加成", "${academy.nationTalentPoolBonus} / 100")
                    if (academy.styleChangeCooldown > 0) {
                        Surface(
                            color = Color(0xFFFF9800).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "风格切换冷却中：剩余 ${academy.styleChangeCooldown} 个月",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 统计信息卡
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "青训统计",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Divider()
                StatisticRow("U18 球员数", "${statistics.u18Count} 名")
                StatisticRow("U21 球员数", "${statistics.u21Count} 名")
                StatisticRow(
                    "天才球员数",
                    "${statistics.geniusCount} 名",
                    highlight = statistics.geniusCount > 0
                )
                StatisticRow(
                    "高潜力球员（PA≥80）",
                    "${statistics.highPotentialCount} 名",
                    highlight = statistics.highPotentialCount > 0
                )
                StatisticRow("已提拔一线队", "${statistics.firstTeamPromotedCount} 名")
                StatisticRow("累计投资", "${statistics.totalInvestment} €")
                StatisticRow("俱乐部余额", "$clubBalance €")
            }
        }

        OutlinedButton(onClick = onTriggerMonthly, modifier = Modifier.fillMaxWidth()) {
            Text("手动触发月度处理（调试用）")
        }
    }
}

// =================================================================
// Tab 2/3: 球员列表
// =================================================================

@Composable
private fun PlayersTab(
    title: String,
    players: List<YouthPlayerViewItem>,
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
            text = "$title（${players.size} 名）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (players.isEmpty() && !isLoading) {
            EmptyCard(
                title = "暂无青训球员",
                description = "每月初由青训学院按 7 因子产出质量公式概率性生成新球员"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(players, key = { it.youthPlayerId }) { player ->
                YouthPlayerCard(player, onClick = { onSelect(player.youthPlayerId) })
            }
        }
    }
}

// =================================================================
// Tab 4: 青训报告（事件）
// =================================================================

@Composable
private fun EventsTab(events: List<com.greendynasty.football.youth.model.YouthEventEntity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "青训报告（${events.size} 个事件）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (events.isEmpty()) {
            EmptyCard(
                title = "暂无青训事件",
                description = "随着青训球员成长，将触发黄金一代 / 教练推荐 / 国青入选等事件"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.eventId }) { event ->
                YouthEventCard(event)
            }
        }
    }
}

// =================================================================
// Tab 5: 投资与风格
// =================================================================

@Composable
private fun InvestTab(
    academy: YouthAcademyViewItem?,
    clubBalance: Int,
    onInvest: (InvestmentField) -> Unit,
    onChangeStyle: (AcademyStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "投资升级（俱乐部余额：$clubBalance €）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (academy == null) {
            EmptyCard(
                title = "青训学院未初始化",
                description = "请先进入青训学院概览页自动初始化"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 5 项投资
                items(InvestmentField.entries.toList()) { field ->
                    InvestmentCard(
                        field = field,
                        academy = academy,
                        onInvest = { onInvest(field) }
                    )
                }
                // 风格切换
                item {
                    StyleChangeSection(
                        currentStyle = academy.academyStyle,
                        cooldown = academy.styleChangeCooldown,
                        onChangeStyle = onChangeStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun InvestmentCard(
    field: InvestmentField,
    academy: YouthAcademyViewItem,
    onInvest: () -> Unit
) {
    val (currentLevel, maxLevel) = when (field) {
        InvestmentField.YOUTH_LEVEL -> academy.youthLevel to 100
        InvestmentField.TRAINING_FACILITY -> academy.trainingFacility to 100
        InvestmentField.RECRUITMENT_RANGE -> academy.recruitmentRange.ordinal to 3
        InvestmentField.U18_COACH -> academy.u18CoachQuality to 100
        InvestmentField.U21_COACH -> academy.u21CoachQuality to 100
    }
    val progress = currentLevel.toFloat() / maxLevel.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = field.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$currentLevel / $maxLevel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = onInvest,
                enabled = currentLevel < maxLevel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentLevel < maxLevel) "升级至 ${currentLevel + 1}" else "已满级")
            }
        }
    }
}

@Composable
private fun StyleChangeSection(
    currentStyle: AcademyStyle,
    cooldown: Int,
    onChangeStyle: (AcademyStyle) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "青训风格切换",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "当前风格：${currentStyle.displayName}（${currentStyle.description}）",
                style = MaterialTheme.typography.bodyMedium
            )
            if (cooldown > 0) {
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "冷却中：剩余 $cooldown 个月",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Divider()
            // 8 种风格网格
            AcademyStyle.entries.forEach { style ->
                val isCurrent = style == currentStyle
                val isEnabled = !isCurrent && cooldown == 0
                OutlinedButton(
                    onClick = { onChangeStyle(style) },
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = (if (isCurrent) "✓ " else "") + style.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = style.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// =================================================================
// 球员卡片
// =================================================================

@Composable
private fun YouthPlayerCard(
    player: YouthPlayerViewItem,
    onClick: () -> Unit
) {
    val statusColor = when (player.status) {
        "YOUTH_CONTRACT" -> Color(0xFF2196F3)
        "PROFESSIONAL_CONTRACT" -> Color(0xFF4CAF50)
        "FIRST_TEAM" -> Color(0xFFE91E63)
        "LOANED_OUT" -> Color(0xFFFF9800)
        "LEAVING" -> Color(0xFF9E9E9E)
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
                            if (player.isGenius) Color(0xFFFF5722)
                            else MaterialTheme.colorScheme.primary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = player.playerName.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = player.playerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append(player.primaryPosition)
                            if (player.alternativePositions.isNotEmpty()) {
                                append(" / ").append(player.alternativePositions.joinToString("/"))
                            }
                            append(" · ").append(player.age).append(" 岁")
                            append(" · ").append(player.nationality)
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
                        text = player.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CA/PA 条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CA ${player.currentCa} / PA ${player.potentialPa}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (player.isGenius) {
                    Surface(
                        color = Color(0xFFFF5722).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "★ 天才",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5722),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (player.isKeyProspect) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "重点",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // CA/PA 进度条
            val caProgress = if (player.potentialPa > 0) {
                player.currentCa.toFloat() / player.potentialPa.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = caProgress,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            // 隐藏标签
            if (player.hiddenTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    player.hiddenTags.take(3).forEach { tag ->
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

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("查看详情 →")
            }
        }
    }
}

// =================================================================
// 球员详情视图
// =================================================================

@Composable
private fun YouthPlayerDetailHeader(
    player: YouthPlayerViewItem,
    onBack: () -> Unit,
    onSignProContract: () -> Unit,
    onPromote: () -> Unit,
    onLoan: () -> Unit,
    onRelease: () -> Unit,
    onSetKeyProspect: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            text = player.playerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (player.isGenius) {
                            Surface(
                                color = Color(0xFFFF5722).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "★ 天才",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF5722),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("位置：").append(player.primaryPosition)
                            if (player.alternativePositions.isNotEmpty()) {
                                append(" / ").append(player.alternativePositions.joinToString("/"))
                            }
                            append("  年龄：").append(player.age).append(" 岁\n")
                            append("国籍：").append(player.nationality).append("\n")
                            append("CA：").append(player.currentCa)
                            append("  PA：").append(player.potentialPa)
                            append("  初始 PA：").append(player.initialPa).append("\n")
                            append("梯队：").append(player.tier)
                            append("  状态：").append(player.status).append("\n")
                            append("职业态度：").append(player.professionalism).append("/100\n")
                            append("合同：").append(player.contractType)
                            append(" 至 ").append(player.contractUntil ?: "—").append("\n")
                            append("周薪：").append(player.wage).append(" €\n")
                            if (player.hiddenTags.isNotEmpty()) {
                                append("标签：").append(player.hiddenTags.joinToString(", "))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 操作按钮组
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "球员操作",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onSetKeyProspect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (player.isKeyProspect) "取消重点培养" else "设为重点培养（成长 +15%）")
                    }
                    if (player.contractType != "PROFESSIONAL") {
                        OutlinedButton(
                            onClick = onSignProContract,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = player.age >= 17
                        ) {
                            Text(if (player.age >= 17) "签职业合同（3 年 / 2000 €周薪）" else "未满 17 岁无法签职业合同")
                        }
                    }
                    if (player.status != "FIRST_TEAM") {
                        OutlinedButton(
                            onClick = onPromote,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = player.age >= 17 && player.contractType == "PROFESSIONAL"
                        ) {
                            Text("提拔至一线队")
                        }
                    }
                    if (player.status != "LOANED_OUT") {
                        OutlinedButton(
                            onClick = onLoan,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = player.age >= 18
                        ) {
                            Text(if (player.age >= 18) "外租培养" else "未满 18 岁无法外租")
                        }
                    }
                    OutlinedButton(
                        onClick = onRelease,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("放弃培养", color = Color(0xFFFF5722))
                    }
                }
            }
        }
    }
}

// =================================================================
// 事件卡片
// =================================================================

@Composable
private fun YouthEventCard(event: com.greendynasty.football.youth.model.YouthEventEntity) {
    val importanceColor = when (event.importance) {
        5 -> Color(0xFFE91E63)
        4 -> Color(0xFFFF5722)
        3 -> Color(0xFFFF9800)
        2 -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = importanceColor.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = importanceColor
                )
                Surface(
                    color = importanceColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = event.eventType,
                        style = MaterialTheme.typography.labelSmall,
                        color = importanceColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = event.triggerDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall
            )
            if (event.status != "PENDING") {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "已处理：${event.status}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

@Composable
private fun LevelBar(level: Int, max: Int = 100) {
    LinearProgressIndicator(
        progress = level.toFloat() / max.toFloat(),
        modifier = Modifier.fillMaxWidth().height(4.dp)
    )
}

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
