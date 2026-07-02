@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.greendynasty.football.transfer.negotiation.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.greendynasty.football.data.save.entity.SaveTransferOfferEntity
import com.greendynasty.football.transfer.negotiation.engine.CounterOffer
import com.greendynasty.football.transfer.negotiation.engine.PlayerDecision
import com.greendynasty.football.transfer.negotiation.engine.SellerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.model.OfferRoundEntity
import com.greendynasty.football.transfer.negotiation.model.OfferType
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import com.greendynasty.football.transfer.negotiation.viewmodel.ContractTermsFormState
import com.greendynasty.football.transfer.negotiation.viewmodel.NegotiationUiState
import com.greendynasty.football.transfer.negotiation.viewmodel.OfferFormState

/**
 * T11 报价谈判页入口 Composable（V0.1 `09_转会_合同_经纪人系统.md` §三-§六）。
 *
 * 根据 [NegotiationUiState] 分发到不同子页面：
 * - [Idle] / [Loading] / [Submitted] → [OfferSubmitScreen] 报价提交页
 * - [SellerReaction] → [NegotiationScreen] 卖方反应页
 * - [PersonalTerms] → [ContractTermsScreen] 合同条款编辑页
 * - [PlayerReaction] → 球员还价卡片（复用 [ContractTermsScreen]）
 * - [Completed] → [TransferCompletedScreen] 转会完成页
 * - [Failed] / [Error] → 失败/错误提示
 *
 * @param targetPlayerId 目标球员 ID（外部传入；0 表示尚未选择球员）
 * @param targetPlayerName 目标球员姓名
 * @param viewModel 谈判 ViewModel
 */
@Composable
fun NegotiationScreen(
    targetPlayerId: Int,
    targetPlayerName: String,
    modifier: Modifier = Modifier,
    viewModel: com.greendynasty.football.transfer.negotiation.viewmodel.NegotiationViewModel = viewModel(
        factory = com.greendynasty.football.transfer.negotiation.viewmodel.NegotiationViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val formState by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val termsForm by viewModel.termsForm.collectAsState()
    val message by viewModel.message.collectAsState()

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
                NegotiationUiState.Idle,
                NegotiationUiState.Loading,
                is NegotiationUiState.Submitted -> {
                    OfferSubmitScreen(
                        formState = formState,
                        isLoading = uiState is NegotiationUiState.Loading,
                        onOfferTypeChange = viewModel::updateOfferType,
                        onFeeChange = viewModel::updateTransferFee,
                        onWageChange = viewModel::updateWage,
                        onYearsChange = viewModel::updateContractYears,
                        onSigningBonusChange = viewModel::updateSigningBonus,
                        onAgentCommissionChange = viewModel::updateAgentCommission,
                        onRolePromiseChange = viewModel::updateRolePromise,
                        onSubmit = viewModel::submitOffer
                    )
                }

                is NegotiationUiState.SellerReaction -> NegotiationScreen(
                    state = state,
                    formState = formState,
                    onAcceptCounter = viewModel::acceptCounter,
                    onModifyAndReoffer = viewModel::modifyAndReoffer,
                    onWithdraw = viewModel::withdrawOffer,
                    onFeeChange = viewModel::updateTransferFee,
                    onWageChange = viewModel::updateWage,
                    onSigningBonusChange = viewModel::updateSigningBonus,
                    onAgentCommissionChange = viewModel::updateAgentCommission,
                    onRolePromiseChange = viewModel::updateRolePromise
                )

                is NegotiationUiState.PersonalTerms -> ContractTermsScreen(
                    offer = state.offer,
                    valuation = state.valuation,
                    termsForm = termsForm,
                    onSubmit = viewModel::submitPersonalTerms,
                    onWithdraw = viewModel::withdrawOffer,
                    onWeeklyWageChange = viewModel::updateTermsWeeklyWage,
                    onYearsChange = viewModel::updateTermsYears,
                    onSigningBonusChange = viewModel::updateTermsSigningBonus,
                    onAgentCommissionChange = viewModel::updateTermsAgentCommission,
                    onAppearanceBonusChange = viewModel::updateTermsAppearanceBonus,
                    onGoalBonusChange = viewModel::updateTermsGoalBonus,
                    onAssistBonusChange = viewModel::updateTermsAssistBonus,
                    onCleanSheetBonusChange = viewModel::updateTermsCleanSheetBonus,
                    onLoyaltyBonusChange = viewModel::updateTermsLoyaltyBonus,
                    onReleaseClauseToggle = viewModel::updateReleaseClauseEnabled,
                    onReleaseClauseChange = viewModel::updateReleaseClause,
                    onRelegationReleaseToggle = viewModel::updateRelegationReleaseEnabled,
                    onRelegationReleaseChange = viewModel::updateRelegationRelease,
                    onUclRaiseToggle = viewModel::updateUclRaiseEnabled,
                    onUclRaisePercentChange = viewModel::updateUclRaisePercent,
                    onAnnualRaiseToggle = viewModel::updateAnnualRaiseEnabled,
                    onAnnualRaisePercentChange = viewModel::updateAnnualRaisePercent,
                    onExtensionOptionToggle = viewModel::updateExtensionOption,
                    onBuybackToggle = viewModel::updateBuybackEnabled,
                    onBuybackClauseChange = viewModel::updateBuybackClause,
                    onSellOnToggle = viewModel::updateSellOnEnabled,
                    onSellOnPercentChange = viewModel::updateSellOnPercent,
                    onRolePromiseChange = viewModel::updateTermsRolePromise
                )

                is NegotiationUiState.PlayerReaction -> PlayerReactionScreen(
                    state = state,
                    termsForm = termsForm,
                    onAccept = viewModel::acceptPlayerCounter,
                    onModify = viewModel::modifyTermsAndReoffer
                )

                is NegotiationUiState.Completed -> TransferCompletedScreen(
                    state = state,
                    onReset = viewModel::resetToIdle
                )

                is NegotiationUiState.Failed -> TransferFailedScreen(
                    state = state,
                    onReset = viewModel::resetToIdle
                )

                is NegotiationUiState.Error -> ErrorScreen(
                    message = state.message,
                    onReset = viewModel::resetToIdle
                )
            }
        }
    }
}

