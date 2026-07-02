package com.greendynasty.football.transfer.repository

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.transfer.compare.PlayerComparator
import com.greendynasty.football.transfer.config.TransferConfig
import com.greendynasty.football.transfer.model.CompareResult
import com.greendynasty.football.transfer.model.PlayerRecommendation
import com.greendynasty.football.transfer.model.SigningDifficulty
import com.greendynasty.football.transfer.model.TransferSearchFilter
import com.greendynasty.football.transfer.model.TransferSearchResult
import com.greendynasty.football.transfer.model.TransferStatus
import com.greendynasty.football.transfer.model.WatchlistEntry
import com.greendynasty.football.transfer.recommend.PlayerRecommender
import com.greendynasty.football.transfer.search.EconomyEstimator
import com.greendynasty.football.transfer.search.PlayerSearchEngine
import com.greendynasty.football.transfer.window.TransferWindowState
import com.greendynasty.football.transfer.window.TransferWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * 转会市场数据仓库（V0.1 09 §二）。
 *
 * 协调 [PlayerSearchEngine] / [PlayerRecommender] / [PlayerComparator] / [TransferWindowManager]，
 * 对外提供：搜索 / 推荐 / 对比 / 观察名单 / 转会窗状态 等能力。
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 *
 * @param databaseManager 三库管理入口
 * @param saveId 当前存档 ID
 * @param clubId 经理当前俱乐部 ID
 * @param config 转会模块配置
 */
