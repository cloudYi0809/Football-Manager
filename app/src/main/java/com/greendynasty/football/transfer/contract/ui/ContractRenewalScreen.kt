@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.transfer.contract.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.transfer.contract.model.ReminderLevel
import com.greendynasty.football.transfer.contract.viewmodel.ContractRenewalUiState
import com.greendynasty.football.transfer.contract.viewmodel.ReminderItem
import com.greendynasty.football.transfer.contract.viewmodel.RenewalFormState
import com.greendynasty.football.transfer.negotiation.model.RolePromise

/**
 * T12 合同续约页入口 Composable（V0.1 `09_转会_合同_经纪人系统.md` §六）。
 *
 * 根据 [ContractRenewalUiState] 分发到不同子页面：
 * - [Idle] / [Loading] → 到期预警列表（默认首页）
 * - [Initiated] → 续约条款编辑页
 * - [PlayerCountered] → 球员还价卡片（接受/修改/撤回）
 * - [Completed] → 续约完成页
 * - [Failed] / [Error] → 失败/错误提示
 *
 * @param targetPlayerId 目标球员 ID（外部传入；0 表示尚未选择球员）
 * @param targetPlayerName 目标球员姓名
 * @param viewModel 续约 ViewModel
 */
@Composable
fun ContractRenewalScreen(
    targetPlayerId: Int,
    targetPlayerName: String,
    modifier: Modifier = Modifier,
    viewModel: com.greendynasty.football.transfer.contract.viewmodel.ContractRenewalViewModel = viewModel(
        factory = com.greendynasty.football.transfer.contract.viewmodel.ContractRenewalViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val formState by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 首次进入：根据外部传入的球员初始化表单
    LaunchedEffect(targetPlayerId) {
        if (targetPlayerId > 0 && formState.playerId != targetPlayerId) {
            viewModel.initFormForPlayer(targetPlayerId, targetPlayerName)
        }
    }

    // 操作结果消息
    LaunchedEffect(message) {
        message?.let {
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
                ContractRenewalUiState.Idle,
                ContractRenewalUiState.Loading -> {
                    if (targetPlayerId > 0) {
                        // 自动发起续约
                        LaunchedEffect(targetPlayerId) {
                            viewModel.initiateRenewal(targetPlayerId, targetPlayerName)
                        }
                    }
                    ReminderListScreen(
                        reminders = reminders,
                        isLoading = uiState is ContractRenewalUiState.Loading,
                        onInitiateRenewal = { item ->
                            viewModel.initiateRenewal(item.reminder.playerId, item.playerName)
                        },
                        onMarkHandled = { item ->
                            viewModel.markReminderHandled(item.reminder.reminderId)
                        }
                    )
                }

                is ContractRenewalUiState.Initiated -> RenewalFormScreen(
                    formState = formState,
                    willingness = state.willingness,
                    monthsRemaining = state.monthsRemaining,
                    onWeeklyWageChange = viewModel::updateWeeklyWage,
                    onYearsChange = viewModel::updateContractYears,
                    onSigningBonusChange = viewModel::updateSigningBonus,
                    onRolePromiseChange = viewModel::updateRolePromise,
                    onReleaseClauseToggle = viewModel::updateReleaseClauseEnabled,
                    onReleaseClauseChange = viewModel::updateReleaseClause,
                    onPerformanceRaiseToggle = viewModel::updatePerformanceRaiseEnabled,
                    onVeteranClauseToggle = viewModel::updateVeteranClauseEnabled,
                    onAcademyProtectionToggle = viewModel::updateAcademyProtectionEnabled,
                    onSubmit = viewModel::submitRenewalOffer,
                    onWithdraw = viewModel::withdrawRenewal
                )

                is ContractRenewalUiState.PlayerCountered -> PlayerCounterScreen(
                    state = state,
                    onAccept = viewModel::acceptCounter,
                    onWithdraw = viewModel::withdrawRenewal
                )

                is ContractRenewalUiState.Completed -> RenewalCompletedScreen(
                    state = state,
                    onReset = viewModel::resetToIdle
                )

                is ContractRenewalUiState.Failed -> RenewalFailedScreen(
                    state = state,
                    onReset = viewModel::resetToIdle
                )

                is ContractRenewalUiState.Error -> ErrorScreen(
                    message = state.message,
                    onReset = viewModel::resetToIdle
                )
            }
        }
    }
}

// =================================================================
// 到期预警列表页
// =================================================================

/**
 * 合同到期预警列表页（V0.1 09 §六）。
 *
 * 展示所有未处理的合同到期提醒，按剩余月数升序排列。
 * 每条提醒卡片含：球员姓名 / 位置 / 年龄 / 当前工资 / 剩余月数 / 建议动作。
 */
@Composable
private fun ReminderListScreen(
    reminders: List<ReminderItem>,
    isLoading: Boolean,
    onInitiateRenewal: (ReminderItem) -> Unit,
    onMarkHandled: (ReminderItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "合同到期预警",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "共 ${reminders.size} 条待处理提醒",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (reminders.isEmpty() && !isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "暂无到期预警",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "所有球员合同剩余均超过 12 个月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reminders, key = { it.reminder.reminderId }) { item ->
                ReminderCard(
                    item = item,
                    onInitiateRenewal = { onInitiateRenewal(item) },
                    onMarkHandled = { onMarkHandled(item) }
                )
            }
        }
    }
}

