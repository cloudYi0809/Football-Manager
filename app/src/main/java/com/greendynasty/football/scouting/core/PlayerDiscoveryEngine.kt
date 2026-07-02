package com.greendynasty.football.scouting.core

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.data.SaveScoutHiredEntity
import com.greendynasty.football.scouting.data.SaveScoutTaskEntity
import com.greendynasty.football.scouting.integration.ProspectDiscoveryBridge
import com.greendynasty.football.scouting.model.BudgetLevel
import com.greendynasty.football.scouting.model.CandidatePlayer
import com.greendynasty.football.scouting.model.Discovery
import com.greendynasty.football.scouting.model.ScoutTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * T14 球员发现引擎（V0.2 08 §三.5 7 因子发现概率公式）。
 *
 * 7 因子加权：
 * ```
 * probability = (regionKnowledge / 100) × 0.25
 *             + (potentialJudgment / 20) × 0.20
 *             + (network / 20) × 0.15
 *             + taskMatch × 0.15
 *             + budgetFactor × 0.10
 *             + regionDensity × 0.10
 *             + random × 0.05
 * ```
 * 单次概率上限 [ScoutConfig.maxProbabilityPerAttempt]（默认 0.8）。
 *
 * 性能保障：
 * - 候选池上限 [ScoutConfig.candidatePoolLimit]（默认 200），超出按随机采样
 * - 每日发现上限 [ScoutConfig.maxDiscoveriesPerDay]（默认 3）
 * - 已有报告球员自动跳过（避免重复发现）
 *
 * @param databaseManager 三库管理入口
 * @param densityProvider 地区人才密度提供者
 * @param prospectBridge 历史新星衔接桥（T15）
 * @param config 球探配置
 */
