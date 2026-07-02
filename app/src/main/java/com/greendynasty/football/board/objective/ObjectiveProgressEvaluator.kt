package com.greendynasty.football.board.objective

import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.board.model.BoardConfig
import com.greendynasty.football.board.model.ObjectiveProgress
import com.greendynasty.football.board.model.SeasonTargetEntity
import com.greendynasty.football.board.model.SeasonTargetEvaluation
import com.greendynasty.football.board.model.SeasonTargetType
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.economy.repository.EconomyRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T22 目标完成度评估器（V0.2 11 §四 + T22 方案 §四.2 GoalEvaluator + 任务要求 §二.3）。
 *
 * 职责：
 * 1. 赛季中实时评估目标完成概率（用于 UI 展示）
 * 2. 赛季末结算：5 类目标逐一评估 + 综合分计算（核心 70% + 次要 30%）
 * 3. 评估结果写入 [SeasonTargetEntity] 的 evaluationStatus / evaluationScore
 *
 * 综合分公式（V0.2 11 §四）：
 * ```
 * overall = core_total * 0.7 + secondary_total * 0.3
 * status = if overall >= 85: ACHIEVED
 *          elif overall >= 60: PARTIALLY
 *          else: FAILED
 * ```
 *
 * @param databaseManager 三库管理入口
 * @param economyRepository T17 经济仓库
 * @param clubProfileRepository T18 俱乐部画像仓库
 * @param config 董事会配置
 */
