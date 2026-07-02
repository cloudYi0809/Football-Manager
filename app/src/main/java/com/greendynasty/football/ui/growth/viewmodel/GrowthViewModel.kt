package com.greendynasty.football.ui.growth.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.growth.model.GrowthEventEntity
import com.greendynasty.football.growth.model.PlayerGrowthSummary
import com.greendynasty.football.growth.repository.GrowthMonthlyService
import com.greendynasty.football.ui.growth.ui.state.GrowthEventDisplay
import com.greendynasty.football.ui.growth.ui.state.GrowthUiState
import com.greendynasty.football.ui.growth.ui.state.PlayerGrowthDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 成长中心页 ViewModel（T09 成长月结模块 UI 入口）
 *
 * 持有 [GrowthMonthlyService] 实例，暴露：
 * 1. 上次月结报告（Top growers / Top decliners / 处理统计）
 * 2. 俱乐部近期成长事件流（6 类事件，含未读提醒）
 * 3. 单球员成长详情（成长曲线 + 事件流，供 T04 阵容页跳转）
 * 4. 玩家操作：标记事件已读
 *
 * UI 状态：[GrowthUiState]，6 种完备状态。
 *
 * 注意：月结触发由 T07 MonthlyTaskScheduler 自动调用，本 ViewModel 不主动触发月结。
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class GrowthViewModel(
    app: Application,
    private val saveId: Int,
    private val clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)
    private val growthService = GrowthMonthlyService(databaseManager)

    /** 当前游戏日期 */
    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    /** UI 状态 */
    private val _uiState = MutableStateFlow<GrowthUiState>(GrowthUiState.Loading)
    val uiState: StateFlow<GrowthUiState> = _uiState.asStateFlow()

    /** 操作结果提示 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        loadGrowthCenter()
    }

    // ==================== 数据加载 ====================

    /** 加载成长中心数据（上次月结报告 + 近期事件） */
    fun loadGrowthCenter() {
        viewModelScope.launch {
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = GrowthUiState.Locked()
                    return@launch
                }
                _currentDate.value = runCatching {
                    LocalDate.parse(saveInfo.gameDate)
                }.getOrDefault(LocalDate.now())

                refreshUiState()
            } catch (e: Exception) {
                _uiState.value = GrowthUiState.Error("加载成长中心失败：${e.message}")
            }
        }
    }

    /** 刷新 UI 状态（操作后或月结后调用） */
    private suspend fun refreshUiState() {
        val recentEvents = runCatching {
            growthService.getRecentClubEvents(saveId, clubId, limit = 20)
        }.getOrDefault(emptyList())

        // 取本月月结报告（如已生成）
        val today = _currentDate.value.toString()
        val todaySnapshots = runCatching {
            growthService.getClubMonthlySnapshots(saveId, clubId, today)
        }.getOrDefault(emptyList())

        val topGrowers = todaySnapshots
            .filter { it.caDelta > 0 }
            .sortedByDescending { it.caDelta }
            .take(5)
            .map { snap ->
                PlayerGrowthSummary(
                    playerId = snap.playerId,
                    playerName = "球员${snap.playerId}", // V1 简化，实际应查 history.player
                    age = snap.age,
                    position = "CM",
                    caBefore = snap.caBefore,
                    caAfter = snap.caAfter,
                    caDelta = snap.caDelta,
                    realizationScore = snap.realizationScore,
                    rangeTier = com.greendynasty.football.growth.model.GrowthRangeTier
                        .valueOf(snap.rangeTier),
                    primaryFactor = "成长之星"
                )
            }

        val topDecliners = todaySnapshots
            .filter { it.caDelta < 0 }
            .sortedBy { it.caDelta }
            .take(5)
            .map { snap ->
                PlayerGrowthSummary(
                    playerId = snap.playerId,
                    playerName = "球员${snap.playerId}",
                    age = snap.age,
                    position = "CM",
                    caBefore = snap.caBefore,
                    caAfter = snap.caAfter,
                    caDelta = snap.caDelta,
                    realizationScore = snap.realizationScore,
                    rangeTier = com.greendynasty.football.growth.model.GrowthRangeTier
                        .valueOf(snap.rangeTier),
                    primaryFactor = "状态下滑"
                )
            }

        val hasCritical = recentEvents.any { it.severity == "CRITICAL" }
        _uiState.value = when {
            todaySnapshots.isEmpty() && recentEvents.isEmpty() ->
                GrowthUiState.Empty()
            hasCritical ->
                GrowthUiState.Warning(
                    message = "存在严重成长事件需关注",
                    lastResult = null,
                    topGrowers = topGrowers,
                    topDecliners = topDecliners,
                    recentEvents = recentEvents
                )
            else ->
                GrowthUiState.Normal(
                    lastResult = null,
                    topGrowers = topGrowers,
                    topDecliners = topDecliners,
                    recentEvents = recentEvents
                )
        }
    }

    // ==================== 球员详情（T04 跳转） ====================

    /** 加载球员成长详情（成长曲线 + 事件流） */
    suspend fun getPlayerGrowthDetail(playerId: Int): PlayerGrowthDetail? {
        return runCatching {
            val snapshots = growthService.getPlayerSnapshots(saveId, playerId)
            // V1 简化：UI 主动拉取最新事件列表（不订阅 Flow，避免页面退出时泄漏）
            val recentEvents = databaseManager.growthEventDao().getByPlayer(saveId, playerId)
            val playerName = databaseManager.historyPlayerDao().getPlayer(playerId)?.displayName
                ?: "球员$playerId"
            PlayerGrowthDetail(
                playerId = playerId,
                playerName = playerName,
                snapshots = snapshots,
                events = recentEvents
            )
        }.getOrNull()
    }

    // ==================== 玩家操作 ====================

    /** 标记事件已读 */
    fun markEventsRead(ids: List<Int>) {
        viewModelScope.launch {
            val count = growthService.markEventsRead(ids)
            if (count > 0) {
                _actionMessage.value = "已标记 $count 条事件为已读"
                refreshUiState()
            }
        }
    }

    /** 消费操作消息 */
    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    // ==================== 内部工具 ====================

    /** 构造成长事件展示模型 */
    private suspend fun buildEventDisplay(event: GrowthEventEntity): GrowthEventDisplay {
        val playerName = runCatching {
            databaseManager.historyPlayerDao().getPlayer(event.playerId)?.displayName
                ?: "球员${event.playerId}"
        }.getOrDefault("球员${event.playerId}")

        val severityName = when (event.severity) {
            "INFO" -> "提示"
            "WARN" -> "警告"
            "CRITICAL" -> "严重"
            else -> event.severity
        }

        val typeName = when (event.eventType) {
            "BREAKTHROUGH_GROWTH" -> "突破性成长"
            "GROWTH_STAGNATION" -> "成长停滞"
            "POTENTIAL_FULFILLED" -> "潜力兑现"
            "EARLY_DECLINE" -> "早衰警告"
            "ATTITUDE_DETERIORATION" -> "训练态度恶化"
            "MENTOR_POSITIVE" -> "导师正向影响"
            else -> event.eventType
        }

        return GrowthEventDisplay(
            event = event,
            playerName = playerName,
            severityDisplayName = severityName,
            typeDisplayName = typeName
        )
    }

    companion object {
        private const val TAG = "GrowthViewModel"

        /**
         * 创建 [GrowthViewModel] 工厂。
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
                    return GrowthViewModel(app, saveId, clubId) as T
                }
            }
    }
}
