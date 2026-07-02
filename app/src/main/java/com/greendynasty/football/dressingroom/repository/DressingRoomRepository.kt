package com.greendynasty.football.dressingroom.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.dressingroom.atmosphere.AtmosphereEvaluator
import com.greendynasty.football.dressingroom.chemistry.ChemistryCalculator
import com.greendynasty.football.dressingroom.event.PlayerEmotionEventService
import com.greendynasty.football.dressingroom.leader.DressingRoomLeaderDetector
import com.greendynasty.football.dressingroom.model.AtmosphereEvaluation
import com.greendynasty.football.dressingroom.model.DressingRoomConfig
import com.greendynasty.football.dressingroom.model.DressingRoomLeaderEntity
import com.greendynasty.football.dressingroom.model.DressingRoomSnapshot
import com.greendynasty.football.dressingroom.model.LeaderRole
import com.greendynasty.football.dressingroom.model.PlayerEmotionEventEntity
import com.greendynasty.football.dressingroom.model.PlayerMoraleEntity
import com.greendynasty.football.dressingroom.model.PlayerProfile
import com.greendynasty.football.dressingroom.morale.MatchOutcome
import com.greendynasty.football.dressingroom.morale.MatchResult
import com.greendynasty.football.dressingroom.morale.MoraleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import java.util.logging.Logger

/**
 * T23 更衣室仓库（V0.2 + T23 任务要求 §二.6 + 实现方案 §五）。
 *
 * 数据访问层 + 业务协调入口，负责：
 * 1. 协调 [MoraleManager] / [ChemistryCalculator] / [AtmosphereEvaluator] /
 *    [DressingRoomLeaderDetector] / [PlayerEmotionEventService] 五大组件
 * 2. 持久化所有更衣室数据到 save.db
 * 3. 提供 Flow / suspend 查询接口供 ViewModel 使用
 * 4. 聚合更衣室快照（[DressingRoomSnapshot]）
 * 5. 衔接 history.db（球员基础信息）+ save.db（运行时状态）
 *
 * 三库分离：运行时数据落 save.db，球员基础信息从 history.db 只读。
 *
 * @param databaseManager 三库管理入口
 * @param config 更衣室配置
 */
