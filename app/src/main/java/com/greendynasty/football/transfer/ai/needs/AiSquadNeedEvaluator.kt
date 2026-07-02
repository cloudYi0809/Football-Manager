package com.greendynasty.football.transfer.ai.needs

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.ai.config.BasicAiConfig
import com.greendynasty.football.transfer.ai.model.PositionNeed
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValueEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T13.1 AI 阵容需求评估器（V0.2 `05_AI俱乐部决策模型.md` §四 基础版）。
 *
 * 位置需求评分（6 因子，固定权重）：
 * ```
 * position_need_score =
 *   starter_gap * 0.40
 * + backup_gap * 0.20
 * + average_age_risk * 0.15
 * + injury_risk * 0.10
 * + contract_expiry_risk * 0.10
 * + tactical_importance * 0.05
 * ```
 *
 * 基础版 tactical_importance 固定 0.5（T18 才按战术风格区分）。
 *
 * 6 因子说明：
 * 1. **starter_gap**：主力缺口（目标 1 人，缺口 > 0 急需，富余 < 0 可卖）
 * 2. **backup_gap**：替补缺口（目标 1 人）
 * 3. **average_age_risk**：位置平均年龄风险（>32 老化 / <19 经验不足）
 * 4. **injury_risk**：伤病风险（受伤人数 + 伤病倾向）
 * 5. **contract_expiry_risk**：合同到期风险（12 月内到期比例）
 * 6. **tactical_importance**：战术重要性（基础版固定 0.5）
 *
 * @param databaseManager 三库管理入口
 * @param config AI 转会配置
 * @param valueEstimator T11 球员估价器（复用年龄计算等工具方法）
 */
