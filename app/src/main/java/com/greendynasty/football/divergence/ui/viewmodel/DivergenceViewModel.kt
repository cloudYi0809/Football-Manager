package com.greendynasty.football.divergence.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.deviation.DeviationCalculator
import com.greendynasty.football.butterfly.repository.ButterflyRepository
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.divergence.DivergenceTimelineComposer
import com.greendynasty.football.divergence.generator.DivergenceTextGenerator
import com.greendynasty.football.divergence.model.DivergenceFilter
import com.greendynasty.football.divergence.repository.DivergenceRepository
import com.greendynasty.football.divergence.ui.state.DivergenceTab
import com.greendynasty.football.divergence.ui.state.DivergenceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * T21 历史分歧时间线页 ViewModel（任务 T21.3：时间线 UI）。
 *
 * 持有：
 * - [DivergenceUiState]：时间线列表 + 归档记录 + 无替代记录 + 详情视图
 * - 当前 Tab（默认时间线）
 *
 * 核心交互：
 * 1. 进入页面 → [loadAll] 加载时间线 + 归档 + 无替代记录
 * 2. 筛选 → [applyFilter] 按分类 / 重要度筛选时间线
 * 3. 点击事件 → [selectTimelineItem] / [selectArchive] → 详情视图
 * 4. 返回 → [clearSelection]
 *
 * @param app Application，用于初始化 [DatabaseManager]
 * @param saveUuid 当前存档 UUID
 */
class DivergenceViewModel(
    app: Application,
    val saveUuid: String
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val config = ButterflyConfig.DEFAULT
    private val textGenerator = DivergenceTextGenerator()
    private val composer = DivergenceTimelineComposer(textGenerator)
    private val butterflyRepository = ButterflyRepository(
        databaseManager, config, DeviationCalculator(config)
    )
    private val repository = DivergenceRepository(
        databaseManager, butterflyRepository, composer
    )

    /** UI 状态 */
    private val _uiState = MutableStateFlow(DivergenceUiState())
    val uiState: StateFlow<DivergenceUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(DivergenceTab.TIMELINE)
    val currentTab: StateFlow<DivergenceTab> = _currentTab.asStateFlow()

    init {
        // 订阅当前事件列表 Flow（自动刷新时间线）
        viewModelScope.launch {
            repository.observeCurrentEvents(saveUuid).collectLatest {
                // 事件列表变更时重新加载时间线
                loadTimeline()
            }
        }
        // 初次加载
        loadAll()
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: DivergenceTab) {
        _currentTab.value = tab
    }

    // ==================== 加载 ====================

    /** 加载全部数据（进入页面时调用）。 */
    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                loadTimeline()
                val archived = repository.getArchivedDivergences(saveUuid)
                val noReplacement = repository.getNoReplacementRecords(saveUuid)
                val noReplacementCount = repository.countNoReplacement(saveUuid)
                val withReplacementCount = repository.countWithReplacement(saveUuid)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    archivedDivergences = archived,
                    noReplacementRecords = noReplacement,
                    noReplacementCount = noReplacementCount,
                    withReplacementCount = withReplacementCount
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "加载失败：${e.message}"
                )
            }
        }
    }

    /** 仅刷新时间线（轻量级，不重载归档）。 */
    fun loadTimeline() {
        viewModelScope.launch {
            try {
                val allItems = repository.getCurrentTimeline(saveUuid)
                val currentFilter = _uiState.value.filter
                // 应用筛选：若为 NONE 直接使用全部，否则在已有 items 上筛选
                val filtered = if (currentFilter == DivergenceFilter.NONE) {
                    allItems
                } else {
                    filterTimelineItems(allItems, currentFilter)
                }
                _uiState.value = _uiState.value.copy(
                    timelineItems = filtered,
                    totalCount = allItems.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "加载时间线失败：${e.message}"
                )
            }
        }
    }

    /**
     * 对已有时间线条目应用筛选（避免重复转换）。
     */
    private fun filterTimelineItems(
        items: List<com.greendynasty.football.divergence.model.DivergenceTimelineItem>,
        filter: DivergenceFilter
    ): List<com.greendynasty.football.divergence.model.DivergenceTimelineItem> {
        return items.filter { item ->
            val categoryMatch = filter.category == null || item.log.category == filter.category
            val importanceMatch = filter.importanceLevel == null ||
                item.importanceLevel == filter.importanceLevel
            val replacementMatch = filter.onlyWithReplacement == null ||
                item.log.hasMajorReplacement == filter.onlyWithReplacement
            categoryMatch && importanceMatch && replacementMatch
        }
    }

    // ==================== 筛选 ====================

    /** 应用筛选条件。 */
    fun applyFilter(filter: DivergenceFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        loadTimeline()
    }

    /** 清除筛选。 */
    fun clearFilter() {
        applyFilter(DivergenceFilter.NONE)
    }

    /** 仅显示有重大替代的分歧。 */
    fun filterOnlyWithReplacement() {
        applyFilter(DivergenceFilter(onlyWithReplacement = true))
    }

    /** 仅显示无重大替代的分歧。 */
    fun filterOnlyNoReplacement() {
        applyFilter(DivergenceFilter(onlyWithReplacement = false))
    }

    // ==================== 详情 ====================

    /** 选中某时间线条目 → 加载详情。 */
    fun selectTimelineItem(eventId: String) {
        viewModelScope.launch {
            try {
                val item = _uiState.value.timelineItems.firstOrNull {
                    it.log.eventId == eventId
                }
                if (item != null) {
                    _uiState.value = _uiState.value.copy(selectedTimelineItem = item)
                    _currentTab.value = DivergenceTab.DETAIL
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "加载详情失败：${e.message}"
                )
            }
        }
    }

    /** 选中某归档记录 → 查看详情。 */
    fun selectArchive(archiveId: String) {
        viewModelScope.launch {
            try {
                val archive = _uiState.value.archivedDivergences.firstOrNull {
                    it.archiveId == archiveId
                }
                if (archive != null) {
                    _uiState.value = _uiState.value.copy(selectedArchive = archive)
                    _currentTab.value = DivergenceTab.DETAIL
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "加载归档详情失败：${e.message}"
                )
            }
        }
    }

    /** 清除选中（返回列表）。 */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTimelineItem = null,
            selectedArchive = null
        )
        _currentTab.value = DivergenceTab.TIMELINE
    }

    /** 消费消息。 */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        /**
         * 创建 ViewModelFactory（注入 saveUuid）。
         */
        fun factory(app: Application, saveUuid: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DivergenceViewModel(app, saveUuid) as T
                }
            }
    }
}
