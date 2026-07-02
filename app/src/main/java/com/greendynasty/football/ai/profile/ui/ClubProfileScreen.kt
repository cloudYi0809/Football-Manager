@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.ai.profile.ui

import androidx.compose.foundation.background
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
import com.greendynasty.football.ai.profile.model.ClubPersonality
import com.greendynasty.football.ai.profile.model.ClubProfile
import com.greendynasty.football.ai.profile.model.ClubType
import com.greendynasty.football.ai.profile.model.LongTermGoal
import com.greendynasty.football.ai.profile.model.PlayerArchetype
import com.greendynasty.football.ai.profile.model.TacticalIdentity
import com.greendynasty.football.ai.profile.ui.state.ClubProfileTab
import com.greendynasty.football.ai.profile.ui.viewmodel.ClubProfileViewModel

/**
 * T18 俱乐部画像页入口 Composable（V0.2 05 §二 + T18 方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → AI 俱乐部画像"进入。
 *
 * 2 个 Tab：
 * - 画像列表：所有 AI 俱乐部画像，可按性格 / 战术 / 长期目标筛选 + 关键字搜索
 * - 性格分布：6 种性格 / 8 种战术 / 6 种长期目标的俱乐部数量统计
 *
 * 点击列表项进入画像详情（含完整 11 字段决策偏好 + 长期目标）。
 */
@Composable
fun ClubProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ClubProfileViewModel = viewModel(
        factory = ClubProfileViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
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

    // 若详情已选中，显示详情页；否则显示列表
    if (uiState.selectedProfile != null) {
        ClubProfileDetailScreen(
            profile = uiState.selectedProfile!!,
            clubName = uiState.clubNameMap[uiState.selectedProfile!!.clubId] ?: "俱乐部 #${uiState.selectedProfile!!.clubId}",
            onBack = viewModel::clearSelectedProfile,
            modifier = modifier
        )
        return
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
                ClubProfileTab.values().forEach { tab ->
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
                    ClubProfileTab.LIST -> ClubProfileListTab(
                        uiState = uiState,
                        onPersonalityFilter = viewModel::setPersonalityFilter,
                        onTacticalFilter = viewModel::setTacticalFilter,
                        onGoalFilter = viewModel::setGoalFilter,
                        onSearchKeywordChange = viewModel::setSearchKeyword,
                        onClearFilters = viewModel::clearFilters,
                        onSelectProfile = viewModel::selectProfile,
                        onInitialize = viewModel::initializeProfilesIfEmpty
                    )
                    ClubProfileTab.STATISTICS -> StatisticsTab(
                        uiState = uiState
                    )
                }
            }
        }
    }
}

// =================================================================
// Tab 1: 画像列表
// =================================================================

