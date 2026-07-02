package com.greendynasty.football.economy.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.economy.calculator.ClubFinancialPowerCalculator
import com.greendynasty.football.economy.calculator.FinancialHealthChecker
import com.greendynasty.football.economy.calculator.PlayerValueCalculator
import com.greendynasty.football.economy.calculator.WageCalculator
import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.index.EconomyIndexService
import com.greendynasty.football.economy.league.LeagueEconomyService
import com.greendynasty.football.economy.repository.EconomyRepository
import com.greendynasty.football.economy.ui.state.EconomyUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T17 经济概览页 ViewModel（UI 入口）。
 *
 * 持有 [EconomyRepository] 实例，暴露：
 * 1. 当前经济指数快照（global_index / transfer_fee_index / wage_index / commercial_index）
 * 2. 通胀趋势（1992 → 当前年份，用于趋势图）
 * 3. 9 大联赛商业系数快照（按系数降序）
 * 4. 玩家俱乐部财政健康报告（4 档预警）
 *
 * UI 状态：[EconomyUiState]，5 种完备状态（对齐 GrowthUiState 模式）。
 *
 * 注意：年度通胀更新由 T07 SeasonEndExecutor 调用 [EconomyRepository.ensureYearIndex]，
 * 本 ViewModel 仅负责展示，不主动触发年度更新。
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager
 */
class EconomyViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)

    // T17 经济服务组件初始化（依赖 DatabaseManager）
    private val indexService = EconomyIndexService(databaseManager.economyIndexDao())
    private val leagueService = LeagueEconomyService(
        dao = databaseManager.economyIndexDao(),
        indexService = indexService
    )
    private val valueCalculator = PlayerValueCalculator(indexService, leagueService)
    private val wageCalculator = WageCalculator(indexService, leagueService)
    private val financialPowerCalculator = ClubFinancialPowerCalculator(leagueService)
    private val healthChecker = FinancialHealthChecker()

    private val repository = EconomyRepository(
        databaseManager = databaseManager,
        indexService = indexService,
        leagueService = leagueService,
        valueCalculator = valueCalculator,
        wageCalculator = wageCalculator,
        financialPowerCalculator = financialPowerCalculator,
        healthChecker = healthChecker
    )

    /** UI 状态 */
    private val _uiState = MutableStateFlow<EconomyUiState>(EconomyUiState.Loading)
    val uiState: StateFlow<EconomyUiState> = _uiState.asStateFlow()

    init {
        loadEconomyOverview()
    }

    // ==================== 数据加载 ====================

    /** 加载经济概览数据 */
    fun loadEconomyOverview() {
        viewModelScope.launch {
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = EconomyUiState.Locked()
                    return@launch
                }

                val currentDate = runCatching {
                    LocalDate.parse(saveInfo.gameDate)
                }.getOrDefault(LocalDate.now())
                val currentYear = currentDate.year

                // 1. 当前经济指数
                val currentIndex = repository.getCurrentEconomyIndex(currentYear)

                // 2. 通胀趋势（1992 至当前年份）
                val trend = repository.getEconomyTrend(currentYear)

                // 3. 9 大联赛商业快照
                val leagueSnapshots = repository.getAllLeagueSnapshots(currentYear)

                // 4. 玩家俱乐部财政健康（可能失败，独立 try-catch）
                // V1 简化：saveId 取 currentSaveIdValue 的数字形式（与 GrowthViewModel 保持一致）
                val clubId = saveInfo.managerClubId
                val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1
                val clubFinancial = runCatching {
                    repository.buildFinancialState(saveId, clubId, currentYear)
                }.getOrNull()
                val healthReport = clubFinancial?.let { financial ->
                    runCatching { healthChecker.check(financial) }.getOrNull()
                }

                if (trend.isEmpty() && leagueSnapshots.isEmpty()) {
                    _uiState.value = EconomyUiState.Empty()
                    return@launch
                }

                _uiState.value = EconomyUiState.Normal(
                    currentDate = currentDate.toString(),
                    currentYear = currentYear,
                    currentIndex = currentIndex,
                    trend = trend,
                    leagueSnapshots = leagueSnapshots,
                    clubFinancial = clubFinancial,
                    healthReport = healthReport
                )
            } catch (e: Exception) {
                _uiState.value = EconomyUiState.Error("加载经济概览失败：${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "EconomyViewModel"

        /**
         * 创建 [EconomyViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档上下文。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EconomyViewModel(app) as T
                }
            }
    }
}
