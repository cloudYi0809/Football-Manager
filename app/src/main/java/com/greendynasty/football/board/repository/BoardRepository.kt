package com.greendynasty.football.board.repository

import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.board.expectation.BoardExpectationManager
import com.greendynasty.football.board.feedback.BoardConfidenceManager
import com.greendynasty.football.board.feedback.BoardFeedbackService
import com.greendynasty.football.board.model.BoardConfig
import com.greendynasty.football.board.model.BoardConfidenceEntity
import com.greendynasty.football.board.model.BoardEventEntity
import com.greendynasty.football.board.model.BoardExpectationSummary
import com.greendynasty.football.board.model.BoardSatisfactionEntity
import com.greendynasty.football.board.model.BudgetRequestEntity
import com.greendynasty.football.board.model.BudgetRequestResult
import com.greendynasty.football.board.model.BudgetRequestType
import com.greendynasty.football.board.model.DismissalDecision
import com.greendynasty.football.board.model.LongTermGoalEntity
import com.greendynasty.football.board.model.ObjectiveProgress
import com.greendynasty.football.board.model.SeasonEndResult
import com.greendynasty.football.board.model.SeasonTargetEntity
import com.greendynasty.football.board.model.SeasonTargetEvaluation
import com.greendynasty.football.board.model.SatisfactionLevel
import com.greendynasty.football.board.model.SatisfactionWeights
import com.greendynasty.football.board.objective.ObjectiveProgressEvaluator
import com.greendynasty.football.board.objective.SeasonObjectiveSetter
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.economy.model.FinancialHealthReport
import com.greendynasty.football.economy.model.FinancialWarningLevel
import com.greendynasty.football.economy.repository.EconomyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * T22 董事会仓库（V0.2 11 §四 + T22 方案 §四 + 任务要求 §二.6）。
 *
 * 数据访问层 + 业务协调入口，负责：
 * 1. 协调 [BoardExpectationManager] / [SeasonObjectiveSetter] / [ObjectiveProgressEvaluator] /
 *    [BoardFeedbackService] / [BoardConfidenceManager] 五大组件
 * 2. 持久化所有董事会数据到 save.db
 * 3. 提供 Flow / suspend 查询接口供 ViewModel 使用
 * 4. 实现 8 因子满意度计算（月度快照）
 * 5. 实现 6 类预算申请审批流程
 *
 * 三库分离：所有运行时数据在 save.db，无 history.db / cache.db 依赖。
 *
 * @param databaseManager 三库管理入口
 * @param clubProfileRepository T18 俱乐部画像仓库
 * @param economyRepository T17 经济仓库
 * @param config 董事会配置
 */
