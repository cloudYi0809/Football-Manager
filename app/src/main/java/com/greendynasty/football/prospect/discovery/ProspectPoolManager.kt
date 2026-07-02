package com.greendynasty.football.prospect.discovery

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.data.ProspectStateDao
import com.greendynasty.football.prospect.data.ProspectStateEntity
import com.greendynasty.football.prospect.model.HistoricalProspect
import com.greendynasty.football.prospect.model.ProspectPathEventType
import com.greendynasty.football.prospect.model.ProspectStatus
import com.greendynasty.football.prospect.path.DefaultTransferPathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T15 历史新星池管理器（V0.2 08 §三 历史新星池）。
 *
 * 职责：
 * 1. 按真实时间激活历史新星（discoverable_from ≤ 当前日期 → ACTIVE）
 * 2. 初始化新星存档状态（首次激活时创建 prospect_state 记录）
 * 3. 提供活跃新星列表查询
 *
 * 与 T07 每日推进集成：
 * - T07 每日推进第 1 步调用 [activateProspects]
 * - 已激活新星才可被 T14 球探发现
 *
 * @param databaseManager 三库管理入口
 */
class ProspectPoolManager(
    private val databaseManager: DatabaseManager
) {

    /**
     * V0.1 08 §三 历史新星池
     * 按真实时间激活到期的新星（V0.2 06 §二.1）。
     *
     * @param currentDate 当前游戏内日期
     * @param saveId 存档 ID（save_player_state.save_id 等）
     * @return 本次新激活的新星列表（用于新闻通知）
     */
    suspend fun activateProspects(
        currentDate: LocalDate,
        saveId: Int
    ): List<HistoricalProspect> = withContext(Dispatchers.IO) {
        val stateDao = databaseManager.prospectStateDao()
        val historyDao = databaseManager.historyProspectDao()

        // 1. 查询 history.db 中所有可发现日期 ≤ 当前日期的新星
        val dateStr = currentDate.toString()
        val allProspects = historyDao.getAllProspectsSync(dateStr)

        val activated = mutableListOf<HistoricalProspect>()
        for (prospectEntity in allProspects) {
            // 2. 检查是否已激活（避免重复插入）
            val existing = stateDao.get(saveId, prospectEntity.prospectId)
            if (existing != null) continue

            // 3. 激活新星：创建 prospect_state 记录
            val state = ProspectStateEntity(
                saveId = saveId,
                prospectId = prospectEntity.prospectId,
                playerId = prospectEntity.playerId,
                status = ProspectStatus.ACTIVE.code,
                activatedDate = dateStr,
                regionCode = prospectEntity.initialRegionCode,
                currentPath = "default",
                // V1 简化：CA/PA 初始值由历史数据派生（无则用 50/80 默认值）
                currentCa = 50,
                currentPa = 80,
                currentClubId = prospectEntity.defaultYouthClubId
            )
            stateDao.insert(state)

            // 4. 写入路径事件
            databaseManager.prospectPathEventDao().insert(
                ProspectPathEventEntity(
                    saveId = saveId,
                    prospectId = prospectEntity.prospectId,
                    playerId = prospectEntity.playerId,
                    eventType = ProspectPathEventType.ACTIVATED.code,
                    eventDate = dateStr,
                    isDefaultPath = 1,
                    summary = "${prospectEntity.initialRegionCode} 地区激活历史新星"
                )
            )

            // 5. 转换为领域模型并加入返回列表
            activated.add(toDomain(prospectEntity))
        }

        activated
    }

    /**
     * 获取当前活跃新星列表（已激活，含 ACTIVE / DISCOVERED / DEFAULT_PATH 状态）。
     */
    suspend fun getActiveProspects(saveId: Int): List<HistoricalProspect> = withContext(Dispatchers.IO) {
        val stateDao = databaseManager.prospectStateDao()
        val historyDao = databaseManager.historyProspectDao()
        val activeStates = stateDao.getActiveUndiscovered(saveId) +
            stateDao.getByStatus(saveId, ProspectStatus.DISCOVERED.code) +
            stateDao.getByStatus(saveId, ProspectStatus.DEFAULT_PATH.code)

        activeStates.mapNotNull { state ->
            historyDao.getProspect(state.prospectId)?.let { toDomain(it) }
        }.distinctBy { it.prospectId }
    }

    /** 获取新星存档状态。 */
    suspend fun getState(saveId: Int, prospectId: Int): ProspectStateEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.prospectStateDao().get(saveId, prospectId)
        }

    /** 获取球员对应的新星存档状态。 */
    suspend fun getStateByPlayer(saveId: Int, playerId: Int): ProspectStateEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.prospectStateDao().getByPlayer(saveId, playerId)
        }

    /** Entity → 领域模型转换。 */
    private fun toDomain(entity: HistoricalProspectPoolEntity): HistoricalProspect {
        return HistoricalProspect(
            prospectId = entity.prospectId,
            playerId = entity.playerId,
            playerName = "", // V1 简化：由 Repository 联表 history.player 填充
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
}