// =================================================================
// T11.2 报价提交页
// =================================================================

/**
 * 报价提交页（V0.1 09 §三 第 3 步）。
 *
 * 组件树：
 * ```
 * PlayerSummaryCard（球员摘要）
 * OfferTypeSelector（5 种报价类型）
 * TransferFeeInput / WageInput / ContractYearsSelector
 * SigningBonusInput / AgentCommissionInput / RolePromiseSelector
 * BudgetPreviewBar（预算预览）
 * SubmitOfferButton
 * ```
 */
@Composable
private fun OfferSubmitScreen(
    formState: OfferFormState,
    isLoading: Boolean,
    onOfferTypeChange: (OfferType) -> Unit,
    onFeeChange: (String) -> Unit,
    onWageChange: (String) -> Unit,
    onYearsChange: (Int) -> Unit,
    onSigningBonusChange: (String) -> Unit,
    onAgentCommissionChange: (String) -> Unit,
    onRolePromiseChange: (RolePromise) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 球员摘要卡片
        PlayerSummaryCard(
            playerId = formState.playerId,
            playerName = formState.playerName,
            suggestedFee = formState.suggestedFee,
            suggestedWage = formState.suggestedWage,
            psychologicalPrice = formState.psychologicalPrice,
            expectedWage = formState.expectedWage
        )

        // 2. 报价类型选择器
        SectionCard(title = "报价类型") {
            OfferTypeSelector(
                selected = formState.offerType,
                onSelect = onOfferTypeChange
            )
        }

        // 3. 报价金额输入
        SectionCard(title = "报价金额") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyInput(
                    label = "转会费",
                    value = formState.transferFee,
                    suggestion = formState.psychologicalPrice,
                    onValueChange = onFeeChange
                )
                MoneyInput(
                    label = "周薪",
                    value = formState.wageOffer,
                    suggestion = formState.expectedWage,
                    onValueChange = onWageChange
                )
                ContractYearsSelector(
                    selected = formState.contractYears,
                    onSelect = onYearsChange
                )
            }
        }

        // 4. 附加费用
        SectionCard(title = "附加费用") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyInput(
                    label = "签字费",
                    value = formState.signingBonus,
                    suggestion = 0,
                    onValueChange = onSigningBonusChange
                )
                MoneyInput(
                    label = "经纪人佣金",
                    value = formState.agentCommission,
                    suggestion = 0,
                    onValueChange = onAgentCommissionChange
                )
            }
        }

        // 5. 角色承诺
        SectionCard(title = "角色承诺") {
            RolePromiseSelector(
                selected = formState.rolePromise,
                onSelect = onRolePromiseChange
            )
        }

        // 6. 预算预览
        BudgetPreviewBar(
            fee = formState.feeInt(),
            signingBonus = formState.signingBonusInt(),
            agentCommission = formState.agentCommissionInt()
        )

        // 7. 提交按钮
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && formState.playerId > 0 && formState.feeInt() > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("评估中…")
            } else {
                Text("提交报价")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =================================================================
// 谈判过程页（卖方反应）
// =================================================================

/**
 * 谈判过程页（V0.1 09 §四）。
 *
 * 组件树：
 * ```
 * NegotiationStageIndicator（阶段指示）
 * RoundHistoryList（历史轮次）
 * SellerReactionCard（卖方反应）
 * CounterOfferCard（还价卡片：接受/拒绝/修改重报）
 * PatienceBar（耐心条）
 * WithdrawButton（撤回按钮）
 * ```
 */
@Composable
private fun NegotiationScreen(
    state: NegotiationUiState.SellerReaction,
    formState: OfferFormState,
    onAcceptCounter: () -> Unit,
    onModifyAndReoffer: () -> Unit,
    onWithdraw: () -> Unit,
    onFeeChange: (String) -> Unit,
    onWageChange: (String) -> Unit,
    onSigningBonusChange: (String) -> Unit,
    onAgentCommissionChange: (String) -> Unit,
    onRolePromiseChange: (RolePromise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 阶段指示
        NegotiationStageIndicator(
            currentRound = state.session.currentRound,
            maxRounds = state.session.maxRounds
        )

        // 2. 耐心条
        PatienceBar(
            buyerPatience = state.session.buyerPatience,
            sellerPatience = state.session.sellerPatience,
            playerPatience = state.session.playerPatience
        )

        // 3. 历史轮次列表
        RoundHistoryList(rounds = state.rounds)

        // 4. 卖方反应卡片
        SellerReactionCard(decision = state.decision)

        // 5. 还价操作卡片（仅当卖方还价时显示）
        when (state.decision) {
            is SellerDecision.Counter -> CounterOfferCard(
                counter = state.decision.counter,
                formState = formState,
                onAccept = onAcceptCounter,
                onModifyAndReoffer = onModifyAndReoffer,
                onFeeChange = onFeeChange,
                onWageChange = onWageChange,
                onSigningBonusChange = onSigningBonusChange,
                onAgentCommissionChange = onAgentCommissionChange,
                onRolePromiseChange = onRolePromiseChange
            )
            else -> {}
        }

        // 6. 撤回按钮
        OutlinedButton(
            onClick = onWithdraw,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("撤回报价")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =================================================================
// 合同条款编辑页
// =================================================================

/**
 * 合同条款编辑页（V0.1 09 §六）。
 *
 * 组件树：
 * ```
 * BasicTermsSection（9 项基础条款）
 * SpecialTermsSection（7 项特殊条款，可折叠）
 * RolePromiseSection（5 档角色承诺单选）
 * SubmitButton
 * ```
 */
@Composable
private fun ContractTermsScreen(
    offer: SaveTransferOfferEntity,
    valuation: PlayerValuation,
    termsForm: ContractTermsFormState,
    onSubmit: () -> Unit,
    onWithdraw: () -> Unit,
    onWeeklyWageChange: (String) -> Unit,
    onYearsChange: (Int) -> Unit,
    onSigningBonusChange: (String) -> Unit,
    onAgentCommissionChange: (String) -> Unit,
    onAppearanceBonusChange: (String) -> Unit,
    onGoalBonusChange: (String) -> Unit,
    onAssistBonusChange: (String) -> Unit,
    onCleanSheetBonusChange: (String) -> Unit,
    onLoyaltyBonusChange: (String) -> Unit,
    onReleaseClauseToggle: (Boolean) -> Unit,
    onReleaseClauseChange: (String) -> Unit,
    onRelegationReleaseToggle: (Boolean) -> Unit,
    onRelegationReleaseChange: (String) -> Unit,
    onUclRaiseToggle: (Boolean) -> Unit,
    onUclRaisePercentChange: (String) -> Unit,
    onAnnualRaiseToggle: (Boolean) -> Unit,
    onAnnualRaisePercentChange: (String) -> Unit,
    onExtensionOptionToggle: (Boolean) -> Unit,
    onBuybackToggle: (Boolean) -> Unit,
    onBuybackClauseChange: (String) -> Unit,
    onSellOnToggle: (Boolean) -> Unit,
    onSellOnPercentChange: (String) -> Unit,
    onRolePromiseChange: (RolePromise) -> Unit
) {
    var specialExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 提示：卖方已接受，进入个人条款谈判
        Surface(
            color = Color(0xFF2E7D32),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "✅ 卖方已接受报价，请提交个人条款（期望周薪：${formatMoney(valuation.expectedWage)}）",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(12.dp)
            )
        }

        // 1. 基础条款 9 项
        SectionCard(title = "基础条款（9 项）") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyInput(
                    label = "周薪",
                    value = termsForm.weeklyWage,
                    suggestion = valuation.expectedWage,
                    onValueChange = onWeeklyWageChange
                )
                ContractYearsSelector(
                    selected = termsForm.contractYears,
                    onSelect = onYearsChange
                )
                MoneyInput(
                    label = "签字费",
                    value = termsForm.signingBonus,
                    suggestion = 0,
                    onValueChange = onSigningBonusChange
                )
                MoneyInput(
                    label = "经纪人佣金",
                    value = termsForm.agentCommission,
                    suggestion = 0,
                    onValueChange = onAgentCommissionChange
                )
                MoneyInput(
                    label = "出场奖金",
                    value = termsForm.appearanceBonus,
                    suggestion = 0,
                    onValueChange = onAppearanceBonusChange
                )
                MoneyInput(
                    label = "进球奖金",
                    value = termsForm.goalBonus,
                    suggestion = 0,
                    onValueChange = onGoalBonusChange
                )
                MoneyInput(
                    label = "助攻奖金",
                    value = termsForm.assistBonus,
                    suggestion = 0,
                    onValueChange = onAssistBonusChange
                )
                MoneyInput(
                    label = "零封奖金",
                    value = termsForm.cleanSheetBonus,
                    suggestion = 0,
                    onValueChange = onCleanSheetBonusChange
                )
                MoneyInput(
                    label = "忠诚奖金",
                    value = termsForm.loyaltyBonus,
                    suggestion = 0,
                    onValueChange = onLoyaltyBonusChange
                )
            }
        }

        // 2. 特殊条款 7 项（可折叠）
        SectionCard(
            title = "特殊条款（7 项）",
            action = {
                OutlinedButton(onClick = { specialExpanded = !specialExpanded }) {
                    Text(if (specialExpanded) "收起" else "展开")
                }
            }
        ) {
            AnimatedVisibility(visible = specialExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 解约金
                    SpecialTermRow(
                        label = "解约金",
                        enabled = termsForm.releaseClauseEnabled,
                        onToggle = onReleaseClauseToggle,
                        value = termsForm.releaseClause,
                        onValueChange = onReleaseClauseChange,
                        valueLabel = "金额"
                    )
                    Divider()
                    // 降级解约
                    SpecialTermRow(
                        label = "降级解约金",
                        enabled = termsForm.relegationReleaseEnabled,
                        onToggle = onRelegationReleaseToggle,
                        value = termsForm.relegationRelease,
                        onValueChange = onRelegationReleaseChange,
                        valueLabel = "金额"
                    )
                    Divider()
                    // 欧冠涨薪
                    SpecialTermRow(
                        label = "欧冠涨薪",
                        enabled = termsForm.uclRaiseEnabled,
                        onToggle = onUclRaiseToggle,
                        value = termsForm.uclRaisePercent,
                        onValueChange = onUclRaisePercentChange,
                        valueLabel = "百分比"
                    )
                    Divider()
                    // 年度涨薪
                    SpecialTermRow(
                        label = "年度涨薪",
                        enabled = termsForm.annualRaiseEnabled,
                        onToggle = onAnnualRaiseToggle,
                        value = termsForm.annualRaisePercent,
                        onValueChange = onAnnualRaisePercentChange,
                        valueLabel = "百分比"
                    )
                    Divider()
                    // 续约选项
                    ToggleRow(
                        label = "续约选项",
                        description = "合同到期自动续约 1 年",
                        checked = termsForm.extensionOption,
                        onCheckedChange = onExtensionOptionToggle
                    )
                    Divider()
                    // 回购条款
                    SpecialTermRow(
                        label = "回购条款",
                        enabled = termsForm.buybackEnabled,
                        onToggle = onBuybackToggle,
                        value = termsForm.buybackClause,
                        onValueChange = onBuybackClauseChange,
                        valueLabel = "金额"
                    )
                    Divider()
                    // 二次转会分成
                    SpecialTermRow(
                        label = "二次转会分成",
                        enabled = termsForm.sellOnEnabled,
                        onToggle = onSellOnToggle,
                        value = termsForm.sellOnPercent,
                        onValueChange = onSellOnPercentChange,
                        valueLabel = "百分比"
                    )
                }
            }
            if (!specialExpanded) {
                Text(
                    text = "点击右上角『展开』配置特殊条款（解约金/降级解约/涨薪/续约/回购/分成）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 3. 角色承诺
        SectionCard(title = "角色承诺（5 档）") {
            RolePromiseSelector(
                selected = termsForm.rolePromise,
                onSelect = onRolePromiseChange
            )
        }

        // 4. 提交按钮
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("提交合同条款")
        }

        // 5. 撤回
        OutlinedButton(
            onClick = onWithdraw,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("撤回交易")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =================================================================
// 球员还价反应页
// =================================================================

/**
 * 球员还价反应页（V0.1 09 §六）。
 *
 * 显示球员还价的条款，玩家可选择接受 / 修改后重提。
 */
@Composable
private fun PlayerReactionScreen(
    state: NegotiationUiState.PlayerReaction,
    termsForm: ContractTermsFormState,
    onAccept: () -> Unit,
    onModify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val decision = state.decision as? PlayerDecision.Counter ?: return@Column

        // 球员反应卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF3E0)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🤝 球员还价",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = decision.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "加盟意愿：${(decision.willingness * 100).toInt()}%   条款评分：${(decision.termsScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 球员还价条款汇总
        SectionCard(title = "球员还价条款") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TermRow(label = "周薪", value = formatMoney(termsForm.weeklyWage.toIntOrNull() ?: 0))
                TermRow(label = "合同年限", value = "${termsForm.contractYears} 年")
                TermRow(label = "签字费", value = formatMoney(termsForm.signingBonus.toIntOrNull() ?: 0))
                TermRow(label = "经纪人佣金", value = formatMoney(termsForm.agentCommission.toIntOrNull() ?: 0))
                TermRow(
                    label = "角色承诺",
                    value = runCatching { RolePromise.valueOf(termsForm.rolePromise.name).label }.getOrDefault(termsForm.rolePromise.name)
                )
            }
        }

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            ) {
                Text("接受还价")
            }
            OutlinedButton(
                onClick = onModify,
                modifier = Modifier.weight(1f)
            ) {
                Text("修改条款")
            }
        }
    }
}

