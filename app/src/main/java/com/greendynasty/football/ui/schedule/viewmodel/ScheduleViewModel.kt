package com.greendynasty.football.ui.schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.ui.schedule.data.ScheduleRepository
import com.greendynasty.football.ui.schedule.model.ScheduleConfig
import com.greendynasty.football.ui.schedule.model.ScheduleTab
import com.greendynasty.football.ui.schedule.model.StandingViewType
import com.greendynasty.football.ui.schedule.ui.state.ScheduleUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 赛程页 ViewModel
 *
 * 持有当前 Tab / 积分榜视图类型 / 当前轮次选择，并通过 [ScheduleRepository] 响应式拉取数据，
 * 暴露 [ScheduleUiState]。
 *
 * 核心交互：
 * - 3 Tab 切换（我的赛程 / 联赛 / 杯赛）
 * - 积分榜主/客/总视图切换
 * - 生成联赛/杯赛赛程（开发/演示入口，正式流程由 T07 调用）
 * - 选择轮次（联赛赛程按轮次过滤展示）
 *
 * @param app Application，用于初始化 [DatabaseManager] / [SaveManager]
 * @param saveId 当前存档 ID
 * @param playerClubId 经理当前俱乐部 ID
 * @param seasonId 当前赛季 ID
 * @param leagueCompetitionId 玩家所在联赛的赛事 ID
 * @param cupCompetitionId 国内杯赛赛事 ID
 */
