package com.greendynasty.football.board.feedback

import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.board.model.BoardConfig
import com.greendynasty.football.board.model.BoardConfidenceEntity
import com.greendynasty.football.board.model.DismissalDecision
import com.greendynasty.football.board.model.DismissalLevel
import com.greendynasty.football.board.model.SeasonTargetEvaluation
import com.greendynasty.football.data.api.DatabaseManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T22 董事会信心值管理器（V0.2 05 §九 + 11 §四 + 任务要求 §二.5）。
 *
 * 职责：
 * 1. 维护董事会信心值 0-100（每俱乐部每赛季一条记录）
 * 2. 比赛结果触发信心值变化（胜 +1 / 平 0 / 负 -2，连续 3 场不胜 -5）
 * 3. 赛季末成就触发信心值变化（夺冠 +20 / 达成核心目标 +10 / 失败 -15）
 * 4. 根据信心值判定警告等级（NONE / WARNING / ULTIMATUM / DISMISS）
 * 5. 紧急解雇检查（更衣室/财政/球迷极端情况，不受缓冲约束）
 *
 * 信心值变化阈值（任务要求）：
 * - 连续 3 场不胜 -5
 * - 夺冠 +20
 * - 达成核心目标 +10
 * - 核心目标失败 -15
 *
 * 警告等级阈值：
 * - < 40 触发 WARNING
 * - < 25 触发 ULTIMATUM
 * - < 10 触发 DISMISS
 *
 * @param databaseManager 三库管理入口
 * @param clubProfileRepository T18 俱乐部画像仓库
 * @param config 董事会配置
 */
