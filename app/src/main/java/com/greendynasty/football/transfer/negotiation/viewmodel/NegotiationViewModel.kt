package com.greendynasty.football.transfer.negotiation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.transfer.negotiation.engine.PlayerDecision
import com.greendynasty.football.transfer.negotiation.engine.SellerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.model.OfferType
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import com.greendynasty.football.transfer.negotiation.repository.NegotiationRepository
import com.greendynasty.football.transfer.negotiation.repository.OfferRequest
import com.greendynasty.football.transfer.negotiation.repository.OfferSubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T11 报价谈判页 ViewModel（V0.1 `09_转会_合同_经纪人系统.md` §三-§六）。
 *
 * 持有：
 * - [OfferFormState]：报价表单状态（5 种报价类型 / 转会费 / 工资 / 年限 / 签字费 / 佣金 / 角色）
 * - [NegotiationUiState]：当前谈判状态（提交 / 卖方反应 / 球员反应 / 完成 / 失败）
 * - [ContractTermsFormState]：合同条款表单（9 基础 + 7 特殊 + 5 角色）
 *
 * 核心交互：
 * 1. 玩家填写报价 → [submitOffer] → 卖方评估
 * 2. 卖方还价 → [acceptCounter] / [modifyAndReoffer] / [withdrawOffer]
 * 3. 卖方接受 → 进入个人条款谈判 → [negotiatePersonalTerms]
 * 4. 球员接受 → [completeTransfer]
 *
 * @param app Application，用于初始化 [DatabaseManager] / [SaveManager]
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class NegotiationViewModel(
    app: Application,
    saveId: Int,
    clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = NegotiationRepository(databaseManager, saveId, clubId)

    /** 报价表单状态 */
    private val _formState = MutableStateFlow(OfferFormState())
    val formState: StateFlow<OfferFormState> = _formState.asStateFlow()

    /** 谈判页面状态 */
    private val _uiState = MutableStateFlow<NegotiationUiState>(NegotiationUiState.Idle)
    val uiState: StateFlow<NegotiationUiState> = _uiState.asStateFlow()

    /** 合同条款表单 */
    private val _termsForm = MutableStateFlow(ContractTermsFormState())
    val termsForm: StateFlow<ContractTermsFormState> = _termsForm.asStateFlow()

    /** 操作结果提示 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ==================== 报价表单 ====================

    /** 初始化报价表单（针对某球员） */
    fun initFormForPlayer(playerId: Int, playerName: String) {
        viewModelScope.launch {
            // 读取球员估价作为建议值
            val player = databaseManager.historyPlayerDao().getPlayer(playerId) ?: return@launch
            val state = databaseManager.savePlayerStateDao().getByPlayer(repository.saveId, playerId)
            // 简化：暂用 player 当前身价作为建议值
            val suggestedFee = state?.marketValue ?: 1_000_000
            val suggestedWage = state?.wage ?: 5_000
            _formState.value = OfferFormState(
                playerId = playerId,
                playerName = playerName,
                transferFee = (suggestedFee * 0.9).toInt().toString(), // 留谈判空间
                wageOffer = suggestedWage.toString(),
                suggestedFee = suggestedFee,
                suggestedWage = suggestedWage,
                psychologicalPrice = suggestedFee,
                expectedWage = suggestedWage
            )
        }
    }

    /** 更新报价类型 */
    fun updateOfferType(type: OfferType) {
        _formState.value = _formState.value.copy(offerType = type)
    }

    /** 更新转会费 */
    fun updateTransferFee(value: String) {
        _formState.value = _formState.value.copy(transferFee = value.filter { it.isDigit() })
    }

    /** 更新工资 */
    fun updateWage(value: String) {
        _formState.value = _formState.value.copy(wageOffer = value.filter { it.isDigit() })
    }

    /** 更新合同年限 */
    fun updateContractYears(years: Int) {
        _formState.value = _formState.value.copy(contractYears = years.coerceIn(1, 5))
    }

    /** 更新签字费 */
    fun updateSigningBonus(value: String) {
        _formState.value = _formState.value.copy(signingBonus = value.filter { it.isDigit() })
    }

    /** 更新经纪人佣金 */
    fun updateAgentCommission(value: String) {
        _formState.value = _formState.value.copy(agentCommission = value.filter { it.isDigit() })
    }

    /** 更新角色承诺 */
    fun updateRolePromise(role: RolePromise) {
        _formState.value = _formState.value.copy(rolePromise = role)
    }

    // ==================== 提交报价 ====================

    /** 提交报价（V0.1 09 §三 第 3 步） */
    fun submitOffer() {
        val form = _formState.value
        if (form.playerId == 0) {
            _message.value = "请先选择球员"
            return
        }
        if (form.feeInt() <= 0) {
            _message.value = "转会费必须大于 0"
            return
        }
        _uiState.value = NegotiationUiState.Loading
        viewModelScope.launch {
            try {
                val request = OfferRequest(
                    playerId = form.playerId,
                    offerType = form.offerType,
                    transferFee = form.feeInt(),
                    wageOffer = form.wageInt(),
                    contractYears = form.contractYears,
                    signingBonus = form.signingBonusInt(),
                    agentCommission = form.agentCommissionInt(),
                    rolePromise = form.rolePromise
                )
                when (val result = repository.submitOffer(request)) {
                    is OfferSubmitResult.Success -> {
                        _formState.value = form.copy(
                            suggestedFee = result.valuation.psychologicalPrice,
                            suggestedWage = result.valuation.expectedWage,
                            psychologicalPrice = result.valuation.psychologicalPrice,
                            expectedWage = result.valuation.expectedWage
                        )
                        _uiState.value = NegotiationUiState.Submitted(result.offerId, result.valuation)
                        // 自动触发卖方评估
                        evaluateBySeller(result.offerId)
                    }
                    is OfferSubmitResult.Failed -> {
                        _uiState.value = NegotiationUiState.Error(result.reason)
                        _message.value = result.reason
                    }
                }
            } catch (e: Exception) {
                _uiState.value = NegotiationUiState.Error("提交报价失败：${e.message}")
            }
        }
    }

    // ==================== 卖方评估 ====================

    /** 触发卖方评估 */
    fun evaluateBySeller(offerId: Int) {
        _uiState.value = NegotiationUiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.evaluateBySeller(offerId)
                if (result == null) {
                    _uiState.value = NegotiationUiState.Error("卖方评估失败")
                    return@launch
                }
                val session = repository.getSession(offerId)
                val rounds = repository.getRounds(offerId)
                when (result.decision) {
                    is SellerDecision.Accept, is SellerDecision.AcceptDirectly -> {
                        // 卖方接受 → 进入球员个人条款谈判
                        val offer = repository.getOffer(offerId)
                        if (offer != null && session != null) {
                            _uiState.value = NegotiationUiState.PersonalTerms(
                                offerId = offerId,
                                offer = offer,
                                valuation = result.valuation
                            )
                            // 初始化合同条款表单（用报价中的工资）
                            _termsForm.value = ContractTermsFormState(
                                weeklyWage = offer.wageOffer.toString(),
                                contractYears = offer.contractYears ?: 4,
                                signingBonus = offer.signingBonus.toString(),
                                agentCommission = offer.agentCommission.toString(),
                                rolePromise = RolePromise.valueOf(offer.rolePromise ?: RolePromise.ROTATION.name)
                            )
                        }
                    }
                    is SellerDecision.Reject -> {
                        _uiState.value = NegotiationUiState.Failed(
                            offerId, (result.decision as SellerDecision.Reject).reason
                        )
                    }
                    is SellerDecision.Counter -> {
                        _uiState.value = NegotiationUiState.SellerReaction(
                            offerId = offerId,
                            decision = result.decision,
                            valuation = result.valuation,
                            session = session ?: return@launch,
                            rounds = rounds
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = NegotiationUiState.Error("卖方评估异常：${e.message}")
            }
        }
    }

    /** 接受卖方还价 */
    fun acceptCounter() {
        val current = _uiState.value as? NegotiationUiState.SellerReaction ?: return
        val counter = (current.decision as? SellerDecision.Counter)?.counter ?: return
        viewModelScope.launch {
            val ok = repository.acceptCounter(current.offerId, counter)
            if (ok) {
                // 进入球员个人条款谈判
                val offer = repository.getOffer(current.offerId)
                if (offer != null) {
                    _uiState.value = NegotiationUiState.PersonalTerms(
                        offerId = current.offerId,
                        offer = offer,
                        valuation = current.valuation
                    )
                    _termsForm.value = ContractTermsFormState(
                        weeklyWage = offer.wageOffer.toString(),
                        contractYears = offer.contractYears ?: 4,
                        signingBonus = offer.signingBonus.toString(),
                        agentCommission = offer.agentCommission.toString(),
                        rolePromise = RolePromise.valueOf(offer.rolePromise ?: RolePromise.ROTATION.name)
                    )
                }
            } else {
                _message.value = "接受还价失败"
            }
        }
    }

    /** 修改后重报（使用当前表单值） */
    fun modifyAndReoffer() {
        val current = _uiState.value as? NegotiationUiState.SellerReaction ?: return
        val form = _formState.value
        viewModelScope.launch {
            val ok = repository.modifyAndReoffer(
                offerId = current.offerId,
                newFee = form.feeInt(),
                newWage = form.wageInt(),
                newSigningBonus = form.signingBonusInt(),
                newAgentCommission = form.agentCommissionInt(),
                newRolePromise = form.rolePromise
            )
            if (ok) {
                _message.value = "已重新报价，等待卖方评估"
                evaluateBySeller(current.offerId)
            } else {
                _message.value = "重新报价失败（可能已达轮次上限）"
            }
        }
    }

    /** 撤回报价 */
    fun withdrawOffer() {
        val offerId = when (val state = _uiState.value) {
            is NegotiationUiState.SellerReaction -> state.offerId
            is NegotiationUiState.PersonalTerms -> state.offerId
            is NegotiationUiState.Submitted -> state.offerId
            else -> return
        }
        viewModelScope.launch {
            val ok = repository.withdrawOffer(offerId)
            _message.value = if (ok) "已撤回报价" else "撤回失败"
            if (ok) {
                _uiState.value = NegotiationUiState.Failed(offerId, "玩家撤回报价")
            }
        }
    }

    // ==================== 合同条款表单 ====================

    fun updateTermsWeeklyWage(value: String) {
        _termsForm.value = _termsForm.value.copy(weeklyWage = value.filter { it.isDigit() })
    }

    fun updateTermsYears(years: Int) {
        _termsForm.value = _termsForm.value.copy(contractYears = years.coerceIn(1, 5))
    }

    fun updateTermsSigningBonus(value: String) {
        _termsForm.value = _termsForm.value.copy(signingBonus = value.filter { it.isDigit() })
    }

    fun updateTermsAgentCommission(value: String) {
        _termsForm.value = _termsForm.value.copy(agentCommission = value.filter { it.isDigit() })
    }

    fun updateTermsAppearanceBonus(value: String) {
        _termsForm.value = _termsForm.value.copy(appearanceBonus = value.filter { it.isDigit() })
    }

    fun updateTermsGoalBonus(value: String) {
        _termsForm.value = _termsForm.value.copy(goalBonus = value.filter { it.isDigit() })
    }

    fun updateTermsAssistBonus(value: String) {
        _termsForm.value = _termsForm.value.copy(assistBonus = value.filter { it.isDigit() })
    }

    fun updateTermsCleanSheetBonus(value: String) {
        _termsForm.value = _termsForm.value.copy(cleanSheetBonus = value.filter { it.isDigit() })
    }

    fun updateTermsLoyaltyBonus(value: String) {
        _termsForm.value = _termsForm.value.copy(loyaltyBonus = value.filter { it.isDigit() })
    }

    fun updateReleaseClauseEnabled(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(releaseClauseEnabled = enabled)
    }

    fun updateReleaseClause(value: String) {
        _termsForm.value = _termsForm.value.copy(releaseClause = value.filter { it.isDigit() })
    }

    fun updateRelegationReleaseEnabled(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(relegationReleaseEnabled = enabled)
    }

    fun updateRelegationRelease(value: String) {
        _termsForm.value = _termsForm.value.copy(relegationRelease = value.filter { it.isDigit() })
    }

    fun updateUclRaiseEnabled(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(uclRaiseEnabled = enabled)
    }

    fun updateUclRaisePercent(value: String) {
        _termsForm.value = _termsForm.value.copy(uclRaisePercent = value.filter { it.isDigit() })
    }

    fun updateAnnualRaiseEnabled(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(annualRaiseEnabled = enabled)
    }

    fun updateAnnualRaisePercent(value: String) {
        _termsForm.value = _termsForm.value.copy(annualRaisePercent = value.filter { it.isDigit() })
    }

    fun updateExtensionOption(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(extensionOption = enabled)
    }

    fun updateBuybackEnabled(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(buybackEnabled = enabled)
    }

    fun updateBuybackClause(value: String) {
        _termsForm.value = _termsForm.value.copy(buybackClause = value.filter { it.isDigit() })
    }

    fun updateSellOnEnabled(enabled: Boolean) {
        _termsForm.value = _termsForm.value.copy(sellOnEnabled = enabled)
    }

    fun updateSellOnPercent(value: String) {
        _termsForm.value = _termsForm.value.copy(sellOnPercent = value.filter { it.isDigit() })
    }

    fun updateTermsRolePromise(role: RolePromise) {
        _termsForm.value = _termsForm.value.copy(rolePromise = role)
    }

    // ==================== 球员个人条款谈判 ====================

    /** 提交合同条款给球员评估 */
    fun submitPersonalTerms() {
        val current = _uiState.value as? NegotiationUiState.PersonalTerms ?: return
        val form = _termsForm.value
        viewModelScope.launch {
            try {
                val terms = form.toEntity(
                    saveId = repository.saveId,
                    offerId = current.offerId
                )
                val decision = repository.negotiatePersonalTerms(current.offerId, terms)
                if (decision == null) {
                    _uiState.value = NegotiationUiState.Error("球员条款谈判失败")
                    return@launch
                }
                when (decision) {
                    is PlayerDecision.Accept -> {
                        // 球员接受 → 完成转会
                        val ok = repository.completeTransfer(current.offerId)
                        _uiState.value = if (ok) {
                            NegotiationUiState.Completed(
                                offerId = current.offerId,
                                summary = decision.message
                            )
                        } else {
                            NegotiationUiState.Error("转会完成失败")
                        }
                    }
                    is PlayerDecision.Reject -> {
                        _uiState.value = NegotiationUiState.Failed(
                            current.offerId, decision.reason
                        )
                    }
                    is PlayerDecision.Counter -> {
                        // 球员还价 → 同步到表单让玩家查看
                        _termsForm.value = ContractTermsFormState(
                            weeklyWage = decision.counterTerms.weeklyWage.toString(),
                            contractYears = decision.counterTerms.contractYears,
                            signingBonus = decision.counterTerms.signingBonus.toString(),
                            agentCommission = decision.counterTerms.agentCommission.toString(),
                            rolePromise = RolePromise.valueOf(decision.counterTerms.rolePromise)
                        )
                        _uiState.value = NegotiationUiState.PlayerReaction(
                            offerId = current.offerId,
                            decision = decision,
                            valuation = current.valuation
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = NegotiationUiState.Error("合同谈判异常：${e.message}")
            }
        }
    }

    /** 接受球员还价 */
    fun acceptPlayerCounter() {
        val current = _uiState.value as? NegotiationUiState.PlayerReaction ?: return
        val counter = (current.decision as? PlayerDecision.Counter)?.counterTerms ?: return
        viewModelScope.launch {
            // 用还价条款再次提交
            val decision = repository.negotiatePersonalTerms(
                current.offerId,
                counter.copy(saveId = repository.saveId, offerId = current.offerId)
            )
            if (decision is PlayerDecision.Accept) {
                val ok = repository.completeTransfer(current.offerId)
                _uiState.value = if (ok) {
                    NegotiationUiState.Completed(current.offerId, decision.message)
                } else {
                    NegotiationUiState.Error("转会完成失败")
                }
            } else if (decision != null) {
                _uiState.value = NegotiationUiState.PlayerReaction(
                    current.offerId, decision, current.valuation
                )
            }
        }
    }

    /** 修改合同条款后重提（回到 PersonalTerms 状态由玩家继续编辑表单并重新提交） */
    fun modifyTermsAndReoffer() {
        val current = _uiState.value as? NegotiationUiState.PlayerReaction ?: return
        viewModelScope.launch {
            val offer = repository.getOffer(current.offerId) ?: return@launch
            _uiState.value = NegotiationUiState.PersonalTerms(
                offerId = current.offerId,
                offer = offer,
                valuation = current.valuation
            )
        }
    }

    // ==================== 消息消费 ====================

    fun consumeMessage() {
        _message.value = null
    }

    /** 重置到空闲状态 */
    fun resetToIdle() {
        _uiState.value = NegotiationUiState.Idle
    }

    companion object {
        /**
         * 创建 [NegotiationViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档与经理俱乐部 ID。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val saveManager = SaveManager.getInstance(app)
                    val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1
                    val clubId = runCatching {
                        kotlinx.coroutines.runBlocking {
                            saveManager.getCurrentSaveInfo()?.managerClubId ?: 1
                        }
                    }.getOrDefault(1)
                    return NegotiationViewModel(app, saveId, clubId) as T
                }
            }
    }
}
