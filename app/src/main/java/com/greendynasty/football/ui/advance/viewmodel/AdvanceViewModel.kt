package com.greendynasty.football.ui.advance.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.CheckpointManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.match.api.MatchSimulator
import com.greendynasty.football.simulation.active.ActiveScopeManager
import com.greendynasty.football.simulation.api.AdvanceResult
import com.greendynasty.football.simulation.api.DailyAdvanceScheduler
import com.greendynasty.football.simulation.config.ProgressionConfig
import com.greendynasty.football.simulation.daily.AiClubTask
import com.greendynasty.football.simulation.daily.ConditionTask
import com.greendynasty.football.simulation.daily.DailyTaskScheduler
import com.greendynasty.football.simulation.daily.HistoryEventTask
import com.greendynasty.football.simulation.daily.InjuryRecoveryTask
import com.greendynasty.football.simulation.daily.MatchCheckTask
import com.greendynasty.football.simulation.daily.MoraleTask
import com.greendynasty.football.simulation.daily.NewsTask
import com.greendynasty.football.simulation.daily.ScoutTaskProgress
import com.greendynasty.football.simulation.daily.TodoRefreshTask
import com.greendynasty.football.simulation.daily.TrainingTask
import com.greendynasty.football.simulation.daily.TransferOfferTask
import com.greendynasty.football.simulation.daily.YouthGrowthTask
import com.greendynasty.football.simulation.matchday.LeagueTableUpdater
import com.greendynasty.football.simulation.matchday.MatchDayExecutor
import com.greendynasty.football.simulation.matchday.TeamSheetBuilder
import com.greendynasty.football.simulation.monthly.MonthlyTaskScheduler
import com.greendynasty.football.simulation.perf.PerfLogger
import com.greendynasty.football.simulation.rollback.AdvanceRollback
import com.greendynasty.football.simulation.season.SeasonTaskScheduler
import com.greendynasty.football.simulation.stub.AiSchedulerStub
import com.greendynasty.football.simulation.stub.BoardServiceStub
import com.greendynasty.football.simulation.stub.ButterflyEventServiceStub
import com.greendynasty.football.simulation.stub.EconomyServiceStub
import com.greendynasty.football.simulation.stub.PlayerGrowthServiceStub
import com.greendynasty.football.simulation.stub.SeasonArchiverStub
import com.greendynasty.football.simulation.weekly.WeeklyTaskScheduler
import com.greendynasty.football.ui.advance.state.AdvanceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 推进页 ViewModel（T07 方案 §三）
 *
 * 持有 [DailyAdvanceScheduler] 实例，暴露 4 种推进模式：
 * 1. [advanceOneDay]：单日推进
 * 2. [advanceToNextMatch]：跳到下一场比赛
 * 3. [advanceToDate]：跳到指定日期
 * 4. [advanceToEndOfMonth]：休息到月底
 *
 * UI 状态：[AdvanceUiState]，推进期间禁用按钮。
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager / 推进组件
 */
class AdvanceViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)

    // ===== 推进组件（懒初始化，依赖 databaseManager） =====
    private val config = ProgressionConfig.DEFAULT
    private val activeScopeManager = ActiveScopeManager(config)
    private val perfLogger = PerfLogger(databaseManager)
    private val checkpointManager = CheckpointManager(app, databaseManager)
    private val rollbackService = AdvanceRollback(databaseManager, checkpointManager)

    // stub 服务（T08+ 接入后替换）
    private val aiSchedulerStub = AiSchedulerStub()
    private val butterflyServiceStub = ButterflyEventServiceStub()
    private val growthServiceStub = PlayerGrowthServiceStub()
    private val economyServiceStub = EconomyServiceStub()
    private val boardServiceStub = BoardServiceStub()
    private val seasonArchiverStub = SeasonArchiverStub()

    // 比赛日组件
    private val matchSimulator = MatchSimulator()
    private val teamSheetBuilder = TeamSheetBuilder(databaseManager)
    private val leagueTableUpdater = LeagueTableUpdater(databaseManager)
    private val injuryService = com.greendynasty.football.injury.repository.InjuryService(databaseManager)
    private val matchDayExecutor = MatchDayExecutor(
        databaseManager, matchSimulator, teamSheetBuilder, leagueTableUpdater, activeScopeManager,
        injuryService
    )

    // 13 项每日任务
    private val trainingTask = TrainingTask(databaseManager, activeScopeManager, config)
    private val conditionTask = ConditionTask(databaseManager, activeScopeManager, config)
    private val injuryRecoveryTask = InjuryRecoveryTask(
        databaseManager, activeScopeManager, config, injuryService
    )
    private val moraleTask = MoraleTask(databaseManager, activeScopeManager, config)
    private val scoutTaskProgress = ScoutTaskProgress(databaseManager)
    private val youthGrowthTask = YouthGrowthTask(databaseManager)
    private val transferOfferTask = TransferOfferTask(databaseManager)
    private val aiClubTask = AiClubTask(aiSchedulerStub, databaseManager)
    private val newsTask = NewsTask(databaseManager, config)
    private val matchCheckTask = MatchCheckTask(matchDayExecutor)
    private val historyEventTask = HistoryEventTask(databaseManager, butterflyServiceStub)
    private val todoRefreshTask = TodoRefreshTask(databaseManager)

    // 调度器
    private val dailyScheduler = DailyTaskScheduler(
        trainingTask, conditionTask, injuryRecoveryTask, moraleTask,
        scoutTaskProgress, youthGrowthTask, transferOfferTask, aiClubTask,
        newsTask, matchCheckTask, historyEventTask, todoRefreshTask
    )
    private val weeklyScheduler = WeeklyTaskScheduler(databaseManager, config)
    private val monthlyScheduler = MonthlyTaskScheduler(
        databaseManager, config, economyServiceStub, growthServiceStub, boardServiceStub
    )
    private val seasonScheduler = SeasonTaskScheduler(
        databaseManager, checkpointManager, config, seasonArchiverStub
    )

    // 推进入口
    private val advanceScheduler = DailyAdvanceScheduler(
        databaseManager, dailyScheduler, weeklyScheduler, monthlyScheduler,
        seasonScheduler, activeScopeManager, rollbackService, config, perfLogger
    )

    // ===== UI 状态 =====
    private val _uiState = MutableStateFlow<AdvanceUiState>(AdvanceUiState.Loading)
    val uiState: StateFlow<AdvanceUiState> = _uiState.asStateFlow()

    /** 操作结果提示 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        // 初始化：读取当前存档日期
        loadCurrentState()
    }

    // ==================== 公共推进方法 ====================

    /**
     * 单日推进
     */
    fun advanceOneDay() {
        launchAdvance { advanceScheduler.advanceOneDay() }
    }

    /**
     * 跳到下一场比赛
     */
    fun advanceToNextMatch() {
        launchAdvance { advanceScheduler.advanceToNextMatch() }
    }

    /**
     * 跳到指定日期
     */
    fun advanceToDate(targetDate: LocalDate) {
        launchAdvance { advanceScheduler.advanceToDate(targetDate) }
    }

    /**
     * 休息到月底
     */
    fun advanceToEndOfMonth() {
        launchAdvance { advanceScheduler.advanceToEndOfMonth() }
    }

    /** 消费操作消息 */
    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    // ==================== 内部方法 ====================

    /**
     * 加载当前存档状态（不推进，仅展示）
     */
    private fun loadCurrentState() {
        viewModelScope.launch {
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = AdvanceUiState.Error(
                        currentDate = LocalDate.now(),
                        message = "未加载存档"
                    )
                    return@launch
                }
                val currentDate = try {
                    LocalDate.parse(saveInfo.gameDate)
                } catch (e: Exception) {
                    LocalDate.now()
                }
                _uiState.value = AdvanceUiState.Ready(
                    currentDate = currentDate,
                    events = emptyList(),
                    matches = emptyList(),
                    news = emptyList(),
                    todos = emptyList(),
                    lastDurationMs = 0,
                    isAdvancing = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "加载存档状态失败: ${e.message}", e)
                _uiState.value = AdvanceUiState.Error(
                    currentDate = LocalDate.now(),
                    message = "加载存档失败：${e.message}"
                )
            }
        }
    }

    /**
     * 启动推进任务
     */
    private fun launchAdvance(action: suspend () -> AdvanceResult) {
        // 标记为推进中
        val currentState = _uiState.value
        if (currentState is AdvanceUiState.Ready) {
            _uiState.value = currentState.copy(isAdvancing = true)
        }

        viewModelScope.launch {
            try {
                val result = action()
                handleAdvanceResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "推进异常: ${e.message}", e)
                _uiState.value = AdvanceUiState.Error(
                    currentDate = (currentState as? AdvanceUiState.Ready)?.currentDate ?: LocalDate.now(),
                    message = "推进异常：${e.message}"
                )
                _actionMessage.value = "推进异常：${e.message}"
            }
        }
    }

    /**
     * 处理推进结果
     */
    private fun handleAdvanceResult(result: AdvanceResult) {
        if (result.rollbackReason != null) {
            _uiState.value = AdvanceUiState.Error(
                currentDate = result.newDate,
                message = result.rollbackReason
            )
            _actionMessage.value = "推进失败已回滚：${result.rollbackReason}"
        } else {
            _uiState.value = AdvanceUiState.Ready(
                currentDate = result.newDate,
                events = result.events,
                matches = result.matches,
                news = result.news,
                todos = result.todos,
                lastDurationMs = result.durationMs,
                isAdvancing = false
            )
            val matchCount = result.matches.size
            val eventCount = result.events.size
            _actionMessage.value = if (matchCount > 0) {
                "推进到 ${result.newDate}，$matchCount 场比赛，$eventCount 条事件"
            } else {
                "推进到 ${result.newDate}，$eventCount 条事件"
            }
        }
    }

    companion object {
        private const val TAG = "AdvanceViewModel"

        /**
         * 创建 [AdvanceViewModel] 工厂。
         */
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdvanceViewModel(app) as T
            }
        }
    }
}
