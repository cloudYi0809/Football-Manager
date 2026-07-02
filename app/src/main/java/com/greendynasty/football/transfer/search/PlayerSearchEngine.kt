package com.greendynasty.football.transfer.search

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.cache.entity.PlayerSearchIndexEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.config.TransferConfig
import com.greendynasty.football.transfer.model.SortOrder
import com.greendynasty.football.transfer.model.TransferSearchFilter
import com.greendynasty.football.transfer.model.TransferSearchResult
import com.greendynasty.football.transfer.model.TransferSortBy
import com.greendynasty.football.transfer.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * 球员搜索引擎（V0.1 09 §二.1，14 项筛选 + 5 种排序）。
 *
 * 核心策略：
 * 1. **简单筛选**（仅位置/CA/年龄/身价）→ 走 cache.db 的 [PlayerSearchIndexDao.searchMulti] 索引优化路径
 * 2. **复杂筛选**（含姓名/国籍/俱乐部/合同等）→ 走 save.db 全量读取 + 内存过滤
 *
 * 性能铁律：搜索响应 ≤1 秒（[TransferConfig.searchTimeoutMs]），超时记录 warn 日志。
 *
 * @param databaseManager 三库管理入口
 * @param economyEstimator 经济估算器
 * @param config 配置
 */
