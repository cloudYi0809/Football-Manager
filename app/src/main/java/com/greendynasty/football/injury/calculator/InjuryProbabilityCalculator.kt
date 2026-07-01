package com.greendynasty.football.injury.calculator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.injury.model.InjuryConfig
import com.greendynasty.football.injury.model.InjuryRiskScore
import com.greendynasty.football.injury.model.MatchInjuryContext
import com.greendynasty.football.injury.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import kotlin.random.Random

/**
 * 伤病发生概率计算器（T08.1）
 *
 * 实现 V0.1 07 §六.2 的 7 因子伤病概率公式：
 * ```
 * 伤病概率 =
 *   伤病倾向 × 0.25
 * + 当前体能反向值 × 0.20
 * + 比赛强度 × 0.15
 * + 训练强度 × 0.15
 * + 赛程密度 × 0.10
 * + 年龄因素 × 0.10
 * + 随机因素 × 0.05
 * ```
 *
 * 任务书 T08.1 强调的 4 主因子（伤病倾向 / 体能 / 比赛强度 / 近期比赛密度）为本公式的核心权重项，
 * 其余 3 因子（训练强度 / 年龄 / 随机）按 V0.2 算法文档补全，权重可配置。
 *
 * 修正因子：
 * - 伤病历史修正（重伤史每次 +10%）
 * - 位置修正（门将 0.4 / 前锋 1.25 等）
 *
 * @param databaseManager 三库入口
 * @param config 伤病配置
 */