class DressingRoomRepository(
    private val databaseManager: DatabaseManager,
    private val config: DressingRoomConfig = DressingRoomConfig.DEFAULT
) {
    private val logger = Logger.getLogger("DressingRoomRepository")

    val moraleManager = MoraleManager(databaseManager, config)
    val chemistryCalculator = ChemistryCalculator(databaseManager, config)
    val atmosphereEvaluator = AtmosphereEvaluator(databaseManager, config)
    val leaderDetector = DressingRoomLeaderDetector(databaseManager, config)
    val eventService = PlayerEmotionEventService(databaseManager, config)

    // ==================== 1. 球员士气 ====================

    /** 观察俱乐部全员士气（Flow 驱动 UI 自动刷新）。 */
    fun observePlayerMorales(saveId: Int, clubId: Int): Flow<List<PlayerMoraleEntity>> {
        return databaseManager.getSaveDatabase().playerMoraleDao()
            .observeByClub(saveId, clubId)
    }

    /** 查询俱乐部全员士气（一次性，按士气升序）。 */
    suspend fun getPlayerMorales(saveId: Int, clubId: Int): List<PlayerMoraleEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.playerMoraleDao().getByClub(saveId, clubId)
        }

    /** 查询俱乐部不满球员（士气 LOW / EXTREME_LOW）。 */
    suspend fun getUnhappyPlayers(saveId: Int, clubId: Int): List<PlayerMoraleEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.playerMoraleDao().getUnhappyPlayers(saveId, clubId)
        }

    /** 查询俱乐部平均士气 0-100。 */
    suspend fun getTeamMorale(saveId: Int, clubId: Int): Int =
        withContext(Dispatchers.IO) {
            databaseManager.playerMoraleDao().getAverageMorale(saveId, clubId)?.toInt() ?: 50
        }

    /** 查询需要谈话的球员（unrestAccumulator ≥ 阈值）。 */
    suspend fun getPlayersNeedingTalk(saveId: Int, clubId: Int): List<PlayerMoraleEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.playerMoraleDao()
                .getPlayersNeedingTalk(saveId, clubId, config.unrest.unrestThreshold)
        }

    /** 比赛日触发球员士气变化（委托给 [moraleManager]）。 */
    suspend fun onMatchPlayed(
        saveId: Int,
        playerId: Int,
        clubId: Int,
        isStarter: Boolean,
        minutesPlayed: Int,
        matchOutcome: MatchOutcome,
        teamGoals: Int,
        opponentGoals: Int,
        rating: Double? = null,
        seasonId: Int,
        currentDate: LocalDate
    ): Int = moraleManager.onMatchPlayed(
        saveId, playerId, clubId, isStarter, minutesPlayed,
        MatchResult(matchOutcome, teamGoals, opponentGoals), rating,
        seasonId, currentDate
    )

    /** 夺冠奖励全队士气 +15（委托给 [moraleManager]）。 */
    suspend fun onWinTitle(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ) = moraleManager.onWinTitle(saveId, clubId, seasonId, currentDate)

    /** 清除球员不满（玩家发起谈话）。 */
    suspend fun clearUnrest(saveId: Int, playerId: Int): Boolean =
        moraleManager.clearUnrest(saveId, playerId)

    // ==================== 2. 化学反应 ====================

    /** 触发俱乐部全队化学反应重算（转会窗关闭 / 阵容大变动时）。 */
    suspend fun recomputeChemistry(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        val profiles = buildPlayerProfiles(saveId, clubId, currentDate)
        chemistryCalculator.computeAndPersistForClub(saveId, clubId, profiles, currentDate)
    }

    /** 查询俱乐部化学反应指数（全队平均，0-1）。 */
    suspend fun getClubChemistryIndex(saveId: Int, clubId: Int): Double =
        chemistryCalculator.getClubChemistryIndex(saveId, clubId)

    // ==================== 3. 更衣室氛围 ====================

    /** 观察最近氛围快照（Flow 驱动 UI 历史曲线）。 */
    fun observeAtmosphereHistory(
        saveId: Int, clubId: Int, limit: Int = 12
    ): Flow<List<com.greendynasty.football.dressingroom.model.DressingRoomAtmosphereEntity>> {
        return databaseManager.getSaveDatabase().dressingRoomAtmosphereDao()
            .observeLatest(saveId, clubId, limit)
    }

    /** 查询最近氛围快照（一次性）。 */
    suspend fun getLatestAtmosphere(
        saveId: Int, clubId: Int
    ): com.greendynasty.football.dressingroom.model.DressingRoomAtmosphereEntity? =
        atmosphereEvaluator.getLatestSnapshot(saveId, clubId)

    /**
     * 月度氛围评估并持久化（委托给 [atmosphereEvaluator]）。
     *
     * 由 T07 每月推进调用。
     */
    suspend fun evaluateAtmosphere(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): com.greendynasty.football.dressingroom.model.DressingRoomAtmosphereEntity? =
        withContext(Dispatchers.IO) {
            try {
                val chemistryIndex = chemistryCalculator.getClubChemistryIndex(saveId, clubId)
                val leaderInfluence = leaderDetector.getTeamLeaderInfluence(saveId, clubId)
                atmosphereEvaluator.evaluateAndPersist(
                    saveId, clubId, seasonId, chemistryIndex, leaderInfluence, currentDate
                )
            } catch (e: Exception) {
                logger.warning("月度氛围评估失败：${e.message}")
                null
            }
        }

    // ==================== 4. 领袖 ====================

    /** 观察俱乐部活跃领袖（Flow 驱动 UI）。 */
    fun observeLeaders(
        saveId: Int, clubId: Int
    ): Flow<List<DressingRoomLeaderEntity>> {
        return databaseManager.getSaveDatabase().dressingRoomLeaderDao()
            .observeActive(saveId, clubId)
    }

    /** 查询活跃领袖（一次性）。 */
    suspend fun getActiveLeaders(
        saveId: Int, clubId: Int
    ): List<DressingRoomLeaderEntity> = leaderDetector.getActiveLeaders(saveId, clubId)

    /** 查询当前队长。 */
    suspend fun getCaptain(
        saveId: Int, clubId: Int
    ): DressingRoomLeaderEntity? = leaderDetector.getCaptain(saveId, clubId)

    /** 赛季初自动识别领袖（委托给 [leaderDetector]）。 */
    suspend fun detectLeaders(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): List<DressingRoomLeaderEntity> = withContext(Dispatchers.IO) {
        val profiles = buildPlayerProfiles(saveId, clubId, currentDate)
        leaderDetector.detectAndPersist(saveId, clubId, profiles, seasonId, currentDate)
    }

    /** 玩家手动任命领袖。 */
    suspend fun appointLeader(
        saveId: Int, clubId: Int, playerId: Int, role: LeaderRole,
        seasonId: Int, currentDate: LocalDate
    ): DressingRoomLeaderEntity? = withContext(Dispatchers.IO) {
        val profiles = buildPlayerProfiles(saveId, clubId, currentDate)
        val profile = profiles.firstOrNull { it.playerId == playerId } ?: return@withContext null
        val influence = leaderDetector.computeInfluence(profile)
        leaderDetector.appointLeader(
            saveId, clubId, playerId, role, profile.leadership, influence,
            seasonId, currentDate
        )
    }

    /** 撤销领袖任命。 */
    suspend fun revokeLeader(
        saveId: Int, clubId: Int, leaderId: Long,
        currentDate: LocalDate, reason: String
    ) = leaderDetector.revokeLeader(saveId, clubId, leaderId, currentDate, reason)

    // ==================== 5. 情绪事件 ====================

    /** 观察最近情绪事件（Flow 驱动 UI）。 */
    fun observeRecentEvents(
        saveId: Int, clubId: Int, limit: Int = 50
    ): Flow<List<PlayerEmotionEventEntity>> {
        return databaseManager.getSaveDatabase().playerEmotionEventDao()
            .observeRecent(saveId, clubId, limit)
    }

    /** 查询最近情绪事件（一次性）。 */
    suspend fun getRecentEvents(
        saveId: Int, clubId: Int, limit: Int = 50
    ): List<PlayerEmotionEventEntity> = eventService.getRecentEvents(saveId, clubId, limit)

    /** 周度扫描触发情绪事件（由 T07 每周推进调用）。 */
    suspend fun scanEmotionEvents(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): List<PlayerEmotionEventEntity> = eventService.scanAndTrigger(
        saveId, clubId, seasonId, currentDate
    )

    /** 解决情绪事件。 */
    suspend fun resolveEvent(
        eventId: Long, resolution: String, currentDate: LocalDate
    ) = eventService.resolveEvent(eventId, resolution, currentDate)

    // ==================== 6. 快照聚合 ====================

    /**
     * 聚合更衣室快照（UI 一次性消费）。
     *
     * 包含：
     * - 当前队长 + 活跃领袖
     * - 全员士气列表 + 不满球员列表
     * - 球队平均士气
     * - 化学反应指数
     * - 最近氛围评估（若存在）
     * - 最近 50 条情绪事件
     */
    suspend fun getSnapshot(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): DressingRoomSnapshot = withContext(Dispatchers.IO) {
        val captain = leaderDetector.getCaptain(saveId, clubId)
        val leaders = leaderDetector.getActiveLeaders(saveId, clubId)
        val playerMorales = databaseManager.playerMoraleDao().getByClub(saveId, clubId)
        val unhappyPlayers = databaseManager.playerMoraleDao().getUnhappyPlayers(saveId, clubId)
        val teamMorale = playerMorales.map { it.morale }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt() ?: 50
        val chemistryIndex = chemistryCalculator.getClubChemistryIndex(saveId, clubId)
        val atmosphere = try {
            atmosphereEvaluator.evaluate(
                saveId, clubId, chemistryIndex,
                leaders.map { it.influence }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0,
                currentDate
            )
        } catch (e: Exception) {
            null
        }
        val recentEvents = databaseManager.playerEmotionEventDao()
            .getRecent(saveId, clubId, 50)

        DressingRoomSnapshot(
            saveId = saveId,
            clubId = clubId,
            captain = captain,
            leaders = leaders,
            playerMorales = playerMorales,
            unhappyPlayers = unhappyPlayers,
            teamMorale = teamMorale,
            chemistryIndex = chemistryIndex,
            atmosphere = atmosphere,
            recentEvents = recentEvents
        )
    }

    // ==================== 7. 月度推进 ====================

    /**
     * 月度更衣室推进（V0.2 + 实现方案 §五）。
     *
     * 由 T07 每月推进调用：
     * 1. 评估并持久化氛围快照
     *
     * 注意：日度士气衰减由 [moraleManager.applyDailyDrift] 在每日推进中调用；
     * 周度情绪事件扫描由 [scanEmotionEvents] 在每周推进中调用。
     */
    suspend fun processMonthlyReview(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        try {
            evaluateAtmosphere(saveId, clubId, seasonId, currentDate)
        } catch (e: Exception) {
            logger.warning("月度更衣室推进失败：${e.message}")
        }
    }

    // ==================== 内部工具：构建球员画像 ====================

    /**
     * 构建俱乐部全员球员画像（V0.2 + T23 任务要求 §二.6）。
     *
     * 衔接 history.db（player + player_attributes）+ save.db（save_player_state）。
     *
     * @return 俱乐部内所有球员的画像列表
     */
    suspend fun buildPlayerProfiles(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): List<PlayerProfile> = withContext(Dispatchers.IO) {
        // 1. 从 save_player_state 取俱乐部内所有球员
        val playerStates = try {
            databaseManager.savePlayerStateDao().getByClub(saveId, clubId)
        } catch (_: Exception) { emptyList() }
        if (playerStates.isEmpty()) return@withContext emptyList()

        // 2. 批量查询球员基础信息 + 最新属性
        val playerIds = playerStates.map { it.playerId }
        val players = try {
            databaseManager.historyPlayerDao().getPlayersByIds(playerIds)
        } catch (_: Exception) { emptyList() }
        val attributes = try {
            databaseManager.historyPlayerDao().getLatestAttributesBatch(playerIds)
        } catch (_: Exception) { emptyList() }

        // 3. 组装画像
        val playerMap = players.associateBy { it.playerId }
        val attrMap = attributes.associateBy { it.playerId }
        playerStates.mapNotNull { state ->
            val player = playerMap[state.playerId] ?: return@mapNotNull null
            val attr = attrMap[state.playerId]
            mapToProfile(player, state, attr, currentDate)
        }
    }

    /**
     * 将 history.player + save_player_state + player_attributes 映射为 [PlayerProfile]。
     */
    private fun mapToProfile(
        player: PlayerEntity,
        state: SavePlayerStateEntity,
        attr: PlayerAttributesEntity?,
        currentDate: LocalDate
    ): PlayerProfile {
        val age = computeAge(player.birthDate, currentDate)
        val secondary = player.secondaryPositions
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return PlayerProfile(
            playerId = player.playerId,
            name = player.realName,
            nationality = player.nationality ?: "",
            language = player.nationality ?: "", // V1 简化：用国籍作为语言
            age = age,
            primaryPosition = player.primaryPosition ?: "",
            secondaryPositions = secondary,
            leadership = attr?.leadership ?: 50,
            professionalism = attr?.professionalism ?: 50,
            temperament = attr?.bigMatch ?: 50,
            squadRole = state.squadRole ?: "backup"
        )
    }

    /**
     * 计算球员年龄（与 PlayerValueEstimator.computeAge 一致）。
     */
    private fun computeAge(birthDate: String?, currentDate: LocalDate): Int {
        if (birthDate.isNullOrBlank()) return 18
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrElse { 18 }
    }
}