class PlayerDiscoveryEngine(
    private val databaseManager: DatabaseManager,
    private val densityProvider: RegionTalentDensityProvider,
    private val prospectBridge: ProspectDiscoveryBridge,
    private val config: ScoutConfig = ScoutConfig.DEFAULT
) {

    /**
     * 尝试发现球员（V0.2 08 §三.5）。
     *
     * 由 [com.greendynasty.football.scouting.ScoutingService.advanceDaily] 在每日推进时调用。
     *
     * @param task 进行中的球探任务
     * @param currentDate 当前游戏内日期
     * @return 发现的球员列表（每日上限 [ScoutConfig.maxDiscoveriesPerDay]）
     */
    suspend fun tryDiscover(
        task: SaveScoutTaskEntity,
        currentDate: java.time.LocalDate
    ): List<Discovery> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Discovery>()

        // 1. 获取雇佣球探
        val hired = databaseManager.saveScoutHiredDao().get(task.hiredId)
            ?: return@withContext results
        val scout = databaseManager.historyScoutDao().getScout(hired.scoutId)
            ?: return@withContext results

        // 2. 构建候选池（地区内球员 + 历史新星）
        val candidates = getCandidates(task, currentDate)
        if (candidates.isEmpty()) return@withContext results

        // 3. 候选池上限保护（超出随机采样）
        val pooled = if (candidates.size > config.candidatePoolLimit) {
            candidates.shuffled().take(config.candidatePoolLimit)
        } else {
            candidates
        }

        // 4. 每日发现上限保护
        var foundToday = 0
        val reportDao = databaseManager.saveScoutReportDao()

        for (candidate in pooled.shuffled()) {
            if (foundToday >= config.maxDiscoveriesPerDay) break

            // 已有报告则跳过（避免重复发现）
            val existing = reportDao.getByPlayer(task.saveId, candidate.playerId, task.clubId)
            if (existing != null) continue

            // 计算发现概率
            val probability = calculateProbability(scout, hired, task, candidate)
            if (Random.nextDouble() < probability) {
                results.add(
                    Discovery(
                        playerId = candidate.playerId,
                        playerName = candidate.name,
                        playerAge = candidate.age,
                        playerPosition = candidate.position,
                        playerRegion = candidate.regionCode,
                        isHistoricalProspect = candidate.isHistoricalProspect,
                        probability = probability
                    )
                )
                foundToday++

                // 历史新星发现通知 T15
                if (candidate.isHistoricalProspect) {
                    prospectBridge.onProspectDiscovered(
                        task.saveId, candidate.playerId, scout.scoutId, hired.hiredId, currentDate
                    )
                }
            }
        }

        results
    }

    /**
     * 7 因子发现概率计算（V0.2 08 §三.5）。
     *
     * 7 因子：
     * 1. 地区知识（权重 0.25）
     * 2. 潜力判断（权重 0.20）
     * 3. 人脉（权重 0.15）
     * 4. 任务匹配度（权重 0.15）
     * 5. 预算（权重 0.10）
     * 6. 地区人才密度（权重 0.10）
     * 7. 随机扰动（权重 0.05）
     *
     * 任务类型加成（discoveryBonus）叠加到最终概率上。
     */
    private suspend fun calculateProbability(
        scout: com.greendynasty.football.data.history.entity.ScoutEntity,
        hired: SaveScoutHiredEntity,
        task: SaveScoutTaskEntity,
        candidate: CandidatePlayer
    ): Double {
        // 因子 1：地区知识（0-100 → 0-1）
        val regionKnowledge = databaseManager.saveScoutRegionKnowledgeDao()
            .get(hired.saveId, hired.hiredId, task.regionCode)?.knowledgeValue
            ?: config.baseRegionKnowledge
        val knowledgeFactor = regionKnowledge / 100.0

        // 因子 2：潜力判断（0-20 → 0-1）
        val potentialFactor = scout.judgingPotential / 20.0

        // 因子 3：人脉（0-20 → 0-1，注意 history.scout 字段名为 network_level）
        val networkFactor = scout.networkLevel / 20.0

        // 因子 4：任务匹配度（0-1）
        val taskMatch = calculateTaskMatch(task, candidate)

        // 因子 5：预算（displayFactor：低 0.6 / 中 0.8 / 高 1.0）
        val budgetFactor = runCatching { BudgetLevel.valueOf(task.budgetLevel) }
            .getOrDefault(BudgetLevel.MEDIUM).displayFactor

        // 因子 6：地区人才密度（0-1）
        val regionDensity = densityProvider.getDensity(task.regionCode)

        // 因子 7：随机扰动（0-1）
        val randomFactor = Random.nextDouble()

        // 加权求和
        val probability = (
            knowledgeFactor * config.regionKnowledgeWeight +
                potentialFactor * config.potentialJudgmentWeight +
                networkFactor * config.networkWeight +
                taskMatch * config.taskMatchWeight +
                budgetFactor * config.budgetWeight +
                regionDensity * config.regionDensityWeight +
                randomFactor * config.randomWeight
            )

        // 任务类型发现加成
        val taskBonus = config.taskTypeParams[task.taskType]?.discoveryBonus ?: 0.0
        val finalProbability = probability + taskBonus

        // 单次概率上限保护
        return finalProbability.coerceIn(0.0, config.maxProbabilityPerAttempt)
    }

    /**
     * 任务匹配度计算（0-1，V0.2 08 §三.5）。
     *
     * 8 种任务类型对候选球员的契合度评分：
     * - REGION_SEARCH：基础 0.8（广撒网）
     * - POSITION_SEARCH：位置匹配 1.0 / 同位置组 0.6 / 其他 0.2
     * - AGE_GROUP_SEARCH：年龄范围内 1.0 / 其他 0.2
     * - CLUB_OBSERVATION：目标俱乐部 1.0 / 其他 0.1
     * - YOUTH_TOURNAMENT：年龄 ≤20 1.0 / 其他 0.2
     * - CONTRACT_OPPORTUNITY：合同剩余 ≤12 月 1.0 / ≤24 月 0.7 / 其他 0.3
     * - LOWER_LEAGUE：低级别联赛（tier≥2）1.0 / 其他 0.3
     * - STAR_TRACKING：声望 ≥70 1.0 / 其他 0.4
     */
    private fun calculateTaskMatch(task: SaveScoutTaskEntity, candidate: CandidatePlayer): Double {
        var match = 0.5 // 基础匹配
        val taskType = runCatching { ScoutTaskType.valueOf(task.taskType) }.getOrNull()
            ?: return match

        when (taskType) {
            ScoutTaskType.REGION_SEARCH -> match = 0.8
            ScoutTaskType.POSITION_SEARCH -> {
                val target = task.targetPosition
                if (target != null && candidate.position == target) match = 1.0
                else if (target != null && samePositionGroup(candidate.position, target)) match = 0.6
                else match = 0.2
            }
            ScoutTaskType.AGE_GROUP_SEARCH -> {
                match = if (candidate.age in task.ageMin..task.ageMax) 1.0 else 0.2
            }
            ScoutTaskType.CLUB_OBSERVATION -> {
                match = if (candidate.clubId == task.targetClubId) 1.0 else 0.1
            }
            ScoutTaskType.YOUTH_TOURNAMENT -> {
                match = if (candidate.age <= 20) 1.0 else 0.2
            }
            ScoutTaskType.CONTRACT_OPPORTUNITY -> {
                match = when {
                    candidate.monthsLeft <= 12 -> 1.0
                    candidate.monthsLeft <= 24 -> 0.7
                    else -> 0.3
                }
            }
            ScoutTaskType.LOWER_LEAGUE -> {
                match = if (candidate.leagueTier >= 2) 1.0 else 0.3
            }
            ScoutTaskType.STAR_TRACKING -> {
                match = if (candidate.reputation >= 70) 1.0 else 0.4
            }
        }

        // 年龄范围叠加过滤（任务指定了年龄范围且候选不在范围内）
        if (candidate.age !in task.ageMin..task.ageMax && task.ageMin > 0) {
            match *= 0.3
        }
        return match
    }

    /** 同位置组判定（V0.2 08 §三.5，简化版）。 */
    private fun samePositionGroup(pos1: String, pos2: String): Boolean {
        val group = when (pos1.uppercase()) {
            "ST", "CF", "LW", "RW" -> "FW"
            "CM", "AM", "DM", "LM", "RM" -> "MF"
            "CB", "LB", "RB", "LWB", "RWB" -> "DF"
            "GK" -> "GK"
            else -> "OTHER"
        }
        val otherGroup = when (pos2.uppercase()) {
            "ST", "CF", "LW", "RW" -> "FW"
            "CM", "AM", "DM", "LM", "RM" -> "MF"
            "CB", "LB", "RB", "LWB", "RWB" -> "DF"
            "GK" -> "GK"
            else -> "OTHER"
        }
        return group == otherGroup && group != "OTHER"
    }

    /**
     * 构建候选池（V0.2 08 §三.5）。
     *
     * 候选池 = 地区内普通球员 + T15 历史新星。
     * 性能：通过 (save_id, region_code) 索引查询，候选池上限 200。
     */
    private suspend fun getCandidates(
        task: SaveScoutTaskEntity,
        currentDate: java.time.LocalDate
    ): List<CandidatePlayer> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<CandidatePlayer>()

        // 1. 普通球员：从 save_player_state 按 nationality/region 查询
        //    V1 简化：通过 PlayerDao 全量后按国籍映射地区过滤（V2 可加索引优化）
        val playerDao = databaseManager.historyPlayerDao()
        val playerStateDao = databaseManager.savePlayerStateDao()

        // 取该地区所有球员（按国籍匹配地区）
        val regionNationalities = nationalityOfRegion(task.regionCode)
        val playersInRegion = mutableListOf<Pair<PlayerEntity, SavePlayerStateEntity>>()

        for (nationality in regionNationalities) {
            val players = playerDao.getPlayersByNationality(nationality).first()
            for (player in players) {
                val state = playerStateDao.getByPlayer(task.saveId, player.playerId)
                if (state != null && state.careerStatus == "active") {
                    playersInRegion.add(player to state)
                }
            }
        }

        for ((player, state) in playersInRegion) {
            val age = calculateAge(player.birthDate, currentDate)
            if (age < 0) continue // 出生日期无效
            candidates.add(
                CandidatePlayer(
                    playerId = player.playerId,
                    name = player.displayName ?: player.realName,
                    age = age,
                    position = player.primaryPosition ?: "CM",
                    regionCode = task.regionCode,
                    clubId = state.currentClubId,
                    leagueTier = 1, // V1 简化：默认顶级联赛
                    monthsLeft = calculateMonthsLeft(state.contractUntil, currentDate),
                    reputation = state.marketValue / 100000, // V1 简化：声望按身价映射
                    currentAbility = state.currentCa,
                    potentialAbility = state.currentPa,
                    isHistoricalProspect = false
                )
            )
        }

        // 2. 历史新星（T15 提供）
        val prospects = prospectBridge.getDiscoverableProspectsInRegion(
            task.saveId, task.regionCode, currentDate
        )
        candidates.addAll(prospects)

        candidates
    }

    /** 地区代码 → 国籍列表映射（V0.2 08 §三.2，简化版）。 */
    private fun nationalityOfRegion(regionCode: String): List<String> {
        return when (regionCode) {
            "ENG" -> listOf("英格兰", "English", "England")
            "ESP" -> listOf("西班牙", "Spanish", "Spain")
            "ITA" -> listOf("意大利", "Italian", "Italy")
            "GER" -> listOf("德国", "German", "Germany")
            "FRA" -> listOf("法国", "French", "France")
            "NED" -> listOf("荷兰", "Dutch", "Netherlands")
            "POR" -> listOf("葡萄牙", "Portuguese", "Portugal")
            "BRA" -> listOf("巴西", "Brazilian", "Brazil")
            "ARG" -> listOf("阿根廷", "Argentine", "Argentina")
            "URU" -> listOf("乌拉圭", "Uruguayan", "Uruguay")
            "EEU" -> listOf("克罗地亚", "塞尔维亚", "波兰", "捷克", "Romanian", "Croatian")
            "NOR" -> listOf("瑞典", "挪威", "丹麦", "芬兰", "Icelandic", "Swedish", "Norwegian", "Danish")
            "WAF" -> listOf("尼日利亚", "科特迪瓦", "加纳", "塞内加尔", "Nigerian", "Ghanaian", "Senegalese")
            "ASI" -> listOf("日本", "韩国", "中国", "Iranian", "Japanese", "Korean", "Chinese")
            "NAM" -> listOf("美国", "加拿大", "墨西哥", "American", "Canadian", "Mexican")
            else -> emptyList()
        }
    }

    /** 根据出生日期计算年龄（V0.2 08 §三.5）。 */
    private fun calculateAge(birthDate: String?, currentDate: java.time.LocalDate): Int {
        if (birthDate.isNullOrBlank()) return -1
        return runCatching {
            val birth = java.time.LocalDate.parse(birthDate.take(10))
            java.time.Period.between(birth, currentDate).years
        }.getOrDefault(-1)
    }

    /** 根据合同到期日计算剩余月数（V0.2 08 §三.5）。 */
    private fun calculateMonthsLeft(contractUntil: String?, currentDate: java.time.LocalDate): Int {
        if (contractUntil.isNullOrBlank()) return 36 // V1 简化：无合同信息默认 36 月
        return runCatching {
            val until = java.time.LocalDate.parse(contractUntil.take(10))
            java.time.Period.between(currentDate, until).let { it.years * 12 + it.months }
        }.getOrDefault(36).coerceAtLeast(0)
    }
}
