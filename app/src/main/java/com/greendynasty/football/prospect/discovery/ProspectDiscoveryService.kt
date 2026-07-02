package com.greendynasty.football.prospect.discovery

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.ScoutEntity
import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.data.ProspectStateEntity
import com.greendynasty.football.prospect.model.ProspectConfig
import com.greendynasty.football.prospect.model.ProspectPathEventType
import com.greendynasty.football.prospect.model.ProspectStatus
import com.greendynasty.football.scouting.data.SaveScoutHiredEntity
import com.greendynasty.football.scouting.integration.ProspectDiscoveryBridge
import com.greendynasty.football.scouting.model.CandidatePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * T15 历史新星发现服务（V0.2 08 §三.5 + 06 §二.1）。
 *
 * 实现 T14 [ProspectDiscoveryBridge] 接口，让球探可在特定年份发现历史新星：
 * - [getDiscoverableProspectsInRegion]：球探构建候选池时调用，返回 ACTIVE 状态的历史新星
 * - [onProspectDiscovered]：球探发现后调用，更新状态为 DISCOVERED 并写入路径事件
 *
 * 时序保证（T14 方案 §六）：
 * - T15 每日推进先于球探推进，新星状态已 ACTIVE
 * - 即便 T15 推进未执行，[getDiscoverableProspectsInRegion] 也会按需激活
 *
 * @param databaseManager 三库管理入口
 * @param config 历史新星池配置
 * @param poolManager 历史新星池管理器（按时间激活）
 */