class ScheduleViewModel(
    app: Application,
    saveId: Int,
    playerClubId: Int,
    private val seasonId: Int = 1,
    private val leagueCompetitionId: Int = 1,
    private val cupCompetitionId: Int = 100
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = ScheduleRepository(databaseManager, saveId, playerClubId)

    /** 当前 Tab */
    private val _selectedTab = MutableStateFlow(ScheduleTab.MY_SCHEDULE)
    val selectedTab: StateFlow<ScheduleTab> = _selectedTab.asStateFlow()

    /** 积分榜视图类型（overall / home / away） */
    private val _standingView = MutableStateFlow(StandingViewType.OVERALL)
    val standingView: StateFlow<StandingViewType> = _standingView.asStateFlow()

    /** 当前选中的联赛轮次（null = 全部） */
    private val _selectedRound = MutableStateFlow<Int?>(null)
    val selectedRound: StateFlow<Int?> = _selectedRound.asStateFlow()

    /** 操作结果提示 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /**
     * 赛程页 UI 状态
     *
     * 由 Tab + 积分榜视图 + 选中轮次三元组 flatMapLatest 派生。
     * Repository 内部已聚合 history.db 俱乐部名称 + save.db 比赛/积分/对阵。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ScheduleUiState> =
        combine(_selectedTab, _standingView, _selectedRound) { tab, view, round ->
            Triple(tab, view, round)
        }.flatMapLatest { (tab, _, round) ->
            when (tab) {
                ScheduleTab.MY_SCHEDULE -> repository.observePlayerMatches(seasonId)
                    .mapMatchesToUiState(playerClubId)
                ScheduleTab.LEAGUE -> combine(
                    repository.observeLeagueMatchesGroupedByRound(seasonId, leagueCompetitionId),
                    repository.observeLeagueTable(seasonId, leagueCompetitionId, emptyList())
                ) { matchesByRound, table ->
                    ScheduleUiState.Ready(
                        myMatches = emptyList(),
                        leagueMatchesByRound = filterByRound(matchesByRound, round),
                        leagueTable = table,
                        playerClubId = playerClubId,
                        totalRounds = matchesByRound.keys.maxOrNull() ?: 0,
                        promotionZoneSize = ScheduleConfig.DEFAULT.promotionZoneSize,
                        relegationZoneSize = ScheduleConfig.DEFAULT.relegationZoneSize
                    )
                }
                ScheduleTab.CUP -> repository.observeCupBracket(cupCompetitionId)
                    .mapCupStagesToUiState(playerClubId)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ScheduleUiState.Loading
        )

    // ==================== 公共交互方法 ====================

    /** 切换 Tab */
    fun onTabSelected(tab: ScheduleTab) {
        if (_selectedTab.value != tab) {
            _selectedTab.value = tab
        }
    }

    /** 切换积分榜视图类型 */
    fun onStandingViewChanged(view: StandingViewType) {
        _standingView.value = view
    }

    /** 选择联赛轮次（null = 全部） */
    fun onRoundSelected(round: Int?) {
        _selectedRound.value = round
    }

    /** 消费操作消息 */
    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    /**
     * 生成联赛赛程（演示/开发入口）
     *
     * 正式流程由 T07 每日推进在赛季初始化时调用 [ScheduleRepository.generateLeagueSchedule]。
     */
    fun generateLeagueSchedule(
        clubIds: List<Int>,
        seasonStart: LocalDate = LocalDate.of(2002, 8, 17)
    ) {
        viewModelScope.launch {
            val count = repository.generateLeagueSchedule(
                seasonId = seasonId,
                competitionId = leagueCompetitionId,
                clubIds = clubIds,
                seasonStart = seasonStart
            )
            _actionMessage.value = if (count > 0) {
                "联赛赛程已生成：$count 场比赛"
            } else {
                "联赛赛程生成失败"
            }
        }
    }

    /**
     * 生成杯赛对阵表（演示/开发入口）
     */
    fun generateCupBracket(
        participants: List<Int>,
        seedRanking: Map<Int, Int>,
        startDate: LocalDate = LocalDate.of(2002, 9, 1)
    ) {
        viewModelScope.launch {
            val (tieCount, matchCount) = repository.generateCupBracket(
                seasonId = seasonId,
                competitionId = cupCompetitionId,
                participants = participants,
                seedRanking = seedRanking,
                startDate = startDate
            )
            _actionMessage.value = if (tieCount > 0) {
                "杯赛对阵已生成：$tieCount 条对阵 / $matchCount 场比赛"
            } else {
                "杯赛对阵生成失败"
            }
        }
    }

    // ==================== 内部工具 ====================

    /** 按选中轮次过滤（null = 全部） */
    private fun filterByRound(
        matchesByRound: Map<Int, List<com.greendynasty.football.ui.schedule.model.MatchUi>>,
        round: Int?
    ): Map<Int, List<com.greendynasty.football.ui.schedule.model.MatchUi>> {
        if (round == null) return matchesByRound
        return matchesByRound.filterKeys { it == round }
    }

    /** 玩家赛程 Flow → UiState 映射 */
    private fun kotlinx.coroutines.flow.Flow<List<com.greendynasty.football.ui.schedule.model.MatchUi>>.mapMatchesToUiState(
        playerClubId: Int
    ): kotlinx.coroutines.flow.Flow<ScheduleUiState> = map { matches ->
        if (matches.isEmpty()) {
            ScheduleUiState.Empty()
        } else {
            ScheduleUiState.Ready(
                myMatches = matches,
                playerClubId = playerClubId,
                totalRounds = matches.maxOfOrNull { it.round } ?: 0
            )
        }
    }

    /** 杯赛 Flow → UiState 映射 */
    private fun kotlinx.coroutines.flow.Flow<List<com.greendynasty.football.ui.schedule.model.CupStageUi>>.mapCupStagesToUiState(
        playerClubId: Int
    ): kotlinx.coroutines.flow.Flow<ScheduleUiState> = map { stages ->
        if (stages.isEmpty()) {
            ScheduleUiState.Empty("杯赛对阵尚未生成")
        } else {
            ScheduleUiState.Ready(
                cupStages = stages,
                playerClubId = playerClubId
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5000L

        /**
         * 创建 [ScheduleViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档与经理俱乐部 ID。
         */
        fun factory(
            app: Application,
            seasonId: Int = 1,
            leagueCompetitionId: Int = 1,
            cupCompetitionId: Int = 100
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val saveManager = SaveManager.getInstance(app)
                val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1
                val clubId = runCatching {
                    kotlinx.coroutines.runBlocking {
                        saveManager.getCurrentSaveInfo()?.managerClubId ?: 1
                    }
                }.getOrDefault(1)
                return ScheduleViewModel(
                    app, saveId, clubId, seasonId, leagueCompetitionId, cupCompetitionId
                ) as T
            }
        }
    }
}
