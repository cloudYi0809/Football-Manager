package com.greendynasty.football.prospect.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.prospect.discovery.ProspectDiscoveryService
import com.greendynasty.football.prospect.discovery.ProspectPoolManager
import com.greendynasty.football.prospect.model.ProspectConfig
import com.greendynasty.football.prospect.path.ButterflyEffectMarker
import com.greendynasty.football.prospect.path.ProspectPathSimulator
import com.greendynasty.football.prospect.repository.ProspectRepository
import com.greendynasty.football.prospect.ui.state.ProspectTab
import com.greendynasty.football.prospect.ui.state.ProspectUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T15 历史新星池页 ViewModel（V0.2 08 §六 + T15 方案 §六）。
 *
 * 持有：
 * - [ProspectUiState]：3 个 Tab 数据 + 详情视图
 * - 当前 Tab（默认已发现）
 * - 当前游戏日期（V1 简化：用系统日期，V2 由 T07 推进上下文传入）
 *
 * 核心交互：
 * 1. 进入历史新星页 → [loadAll] 加载统计 + 已发现列表
 * 2. 切换 Tab → [switchTab]
 * 3. 点击新星 → [selectProspect] → 详情页（路径时间轴）
 * 4. 返回 → [clearSelectedProspect]
 *
 * @param app Application，用于初始化 [DatabaseManager]
 * @param saveId 当前存档 ID
 * @param saveUuid 当前存档 UUID（蝴蝶事件 save_id 字段用）
 */
class ProspectViewModel(
    app: Application,
    val saveId: Int,
    val saveUuid: String
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val config = ProspectConfig.DEFAULT
    private val repository = ProspectRepository(databaseManager, config)
    private val poolManager = ProspectPoolManager(databaseManager)
    @Suppress("unused")
    private val discoveryService = ProspectDiscoveryService(databaseManager, config, poolManager)
    private val butterflyMarker = ButterflyEffectMarker(databaseManager, config)
    private val pathSimulator = ProspectPathSimulator(databaseManager, config, butterflyMarker)

    /** UI 状态 */
    private val _uiState = MutableStateFlow(ProspectUiState())
    val uiState: StateFlow<ProspectUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(ProspectTab.DISCOVERED)
    val currentTab: StateFlow<ProspectTab> = _currentTab.asStateFlow()

    /** 当前游戏日期（V1 简化：用系统日期，V2 由 T07 推进上下文传入） */
    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    init {
        // 订阅已发现新星 Flow（自动刷新 UI）
        viewModelScope.launch {
            repository.observeDiscoveredProspects(saveId).collectLatest { prospects ->
                _uiState.value = _uiState.value.copy(discoveredProspects = prospects)
            }
        }
        // 订阅全部活跃新星 Flow
        viewModelScope.launch {
            repository.observeAllActiveProspects(saveId).collectLatest { prospects ->
                _uiState.value = _uiState.value.copy(allActiveProspects = prospects)
            }
        }
        // 订阅选中新星的路径事件 Flow
        viewModelScope.launch {
            _uiState.value.selectedProspect?.let { prospect ->
                repository.observePathEvents(saveId, prospect.prospectId).collectLatest { events ->
                    _uiState.value = _uiState.value.copy(pathEvents = events)
                }
            }
        }
        // 初次加载统计
        loadStatistics()
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: ProspectTab) {
        _currentTab.value = tab
    }

    // ==================== 加载 ====================

    /** 加载统计信息 + 池大小。 */
    fun loadStatistics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val stats = repository.getStatistics(saveId)
                val poolSize = runCatching { repository.getPoolSize() }.getOrDefault(0)
                _uiState.value = _uiState.value.copy(
                    statistics = stats,
                    poolSize = poolSize,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "加载统计失败：${e.message}"
                )
            }
        }
    }

    /** 加载全部数据（进入页面时调用）。 */
    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 尝试激活到期新星（V1 简化：进入页面时也激活一次）
                runCatching { poolManager.activateProspects(_currentDate.value, saveId) }

                val stats = repository.getStatistics(saveId)
                val poolSize = runCatching { repository.getPoolSize() }.getOrDefault(0)
                val discovered = repository.getDiscoveredProspects(saveId)
                _uiState.value = _uiState.value.copy(
                    statistics = stats,
                    poolSize = poolSize,
                    discoveredProspects = discovered,
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

    // ==================== 详情 ====================

    /** 选中某新星 → 加载详情 + 路径事件。 */
    fun selectProspect(prospectId: Int) {
        viewModelScope.launch {
            try {
                val detail = repository.getProspectDetail(saveId, prospectId)
                if (detail != null) {
                    _uiState.value = _uiState.value.copy(
                        selectedProspect = detail.prospect,
                        pathEvents = detail.pathEvents
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "加载详情失败：${e.message}"
                )
            }
        }
    }

    /** 清除选中（返回列表）。 */
    fun clearSelectedProspect() {
        _uiState.value = _uiState.value.copy(
            selectedProspect = null,
            pathEvents = emptyList()
        )
    }

    // ==================== 手动触发路径模拟（V1 调试用） ====================

    /**
     * 手动触发一次月度路径模拟（V1 调试用，正式由 T07 每月推进调用）。
     */
    fun triggerMonthlySimulation() {
        viewModelScope.launch {
            try {
                val results = pathSimulator.simulateMonthly(saveId, saveUuid, _currentDate.value)
                _uiState.value = _uiState.value.copy(
                    message = "路径模拟完成：${results.size} 个事件"
                )
                loadStatistics()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "路径模拟失败：${e.message}"
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
         * 创建 ViewModelFactory（注入 saveId + saveUuid）。
         */
        fun factory(app: Application, saveId: Int, saveUuid: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProspectViewModel(app, saveId, saveUuid) as T
                }
            }
    }
}
