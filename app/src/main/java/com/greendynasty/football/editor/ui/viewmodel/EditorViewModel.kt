package com.greendynasty.football.editor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.editor.club.ClubEditor
import com.greendynasty.football.editor.match.MatchEditor
import com.greendynasty.football.editor.model.EditReport
import com.greendynasty.football.editor.model.EditTargetTable
import com.greendynasty.football.editor.model.EditableClub
import com.greendynasty.football.editor.model.EditableMatch
import com.greendynasty.football.editor.model.EditablePlayer
import com.greendynasty.football.editor.player.PlayerEditor
import com.greendynasty.football.editor.repository.EditorRepository
import com.greendynasty.football.editor.ui.state.EditorTab
import com.greendynasty.football.editor.ui.state.EditorUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * T25 数据编辑器 ViewModel（V0.2 + T25 任务要求 + 实现方案 §六）。
 *
 * 持有 [PlayerEditor] / [ClubEditor] / [MatchEditor] / [EditorRepository]，
 * 暴露 4 个 Tab 数据：
 * 1. 球员编辑：球员列表 + 选中球员草稿 + 基础信息/属性/CA-PA 修改
 * 2. 俱乐部编辑：俱乐部列表 + 选中俱乐部草稿 + 声望/财政/设施修改
 * 3. 赛程编辑：比赛列表 + 选中比赛草稿 + 日期/对阵/比分修改
 * 4. 变更历史：操作历史 + 撤销/重做
 *
 * UI 状态：[EditorUiState]，5 种完备状态（对齐 MediaUiState 模式）。
 *
 * @param app Application，用于初始化 DatabaseManager
 */
class EditorViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)

    /** history.db 实例（可能未初始化，懒加载） */
    private val historyDb by lazy { databaseManager.getHistoryDatabase() }

    private val playerEditor: PlayerEditor by lazy { PlayerEditor(historyDb) }
    private val clubEditor: ClubEditor by lazy { ClubEditor(historyDb) }
    private val matchEditor: MatchEditor by lazy { MatchEditor(historyDb) }
    private val repository: EditorRepository by lazy { EditorRepository(historyDb) }

    /** UI 状态 */
    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(EditorTab.PLAYER)
    val currentTab: StateFlow<EditorTab> = _currentTab.asStateFlow()

    init {
        loadOverview()
    }

    // ==================== 数据加载 ====================

    /**
     * 加载编辑器概览数据（首次进入页面时调用）。
     *
     * 流程：
     * 1. 获取 history.db（未初始化 → Locked）
     * 2. 加载球员/俱乐部/比赛列表（首屏只加载有限条数，避免内存压力）
     * 3. 加载变更历史
     */
    fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                val historyDb = runCatching { databaseManager.getHistoryDatabase() }.getOrNull()
                if (historyDb == null) {
                    _uiState.value = EditorUiState.Locked()
                    return@launch
                }

                // 加载列表（首屏各 50 条，避免一次加载过多）
                val players = historyDb.playerDao().count()
                val clubs = historyDb.clubDao().count()
                val matches = historyDb.matchDao().count()
                if (players == 0 && clubs == 0 && matches == 0) {
                    _uiState.value = EditorUiState.Empty("history.db 暂无数据")
                    return@launch
                }

                // 通过 Flow 首次获取快照
                val playerList = takeFirst(historyDb.playerDao().getAllPlayers(), 50)
                val clubList = takeFirst(historyDb.clubDao().getAllClubs(), 50)
                val matchList = takeFirst(historyDb.matchDao().getMatchesBySeason(1), 50)

                _uiState.value = EditorUiState.Normal(
                    players = playerList,
                    clubs = clubList,
                    matches = matchList,
                    history = repository.getHistory(),
                    undoableCount = repository.getUndoableCount(),
                    redoableCount = repository.getRedoableCount()
                )
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error("加载数据失败：${e.message}")
            }
        }
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: EditorTab) {
        _currentTab.value = tab
    }

    // ==================== 球员编辑 ====================

    /** 选中球员进行编辑（加载草稿态）。 */
    fun selectPlayer(playerId: Int) {
        viewModelScope.launch {
            try {
                val editable = playerEditor.loadEditable(playerId)
                updateNormal { it.copy(currentEditablePlayer = editable) }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "加载球员失败：${e.message}") }
            }
        }
    }

    /** 创建新球员草稿。 */
    fun createNewPlayer(newPlayerId: Int) {
        viewModelScope.launch {
            val editable = playerEditor.createNew(newPlayerId)
            updateNormal { it.copy(currentEditablePlayer = editable) }
        }
    }

    /** 修改球员基础信息字段。 */
    fun updatePlayerBasicInfo(field: String, value: Any?) {
        updateNormal { state ->
            val editable = state.currentEditablePlayer ?: return@updateNormal state
            state.copy(currentEditablePlayer = playerEditor.updateBasicInfo(editable, field, value))
        }
    }

    /** 修改球员属性字段。 */
    fun updatePlayerAttribute(seasonId: Int, field: String, value: Int) {
        updateNormal { state ->
            val editable = state.currentEditablePlayer ?: return@updateNormal state
            state.copy(currentEditablePlayer = playerEditor.updateAttribute(editable, seasonId, field, value))
        }
    }

    /** 标记当前球员草稿为待删除。 */
    fun markPlayerDeleted() {
        updateNormal { state ->
            val editable = state.currentEditablePlayer ?: return@updateNormal state
            state.copy(currentEditablePlayer = playerEditor.markDeleted(editable))
        }
    }

    /** 保存当前球员草稿到 history.db。 */
    fun savePlayer() {
        viewModelScope.launch {
            val editable = (_uiState.value as? EditorUiState.Normal)?.currentEditablePlayer
                ?: return@launch
            val report = repository.save(editable, EditTargetTable.PLAYER)
            handleReport(report, "球员")
            if (report.success) {
                // 保存成功后刷新列表并清空草稿
                refreshPlayers()
                updateNormal { it.copy(currentEditablePlayer = null) }
            }
        }
    }

    /** 取消编辑球员（丢弃草稿）。 */
    fun cancelPlayerEdit() {
        updateNormal { it.copy(currentEditablePlayer = null) }
    }

    // ==================== 俱乐部编辑 ====================

    /** 选中俱乐部进行编辑。 */
    fun selectClub(clubId: Int) {
        viewModelScope.launch {
            try {
                val editable = clubEditor.loadEditable(clubId)
                updateNormal { it.copy(currentEditableClub = editable) }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "加载俱乐部失败：${e.message}") }
            }
        }
    }

    /** 创建新俱乐部草稿。 */
    fun createNewClub(newClubId: Int) {
        viewModelScope.launch {
            val editable = clubEditor.createNew(newClubId)
            updateNormal { it.copy(currentEditableClub = editable) }
        }
    }

    /** 修改俱乐部基础信息字段。 */
    fun updateClubBasicInfo(field: String, value: Any?) {
        updateNormal { state ->
            val editable = state.currentEditableClub ?: return@updateNormal state
            state.copy(currentEditableClub = clubEditor.updateBasicInfo(editable, field, value))
        }
    }

    /** 修改俱乐部声望。 */
    fun updateClubReputation(reputation: Int) {
        updateNormal { state ->
            val editable = state.currentEditableClub ?: return@updateNormal state
            state.copy(currentEditableClub = clubEditor.updateReputation(editable, reputation))
        }
    }

    /** 修改俱乐部财政等级。 */
    fun updateClubFinance(financeLevel: Int) {
        updateNormal { state ->
            val editable = state.currentEditableClub ?: return@updateNormal state
            state.copy(currentEditableClub = clubEditor.updateFinance(editable, financeLevel))
        }
    }

    /** 修改俱乐部设施等级。 */
    fun updateClubFacility(facilityType: com.greendynasty.football.editor.club.FacilityType, level: Int) {
        updateNormal { state ->
            val editable = state.currentEditableClub ?: return@updateNormal state
            state.copy(currentEditableClub = clubEditor.updateFacility(editable, facilityType, level))
        }
    }

    /** 标记当前俱乐部草稿为待删除。 */
    fun markClubDeleted() {
        updateNormal { state ->
            val editable = state.currentEditableClub ?: return@updateNormal state
            state.copy(currentEditableClub = clubEditor.markDeleted(editable))
        }
    }

    /** 保存当前俱乐部草稿。 */
    fun saveClub() {
        viewModelScope.launch {
            val editable = (_uiState.value as? EditorUiState.Normal)?.currentEditableClub
                ?: return@launch
            val report = repository.save(editable, EditTargetTable.CLUB)
            handleReport(report, "俱乐部")
            if (report.success) {
                refreshClubs()
                updateNormal { it.copy(currentEditableClub = null) }
            }
        }
    }

    /** 取消编辑俱乐部。 */
    fun cancelClubEdit() {
        updateNormal { it.copy(currentEditableClub = null) }
    }

    // ==================== 赛程编辑 ====================

    /** 选中比赛进行编辑。 */
    fun selectMatch(matchId: Int) {
        viewModelScope.launch {
            try {
                val editable = matchEditor.loadEditable(matchId)
                updateNormal { it.copy(currentEditableMatch = editable) }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "加载比赛失败：${e.message}") }
            }
        }
    }

    /** 创建新比赛草稿。 */
    fun createNewMatch(seasonId: Int, competitionId: Int) {
        viewModelScope.launch {
            val editable = matchEditor.createNew(seasonId, competitionId)
            updateNormal { it.copy(currentEditableMatch = editable) }
        }
    }

    /** 修改比赛字段。 */
    fun updateMatchInfo(field: String, value: Any?) {
        updateNormal { state ->
            val editable = state.currentEditableMatch ?: return@updateNormal state
            state.copy(currentEditableMatch = matchEditor.updateMatchInfo(editable, field, value))
        }
    }

    /** 标记当前比赛草稿为待删除。 */
    fun markMatchDeleted() {
        updateNormal { state ->
            val editable = state.currentEditableMatch ?: return@updateNormal state
            state.copy(currentEditableMatch = matchEditor.markDeleted(editable))
        }
    }

    /** 保存当前比赛草稿。 */
    fun saveMatch() {
        viewModelScope.launch {
            val editable = (_uiState.value as? EditorUiState.Normal)?.currentEditableMatch
                ?: return@launch
            val report = repository.save(editable, EditTargetTable.MATCH)
            handleReport(report, "比赛")
            if (report.success) {
                refreshMatches()
                updateNormal { it.copy(currentEditableMatch = null) }
            }
        }
    }

    /** 取消编辑比赛。 */
    fun cancelMatchEdit() {
        updateNormal { it.copy(currentEditableMatch = null) }
    }

    // ==================== 撤销/重做 ====================

    /** 撤销最近一次操作。 */
    fun undo() {
        viewModelScope.launch {
            val report = repository.undo()
            if (report.success) {
                refreshHistory()
                refreshPlayers()
                refreshClubs()
                refreshMatches()
                updateNormal { it.copy(message = "已撤销：${report.summary()}") }
            } else {
                updateNormal { it.copy(message = report.errors.firstOrNull() ?: "撤销失败") }
            }
        }
    }

    /** 重做最近一次撤销。 */
    fun redo() {
        viewModelScope.launch {
            val report = repository.redo()
            if (report.success) {
                refreshHistory()
                refreshPlayers()
                refreshClubs()
                refreshMatches()
                updateNormal { it.copy(message = "已重做：${report.summary()}") }
            } else {
                updateNormal { it.copy(message = report.errors.firstOrNull() ?: "重做失败") }
            }
        }
    }

    /** 消费 Snackbar 消息。 */
    fun consumeMessage() {
        updateNormal { it.copy(message = null) }
    }

    // ==================== 内部工具 ====================

    /** 处理保存结果报告（成功/失败都更新 message）。 */
    private fun handleReport(report: EditReport, target: String) {
        updateNormal { state ->
            val msg = if (report.success) {
                "$target 保存成功：${report.summary()}"
            } else {
                "$target 保存失败：${report.errors.joinToString("; ")}"
            }
            state.copy(
                lastReport = msg,
                message = msg,
                history = repository.getHistory(),
                undoableCount = repository.getUndoableCount(),
                redoableCount = repository.getRedoableCount()
            )
        }
    }

    /** 刷新球员列表。 */
    private suspend fun refreshPlayers() {
        val list = takeFirst(historyDb.playerDao().getAllPlayers(), 50)
        updateNormal { it.copy(players = list) }
    }

    /** 刷新俱乐部列表。 */
    private suspend fun refreshClubs() {
        val list = takeFirst(historyDb.clubDao().getAllClubs(), 50)
        updateNormal { it.copy(clubs = list) }
    }

    /** 刷新比赛列表。 */
    private suspend fun refreshMatches() {
        val list = takeFirst(historyDb.matchDao().getMatchesBySeason(1), 50)
        updateNormal { it.copy(matches = list) }
    }

    /** 刷新变更历史。 */
    private fun refreshHistory() {
        updateNormal {
            it.copy(
                history = repository.getHistory(),
                undoableCount = repository.getUndoableCount(),
                redoableCount = repository.getRedoableCount()
            )
        }
    }

    /** 安全更新 Normal 状态（非 Normal 状态时忽略）。 */
    private fun updateNormal(transform: (EditorUiState.Normal) -> EditorUiState.Normal) {
        val current = _uiState.value
        if (current is EditorUiState.Normal) {
            _uiState.value = transform(current)
        }
    }

    /**
     * 从 Flow 取首批 N 条数据（阻塞式首帧快照）。
     *
     * 编辑器首屏只需要展示列表前 50 条，避免一次加载全部数据。
     */
    private suspend fun <T> takeFirst(flow: kotlinx.coroutines.flow.Flow<List<T>>, limit: Int): List<T> {
        return flow.first().take(limit)
    }

    companion object {
        /**
         * 创建 [EditorViewModel] 工厂。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EditorViewModel(app) as T
                }
            }
    }
}
