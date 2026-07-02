package com.greendynasty.football.youth.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.growth.calculator.GrowthCalculator
import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.youth.generator.PositionWeightTable
import com.greendynasty.football.youth.generator.ProductionQualityCalculator
import com.greendynasty.football.youth.generator.YouthPlayerGenerator
import com.greendynasty.football.youth.growth.YouthAnomalyGuard
import com.greendynasty.football.youth.growth.YouthGrowthService
import com.greendynasty.football.youth.model.AcademyStyle
import com.greendynasty.football.youth.model.InvestmentField
import com.greendynasty.football.youth.model.YouthAcademyConfig
import com.greendynasty.football.youth.model.YouthTier
import com.greendynasty.football.youth.repository.YouthRepository
import com.greendynasty.football.youth.ui.state.YouthTab
import com.greendynasty.football.youth.ui.state.YouthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T16 青训学院 ViewModel（V0.1 08 §二 + T16 方案 §六）。
 *
 * 持有：
 * - [YouthUiState]：4 个 Tab 数据 + 详情视图
 * - 当前 Tab（默认概览）
 * - 当前游戏日期（V1 简化：用系统日期，V2 由 T07 推进上下文传入）
 *
 * 核心交互：
 * 1. 进入青训学院页 → [loadAll] 加载配置 + 球员列表 + 事件
 * 2. 切换 Tab → [switchTab]
 * 3. 点击球员 → [selectPlayer] → 详情视图
 * 4. 投资 / 风格切换 / 球员操作 → [invest] / [changeStyle] / [signProContract] 等
 *
 * @param app Application，用于初始化 [DatabaseManager]
 * @param saveId 当前存档 ID
 * @param clubId 当前俱乐部 ID
 */
