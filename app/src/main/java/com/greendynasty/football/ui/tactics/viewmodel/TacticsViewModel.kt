package com.greendynasty.football.ui.tactics.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.ui.tactics.algorithm.RiskLevel
import com.greendynasty.football.ui.tactics.data.PlayerWithPosition
import com.greendynasty.football.ui.tactics.data.TacticsRepository
import com.greendynasty.football.ui.tactics.model.FormationDefinition
import com.greendynasty.football.ui.tactics.model.PlayerRole
import com.greendynasty.football.ui.tactics.model.PlayerSlot
import com.greendynasty.football.ui.tactics.model.TacticalParameters
import com.greendynasty.football.ui.tactics.model.TacticalSetup
import com.greendynasty.football.ui.tactics.ui.state.TacticsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 战术页 ViewModel（V0.1 03 §3 战术页 + T05 方案）。
 *
 * 持有当前战术设置 [TacticalSetup]，通过 [TacticsRepository] 读取球员列表，
 * 暴露 6 种完备 UI 状态（[TacticsUiState]）。
 *
 * 核心交互：
 * - 切换阵型（6 阵型）
 * - 切换战术风格（8 风格）
 * - 调整战术参数（节奏 / 压迫 / 防线 / 传球 / 心态）
 * - 分配球员角色（6 角色）
 * - 拖拽球员换位 / 替补拖入首发
 * - 实时计算战术熟练度
 *
 * @param app Application，用于初始化 [DatabaseManager] / [SaveManager]
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 */
class TacticsViewModel(
    app: Application,
    saveId: Int,
    clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = TacticsRepository(databaseManager, saveId, clubId)

    /** 当前战术设置（内存态，可被 UI 修改） */
    private val _setup = MutableStateFlow(TacticalSetup.DEFAULT)
    val setup: StateFlow<TacticalSetup> = _setup.asStateFlow()

    /** 战术熟练度（实时计算） */
    private val _proficiency = MutableStateFlow(50.0)
    val proficiency: StateFlow<Double> = _proficiency.asStateFlow()

    /** 操作结果提示 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 战术页 UI 状态。
     *
     * 由 (setup, availablePlayers) 派生，实时计算熟练度与风险等级。
     */
    val uiState: StateFlow<TacticsUiState> =
        combine(_setup, repository.getAvailablePlayers()) { setup, players ->
            StatePayload(setup, players)
        }.map { payload ->
            deriveState(payload.setup, payload.players)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TacticsUiState.Loading
        )

    init {
        // 初始化时加载战术设置
        loadTactics()
    }

    // ==================== 公共交互方法 ====================

    /** 加载战术设置（取首次快照，避免 Room 流挂起） */
    fun loadTactics() {
        viewModelScope.launch {
            val saved = repository.getCurrentTactics().first()
            _setup.value = saved
            recalculateProficiency(saved)
        }
    }

    /**
     * 切换阵型。
     * 切换后重新生成 11 个位置槽位，尽量保留已分配球员。
     */
    fun changeFormation(formation: Formation) {
        val current = _setup.value
        if (current.formation == formation) return

        val def = FormationDefinition.from(formation)
        val oldSlots = current.starting11.associateBy { it.slotId }

        val newSlots = def.positions.map { pos ->
            val old = oldSlots[pos.slotId]
            PlayerSlot(
                slotId = pos.slotId,
                playerId = old?.playerId,
                position = pos.position,
                roleLabel = pos.roleLabel,
                positionFitScore = old?.positionFitScore ?: 0
            )
        }
        val newSetup = current.copy(formation = formation, starting11 = newSlots)
        _setup.value = newSetup
        repository.updateSetup(newSetup)
        recalculateProficiency(newSetup)
    }

    /**
     * 切换战术风格。
     */
    fun changeStyle(style: TacticStyle) {
        val current = _setup.value
        if (current.style == style) return
        val newSetup = current.copy(style = style)
        _setup.value = newSetup
        repository.updateSetup(newSetup)
        recalculateProficiency(newSetup)
    }

    /**
     * 更新战术参数。
     */
    fun updateParameters(params: TacticalParameters) {
        val current = _setup.value
        if (current.parameters == params) return
        val newSetup = current.copy(parameters = params)
        _setup.value = newSetup
        repository.updateSetup(newSetup)
        recalculateProficiency(newSetup)
    }

    /**
     * 分配球员角色。
     *
     * @param role 角色类型
     * @param playerId 球员 ID，传 null 取消分配
     */
    fun assignRole(role: PlayerRole, playerId: Int?) {
        val current = _setup.value
        val newRoles = current.playerRoles.assign(role, playerId)
        val newSetup = current.copy(playerRoles = newRoles)
        _setup.value = newSetup
        repository.updateSetup(newSetup)
        _message.value = "${role.label}已${if (playerId != null) "分配" else "取消"}"
    }

    /**
     * 拖拽球员换位：将球员从 fromSlot 移到 toSlot。
     *
     * - 若 toSlot 已有球员，则交换两人
     * - 若 toSlot 为空，则直接移入
     */
    fun dragPlayer(fromSlotId: Int, toSlotId: Int) {
        if (fromSlotId == toSlotId) return
        val current = _setup.value
        val slots = current.starting11.toMutableList()
        val fromIndex = slots.indexOfFirst { it.slotId == fromSlotId }
        val toIndex = slots.indexOfFirst { it.slotId == toSlotId }
        if (fromIndex < 0 || toIndex < 0) return

        val fromSlot = slots[fromIndex]
        val toSlot = slots[toIndex]
        // 交换球员 ID 与适配度
        slots[fromIndex] = fromSlot.copy(
            playerId = toSlot.playerId,
            positionFitScore = toSlot.positionFitScore
        )
        slots[toIndex] = toSlot.copy(
            playerId = fromSlot.playerId,
            positionFitScore = fromSlot.positionFitScore
        )

        val newSetup = current.copy(starting11 = slots)
        _setup.value = newSetup
        repository.updateSetup(newSetup)
        recalculateProficiency(newSetup)
    }

    /**
     * 将替补球员拖入首发槽位。
     *
     * @param playerId 替补球员 ID
     * @param slotId 目标首发槽位 ID
     */
    fun dragPlayerToSlot(playerId: Int, slotId: Int) {
        val current = _setup.value
        val slots = current.starting11.toMutableList()
        val targetIndex = slots.indexOfFirst { it.slotId == slotId }
        if (targetIndex < 0) return

        val targetSlot = slots[targetIndex]
        // 若目标槽位已有球员，将其放回替补席
        val oldPlayerId = targetSlot.playerId
        val substitutes = current.substitutes.toMutableList()

        // 从替补席移除新球员
        val newPlayerSubIndex = substitutes.indexOfFirst { it.playerId == playerId }
        if (newPlayerSubIndex >= 0) {
            val subSlot = substitutes[newPlayerSubIndex]
            substitutes[newPlayerSubIndex] = subSlot.copy(playerId = oldPlayerId)
        } else if (oldPlayerId != null) {
            // 替补席无空位则追加
            substitutes.add(
                PlayerSlot(
                    slotId = current.substitutes.size + 100,
                    playerId = oldPlayerId,
                    position = targetSlot.position,
                    roleLabel = "替补"
                )
            )
        }

        slots[targetIndex] = targetSlot.copy(
            playerId = playerId,
            positionFitScore = calculateFitForSlot(playerId, targetSlot.position.name)
        )

        val newSetup = current.copy(starting11 = slots, substitutes = substitutes)
        _setup.value = newSetup
        repository.updateSetup(newSetup)
        recalculateProficiency(newSetup)
    }

    /** 保存战术设置 */
    fun saveTactics() {
        viewModelScope.launch {
            val ok = repository.saveTactics(_setup.value)
            _message.value = if (ok) "战术设置已保存" else "保存失败，请重试"
        }
    }

    /** 消费操作结果消息 */
    fun consumeMessage() {
        _message.value = null
    }

    // ==================== 内部方法 ====================

    /** 实时重新计算战术熟练度 */
    private fun recalculateProficiency(setup: TacticalSetup) {
        viewModelScope.launch {
            val players = try {
                repository.getAvailablePlayers().first()
            } catch (e: Exception) {
                emptyList()
            }
            val score = repository.calculateProficiencySync(setup, players)
            _proficiency.value = score
        }
    }

    /** 计算球员在某位置的适配度（同步，用于拖拽后即时更新） */
    private fun calculateFitForSlot(playerId: Int, position: String): Int {
        // V1 简化：返回固定值，详细计算在 Repository.calculatePositionFit
        return 75
    }

    /** 由 setup + players 派生 UI 状态 */
    private suspend fun deriveState(
        setup: TacticalSetup,
        players: List<PlayerWithPosition>
    ): TacticsUiState {
        if (players.isEmpty() && !repository.isSaveReady()) {
            return TacticsUiState.Locked("请先加载存档")
        }
        val score = repository.calculateProficiencySync(setup, players)
        _proficiency.value = score
        val riskLevel = repository.calculateRiskLevel(setup)

        return when {
            players.isEmpty() -> TacticsUiState.Empty()
            riskLevel == RiskLevel.HIGH -> TacticsUiState.Warning(
                message = "战术过于激进，下半场体能风险高",
                setup = setup,
                proficiency = score,
                riskLevel = riskLevel,
                availablePlayers = players
            )
            else -> TacticsUiState.Normal(
                setup = setup,
                proficiency = score,
                riskLevel = riskLevel,
                availablePlayers = players,
                proficiencyHint = setup.parameters.proficiencyHint
            )
        }
    }

    /** 内部状态载荷 */
    private data class StatePayload(
        val setup: TacticalSetup,
        val players: List<PlayerWithPosition>
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5000L

        /**
         * 创建 [TacticsViewModel] 工厂。
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
                    return TacticsViewModel(app, saveId, clubId) as T
                }
            }
    }
}