class AiSquadNeedEvaluator(
    private val databaseManager: DatabaseManager,
    private val config: BasicAiConfig = BasicAiConfig.DEFAULT,
    private val valueEstimator: PlayerValueEstimator = PlayerValueEstimator()
) {

    /**
     * 分析指定俱乐部的阵容短板。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentDate 当前游戏日期
     * @return 按需求评分降序排列的位置需求列表（仅含 needScore > 过滤阈值的位置）
     */
    suspend fun analyze(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ): List<PositionNeed> = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()

        // 1. 获取俱乐部全部球员存档状态
        val players = saveDb.savePlayerStateDao().getByClub(saveId, clubId)
        if (players.isEmpty()) return@withContext emptyList()

        // 2. 批量查询球员基础信息（history.db）获取位置/出生日期
        val playerIds = players.map { it.playerId }
        val historyPlayers = databaseManager.historyPlayerDao().getPlayersByIds(playerIds)
            .associateBy { it.playerId }

        // 3. 按位置分组（主要位置 + 次要位置均可覆盖）
        val positionGroups = mutableMapOf<String, MutableList<SavePlayerStateEntity>>()
        for (state in players) {
            val player = historyPlayers[state.playerId]
            val primaryPos = player?.primaryPosition ?: "CM"
            positionGroups.getOrPut(primaryPos) { mutableListOf() }.add(state)
        }

        // 4. 对每个标准位置计算 6 因子需求评分
        val w = config.needScoreWeights
        val results = config.standardPositions.map { pos ->
            val posPlayers = positionGroups[pos] ?: emptyList()

            val starterGap = calculateStarterGap(posPlayers)
            val backupGap = calculateBackupGap(posPlayers)
            val ageRisk = calculateAgeRisk(posPlayers, historyPlayers, currentDate)
            val injuryRisk = calculateInjuryRisk(posPlayers)
            val contractRisk = calculateContractExpiryRisk(posPlayers, currentDate)
            val tacticalImportance = 0.5 // 基础版固定 0.5，T18 才按战术区分

            val needScore = (
                starterGap * w.starterGap +
                    backupGap * w.backupGap +
                    ageRisk * w.averageAgeRisk +
                    injuryRisk * w.injuryRisk +
                    contractRisk * w.contractExpiryRisk +
                    tacticalImportance * w.tacticalImportance
                ) * 100

            PositionNeed(
                position = pos,
                needScore = needScore.coerceIn(0.0, 100.0),
                starterGap = starterGap,
                backupGap = backupGap,
                averageAgeRisk = ageRisk,
                injuryRisk = injuryRisk,
                contractExpiryRisk = contractRisk,
                tacticalImportance = tacticalImportance
            )
        }

        // 5. 过滤急需位置并按评分降序排列
        results
            .filter { it.needScore > config.thresholds.needScoreFilterThreshold }
            .sortedByDescending { it.needScore }
    }

    /**
     * 因子 1：主力缺口（V0.2 §四）。
     *
     * 目标 1 名主力，缺口 > 0 急需，富余 < 0 可卖。
     * squadRole: starter / key_player / first_team 视为主力。
     */
    private fun calculateStarterGap(players: List<SavePlayerStateEntity>): Double {
        val targetStarters = 1
        val actualStarters = players.count {
            val role = it.squadRole?.lowercase()
            role == "starter" || role == "key_player" || role == "core" || role == "first_team"
        }
        val gap = targetStarters - actualStarters
        return when {
            gap > 0 -> gap.toDouble()
            gap == 0 -> 0.0
            else -> -1.0 // 多余可卖
        }
    }

    /**
     * 因子 2：替补缺口（V0.2 §四）。
     *
     * 目标 1 名替补，squadRole: backup / rotation / prospect 视为替补。
     */
    private fun calculateBackupGap(players: List<SavePlayerStateEntity>): Double {
        val targetBackups = 1
        val actualBackups = players.count {
            val role = it.squadRole?.lowercase()
            role == "backup" || role == "rotation" || role == "prospect" || role == "youth" || role == "substitute"
        }
        val gap = targetBackups - actualBackups
        return when {
            gap > 0 -> gap.toDouble()
            gap == 0 -> 0.0
            else -> -1.0
        }
    }

    /**
     * 因子 3：位置平均年龄风险（V0.2 §四）。
     *
     * | 平均年龄 | 风险 |
     * |---|---|
     * | > 32 | 0.9（老化风险高） |
     * | 30-32 | 0.5 |
     * | < 19 | 0.7（经验不足） |
     * | 其他 | 0.1 |
     */
    private fun calculateAgeRisk(
        players: List<SavePlayerStateEntity>,
        historyPlayers: Map<Int, com.greendynasty.football.data.history.entity.PlayerEntity>,
        currentDate: LocalDate
    ): Double {
        if (players.isEmpty()) return 1.0
        val ages = players.mapNotNull { state ->
            historyPlayers[state.playerId]?.birthDate?.let {
                runCatching { valueEstimator.computeAge(it, currentDate) }.getOrNull()
            }
        }
        if (ages.isEmpty()) return 0.5
        val avgAge = ages.average()
        return when {
            avgAge > 32 -> 0.9
            avgAge < 19 -> 0.7
            avgAge in 30.0..32.0 -> 0.5
            else -> 0.1
        }
    }

    /**
     * 因子 4：伤病风险（V0.2 §四）。
     *
     * 当前受伤人数 × 0.3 + 伤病倾向高（>70）人数 × 0.2，clamp 0-1。
     *
     * V1 简化：伤病倾向需从 history 属性表读取，但 T11 PlayerValueEstimator
     * 未暴露 injuryProneness，此处用受伤状态近似（V2 再接入属性表）。
     */
    private fun calculateInjuryRisk(
        players: List<SavePlayerStateEntity>
    ): Double {
        if (players.isEmpty()) return 1.0
        val injuredCount = players.count { it.injuryStatus != "healthy" }
        val proneCount = players.count { it.injuryStatus != "healthy" && it.careerStatus == "injured" }
        return (injuredCount * 0.3 + proneCount * 0.2).coerceIn(0.0, 1.0)
    }

    /**
     * 因子 5：合同到期风险（V0.2 §四）。
     *
     * 12 个月内合同到期人数占比，clamp 0-1。
     */
    private fun calculateContractExpiryRisk(
        players: List<SavePlayerStateEntity>,
        currentDate: LocalDate
    ): Double {
        if (players.isEmpty()) return 1.0
        val threshold = currentDate.plusMonths(12)
        val expiringCount = players.count { p ->
            p.contractUntil?.let {
                runCatching {
                    LocalDate.parse(it.take(10)).isBefore(threshold)
                }.getOrElse { false }
            } ?: true // 无合同视为高风险
        }
        return (expiringCount.toDouble() / players.size).coerceIn(0.0, 1.0)
    }
}