class YouthViewModel(
    app: Application,
    val saveId: Int,
    val clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val config = YouthAcademyConfig.getDefault()
    private val positionWeightTable = PositionWeightTable()
    private val productionCalculator = ProductionQualityCalculator(databaseManager, config)
    private val generator = YouthPlayerGenerator(databaseManager, productionCalculator, positionWeightTable, config)
    private val growthCalculator = GrowthCalculator(GrowthConfig.getDefault())
    private val anomalyGuard = YouthAnomalyGuard(config)
    @Suppress("unused")
    private val growthService = YouthGrowthService(databaseManager, growthCalculator, anomalyGuard, config)
    private val repository = YouthRepository(databaseManager, generator, productionCalculator, config)

    /** UI 状态 */
    private val _uiState = MutableStateFlow(YouthUiState())
    val uiState: StateFlow<YouthUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(YouthTab.OVERVIEW)
    val currentTab: StateFlow<YouthTab> = _currentTab.asStateFlow()

    /** 当前游戏日期（V1 简化：用系统日期，V2 由 T07 推进上下文传入） */
    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    init {
        // 订阅青训学院配置 Flow（自动刷新 UI）
        viewModelScope.launch {
            repository.observeAcademy(saveId, clubId).collectLatest { academy ->
                _uiState.value = _uiState.value.copy(
                    academy = academy?.let { repository.toViewItem(it) }
                )
            }
        }
        // 订阅 U18 球员 Flow
        viewModelScope.launch {
            repository.observePlayersByTier(saveId, clubId, YouthTier.U18).collectLatest { players ->
                val viewItems = players.map { repository.toViewItem(it, _currentDate.value) }
                _uiState.value = _uiState.value.copy(u18Players = viewItems)
            }
        }
        // 订阅 U21 球员 Flow
        viewModelScope.launch {
            repository.observePlayersByTier(saveId, clubId, YouthTier.U21).collectLatest { players ->
                val viewItems = players.map { repository.toViewItem(it, _currentDate.value) }
                _uiState.value = _uiState.value.copy(u21Players = viewItems)
            }
        }
        // 订阅最近事件 Flow
        viewModelScope.launch {
            repository.observeRecentEvents(saveId, clubId, 20).collectLatest { events ->
                _uiState.value = _uiState.value.copy(recentEvents = events)
            }
        }
        // 初次加载
        loadAll()
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: YouthTab) {
        _currentTab.value = tab
    }

    // ==================== 加载 ====================

    /** 加载全部数据（进入页面时调用）。 */
    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 确保青训学院已初始化
                val academy = repository.initializeAcademy(saveId, clubId)

                val stats = repository.getStatistics(saveId, clubId)
                val clubState = runCatching {
                    databaseManager.saveClubStateDao().getByClub(saveId, clubId)
                }.getOrNull()
                val balance = clubState?.balance ?: 0

                _uiState.value = _uiState.value.copy(
                    academy = academy?.let { repository.toViewItem(it) },
                    statistics = stats,
                    clubBalance = balance,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "加载数据失败：${e.message}"
                )
            }
        }
    }

    /** 刷新统计信息。 */
    fun loadStatistics() {
        viewModelScope.launch {
            try {
                val stats = repository.getStatistics(saveId, clubId)
                _uiState.value = _uiState.value.copy(statistics = stats)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "加载统计失败：${e.message}"
                )
            }
        }
    }

    // ==================== 详情 ====================

    /** 选中某球员 → 加载详情。 */
    fun selectPlayer(youthPlayerId: Int) {
        viewModelScope.launch {
            try {
                val player = repository.getPlayer(youthPlayerId)
                if (player != null) {
                    val viewItem = repository.toViewItem(player, _currentDate.value)
                    _uiState.value = _uiState.value.copy(selectedPlayer = viewItem)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "加载详情失败：${e.message}"
                )
            }
        }
    }

    /** 清除选中（返回列表）。 */
    fun clearSelectedPlayer() {
        _uiState.value = _uiState.value.copy(selectedPlayer = null)
    }

    // ==================== 学院操作 ====================

    /** 投资升级。 */
    fun invest(field: InvestmentField) {
        viewModelScope.launch {
            try {
                val result = repository.invest(saveId, clubId, field, _currentDate.value)
                _uiState.value = _uiState.value.copy(message = result.message)
                loadStatistics()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "投资失败：${e.message}"
                )
            }
        }
    }

    /** 切换风格。 */
    fun changeStyle(style: AcademyStyle) {
        viewModelScope.launch {
            try {
                val result = repository.changeStyle(saveId, clubId, style)
                _uiState.value = _uiState.value.copy(message = result.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "切换风格失败：${e.message}"
                )
            }
        }
    }

    // ==================== 球员操作 ====================

    /** 签青年合同。 */
    fun signYouthContract(youthPlayerId: Int) {
        viewModelScope.launch {
            val result = repository.signYouthContract(youthPlayerId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 签职业合同。 */
    fun signProContract(youthPlayerId: Int, wage: Int, years: Int) {
        viewModelScope.launch {
            val result = repository.signProfessionalContract(
                youthPlayerId, wage, years, _currentDate.value
            )
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 重点培养开关。 */
    fun setKeyProspect(youthPlayerId: Int, isKey: Boolean) {
        viewModelScope.launch {
            val result = repository.setKeyProspect(youthPlayerId, isKey)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 设训练方向。 */
    fun setTrainingFocus(youthPlayerId: Int, focus: String) {
        viewModelScope.launch {
            val result = repository.setTrainingFocus(youthPlayerId, focus)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 安排导师。 */
    fun assignMentor(youthPlayerId: Int, mentorPlayerId: Int) {
        viewModelScope.launch {
            val result = repository.assignMentor(saveId, youthPlayerId, mentorPlayerId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 提拔一线队。 */
    fun promoteToFirstTeam(youthPlayerId: Int) {
        viewModelScope.launch {
            val result = repository.promoteToFirstTeam(youthPlayerId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 外租。 */
    fun loanOut(youthPlayerId: Int) {
        viewModelScope.launch {
            val result = repository.loanOut(youthPlayerId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    /** 放弃培养。 */
    fun release(youthPlayerId: Int) {
        viewModelScope.launch {
            val result = repository.release(youthPlayerId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
            clearSelectedPlayer()
        }
    }

    // ==================== 事件处理 ====================

    /** 处理事件。 */
    fun resolveEvent(eventId: Int, accepted: Boolean, summary: String? = null) {
        viewModelScope.launch {
            val result = repository.resolveEvent(eventId, accepted, _currentDate.value, summary)
            _uiState.value = _uiState.value.copy(message = result.message)
        }
    }

    // ==================== 月度处理（V1 调试用） ====================

    /**
     * 手动触发月度处理（V1 调试用，正式由 T07 每月推进调用）。
     */
    fun triggerMonthlyProcess() {
        viewModelScope.launch {
            try {
                val result = repository.processMonthly(saveId, clubId, _currentDate.value)
                _uiState.value = _uiState.value.copy(message = result.message)
                loadStatistics()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "月度处理失败：${e.message}"
                )
            }
        }
    }

    /** 消费消息。 */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        /**
         * 创建 ViewModelFactory（注入 saveId + clubId）。
         */
        fun factory(app: Application, saveId: Int, clubId: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return YouthViewModel(app, saveId, clubId) as T
                }
            }
    }
}
