package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.ui.tactics.model.FormationDefinition
import com.greendynasty.football.ui.tactics.ui.state.TacticsUiState
import com.greendynasty.football.ui.tactics.viewmodel.TacticsViewModel

/**
 * 战术页入口 Composable（V0.1 03 §3 战术页）。
 *
 * 结构：
 * - FormationSection：阵型选择 + 2D 球场 + 替补席
 * - StyleSection：战术风格选择
 * - ParametersSection：战术参数滑块
 * - RoleSection：角色分配面板
 *
 * 处理 6 种 UI 状态：Loading / Error / Empty / Normal / Warning / Locked。
 *
 * @param viewModel 战术页 ViewModel
 */
@Composable
fun TacticsScreen(
    modifier: Modifier = Modifier,
    viewModel: TacticsViewModel = viewModel(
        factory = TacticsViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()

    TacticsScreenContent(
        uiState = uiState,
        message = message,
        onConsumeMessage = viewModel::consumeMessage,
        onChangeFormation = viewModel::changeFormation,
        onChangeStyle = viewModel::changeStyle,
        onUpdateParameters = viewModel::updateParameters,
        onAssignRole = viewModel::assignRole,
        onPlayerDragged = viewModel::dragPlayer,
        onDragSubstituteToSlot = viewModel::dragPlayerToSlot,
        onSaveTactics = viewModel::saveTactics,
        modifier = modifier
    )
}

/**
 * 战术页内容（纯 Composable，便于预览与测试）。
 */
@Composable
private fun TacticsScreenContent(
    uiState: TacticsUiState,
    message: String?,
    onConsumeMessage: () -> Unit,
    onChangeFormation: (com.greendynasty.football.match.api.Formation) -> Unit,
    onChangeStyle: (com.greendynasty.football.match.api.TacticStyle) -> Unit,
    onUpdateParameters: (com.greendynasty.football.ui.tactics.model.TacticalParameters) -> Unit,
    onAssignRole: (com.greendynasty.football.ui.tactics.model.PlayerRole, Int?) -> Unit,
    onPlayerDragged: (Int, Int) -> Unit,
    onDragSubstituteToSlot: (Int, Int) -> Unit,
    onSaveTactics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is TacticsUiState.Loading -> LoadingState()
            is TacticsUiState.Error -> ErrorState(uiState.message)
            is TacticsUiState.Empty -> EmptyState(uiState.reason)
            is TacticsUiState.Locked -> LockedState(uiState.reason)
            is TacticsUiState.Normal -> NormalContent(
                state = uiState,
                onChangeFormation = onChangeFormation,
                onChangeStyle = onChangeStyle,
                onUpdateParameters = onUpdateParameters,
                onAssignRole = onAssignRole,
                onPlayerDragged = onPlayerDragged,
                onDragSubstituteToSlot = onDragSubstituteToSlot,
                onSaveTactics = onSaveTactics
            )
            is TacticsUiState.Warning -> WarningContent(
                state = uiState,
                onChangeFormation = onChangeFormation,
                onChangeStyle = onChangeStyle,
                onUpdateParameters = onUpdateParameters,
                onAssignRole = onAssignRole,
                onPlayerDragged = onPlayerDragged,
                onDragSubstituteToSlot = onDragSubstituteToSlot,
                onSaveTactics = onSaveTactics
            )
        }

        // 操作结果提示
        if (message != null) {
            SnackbarMessage(message = message, onDismiss = onConsumeMessage)
        }
    }
}

// ==================== 状态视图 ====================

/** 加载中 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("加载战术数据中…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 错误 */
@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "加载失败：$message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