class ObjectiveProgressEvaluator(
    private val databaseManager: DatabaseManager,
    private val economyRepository: EconomyRepository,
    private val clubProfileRepository: ClubProfileRepository,
    private val config: BoardConfig = BoardConfig.DEFAULT
) {

    /**
     * 赛季中实时评估单项目标完成概率。
     *
     * 由 UI 调用展示目标进度。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 5 项目标进度列表（联赛 / 杯赛 / 欧战 / 财政 / 青训）
     */
    suspend fun evaluateProgress(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate
    ): List<ObjectiveProgress> {
        val target = databaseManager.getSaveDatabase().seasonTargetDao()
            .getBySeason(saveId, clubId, seasonId)
            ?: return emptyList()

        val currentLeaguePos = getCurrentLeaguePosition(saveId, clubId, seasonId)
        val currentWageRatio = try {
            economyRepository.buildFinancialState(saveId, clubId, currentDate.year).wageToIncomeRatio
        } catch (_: Exception) {
            0.0
        }
        val currentYouthPromotions = countYouthPromotionsThisSeason(saveId, clubId, seasonId)

        return listOf(
            evaluateLeagueProgress(target, currentLeaguePos),
            evaluateCupProgress(target),
            evaluateEuropeanProgress(target),
            evaluateFinancialProgress(target, currentWageRatio),
            evaluateYouthProgress(target, currentYouthPromotions)
        )
    }

    /**
     * 赛季末评估目标达成（5 类目标 + 综合分）。
     *
     * 由 T19 SeasonArchiveExecutor 调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 赛季目标评估结果，目标不存在时返回 empty
     */
    suspend fun evaluateSeasonTargets(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate
    ): SeasonTargetEvaluation {
        val target = databaseManager.getSaveDatabase().seasonTargetDao()
            .getBySeason(saveId, clubId, seasonId)
            ?: return SeasonTargetEvaluation.empty()

        val actualLeaguePosition = getCurrentLeaguePosition(saveId, clubId, seasonId)
        val actualWageRatio = try {
            economyRepository.buildFinancialState(saveId, clubId, currentDate.year).wageToIncomeRatio
        } catch (_: Exception) {
            0.0
        }
        val actualYouthPromotions = countYouthPromotionsThisSeason(saveId, clubId, seasonId)

        // 5 类目标评分
        val leagueScore = evaluateLeagueScore(target.leaguePositionTarget, actualLeaguePosition)
        val cupScore = evaluateCupScore(target.cupTarget, null) // V1 简化：杯赛实际结果暂未接入
        val europeanScore = evaluateEuropeanScore(target.europeanTarget, null)
        val financialScore = evaluateFinancialScore(target.financialWageRatioTarget, actualWageRatio)
        val youthScore = evaluateYouthScore(target.youthPromotionTarget, actualYouthPromotions)

        // 核心/次要目标分组计算
        val coreScores = mutableListOf<Pair<Double, Double>>() // (score, weight)
        val secondaryScores = mutableListOf<Pair<Double, Double>>()

        if (target.leaguePositionImportance == "CORE") coreScores.add(leagueScore to 0.35)
        else secondaryScores.add(leagueScore to 0.30)

        if (target.cupImportance == "CORE") coreScores.add(cupScore to 0.20)
        else secondaryScores.add(cupScore to 0.20)

        if (target.europeanImportance == "CORE") coreScores.add(europeanScore to 0.20)
        else secondaryScores.add(europeanScore to 0.15)

        if (target.financialImportance == "CORE") coreScores.add(financialScore to 0.15)
        else secondaryScores.add(financialScore to 0.20)

        secondaryScores.add(youthScore to 0.15)

        // 综合分：核心 70% + 次要 30%
        val coreTotal = if (coreScores.isNotEmpty())
            coreScores.sumOf { it.first * it.second } / coreScores.sumOf { it.second }
        else 0.0
        val secondaryTotal = if (secondaryScores.isNotEmpty())
            secondaryScores.sumOf { it.first * it.second } / secondaryScores.sumOf { it.second }
        else 0.0

        val overallScore = (
            coreTotal * config.seasonTarget.coreWeight +
                secondaryTotal * config.seasonTarget.secondaryWeight
            ).coerceIn(0.0, 100.0)

        val status = when {
            overallScore >= config.seasonTarget.achievedThreshold -> "ACHIEVED"
            overallScore >= config.seasonTarget.partiallyThreshold -> "PARTIALLY"
            else -> "FAILED"
        }

        val coreGoalFailed = listOf(
            target.leaguePositionImportance to leagueScore,
            target.cupImportance to cupScore,
            target.europeanImportance to europeanScore,
            target.financialImportance to financialScore
        ).any { it.first == "CORE" && it.second < 50.0 }

        // 写入评估结果
        databaseManager.getSaveDatabase().seasonTargetDao().updateEvaluation(
            target.id, status, overallScore, currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )

        return SeasonTargetEvaluation(
            seasonId = seasonId,
            overallScore = overallScore,
            status = status,
            leagueScore = leagueScore,
            cupScore = cupScore,
            europeanScore = europeanScore,
            financialScore = financialScore,
            youthScore = youthScore,
            coreGoalFailed = coreGoalFailed
        )
    }

    // ==================== 实时进度评估 ====================

    private fun evaluateLeagueProgress(target: SeasonTargetEntity, currentPos: Int?): ObjectiveProgress {
        val targetValue = "前 ${target.leaguePositionTarget} 名"
        val currentValue = currentPos?.let { "当前第 $it 名" } ?: "尚未开始"
        val progress = if (currentPos == null || currentPos <= 0) 50.0
        else when {
            currentPos <= target.leaguePositionTarget -> 100.0
            currentPos == target.leaguePositionTarget + 1 -> 80.0
            currentPos == target.leaguePositionTarget + 2 -> 60.0
            currentPos <= target.leaguePositionTarget + 4 -> 40.0
            else -> 20.0
        }
        val status = when {
            progress >= 80 -> "ON_TRACK"
            progress >= 50 -> "AT_RISK"
            else -> "BEHIND"
        }
        return ObjectiveProgress(
            targetType = SeasonTargetType.LEAGUE.name,
            targetValue = targetValue,
            currentValue = currentValue,
            progressPercent = progress,
            importance = target.leaguePositionImportance,
            status = status
        )
    }

    private fun evaluateCupProgress(target: SeasonTargetEntity): ObjectiveProgress {
        return ObjectiveProgress(
            targetType = SeasonTargetType.CUP.name,
            targetValue = target.cupTarget,
            currentValue = "进行中",
            progressPercent = 50.0,
            importance = target.cupImportance,
            status = "AT_RISK"
        )
    }

    private fun evaluateEuropeanProgress(target: SeasonTargetEntity): ObjectiveProgress {
        val progress = if (target.europeanTarget == "NONE") 80.0 else 50.0
        return ObjectiveProgress(
            targetType = SeasonTargetType.EUROPEAN.name,
            targetValue = target.europeanTarget,
            currentValue = if (target.europeanTarget == "NONE") "无欧战任务" else "进行中",
            progressPercent = progress,
            importance = target.europeanImportance,
            status = if (progress >= 80) "ON_TRACK" else "AT_RISK"
        )
    }

    private fun evaluateFinancialProgress(target: SeasonTargetEntity, currentRatio: Double): ObjectiveProgress {
        val progress = when {
            currentRatio <= target.financialWageRatioTarget -> 100.0
            currentRatio <= target.financialWageRatioTarget + 0.05 -> 70.0
            currentRatio <= target.financialWageRatioTarget + 0.10 -> 50.0
            currentRatio <= target.financialWageRatioTarget + 0.20 -> 30.0
            else -> 10.0
        }
        val status = when {
            progress >= 80 -> "ON_TRACK"
            progress >= 50 -> "AT_RISK"
            else -> "BEHIND"
        }
        return ObjectiveProgress(
            targetType = SeasonTargetType.FINANCIAL.name,
            targetValue = "工资/收入比 ≤ ${(target.financialWageRatioTarget * 100).toInt()}%",
            currentValue = "当前 ${(currentRatio * 100).toInt()}%",
            progressPercent = progress,
            importance = target.financialImportance,
            status = status
        )
    }

    private fun evaluateYouthProgress(target: SeasonTargetEntity, currentYouth: Int): ObjectiveProgress {
        val progress = when {
            currentYouth >= target.youthPromotionTarget -> 100.0
            currentYouth == target.youthPromotionTarget - 1 -> 60.0
            currentYouth == 0 && target.youthPromotionTarget > 0 -> 20.0
            else -> 40.0
        }
        val status = when {
            progress >= 80 -> "ON_TRACK"
            progress >= 50 -> "AT_RISK"
            else -> "BEHIND"
        }
        return ObjectiveProgress(
            targetType = SeasonTargetType.YOUTH.name,
            targetValue = "提拔 ${target.youthPromotionTarget} 名青训球员",
            currentValue = "已提拔 $currentYouth 名",
            progressPercent = progress,
            importance = "SECONDARY",
            status = status
        )
    }

    // ==================== 赛季末评估打分 ====================

    private fun evaluateLeagueScore(target: Int, actual: Int?): Double {
        if (actual == null) return 50.0
        return when {
            actual <= target -> config.seasonTarget.leagueAchieved
            actual == target + 1 -> config.seasonTarget.leagueMissBy1
            actual == target + 2 -> config.seasonTarget.leagueMissBy2
            actual <= target + 4 -> config.seasonTarget.leagueMissBy3To4
            else -> config.seasonTarget.leagueMissBy5Plus
        }
    }

    private fun evaluateCupScore(target: String, actual: String?): Double {
        if (actual == null) return 50.0
        val order = listOf("GROUP_STAGE", "ROUND_OF_16", "QUARTER_FINAL", "SEMI_FINAL", "FINAL", "WIN")
        val targetIdx = order.indexOf(target)
        val actualIdx = order.indexOf(actual)
        if (targetIdx < 0 || actualIdx < 0) return 50.0
        return when {
            actualIdx >= targetIdx -> config.seasonTarget.cupAchieved
            actualIdx == targetIdx - 1 -> config.seasonTarget.cupMissBy1Round
            actualIdx == targetIdx - 2 -> config.seasonTarget.cupMissBy2Rounds
            else -> config.seasonTarget.cupEarlyExit
        }
    }

    private fun evaluateEuropeanScore(target: String, actual: String?): Double {
        if (target == "NONE") return config.seasonTarget.europeanNoneScore
        return evaluateCupScore(target, actual)
    }

    private fun evaluateFinancialScore(target: Double, actual: Double): Double {
        return when {
            actual <= target -> config.seasonTarget.financialAchieved
            actual <= target + 0.05 -> config.seasonTarget.financialExceedBy5pct
            actual <= target + 0.10 -> config.seasonTarget.financialExceedBy10pct
            actual <= target + 0.20 -> config.seasonTarget.financialExceedBy20pct
            else -> config.seasonTarget.financialExceedBy30pctPlus
        }
    }

    private fun evaluateYouthScore(target: Int, actual: Int): Double {
        return when {
            actual >= target -> config.seasonTarget.youthAchieved
            actual == target - 1 -> config.seasonTarget.youthMissBy1
            actual == 0 && target > 0 -> config.seasonTarget.youthZeroWhenTargetPositive
            else -> config.seasonTarget.youthPartial
        }
    }

    // ==================== 数据查询辅助 ====================

    /**
     * 查询当前联赛排名（V1 简化：从 save_league_table 查询 competitionId=1）。
     */
    private suspend fun getCurrentLeaguePosition(saveId: Int, clubId: Int, seasonId: Int): Int? {
        return try {
            val leagueEntry = databaseManager.saveLeagueTableDao()
                .getByClub(saveId, seasonId, 1, clubId)
            leagueEntry?.position?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 统计本季青训球员提拔数（V1 简化：返回 0，V2 接入 T16 青训学院数据）。
     */
    private suspend fun countYouthPromotionsThisSeason(saveId: Int, clubId: Int, seasonId: Int): Int {
        // V1 简化：暂未接入 T16 青训学院数据，返回 0
        // V2 可查询 youth_event 表中 PROMOTED_TO_FIRST_TEAM 事件数
        return 0
    }
}