class InjuryProbabilityCalculator(
    private val databaseManager: DatabaseManager,
    private val config: InjuryConfig = InjuryConfig.getDefault()
) {

    /**
     * 计算比赛中单球员单次判定的伤病概率（T02 比赛引擎回调使用）
     *
     * @return 0.0-0.5 的最终概率
     */
    suspend fun calculateMatchRisk(saveId: Int, playerId: Int, ctx: MatchInjuryContext): Double =
        withContext(Dispatchers.IO) {
            val player = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
                ?: return@withContext 0.0
            val attrs = getPlayerAttributes(playerId)
            val history = databaseManager.historyPlayerDao()

            val w = config.riskWeights

            // 1. 伤病倾向（injury_proneness 0-100 → 0-1）
            val injuryProneness = (attrs?.injuryProneness ?: 50) / 100.0

            // 2. 当前体能反向值（体能越低风险越高）
            val conditionInverse = (100 - player.condition).coerceIn(0, 100) / 100.0

            // 3. 比赛强度（0-1）
            val matchIntensity = ctx.matchIntensity.coerceIn(0, 10) / 10.0

            // 4. 训练强度（最近一次训练强度，0-1）
            val trainingIntensity = (ctx.recentTrainingIntensity ?: 5).coerceIn(0, 10) / 10.0

            // 5. 赛程密度（最近 14 天比赛数 / 6，0-1+）
            val fixtureDensity = calculateFixtureDensity(saveId, player.currentClubId, ctx.matchDate)

            // 6. 年龄因素
            val age = calculateAge(playerId, ctx.matchDate)
            val ageFactor = if (age > 0) config.ageRiskTable.getRiskFactor(age) else 1.0

            // 7. 随机因素
            val randomFactor = Random.nextDouble()

            val weightedSum = (
                injuryProneness * w.injuryProneness +
                    conditionInverse * w.conditionInverse +
                    matchIntensity * w.matchIntensity +
                    trainingIntensity * w.trainingIntensity +
                    fixtureDensity * w.fixtureDensity +
                    ageFactor * w.ageFactor +
                    randomFactor * w.randomFactor
                )

            // 归一化后乘以基础概率
            val sum = w.sum.takeIf { it > 0 } ?: 1.0
            val normalizedRisk = (weightedSum / sum) * config.baseMatchInjuryProbability

            // 8. 伤病历史修正（重伤史每次 +10%）
            val historyModifier = calculateHistoryModifier(saveId, playerId)

            // 9. 位置修正
            val position = databaseManager.historyPlayerDao().getPlayer(playerId)?.primaryPosition
            val positionModifier = position?.let { config.positionRiskModifier[it] } ?: 1.0

            (normalizedRisk * historyModifier * positionModifier).coerceIn(0.0, 0.5)
        }

    /**
     * 计算训练中单球员的伤病概率（T07 TrainingTask 调用）
     *
     * @param trainingIntensity 训练强度 1-10
     * @return 0.0-0.3 的最终概率
     */
    suspend fun calculateTrainingRisk(
        saveId: Int, playerId: Int, trainingIntensity: Int, currentDate: LocalDate
    ): Double = withContext(Dispatchers.IO) {
        val player = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
            ?: return@withContext 0.0
        val attrs = getPlayerAttributes(playerId)
        val w = config.riskWeights

        val injuryProneness = (attrs?.injuryProneness ?: 50) / 100.0
        val conditionInverse = (100 - player.condition).coerceIn(0, 100) / 100.0
        val trainingFactor = trainingIntensity.coerceIn(0, 10) / 10.0
        val fixtureDensity = calculateFixtureDensity(saveId, player.currentClubId, currentDate)
        val age = calculateAge(playerId, currentDate)
        val ageFactor = if (age > 0) config.ageRiskTable.getRiskFactor(age) else 1.0
        val randomFactor = Random.nextDouble()

        // 训练无比赛强度，用训练强度半折替代
        val weightedSum = (
            injuryProneness * w.injuryProneness +
                conditionInverse * w.conditionInverse +
                trainingFactor * w.trainingIntensity +
                trainingFactor * w.matchIntensity * 0.5 +
                fixtureDensity * w.fixtureDensity +
                ageFactor * w.ageFactor +
                randomFactor * w.randomFactor
            )
        val sum = w.sum.takeIf { it > 0 } ?: 1.0
        val normalizedRisk = (weightedSum / sum) * config.baseTrainingInjuryProbability
        val historyModifier = calculateHistoryModifier(saveId, playerId)

        (normalizedRisk * historyModifier).coerceIn(0.0, 0.3)
    }

    /**
     * 全队伤病风险评分（医疗中心"疲劳风险"模块加载）
     *
     * 仅计算健康球员，返回 0-100 风险评分列表，按风险降序排列。
     */
    suspend fun calculateForSquad(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): List<InjuryRiskScore> = withContext(Dispatchers.IO) {
        val players = databaseManager.savePlayerStateDao().getByClub(saveId, clubId)
        players.map { player ->
            val attrs = if (player.injuryStatus == "healthy") getPlayerAttributes(player.playerId) else null
            val risk = if (attrs != null && player.injuryStatus == "healthy") {
                calculateSquadRiskScore(player, attrs, currentDate)
            } else 0.0
            val name = databaseManager.historyPlayerDao().getPlayer(player.playerId)?.displayName
                ?: "球员${player.playerId}"
            InjuryRiskScore(
                playerId = player.playerId,
                playerName = name,
                riskScore = (risk * 100).toInt().coerceIn(0, 100),
                riskLevel = classifyRiskLevel(risk),
                mainFactors = explainRiskFactors(player, attrs, currentDate)
            )
        }.sortedByDescending { it.riskScore }
    }

    // ==================== 内部计算 ====================

    /** 单球员风险评分（0-1，用于医疗中心排序展示，不乘基础概率的小概率门槛） */
    private suspend fun calculateSquadRiskScore(
        player: SavePlayerStateEntity, attrs: PlayerAttributesEntity, currentDate: LocalDate
    ): Double {
        val w = config.riskWeights
        val injuryProneness = attrs.injuryProneness / 100.0
        val conditionInverse = (100 - player.condition).coerceIn(0, 100) / 100.0
        val fixtureDensity = calculateFixtureDensity(saveId = 0, player.currentClubId, currentDate)
        val age = calculateAge(player.playerId, currentDate)
        val ageFactor = if (age > 0) config.ageRiskTable.getRiskFactor(age) else 1.0

        val score = (
            injuryProneness * w.injuryProneness +
                conditionInverse * w.conditionInverse +
                fixtureDensity * w.fixtureDensity +
                ageFactor * w.ageFactor
            ) / (w.injuryProneness + w.conditionInverse + w.fixtureDensity + w.ageFactor).coerceAtLeast(0.01)

        val historyModifier = runCatching {
            calculateHistoryModifier(0, player.playerId)
        }.getOrDefault(1.0)
        return (score * historyModifier).coerceIn(0.0, 1.0)
    }

    /** 计算最近 14 天比赛密度（场数 / 6） */
    private suspend fun calculateFixtureDensity(saveId: Int, clubId: Int?, date: LocalDate): Double {
        if (clubId == null) return 0.0
        return runCatching {
            val start = date.minusDays(14).toString()
            val end = date.toString()
            // saveId=0 用于全队评分场景（无具体 saveId），直接返回 0 避免误查
            if (saveId <= 0) return@runCatching 0.0
            val matches = databaseManager.saveMatchDao().getByDateRange(saveId, start, end)
            val count = matches.count {
                (it.homeClubId == clubId || it.awayClubId == clubId) && it.status == "finished"
            }
            (count / 6.0).coerceAtMost(2.0)
        }.getOrDefault(0.0)
    }

    /** 重伤历史修正：每次重伤史 +10% */
    private suspend fun calculateHistoryModifier(saveId: Int, playerId: Int): Double {
        if (saveId <= 0) return 1.0
        return runCatching {
            val majorCount = databaseManager.saveInjuryDao()
                .getMajorInjuryHistory(saveId, playerId).size
            1.0 + majorCount * 0.10
        }.getOrDefault(1.0)
    }

    /** 计算球员年龄（基于 history.player.birth_date） */
    private suspend fun calculateAge(playerId: Int, currentDate: LocalDate): Int {
        return runCatching {
            val player: PlayerEntity? = databaseManager.historyPlayerDao().getPlayer(playerId)
            val birthStr = player?.birthDate ?: return@runCatching 0
            val birth = LocalDate.parse(birthStr)
            Period.between(birth, currentDate).years
        }.getOrDefault(0)
    }

    private suspend fun getPlayerAttributes(playerId: Int): PlayerAttributesEntity? {
        return runCatching {
            databaseManager.historyPlayerDao().getLatestAttributes(playerId)
        }.getOrNull()
    }

    private fun classifyRiskLevel(risk: Double): RiskLevel = when {
        risk >= 0.15 -> RiskLevel.CRITICAL
        risk >= 0.08 -> RiskLevel.HIGH
        risk >= 0.04 -> RiskLevel.MEDIUM
        risk > 0 -> RiskLevel.LOW
        else -> RiskLevel.NONE
    }

    /** 解释主要风险因子（用于 UI 展示） */
    private fun explainRiskFactors(
        player: SavePlayerStateEntity,
        attrs: PlayerAttributesEntity?,
        currentDate: LocalDate
    ): List<String> {
        val factors = mutableListOf<String>()
        if (player.condition < 40) factors.add("体能过低 ${player.condition}")
        attrs?.let {
            if (it.injuryProneness >= 70) factors.add("伤病倾向高 ${it.injuryProneness}")
            if (it.stamina < 40) factors.add("体质偏弱")
        }
        if (player.injuryStatus == "healthy_forced") factors.add("强行复出中")
        return factors
    }
}
