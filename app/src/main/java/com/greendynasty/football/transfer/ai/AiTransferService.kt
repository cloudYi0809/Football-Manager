package com.greendynasty.football.transfer.ai

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.transfer.ai.config.BasicAiConfig
import com.greendynasty.football.transfer.ai.decision.AiTransferDecisionEngine
import com.greendynasty.football.transfer.ai.executor.AiTransferExecutor
import com.greendynasty.football.transfer.ai.model.AiActionType
import com.greendynasty.football.transfer.ai.model.AiOffer
import com.greendynasty.football.transfer.ai.model.AiTransferAction
import com.greendynasty.football.transfer.ai.model.AiTransferResult
import com.greendynasty.football.transfer.ai.model.ClubFinancialState
import com.greendynasty.football.transfer.ai.model.PlayerCandidate
import com.greendynasty.football.transfer.ai.model.PositionNeed
import com.greendynasty.football.transfer.ai.needs.AiSquadNeedEvaluator
import com.greendynasty.football.transfer.ai.offer.AiOfferGenerator
import com.greendynasty.football.transfer.ai.target.AiTargetFinder
import com.greendynasty.football.transfer.negotiation.engine.ClubNegotiator
import com.greendynasty.football.transfer.negotiation.engine.SellerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValueEstimator
import com.greendynasty.football.transfer.search.EconomyEstimator
import com.greendynasty.football.transfer.search.PlayerSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * T13.6 AI 转会门面服务（V0.2 `05_AI俱乐部决策模型.md` 基础版入口）。
 *
 * 集成所有 AI 转会组件，由 T07 每日推进在转会窗期间调用。
 *
 * 流水线：
 * ```
 * AiTransferService.processDailyTransfers(ctx)
 *   ├── 遍历活跃俱乐部（排除玩家俱乐部）
 *   │   ├── AiSquadNeedEvaluator.analyze()    # 阵容短板识别
 *   │   ├── tryBuyPlayer()                     # 买入决策
 *   │   │   ├── AiTargetFinder.findCandidates() # 目标搜索
 *   │   │   ├── AiOfferGenerator.generateOffer() # 报价生成
 *   │   │   ├── AiTransferDecisionEngine.scoreTarget() # 9 因子评分
 *   │   │   ├── AiTransferDecisionEngine.checkConstraints() # 防崩坏
 *   │   │   ├── AiTransferDecisionEngine.evaluateSellerResponse() # 卖方决策
 *   │   │   └── AiTransferExecutor.executeBuy() # 执行买入
 *   │   └── trySellPlayers()                   # 卖人决策
 *   │       ├── AiTransferDecisionEngine.shouldSell() # 6 因子卖人
 *   │       └── AiTransferExecutor.executeSell() # 执行卖出
 * ```
 *
 * 基础版特性：
 * - 转会窗期间每日执行
 * - 玩家俱乐部不自动操作
 * - AI 决策含随机扰动（不确定性）
 * - 防崩坏 3 条约束
 *
 * @param databaseManager 三库管理入口
 * @param needEvaluator 阵容需求评估器
 * @param targetFinder 目标搜索器
 * @param offerGenerator 报价生成器
 * @param decisionEngine 决策引擎
 * @param executor 转会执行器
 * @param config AI 转会配置
 */