/** 单条提醒卡片 */
@Composable
private fun ReminderCard(
    item: ReminderItem,
    onInitiateRenewal: () -> Unit,
    onMarkHandled: () -> Unit
) {
    val reminder = item.reminder
    val level = runCatching { ReminderLevel.valueOf(reminder.level) }.getOrNull()
    val levelColor = when (level) {
        ReminderLevel.FINAL -> MaterialTheme.colorScheme.error
        ReminderLevel.URGENT -> Color(0xFFFF9800) // 橙色
        ReminderLevel.EARLY_WARNING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val actionLabel = when (reminder.recommendedAction) {
        "RENEW_IMMEDIATELY" -> "立即续约"
        "RENEW_OR_RELEASE" -> "续约或释放"
        "RELEASE_OR_SHORT_TERM" -> "释放或短约"
        "EVALUATE" -> "评估"
        else -> "续约"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 顶部：球员信息 + 提醒级别
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.playerName.firstOrNull()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.playerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.playerPosition ?: "—"} · ${item.playerAge} 岁 · ${item.squadRole ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Surface(
                    color = levelColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = level?.label ?: reminder.level,
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 中部：合同信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "剩余 ${reminder.monthsRemaining} 个月",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (reminder.monthsRemaining <= 1) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "当前周薪：${formatMoney(item.currentWage)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "建议：$actionLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (reminder.playerDemandTriggered) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠ 球员已主动要求续约",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部：操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInitiateRenewal,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("续约")
                }
                OutlinedButton(
                    onClick = onMarkHandled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("忽略")
                }
            }
        }
    }
}

// =================================================================
// 续约条款编辑页
// =================================================================

/**
 * 续约条款编辑页（V0.1 09 §六）。
 *
 * 组件树：
 * - PlayerSummaryCard：球员摘要 + 工资分解
 * - WageInput：周薪输入（含建议值）
 * - ContractYearsSelector：合同年限
 * - SigningBonusInput：签字费
 * - RolePromiseSelector：角色承诺
 * - ReleaseClauseInput：违约金
 * - RenewalSpecialTermsSelector：续约特有 3 项条款
 * - SubmitButton / WithdrawButton
 */
@Composable
private fun RenewalFormScreen(
    formState: RenewalFormState,
    willingness: Double,
    monthsRemaining: Int,
    onWeeklyWageChange: (String) -> Unit,
    onYearsChange: (Int) -> Unit,
    onSigningBonusChange: (String) -> Unit,
    onRolePromiseChange: (RolePromise) -> Unit,
    onReleaseClauseToggle: (Boolean) -> Unit,
    onReleaseClauseChange: (String) -> Unit,
    onPerformanceRaiseToggle: (Boolean) -> Unit,
    onVeteranClauseToggle: (Boolean) -> Unit,
    onAcademyProtectionToggle: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onWithdraw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 球员摘要 + 续约意愿
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formState.playerName.firstOrNull()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = formState.playerName.ifEmpty { "未选择球员" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "合同剩余 $monthsRemaining 个月 · 当前周薪 ${formatMoney(formState.currentWage)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 续约意愿进度条
                Text(
                    text = "续约意愿：${"%.0f".format(willingness * 100)}%",
                    style = MaterialTheme.typography.labelMedium
                )
                LinearProgressIndicator(
                    progress = willingness.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = when {
                        willingness >= 0.6 -> Color(0xFF4CAF50)
                        willingness >= 0.3 -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                // 工资分解（5 因子）
                formState.wageBreakdown?.let { bd ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "工资分解（5 因子公式）",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    TermRow(label = "期望周薪", value = formatMoney(bd.expectedWage))
                    TermRow(label = "基础工资（CA=${bd.squadRole}）", value = formatMoney(bd.wageBase.toInt()))
                    TermRow(label = "俱乐部声望系数", value = "%.2f".format(bd.clubReputationFactor))
                    TermRow(label = "联赛系数", value = "%.2f".format(bd.leagueFactor))
                    TermRow(label = "经济指数", value = "%.2f".format(bd.economyFactor))
                    TermRow(label = "队内角色系数", value = "%.2f".format(bd.squadRoleFactor))
                }
                if (formState.demandsWage > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "球员要求周薪：${formatMoney(formState.demandsWage)}（最长 ${formState.demandsMaxYears} 年）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 2. 工资 / 年限 / 签字费
        SectionCard(title = "续约条款") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyInput(
                    label = "周薪",
                    value = formState.weeklyWage,
                    suggestion = formState.demandsWage,
                    onValueChange = onWeeklyWageChange
                )
                ContractYearsSelector(
                    selected = formState.contractYears,
                    onSelect = onYearsChange
                )
                MoneyInput(
                    label = "签字费",
                    value = formState.signingBonus,
                    suggestion = 0,
                    onValueChange = onSigningBonusChange
                )
            }
        }

        // 3. 角色承诺
        SectionCard(title = "角色承诺") {
            RolePromiseSelector(
                selected = formState.rolePromise,
                onSelect = onRolePromiseChange
            )
        }

        // 4. 违约金
        SectionCard(title = "违约金（可选）") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = formState.releaseClauseEnabled,
                    onCheckedChange = onReleaseClauseToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (formState.releaseClauseEnabled) "已启用" else "未启用",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (formState.releaseClauseEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = formState.releaseClause,
                    onValueChange = onReleaseClauseChange,
                    label = { Text("违约金金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 5. 续约特有 3 项条款
        SectionCard(title = "续约特有条款（可选）") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecialTermRow(
                    label = "涨薪条款（表现达标自动涨薪）",
                    description = "对球员有利，提升接受概率 +20%",
                    checked = formState.performanceRaiseEnabled,
                    onCheckedChange = onPerformanceRaiseToggle
                )
                SpecialTermRow(
                    label = "退役条款（老将一年一签）",
                    description = "对俱乐部有利，降低接受概率 -10%",
                    checked = formState.veteranClauseEnabled,
                    onCheckedChange = onVeteranClauseToggle
                )
                SpecialTermRow(
                    label = "青训保护条款（特殊解约金）",
                    description = "对球员有利，提升接受概率 +15%",
                    checked = formState.academyProtectionEnabled,
                    onCheckedChange = onAcademyProtectionToggle
                )
            }
        }

        // 6. 提交 / 撤回
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSubmit,
                modifier = Modifier.weight(1f),
                enabled = formState.wageInt() > 0
            ) {
                Text("提交报价")
            }
            OutlinedButton(
                onClick = onWithdraw,
                modifier = Modifier.weight(1f)
            ) {
                Text("撤回")
            }
        }
    }
}

