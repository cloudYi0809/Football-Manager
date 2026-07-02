@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.dressingroom.ui

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.greendynasty.football.dressingroom.model.AtmosphereLevel
import com.greendynasty.football.dressingroom.model.DressingRoomLeaderEntity
import com.greendynasty.football.dressingroom.model.EventSeverity
import com.greendynasty.football.dressingroom.model.LeaderRole
import com.greendynasty.football.dressingroom.model.MoraleLevel
import com.greendynasty.football.dressingroom.model.PlayerEmotionEventEntity
import com.greendynasty.football.dressingroom.model.PlayerEmotionEventType
import com.greendynasty.football.dressingroom.model.PlayerMoraleEntity
import com.greendynasty.football.dressingroom.ui.state.DressingRoomTab
import com.greendynasty.football.dressingroom.ui.state.DressingRoomUiState
import com.greendynasty.football.dressingroom.ui.viewmodel.DressingRoomViewModel

/**
 * T23 更衣室页入口 Composable（V0.2 + T23 任务要求 §二.6 + 实现方案 §六 UI 结构）。
 *
 * 二级页面，由首页"快捷入口 → 更衣室"进入。
 *
 * 4 个 Tab：
 * 1. 氛围：4 档氛围等级 + 稳定指数 + 球队士气 + 化学反应
 * 2. 士气：全员士气列表 + 不满球员列表
 * 3. 领袖：队长 / 副队长 / 影响力球员列表
 * 4. 事件：最近情绪事件列表
 */
@Composable
fun DressingRoomScreen(
    modifier: Modifier = Modifier,
    viewModel: DressingRoomViewModel = viewModel(
        factory = DressingRoomViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果消息
    LaunchedEffect((uiState as? DressingRoomUiState.Normal)?.message) {
        (uiState as? DressingRoomUiState.Normal)?.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
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
                is DressingRoomUiState.Loading -> LoadingView()
                is DressingRoomUiState.Locked -> EmptyView(state.reason)
                is DressingRoomUiState.Empty -> EmptyView(state.reason)
                is DressingRoomUiState.Error -> EmptyView(state.message)
                is DressingRoomUiState.Normal -> DressingRoomContent(
                    state = state,
                    currentTab = currentTab,
                    onSwitchTab = viewModel::switchTab,
                    onRecomputeChemistry = viewModel::recomputeChemistry,
                    onDetectLeaders = viewModel::detectLeaders,
                    onClearUnrest = viewModel::clearPlayerUnrest
                )
            }
        }
    }
}

// ==================== 主内容区 ====================

@Composable
private fun DressingRoomContent(
    state: DressingRoomUiState.Normal,
    currentTab: DressingRoomTab,
    onSwitchTab: (DressingRoomTab) -> Unit,
    onRecomputeChemistry: () -> Unit,
    onDetectLeaders: () -> Unit,
    onClearUnrest: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：氛围仪表盘
        DressingRoomHeader(state)

        // Tab 切换栏
        TabRow(selectedTabIndex = currentTab.ordinal) {
            DressingRoomTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { onSwitchTab(tab) },
                    text = { Text(tab.title) }
                )
            }
        }

        // 内容区
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                DressingRoomTab.ATMOSPHERE -> AtmosphereTab(
                    state = state,
                    onRecomputeChemistry = onRecomputeChemistry
                )
                DressingRoomTab.MORALE -> MoraleTab(
                    state = state,
                    onClearUnrest = onClearUnrest
                )
                DressingRoomTab.LEADER -> LeaderTab(
                    state = state,
                    onDetectLeaders = onDetectLeaders
                )
                DressingRoomTab.EVENT -> EventTab(state = state)
            }
        }
    }
}

// ==================== 顶部氛围仪表盘 ====================