class BoardRepository(
    private val databaseManager: DatabaseManager,
    private val clubProfileRepository: ClubProfileRepository,
    private val economyRepository: EconomyRepository,
    private val config: BoardConfig = BoardConfig.DEFAULT
) {
    private val logger = Logger.getLogger("BoardRepository")

    val expectationManager = BoardExpectationManager(
        databaseManager, clubProfileRepository, economyRepository, config
    )
    val objectiveSetter = SeasonObjectiveSetter(
        databaseManager, expectationManager, clubProfileRepository, economyRepository, config
    )
    val progressEvaluator = ObjectiveProgressEvaluator(
        databaseManager, economyRepository, clubProfileRepository, config
    )
    val feedbackService = BoardFeedbackService(
        databaseManager, clubProfileRepository, config
    )
    val confidenceManager = BoardConfidenceManager(
        databaseManager, clubProfileRepository, config
    )

    // ==================== 1. 赛季目标 ====================

    /** 观察当前赛季目标（Flow 驱动 UI 自动刷新）。 */
    fun observeSeasonTarget(saveId: Int, clubId: Int, seasonId: Int): Flow<SeasonTargetEntity?> {
        return databaseManager.getSaveDatabase().seasonTargetDao()
            .observeBySeason(saveId, clubId, seasonId)
    }

    /** 查询当前赛季目标（一次性）。 */
    suspend fun getSeasonTarget(saveId: Int, clubId: Int, seasonId: Int): SeasonTargetEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().seasonTargetDao()
                .getBySeason(saveId, clubId, seasonId)
        }

    /** 查询历史赛季目标（用于 UI 历史记录）。 */
    suspend fun getSeasonTargetHistory(saveId: Int, clubId: Int, limit: Int = 10): List<SeasonTargetEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().seasonTargetDao()
                .getHistory(saveId, clubId, limit)
        }

    /** 赛季初生成赛季目标（委托给 [objectiveSetter]）。 */
    suspend fun generateSeasonTargets(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): SeasonTargetEntity? = objectiveSetter.generateSeasonTargets(saveId, clubId, seasonId, currentDate)

    /** 赛季中实时评估目标进度（委托给 [progressEvaluator]）。 */
    suspend fun evaluateProgress(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): List<ObjectiveProgress> = progressEvaluator.evaluateProgress(saveId, clubId, seasonId, currentDate)

    /** 赛季末评估目标达成（委托给 [progressEvaluator]）。 */
    suspend fun evaluateSeasonTargets(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): SeasonTargetEvaluation = progressEvaluator.evaluateSeasonTargets(saveId, clubId, seasonId, currentDate)

    // ==================== 2. 长期目标 ====================

    /** 观察活跃长期目标（Flow 驱动 UI）。 */
    fun observeLongTermGoals(saveId: Int, clubId: Int): Flow<List<LongTermGoalEntity>> {
        return databaseManager.getSaveDatabase().longTermGoalDao()
            .observeActiveGoals(saveId, clubId)
    }

    /** 查询活跃长期目标（一次性）。 */
    suspend fun getLongTermGoals(saveId: Int, clubId: Int): List<LongTermGoalEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().longTermGoalDao().getActiveGoals(saveId, clubId)
        }

    // ==================== 3. 满意度快照 ====================

    /** 观察最近 12 个月满意度快照（Flow 驱动 UI 历史曲线）。 */
    fun observeSatisfactionHistory(saveId: Int, clubId: Int, limit: Int = 12): Flow<List<BoardSatisfactionEntity>> {
        return databaseManager.getSaveDatabase().boardSatisfactionDao()
            .observeLatest(saveId, clubId, limit)
    }

    /** 查询最近满意度快照（一次性）。 */
    suspend fun getLatestSatisfaction(saveId: Int, clubId: Int): BoardSatisfactionEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().boardSatisfactionDao()
                .getLatest(saveId, clubId, 1).firstOrNull()
        }

    /**
     * 月度计算满意度快照（8 因子）。
     *
     * 由 T07 每月推进调用（月初）。
     *
     * 8 因子权重（V0.2 11 §四）：
     * - 联赛成绩 0.20
     * - 杯赛成绩 0.10
     * - 财政 0.15
     * - 球迷满意度 0.15
     * - 转会市场 0.10
     * - 青训发展 0.10
     * - 更衣室稳定 0.10
     * - 经理声望 0.10
     */
    suspend fun calculateSatisfactionSnapshot(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): BoardSatisfactionEntity? = withContext(Dispatchers.IO) {
        try {
            val profile = clubProfileRepository.getProfile(clubId) ?: return@withContext null
            val clubState = databaseManager.saveClubStateDao().getByClub(saveId, clubId) ?: return@withContext null
            val financial = economyRepository.buildFinancialState(saveId, clubId, currentDate.year)
            val currentTarget = databaseManager.getSaveDatabase().seasonTargetDao()
                .getBySeason(saveId, clubId, seasonId)

            // 1. 联赛成绩（与赛季目标对比）
            val currentLeaguePos = try {
                databaseManager.saveLeagueTableDao().getByClub(saveId, seasonId, 1, clubId)?.position
            } catch (_: Exception) { null }
            val targetLeaguePos = currentTarget?.leaguePositionTarget
            val leagueScore = if (currentLeaguePos == null || targetLeaguePos == null) 50.0
            else when {
                currentLeaguePos <= targetLeaguePos -> 100.0
                currentLeaguePos == targetLeaguePos + 1 -> 80.0
                currentLeaguePos == targetLeaguePos + 2 -> 60.0
                currentLeaguePos <= targetLeaguePos + 4 -> 40.0
                else -> 20.0
            }

            // 2. 杯赛成绩（V1 简化：默认 50）
            val cupScore = 50.0

            // 3. 财政状况
            val financialScore = when {
                financial.wageToIncomeRatio < 0.55 -> 100.0
                financial.wageToIncomeRatio < 0.70 -> 80.0
                financial.wageToIncomeRatio < 0.85 -> 50.0
                else -> 20.0
            }

            // 4. 球迷满意度（来自 save_club_state.fan_satisfaction）
            val fanScore = clubState.fanSatisfaction.toDouble()

            // 5. 转会市场（V1 简化：默认 60）
            val transferScore = 60.0

            // 6. 青训发展（V1 简化：默认 50）
            val youthScore = 50.0

            // 7. 更衣室稳定（来自 save_club_state.dressing_room_morale）
            val dressingRoomScore = clubState.dressingRoomMorale.toDouble()

            // 8. 经理个人声望（V1 简化：俱乐部声望近似）
            val managerReputationScore = clubState.reputation.toDouble()

            // 按董事会性格微调权重
            val personality = inferBoardPersonality(profile.ambition, profile.wageStrictness, profile.youthPreference)
            val adjustedWeights = adjustWeightsByPersonality(config.satisfactionWeights, personality)

            val overall = (
                leagueScore * adjustedWeights.league +
                    cupScore * adjustedWeights.cup +
                    financialScore * adjustedWeights.financial +
                    fanScore * adjustedWeights.fan +
                    transferScore * adjustedWeights.transfer +
                    youthScore * adjustedWeights.youth +
                    dressingRoomScore * adjustedWeights.dressingRoom +
                    managerReputationScore * adjustedWeights.managerReputation
                ).coerceIn(0.0, 100.0)

            val level = SatisfactionLevel.fromScore(overall, config.satisfactionLevels)

            // 趋势判定
            val lastSnapshot = databaseManager.getSaveDatabase().boardSatisfactionDao()
                .getLatest(saveId, clubId, 1).firstOrNull()
            val trend = when {
                lastSnapshot == null -> "STABLE"
                overall > lastSnapshot.overallSatisfaction + 3 -> "RISING"
                overall < lastSnapshot.overallSatisfaction - 3 -> "FALLING"
                else -> "STABLE"
            }

            val snapshot = BoardSatisfactionEntity(
                saveId = saveId,
                clubId = clubId,
                snapshotDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                leaguePerformanceScore = leagueScore,
                cupPerformanceScore = cupScore,
                financialScore = financialScore,
                fanSatisfactionScore = fanScore,
                transferMarketScore = transferScore,
                youthDevelopmentScore = youthScore,
                dressingRoomStabilityScore = dressingRoomScore,
                managerPersonalReputationScore = managerReputationScore,
                overallSatisfaction = overall,
                satisfactionLevel = level.name,
                trendDirection = trend
            )
            databaseManager.getSaveDatabase().boardSatisfactionDao().insert(snapshot)

            // 同步更新 save_club_state.board_satisfaction 冗余字段
            databaseManager.saveClubStateDao()
                .updateBoardSatisfaction(saveId, clubId, overall.toInt())

            snapshot
        } catch (e: Exception) {
            logger.warning("计算满意度快照失败：${e.message}")
            null
        }
    }

    /**
     * 根据俱乐部画像特征推导董事会性格（V0.2 + T22 方案 §四.3）。
     *
     * V1 简化：基于 ambition / wageStrictness / youthPreference 推导
     * - ambition > 70 → AMBITIOUS
     * - wageStrictness > 70 → HANDS_OFF
     * - youthPreference > 70 → PATIENT
     * - 否则 → RUTHLESS（默认）
     */
    private fun inferBoardPersonality(ambition: Int, wageStrictness: Int, youthPreference: Int): String {
        return when {
            ambition > 70 -> "AMBITIOUS"
            wageStrictness > 70 -> "HANDS_OFF"
            youthPreference > 70 -> "PATIENT"
            else -> "RUTHLESS"
        }
    }

    /**
     * 按董事会性格调整权重（V0.2 11 §四 + T22 方案 §四.3）。
     */
    private fun adjustWeightsByPersonality(base: SatisfactionWeights, personality: String): SatisfactionWeights {
        val adjustment = config.personalityAdjustments[personality] ?: return base
        return SatisfactionWeights(
            league = base.league + adjustment.league,
            cup = base.cup + adjustment.cup,
            financial = base.financial + adjustment.financial,
            fan = base.fan + adjustment.fan,
            transfer = base.transfer + adjustment.transfer,
            youth = base.youth + adjustment.youth,
            dressingRoom = base.dressingRoom + adjustment.dressingRoom,
            managerReputation = base.managerReputation + adjustment.managerReputation
        )
    }

    // ==================== 4. 信心值 ====================

    /** 观察当前赛季信心值（Flow 驱动 UI 仪表盘）。 */
    fun observeConfidence(saveId: Int, clubId: Int, seasonId: Int): Flow<BoardConfidenceEntity?> {
        return databaseManager.getSaveDatabase().boardConfidenceDao()
            .observeBySeason(saveId, clubId, seasonId)
    }

    /** 查询当前信心值（一次性）。 */
    suspend fun getConfidence(saveId: Int, clubId: Int, seasonId: Int): BoardConfidenceEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().boardConfidenceDao()
                .getBySeason(saveId, clubId, seasonId)
        }

    /** 赛季初初始化信心值。 */
    suspend fun initializeConfidenceForSeason(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): BoardConfidenceEntity = confidenceManager.initializeForSeason(saveId, clubId, seasonId, currentDate)

    /** 比赛结果触发信心值变化。 */
    suspend fun onMatchResult(
        saveId: Int, clubId: Int, seasonId: Int,
        isWin: Boolean, isDraw: Boolean, consecutiveWinless: Int,
        currentDate: LocalDate
    ): Int = confidenceManager.onMatchResult(
        saveId, clubId, seasonId, isWin, isDraw, consecutiveWinless, currentDate
    )

    // ==================== 5. 预算申请 ====================

    /** 观察预算申请记录（Flow 驱动 UI）。 */
    fun observeBudgetRequests(saveId: Int, clubId: Int): Flow<List<BudgetRequestEntity>> {
        return databaseManager.getSaveDatabase().budgetRequestDao()
            .observeAll(saveId, clubId)
    }

    /**
     * 提交预算申请并立即审批（V0.2 + T22 方案 §四.4）。
     *
     * 审批策略：
     * | 满意度 | 财政健康 | 审批结果 |
     * | ≥80    | HEALTHY  | 100% 批准 |
     * | ≥80    | ACCEPTABLE | 80% 批准 |
     * | ≥65    | HEALTHY  | 80% 批准 |
     * | ≥65    | ACCEPTABLE | 60% 批准 |
     * | ≥50    | -        | 50% 批准 + 警告 |
     * | <50    | -        | 拒绝 + 冷却 30 天 |
     */
    suspend fun submitBudgetRequest(
        saveId: Int, clubId: Int, currentDate: LocalDate,
        requestType: BudgetRequestType, amount: Int, justification: String
    ): BudgetRequestResult = withContext(Dispatchers.IO) {
        // 1. 冷却期检查（30 天内同类型不可重复申请）
        val cooldownDays = config.budgetRequest.cooldownDays
        val sinceDate = currentDate.minusDays(cooldownDays.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastReviewed = databaseManager.getSaveDatabase().budgetRequestDao()
            .getLatestReviewed(saveId, clubId, requestType.name, sinceDate)

        if (lastReviewed != null) {
            val daysSince = try {
                java.time.Period.between(
                    LocalDate.parse(lastReviewed.reviewedAt ?: sinceDate),
                    currentDate
                ).days
            } catch (_: Exception) { cooldownDays }
            if (daysSince < cooldownDays) {
                return@withContext BudgetRequestResult.rejectedDueToCooldown(cooldownDays - daysSince)
            }
        }

        // 2. 获取当前满意度 + 财政健康度
        val satisfaction = databaseManager.getSaveDatabase().boardSatisfactionDao()
            .getLatest(saveId, clubId, 1).firstOrNull()?.overallSatisfaction ?: 50.0
        val financial = try {
            economyRepository.buildFinancialState(saveId, clubId, currentDate.year)
        } catch (_: Exception) { null }
        val healthReport = try {
            economyRepository.checkFinancialHealth(saveId, clubId, currentDate.year)
        } catch (_: Exception) {
            FinancialHealthReport(clubId, 0.5, FinancialWarningLevel.ACCEPTABLE, emptyList())
        }

        // 3. 计算审批比例
        val approvalRatio = calculateApprovalRatio(satisfaction, healthReport.level, requestType)
        var approvedAmount = (amount * approvalRatio).toInt()

        // 4. 财政承受力二次校验（不超过俱乐部年收入 50%）
        val maxAffordable = ((financial?.totalIncome ?: 0) * config.budgetRequest.maxBudgetRequestIncomeRatio).toInt()
        val finalAmount = minOf(approvedAmount, maxAffordable)

        // 5. 状态判定
        val status = when {
            finalAmount <= 0 -> "REJECTED"
            finalAmount < amount -> "NEGOTIATED"
            else -> "APPROVED"
        }

        val boardResponse = buildBudgetResponse(status, finalAmount, satisfaction, healthReport.level)

        // 6. 写入申请记录
        val request = BudgetRequestEntity(
            saveId = saveId,
            clubId = clubId,
            requestDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            requestType = requestType.name,
            requestedAmount = amount,
            justification = justification,
            currentSatisfaction = satisfaction,
            currentFinancialHealth = healthReport.level.name,
            status = status,
            approvedAmount = finalAmount,
            boardResponse = boardResponse,
            reviewedAt = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            cooldownDaysRemaining = if (status == "REJECTED") config.budgetRequest.cooldownDays else 0
        )
        databaseManager.getSaveDatabase().budgetRequestDao().insert(request)

        // 7. 执行预算变更
        if (status in listOf("APPROVED", "NEGOTIATED") && finalAmount > 0) {
            applyBudgetChange(saveId, clubId, requestType, finalAmount)
        }

        BudgetRequestResult(
            status = status,
            approvedAmount = finalAmount,
            boardResponse = boardResponse,
            satisfactionImpact = -1 // 申请即使批准也小扣满意度（频繁要钱惹恼董事会）
        )
    }

    /**
     * 计算审批比例（V0.2 + T22 方案 §四.4）。
     */
    private fun calculateApprovalRatio(
        satisfaction: Double, healthLevel: FinancialWarningLevel,
        requestType: BudgetRequestType
    ): Double {
        val facilityPenalty = if (requestType.isFacilityType()) config.budgetRequest.facilityPenaltyRatio else 1.0
        val baseRatio = when {
            satisfaction >= 80 && healthLevel == FinancialWarningLevel.HEALTHY ->
                config.budgetRequest.approvalSat80HealthHealthy
            satisfaction >= 80 && healthLevel == FinancialWarningLevel.ACCEPTABLE ->
                config.budgetRequest.approvalSat80HealthAcceptable
            satisfaction >= 65 && healthLevel == FinancialWarningLevel.HEALTHY ->
                config.budgetRequest.approvalSat65HealthHealthy
            satisfaction >= 65 && healthLevel == FinancialWarningLevel.ACCEPTABLE ->
                config.budgetRequest.approvalSat65HealthAcceptable
            satisfaction >= 50 && healthLevel != FinancialWarningLevel.HIGH_RISK ->
                config.budgetRequest.approvalSat50
            else -> config.budgetRequest.approvalBelowThreshold
        }
        return baseRatio * facilityPenalty
    }

    private fun buildBudgetResponse(
        status: String, amount: Int, satisfaction: Double,
        healthLevel: FinancialWarningLevel
    ): String = when (status) {
        "APPROVED" -> "董事会已批准你的申请，金额 $amount 欧元。"
        "NEGOTIATED" -> "董事会部分批准你的申请，金额 $amount 欧元。当前满意度 ${satisfaction.toInt()}，财政状况 $healthLevel。"
        else -> "董事会拒绝了你的请求。当前满意度 ${satisfaction.toInt()}，财政状况 $healthLevel。30 天内不可重复申请。"
    }

    /**
     * 执行预算变更（写入 save_club_state）。
     */
    private suspend fun applyBudgetChange(
        saveId: Int, clubId: Int, requestType: BudgetRequestType, amount: Int
    ) {
        when (requestType) {
            BudgetRequestType.TRANSFER_BUDGET -> {
                val state = databaseManager.saveClubStateDao().getByClub(saveId, clubId) ?: return
                databaseManager.saveClubStateDao()
                    .updateTransferBudget(saveId, clubId, state.transferBudget + amount)
            }
            BudgetRequestType.WAGE_BUDGET -> {
                val state = databaseManager.saveClubStateDao().getByClub(saveId, clubId) ?: return
                databaseManager.saveClubStateDao()
                    .updateWageBudget(saveId, clubId, state.wageBudget + amount)
            }
            // 设施类投资：V1 简化，仅记录申请，不实际改变设施等级（V2 接入 T16 青训学院 + T08 医疗设施）
            BudgetRequestType.YOUTH_FACILITY,
            BudgetRequestType.TRAINING_FACILITY,
            BudgetRequestType.MEDICAL_FACILITY,
            BudgetRequestType.STADIUM_EXPANSION -> {
                // V1 简化：设施投资仅扣减余额，不影响设施等级
                val state = databaseManager.saveClubStateDao().getByClub(saveId, clubId) ?: return
                databaseManager.saveClubStateDao()
                    .updateBalance(saveId, clubId, state.balance - amount)
            }
        }
    }

    // ==================== 6. 董事会事件 ====================

    /** 观察最近董事会事件（Flow 驱动 UI）。 */
    fun observeRecentEvents(saveId: Int, clubId: Int, limit: Int = 50): Flow<List<BoardEventEntity>> {
        return databaseManager.getSaveDatabase().boardEventDao()
            .observeRecent(saveId, clubId, limit)
    }

    /** 查询最近董事会事件（一次性）。 */
    suspend fun getRecentEvents(saveId: Int, clubId: Int, limit: Int = 50): List<BoardEventEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().boardEventDao().getRecent(saveId, clubId, limit)
        }

    /** 玩家响应董事会事件。 */
    suspend fun resolveEvent(eventId: Int, response: String, currentDate: LocalDate) =
        withContext(Dispatchers.IO) {
            databaseManager.getSaveDatabase().boardEventDao().resolveEvent(
                eventId, response, currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        }

    // ==================== 7. 赛季末处理（综合入口） ====================

    /**
     * 赛季末处理：评估目标 + 解雇判定 + 生成总结事件。
     *
     * 由 T19 SeasonArchiveExecutor 调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param wonLeagueTitle 是否夺得联赛冠军
     * @param currentDate 当前游戏日期
     * @return 赛季末处理结果
     */
    suspend fun processSeasonEnd(
        saveId: Int, clubId: Int, seasonId: Int,
        wonLeagueTitle: Boolean, currentDate: LocalDate
    ): SeasonEndResult = withContext(Dispatchers.IO) {
        // 1. 评估赛季目标
        val evaluation = progressEvaluator.evaluateSeasonTargets(saveId, clubId, seasonId, currentDate)

        // 2. 当前财政状况
        val currentWageRatio = try {
            economyRepository.buildFinancialState(saveId, clubId, currentDate.year).wageToIncomeRatio
        } catch (_: Exception) { 0.0 }

        // 3. 解雇判定
        val dismissal = confidenceManager.evaluateDismissal(
            saveId, clubId, seasonId, evaluation, currentWageRatio, currentDate
        )

        // 4. 计算连续未达成赛季数（用于信心值更新）
        val consecutiveFailed = dismissal.consecutiveCoreFailedSeasons

        // 5. 更新信心值
        confidenceManager.onSeasonEnd(
            saveId, clubId, seasonId, evaluation, wonLeagueTitle, consecutiveFailed, currentDate
        )

        // 6. 生成赛季末总结事件
        feedbackService.triggerSeasonEndSummary(
            saveId, clubId, seasonId, evaluation, dismissal, currentDate
        )

        // 7. 根据警告等级生成对应事件
        when (dismissal.warningLevel) {
            com.greendynasty.football.board.model.DismissalLevel.WARNING -> {
                feedbackService.triggerTargetMissedWarning(
                    saveId, clubId, seasonId, consecutiveFailed, currentDate
                )
            }
            com.greendynasty.football.board.model.DismissalLevel.ULTIMATUM -> {
                feedbackService.triggerDismissalThreat(saveId, clubId, seasonId, currentDate)
            }
            else -> { /* NONE / DISMISS 无需额外事件 */ }
        }

        SeasonEndResult(evaluation, dismissal)
    }

    // ==================== 8. 董事会期望 ====================

    /** 计算董事会期望摘要（委托给 [expectationManager]）。 */
    suspend fun computeExpectation(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): BoardExpectationSummary? = expectationManager.computeExpectation(
        saveId, clubId, seasonId, currentDate
    )

    /**
     * 月度董事会推进（V0.2 11 §四 + T22 方案 §五）。
     *
     * 由 T07 每月推进调用：
     * 1. 计算满意度快照
     * 2. 赛季中评估（1 月 1 日触发）
     *
     * @param isPlayerClub 是否为玩家俱乐部（玩家俱乐部才生成事件）
     */
    suspend fun processMonthlyReview(
        saveId: Int, clubId: Int, seasonId: Int,
        currentDate: LocalDate, isPlayerClub: Boolean
    ) = withContext(Dispatchers.IO) {
        // 1. 月度满意度快照
        calculateSatisfactionSnapshot(saveId, clubId, seasonId, currentDate)

        // 2. 赛季中评估（1 月 1 日触发，仅玩家俱乐部）
        if (isPlayerClub &&
            currentDate.monthValue == config.events.midSeasonReviewMonth &&
            currentDate.dayOfMonth == config.events.midSeasonReviewDay
        ) {
            val progressList = progressEvaluator.evaluateProgress(saveId, clubId, seasonId, currentDate)
            val avgScore = if (progressList.isNotEmpty()) {
                progressList.map { it.progressPercent }.average()
            } else 50.0
            feedbackService.triggerMidSeasonReview(saveId, clubId, seasonId, avgScore, currentDate)
        }
    }
}