class AiTransferService(
    private val databaseManager: DatabaseManager,
    private val needEvaluator: AiSquadNeedEvaluator,
    private val targetFinder: AiTargetFinder,
    private val offerGenerator: AiOfferGenerator,
    private val decisionEngine: AiTransferDecisionEngine,
    private val executor: AiTransferExecutor,
    private val config: BasicAiConfig = BasicAiConfig.DEFAULT
) {

    /**
     * 转会窗期间每日执行（由 T07 AiClubTask 调用）。
     *
     * @param ctx 推进上下文
     * @return 各 AI 俱乐部的转会结果列表
     */
    suspend fun processDailyTransfers(ctx: AdvanceContext): List<AiTransferResult> = withContext(Dispatchers.IO) {
        if (!ctx.isTransferWindowOpen) return@withContext emptyList()

        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        val random = Random(ctx.randomSeed)
        val results = mutableListOf<AiTransferResult>()

        // 获取全部俱乐部（V1 简化：从 save_club_state 全量读取）
        val clubs = saveDb.saveClubStateDao().getAll(ctx.saveId)

        for (club in clubs) {
            // 玩家俱乐部不自动操作
            if (club.clubId == ctx.managerClubId) continue

            // AI 不确定性：每日按概率决定是否执行转会（控制交易频率）
            if (random.nextDouble() > config.randomness.dailyTransferProbability) continue

            try {
                val result = processClubTransfers(club, ctx, random)
                if (result.actions.isNotEmpty()) {
                    results.add(result)
                }
            } catch (e: Exception) {
                Log.w(TAG, "俱乐部 ${club.clubId} AI 转会失败: ${e.message}")
            }
        }

        if (results.isNotEmpty()) {
            Log.d(TAG, "AI 转会完成: ${results.size} 个俱乐部执行了 ${results.sumOf { it.actions.size }} 笔交易")
        }
        results
    }

    /**
     * 处理单个俱乐部的转会决策。
     *
     * 1. 阵容短板分析
     * 2. 买入决策（如有急需位置）
     * 3. 卖人决策（检查高卖人评分的球员）
     * 4. 更新预算
     *
     * @param club AI 俱乐部存档状态
     * @param ctx 推进上下文
     * @param random 随机源
     * @return 该俱乐部的转会结果
     */
    private suspend fun processClubTransfers(
        club: SaveClubStateEntity,
        ctx: AdvanceContext,
        random: Random
    ): AiTransferResult = withContext(Dispatchers.IO) {
        val actions = mutableListOf<AiTransferAction>()
        val saveDb = databaseManager.getSaveDatabaseOrNull()!!
        val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, club.clubId)
        val financial = buildFinancialState(club, players)

        // 1. 阵容短板分析
        val needs = needEvaluator.analyze(ctx.saveId, club.clubId, ctx.currentDate)
        val topNeed = needs.firstOrNull()

        // 2. 买入决策（如有急需位置）
        if (topNeed != null && topNeed.needScore > config.thresholds.needScoreActionThreshold) {
            val buyAction = tryBuyPlayer(club, topNeed, financial, ctx, random)
            if (buyAction != null) actions.add(buyAction)
        }

        // 3. 卖人决策
        val sellActions = trySellPlayers(club, players, financial, ctx, random)
        actions.addAll(sellActions)

        // 4. 计算预算变化
        val totalSpent = actions.filter { it.type == AiActionType.BUY }.sumOf { it.fee }
        val totalEarned = actions.filter { it.type == AiActionType.SELL }.sumOf { it.fee }
        val updatedBudget = club.transferBudget - totalSpent + totalEarned

        AiTransferResult(
            clubId = club.clubId,
            actions = actions,
            budgetUsed = totalSpent,
            budgetEarned = totalEarned,
            budgetRemaining = updatedBudget
        )
    }

    /**
     * 尝试买入球员补强急需位置。
     *
     * 1. 搜索候选球员
     * 2. 对每个候选生成报价 + 评分
     * 3. 选择最高分目标
     * 4. 防崩坏约束检查
     * 5. 卖方响应评估
     * 6. 执行买入
     *
     * @param club 买方俱乐部
     * @param topNeed 最急需位置
     * @param financial 买方财政状态
     * @param ctx 推进上下文
     * @param random 随机源
     * @return 买入动作（null 表示未买入）
     */
    private suspend fun tryBuyPlayer(
        club: SaveClubStateEntity,
        topNeed: PositionNeed,
        financial: ClubFinancialState,
        ctx: AdvanceContext,
        random: Random
    ): AiTransferAction? = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull()!!
        val currentYear = ctx.currentDate.year

        // 1. 搜索候选球员
        val candidates = targetFinder.findCandidates(
            saveId = ctx.saveId,
            excludeClubId = club.clubId,
            position = topNeed.position,
            currentYear = currentYear
        )
        if (candidates.isEmpty()) return@withContext null

        // 2. 对每个候选生成报价 + 评分，筛选可承受的目标
        val scoredTargets = mutableListOf<Pair<PlayerCandidate, AiOffer>>()
        for (candidate in candidates) {
            // 获取卖方俱乐部
            val sellerClub = candidate.currentClubId?.let {
                saveDb.saveClubStateDao().getByClub(ctx.saveId, it)
            }

            // 生成报价
            val offer = offerGenerator.generateOffer(
                saveId = ctx.saveId,
                candidate = candidate,
                sellerClub = sellerClub,
                financial = financial,
                currentDate = ctx.currentDate,
                currentYear = currentYear,
                random = random
            )

            scoredTargets.add(candidate to offer)
        }

        // 3. 评分并排序
        val targets = scoredTargets.map { (candidate, offer) ->
            val target = decisionEngine.scoreTarget(candidate, topNeed, financial, offer, random)
            Triple(candidate, offer, target)
        }.filter { it.third.isAffordable }
            .sortedByDescending { it.third.targetScore }

        // 4. 选择最高分目标
        val (bestCandidate, bestOffer, bestTarget) = targets.firstOrNull() ?: return@withContext null

        // 5. 防崩坏约束检查
        val windowTxCount = executor.countWindowTransactions(ctx.saveId, club.clubId, ctx.currentDate)
        val violation = decisionEngine.checkConstraints(
            target = bestTarget,
            windowTxCount = windowTxCount,
            topNeedPosition = topNeed.position,
            targetPosition = bestCandidate.position
        )
        if (violation != null) {
            Log.d(TAG, "俱乐部 ${club.clubId} 买入被约束拦截: ${violation.code} - ${violation.message}")
            return@withContext null
        }

        // 6. 卖方响应评估
        val sellerClub = bestCandidate.currentClubId?.let {
            saveDb.saveClubStateDao().getByClub(ctx.saveId, it)
        }
        val valuation = offerGenerator.estimateValuation(
            saveId = ctx.saveId,
            candidate = bestCandidate,
            sellerClub = sellerClub,
            currentDate = ctx.currentDate,
            currentYear = currentYear
        )

        val sellerDecision = decisionEngine.evaluateSellerResponse(
            offer = bestOffer,
            valuation = valuation,
            sellerClub = sellerClub,
            buyerClubId = club.clubId,
            random = random
        )

        // 7. 根据卖方决策执行
        val (finalFee, shouldExecute) = when (sellerDecision) {
            is SellerDecision.AcceptDirectly -> bestOffer.fee to true
            is SellerDecision.Accept -> bestOffer.fee to true
            is SellerDecision.Counter -> {
                // 基础版：如果还价 ≤ 最大报价，接受还价
                val counterFee = sellerDecision.counter.fee
                if (counterFee <= bestOffer.fee) counterFee to true
                else null to false
            }
            is SellerDecision.Reject -> {
                Log.d(TAG, "俱乐部 ${club.clubId} 买入 ${bestCandidate.playerName} 被卖方拒绝: ${sellerDecision.reason}")
                null to false
            }
        }

        if (!shouldExecute || finalFee == null) return@withContext null

        // 8. 执行买入
        val success = executor.executeBuy(
            saveId = ctx.saveId,
            playerId = bestCandidate.playerId,
            fromClubId = bestCandidate.currentClubId,
            toClubId = club.clubId,
            fee = finalFee,
            wage = bestOffer.wage,
            contractYears = bestOffer.contractYears,
            squadRole = "starter", // 买入球员默认为主力
            currentDate = ctx.currentDate
        )

        if (success) {
            AiTransferAction(
                type = AiActionType.BUY,
                playerId = bestCandidate.playerId,
                targetClubId = club.clubId,
                fee = finalFee,
                reason = "补强 ${topNeed.position}（评分 ${bestTarget.targetScore.toInt()}）"
            )
        } else {
            null
        }
    }

    /**
     * 尝试卖出球员。
     *
     * 对每个球员：
     * 1. 按概率决定是否评估卖人（控制卖人频率）
     * 2. 计算市场价值
     * 3. 模拟报价 = 市场价值 × 80%
     * 4. 6 因子卖人决策
     * 5. 如果 shouldSell → 执行卖出
     *
     * @param club 卖方俱乐部
     * @param players 俱乐部全部球员
     * @param financial 卖方财政状态
     * @param ctx 推进上下文
     * @param random 随机源
     * @return 卖出动作列表
     */
    private suspend fun trySellPlayers(
        club: SaveClubStateEntity,
        players: List<SavePlayerStateEntity>,
        financial: ClubFinancialState,
        ctx: AdvanceContext,
        random: Random
    ): List<AiTransferAction> = withContext(Dispatchers.IO) {
        val actions = mutableListOf<AiTransferAction>()
        val currentYear = ctx.currentDate.year

        // 批量查询球员基础信息（获取位置 + 出生日期）
        val playerIds = players.map { it.playerId }
        val historyPlayers = if (playerIds.isEmpty()) {
            emptyMap()
        } else {
            databaseManager.historyPlayerDao().getPlayersByIds(playerIds).associateBy { it.playerId }
        }

        // 计算位置厚度
        val squadDepth = mutableMapOf<String, Int>()
        for (state in players) {
            val pos = historyPlayers[state.playerId]?.primaryPosition ?: "CM"
            squadDepth[pos] = (squadDepth[pos] ?: 0) + 1
        }

        // 防崩坏：限制每次最多卖 2 人
        var sellCount = 0
        val maxSellsPerDay = 2

        for (player in players) {
            if (sellCount >= maxSellsPerDay) break

            // AI 不确定性：按概率决定是否评估该球员
            if (random.nextDouble() > config.randomness.sellDecisionProbability) continue

            // 跳过受伤球员（不好卖）
            if (player.injuryStatus != "healthy") continue

            val historyPlayer = historyPlayers[player.playerId]
            val playerPosition = historyPlayer?.primaryPosition ?: "CM"
            val birthDate = historyPlayer?.birthDate

            // 计算市场价值
            val marketValue = if (player.marketValue > 0) {
                player.marketValue
            } else {
                economyEstimator.estimateMarketValue(
                    ca = player.currentCa,
                    pa = player.currentPa,
                    age = runCatching {
                        valueEstimatorUtil.computeAge(birthDate, ctx.currentDate)
                    }.getOrElse { 25 },
                    position = playerPosition,
                    contractUntil = player.contractUntil,
                    currentYear = currentYear
                )
            }

            // 模拟报价 = 市场价值 × 80%
            val offerAmount = (marketValue * config.offer.initialOfferRatio).toInt()

            // 6 因子卖人决策
            val decision = decisionEngine.shouldSell(
                player = player,
                offerAmount = offerAmount,
                marketValue = marketValue,
                financial = financial,
                squadDepth = squadDepth,
                playerPosition = playerPosition,
                birthDate = birthDate,
                currentDate = ctx.currentDate,
                random = random
            )

            if (decision.shouldSell) {
                // 防崩坏：检查窗口交易上限
                val windowTxCount = executor.countWindowTransactions(ctx.saveId, club.clubId, ctx.currentDate)
                if (windowTxCount >= config.constraints.maxTransfersPerWindow) break

                // 执行卖出
                val success = executor.executeSell(
                    saveId = ctx.saveId,
                    playerId = player.playerId,
                    fromClubId = club.clubId,
                    fee = offerAmount,
                    currentDate = ctx.currentDate
                )

                if (success) {
                    sellCount++
                    actions.add(AiTransferAction(
                        type = AiActionType.SELL,
                        playerId = player.playerId,
                        targetClubId = null,
                        fee = offerAmount,
                        reason = decision.reason
                    ))
                }
            }
        }

        actions
    }

    /**
     * 由俱乐部存档状态 + 球员工资构建财政状态。
     *
     * - transferBudgetRemaining = club.transferBudget
     * - wageBudgetRemaining = club.wageBudget - 当前总工资
     * - wageToIncomeRatio = 当前总工资 / club.wageBudget（>0.85 高压力）
     */
    private fun buildFinancialState(
        club: SaveClubStateEntity,
        players: List<SavePlayerStateEntity>
    ): ClubFinancialState {
        val totalWages = players.sumOf { it.wage }
        val wageBudgetRemaining = (club.wageBudget - totalWages).coerceAtLeast(0)
        val wageToIncomeRatio = if (club.wageBudget > 0) {
            (totalWages.toDouble() / club.wageBudget).coerceIn(0.0, 2.0)
        } else {
            1.0
        }
        return ClubFinancialState(
            transferBudgetRemaining = club.transferBudget,
            wageBudgetRemaining = wageBudgetRemaining,
            wageToIncomeRatio = wageToIncomeRatio,
            balance = club.balance
        )
    }

    /** T11 估价器工具实例（用于年龄计算） */
    private val valueEstimatorUtil = PlayerValueEstimator()

    /** T10 经济估算器（用于市场价值回退计算） */
    private val economyEstimator = EconomyEstimator()

    companion object {
        private const val TAG = "AiTransferService"

        /**
         * 工厂方法：创建 AiTransferService 并自动装配所有组件。
         *
         * @param databaseManager 三库管理入口
         * @param config AI 转会配置
         * @return 已装配的 AiTransferService 实例
         */
        fun create(
            databaseManager: DatabaseManager,
            config: BasicAiConfig = BasicAiConfig.DEFAULT
        ): AiTransferService {
            val economyEstimator = EconomyEstimator()
            val valueEstimator = PlayerValueEstimator()
            val searchEngine = PlayerSearchEngine(databaseManager, economyEstimator)
            val clubNegotiator = ClubNegotiator()

            val needEvaluator = AiSquadNeedEvaluator(databaseManager, config, valueEstimator)
            val targetFinder = AiTargetFinder(searchEngine, config)
            val offerGenerator = AiOfferGenerator(databaseManager, valueEstimator, economyEstimator, config)
            val decisionEngine = AiTransferDecisionEngine(offerGenerator, clubNegotiator, economyEstimator, config)
            val executor = AiTransferExecutor(databaseManager)

            return AiTransferService(
                databaseManager = databaseManager,
                needEvaluator = needEvaluator,
                targetFinder = targetFinder,
                offerGenerator = offerGenerator,
                decisionEngine = decisionEngine,
                executor = executor,
                config = config
            )
        }
    }
}