// =================================================================
// 完成 / 失败 / 错误页
// =================================================================

/** 转会完成页 */
@Composable
private fun TransferCompletedScreen(
    state: NegotiationUiState.Completed,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉 转会完成",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("完成")
        }
    }
}

/** 谈判失败页 */
@Composable
private fun TransferFailedScreen(
    state: NegotiationUiState.Failed,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "❌ 谈判破裂",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.reason,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

/** 错误页 */
@Composable
private fun ErrorScreen(
    message: String,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠️ 发生错误",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("重试")
        }
    }
}

// =================================================================
// 通用组件
// =================================================================

/** 分区卡片（带标题与可选操作） */
@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

/** 球员摘要卡片 */
@Composable
private fun PlayerSummaryCard(
    playerId: Int,
    playerName: String,
    suggestedFee: Int,
    suggestedWage: Int,
    psychologicalPrice: Int,
    expectedWage: Int
) {
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
                        text = playerName.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = playerName.ifEmpty { "未选择球员" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID：$playerId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (psychologicalPrice > 0) {
                TermRow(label = "心理价位（建议）", value = formatMoney(psychologicalPrice))
                TermRow(label = "期望周薪（建议）", value = formatMoney(expectedWage))
            } else {
                Text(
                    text = "提示：选择球员后将自动加载建议报价",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 报价类型选择器 */
@Composable
private fun OfferTypeSelector(
    selected: OfferType,
    onSelect: (OfferType) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OfferType.values().forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.label) }
            )
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

/** 预算预览条 */
@Composable
private fun BudgetPreviewBar(
    fee: Int,
    signingBonus: Int,
    agentCommission: Int
) {
    val total = fee + signingBonus + agentCommission
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "本次支出预算",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatMoney(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "转会费 + 签字费 + 佣金",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 谈判阶段指示 */
@Composable
private fun NegotiationStageIndicator(
    currentRound: Int,
    maxRounds: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🤝 第 $currentRound / $maxRounds 轮谈判",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (maxRounds > 0) {
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = (currentRound.toFloat() / maxRounds).coerceIn(0f, 1f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/** 耐心条（三方） */
@Composable
private fun PatienceBar(
    buyerPatience: Int,
    sellerPatience: Int,
    playerPatience: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "谈判耐心",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            PatienceRow(label = "买方（你）", value = buyerPatience, color = Color(0xFF2196F3))
            PatienceRow(label = "卖方", value = sellerPatience, color = Color(0xFFFF9800))
            PatienceRow(label = "球员", value = playerPatience, color = Color(0xFF4CAF50))
        }
    }
}

/** 单条耐心行 */
@Composable
private fun PatienceRow(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(80.dp)
        )
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = (value.toFloat() / 100f).coerceIn(0f, 1f),
            color = color,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

/** 历史轮次列表（普通 Column + forEach，避免与外层 verticalScroll 冲突） */
@Composable
private fun RoundHistoryList(rounds: List<OfferRoundEntity>) {
    if (rounds.isEmpty()) return
    SectionCard(title = "谈判历史（${rounds.size} 轮）") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rounds.forEach { round ->
                RoundHistoryItem(round)
            }
        }
    }
}

/** 单轮历史项 */
@Composable
private fun RoundHistoryItem(round: OfferRoundEntity) {
    Surface(
        color = when (round.proposer) {
            "BUYER" -> Color(0xFFE3F2FD)
            "SELLER" -> Color(0xFFFFF3E0)
            else -> Color(0xFFE8F5E9)
        },
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "第 ${round.roundNumber} 轮 · ${proposerLabel(round.proposer)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = round.reaction.ifBlank { "待回应" },
                    style = MaterialTheme.typography.labelSmall,
                    color = reactionColor(round.reaction)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "转会费 ${formatMoney(round.fee)}  ·  周薪 ${formatMoney(round.wage)}  ·  ${round.contractYears} 年",
                style = MaterialTheme.typography.labelSmall
            )
            if (round.reactionMessage.isNotBlank()) {
                Text(
                    text = round.reactionMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 卖方反应卡片 */
@Composable
private fun SellerReactionCard(decision: SellerDecision) {
    val (bgColor, icon, title) = when (decision) {
        is SellerDecision.AcceptDirectly -> Triple(Color(0xFFE8F5E9), "✅", "自由签约，无卖方")
        is SellerDecision.Accept -> Triple(Color(0xFFE8F5E9), "✅", "卖方接受报价")
        is SellerDecision.Reject -> Triple(Color(0xFFFFEBEE), "❌", "卖方拒绝")
        is SellerDecision.Counter -> Triple(Color(0xFFFFF3E0), "🤝", "卖方还价")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            when (decision) {
                is SellerDecision.Accept -> Text(
                    text = decision.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                is SellerDecision.AcceptDirectly -> Text(
                    text = "无主球员，无需卖方评估，直接进入个人条款谈判。",
                    style = MaterialTheme.typography.bodyMedium
                )
                is SellerDecision.Reject -> Text(
                    text = decision.reason,
                    style = MaterialTheme.typography.bodyMedium
                )
                is SellerDecision.Counter -> Text(
                    text = decision.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** 还价卡片（接受 / 修改重报） */
@Composable
private fun CounterOfferCard(
    counter: CounterOffer,
    formState: OfferFormState,
    onAccept: () -> Unit,
    onModifyAndReoffer: () -> Unit,
    onFeeChange: (String) -> Unit,
    onWageChange: (String) -> Unit,
    onSigningBonusChange: (String) -> Unit,
    onAgentCommissionChange: (String) -> Unit,
    onRolePromiseChange: (RolePromise) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "卖方还价条款",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            TermRow(label = "转会费", value = formatMoney(counter.fee))
            TermRow(label = "周薪", value = formatMoney(counter.wage))
            TermRow(label = "签字费", value = formatMoney(counter.signingBonus))
            TermRow(label = "佣金", value = formatMoney(counter.agentCommission))
            if (!counter.message.isNullOrBlank()) {
                Text(
                    text = counter.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Divider()

            Text(
                text = "修改你的报价（重新提交后卖方将再次评估）",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            MoneyInput(
                label = "新转会费",
                value = formState.transferFee,
                suggestion = counter.fee,
                onValueChange = onFeeChange
            )
            MoneyInput(
                label = "新周薪",
                value = formState.wageOffer,
                suggestion = counter.wage,
                onValueChange = onWageChange
            )
            MoneyInput(
                label = "新签字费",
                value = formState.signingBonus,
                suggestion = counter.signingBonus,
                onValueChange = onSigningBonusChange
            )
            MoneyInput(
                label = "新佣金",
                value = formState.agentCommission,
                suggestion = counter.agentCommission,
                onValueChange = onAgentCommissionChange
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("接受还价")
                }
                Button(
                    onClick = onModifyAndReoffer,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("修改并重报")
                }
            }
        }
    }
}

/** 特殊条款行（开关 + 数值输入） */
@Composable
private fun SpecialTermRow(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    valueLabel: String
) {
    Column {
        ToggleRow(
            label = label,
            description = null,
            checked = enabled,
            onCheckedChange = onToggle
        )
        if (enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(valueLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 开关行 */
@Composable
private fun ToggleRow(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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

/** 提议方标签 */
private fun proposerLabel(proposer: String): String = when (proposer) {
    "BUYER" -> "买方"
    "SELLER" -> "卖方"
    "PLAYER" -> "球员"
    else -> proposer
}

/** 反应颜色（@Composable 以便读取 MaterialTheme 默认色） */
@Composable
private fun reactionColor(reaction: String): Color = when (reaction) {
    "ACCEPT" -> Color(0xFF2E7D32)
    "REJECT" -> Color(0xFFC62828)
    "COUNTER" -> Color(0xFFEF6C00)
    else -> MaterialTheme.colorScheme.outline
}
