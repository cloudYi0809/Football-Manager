package com.greendynasty.football.prospect.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.data.ProspectStateEntity
import com.greendynasty.football.prospect.model.HistoricalProspect
import com.greendynasty.football.prospect.model.ProspectConfig
import com.greendynasty.football.prospect.model.ProspectPathEventType
import com.greendynasty.football.prospect.model.ProspectStatus
import com.greendynasty.football.prospect.path.DefaultTransferPathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * T15 历史新星视图项（聚合 history.historical_prospect_pool + history.player + save.prospect_state）。
 *
 * 由 [ProspectRepository] 构造，供 ViewModel / UI 使用。
 */
data class ProspectViewItem(
    val prospectId: Int,
    val playerId: Int,
    val playerName: String,
    val realName: String,
    val nationality: String?,
    val birthDate: String?,
    val primaryPosition: String?,
    val age: Int, // V1 简化：由 birthDate 计算
    val regionCode: String,
    val legendLevel: Int,
    val tags: List<String>,
    val status: String,
    val statusDisplay: String,
    val discoveredByClubId: Int?,
    val discoveredDate: String?,
    val activatedDate: String?,
    val currentCa: Int,
    val currentPa: Int,
    val currentClubId: Int?,
    val currentPath: String,
    val butterflyTriggered: Boolean,
    val butterflyEventId: String?,
    val defaultBreakthroughYear: Int,
    val discoverableFrom: String,
    val transferPathEventCount: Int
)

/**
 * T15 历史新星详情视图项（聚合状态 + 路径事件时间轴）。
 */
data class ProspectDetail(
    val prospect: ProspectViewItem,
    val pathEvents: List<ProspectPathEventEntity>,
    val historicalProspect: HistoricalProspect?
)

/**
 * T15 历史新星仓库（V0.2 08 §三 + T15 方案 §六 UI 数据层）。
 *
 * 职责：
 * 1. 封装 DAO 访问，提供 Flow / suspend 查询接口供 ViewModel 使用
 * 2. Entity → ViewModel 数据转换（联表 history.player + history.historical_prospect_pool）
 * 3. 加载路径事件时间轴（用于详情页蝴蝶路径图）
 *
 * 三库分离：history.historical_prospect_pool / history.player 只读，save.prospect_state 可写。
 *
 * @param databaseManager 三库管理入口
 * @param config 历史新星池配置
 */
