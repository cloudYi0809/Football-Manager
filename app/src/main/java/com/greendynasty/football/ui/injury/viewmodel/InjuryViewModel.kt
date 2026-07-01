package com.greendynasty.football.ui.injury.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.injury.model.InjuryConfig
import com.greendynasty.football.injury.model.InjurySeverity
import com.greendynasty.football.injury.model.TreatmentType
import com.greendynasty.football.injury.repository.InjuryService
import com.greendynasty.football.ui.injury.ui.state.InjuryDisplay
import com.greendynasty.football.ui.injury.ui.state.InjuryUiState
import com.greendynasty.football.ui.injury.ui.state.MedicalFacilityDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 医疗中心页 ViewModel（T08 伤病系统 UI 入口）
 *
 * 持有 [InjuryService] 实例，暴露：
 * 1. 活跃伤病列表（Flow 观察 save_injury 变化）
 * 2. 医疗设施信息（等级 / 恢复速度系数 / 复发降低）
 * 3. 全队伤病风险评分（Top 5 高风险球员）
 * 4. 玩家操作：强行复出 / 选择治疗方案 / 升级医疗设施
 *
 * UI 状态：[InjuryUiState]，6 种完备状态。
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class InjuryViewModel(
    app: Application,
    private val saveId: Int,
    private val clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)
    private val injuryService = InjuryService(databaseManager)

    /** 当前游戏日期（从存档读取，用于恢复进度计算） */
    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    /** UI 状态 */
    private val _uiState = MutableStateFlow<InjuryUiState>(InjuryUiState.Loading)
    val uiState: StateFlow<InjuryUiState> = _uiState.asStateFlow()

    /** 操作结果提示 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        loadInjuryCenter()
    }

    // ==================== 数据加载 ====================

    /** 加载医疗中心数据（活跃伤病 + 医疗设施 + 风险评分） */
    fun loadInjuryCenter() {
        viewModelScope.launch {
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = InjuryUiState.Locked()
                    return@launch
                }
                _currentDate.value = runCatching {
                    LocalDate.parse(saveInfo.gameDate)
                }.getOrDefault(LocalDate.now())

                refreshUiState()
            } catch (e: Exception) {
                _uiState.value = InjuryUiState.Error("加载医疗中心失败：${e.message}")
            }
        }
    }

    /** 刷新 UI 状态（操作后调用） */
    private suspend fun refreshUiState() {
        val date = _currentDate.value
        val injuries = injuryService.getActiveInjuries(saveId, clubId)
        val facility = injuryService.getMedicalFacility(saveId, clubId)
        val riskScores = injuryService.getSquadInjuryRisk(saveId, clubId, date)
            .take(5) // Top 5 高风险球员

        val displays = injuries.map { buildDisplay(it) }
        val facilityDisplay = facility?.let { buildFacilityDisplay(it) }

        _uiState.value = when {
            displays.isEmpty() && riskScores.isEmpty() ->
                InjuryUiState.Empty()
            riskScores.any { it.riskScore >= 60 } ->
                InjuryUiState.Warning(
                    message = "存在高风险伤病球员",
                    injuries = displays, facility = facilityDisplay, riskScores = riskScores
                )
            else ->
                InjuryUiState.Normal(
                    injuries = displays, facility = facilityDisplay, riskScores = riskScores
                )
        }
    }

    // ==================== 玩家操作 ====================

    /** 强行复出 */
    fun forceReturn(injuryId: Int) {
        viewModelScope.launch {
            val result = injuryService.forceReturn(saveId, injuryId, _currentDate.value)
            _actionMessage.value = if (result.success) {
                "强行复出成功：${result.message}"
            } else {
                "强行复出失败：${result.message}"
            }
            if (result.success) refreshUiState()
        }
    }

    /** 选择治疗方案 */
    fun selectTreatment(injuryId: Int, treatmentType: TreatmentType) {
        viewModelScope.launch {
            val result = injuryService.selectTreatment(
                saveId, injuryId, treatmentType, _currentDate.value
            )
            _actionMessage.value = if (result.success) {
                result.message
            } else {
                "治疗方案选择失败：${result.message}"
            }
            if (result.success) refreshUiState()
        }
    }

    /** 升级医疗设施 */
    fun upgradeMedicalFacility(targetLevel: Int) {
        viewModelScope.launch {
            val result = injuryService.upgradeMedicalFacility(
                saveId, clubId, targetLevel, _currentDate.value
            )
            _actionMessage.value = if (result.success) {
                result.message
            } else {
                "医疗设施升级失败：${result.message}"
            }
            if (result.success) refreshUiState()
        }
    }

    /** 消费操作消息 */
    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    // ==================== 内部工具 ====================

    /** 构造伤病展示模型 */
    private suspend fun buildDisplay(injury: com.greendynasty.football.data.save.entity.SaveInjuryEntity): InjuryDisplay {
        val playerName = runCatching {
            databaseManager.historyPlayerDao().getPlayer(injury.playerId)?.displayName
                ?: "球员${injury.playerId}"
        }.getOrDefault("球员${injury.playerId}")

        val severity = InjurySeverity.fromCode(injury.severity)
        val typeDef = InjuryConfig.getDefault().getInjuryType(injury.injuryType)
        val progress = injury.recoveryProgress
        val remaining = runCatching {
            java.time.Period.between(_currentDate.value, LocalDate.parse(injury.expectedReturnDate)).days
        }.getOrDefault(0).coerceAtLeast(0)

        val statusName = when (injury.status) {
            "active" -> "伤病中"
            "recovering" -> "恢复中"
            "returned_early" -> "强行复出"
            "recurred" -> "复发"
            else -> injury.status
        }

        return InjuryDisplay(
            injury = injury,
            playerName = playerName,
            severityName = severity.displayName,
            injuryTypeNameCn = typeDef?.nameCn ?: injury.injuryType,
            progressPercent = progress,
            remainingDays = remaining,
            statusDisplayName = statusName
        )
    }

    /** 构造医疗设施展示模型 */
    private fun buildFacilityDisplay(
        facility: com.greendynasty.football.injury.model.MedicalFacilityEntity
    ): MedicalFacilityDisplay {
        val config = InjuryConfig.getDefault()
        val canUpgrade = facility.upgradeCooldownDays <= 0 &&
            facility.medicalLevel < config.facility.maxLevel
        val targetLevel = (facility.medicalLevel + 10).coerceAtMost(config.facility.maxLevel)
        val upgradeCost = config.facility.upgradeBaseCost +
            (targetLevel - facility.medicalLevel) * config.facility.upgradeCostCoefficient

        return MedicalFacilityDisplay(
            facility = facility,
            speedMultiplierPercent = ((facility.recoverySpeedMultiplier - 1.0) * 100).toInt(),
            recurrenceReductionPercent = (facility.recurrenceReduction * 100).toInt(),
            canUpgrade = canUpgrade,
            upgradeCost = upgradeCost
        )
    }

    companion object {
        private const val TAG = "InjuryViewModel"

        /**
         * 创建 [InjuryViewModel] 工厂。
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
                    return InjuryViewModel(app, saveId, clubId) as T
                }
            }
    }
}