@Composable
private fun ClubProfileListTab(
    uiState: com.greendynasty.football.ai.profile.ui.state.ClubProfileUiState,
    onPersonalityFilter: (ClubPersonality?) -> Unit,
    onTacticalFilter: (TacticalIdentity?) -> Unit,
    onGoalFilter: (LongTermGoal?) -> Unit,
    onSearchKeywordChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSelectProfile: (ClubProfile) -> Unit,
    onInitialize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 顶部标题 + 初始化按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI 俱乐部画像（${uiState.filteredProfiles.size}/${uiState.profiles.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (uiState.profiles.isEmpty() && !uiState.isLoading) {
                Button(onClick = onInitialize) { Text("生成画像") }
            }
        }

        // 搜索框
        OutlinedTextField(
            value = uiState.searchKeyword,
            onValueChange = onSearchKeywordChange,
            label = { Text("搜索俱乐部名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 性格筛选 Chip 行
        Text(
            text = "性格筛选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ClubPersonality.values().forEach { p ->
                FilterChip(
                    selected = uiState.personalityFilter == p,
                    onClick = {
                        onPersonalityFilter(if (uiState.personalityFilter == p) null else p)
                    },
                    label = { Text(p.label) }
                )
            }
        }

        // 战术筛选 Chip 行
        Text(
            text = "战术风格",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TacticalIdentity.values().forEach { t ->
                FilterChip(
                    selected = uiState.tacticalFilter == t,
                    onClick = {
                        onTacticalFilter(if (uiState.tacticalFilter == t) null else t)
                    },
                    label = { Text(t.label) }
                )
            }
        }

        // 长期目标筛选 Chip 行
        Text(
            text = "长期目标",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LongTermGoal.values().forEach { g ->
                FilterChip(
                    selected = uiState.goalFilter == g,
                    onClick = {
                        onGoalFilter(if (uiState.goalFilter == g) null else g)
                    },
                    label = { Text(g.label) }
                )
            }
        }

        // 清除筛选按钮
        if (uiState.personalityFilter != null || uiState.tacticalFilter != null ||
            uiState.goalFilter != null || uiState.searchKeyword.isNotBlank()
        ) {
            OutlinedButton(onClick = onClearFilters) { Text("清除筛选") }
        }

        // 加载中
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        // 空列表占位
        if (uiState.filteredProfiles.isEmpty() && !uiState.isLoading) {
            EmptyCard(
                title = if (uiState.profiles.isEmpty()) "暂无俱乐部画像"
                else "无匹配俱乐部",
                description = if (uiState.profiles.isEmpty())
                    "点击右上角「生成画像」为当前存档批量初始化 AI 俱乐部画像"
                else "尝试调整筛选条件或清除筛选"
            )
        }

        // 画像列表
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.filteredProfiles, key = { it.clubId }) { profile ->
                ClubProfileCard(
                    profile = profile,
                    clubName = uiState.clubNameMap[profile.clubId] ?: "俱乐部 #${profile.clubId}",
                    onClick = { onSelectProfile(profile) }
                )
            }
        }
    }
}

@Composable
private fun ClubProfileCard(
    profile: ClubProfile,
    clubName: String,
    onClick: () -> Unit
) {
    val personalityColor = personalityColor(profile.personality)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
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
                // 性格色块（首字）
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(personalityColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.personality.label.first().toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clubName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "#${profile.clubId} · ${profile.personality.label} · ${profile.tacticalIdentity.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Surface(
                    color = personalityColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = profile.clubType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = personalityColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 长期目标 + 球员类型偏好
            Text(
                text = "目标：${profile.longTermGoal.label}（${profile.targetSeasons} 年） · " +
                    "偏好：${profile.playerArchetype.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 关键偏好条
            PreferenceBarItem("野心", profile.ambition)
            PreferenceBarItem("财力", profile.financialPower)
            PreferenceBarItem("青训", profile.youthPreference)
            PreferenceBarItem("球星", profile.starPreference)
        }
    }
}

@Composable
private fun PreferenceBarItem(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(36.dp)
        )
        LinearProgressIndicator(
            progress = value / 100f,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (value >= 70) Color(0xFFE53935)
            else if (value >= 50) Color(0xFFFFA726)
            else Color(0xFF66BB6A),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(28.dp)
        )
    }
}

// =================================================================
// Tab 2: 性格分布统计
// =================================================================

@Composable
private fun StatisticsTab(
    uiState: com.greendynasty.football.ai.profile.ui.state.ClubProfileUiState
) {
    val stats = uiState.statistics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stats == null) {
            EmptyCard(
                title = "暂无统计数据",
                description = "请先在画像列表 Tab 生成画像"
            )
            return@Column
        }

        Text(
            text = "共 ${stats.totalClubs} 家俱乐部",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // 性格分布
        StatisticsSection(
            title = "性格分布（6 种）",
            entries = ClubPersonality.values().map { p ->
                p.label to (stats.byPersonality[p] ?: 0)
            }
        )

        // 战术风格分布
        StatisticsSection(
            title = "战术风格分布（8 种）",
            entries = TacticalIdentity.values().map { t ->
                t.label to (stats.byTacticalIdentity[t] ?: 0)
            }
        )

        // 长期目标分布
        StatisticsSection(
            title = "长期目标分布（6 种）",
            entries = LongTermGoal.values().map { g ->
                g.label to (stats.byLongTermGoal[g] ?: 0)
            }
        )

        // 球员类型偏好分布
        StatisticsSection(
            title = "球员类型偏好分布（5 种）",
            entries = PlayerArchetype.values().map { a ->
                a.label to (stats.byPlayerArchetype[a] ?: 0)
            }
        )
    }
}