class ProspectRepository(
    private val databaseManager: DatabaseManager,
    private val config: ProspectConfig = ProspectConfig.DEFAULT
) {

    // ==================== 1. 已发现新星列表 ====================

    /**
     * 观察已发现 + 默认路径状态的新星列表（Flow 驱动 UI 刷新）。
     */
    fun observeDiscoveredProspects(saveId: Int): Flow<List<ProspectViewItem>> {
        return databaseManager.prospectStateDao().observeActiveAndDiscovered(saveId).map { states ->
            states.mapNotNull { buildViewItem(saveId, it) }
        }
    }

    /**
     * 一次性加载已发现新星列表（无 Flow）。
     */
    suspend fun getDiscoveredProspects(saveId: Int): List<ProspectViewItem> =
        withContext(Dispatchers.IO) {
            val stateDao = databaseManager.prospectStateDao()
            val states = stateDao.getDiscovered(saveId)
            states.mapNotNull { buildViewItem(saveId, it) }
        }

    /**
     * 观察所有已激活新星列表（含 ACTIVE 状态）。
     */
    fun observeAllActiveProspects(saveId: Int): Flow<List<ProspectViewItem>> {
        return databaseManager.prospectStateDao().observeAll(saveId).map { states ->
            states.mapNotNull { buildViewItem(saveId, it) }
        }
    }

    // ==================== 2. 详情 ====================

    /**
     * 加载历史新星详情（含路径事件时间轴 + 历史配置）。
     */
    suspend fun getProspectDetail(saveId: Int, prospectId: Int): ProspectDetail? =
        withContext(Dispatchers.IO) {
            val state = databaseManager.prospectStateDao().get(saveId, prospectId)
                ?: return@withContext null
            val viewItem = buildViewItem(saveId, state) ?: return@withContext null
            val pathEvents = databaseManager.prospectPathEventDao().getByProspect(saveId, prospectId)

            val historyProspect = databaseManager.historyProspectDao().getProspect(prospectId)?.let {
                toDomain(it)
            }

            ProspectDetail(
                prospect = viewItem,
                pathEvents = pathEvents,
                historicalProspect = historyProspect
            )
        }

    /**
     * 观察路径事件时间轴（详情页蝴蝶路径图）。
     */
    fun observePathEvents(saveId: Int, prospectId: Int): Flow<List<ProspectPathEventEntity>> {
        return databaseManager.prospectPathEventDao().observeByProspect(saveId, prospectId)
    }

    /**
     * 观察最近路径事件（首页新闻流可选接入）。
     */
    fun observeRecentEvents(saveId: Int, limit: Int = 20): Flow<List<ProspectPathEventEntity>> {
        return databaseManager.prospectPathEventDao().observeRecent(saveId, limit)
    }

    // ==================== 3. 统计 ====================

    /**
     * 获取统计信息：已激活 / 已发现 / 已签约 / 蝴蝶触发 数量。
     */
    suspend fun getStatistics(saveId: Int): ProspectStatistics = withContext(Dispatchers.IO) {
        val stateDao = databaseManager.prospectStateDao()
        val allStates = stateDao.getAll(saveId)
        val discovered = allStates.count {
            it.status in listOf(
                ProspectStatus.DISCOVERED.code,
                ProspectStatus.DEFAULT_PATH.code,
                ProspectStatus.SIGNED_EARLY.code
            )
        }
        val active = allStates.count { it.status == ProspectStatus.ACTIVE.code }
        val signedEarly = allStates.count { it.status == ProspectStatus.SIGNED_EARLY.code }
        val butterflyCount = allStates.count { it.butterflyTriggered == 1 }

        ProspectStatistics(
            totalActivated = allStates.size,
            activeCount = active,
            discoveredCount = discovered,
            signedEarlyCount = signedEarly,
            butterflyTriggeredCount = butterflyCount
        )
    }

    /**
     * 获取历史新星池总数（history.db）。
     */
    suspend fun getPoolSize(): Int = withContext(Dispatchers.IO) {
        databaseManager.historyProspectDao().count()
    }

    // ==================== 4. 转换工具 ====================

    /** 构建 ProspectViewItem（联表 history.player + history.historical_prospect_pool）。 */
    private suspend fun buildViewItem(saveId: Int, state: ProspectStateEntity): ProspectViewItem? {
        val historyProspect = databaseManager.historyProspectDao().getProspect(state.prospectId)
            ?: return null
        val player = databaseManager.historyPlayerDao().getPlayer(state.playerId)
        // 计算已执行的转会数（按 TRANSFER 类型，过滤当前 prospect）
        val transferPathCount = databaseManager.prospectPathEventDao()
            .getByType(saveId, ProspectPathEventType.TRANSFER.code)
            .count { it.prospectId == state.prospectId }

        val statusDisplay = ProspectStatus.fromCode(state.status)?.display ?: state.status
        val age = calculateAge(player?.birthDate, state.activatedDate)

        return ProspectViewItem(
            prospectId = state.prospectId,
            playerId = state.playerId,
            playerName = player?.displayName ?: player?.realName ?: "未知球员",
            realName = player?.realName ?: "",
            nationality = player?.nationality,
            birthDate = player?.birthDate,
            primaryPosition = player?.primaryPosition,
            age = age,
            regionCode = state.regionCode,
            legendLevel = historyProspect.legendLevel,
            tags = historyProspect.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            status = state.status,
            statusDisplay = statusDisplay,
            discoveredByClubId = state.discoveredByClubId,
            discoveredDate = state.discoveredDate,
            activatedDate = state.activatedDate,
            currentCa = state.currentCa,
            currentPa = state.currentPa,
            currentClubId = state.currentClubId,
            currentPath = state.currentPath,
            butterflyTriggered = state.butterflyTriggered == 1,
            butterflyEventId = state.butterflyEventId,
            defaultBreakthroughYear = historyProspect.defaultBreakthroughYear,
            discoverableFrom = historyProspect.discoverableFrom,
            transferPathEventCount = transferPathCount
        )
    }

    /** Entity → 领域模型转换（无联表 history.player）。 */
    private fun toDomain(entity: HistoricalProspectPoolEntity): HistoricalProspect {
        return HistoricalProspect(
            prospectId = entity.prospectId,
            playerId = entity.playerId,
            playerName = "",
            realName = "",
            discoverableFrom = entity.discoverableFrom,
            defaultYouthClubId = entity.defaultYouthClubId,
            defaultFirstTeamClubId = entity.defaultFirstTeamClubId,
            defaultBreakthroughYear = entity.defaultBreakthroughYear,
            defaultTransferPath = DefaultTransferPathParser.parse(entity.defaultTransferPath),
            initialRegionCode = entity.initialRegionCode,
            hiddenUntilDiscovered = entity.hiddenUntilDiscovered == 1,
            legendLevel = entity.legendLevel,
            createdScenario = entity.createdScenario,
            tags = entity.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            nationality = null,
            birthDate = null,
            primaryPosition = null
        )
    }

    /** 根据出生日期 + 当前日期计算年龄。 */
    private fun calculateAge(birthDate: String?, currentDateStr: String?): Int {
        if (birthDate.isNullOrBlank()) return -1
        return runCatching {
            val birth = java.time.LocalDate.parse(birthDate.take(10))
            val current = if (currentDateStr.isNullOrBlank()) {
                java.time.LocalDate.now()
            } else {
                java.time.LocalDate.parse(currentDateStr.take(10))
            }
            java.time.Period.between(birth, current).years
        }.getOrDefault(-1)
    }
}

/**
 * 历史新星统计信息。
 */
data class ProspectStatistics(
    val totalActivated: Int,
    val activeCount: Int,
    val discoveredCount: Int,
    val signedEarlyCount: Int,
    val butterflyTriggeredCount: Int
)
