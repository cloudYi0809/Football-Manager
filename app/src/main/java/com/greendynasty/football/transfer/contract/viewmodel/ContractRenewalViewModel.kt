package com.greendynasty.football.transfer.contract.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.transfer.contract.model.InitiationType
import com.greendynasty.football.transfer.contract.model.RenewalSpecialTerms
import com.greendynasty.football.transfer.contract.model.AcademyProtectionClause
import com.greendynasty.football.transfer.contract.model.PerformanceRaiseClause
import com.greendynasty.football.transfer.contract.model.VeteranClause
import com.greendynasty.football.transfer.contract.negotiation.RenewalInitiateResult
import com.greendynasty.football.transfer.contract.negotiation.RenewalSubmitResult
import com.greendynasty.football.transfer.contract.repository.ContractRepository
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/**
 * T12 合同续约页 ViewModel（V0.1 `09_转会_合同_经纪人系统.md` §六）。
 *
 * 持有：
 * - [RenewalFormState]：续约表单状态（工资/年限/签字费/违约金/角色 + 3 项续约特有条款）
 * - [ContractRenewalUiState]：当前续约谈判状态（发起/还价/完成/失败）
 * - 提醒列表（Flow → StateFlow）：未处理的合同到期提醒
 * - 续约历史列表：球员历史续约记录
 *
 * 核心交互：
 * 1. 玩家点击球员"续约" → [initiateRenewal] → 状态变为 Initiated
 * 2. 玩家填写条款 → [submitRenewalOffer] → 球员评估
 * 3. 球员接受 → Completed / 球员还价 → PlayerCountered → [acceptCounter] / [withdrawRenewal]
 *
 * @param app Application，用于初始化 [DatabaseManager] / [SaveManager]
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class ContractRenewalViewModel(
    app: Application,
    val saveId: Int,
    val clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = ContractRepository(databaseManager, saveId, clubId)

    /** 续约表单状态 */
    private val _formState = MutableStateFlow(RenewalFormState())
    val formState: StateFlow<RenewalFormState> = _formState.asStateFlow()

    /** 谈判页面状态 */
    private val _uiState = MutableStateFlow<ContractRenewalUiState>(ContractRenewalUiState.Idle)
    val uiState: StateFlow<ContractRenewalUiState> = _uiState.asStateFlow()

    /** 操作结果提示 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 未处理的合同到期提醒列表（驱动 UI 刷新） */
    private val _reminders = MutableStateFlow<List<ReminderItem>>(emptyList())
    val reminders: StateFlow<List<ReminderItem>> = _reminders.asStateFlow()

    /** 续约历史记录列表 */
    private val _renewalHistory = MutableStateFlow<List<RenewalHistoryItem>>(emptyList())
    val renewalHistory: StateFlow<List<RenewalHistoryItem>> = _renewalHistory.asStateFlow()

    init {
        // 订阅提醒 Flow（自动刷新 UI）
        viewModelScope.launch {
            repository.observeActiveReminders().collectLatest { reminders ->
                _reminders.value = reminders.mapNotNull { buildReminderItem(it) }
            }
        }
    }

    // ==================== 表单操作 ====================

    /** 初始化续约表单（针对某球员） */
    fun initFormForPlayer(playerId: Int, playerName: String) {
        viewModelScope.launch {
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)
            val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
            if (player == null || state == null) {
                _message.value = "球员不存在"
                return@launch
            }
            _formState.value = RenewalFormState(
                playerId = playerId,
                playerName = playerName,
                weeklyWage = (state.wage).toString(),
                contractYears = 3,
                signingBonus = "0",
                rolePromise = RolePromise.STARTER,
                currentWage = state.wage,
                demandsWage = state.wage,
                expectedWage = state.wage
            )
        }
    }

    /** 更新工资 */
    fun updateWeeklyWage(value: String) {
        _formState.value = _formState.value.copy(weeklyWage = value.filter { it.isDigit() })
    }

    /** 更新合同年限 */
    fun updateContractYears(years: Int) {
        _formState.value = _formState.value.copy(contractYears = years.coerceIn(1, 5))
    }

    /** 更新签字费 */
    fun updateSigningBonus(value: String) {
        _formState.value = _formState.value.copy(signingBonus = value.filter { it.isDigit() })
    }

    /** 更新角色承诺 */
    fun updateRolePromise(role: RolePromise) {
        _formState.value = _formState.value.copy(rolePromise = role)
    }

    /** 更新违约金启用 */
    fun updateReleaseClauseEnabled(enabled: Boolean) {
        _formState.value = _formState.value.copy(releaseClauseEnabled = enabled)
    }

    /** 更新违约金 */
    fun updateReleaseClause(value: String) {
        _formState.value = _formState.value.copy(releaseClause = value.filter { it.isDigit() })
    }

    /** 切换涨薪条款 */
    fun updatePerformanceRaiseEnabled(enabled: Boolean) {
        _formState.value = _formState.value.copy(performanceRaiseEnabled = enabled)
    }

    /** 切换退役条款 */
    fun updateVeteranClauseEnabled(enabled: Boolean) {
        _formState.value = _formState.value.copy(veteranClauseEnabled = enabled)
    }

    /** 切换青训保护条款 */
    fun updateAcademyProtectionEnabled(enabled: Boolean) {
        _formState.value = _formState.value.copy(academyProtectionEnabled = enabled)
    }

    // ==================== 续约发起 / 提交 ====================

    /** 发起续约（V0.1 09 §六） */
    fun initiateRenewal(playerId: Int, playerName: String) {
        _uiState.value = ContractRenewalUiState.Loading
        viewModelScope.launch {
            try {
                val ctx = repository.buildContext(seasonId = 1)
                val result = repository.initiateRenewal(
                    ctx = ctx,
                    playerId = playerId,
                    initiationType = InitiationType.PLAYER_INITIATED
                )
                when (result) {
                    is RenewalInitiateResult.Success -> {
                        // 同步表单建议值
                        _formState.value = _formState.value.copy(
                            playerId = playerId,
                            playerName = playerName,
                            weeklyWage = result.demandsWage.toString(),
                            contractYears = result.demandsMaxYears.coerceAtLeast(1),
                            demandsWage = result.demandsWage,
                            demandsMaxYears = result.demandsMaxYears,
                            expectedWage = result.expectedWage,
                            willingness = result.willingness,
                            wageBreakdown = result.wageBreakdown,
                            monthsRemaining = result.monthsRemaining
                        )
                        _uiState.value = ContractRenewalUiState.Initiated(
                            renewalId = result.renewalId,
                            demandsWage = result.demandsWage,
                            demandsMaxYears = result.demandsMaxYears,
                            expectedWage = result.expectedWage,
                            willingness = result.willingness,
                            wageBreakdown = result.wageBreakdown,
                            monthsRemaining = result.monthsRemaining
                        )
                    }
                    is RenewalInitiateResult.PlayerRejected -> {
                        _uiState.value = ContractRenewalUiState.Failed(0, result.message)
                        _message.value = result.message
                    }
                    is RenewalInitiateResult.Failed -> {
                        _uiState.value = ContractRenewalUiState.Error(result.reason)
                        _message.value = result.reason
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ContractRenewalUiState.Error("发起续约异常：${e.message}")
            }
        }
    }

    /** 提交续约报价（V0.1 09 §六） */
    fun submitRenewalOffer() {
        val form = _formState.value
        if (form.playerId == 0) {
            _message.value = "请先选择球员"
            return
        }
        val initiated = _uiState.value as? ContractRenewalUiState.Initiated
            ?: _uiState.value as? ContractRenewalUiState.PlayerCountered
        if (initiated == null) {
            _message.value = "请先发起续约"
            return
        }
        val renewalId = when (initiated) {
            is ContractRenewalUiState.Initiated -> initiated.renewalId
            is ContractRenewalUiState.PlayerCountered -> initiated.renewalId
            else -> 0
        }
        if (renewalId == 0) return

        _uiState.value = ContractRenewalUiState.Loading
        viewModelScope.launch {
            try {
                val ctx = repository.buildContext(seasonId = 1)
                // 构造续约特有条款
                val specialTerms = buildSpecialTerms(form)
                val result = repository.submitRenewalOffer(
                    ctx = ctx,
                    renewalId = renewalId,
                    weeklyWage = form.wageInt(),
                    contractYears = form.contractYears,
                    signingBonus = form.signingBonusInt(),
                    rolePromise = form.rolePromise,
                    releaseClause = form.releaseClauseInt(),
                    specialTerms = specialTerms
                )
                when (result) {
                    is RenewalSubmitResult.Accepted -> {
                        val c = result.completeResult
                        _uiState.value = ContractRenewalUiState.Completed(
                            renewalId = c.renewalId,
                            newWage = c.newWage,
                            newContractUntil = c.newContractUntil,
                            newSquadRole = c.newSquadRole,
                            wageChangePercent = c.wageChangePercent,
                            message = c.message
                        )
                        _message.value = "续约成功：${c.message}"
                    }
                    is RenewalSubmitResult.Counter -> {
                        // 同步还价到表单让玩家查看
                        _formState.value = form.copy(
                            weeklyWage = result.counterWeeklyWage.toString(),
                            contractYears = result.counterContractYears,
                            signingBonus = result.counterSigningBonus.toString()
                        )
                        _uiState.value = ContractRenewalUiState.PlayerCountered(
                            renewalId = renewalId,
                            counterWeeklyWage = result.counterWeeklyWage,
                            counterContractYears = result.counterContractYears,
                            counterSigningBonus = result.counterSigningBonus,
                            counterAgentCommission = result.counterAgentCommission,
                            message = result.message,
                            willingness = result.willingness
                        )
                    }
                    is RenewalSubmitResult.Rejected -> {
                        _uiState.value = ContractRenewalUiState.Failed(renewalId, result.reason)
                        _message.value = "球员拒绝：${result.reason}"
                    }
                    is RenewalSubmitResult.Failed -> {
                        _uiState.value = ContractRenewalUiState.Error(result.reason)
                        _message.value = result.reason
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ContractRenewalUiState.Error("提交报价异常：${e.message}")
            }
        }
    }

    /** 接受球员还价（V0.1 09 §六） */
    fun acceptCounter() {
        val current = _uiState.value as? ContractRenewalUiState.PlayerCountered ?: return
        _uiState.value = ContractRenewalUiState.Loading
        viewModelScope.launch {
            try {
                val ctx = repository.buildContext(seasonId = 1)
                val result = repository.acceptCounter(
                    ctx = ctx,
                    renewalId = current.renewalId,
                    counterWeeklyWage = current.counterWeeklyWage,
                    counterContractYears = current.counterContractYears,
                    counterSigningBonus = current.counterSigningBonus,
                    counterAgentCommission = current.counterAgentCommission
                )
                if (result != null) {
                    _uiState.value = ContractRenewalUiState.Completed(
                        renewalId = result.renewalId,
                        newWage = result.newWage,
                        newContractUntil = result.newContractUntil,
                        newSquadRole = result.newSquadRole,
                        wageChangePercent = result.wageChangePercent,
                        message = result.message
                    )
                    _message.value = "续约成功：${result.message}"
                } else {
                    _uiState.value = ContractRenewalUiState.Error("接受还价失败")
                    _message.value = "接受还价失败"
                }
            } catch (e: Exception) {
                _uiState.value = ContractRenewalUiState.Error("接受还价异常：${e.message}")
            }
        }
    }

    /** 撤回续约（V0.1 09 §六） */
    fun withdrawRenewal() {
        val renewalId = when (val state = _uiState.value) {
            is ContractRenewalUiState.Initiated -> state.renewalId
            is ContractRenewalUiState.PlayerCountered -> state.renewalId
            else -> return
        }
        viewModelScope.launch {
            try {
                val ctx = repository.buildContext(seasonId = 1)
                val ok = repository.withdrawRenewal(ctx, renewalId)
                _message.value = if (ok) "已撤回续约" else "撤回失败"
                if (ok) {
                    _uiState.value = ContractRenewalUiState.Failed(renewalId, "玩家撤回续约")
                }
            } catch (e: Exception) {
                _message.value = "撤回异常：${e.message}"
            }
        }
    }

    // ==================== 提醒操作 ====================

    /** 标记提醒已处理（玩家点击忽略） */
    fun markReminderHandled(reminderId: Int) {
        viewModelScope.launch {
            repository.markReminderHandled(reminderId)
        }
    }

    /** 加载球员历史续约记录 */
    fun loadRenewalHistory(playerId: Int) {
        viewModelScope.launch {
            val renewals = repository.getRenewalsByPlayer(playerId)
            _renewalHistory.value = renewals.map { buildHistoryItem(it) }
        }
    }

    // ==================== 消息消费 ====================

    fun consumeMessage() {
        _message.value = null
    }

    /** 重置到空闲状态 */
    fun resetToIdle() {
        _uiState.value = ContractRenewalUiState.Idle
    }

    // ==================== 内部工具 ====================

    /** 构造续约特有条款（V0.1 09 §六） */
    private fun buildSpecialTerms(form: RenewalFormState): RenewalSpecialTerms? {
        val performance = if (form.performanceRaiseEnabled) {
            PerformanceRaiseClause(
                triggerCondition = "season_apps >= 25",
                raisePercent = form.performanceRaisePercent.toIntOrNull() ?: 10,
                maxRaises = 2
            )
        } else null
        val veteran = if (form.veteranClauseEnabled) {
            VeteranClause(
                autoExtension = true,
                minAppearances = 15,
                minRating = 6.5
            )
        } else null
        val academy = if (form.academyProtectionEnabled) {
            AcademyProtectionClause(
                maxReleaseClause = 10_000_000,
                minContractYears = 3,
                trainingCompensation = 500_000
            )
        } else null
        return if (performance != null || veteran != null || academy != null) {
            RenewalSpecialTerms(
                performanceRaiseClause = performance,
                veteranClause = veteran,
                academyProtectionClause = academy
            )
        } else null
    }

    /** 构建提醒列表项 */
    private suspend fun buildReminderItem(reminder: com.greendynasty.football.transfer.contract.model.ContractReminderEntity): ReminderItem? {
        val player = databaseManager.historyPlayerDao().getPlayer(reminder.playerId) ?: return null
        val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, reminder.playerId) ?: return null
        val age = computeAge(player.birthDate)
        return ReminderItem(
            reminder = reminder,
            playerName = player.realName,
            playerPosition = player.primaryPosition,
            playerAge = age,
            currentWage = state.wage,
            squadRole = state.squadRole
        )
    }

    /** 构建续约历史列表项 */
    private suspend fun buildHistoryItem(
        renewal: com.greendynasty.football.transfer.contract.model.ContractRenewalEntity
    ): RenewalHistoryItem {
        val player = databaseManager.historyPlayerDao().getPlayer(renewal.playerId)
        return RenewalHistoryItem(
            renewal = renewal,
            playerName = player?.realName ?: "未知球员"
        )
    }

    /** 计算年龄 */
    private fun computeAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return 18
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, LocalDate.now()).years
        }.getOrElse { 18 }
    }

    companion object {
        /**
         * 创建 [ContractRenewalViewModel] 工厂。
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
                    return ContractRenewalViewModel(app, saveId, clubId) as T
                }
            }
    }
}