class ProspectDiscoveryService(
    private val databaseManager: DatabaseManager,
    private val config: ProspectConfig = ProspectConfig.DEFAULT,
    private val poolManager: ProspectPoolManager = ProspectPoolManager(databaseManager)
) : ProspectDiscoveryBridge {

    /**
     * 获取指定地区内可发现的历史新星（V0.2 08 §三.5）。
     *
     * 由 [com.greendynasty.football.scouting.core.PlayerDiscoveryEngine.getCandidates] 调用，
     * 历史新星与普通球员合并为候选池后参与 7 因子发现概率计算。
     *
     * @param saveId 存档 ID
     * @param regionCode 地区代码（如 "BRA" / "POR"）
     * @param currentDate 当前游戏内日期（仅返回 discoverable_from ≤ 当前日期的新星）
     * @return 可发现的历史新星候选列表（已转为 CandidatePlayer）
     */
    override suspend fun getDiscoverableProspectsInRegion(
        saveId: Int,
        regionCode: String,
        currentDate: LocalDate
    ): List<CandidatePlayer> = withContext(Dispatchers.IO) {
        // 1. 确保到期新星已激活（V1 简化：球探查询时也尝试激活一次，防止 T07 推进未执行）
        runCatching { poolManager.activateProspects(currentDate, saveId) }

        // 2. 查询该地区 ACTIVE 状态的新星
        val stateDao = databaseManager.prospectStateDao()
        val activeStates = stateDao.getActiveUndiscoveredInRegion(saveId, regionCode)

        if (activeStates.isEmpty()) return@withContext emptyList()

        // 3. 联表 history.player 填充球员信息
        val playerDao = databaseManager.historyPlayerDao()
        val results = mutableListOf<CandidatePlayer>()

        for (state in activeStates) {
            val player = playerDao.getPlayer(state.playerId) ?: continue
            val age = calculateAge(player.birthDate, currentDate)
            if (age < 0) continue

            results.add(
                CandidatePlayer(
                    playerId = player.playerId,
                    name = player.displayName ?: player.realName,
                    age = age,
                    position = player.primaryPosition ?: "CM",
                    regionCode = regionCode,
                    clubId = state.currentClubId,
                    leagueTier = 1, // V1 简化：默认顶级联赛
                    monthsLeft = 36, // V1 简化：历史新星默认 36 月
                    reputation = state.currentPa * 70 / 100, // V1 简化：声望按 PA 估值
                    currentAbility = state.currentCa,
                    potentialAbility = state.currentPa,
                    isHistoricalProspect = true
                )
            )
        }

        results
    }

    /**
     * 球探发现历史新星后通知 T15 更新星状态（V0.2 08 §六）。
     *
     * 由 [com.greendynasty.football.scouting.core.PlayerDiscoveryEngine.tryDiscover] 在
     * 概率性发现命中后调用。
     *
     * 行为：
     * 1. 更新 prospect_state.status = DISCOVERED
     * 2. 记录 discovered_by_club_id / discovered_date
     * 3. 写入路径事件 DISCOVERED（V1 简化：不触发蝴蝶，发现不算干预）
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param scoutId 球探 ID
     * @param hiredId 雇佣记录 ID
     * @param currentDate 当前游戏内日期
     */
    override suspend fun onProspectDiscovered(
        saveId: Int,
        playerId: Int,
        scoutId: Int,
        hiredId: Int,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        val stateDao = databaseManager.prospectStateDao()
        val eventDao = databaseManager.prospectPathEventDao()
        val hiredDao = databaseManager.saveScoutHiredDao()
        val scoutDao = databaseManager.historyScoutDao()

        // 1. 查找新星状态
        val state = stateDao.getByPlayer(saveId, playerId) ?: return@withContext
        if (state.status != ProspectStatus.ACTIVE.code) return@withContext

        // 2. 查找雇佣记录获取俱乐部 ID
        val hired: SaveScoutHiredEntity? = hiredDao.get(hiredId)
        val scout: ScoutEntity? = scoutDao.getScout(scoutId)
        val clubId = hired?.clubId ?: return@withContext

        // 3. 更新星状态为 DISCOVERED
        stateDao.markDiscovered(saveId, state.prospectId, clubId, currentDate.toString())

        // 4. 写入路径事件
        eventDao.insert(
            ProspectPathEventEntity(
                saveId = saveId,
                prospectId = state.prospectId,
                playerId = playerId,
                eventType = ProspectPathEventType.DISCOVERED.code,
                eventDate = currentDate.toString(),
                toClubId = clubId,
                isDefaultPath = 1, // 发现本身不算干预，仍按默认路径
                summary = "球探 ${scout?.name ?: ""} 在 ${state.regionCode} 发现该新星"
            )
        )
    }

    /**
     * V0.1 08 §三.5 发现概率（V1 简化版）
     *
     * 基于球探能力 + 地区知识（不含任务匹配，由 T14 7 因子公式覆盖）。
     *
     * 注意：本方法 V1 暂未启用，发现概率完全由 T14 PlayerDiscoveryEngine 7 因子公式计算，
     * 历史新星与普通球员统一进入候选池后等概率判定。
     */
    @Suppress("unused")
    private suspend fun calculateDiscoveryProbability(
        scout: ScoutEntity,
        state: ProspectStateEntity,
        regionCode: String,
        hired: SaveScoutHiredEntity
    ): Double {
        // 因子 1：地区知识（0-100 → 0-1）
        val regionKnowledge = databaseManager.saveScoutRegionKnowledgeDao()
            .get(hired.saveId, hired.hiredId, regionCode)?.knowledgeValue
            ?: 20
        val knowledgeFactor = regionKnowledge / 100.0

        // 因子 2：潜力判断（0-20 → 0-1）
        val potentialFactor = scout.judgingPotential / 20.0

        // 因子 3：人脉（0-20 → 0-1）
        val networkFactor = scout.networkLevel / 20.0

        // 因子 4：传奇等级加成（0-5 → 0-0.2）
        val legendBonus = (state.currentPa - 80).coerceAtLeast(0) / 100.0

        // 因子 5：随机扰动
        val randomFactor = Random.nextDouble()

        val probability = (
            knowledgeFactor * config.regionKnowledgeWeight +
                potentialFactor * config.potentialJudgmentWeight +
                networkFactor * 0.15 +
                legendBonus +
                randomFactor * 0.05 +
                config.baseDiscoveryProbability * 0.20
            )

        return probability.coerceIn(0.0, config.maxProbabilityPerAttempt)
    }

    /** 根据出生日期计算年龄。 */
    private fun calculateAge(birthDate: String?, currentDate: LocalDate): Int {
        if (birthDate.isNullOrBlank()) return -1
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            java.time.Period.between(birth, currentDate).years
        }.getOrDefault(-1)
    }
}
