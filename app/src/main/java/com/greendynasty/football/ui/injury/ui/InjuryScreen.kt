package com.greendynasty.football.ui.injury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.injury.model.RiskLevel
import com.greendynasty.football.injury.model.TreatmentType
import com.greendynasty.football.ui.injury.ui.state.InjuryDisplay
import com.greendynasty.football.ui.injury.ui.state.InjuryUiState
import com.greendynasty.football.ui.injury.ui.state.MedicalFacilityDisplay
import com.greendynasty.football.ui.injury.viewmodel.InjuryViewModel

/**
 * 医疗中心页（T08 伤病系统 UI 入口）
 *
 * 轻量级记录页设计：
 * - 顶部：医疗设施卡片（等级 / 恢复速度 / 升级按钮）
 * - 中部：活跃伤病列表（球员 / 类型 / 严重度 / 进度条 / 剩余天数 / 操作）
 * - 底部：全队风险评分 Top 5
 */
@Composable
fun InjuryScreen(
    viewModel: InjuryViewModel = viewModel(factory = InjuryViewModel.factory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作结果提示
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is InjuryUiState.Loading -> LoadingView(padding)
            is InjuryUiState.Locked -> EmptyView(padding, state.reason)
            is InjuryUiState.Empty -> EmptyView(padding, state.reason)
            is InjuryUiState.Error -> ErrorView(padding, state.message)
            is InjuryUiState.Normal -> InjuryContent(
                padding = padding,
                injuries = state.injuries,
                facility = state.facility,
                riskScores = state.riskScores,
                onForceReturn = viewModel::forceReturn,
                onSelectTreatment = viewModel::selectTreatment,
                onUpgrade = viewModel::upgradeMedicalFacility
            )
            is InjuryUiState.Warning -> InjuryContent(
                padding = padding,
                injuries = state.injuries,
                facility = state.facility,
                riskScores = state.riskScores,
                warningMessage = state.message,
                onForceReturn = viewModel::forceReturn,
                onSelectTreatment = viewModel::selectTreatment,
                onUpgrade = viewModel::upgradeMedicalFacility
            )
        }
    }
}

// ==================== 内容视图 ====================

@Composable
private fun InjuryContent(
    padding: PaddingValues,
    injuries: List<InjuryDisplay>,
    facility: MedicalFacilityDisplay?,
    riskScores: List<com.greendynasty.football.injury.model.InjuryRiskScore>,
    warningMessage: String? = null,
    onForceReturn: (Int) -> Unit,
    onSelectTreatment: (Int, TreatmentType) -> Unit,
    onUpgrade: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "医疗中心",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // 警告提示
        if (warningMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = warningMessage,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 医疗设施卡片
        if (facility != null) {
            item { FacilityCard(facility, onUpgrade) }
        }

        // 活跃伤病列表
        item {
            Text(
                text = "活跃伤病（${injuries.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (injuries.isEmpty()) {
            item {
                Text(
                    text = "暂无伤病球员",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(injuries, key = { it.injury.injuryId }) { injury ->
                InjuryCard(injury, onForceReturn, onSelectTreatment)
            }
        }

        // 风险评分
        if (riskScores.isNotEmpty()) {
            item {
                Text(
                    text = "伤病风险评分（Top ${riskScores.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            items(riskScores, key = { it.playerId }) { risk ->
                RiskScoreRow(risk)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ==================== 子组件 ====================

@Composable
private fun FacilityCard(
    facility: MedicalFacilityDisplay,
    onUpgrade: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "医疗设施等级",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Lv. ${facility.facility.medicalLevel}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "恢复速度 ${if (facility.speedMultiplierPercent >= 0) "+" else ""}${facility.speedMultiplierPercent}%" +
                    "  ·  复发降低 ${facility.recurrenceReductionPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (facility.canUpgrade) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onUpgrade(facility.facility.medicalLevel + 10) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("升级至 Lv. ${(facility.facility.medicalLevel + 10).coerceAtMost(100)}（¥${facility.upgradeCost}）")
                }
            } else if (facility.facility.upgradeCooldownDays > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "升级冷却中（剩余 ${facility.facility.upgradeCooldownDays} 天）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InjuryCard(
    display: InjuryDisplay,
    onForceReturn: (Int) -> Unit,
    onSelectTreatment: (Int, TreatmentType) -> Unit
) {
    val severityColor = when (display.injury.severity) {
        1 -> Color(0xFF4CAF50) // 绿
        2 -> Color(0xFFFF9800) // 橙
        3 -> Color(0xFFF44336) // 红
        else -> Color(0xFF9C27B0) // 紫（职业威胁）
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display.playerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = display.severityName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .background(severityColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${display.injuryTypeNameCn}  ·  ${display.statusDisplayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 恢复进度条
            LinearProgressIndicator(
                progress = display.progressPercent / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = severityColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "进度 ${display.progressPercent}%  ·  预计剩余 ${display.remainingDays} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 操作按钮（仅 active/recovering 状态显示）
            if (display.injury.status in listOf("active", "recovering")) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onSelectTreatment(display.injury.injuryId, TreatmentType.SURGERY) },
                        modifier = Modifier.weight(1f)
                    ) { Text("手术") }
                    TextButton(
                        onClick = { onForceReturn(display.injury.injuryId) },
                        modifier = Modifier.weight(1f)
                    ) { Text("强行复出") }
                }
            }
        }
    }
}

@Composable
private fun RiskScoreRow(risk: com.greendynasty.football.injury.model.InjuryRiskScore) {
    val riskColor = when (risk.riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFF9C27B0)
        RiskLevel.HIGH -> Color(0xFFF44336)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.NONE -> Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = risk.playerName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${risk.riskScore}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = riskColor,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = risk.riskLevel.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = riskColor
        )
    }
}

// ==================== 占位视图 ====================

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(padding: PaddingValues, reason: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorView(padding: PaddingValues, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}