/** 空数据 */
@Composable
private fun EmptyState(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 功能未解锁 */
@Composable
private fun LockedState(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ==================== 正常内容 ====================

/** 正常状态内容 */
@Composable
private fun NormalContent(
    state: TacticsUiState.Normal,
    onChangeFormation: (com.greendynasty.football.match.api.Formation) -> Unit,
    onChangeStyle: (com.greendynasty.football.match.api.TacticStyle) -> Unit,
    onUpdateParameters: (com.greendynasty.football.ui.tactics.model.TacticalParameters) -> Unit,
    onAssignRole: (com.greendynasty.football.ui.tactics.model.PlayerRole, Int?) -> Unit,
    onPlayerDragged: (Int, Int) -> Unit,
    onDragSubstituteToSlot: (Int, Int) -> Unit,
    onSaveTactics: () -> Unit
) {
    TacticsContent(
        setup = state.setup,
        availablePlayers = state.availablePlayers,
        proficiency = state.proficiency,
        onChangeFormation = onChangeFormation,
        onChangeStyle = onChangeStyle,
        onUpdateParameters = onUpdateParameters,
        onAssignRole = onAssignRole,
        onPlayerDragged = onPlayerDragged,
        onDragSubstituteToSlot = onDragSubstituteToSlot,
        onSaveTactics = onSaveTactics
    )
}

/** 警告状态内容（有风险但仍展示） */
@Composable
private fun WarningContent(
    state: TacticsUiState.Warning,
    onChangeFormation: (com.greendynasty.football.match.api.Formation) -> Unit,
    onChangeStyle: (com.greendynasty.football.match.api.TacticStyle) -> Unit,
    onUpdateParameters: (com.greendynasty.football.ui.tactics.model.TacticalParameters) -> Unit,
    onAssignRole: (com.greendynasty.football.ui.tactics.model.PlayerRole, Int?) -> Unit,
    onPlayerDragged: (Int, Int) -> Unit,
    onDragSubstituteToSlot: (Int, Int) -> Unit,
    onSaveTactics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 风险警告横幅
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        TacticsContent(
            setup = state.setup,
            availablePlayers = state.availablePlayers,
            proficiency = state.proficiency,
            onChangeFormation = onChangeFormation,
            onChangeStyle = onChangeStyle,
            onUpdateParameters = onUpdateParameters,
            onAssignRole = onAssignRole,
            onPlayerDragged = onPlayerDragged,
            onDragSubstituteToSlot = onDragSubstituteToSlot,
            onSaveTactics = onSaveTactics
        )
    }
}

/**
 * 战术页核心内容（阵型 + 风格 + 参数 + 角色）。
 */
@Composable
private fun TacticsContent(
    setup: com.greendynasty.football.ui.tactics.model.TacticalSetup,
    availablePlayers: List<com.greendynasty.football.ui.tactics.data.PlayerWithPosition>,
    proficiency: Double,
    onChangeFormation: (com.greendynasty.football.match.api.Formation) -> Unit,
    onChangeStyle: (com.greendynasty.football.match.api.TacticStyle) -> Unit,
    onUpdateParameters: (com.greendynasty.football.ui.tactics.model.TacticalParameters) -> Unit,
    onAssignRole: (com.greendynasty.football.ui.tactics.model.PlayerRole, Int?) -> Unit,
    onPlayerDragged: (Int, Int) -> Unit,
    onDragSubstituteToSlot: (Int, Int) -> Unit,
    onSaveTactics: () -> Unit
) {
    // 当前选中的首发槽位（用于替补拖入首发）
    var selectedSlotId by remember { mutableStateOf<Int?>(null) }
    val playerNames = availablePlayers.associate { it.playerId to it.name }
    val formationDef = FormationDefinition.from(setup.formation)
    // 替补球员列表（从 availablePlayers 中排除首发已选球员）
    val startingIds = setup.starting11.mapNotNull { it.playerId }.toSet()
    val substitutes = availablePlayers.filter { it.playerId !in startingIds }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 1. 阵型区 =====
        SectionCard(title = "阵型（${setup.formation.name}）") {
            FormationSelector(
                current = setup.formation,
                onSelect = onChangeFormation
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 2D 球场
            PitchCanvas(
                formation = formationDef,
                starting11 = setup.starting11,
                playerNames = playerNames,
                onPlayerDragged = onPlayerDragged,
                onSlotClicked = { slotId -> selectedSlotId = slotId }
            )
            if (selectedSlotId != null) {
                Text(
                    text = "已选中 ${selectedSlotId} 号槽位，点击替补球员放入首发",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 替补席
            SubstituteBench(
                substitutes = substitutes,
                onSelectSubstitute = { playerId ->
                    // 优先放入选中槽位，否则放入第一个空槽
                    val targetSlot = selectedSlotId
                        ?: setup.starting11.firstOrNull { it.playerId == null }?.slotId
                    if (targetSlot != null) {
                        onDragSubstituteToSlot(playerId, targetSlot)
                        selectedSlotId = null
                    }
                }
            )
        }

        // ===== 2. 战术风格区 =====
        SectionCard(title = "战术风格") {
            TacticalStyleSelector(
                current = setup.style,
                currentFormation = setup.formation,
                onSelect = onChangeStyle
            )
        }

        // ===== 3. 参数区 =====
        TacticalParameterSliders(
            parameters = setup.parameters,
            proficiency = proficiency,
            onUpdate = onUpdateParameters
        )

        // ===== 4. 角色区 =====
        RoleAssignmentPanel(
            roles = setup.playerRoles,
            availablePlayers = availablePlayers,
            onAssign = onAssignRole
        )

        // ===== 保存按钮 =====
        Button(
            onClick = onSaveTactics,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存战术设置")
        }
    }
}

/** 区块卡片（标题 + 内容） */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** 简易消息提示（替代 Snackbar，避免额外依赖） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnackbarMessage(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            onClick = onDismiss
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