@Composable
private fun DressingRoomHeader(state: DressingRoomUiState.Normal) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "更衣室概览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 氛围等级 + 稳定指数
            val atmosphere = state.atmosphere
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "氛围：${atmosphere?.level?.label ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = atmosphereColor(atmosphere?.level)
                )
                Text(
                    text = "稳定指数：${atmosphere?.stabilityIndex ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 球队士气 + 化学反应
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "球队士气：${state.teamMorale}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = moraleColor(state.teamMorale)
                )
                Text(
                    text = "化学反应：${(state.chemistryIndex * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = chemistryColor(state.chemistryIndex)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 不满球员数
            val unrestCount = atmosphere?.unrestCount ?: state.unhappyPlayers.size
            Text(
                text = "不满球员：$unrestCount 名",
                style = MaterialTheme.typography.bodyMedium,
                color = if (unrestCount > 0) Color(0xFFFF5722) else MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ==================== Tab 1: 氛围 ====================

@Composable
private fun AtmosphereTab(
    state: DressingRoomUiState.Normal,
    onRecomputeChemistry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "更衣室氛围仪表盘",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val atmosphere = state.atmosphere
        if (atmosphere == null) {
            EmptyCard(
                title = "暂无氛围评估",
                description = "月度推进后自动评估氛围，或手动触发化学反应重算"
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticRow("氛围等级", atmosphere.level.label, atmosphereColor(atmosphere.level))
                    StatisticRow("球队士气", "${atmosphere.teamMorale}", moraleColor(atmosphere.teamMorale))
                    StatisticRow(
                        "化学反应指数",
                        "${(atmosphere.chemistryIndex * 100).toInt()}%",
                        chemistryColor(atmosphere.chemistryIndex)
                    )
                    StatisticRow("领袖影响力", "${atmosphere.leaderInfluence}")
                    StatisticRow("不满球员数", "${atmosphere.unrestCount} 名")
                    StatisticRow(
                        "稳定指数",
                        "${atmosphere.stabilityIndex}",
                        MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    // 稳定指数进度条
                    LinearProgressIndicator(
                        progress = atmosphere.stabilityIndex / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = atmosphereColor(atmosphere.level)
                    )
                }
            }
        }

        // 4 档氛围说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "氛围等级说明",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text("• 和谐：士气 ≥75 + 化学反应 ≥70% + 无不满", style = MaterialTheme.typography.bodySmall)
                Text("• 一般：士气 ≥50 + 不满 ≤2", style = MaterialTheme.typography.bodySmall)
                Text("• 紧张：士气 ≥30 或 不满 ≥3", style = MaterialTheme.typography.bodySmall)
                Text("• 分裂：士气 <30 或 不满 ≥5", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(onClick = onRecomputeChemistry, modifier = Modifier.fillMaxWidth()) {
            Text("重算化学反应（转会窗关闭后调用）")
        }
    }
}

// ==================== Tab 2: 士气 ====================

@Composable
private fun MoraleTab(
    state: DressingRoomUiState.Normal,
    onClearUnrest: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "球员士气（${state.playerMorales.size} 名）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (state.unhappyPlayers.isNotEmpty()) {
            Text(
                text = "⚠ 不满球员：${state.unhappyPlayers.size} 名",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF5722),
                fontWeight = FontWeight.Bold
            )
        }

        if (state.playerMorales.isEmpty()) {
            EmptyCard(
                title = "暂无球员士气记录",
                description = "比赛日结算后将自动生成球员士气记录"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.playerMorales, key = { it.playerId }) { morale ->
                    MoraleCard(morale = morale, onClearUnrest = onClearUnrest)
                }
            }
        }
    }
}

@Composable
private fun MoraleCard(
    morale: PlayerMoraleEntity,
    onClearUnrest: (Int) -> Unit
) {
    val level = runCatching { MoraleLevel.valueOf(morale.moraleLevel) }.getOrDefault(MoraleLevel.MID)
    val cardColor = when (level) {
        MoraleLevel.EXTREME_HIGH -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        MoraleLevel.HIGH -> Color(0xFF8BC34A).copy(alpha = 0.15f)
        MoraleLevel.MID -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        MoraleLevel.LOW -> Color(0xFFFF9800).copy(alpha = 0.15f)
        MoraleLevel.EXTREME_LOW -> Color(0xFFFF5722).copy(alpha = 0.15f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(moraleColor(morale.morale)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${morale.playerId}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "球员 #${morale.playerId}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${level.label} · 士气 ${morale.morale}",
                        style = MaterialTheme.typography.bodySmall,
                        color = moraleColor(morale.morale)
                    )
                }
                // 不满标记
                if (morale.unrestAccumulator > 0) {
                    Text(
                        text = "不满×${morale.unrestAccumulator}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF5722),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 士气进度条
            LinearProgressIndicator(
                progress = morale.morale / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = moraleColor(morale.morale)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 连续首发 / 替补
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "连续首发：${morale.consecutiveStarts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "连续替补：${morale.consecutiveBenched}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 待谈话时显示谈话按钮
            if (morale.pendingConversation) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onClearUnrest(morale.playerId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("发起谈话（清除不满）")
                }
            }
        }
    }
}

// ==================== Tab 3: 领袖 ====================

@Composable
private fun LeaderTab(
    state: DressingRoomUiState.Normal,
    onDetectLeaders: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "更衣室领袖（${state.leaders.size} 名）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (state.leaders.isEmpty()) {
            EmptyCard(
                title = "暂无活跃领袖",
                description = "赛季初自动识别，或点击下方按钮立即识别"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.leaders, key = { it.id }) { leader ->
                    LeaderCard(leader = leader)
                }
            }
        }

        OutlinedButton(onClick = onDetectLeaders, modifier = Modifier.fillMaxWidth()) {
            Text("重新识别领袖（赛季初调用）")
        }
    }
}

