package com.greendynasty.football.transfer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.transfer.model.CompareResult
import com.greendynasty.football.transfer.model.PlayerRecommendation
import com.greendynasty.football.transfer.model.SortOrder
import com.greendynasty.football.transfer.model.TransferSearchFilter
import com.greendynasty.football.transfer.model.TransferSearchResult
import com.greendynasty.football.transfer.model.TransferSortBy
import com.greendynasty.football.transfer.repository.TransferRepository
import com.greendynasty.football.transfer.ui.state.CompareUiState
import com.greendynasty.football.transfer.ui.state.RecommendUiState
import com.greendynasty.football.transfer.ui.state.TransferSearchUiState
import com.greendynasty.football.transfer.ui.state.TransferTab
import com.greendynasty.football.transfer.ui.state.WatchlistUiState
import com.greendynasty.football.transfer.window.TransferWindowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 转会市场页 ViewModel。
 *
 * 持有当前 Tab / 筛选条件 / 搜索结果 / 推荐列表 / 对比列表 / 观察名单 / 转会窗状态，
 * 通过 [TransferRepository] 协调各子模块。
 *
 * 核心交互：
 * - 4 Tab 切换：搜索 / 推荐 / 对比 / 观察名单
 * - 14 项筛选条件 + 5 种排序
 * - 球员对比（2-3 人）
 * - 观察名单增删查
 * - 转会窗状态实时展示
 *
 * @param app Application，用于初始化 [DatabaseManager] / [SaveManager]
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class TransferSearchViewModel(
    app: Application,
    saveId: Int,
    clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = TransferRepository(databaseManager, saveId, clubId)

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(TransferTab.DEFAULT)
    val currentTab: StateFlow<TransferTab> = _currentTab.asStateFlow()

    /** 当前筛选条件 */
    private val _currentFilter = MutableStateFlow(TransferSearchFilter.DEFAULT)
    val currentFilter: StateFlow<TransferSearchFilter> = _currentFilter.asStateFlow()

    /** 搜索结果 UI 状态 */
    private val _searchUiState = MutableStateFlow<TransferSearchUiState>(TransferSearchUiState.Loading)
    val searchUiState: StateFlow<TransferSearchUiState> = _searchUiState.asStateFlow()

    /** 推荐 UI 状态 */
    private val _recommendUiState = MutableStateFlow<RecommendUiState>(RecommendUiState.Loading)
    val recommendUiState: StateFlow<RecommendUiState> = _recommendUiState.asStateFlow()

    /** 对比 UI 状态 */
    private val _compareUiState = MutableStateFlow<CompareUiState>(CompareUiState.Idle)
    val compareUiState: StateFlow<CompareUiState> = _compareUiState.asStateFlow()

    /** 观察名单 UI 状态 */
    private val _watchlistUiState = MutableStateFlow<WatchlistUiState>(WatchlistUiState.Loading)
    val watchlistUiState: StateFlow<WatchlistUiState> = _watchlistUiState.asStateFlow()

    /** 转会窗状态 */
    private val _windowState = MutableStateFlow(TransferWindowState.CLOSED)
    val windowState: StateFlow<TransferWindowState> = _windowState.asStateFlow()

    /** 待对比球员 ID 列表（最多 3 人） */
    private val _compareSelection = MutableStateFlow<Set<Int>>(emptySet())
    val compareSelection: StateFlow<Set<Int>> = _compareSelection.asStateFlow()

    /** 操作结果提示 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // 启动时刷新转会窗状态与观察名单
        refreshWindowState()
        refreshWatchlist()
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab */
    fun switchTab(tab: TransferTab) {
        if (_currentTab.value != tab) {
            _currentTab.value = tab
            when (tab) {
                TransferTab.SEARCH -> Unit // 搜索结果由筛选条件驱动，不主动刷新
                TransferTab.RECOMMEND -> refreshRecommendations()
                TransferTab.COMPARE -> Unit // 对比由选择驱动
                TransferTab.WATCHLIST -> refreshWatchlist()
            }
        }
    }

    // ==================== 搜索 ====================

    /** 应用筛选条件并触发搜索 */
    fun applyFilter(filter: TransferSearchFilter) {
        _currentFilter.value = filter
        performSearch(filter)
    }

    /** 修改排序 */
    fun changeSort(sortBy: TransferSortBy, sortOrder: SortOrder) {
        val newFilter = _currentFilter.value.copy(sortBy = sortBy, sortOrder = sortOrder)
        applyFilter(newFilter)
    }

    /** 重置筛选 */
    fun resetFilter() {
        applyFilter(TransferSearchFilter.DEFAULT)
    }

    /** 执行搜索 */
    private fun performSearch(filter: TransferSearchFilter) {
        if (databaseManager.getSaveDatabaseOrNull() == null) {
            _searchUiState.value = TransferSearchUiState.Locked()
            return
        }
        _searchUiState.value = TransferSearchUiState.Loading
        viewModelScope.launch {
            try {
                val results = repository.search(filter)
                val window = repository.getTransferWindowState()
                _searchUiState.value = if (results.isEmpty()) {
                    TransferSearchUiState.Empty("未找到符合条件的球员")
                } else {
                    TransferSearchUiState.Normal(results, window, results.size)
                }
            } catch (e: Exception) {
                _searchUiState.value = TransferSearchUiState.Error("搜索失败：${e.message}")
            }
        }
    }

    // ==================== 推荐 ====================

    /** 刷新推荐列表 */
    fun refreshRecommendations() {
        if (databaseManager.getSaveDatabaseOrNull() == null) {
            _recommendUiState.value = RecommendUiState.Locked()
            return
        }
        _recommendUiState.value = RecommendUiState.Loading
        viewModelScope.launch {
            try {
                val recommendations = repository.recommend(_currentFilter.value)
                val weakPositions = repository.analyzeWeakPositions()
                _recommendUiState.value = if (recommendations.isEmpty()) {
                    RecommendUiState.Empty("暂无推荐球员，试试调整筛选条件")
                } else {
                    RecommendUiState.Normal(recommendations, weakPositions)
                }
            } catch (e: Exception) {
                _recommendUiState.value = RecommendUiState.Error("推荐加载失败：${e.message}")
            }
        }
    }

    // ==================== 对比 ====================

    /** 切换对比选择（加入/移出对比列表，最多 3 人） */
    fun toggleCompareSelection(playerId: Int) {
        val current = _compareSelection.value.toMutableSet()
        if (playerId in current) {
            current.remove(playerId)
        } else {
            if (current.size >= 3) {
                _message.value = "对比最多 3 人，请先移除一名球员"
                return
            }
            current.add(playerId)
        }
        _compareSelection.value = current
    }

    /** 执行对比 */
    fun performCompare() {
        val ids = _compareSelection.value.toList()
        if (ids.size < 2) {
            _message.value = "请至少选择 2 名球员进行对比"
            return
        }
        _compareUiState.value = CompareUiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.compare(ids)
                if (result == null) {
                    _compareUiState.value = CompareUiState.Error("对比失败，请重试")
                } else {
                    _compareUiState.value = CompareUiState.Normal(result)
                }
            } catch (e: Exception) {
                _compareUiState.value = CompareUiState.Error("对比失败：${e.message}")
            }
        }
    }

    /** 清空对比选择 */
    fun clearCompareSelection() {
        _compareSelection.value = emptySet()
        _compareUiState.value = CompareUiState.Idle
    }

    // ==================== 观察名单 ====================

    /** 加入/移出观察名单 */
    fun toggleWatchlist(playerId: Int) {
        viewModelScope.launch {
            if (repository.isOnWatchlist(playerId)) {
                repository.removeFromWatchlist(playerId)
                _message.value = "已移出观察名单"
            } else {
                val ok = repository.addToWatchlist(playerId)
                _message.value = if (ok) "已加入观察名单" else "加入失败（已存在或名单已满）"
            }
            refreshWatchlist()
        }
    }

    /** 刷新观察名单 */
    fun refreshWatchlist() {
        val entries = repository.watchlist.value
        _watchlistUiState.value = when {
            entries.isEmpty() -> WatchlistUiState.Empty()
            else -> WatchlistUiState.Normal(entries)
        }
    }

    // ==================== 转会窗状态 ====================

    /** 刷新转会窗状态 */
    fun refreshWindowState() {
        viewModelScope.launch {
            try {
                _windowState.value = repository.getTransferWindowState()
            } catch (e: Exception) {
                // 静默失败，保持 CLOSED 默认值
            }
        }
    }

    // ==================== 消息消费 ====================

    /** 消费操作结果消息 */
    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        /**
         * 创建 [TransferSearchViewModel] 工厂。
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
                    return TransferSearchViewModel(app, saveId, clubId) as T
                }
            }
    }
}