class BoardConfidenceManager(
    private val databaseManager: DatabaseManager,
    private val clubProfileRepository: ClubProfileRepository,
    private val config: BoardConfig = BoardConfig.DEFAULT
) {

    /**
     * 初始化俱乐部本赛季的信心值记录。
     *
     * 赛季初由 T07 SeasonStartExecutor 调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 信心值实体
     */
    suspend fun initializeForSeason(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): BoardConfidenceEntity {
        val existing = databaseManager.getSaveDatabase().boardConfidenceDao()
            .getBySeason(saveId, clubId, seasonId)
        if (existing != null) return existing

        // 新赛季初继承上赛季信心值（如有），否则用默认值
        val lastConfidence = databaseManager.getSaveDatabase().boardConfidenceDao()
            .getLatest(saveId, clubId)
        val initialValue = lastConfidence?.confidenceValue?.coerceIn(
            config.confidence.initialConfidence - 10,
            config.confidence.initialConfidence + 10
        ) ?: config.confidence.initialConfidence

        val entity = BoardConfidenceEntity(
            saveId = saveId,
            clubId = clubId,
            seasonId = seasonId,
            confidenceValue = initialValue,
            warningLevel = "NONE",
            consecutiveCoreFailedSeasons = 0,
            lastUpdatedAt = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
        databaseManager.getSaveDatabase().boardConfidenceDao().upsert(entity)
        return entity
    }

    /**
     * 比赛结果触发信心值变化。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param isWin 是否获胜
     * @param isDraw 是否平局
     * @param consecutiveWinless 当前连续不胜场次（用于判断是否触发 -5 惩罚）
     * @param currentDate 当前游戏日期
     * @return 更新后的信心值
     */
    suspend fun onMatchResult(
        saveId: Int, clubId: Int, seasonId: Int,
        isWin: Boolean, isDraw: Boolean, consecutiveWinless: Int,
        currentDate: LocalDate
    ): Int {
        val confidence = databaseManager.getSaveDatabase().boardConfidenceDao()
            .getBySeason(saveId, clubId, seasonId)
            ?: initializeForSeason(saveId, clubId, seasonId, currentDate)

        // 单场结果影响
        val matchDelta = when {
            isWin -> config.confidence.winDelta
            isDraw -> config.confidence.drawDelta
            else -> config.confidence.loseDelta
        }

        // 连续不胜惩罚（连续 3 场不胜额外 -5）
        val consecutiveDelta = if (consecutiveWinless >= config.confidence.consecutiveWinlessThreshold &&
            (consecutiveWinless - config.confidence.consecutiveWinlessThreshold) == 0
        ) {
            config.confidence.consecutiveWinlessDelta
        } else 0

        val newValue = (confidence.confidenceValue + matchDelta + consecutiveDelta)
            .coerceIn(0, 100)

        val newWarningLevel = determineWarningLevel(newValue)
        updateConfidence(confidence.id, newValue, newWarningLevel, confidence.consecutiveCoreFailedSeasons, currentDate)

        return newValue
    }

    /**
     * 赛季末评估后更新信心值。
     *
     * - 夺冠 +20
     * - 达成核心目标 +10
     * - 部分达成 0
     * - 核心目标失败 -15
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param evaluation 赛季目标评估
     * @param wonLeagueTitle 是否夺得联赛冠军
     * @param currentDate 当前游戏日期
     * @return 更新后的信心值
     */
    suspend fun onSeasonEnd(
        saveId: Int, clubId: Int, seasonId: Int,
        evaluation: SeasonTargetEvaluation,
        wonLeagueTitle: Boolean,
        consecutiveCoreFailedSeasons: Int,
        currentDate: LocalDate
    ): Int {
        val confidence = databaseManager.getSaveDatabase().boardConfidenceDao()
            .getBySeason(saveId, clubId, seasonId)
            ?: initializeForSeason(saveId, clubId, seasonId, currentDate)

        // 赛季末成就奖励
        val achievementDelta = when {
            wonLeagueTitle -> config.confidence.winLeagueTitleDelta // 夺冠 +20
            evaluation.status == "ACHIEVED" -> config.confidence.achieveCoreGoalDelta // 达成核心目标 +10
            evaluation.status == "PARTIALLY" -> config.confidence.partialAchieveDelta // 部分达成 0
            else -> config.confidence.failCoreGoalDelta // 失败 -15
        }

        val newValue = (confidence.confidenceValue + achievementDelta).coerceIn(0, 100)
        val newWarningLevel = determineWarningLevel(newValue)

        updateConfidence(
            confidence.id, newValue, newWarningLevel,
            consecutiveCoreFailedSeasons, currentDate
        )

        return newValue
    }

    /**
     * 根据信心值判定警告等级。
     *
     * - < 10 触发 DISMISS
     * - < 25 触发 ULTIMATUM
     * - < 40 触发 WARNING
     * - 否则 NONE
     */
    fun determineWarningLevel(confidenceValue: Int): DismissalLevel {
        return when {
            confidenceValue < config.confidence.dismissThreshold -> DismissalLevel.DISMISS
            confidenceValue < config.confidence.ultimatumThreshold -> DismissalLevel.ULTIMATUM
            confidenceValue < config.confidence.warningThreshold -> DismissalLevel.WARNING
            else -> DismissalLevel.NONE
        }
    }

    /**
     * 紧急解雇检查（V0.2 05 §九 + T22 方案 §四.6）。
     *
     * 紧急解雇不受缓冲约束，立即触发：
     * - 严重更衣室叛乱（dressing_room_morale < 20）
     * - 严重财政危机（wageToIncomeRatio > 1.0）
     * - 球迷大规模抗议（fan_satisfaction < 15）
     *
     * @return 紧急解雇原因，null 表示无紧急解雇
     */
    suspend fun checkEmergencyDismissal(
        saveId: Int, clubId: Int, currentWageRatio: Double
    ): String? {
        val clubState = databaseManager.saveClubStateDao().getByClub(saveId, clubId) ?: return null

        // 1. 严重更衣室叛乱
        if (clubState.dressingRoomMorale < config.dismissal.emergencyDressingRoomMoraleThreshold) {
            return "更衣室严重失控：球员士气已跌至 ${clubState.dressingRoomMorale}，董事会认为你已失去对球队的控制。"
        }

        // 2. 严重财政危机
        if (currentWageRatio > config.dismissal.emergencyWageRatioThreshold) {
            return "严重财政危机：工资/收入比已达 ${(currentWageRatio * 100).toInt()}%，俱乐部濒临破产。"
        }

        // 3. 球迷大规模抗议
        if (clubState.fanSatisfaction < config.dismissal.emergencyFanSatisfactionThreshold) {
            return "球迷大规模抗议：球迷满意度已跌至 ${clubState.fanSatisfaction}，董事会为平息民愤不得不做出解雇决定。"
        }

        return null
    }

    /**
     * 解雇判定主流程（V0.2 05 §九 + T22 方案 §四.6）。
     *
     * 4 档解雇机制：
     * 1. NONE - 无解雇风险
     * 2. WARNING - 连续 2 赛季核心目标未达成
     * 3. ULTIMATUM - 连续 3 赛季核心目标未达成
     * 4. DISMISS - 连续 4 赛季核心目标未达成 OR 最终通牒赛季仍未达成
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param evaluation 赛季目标评估
     * @param currentWageRatio 当前工资/收入比
     * @param currentDate 当前游戏日期
     * @return 解雇判定结果
     */
    suspend fun evaluateDismissal(
        saveId: Int, clubId: Int, seasonId: Int,
        evaluation: SeasonTargetEvaluation,
        currentWageRatio: Double,
        currentDate: LocalDate
    ): DismissalDecision {
        // 1. 紧急解雇检查（最高优先级）
        val emergencyReason = checkEmergencyDismissal(saveId, clubId, currentWageRatio)
        if (emergencyReason != null) {
            return DismissalDecision(
                shouldDismiss = true,
                warningLevel = DismissalLevel.DISMISS,
                reason = emergencyReason,
                consecutiveCoreFailedSeasons = 0,
                isEmergency = true
            )
        }

        // 2. 计算连续未达成核心目标赛季数
        val consecutiveFailed = countConsecutiveCoreFailedSeasons(
            saveId, clubId, seasonId, evaluation
        )

        // 3. 董事会耐心调整阈值（patience 越低，触发解雇越快）
        val profile = clubProfileRepository.getProfile(clubId)
        val patienceAdjustment = ((profile?.patienceWithManager ?: 50) - 50) / 50.0 // -1.0 ~ +1.0
        val effectiveWarningThreshold = (config.dismissal.warningTriggerSeasons - patienceAdjustment.toInt())
            .coerceAtLeast(1)
        val effectiveUltimatumThreshold = (config.dismissal.ultimatumTriggerSeasons - patienceAdjustment.toInt())
            .coerceAtLeast(2)
        val effectiveDismissThreshold = (config.dismissal.dismissTriggerSeasons - patienceAdjustment.toInt())
            .coerceAtLeast(3)

        // 4. 当前信心值
        val confidence = databaseManager.getSaveDatabase().boardConfidenceDao()
            .getBySeason(saveId, clubId, seasonId)
        val currentConfidenceValue = confidence?.confidenceValue ?: config.confidence.initialConfidence

        // 5. 判定警告等级
        val warningLevel = when {
            // 连续 4 赛季核心目标未达成 → DISMISS
            consecutiveFailed >= effectiveDismissThreshold -> DismissalLevel.DISMISS

            // 连续 3 赛季 + 本季核心目标失败 → DISMISS（升级）
            consecutiveFailed >= effectiveUltimatumThreshold && evaluation.coreGoalFailed -> {
                if (confidence?.warningLevel == "ULTIMATUM" && evaluation.coreGoalFailed) {
                    DismissalLevel.DISMISS
                } else {
                    DismissalLevel.ULTIMATUM
                }
            }

            // 连续 2 赛季核心目标未达成 → WARNING（升级路径）
            consecutiveFailed >= effectiveWarningThreshold -> {
                if (confidence?.warningLevel == "WARNING" && evaluation.coreGoalFailed) {
                    DismissalLevel.ULTIMATUM // 上季已警告，本季仍未达成 → 升级
                } else {
                    DismissalLevel.WARNING
                }
            }

            // 信心值极低也触发警告
            currentConfidenceValue < config.confidence.warningThreshold ->
                DismissalLevel.WARNING

            else -> DismissalLevel.NONE
        }

        val shouldDismiss = warningLevel == DismissalLevel.DISMISS
        val reason = buildDismissalReason(warningLevel, consecutiveFailed, currentConfidenceValue)

        // 6. 更新信心值表中的警告等级
        confidence?.let {
            updateConfidence(
                it.id, currentConfidenceValue, warningLevel,
                consecutiveFailed, currentDate
            )
        }

        return DismissalDecision(
            shouldDismiss = shouldDismiss,
            warningLevel = warningLevel,
            reason = reason,
            consecutiveCoreFailedSeasons = consecutiveFailed,
            isEmergency = false
        )
    }

    /**
     * 计算连续未达成核心目标赛季数。
     *
     * 从当前赛季倒推，直到遇到一个达成的核心目标赛季。
     */
    private suspend fun countConsecutiveCoreFailedSeasons(
        saveId: Int, clubId: Int, currentSeasonId: Int,
        currentEvaluation: SeasonTargetEvaluation
    ): Int {
        if (!currentEvaluation.coreGoalFailed && currentEvaluation.status != "FAILED") return 0

        var count = 1 // 当前赛季算 1
        var seasonId = currentSeasonId - 1
        val seasonTargetDao = databaseManager.getSaveDatabase().seasonTargetDao()
        val lookbackLimit = currentSeasonId - config.dismissal.dismissalLookbackSeasons

        while (seasonId > 0 && seasonId > lookbackLimit) {
            val target = seasonTargetDao.getBySeason(saveId, clubId, seasonId) ?: break
            if (target.evaluationStatus == "FAILED") {
                count++
                seasonId--
            } else {
                break
            }
        }
        return count
    }

    private fun buildDismissalReason(
        level: DismissalLevel, consecutiveFailed: Int, confidenceValue: Int
    ): String {
        return when (level) {
            DismissalLevel.NONE -> "董事会暂时对你的工作表示满意。"
            DismissalLevel.WARNING -> "你已经连续 $consecutiveFailed 赛季未能达成核心目标，董事会对你表达了明确的不满。"
            DismissalLevel.ULTIMATUM -> "连续 $consecutiveFailed 赛季未达成核心目标，董事会下达最后通牒：下赛季必须达成。"
            DismissalLevel.DISMISS -> "连续 $consecutiveFailed 赛季核心目标未达成（当前信心值 $confidenceValue），董事会决定解除你的主帅职务。"
        }
    }

    private suspend fun updateConfidence(
        id: Int, newValue: Int, warningLevel: DismissalLevel,
        consecutiveFailed: Int, currentDate: LocalDate
    ) {
        databaseManager.getSaveDatabase().boardConfidenceDao().updateConfidence(
            id = id,
            value = newValue,
            warningLevel = warningLevel.name,
            consecutiveFailed = consecutiveFailed,
            date = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
    }
}
