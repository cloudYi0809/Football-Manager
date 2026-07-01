package com.greendynasty.football.ui.squad.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.ui.squad.data.SquadRepository
import com.greendynasty.football.ui.squad.model.PlayerAction
import com.greendynasty.football.ui.squad.model.PlayerWithState
import com.greendynasty.football.ui.squad.model.SquadFilter
import com.greendynasty.football.ui.squad.model.SquadSortOption
import com.greendynasty.football.ui.squad.model.SquadTab
import com.greendynasty.football.ui.squad.ui.state.SquadUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 阵容页 ViewModel。
 *
 * 持有当前梯队 / 筛选 / 排序 / 搜索关键字，并通过 [SquadRepository] 响应式拉取球员列表，
 * 暴露 6 种完备 UI 状态（[SquadUiState]）。
 *
 * 核心交互：5 梯队切换、8 项组合筛选、8 种排序、模糊搜索、长按操作。
 *
 * @param app Application，用于初始化 [DatabaseManager] / [SaveManager]
 * @param saveId 当前存档 ID（save.db 列值）
 * @param clubId 经理当前俱乐部 ID
 */
class SquadViewModel(
    app: Application,
    saveId: Int,
    clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = SquadRepository(databaseManager, saveId, clubId)

    /** 当前梯队 */
    private val _currentTab = MutableStateFlow(SquadTab.DEFAULT)
    val currentTab: StateFlow<SquadTab> = _currentTab.asStateFlow()

    /** 当前筛选条件 */
    private val _currentFilter = MutableStateFlow(SquadFilter.DEFAULT)
    val currentFilter: StateFlow<SquadFilter> = _currentFilter.asStateFlow()

    /** 当前排序 */
    private val _currentSort = MutableStateFlow(SquadSortOption.DEFAULT)
    val currentSort: StateFlow<SquadSortOption> = _currentSort.asStateFlow()

    /** 搜索关键字 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 待导航的球员 ID（点击行触发，UI 消费后置空） */
    private val _selectedPlayerId = MutableStateFlow<Int?>(null)
    val selectedPlayerId: StateFlow<Int?> = _selectedPlayerId.asStateFlow()

    /** 待展示操作弹层的球员 ID（长按行触发，UI 消费后置空） */
    private val _actionSheetPlayerId = MutableStateFlow<Int?>(null)
    val actionSheetPlayerId: StateFlow<Int?> = _actionSheetPlayerId.asStateFlow()

    /** 操作结果提示（成功/失败消息） */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /**
     * 阵容页 UI 状态。
     *
     * 由 (tab, query, filter, sort) 四元组 flatMapLatest 到 repository 流派生。
     * - 搜索/筛选非默认 → [SquadRepository.searchPlayers]
     * - 否则 → [SquadRepository.getSquadPlayers]
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SquadUiState> =
        combine(_currentTab, _query, _currentFilter, _currentSort) { tab, q, f, s ->
            QueryParams(tab, q, f, s)
        }.flatMapLatest { params ->
            if (params.query.isNotBlank() || !params.filter.isDefault()) {
                repository.searchPlayers(params.query, params.filter, params.sort)
            } else {
                repository.getSquadPlayers(params.currentTab)
            }
        }.mapToState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SquadUiState.Loading
            )

    // ==================== 公共交互方法 ====================

    /** 切换梯队 */
    fun switchTab(tab: SquadTab) {
        if (_currentTab.value != tab) {
            _currentTab.value = tab
        }
    }

    /** 应用筛选条件 */
    fun applyFilter(filter: SquadFilter) {
        _currentFilter.value = filter
    }

    /** 应用排序 */
    fun applySort(sort: SquadSortOption) {
        _currentSort.value = sort
    }

    /** 搜索（空串清除搜索） */
    fun search(query: String) {
        _query.value = query
    }

    /** 点击球员行：触发导航 */
    fun onPlayerClick(playerId: Int) {
        _selectedPlayerId.value = playerId
    }

    /** 消费导航事件 */
    fun consumeNavigation() {
        _selectedPlayerId.value = null
    }

    /** 长按球员行：触发操作弹层 */
    fun onPlayerLongClick(playerId: Int) {
        _actionSheetPlayerId.value = playerId
    }

    /** 消费操作弹层事件 */
    fun consumeActionSheet() {
        _actionSheetPlayerId.value = null
    }

    /** 执行长按操作 */
    fun performAction(action: PlayerAction) {
        val playerId = _actionSheetPlayerId.value ?: return
        consumeActionSheet()
        viewModelScope.launch {
            val ok = repository.performPlayerAction(playerId, action)
            _actionMessage.value = if (ok) {
                "${action.displayName}操作已提交"
            } else {
                "${action.displayName}操作失败"
            }
        }
    }

    /** 消费操作结果消息 */
    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    // ==================== 内部工具 ====================

    /** 将列表流映射为 6 种 UI 状态 */
    private fun kotlinx.coroutines.flow.Flow<List<PlayerWithState>>.mapToState():
        kotlinx.coroutines.flow.Flow<SquadUiState> = map { list ->
            when {
                list.isEmpty() -> SquadUiState.Empty()
                list.any { it.injuryStatus != "healthy" } ->
                    SquadUiState.Warning("当前梯队有伤病球员", list)
                else -> SquadUiState.Normal(list)
            }
        }

    /** 查询参数四元组 */
    private data class QueryParams(
        val currentTab: SquadTab,
        val query: String,
        val filter: SquadFilter,
        val sort: SquadSortOption
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5000L

        /**
         * 创建 [SquadViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档与经理俱乐部 ID。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val saveManager = SaveManager.getInstance(app)
                    val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1
                    // managerClubId 由 SaveInfo 提供，此处同步获取默认值 1
                    val clubId = runCatching {
                        kotlinx.coroutines.runBlocking {
                            saveManager.getCurrentSaveInfo()?.managerClubId ?: 1
                        }
                    }.getOrDefault(1)
                    return SquadViewModel(app, saveId, clubId) as T
                }
            }
    }
}