class TransferRepository(
    private val databaseManager: DatabaseManager,
    private val saveId: Int = DEFAULT_SAVE_ID,
    private val clubId: Int = DEFAULT_CLUB_ID,
    private val config: TransferConfig = TransferConfig.DEFAULT
) {

    private val economyEstimator = EconomyEstimator(config.economy)
    private val searchEngine = PlayerSearchEngine(databaseManager, economyEstimator, config)
    private val recommender = PlayerRecommender(config.recommend)
    private val comparator = PlayerComparator(databaseManager, economyEstimator)
    private val windowManager = TransferWindowManager(config.window)

    /** 观察名单（内存缓存，T14 球探任务统一持久化到 save.db） */
    private val _watchlist = MutableStateFlow<List<WatchlistEntry>>(emptyList())
    val watchlist: StateFlow<List<WatchlistEntry>> = _watchlist.asStateFlow()

    /** 自增 entryId */
    private var nextEntryId: Int = 1

    // ==================== 搜索 ====================

    /**
     * 转会市场搜索（V0.1 09 §二.1）。
     *
     * @param filter 筛选条件
     * @param page 页码
     * @param pageSize 每页大小
     * @return 搜索结果列表
     */
    suspend fun search(
        filter: TransferSearchFilter,
        page: Int = 0,
        pageSize: Int = config.defaultPageSize
    ): List<TransferSearchResult> {
        if (!isSaveReady()) return emptyList()
        return searchEngine.search(
            saveId = saveId,
            excludeClubId = clubId,
            filter = filter,
            page = page,
            pageSize = pageSize,
            currentYear = currentYear(),
            watchlistIds = _watchlist.value.map { it.playerId }.toSet()
        )
    }

    // ==================== 推荐 ====================

    /**
     * 球员推荐（V0.2 §四，按需求匹配度排序）。
     *
     * 内部调用 [search] 获取候选，再用 [PlayerRecommender] 评分排序。
     * 自动注入球队薄弱位置 + 转会预算 + 当前战术风格。
     *
     * @param filter 筛选条件（可选，默认宽松）
     * @return 推荐球员列表
     */
    suspend fun recommend(
        filter: TransferSearchFilter = TransferSearchFilter()
    ): List<PlayerRecommendation> {
        if (!isSaveReady()) return emptyList()
        val candidates = searchEngine.search(
            saveId = saveId,
            excludeClubId = clubId,
            filter = filter,
            page = 0,
            pageSize = config.defaultPageSize,
            currentYear = currentYear(),
            watchlistIds = _watchlist.value.map { it.playerId }.toSet()
        )
        if (candidates.isEmpty()) return emptyList()

        val weakPositions = analyzeWeakPositions()
        val tacticalStyle = getCurrentTacticalStyle()
        val transferBudget = getTransferBudget()

        return recommender.recommend(
            candidates = candidates,
            weakPositions = weakPositions,
            tacticalStyle = tacticalStyle,
            transferBudget = transferBudget
        )
    }

    // ==================== 对比 ====================

    /**
     * 球员对比（V0.1 03 阵容页：球员对比功能）。
     *
     * @param playerIds 球员 ID 列表（2-3 人）
     * @return 对比结果
     */
    suspend fun compare(playerIds: List<Int>): CompareResult? {
        if (playerIds.size !in config.minComparePlayers..config.maxComparePlayers) {
            Log.w(TAG, "对比球员数量必须 ${config.minComparePlayers}-${config.maxComparePlayers} 人")
            return null
        }
        if (!isSaveReady()) return null
        return runCatching {
            comparator.compare(playerIds, saveId, currentYear())
        }.getOrElse { e ->
            Log.e(TAG, "球员对比失败: $playerIds", e)
            null
        }
    }

    // ==================== 观察名单 ====================

    /**
     * 添加到观察名单（V0.1 09 §二.2）。
     *
     * 自动计算：报告等级 / 预计身价 / 签约难度 / 竞争球队 / 球探建议。
     *
     * @param playerId 球员 ID
     * @return true 表示添加成功，false 表示已存在或满额
     */
    suspend fun addToWatchlist(playerId: Int): Boolean = withContext(Dispatchers.IO) {
        if (_watchlist.value.any { it.playerId == playerId }) return@withContext false
        if (_watchlist.value.size >= config.maxWatchlistSize) {
            Log.w(TAG, "观察名单已满 ${config.maxWatchlistSize}")
            return@withContext false
        }

        try {
            val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
            val player = databaseManager.historyPlayerDao().getPlayer(playerId) ?: return@withContext false
            val age = computeAge(player.birthDate)
            val ca = state?.currentCa ?: 50
            val pa = state?.currentPa ?: ca
            val position = player.primaryPosition ?: "CM"
            val contractUntil = state?.contractUntil
            val marketValue = if (state?.marketValue != null && state.marketValue > 0) {
                state.marketValue
            } else {
                economyEstimator.estimateMarketValue(ca, pa, age, position, contractUntil, currentYear())
            }
            val isFreeAgent = state?.currentClubId == null
            val signingDifficulty = SigningDifficulty.fromMarketValue(marketValue, isFreeAgent)
            val reportLevel = computeScoutingReportLevel(ca)
            val recommendation = generateScoutRecommendation(player.realName, ca, pa, age, marketValue, isFreeAgent)

            val entry = WatchlistEntry(
                entryId = nextEntryId++,
                saveId = saveId,
                playerId = playerId,
                addedDate = LocalDate.now().toString(),
                reportLevel = reportLevel,
                estimatedValue = marketValue,
                signingDifficulty = signingDifficulty,
                competitorClubs = emptyList(), // V1 阶段无 AI 决策日志，T13 接入
                scoutRecommendation = recommendation
            )
            _watchlist.value = _watchlist.value + entry
            Log.d(TAG, "已加入观察名单: ${player.realName}($playerId)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "加入观察名单失败: playerId=$playerId", e)
            false
        }
    }

    /** 移出观察名单 */
    fun removeFromWatchlist(playerId: Int) {
        _watchlist.value = _watchlist.value.filterNot { it.playerId == playerId }
    }

    /** 球员是否在观察名单 */
    fun isOnWatchlist(playerId: Int): Boolean =
        _watchlist.value.any { it.playerId == playerId }

    // ==================== 转会窗状态 ====================

    /**
     * 获取当前转会窗口状态（V0.1 09 §六）。
     *
     * 读取 save.db 的当前游戏日期，调用 [TransferWindowManager] 计算状态。
     * 在协程中调用，结果通过 ViewModel StateFlow 暴露给 UI。
     */
    suspend fun getTransferWindowState(): TransferWindowState = withContext(Dispatchers.IO) {
        val today = currentGameDate()
        windowManager.getState(today)
    }

    // ==================== 球队需求分析 ====================

    /**
     * 分析球队薄弱位置（基于阵型需求 vs 现有球员）。
     *
     * 简化逻辑：统计每个位置族（GK/DEF/MID/ATT）的人数，
     * 低于阈值（GK<3 / DEF<6 / MID<6 / ATT<4）视为薄弱。
     *
     * 后续 T11/T18 可改为基于阵型 11 人槽位的精细分析。
     */
    suspend fun analyzeWeakPositions(): Set<String> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptySet()
        try {
            val stateDao = databaseManager.savePlayerStateDao()
            val playerDao = databaseManager.historyPlayerDao()
            val states = stateDao.getByClub(saveId, clubId)
            if (states.isEmpty()) return@withContext emptySet()

            val playerIds = states.map { it.playerId }
            val players = playerDao.getPlayersByIds(playerIds)
            val positionCounts = mutableMapOf<String, Int>()
            players.forEach { p ->
                val pos = p.primaryPosition ?: "CM"
                positionCounts[pos] = (positionCounts[pos] ?: 0) + 1
            }

            // 按位置族统计
            val gkCount = positionCounts["GK"] ?: 0
            val defCount = listOf("CB", "LB", "RB").sumOf { positionCounts[it] ?: 0 }
            val midCount = listOf("DM", "CM", "AM").sumOf { positionCounts[it] ?: 0 }
            val attCount = listOf("LW", "RW", "ST", "CF").sumOf { positionCounts[it] ?: 0 }

            val weak = mutableSetOf<String>()
            if (gkCount < 3) weak.add("GK")
            // 后卫不足，标记 CB 薄弱
            if (defCount < 6) weak.add("CB")
            if (midCount < 6) weak.add("CM")
            if (attCount < 4) weak.add("ST")
            weak
        } catch (e: Exception) {
            Log.e(TAG, "分析薄弱位置失败", e)
            emptySet()
        }
    }

    // ==================== 内部工具 ====================

    /** save.db 是否就绪 */
    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    /**
     * 获取当前战术风格（V1 阶段战术设置在 TacticsRepository 内存中，
     * T10 阶段先用默认 POSSESSION，后续 T11 接入战术页状态）。
     */
    private fun getCurrentTacticalStyle(): TacticStyle = TacticStyle.POSSESSION

    /** 获取转会预算 */
    private suspend fun getTransferBudget(): Int? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        runCatching {
            databaseManager.saveClubStateDao().getByClub(saveId, clubId)?.transferBudget
        }.getOrNull()
    }

    /** 当前游戏日期（从 save_world_state 读取，失败回退到系统日期） */
    private suspend fun currentGameDate(): LocalDate {
        return runCatching {
            val dateStr = databaseManager.getSaveDatabaseOrNull()
                ?.saveWorldStateDao()?.get()?.currentDate
            if (dateStr.isNullOrBlank()) LocalDate.now()
            else LocalDate.parse(dateStr.take(10))
        }.getOrElse { LocalDate.now() }
    }

    /** 当前年份 */
    private suspend fun currentYear(): Int = currentGameDate().year

    /** 球探报告等级（由 CA 推算） */
    private fun computeScoutingReportLevel(ca: Int): Int = when {
        ca >= 150 -> 5
        ca >= 130 -> 4
        ca >= 110 -> 3
        ca >= 90 -> 2
        ca >= 70 -> 1
        else -> 0
    }

    /** 生成球探建议（V1 简化） */
    private fun generateScoutRecommendation(
        name: String,
        ca: Int,
        pa: Int,
        age: Int,
        marketValue: Int,
        isFreeAgent: Boolean
    ): String {
        val status = if (isFreeAgent) "自由球员，无需转会费" else "需与俱乐部谈判转会费"
        val potential = if (pa > ca + 30) "高潜力" else if (pa > ca + 10) "有成长空间" else "潜力有限"
        val valueDesc = if (marketValue > 30_000_000) "高价球员" else if (marketValue > 5_000_000) "中等身价" else "物美价廉"
        return "$name（${age}岁 CA$ca/PA$pa） - $valueDesc · $potential · $status"
    }

    /** 由出生日期计算年龄 */
    private fun computeAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return 18
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, LocalDate.now()).years
        } catch (e: Exception) {
            18
        }
    }

    companion object {
        private const val TAG = "TransferRepository"
        private const val DEFAULT_SAVE_ID = 1
        private const val DEFAULT_CLUB_ID = 1
    }
}