class PlayerSearchEngine(
    private val databaseManager: DatabaseManager,
    private val economyEstimator: EconomyEstimator,
    private val config: TransferConfig = TransferConfig.DEFAULT
) {

    /**
     * 执行转会市场搜索。
     *
     * @param saveId 当前存档 ID
     * @param excludeClubId 排除的俱乐部 ID（通常为玩家俱乐部，避免搜到自己球员），null 表示不排除
     * @param filter 筛选条件
     * @param page 页码（0-based）
     * @param pageSize 每页大小
     * @param currentYear 当前年份（经济指数计算）
     * @param watchlistIds 当前观察名单球员 ID 集合（用于标记 isOnWatchlist）
     * @return 搜索结果列表
     */
    suspend fun search(
        saveId: Int,
        excludeClubId: Int?,
        filter: TransferSearchFilter,
        page: Int = 0,
        pageSize: Int = config.defaultPageSize,
        currentYear: Int,
        watchlistIds: Set<Int> = emptySet()
    ): List<TransferSearchResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // 1. 候选球员：简单筛选走 cache 索引，复杂筛选走 save.db
            val candidates = if (filter.isSimple() && databaseManager.getSaveDatabaseOrNull() != null) {
                searchFromCache(saveId, excludeClubId, filter, page, pageSize, currentYear, watchlistIds)
            } else {
                searchFromDb(saveId, excludeClubId, filter, page, pageSize, currentYear, watchlistIds)
            }

            // 2. 排序
            val sorted = applySort(candidates, filter.sortBy, filter.sortOrder)

            // 3. 性能监控
            val duration = System.currentTimeMillis() - startTime
            if (duration > config.searchTimeoutMs) {
                Log.w(TAG, "搜索超时：${duration}ms (filter=$filter)")
            } else {
                Log.d(TAG, "搜索完成：${candidates.size}条 耗时${duration}ms")
            }
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败: filter=$filter", e)
            emptyList()
        }
    }

    /**
     * 简单筛选路径：走 cache.db 的 PlayerSearchIndexDao 复合索引查询。
     *
     * 由于 PlayerSearchIndexEntity 字段有限，需通过 save.db + history.db 补充
     * PA / 国籍 / 合同到期 / 球员姓名 / 俱乐部名 / 头像等字段。
     */
    private suspend fun searchFromCache(
        saveId: Int,
        excludeClubId: Int?,
        filter: TransferSearchFilter,
        page: Int,
        pageSize: Int,
        currentYear: Int,
        watchlistIds: Set<Int>
    ): List<TransferSearchResult> {
        val cacheDao = databaseManager.playerSearchIndexDao()
        // Room 不支持空 list 作为 IN 参数，使用占位元素 + positionsEmpty 标志
        val safePositions = if (filter.positions.isEmpty()) listOf("") else filter.positions
        val positionsEmpty = if (filter.positions.isEmpty()) 1 else 0

        val indices = cacheDao.searchMulti(
            positions = safePositions,
            positionsEmpty = positionsEmpty,
            caMin = filter.caRange?.first,
            caMax = filter.caRange?.last,
            ageMin = filter.ageRange?.first,
            ageMax = filter.ageRange?.last,
            maxValue = filter.maxMarketValue,
            limit = pageSize,
            offset = page * pageSize
        )

        if (indices.isEmpty()) return emptyList()

        // 聚合补充字段：批量查 history.player + save.save_player_state
        val playerIds = indices.map { it.playerId }
        val players = databaseManager.historyPlayerDao().getPlayersByIds(playerIds).associateBy { it.playerId }
        val stateDao = databaseManager.savePlayerStateDao()
        val states = mutableMapOf<Int, SavePlayerStateEntity>()
        playerIds.forEach { pid ->
            stateDao.getByPlayer(saveId, pid)?.let { states[pid] = it }
        }
        val clubCache = mutableMapOf<Int, String?>()

        return indices.mapNotNull { idx ->
            val player = players[idx.playerId] ?: return@mapNotNull null
            val state = states[idx.playerId]
            // 排除当前俱乐部球员
            if (excludeClubId != null && state?.currentClubId == excludeClubId) {
                return@mapNotNull null
            }
            buildResult(player, idx, state, clubCache, currentYear, watchlistIds)
        }
    }

    /**
     * 复杂筛选路径：从 save.db 读取全部球员状态，批量聚合 history.player，内存过滤。
     *
     * 性能权衡：全量读取可能 >1 秒，但保证筛选完整性。
     * 后续 T17/T18 可改为更复杂的 SQL 查询或索引优化。
     */
    private suspend fun searchFromDb(
        saveId: Int,
        excludeClubId: Int?,
        filter: TransferSearchFilter,
        page: Int,
        pageSize: Int,
        currentYear: Int,
        watchlistIds: Set<Int>
    ): List<TransferSearchResult> {
        val stateDao = databaseManager.savePlayerStateDao()
        val allStates = stateDao.getAll(saveId)
        val playerDao = databaseManager.historyPlayerDao()

        // 批量查球员基础信息（避免 N+1）
        val playerIds = allStates.map { it.playerId }
        val players = if (playerIds.isEmpty()) {
            emptyMap()
        } else {
            playerDao.getPlayersByIds(playerIds).associateBy { it.playerId }
        }
        val clubCache = mutableMapOf<Int, String?>()

        // 内存过滤 + 聚合
        val filtered = allStates.mapNotNull { state ->
            val player = players[state.playerId] ?: return@mapNotNull null
            // 排除当前俱乐部球员
            if (excludeClubId != null && state.currentClubId == excludeClubId) {
                return@mapNotNull null
            }
            // 排除退役球员
            if (state.careerStatus == "retired") return@mapNotNull null

            if (!matchesFilter(player, state, filter, currentYear)) return@mapNotNull null

            val idx = PlayerSearchIndexEntity(
                playerId = player.playerId,
                normalizedName = (player.displayName ?: player.realName).lowercase(),
                searchTokens = "",
                currentClubId = state.currentClubId,
                currentCa = state.currentCa,
                age = computeAge(player.birthDate),
                position = player.primaryPosition ?: "CM",
                marketValue = state.marketValue
            )
            buildResult(player, idx, state, clubCache, currentYear, watchlistIds)
        }

        // 内存分页
        val fromIndex = (page * pageSize).coerceAtMost(filtered.size)
        val toIndex = (fromIndex + pageSize).coerceAtMost(filtered.size)
        return filtered.subList(fromIndex, toIndex)
    }

    /** 14 项筛选条件匹配（AND 关系） */
    private fun matchesFilter(
        player: PlayerEntity,
        state: SavePlayerStateEntity,
        filter: TransferSearchFilter,
        currentYear: Int
    ): Boolean {
        // 1. 姓名（模糊匹配，不区分大小写）
        if (!filter.name.isNullOrBlank()) {
            val target = filter.name.lowercase()
            val playerName = (player.displayName ?: player.realName).lowercase()
            if (!playerName.contains(target)) return false
        }

        // 2. 年龄范围
        val age = computeAge(player.birthDate)
        filter.ageRange?.let { if (age !in it) return false }

        // 3. 国籍（OR 关系）
        if (filter.nationalities.isNotEmpty()) {
            val nat = player.nationality ?: return false
            if (filter.nationalities.none { it.equals(nat, ignoreCase = true) }) return false
        }

        // 4. 主要位置（OR 关系）
        if (filter.positions.isNotEmpty()) {
            val pos = player.primaryPosition ?: return false
            val secondary = player.secondaryPositions
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val allPositions = listOf(pos) + secondary
            if (filter.positions.none { target -> allPositions.any { it.equals(target, ignoreCase = true) } }) {
                return false
            }
        }

        // 5. CA 范围
        filter.caRange?.let { if (state.currentCa !in it) return false }

        // 6. PA 范围
        filter.paRange?.let { if (state.currentPa !in it) return false }

        // 7. 最大身价
        filter.maxMarketValue?.let { if (state.marketValue > it) return false }

        // 8. 最大工资
        filter.maxWage?.let { if (state.wage > it) return false }

        // 9. 俱乐部 ID（OR 关系）
        if (filter.clubIds.isNotEmpty()) {
            val clubId = state.currentClubId ?: return false
            if (clubId !in filter.clubIds) return false
        }

        // 10. 联赛 ID（OR 关系，V1 阶段 league_id 暂未实现，跳过）
        // if (filter.leagueIds.isNotEmpty()) { ... }

        // 11. 合同剩余年限上限
        filter.contractRemainingMax?.let {
            val remaining = computeContractRemainingYears(state.contractUntil, currentYear)
            if (remaining == null || remaining > it) return false
        }

        // 12. 转会状态
        filter.transferStatus?.let {
            val status = determineTransferStatus(state)
            if (status != it) return false
        }

        return true
    }

    /** 聚合构建 [TransferSearchResult] */
    private suspend fun buildResult(
        player: PlayerEntity,
        idx: PlayerSearchIndexEntity,
        state: SavePlayerStateEntity?,
        clubCache: MutableMap<Int, String?>,
        currentYear: Int,
        watchlistIds: Set<Int>
    ): TransferSearchResult {
        val clubId = state?.currentClubId ?: idx.currentClubId
        val clubName = clubId?.let { cid ->
            clubCache.getOrPut(cid) {
                runCatching { databaseManager.historyClubDao().getClub(cid)?.clubName }.getOrNull()
            }
        }
        val ca = state?.currentCa ?: idx.currentCa
        val pa = state?.currentPa ?: ca
        val contractUntil = state?.contractUntil
        val marketValue = if (state?.marketValue != null && state.marketValue > 0) {
            state.marketValue
        } else {
            economyEstimator.estimateMarketValue(ca, pa, idx.age, idx.position, contractUntil, currentYear)
        }
        val wage = if (state?.wage != null && state.wage > 0) {
            state.wage
        } else {
            economyEstimator.estimateExpectedWage(ca, idx.position, state?.squadRole, currentYear)
        }
        val transferStatus = determineTransferStatus(state)
        val reportLevel = computeScoutingReportLevel(ca)

        return TransferSearchResult(
            playerId = player.playerId,
            playerName = player.displayName ?: player.realName,
            age = idx.age,
            nationality = player.nationality ?: "未知",
            position = player.primaryPosition ?: "CM",
            secondaryPositions = player.secondaryPositions
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            currentCa = ca,
            potentialPa = pa,
            clubId = clubId,
            clubName = clubName,
            leagueId = null, // V1 阶段未实现联赛 ID 关联
            marketValue = marketValue,
            wage = wage,
            contractUntil = contractUntil,
            transferStatus = transferStatus,
            scoutingReportLevel = reportLevel,
            isOnWatchlist = player.playerId in watchlistIds,
            preferredFoot = player.preferredFoot,
            portraitPath = player.portraitPath
        )
    }

    /** 推断球员转会状态 */
    private fun determineTransferStatus(state: SavePlayerStateEntity?): TransferStatus {
        if (state == null) return TransferStatus.FREE_AGENT
        if (state.currentClubId == null) return TransferStatus.FREE_AGENT
        if (state.loanClubId != null) return TransferStatus.LOANABLE
        // V1 阶段无"挂牌"标记，默认均可售
        return TransferStatus.TRANSFERABLE
    }

    /** 球探报告等级 0-5（由 CA 推算，V1 简化） */
    private fun computeScoutingReportLevel(ca: Int): Int = when {
        ca >= 150 -> 5
        ca >= 130 -> 4
        ca >= 110 -> 3
        ca >= 90 -> 2
        ca >= 70 -> 1
        else -> 0
    }

    /** 5 种排序 */
    private fun applySort(
        results: List<TransferSearchResult>,
        sortBy: TransferSortBy,
        sortOrder: SortOrder
    ): List<TransferSearchResult> {
        val comparator = when (sortBy) {
            TransferSortBy.CA -> compareBy<TransferSearchResult> { it.currentCa }
            TransferSortBy.PA -> compareBy<TransferSearchResult> { it.potentialPa }
            TransferSortBy.AGE -> compareBy<TransferSearchResult> { it.age }
            TransferSortBy.MARKET_VALUE -> compareBy<TransferSearchResult> { it.marketValue }
            TransferSortBy.WAGE -> compareBy<TransferSearchResult> { it.wage }
        }
        return if (sortOrder == SortOrder.DESC) {
            results.sortedWith(comparator.reversed())
        } else {
            results.sortedWith(comparator)
        }
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

    /** 计算合同剩余年限（null 表示无合同/无法解析） */
    private fun computeContractRemainingYears(contractUntil: String?, currentYear: Int): Int? {
        if (contractUntil.isNullOrBlank()) return null
        return runCatching {
            val untilYear = contractUntil.take(4).toInt()
            untilYear - currentYear
        }.getOrNull()
    }

    companion object {
        private const val TAG = "PlayerSearchEngine"
    }
}