@Composable
private fun StatisticsSection(
    title: String,
    entries: List<Pair<String, Int>>
) {
    val max = entries.maxOf { it.second }.coerceAtLeast(1)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
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
            entries.forEach { (label, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(80.dp)
                    )
                    LinearProgressIndicator(
                        progress = count.toFloat() / max,
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

// =================================================================
// 画像详情页
// =================================================================

@Composable
private fun ClubProfileDetailScreen(
    profile: ClubProfile,
    clubName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部：返回按钮 + 俱乐部名
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("返回") }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = clubName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = personalityColor(profile.personality).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${profile.personality.label} · ${profile.clubType.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = personalityColor(profile.personality),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 性格描述卡
            DetailCard(title = "性格画像") {
                Text(
                    text = profile.personality.label + "（${profile.personality.name}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.personality.description,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 战术风格
            DetailCard(title = "战术风格倾向") {
                Text(
                    text = profile.tacticalIdentity.label + "（${profile.tacticalIdentity.name}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.tacticalIdentity.description,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "关键位置：" + profile.tacticalIdentity.keyPositions.joinToString(" / "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 球员类型偏好
            DetailCard(title = "球员类型偏好") {
                Text(
                    text = profile.playerArchetype.label + "（${profile.playerArchetype.name}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.playerArchetype.description,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                profile.playerArchetype.ageRange?.let {
                    Text(
                        text = "偏好年龄：${it.first}-${it.last} 岁",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                profile.playerArchetype.minCa?.let {
                    Text(
                        text = "最低 CA：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                profile.playerArchetype.minPa?.let {
                    Text(
                        text = "最低 PA：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 长期目标
            DetailCard(title = "长期目标") {
                Text(
                    text = profile.longTermGoal.label + "（${profile.longTermGoal.name}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.longTermGoal.description,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "目标赛季数：${profile.targetSeasons} 年",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 决策偏好（11 字段）
            DetailCard(title = "决策偏好（0-100）") {
                PreferenceBarItem("野心", profile.ambition)
                PreferenceBarItem("财力", profile.financialPower)
                PreferenceBarItem("青训偏好", profile.youthPreference)
                PreferenceBarItem("球星偏好", profile.starPreference)
                PreferenceBarItem("转售偏好", profile.resalePreference)
                PreferenceBarItem("本土偏好", profile.domesticPreference)
                PreferenceBarItem("风险容忍", profile.riskTolerance)
                PreferenceBarItem("工资纪律", profile.wageStrictness)
                PreferenceBarItem("主帅耐心", profile.patienceWithManager)
                PreferenceBarItem("转会预算%", profile.transferBudgetRatio)
                PreferenceBarItem("青训投入%", profile.youthInvestmentRatio)
            }

            // 兼容 V0.2 05 §三 5 种 ClubType
            DetailCard(title = "派生类型（V0.2 §三）") {
                Text(
                    text = "ClubType: ${profile.clubType.name}（${profile.clubType.label}）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "由 ambition + starPreference + financialPower + wageStrictness 综合推导，" +
                        "T13 AI 转会决策可消费此字段调整 target_score 权重。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ==================== 工具函数 ====================

/** 性格对应主题色。 */
private fun personalityColor(personality: ClubPersonality): Color = when (personality) {
    ClubPersonality.CONSERVATIVE -> Color(0xFF66BB6A)   // 绿色
    ClubPersonality.AGGRESSIVE -> Color(0xFFEF5350)     // 红色
    ClubPersonality.PRAGMATIC -> Color(0xFFFFA726)      // 橙色
    ClubPersonality.IDEALIST -> Color(0xFF42A5F5)       // 蓝色
    ClubPersonality.MONEY_DRIVEN -> Color(0xFFAB47BC)   // 紫色
    ClubPersonality.YOUTH_ADVOCATE -> Color(0xFF26A69A) // 青色
}
