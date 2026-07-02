package com.greendynasty.football.scouting.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.scouting.ScoutingService
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.model.BudgetLevel
import com.greendynasty.football.scouting.model.DispatchTaskRequest
import com.greendynasty.football.scouting.model.ScoutTaskType
import com.greendynasty.football.scouting.model.ScoutWithKnowledge
import com.greendynasty.football.scouting.repository.ScoutingRepository
import com.greendynasty.football.scouting.ui.state.DispatchFormState
import com.greendynasty.football.scouting.ui.state.ScoutingTab
import com.greendynasty.football.scouting.ui.state.ScoutingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T14 球探中心页 ViewModel（V0.2 08 §六 + T14 方案 §六）。
 *
 * 持有：
 * - [ScoutingUiState]：5 个 Tab 数据
 * - 当前 Tab（默认球探列表）
 * - 派遣弹窗表单状态
 *
 * 核心交互：
 * 1. 进入球探中心 → [loadScouts]
 * 2. 选择空闲球探 → [showDispatchDialog] → 填写表单 → [dispatchTask]
 * 3. 取消任务 → [cancelTask]
 * 4. 点击报告 → [selectReport] → 报告详情页
 *
 * @param app Application，用于初始化 [DatabaseManager]
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class ScoutingViewModel(
    app: Application,
    val saveId: Int,
    val clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val config = ScoutConfig.DEFAULT
    private val repository = ScoutingRepository(databaseManager, config)
    private val service = ScoutingService(databaseManager, config)

    /** UI 状态 */
    private val _uiState = MutableStateFlow(ScoutingUiState())
    val uiState: StateFlow<ScoutingUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(ScoutingTab.SCOUTS)
    val currentTab: StateFlow<ScoutingTab> = _currentTab.asStateFlow()

    /** 派遣弹窗显示状态 */
    private val _showDispatchDialog = MutableStateFlow(false)
    val showDispatchDialog: StateFlow<Boolean> = _showDispatchDialog.asStateFlow()

    /** 派遣弹窗表单 */
    private val _dispatchForm = MutableStateFlow(DispatchFormState())
    val dispatchForm: StateFlow<DispatchFormState> = _dispatchForm.asStateFlow()

    /** 当前游戏日期（V1 简化：用系统日期，V2 由 T07 推进上下文传入） */
    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    init {
        // 订阅活跃任务 Flow（自动刷新 UI）
        viewModelScope.launch {
            repository.observeActiveClubTasks(saveId, clubId).collectLatest { tasks ->
                _uiState.value = _uiState.value.copy(activeTasks = tasks)
            }
        }
        // 订阅所有任务 Flow
        viewModelScope.launch {
            repository.observeClubTasks(saveId, clubId).collectLatest { tasks ->
                _uiState.value = _uiState.value.copy(allTasks = tasks)
            }
        }
        // 订阅最新报告 Flow
        viewModelScope.launch {
            repository.observeClubReports(saveId, clubId).collectLatest { reports ->
                _uiState.value = _uiState.value.copy(recentReports = reports)
            }
        }
        // 订阅球探事件 Flow
        viewModelScope.launch {
            repository.observeRecentEvents(saveId).collectLatest { events ->
                _uiState.value = _uiState.value.copy(recentEvents = events)
            }
        }
        // 初次加载球探列表
        loadScouts()
    }

    // ==================== 球探管理 ====================

    /** 加载俱乐部所有球探（V0.2 08 §三.1）。 */
    fun loadScouts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val scouts = repository.listScouts(saveId, clubId)
                _uiState.value = _uiState.value.copy(scouts = scouts, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "加载球探失败：${e.message}"
                )
            }
        }
    }

    /** 雇佣球探（V0.2 08 §三.1）。 */
    fun hireScout(scoutId: Int) {
        viewModelScope.launch {
            val result = service.hireScout(saveId, clubId, scoutId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
            if (result.success) loadScouts()
        }
    }

    /** 解雇球探（V0.2 08 §三.1）。 */
    fun releaseScout(hiredId: Int) {
        viewModelScope.launch {
            service.releaseScout(saveId, hiredId)
            _uiState.value = _uiState.value.copy(message = "球探已解雇")
            loadScouts()
        }
    }

    // ==================== 任务派遣 ====================

    /** 打开派遣弹窗（针对某球探）。 */
    fun showDispatchDialog(scout: ScoutWithKnowledge) {
        _dispatchForm.value = DispatchFormState(
            scoutId = scout.scout.scoutId,
            scoutName = scout.scout.name
        )
        _showDispatchDialog.value = true
    }

    /** 关闭派遣弹窗。 */
    fun dismissDispatchDialog() {
        _showDispatchDialog.value = false
    }

    /** 更新派遣表单字段。 */
    fun updateDispatchForm(transform: (DispatchFormState) -> DispatchFormState) {
        _dispatchForm.value = transform(_dispatchForm.value)
    }

    /** 提交派遣任务（V0.2 08 §三.4）。 */
    fun dispatchTask() {
        val form = _dispatchForm.value
        if (form.scoutId <= 0) {
            _uiState.value = _uiState.value.copy(message = "请选择球探")
            return
        }
        viewModelScope.launch {
            val taskType = ScoutTaskType.values()[form.taskTypeIndex]
            val budgetLevel = BudgetLevel.values()[form.budgetLevelIndex]
            val request = DispatchTaskRequest(
                saveId = saveId,
                clubId = clubId,
                scoutId = form.scoutId,
                taskType = taskType,
                regionCode = form.regionCode,
                targetPosition = if (taskType == ScoutTaskType.POSITION_SEARCH)
                    form.targetPosition.ifBlank { null } else null,
                ageMin = form.ageMin,
                ageMax = form.ageMax,
                durationDays = form.durationDays,
                budgetLevel = budgetLevel,
                startDate = _currentDate.value,
                targetClubId = if (taskType == ScoutTaskType.CLUB_OBSERVATION && form.targetClubId > 0)
                    form.targetClubId else null,
                youthTournamentId = if (taskType == ScoutTaskType.YOUTH_TOURNAMENT)
                    com.greendynasty.football.scouting.model.YouthTournament.values()[form.youthTournamentIndex].id
                else null
            )
            val result = service.dispatchTask(request)
            _uiState.value = _uiState.value.copy(message = result.message)
            if (result.success) {
                _showDispatchDialog.value = false
                loadScouts()
            }
        }
    }

    /** 取消任务（V0.2 08 §三.4）。 */
    fun cancelTask(taskId: Int) {
        viewModelScope.launch {
            val result = service.cancelTask(saveId, taskId, _currentDate.value)
            _uiState.value = _uiState.value.copy(message = result.message)
            if (result.success) loadScouts()
        }
    }

    // ==================== 报告查看 ====================

    /** 加载报告详情（V0.2 08 §四）。 */
    fun selectReport(reportId: Int) {
        viewModelScope.launch {
            val detail = repository.getReportDetail(saveId, reportId)
            _uiState.value = _uiState.value.copy(selectedReportDetail = detail)
        }
    }

    /** 清除选中的报告详情（返回报告列表）。 */
    fun clearSelectedReport() {
        _uiState.value = _uiState.value.copy(selectedReportDetail = null)
    }

    /** 设置球探推荐等级（0-100）。 */
    fun setScoutRecommendation(reportId: Int, level: Int) {
        viewModelScope.launch {
            service.setScoutRecommendation(saveId, reportId, level)
            // 重新加载报告详情
            selectReport(reportId)
        }
    }

    // ==================== 事件 ====================

    /** 标记事件为已读。 */
    fun markEventRead(eventId: Int) {
        viewModelScope.launch {
            repository.markEventRead(saveId, eventId)
        }
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: ScoutingTab) {
        _currentTab.value = tab
    }

    /** 消费消息（避免 Snackbar 重复显示）。 */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        /** ViewModel 工厂（注入 saveId / clubId）。 */
        fun factory(app: Application, saveId: Int, clubId: Int) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScoutingViewModel(app, saveId, clubId) as T
            }
        }
    }
}