// =================================================================
// 球员还价页
// =================================================================

@Composable
private fun PlayerCounterScreen(
    state: ContractRenewalUiState.PlayerCountered,
    onAccept: () -> Unit,
    onWithdraw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "球员还价",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                TermRow(label = "还价周薪", value = formatMoney(state.counterWeeklyWage))
                TermRow(label = "还价年限", value = "${state.counterContractYears} 年")
                TermRow(label = "还价签字费", value = formatMoney(state.counterSigningBonus))
                TermRow(label = "还价佣金", value = formatMoney(state.counterAgentCommission))
                TermRow(label = "续约意愿", value = "${"%.0f".format(state.willingness * 100)}%")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("接受还价")
            }
            OutlinedButton(
                onClick = onWithdraw,
                modifier = Modifier.weight(1f)
            ) {
                Text("拒绝并撤回")
            }
        }
    }
}

// =================================================================
// 续约完成页
// =================================================================

@Composable
private fun RenewalCompletedScreen(
    state: ContractRenewalUiState.Completed,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✓ 续约成功",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                TermRow(label = "新周薪", value = formatMoney(state.newWage))
                TermRow(label = "新合同到期", value = state.newContractUntil.take(10))
                TermRow(label = "新角色", value = state.newSquadRole)
                TermRow(
                    label = "工资变化",
                    value = "${"%.1f".format(state.wageChangePercent)}%"
                )
            }
        }

        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}

// =================================================================
// 失败 / 错误页
// =================================================================

@Composable
private fun RenewalFailedScreen(
    state: ContractRenewalUiState.Failed,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "续约失败",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = state.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Button(onClick = onReset) {
            Text("返回")
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "错误",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Button(onClick = onReset) {
            Text("重试")
        }
    }
}

// =================================================================
// 通用组件
// =================================================================

/** 分区卡片（带标题） */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
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

/** 条款行（label : value） */
@Composable
private fun TermRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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

/** 金额输入框（带建议值提示） */
@Composable
private fun MoneyInput(
    label: String,
    value: String,
    suggestion: Int,
    onValueChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestion > 0) {
            Text(
                text = "建议值：${formatMoney(suggestion)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

/** 合同年限选择器（1-5 年） */
@Composable
private fun ContractYearsSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            text = "合同年限",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { year ->
                FilterChip(
                    selected = year == selected,
                    onClick = { onSelect(year) },
                    label = { Text("$year 年") }
                )
            }
        }
    }
}

/** 角色承诺选择器 */
@Composable
private fun RolePromiseSelector(
    selected: RolePromise,
    onSelect: (RolePromise) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RolePromise.values().forEach { role ->
            FilterChip(
                selected = role == selected,
                onClick = { onSelect(role) },
                label = { Text(role.label) }
            )
        }
    }
}

/** 续约特有条款行（带开关 + 描述） */
@Composable
private fun SpecialTermRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// =================================================================
// 工具函数
// =================================================================

/** 格式化金额：亿 / 万 / 原值 */
private fun formatMoney(amount: Int): String {
    return when {
        amount >= 100_000_000 -> String.format("%.2f 亿", amount / 100_000_000.0)
        amount >= 10_000 -> String.format("%.1f 万", amount / 10_000.0)
        else -> amount.toString()
    }
}
