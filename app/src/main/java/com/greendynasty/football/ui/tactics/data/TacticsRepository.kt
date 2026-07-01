package com.greendynasty.football.ui.tactics.data

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.PlayerAttributes
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.ui.tactics.algorithm.PositionFitChecker
import com.greendynasty.football.ui.tactics.algorithm.TacticalProficiencyCalculator
import com.greendynasty.football.ui.tactics.model.FormationDefinition
import com.greendynasty.football.ui.tactics.model.PlayerSlot
import com.greendynasty.football.ui.tactics.model.PositionFit
import com.greendynasty.football.ui.tactics.model.TacticalSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * 战术数据仓库（V0.1 03 §3 战术页 + T05 方案 §五）。
 *
 * 职责：
 * - 读取 / 保存战术设置（save.db）
 * - 提供可选球员列表（聚合 history.db + save.db）
 * - 计算位置适配度（委托 [PositionFitChecker]）
 * - 计算战术熟练度（委托 [TacticalProficiencyCalculator]）
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 *
 * @param databaseManager 三库管理入口
 * @param saveId 当前存档 ID（save_player_state.save_id 列值）
 * @param clubId 经理当前俱乐部 ID
 */
class TacticsRepository(
    private val databaseManager: DatabaseManager,
    private val saveId: Int = DEFAULT_SAVE_ID,
    private val clubId: Int = DEFAULT_CLUB_ID
) {

    private val positionFitChecker = PositionFitChecker()
    private val proficiencyCalculator = TacticalProficiencyCalculator(positionFitChecker)

    /** 内存中的战术设置缓存（V1 简化：未持久化到独立表，保留在内存） */
    private val currentSetup = kotlinx.coroutines.flow.MutableStateFlow(TacticalSetup.DEFAULT)

    /**
     * 获取当前战术设置（响应式）。
     *
     * V1 简化实现：战术设置暂存内存，未持久化到 save.db 独立表。
     * 后续 T05 扩展可接入 save_club_state.tactics JSON 字段。
     */
    fun getCurrentTactics(): Flow<TacticalSetup> = currentSetup
        .onStart {
            // 首次发射时尝试用首发球员填充槽位
            if (currentSetup.value.starting11.isEmpty()) {
                initializeStartingSlots(Formation.F433)
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * 保存战术设置。
     *
     * @param setup 战术设置
     * @return true 表示保存成功
     */
    suspend fun saveTactics(setup: TacticalSetup): Boolean = withContext(Dispatchers.IO) {
        try {
            currentSetup.value = setup
            Log.d(TAG, "战术设置已保存：阵型=${setup.formation}, 风格=${setup.style}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveTactics 失败", e)
            false
        }
    }

    /**
     * 获取可选球员列表（响应式）。
     *
     * 数据源：save.db 的 save_player_state（按俱乐部观察），
     * 聚合 history.db 的 player 基础信息。
     * 自动排除外租球员。
     */
    fun getAvailablePlayers(): Flow<List<PlayerWithPosition>> {
        if (!isSaveReady()) {
            return flow { emit(emptyList()) }
        }
        return try {
            val stateDao = databaseManager.savePlayerStateDao()
            val playerDao = databaseManager.historyPlayerDao()

            stateDao.observeByClub(saveId, clubId)
                .map { states ->
                    states.filter { it.loanClubId == null }
                        .mapNotNull { state ->
                            val player = runCatching { playerDao.getPlayer(state.playerId) }
                                .getOrNull() ?: return@mapNotNull null
                            PlayerWithPosition(
                                playerId = state.playerId,
                                name = player.displayName ?: player.realName,
                                position = player.primaryPosition ?: "CM",
                                ca = state.currentCa,
                                pa = state.currentPa,
                                condition = state.condition,
                                morale = state.morale,
                                preferredFoot = player.preferredFoot ?: "right",
                                isInjured = state.injuryStatus != "healthy",
                                isSuspended = false,
                                attributes = null,
                                secondaryPositions = player.secondaryPositions
                                    ?.split(",")
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotBlank() }
                                    ?: emptyList(),
                                shirtNumber = null
                            )
                        }
                        .sortedByDescending { it.ca }
                }
                .catch { e ->
                    Log.e(TAG, "getAvailablePlayers 失败", e)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
        } catch (e: Exception) {
            Log.e(TAG, "getAvailablePlayers 初始化失败", e)
            flow { emit(emptyList()) }
        }
    }

    /**
     * 计算球员在指定位置的适配度。
     *
     * @param playerId 球员 ID
     * @param position 目标位置
     * @return 适配度结果
     */
    suspend fun calculatePositionFit(playerId: Int, position: String): PositionFit =
        withContext(Dispatchers.IO) {
            if (!isSaveReady()) {
                return@withContext PositionFit(playerId, position, 50, "存档未加载，使用默认适配度")
            }
            try {
                val playerDao = databaseManager.historyPlayerDao()
                val player = playerDao.getPlayer(playerId)

                val playerPosition = player?.primaryPosition ?: "CM"
                val secondaryPositions = player?.secondaryPositions
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                // 优先用副位置
                val score = if (secondaryPositions.any {
                        it.equals(position, ignoreCase = true)
                    }) {
                    85
                } else {
                    positionFitChecker.calculateFit(playerPosition, position, null)
                }

                val reason = when {
                    playerPosition.equals(position, ignoreCase = true) -> "主位置匹配"
                    secondaryPositions.any { it.equals(position, ignoreCase = true) } -> "副位置匹配"
                    score >= 70 -> "同类位置可踢"
                    score >= 40 -> "勉强可踢"
                    else -> "位置不适应"
                }

                PositionFit(playerId, position, score, reason)
            } catch (e: Exception) {
                Log.e(TAG, "calculatePositionFit 失败: playerId=$playerId", e)
                PositionFit(playerId, position, 50, "计算失败，使用默认适配度")
            }
        }

    /**
     * 计算战术熟练度（0-100）。
     *
     * @param setup 战术设置
     * @return 熟练度 0-100
     */
    suspend fun calculateTacticalProficiency(setup: TacticalSetup): Double =
        withContext(Dispatchers.IO) {
            try {
                // 取首次发射的球员快照（Room 流永不完成，使用 first 避免挂起）
                val players = getAvailablePlayers().first()
                proficiencyCalculator.calculate(setup, players)
            } catch (e: Exception) {
                Log.e(TAG, "calculateTacticalProficiency 失败", e)
                50.0
            }
        }

    /**
     * 同步计算战术熟练度（已有球员列表时使用，避免重复查询）。
     */
    fun calculateProficiencySync(
        setup: TacticalSetup,
        players: List<PlayerWithPosition>
    ): Double = proficiencyCalculator.calculate(setup, players)

    /** 计算体能风险等级 */
    fun calculateRiskLevel(setup: TacticalSetup) =
        proficiencyCalculator.calculateRiskLevel(setup.parameters)

    /** 更新内存中的战术设置（不持久化） */
    fun updateSetup(setup: TacticalSetup) {
        currentSetup.value = setup
    }

    /** save.db 是否就绪（供 ViewModel 派生状态用） */
    fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    // ==================== 内部方法 ====================

    /** 初始化首发槽位（按阵型 11 个位置生成空槽位） */
    private fun initializeStartingSlots(formation: Formation) {
        val def = FormationDefinition.from(formation)
        val slots = def.positions.map { pos ->
            PlayerSlot(
                slotId = pos.slotId,
                playerId = null,
                position = pos.position,
                roleLabel = pos.roleLabel,
                positionFitScore = 0
            )
        }
        currentSetup.value = currentSetup.value.copy(
            formation = formation,
            starting11 = slots
        )
    }

    companion object {
        private const val TAG = "TacticsRepository"

        private const val DEFAULT_SAVE_ID = 1
        private const val DEFAULT_CLUB_ID = 1
    }
}