@Composable
private fun LeaderCard(leader: DressingRoomLeaderEntity) {
    val role = runCatching { LeaderRole.valueOf(leader.leaderRole) }
        .getOrDefault(LeaderRole.INFLUENTIAL)
    val roleColor = when (role) {
        LeaderRole.CAPTAIN -> Color(0xFFFFD700)
        LeaderRole.VICE_CAPTAIN -> Color(0xFFC0C0C0)
        LeaderRole.INFLUENTIAL -> Color(0xFFCD7F32)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(roleColor.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (role) {
                        LeaderRole.CAPTAIN -> "C"
                        LeaderRole.VICE_CAPTAIN -> "V"
                        LeaderRole.INFLUENTIAL -> "I"
                    },
                    color = roleColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "球员 #${leader.playerId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${role.label} · 领导力 ${leader.leadership} · 影响力 ${leader.influence}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ==================== Tab 4: 事件 ====================

@Composable
private fun EventTab(state: DressingRoomUiState.Normal) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "球员情绪事件（${state.recentEvents.size} 条）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (state.recentEvents.isEmpty()) {
            EmptyCard(
                title = "暂无情绪事件",
                description = "周度推进时会自动检查球员情绪并触发事件"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.recentEvents, key = { it.id }) { event ->
                    EventCard(event = event)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: PlayerEmotionEventEntity) {
    val type = runCatching { PlayerEmotionEventType.valueOf(event.eventType) }
        .getOrDefault(PlayerEmotionEventType.UNHAPPY)
    val severity = runCatching { EventSeverity.valueOf(event.severity) }
        .getOrDefault(EventSeverity.MODERATE)
    val severityColor = when (severity) {
        EventSeverity.MINOR -> MaterialTheme.colorScheme.outline
        EventSeverity.MODERATE -> Color(0xFFFF9800)
        EventSeverity.MAJOR -> Color(0xFFFF5722)
        EventSeverity.CRITICAL -> Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = severity.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = severityColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "球员 #${event.playerId} · ${event.eventDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (event.moraleImpact != 0) {
                    Text(
                        text = "士气影响 ${event.moraleImpact}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (event.moraleImpact > 0) Color(0xFF4CAF50) else Color(0xFFFF5722)
                    )
                }
            }
            if (event.resolved) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✓ 已处理：${event.resolution ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ==================== 通用组件 ====================

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text("加载更衣室数据…")
        }
    }
}

@Composable
private fun EmptyView(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

@Composable
private fun StatisticRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

// ==================== 颜色工具 ====================

@Composable
private fun moraleColor(morale: Int): Color = when {
    morale >= 90 -> Color(0xFF4CAF50)
    morale >= 75 -> Color(0xFF8BC34A)
    morale >= 50 -> MaterialTheme.colorScheme.primary
    morale >= 30 -> Color(0xFFFF9800)
    else -> Color(0xFFFF5722)
}

@Composable
private fun atmosphereColor(level: AtmosphereLevel?): Color = when (level) {
    AtmosphereLevel.HARMONIOUS -> Color(0xFF4CAF50)
    AtmosphereLevel.NORMAL -> MaterialTheme.colorScheme.primary
    AtmosphereLevel.TENSE -> Color(0xFFFF9800)
    AtmosphereLevel.SPLIT -> Color(0xFFFF5722)
    null -> MaterialTheme.colorScheme.outline
}

private fun chemistryColor(index: Double): Color = when {
    index >= 0.7 -> Color(0xFF4CAF50)
    index >= 0.5 -> Color(0xFF8BC34A)
    index >= 0.3 -> Color(0xFFFF9800)
    else -> Color(0xFFFF5722)
}
