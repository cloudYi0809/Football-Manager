package com.greendynasty.football.board.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.ai.profile.generator.ClubProfileGenerator
import com.greendynasty.football.board.model.BudgetRequestType
import com.greendynasty.football.board.repository.BoardRepository
import com.greendynasty.football.board.ui.state.BoardTab
import com.greendynasty.football.board.ui.state.BoardUiState
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.economy.calculator.ClubFinancialPowerCalculator
import com.greendynasty.football.economy.calculator.FinancialHealthChecker
import com.greendynasty.football.economy.calculator.PlayerValueCalculator
import com.greendynasty.football.economy.calculator.WageCalculator
import com.greendynasty.football.economy.index.EconomyIndexService
import com.greendynasty.football.economy.league.LeagueEconomyService
import com.greendynasty.football.economy.repository.EconomyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T22 董事会页 ViewModel（V0.2 11 §四 + T22 方案 §六）。
 *
 * 持有 [BoardRepository] 实例，暴露 5 个 Tab 数据：
 * 1. 赛季目标：当前赛季 5 类目标 + 实时进度（Flow 订阅）
 * 2. 长期目标：3 年/5 年规划列表（Flow 订阅）
 * 3. 满意度：8 因子分项 + 历史曲线（Flow 订阅）
 * 4. 预算申请：申请记录 + 提交表单（Flow 订阅 + suspend 提交）
 * 5. 董事会评价：反馈文案 + 最近事件（Flow 订阅）
 *
 * UI 状态：[BoardUiState]，5 种完备状态（对齐 EconomyUiState 模式）。
 *
 * 用户交互：
 * - [switchTab]：切换 Tab
 * - [generateSeasonTargets]：赛季初生成目标（若不存在）
 * - [refreshProgress]：刷新赛季中目标进度
 * - [submitBudgetRequest]：提交预算申请
 * - [resolveEvent]：响应董事会事件
 * - [dismissWarning]：关闭解雇警告弹窗
 * - [consumeMessage]：消费 Snackbar 消息
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager / BoardRepository
 */
class BoardViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)

    // T17 经济服务组件（与 EconomyViewModel 一致的初始化方式）
    private val indexService = EconomyIndexService(databaseManager.economyIndexDao())
    private val leagueService = LeagueEconomyService(
        dao = databaseManager.economyIndexDao(),
        indexService = indexService
    )
    private val valueCalculator = PlayerValueCalculator(indexService, leagueService)
    private val wageCalculator = WageCalculator(indexService, leagueService)
    private val financialPowerCalculator = ClubFinancialPowerCalculator(leagueService)
    private val healthChecker = FinancialHealthChecker()

    private val economyRepository = EconomyRepository(
        databaseManager = databaseManager,
        indexService = indexService,
        leagueService = leagueService,
        valueCalculator = valueCalculator,
        wageCalculator = wageCalculator,
        financialPowerCalculator = financialPowerCalculator,
        healthChecker = healthChecker
    )

    private val clubProfileRepository = ClubProfileRepository(
        databaseManager, ClubProfileGenerator()
    )

    private val repository = BoardRepository(
        databaseManager = databaseManager,
        clubProfileRepository = clubProfileRepository,
        economyRepository = economyRepository
    )

    /** UI 状态 */
    private val _uiState = MutableStateFlow<BoardUiState>(BoardUiState.Loading)
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(BoardTab.SEASON_TARGET)
    val currentTab: StateFlow<BoardTab> = _currentTab.asStateFlow()

    init {
        loadBoardOverview()
    }

    // ==================== 数据加载 ====================

    /**
     * 加载董事会概览数据（首次进入页面时调用）。
     *
     * 流程：
     * 1. 从 SaveManager 获取当前存档上下文（saveId / clubId / seasonId / currentDate）
     * 2. 若未打开存档 → Locked
     * 3. 计算董事会期望摘要
     * 4. 订阅 5 个 Tab 的 Flow（自动刷新 UI）
     * 5. 加载最近反馈
     */
    fun loadBoardOverview() {
        viewModelScope.launch {
            _uiState.value = BoardUiState.Loading
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = BoardUiState.Locked()
                    return@launch
                }

                val currentDate = runCatching {
                    LocalDate.parse(saveInfo.gameDate)
                }.getOrDefault(LocalDate.now())
                val clubId = saveInfo.managerClubId
                val seasonId = saveInfo.currentSeason
                // saveId 取 currentSaveIdValue 的数字形式（与 EconomyViewModel 保持一致）
                val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1

                // 计算董事会期望摘要（失败不阻断 UI）
                val expectation = runCatching {
                    repository.computeExpectation(saveId, clubId, seasonId, currentDate)
                }.getOrNull()

                // 初始化 Normal 状态
                _uiState.value = BoardUiState.Normal(
                    saveId = saveId,
                    clubId = clubId,
                    seasonId = seasonId,
                    currentDate = currentDate,
                    expectation = expectation
                )

                // 订阅 5 个 Tab 的 Flow
                observeSeasonTarget(saveId, clubId, seasonId)
                observeLongTermGoals(saveId, clubId)
                observeSatisfaction(saveId, clubId)
                observeConfidence(saveId, clubId, seasonId)
                observeBudgetRequests(saveId, clubId)
                observeRecentEvents(saveId, clubId)

                // 加载最近反馈（基于当前满意度）
                loadFeedback(saveId, clubId, seasonId, currentDate)
            } catch (e: Exception) {
                _uiState.value = BoardUiState.Error("加载董事会数据失败：${e.message}")
            }
        }
    }

    /** 订阅赛季目标 Flow（自动刷新 UI）。 */
    private fun observeSeasonTarget(saveId: Int, clubId: Int, seasonId: Int) {
        viewModelScope.launch {
            try {
                repository.observeSeasonTarget(saveId, clubId, seasonId).collectLatest { target ->
                    updateNormal { it.copy(seasonTarget = target) }
                }
            } catch (_: Exception) {
                // 静默失败：UI 显示空状态
            }
        }
    }

    /** 订阅长期目标 Flow。 */
    private fun observeLongTermGoals(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeLongTermGoals(saveId, clubId).collectLatest { goals ->
                    updateNormal { it.copy(longTermGoals = goals) }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅满意度快照 Flow（最近 12 个月）。 */
    private fun observeSatisfaction(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeSatisfactionHistory(saveId, clubId, 12).collectLatest { history ->
                    updateNormal {
                        it.copy(
                            satisfactionHistory = history,
                            latestSatisfaction = history.firstOrNull()
                        )
                    }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅信心值 Flow。 */
    private fun observeConfidence(saveId: Int, clubId: Int, seasonId: Int) {
        viewModelScope.launch {
            try {
                repository.observeConfidence(saveId, clubId, seasonId).collectLatest { confidence ->
                    updateNormal { it.copy(confidence = confidence) }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅预算申请记录 Flow。 */
    private fun observeBudgetRequests(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeBudgetRequests(saveId, clubId).collectLatest { requests ->
                    updateNormal { it.copy(budgetRequests = requests) }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅最近董事会事件 Flow。 */
    private fun observeRecentEvents(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeRecentEvents(saveId, clubId, 50).collectLatest { events ->
                    updateNormal { it.copy(recentEvents = events) }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 加载最近反馈（基于当前满意度）。 */
    private fun loadFeedback(saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate) {
        viewModelScope.launch {
            try {
                val satisfaction = repository.getLatestSatisfaction(saveId, clubId)
                val feedback = satisfaction?.let {
                    repository.feedbackService.generateFeedback(it.overallSatisfaction)
                }
                updateNormal { it.copy(feedback = feedback) }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    // ==================== 用户交互 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: BoardTab) {
        _currentTab.value = tab
    }

    /**
     * 赛季初生成赛季目标（若当前赛季无目标）。
     */
    fun generateSeasonTargets() {
        viewModelScope.launch {
            val normal = _uiState.value as? BoardUiState.Normal ?: return@launch
            try {
                val target = repository.generateSeasonTargets(
                    normal.saveId, normal.clubId, normal.seasonId, normal.currentDate
                )
                updateNormal {
                    it.copy(message = if (target != null) "已生成本赛季董事会目标" else "目标已存在")
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "生成目标失败：${e.message}") }
            }
        }
    }

    /**
     * 刷新赛季中目标实时进度。
     */
    fun refreshProgress() {
        viewModelScope.launch {
            val normal = _uiState.value as? BoardUiState.Normal ?: return@launch
            try {
                val progress = repository.evaluateProgress(
                    normal.saveId, normal.clubId, normal.seasonId, normal.currentDate
                )
                updateNormal {
                    it.copy(
                        objectiveProgress = progress,
                        message = "已刷新目标进度"
                    )
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "刷新进度失败：${e.message}") }
            }
        }
    }

    /**
     * 提交预算申请。
     *
     * @param requestType 申请类型（6 类）
     * @param amount 申请金额
     * @param justification 申请理由
     */
    fun submitBudgetRequest(
        requestType: BudgetRequestType, amount: Int, justification: String
    ) {
        viewModelScope.launch {
            val normal = _uiState.value as? BoardUiState.Normal ?: return@launch
            try {
                val result = repository.submitBudgetRequest(
                    normal.saveId, normal.clubId, normal.currentDate,
                    requestType, amount, justification
                )
                updateNormal {
                    it.copy(message = result.boardResponse)
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "提交申请失败：${e.message}") }
            }
        }
    }

    /**
     * 响应董事会事件。
     *
     * @param eventId 事件 ID
     * @param response 玩家回应
     */
    fun resolveEvent(eventId: Int, response: String) {
        viewModelScope.launch {
            val normal = _uiState.value as? BoardUiState.Normal ?: return@launch
            try {
                repository.resolveEvent(eventId, response, normal.currentDate)
                updateNormal { it.copy(message = "已响应董事会事件") }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "响应事件失败：${e.message}") }
            }
        }
    }

    /** 关闭解雇警告弹窗。 */
    fun dismissWarning() {
        updateNormal { it.copy(dismissalWarning = null) }
    }

    /** 消费消息（避免 Snackbar 重复显示）。 */
    fun consumeMessage() {
        updateNormal { it.copy(message = null) }
    }

    // ==================== 内部工具 ====================

    /** 安全更新 Normal 状态（非 Normal 状态时忽略）。 */
    private fun updateNormal(transform: (BoardUiState.Normal) -> BoardUiState.Normal) {
        val current = _uiState.value
        if (current is BoardUiState.Normal) {
            _uiState.value = transform(current)
        }
    }

    companion object {
        /**
         * 创建 [BoardViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档上下文。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BoardViewModel(app) as T
                }
            }
    }
}
