package com.greendynasty.football.dressingroom.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.dressingroom.model.LeaderRole
import com.greendynasty.football.dressingroom.repository.DressingRoomRepository
import com.greendynasty.football.dressingroom.ui.state.DressingRoomTab
import com.greendynasty.football.dressingroom.ui.state.DressingRoomUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T23 更衣室页 ViewModel（V0.2 + T23 任务要求 §二.6 + 实现方案 §六）。
 *
 * 持有 [DressingRoomRepository] 实例，暴露 4 个 Tab 数据：
 * 1. 氛围：4 档氛围 + 稳定指数 + 球队士气 + 化学反应（Flow 订阅）
 * 2. 士气：全员士气列表 + 不满球员列表（Flow 订阅）
 * 3. 领袖：队长 / 副队长 / 影响力球员列表（Flow 订阅）
 * 4. 事件：最近情绪事件列表（Flow 订阅）
 *
 * UI 状态：[DressingRoomUiState]，5 种完备状态（对齐 BoardUiState 模式）。
 *
 * 用户交互：
 * - [switchTab]：切换 Tab
 * - [recomputeChemistry]：手动重算化学反应（转会窗关闭后）
 * - [detectLeaders]：赛季初识别领袖
 * - [appointLeader]：手动任命领袖
 * - [revokeLeader]：撤销领袖
 * - [resolveEvent]：解决情绪事件
 * - [clearPlayerUnrest]：清除球员不满（谈话）
 * - [consumeMessage]：消费 Snackbar 消息
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager / DressingRoomRepository
 */
class DressingRoomViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)

    private val repository = DressingRoomRepository(databaseManager)

    /** UI 状态 */
    private val _uiState = MutableStateFlow<DressingRoomUiState>(DressingRoomUiState.Loading)
    val uiState: StateFlow<DressingRoomUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(DressingRoomTab.ATMOSPHERE)
    val currentTab: StateFlow<DressingRoomTab> = _currentTab.asStateFlow()

    init {
        loadDressingRoomOverview()
    }

    // ==================== 数据加载 ====================

    /**
     * 加载更衣室概览数据（首次进入页面时调用）。
     *
     * 流程：
     * 1. 从 SaveManager 获取当前存档上下文（saveId / clubId / seasonId / currentDate）
     * 2. 若未打开存档 → Locked
     * 3. 计算更衣室快照
     * 4. 订阅 4 个 Tab 的 Flow（自动刷新 UI）
     */
    fun loadDressingRoomOverview() {
        viewModelScope.launch {
            _uiState.value = DressingRoomUiState.Loading
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = DressingRoomUiState.Locked()
                    return@launch
                }

                val currentDate = runCatching {
                    LocalDate.parse(saveInfo.gameDate)
                }.getOrDefault(LocalDate.now())
                val clubId = saveInfo.managerClubId
                val seasonId = saveInfo.currentSeason
                val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1

                // 初始化 Normal 状态（先加载快照）
                val snapshot = try {
                    repository.getSnapshot(saveId, clubId, currentDate)
                } catch (_: Exception) { null }

                _uiState.value = DressingRoomUiState.Normal(
                    saveId = saveId,
                    clubId = clubId,
                    seasonId = seasonId,
                    currentDate = currentDate,
                    captain = snapshot?.captain,
                    leaders = snapshot?.leaders ?: emptyList(),
                    playerMorales = snapshot?.playerMorales ?: emptyList(),
                    unhappyPlayers = snapshot?.unhappyPlayers ?: emptyList(),
                    teamMorale = snapshot?.teamMorale ?: 50,
                    chemistryIndex = snapshot?.chemistryIndex ?: 0.5,
                    atmosphere = snapshot?.atmosphere,
                    recentEvents = snapshot?.recentEvents ?: emptyList()
                )

                // 订阅 4 个 Tab 的 Flow
                observePlayerMorales(saveId, clubId)
                observeLeaders(saveId, clubId)
                observeAtmosphereHistory(saveId, clubId)
                observeRecentEvents(saveId, clubId)
            } catch (e: Exception) {
                _uiState.value = DressingRoomUiState.Error("加载更衣室数据失败：${e.message}")
            }
        }
    }

    /** 订阅全员士气 Flow（自动刷新 UI）。 */
    private fun observePlayerMorales(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observePlayerMorales(saveId, clubId).collectLatest { morales ->
                    updateNormal {
                        val unhappy = morales.filter {
                            it.moraleLevel in listOf("LOW", "EXTREME_LOW")
                        }
                        val teamMorale = morales.map { it.morale }.takeIf { it.isNotEmpty() }
                            ?.average()?.toInt() ?: 50
                        it.copy(
                            playerMorales = morales,
                            unhappyPlayers = unhappy,
                            teamMorale = teamMorale
                        )
                    }
                }
            } catch (_: Exception) {
                // 静默失败：UI 显示空状态
            }
        }
    }

    /** 订阅活跃领袖 Flow。 */
    private fun observeLeaders(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeLeaders(saveId, clubId).collectLatest { leaders ->
                    updateNormal {
                        it.copy(
                            leaders = leaders,
                            captain = leaders.firstOrNull { l -> l.leaderRole == "CAPTAIN" }
                        )
                    }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅氛围历史 Flow（最近 12 个月，用于稳定指数趋势）。 */
    private fun observeAtmosphereHistory(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeAtmosphereHistory(saveId, clubId, 12).collectLatest { history ->
                    // 取最近一次快照更新当前氛围展示
                    val latest = history.firstOrNull()
                    updateNormal {
                        it.copy(
                            atmosphere = latest?.let { snap ->
                                com.greendynasty.football.dressingroom.model.AtmosphereEvaluation(
                                    level = com.greendynasty.football.dressingroom.model.AtmosphereLevel
                                        .valueOf(snap.atmosphereLevel),
                                    teamMorale = snap.teamMorale,
                                    chemistryIndex = snap.chemistryIndex,
                                    leaderInfluence = snap.leaderInfluence,
                                    unrestCount = snap.unrestCount,
                                    stabilityIndex = snap.stabilityIndex
                                )
                            } ?: it.atmosphere
                        )
                    }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅最近情绪事件 Flow。 */
    private fun observeRecentEvents(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeRecentEvents(saveId, clubId, 50).collectLatest { events ->
                    updateNormal { it.copy(recentEvents = events) }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    // ==================== 用户交互 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: DressingRoomTab) {
        _currentTab.value = tab
    }

    /**
     * 重算俱乐部化学反应（转会窗关闭 / 阵容大变动后调用）。
     */
    fun recomputeChemistry() {
        viewModelScope.launch {
            val normal = _uiState.value as? DressingRoomUiState.Normal ?: return@launch
            try {
                val count = repository.recomputeChemistry(
                    normal.saveId, normal.clubId, normal.currentDate
                )
                updateNormal {
                    it.copy(message = "已重算化学反应：$count 对球员")
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "重算化学反应失败：${e.message}") }
            }
        }
    }

    /**
     * 赛季初识别领袖（自动识别 + 持久化）。
     */
    fun detectLeaders() {
        viewModelScope.launch {
            val normal = _uiState.value as? DressingRoomUiState.Normal ?: return@launch
            try {
                val leaders = repository.detectLeaders(
                    normal.saveId, normal.clubId, normal.seasonId, normal.currentDate
                )
                updateNormal {
                    it.copy(
                        leaders = leaders,
                        captain = leaders.firstOrNull { l -> l.leaderRole == "CAPTAIN" },
                        message = "已识别 ${leaders.size} 名领袖"
                    )
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "识别领袖失败：${e.message}") }
            }
        }
    }

    /**
     * 手动任命领袖。
     */
    fun appointLeader(playerId: Int, role: LeaderRole) {
        viewModelScope.launch {
            val normal = _uiState.value as? DressingRoomUiState.Normal ?: return@launch
            try {
                val entity = repository.appointLeader(
                    normal.saveId, normal.clubId, playerId, role,
                    normal.seasonId, normal.currentDate
                )
                updateNormal {
                    it.copy(message = if (entity != null) "已任命 ${role.label}" else "任命失败：球员不存在")
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "任命领袖失败：${e.message}") }
            }
        }
    }

    /**
     * 撤销领袖任命。
     */
    fun revokeLeader(leaderId: Long, reason: String = "玩家撤销") {
        viewModelScope.launch {
            val normal = _uiState.value as? DressingRoomUiState.Normal ?: return@launch
            try {
                repository.revokeLeader(
                    normal.saveId, normal.clubId, leaderId, normal.currentDate, reason
                )
                updateNormal { it.copy(message = "已撤销领袖任命") }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "撤销领袖失败：${e.message}") }
            }
        }
    }

    /**
     * 解决情绪事件。
     */
    fun resolveEvent(eventId: Long, resolution: String) {
        viewModelScope.launch {
            val normal = _uiState.value as? DressingRoomUiState.Normal ?: return@launch
            try {
                repository.resolveEvent(eventId, resolution, normal.currentDate)
                updateNormal { it.copy(message = "已处理事件") }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "处理事件失败：${e.message}") }
            }
        }
    }

    /**
     * 清除球员不满（玩家发起谈话）。
     */
    fun clearPlayerUnrest(playerId: Int) {
        viewModelScope.launch {
            val normal = _uiState.value as? DressingRoomUiState.Normal ?: return@launch
            try {
                val ok = repository.clearUnrest(normal.saveId, playerId)
                updateNormal {
                    it.copy(message = if (ok) "已与球员谈话，不满已清除" else "未找到球员士气记录")
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "谈话失败：${e.message}") }
            }
        }
    }

    /** 消费消息（避免 Snackbar 重复显示）。 */
    fun consumeMessage() {
        updateNormal { it.copy(message = null) }
    }

    // ==================== 内部工具 ====================

    /** 安全更新 Normal 状态（非 Normal 状态时忽略）。 */
    private fun updateNormal(transform: (DressingRoomUiState.Normal) -> DressingRoomUiState.Normal) {
        val current = _uiState.value
        if (current is DressingRoomUiState.Normal) {
            _uiState.value = transform(current)
        }
    }

    companion object {
        /**
         * 创建 [DressingRoomViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档上下文。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DressingRoomViewModel(app) as T
                }
            }
    }
}
