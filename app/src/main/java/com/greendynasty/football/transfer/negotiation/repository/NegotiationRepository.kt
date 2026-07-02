package com.greendynasty.football.transfer.negotiation.repository

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.data.save.entity.SaveTransferOfferEntity
import com.greendynasty.football.transfer.negotiation.config.NegotiationConfig
import com.greendynasty.football.transfer.negotiation.engine.ClubNegotiator
import com.greendynasty.football.transfer.negotiation.engine.CounterOffer
import com.greendynasty.football.transfer.negotiation.engine.PersonalTermsNegotiator
import com.greendynasty.football.transfer.negotiation.engine.PlayerDecision
import com.greendynasty.football.transfer.negotiation.engine.SellerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValueEstimator
import com.greendynasty.football.transfer.negotiation.model.ContractTermsEntity
import com.greendynasty.football.transfer.negotiation.model.NegotiationSessionEntity
import com.greendynasty.football.transfer.negotiation.model.NegotiationStage
import com.greendynasty.football.transfer.negotiation.model.OfferRoundEntity
import com.greendynasty.football.transfer.negotiation.model.OfferStatus
import com.greendynasty.football.transfer.negotiation.model.OfferType
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import com.greendynasty.football.transfer.search.EconomyEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T11 报价谈判数据仓库（V0.1 `09_转会_合同_经纪人系统.md` §三-§六）。
 *
 * 协调 [PlayerValueEstimator] / [ClubNegotiator] / [PersonalTermsNegotiator]，
 * 对外提供：提交报价 / 卖方评估 / 卖方还价处理 / 球员个人条款谈判 / 谈判历史查询 等能力。
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 *
 * 12 步转会流程入口（V0.1 09 §三）：
 * 1. [submitOffer] 提交报价 → 创建报价单 + 谈判会话
 * 2. [evaluateBySeller] 卖方评估 → 决策接受/拒绝/还价
 * 3. [acceptCounter] / [modifyAndReoffer] / [withdrawOffer] 玩家响应
 * 4. [negotiatePersonalTerms] 球员个人条款谈判
 * 5. [completeTransfer] 转会完成（更新俱乐部/合同/预算/新闻）
 *
 * @param databaseManager 三库管理入口
 * @param saveId 当前存档 ID
 * @param buyerClubId 买方（玩家）俱乐部 ID
 * @param config 谈判配置
 */
class NegotiationRepository(
    private val databaseManager: DatabaseManager,
    val saveId: Int = DEFAULT_SAVE_ID,
    val buyerClubId: Int = DEFAULT_CLUB_ID,
    private val config: NegotiationConfig = NegotiationConfig.DEFAULT
) {
    private val estimator = PlayerValueEstimator(config, EconomyEstimator())
    private val clubNegotiator = ClubNegotiator(config)
    private val personalTermsNegotiator = PersonalTermsNegotiator(config)

    // ==================== 1. 提交报价 ====================

    /**
     * 提交报价（V0.1 09 §三 第 3 步）。
     *
     * 校验：转会窗状态 / 球员可交易性 / 预算 / 工资预算。
     * 创建：报价单 + 谈判会话 + 第 1 轮 OfferRound。
     *
     * @param request 报价请求
     * @return 报价 ID（失败返回 null + 原因）
     */
    suspend fun submitOffer(request: OfferRequest): OfferSubmitResult = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext OfferSubmitResult.Failed("存档未加载")

        try {
            // 1. 校验球员
            val player = databaseManager.historyPlayerDao().getPlayer(request.playerId)
                ?: return@withContext OfferSubmitResult.Failed("球员不存在")
            val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, request.playerId)
                ?: return@withContext OfferSubmitResult.Failed("球员存档状态不存在")

            // 2. 校验报价类型与球员匹配
            val validateMsg = validateOfferTypeForPlayer(request.offerType, state)
            if (validateMsg != null) return@withContext OfferSubmitResult.Failed(validateMsg)

            // 3. 校验预算
            val buyerClub = databaseManager.saveClubStateDao().getByClub(saveId, buyerClubId)
                ?: return@withContext OfferSubmitResult.Failed("买方俱乐部不存在")
            val totalUpfront = request.transferFee + request.signingBonus + request.agentCommission
            if (totalUpfront > buyerClub.transferBudget) {
                return@withContext OfferSubmitResult.Failed(
                    "预算不足（需要 $totalUpfront，剩余 ${buyerClub.transferBudget}）"
                )
            }

            // 4. 计算估价（缓存到会话）
            val attributes = databaseManager.historyPlayerDao().getLatestAttributes(request.playerId)
            val sellerClub = state.currentClubId?.let {
                databaseManager.saveClubStateDao().getByClub(saveId, it)
            }
            val currentDate = currentGameDate()
            val valuation = estimator.estimate(
                player = player,
                attributes = attributes,
                state = state,
                sellerClub = sellerClub,
                currentDate = currentDate,
                currentYear = currentDate.year
            )

            // 5. 创建报价单
            val now = currentDate.toString()
            val offerId = databaseManager.saveTransferOfferDao().insert(
                SaveTransferOfferEntity(
                    offerId = 0,
                    saveId = saveId,
                    playerId = request.playerId,
                    fromClubId = state.currentClubId, // 卖方
                    toClubId = buyerClubId, // 买方（玩家）
                    offerType = request.offerType.name.lowercase(),
                    fee = request.transferFee,
                    wageOffer = request.wageOffer,
                    contractYears = request.contractYears,
                    status = OfferStatus.SUBMITTED.name,
                    createdDate = now,
                    expiresDate = currentDate.plusDays(config.offer.validityDays.toLong()).toString(),
                    negotiationType = request.offerType.name,
                    signingBonus = request.signingBonus,
                    agentCommission = request.agentCommission,
                    rolePromise = request.rolePromise.name,
                    currentRound = 0,
                    psychologicalPrice = valuation.psychologicalPrice
                )
            ).toInt()

            // 6. 创建谈判会话
            val sessionId = databaseManager.negotiationSessionDao().insert(
                NegotiationSessionEntity(
                    saveId = saveId,
                    offerId = offerId,
                    playerId = request.playerId,
                    agentId = null, // V1 简化：暂不接入经纪人
                    stage = NegotiationStage.SELLER_EVALUATION.name,
                    currentRound = 0,
                    maxRounds = config.sellerEvaluation.maxNegotiationRounds,
                    buyerPatience = 100,
                    sellerPatience = 100,
                    playerPatience = 100,
                    cachedPsychologicalPrice = valuation.psychologicalPrice,
                    cachedExpectedWage = valuation.expectedWage,
                    startedDate = now,
                    lastUpdatedDate = now
                )
            ).toInt()

            // 7. 记录第 1 轮玩家报价
            databaseManager.offerRoundDao().insert(
                OfferRoundEntity(
                    saveId = saveId,
                    offerId = offerId,
                    roundNumber = 1,
                    roundType = "TRANSFER_NEGOTIATION",
                    proposer = "BUYER",
                    fee = request.transferFee,
                    wage = request.wageOffer,
                    contractYears = request.contractYears,
                    signingBonus = request.signingBonus,
                    agentCommission = request.agentCommission,
                    rolePromise = request.rolePromise.name,
                    reaction = "", // 待卖方评估后填入
                    reactionMessage = "玩家提交报价",
                    createdDate = now
                )
            )

            Log.d(TAG, "报价已提交: offerId=$offerId, sessionId=$sessionId, playerId=${request.playerId}")
            OfferSubmitResult.Success(offerId = offerId, valuation = valuation)
        } catch (e: Exception) {
            Log.e(TAG, "提交报价失败", e)
            OfferSubmitResult.Failed("提交报价失败：${e.message}")
        }
    }

    // ==================== 2. 卖方评估 ====================

    /**
     * 卖方评估当前报价（V0.1 09 §三 第 4 步）。
     *
     * 决策树：接受 / 拒绝 / 还价（含多轮拉锯）。
     *
     * @param offerId 报价 ID
     * @return 卖方决策 + 估价（失败返回 null）
     */
    suspend fun evaluateBySeller(offerId: Int): SellerEvaluationResult? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null

        try {
            val offer = databaseManager.saveTransferOfferDao().get(offerId)
                ?: return@withContext null
            val session = databaseManager.negotiationSessionDao().getByOffer(saveId, offerId)
                ?: return@withContext null

            // 加载完整估价（在卖方判定前先计算，便于自由签约分支复用）
            val valuation = buildValuation(offer, session) ?: return@withContext null

            // 自由签约无卖方 → 直接接受
            val offerType = runCatching { OfferType.valueOf(offer.negotiationType) }
                .getOrElse { OfferType.PERMANENT }
            if (!OfferType.needsSeller(offerType)) {
                val decision = SellerDecision.AcceptDirectly
                updateOfferStatus(offerId, OfferStatus.SELLER_ACCEPTED)
                databaseManager.negotiationSessionDao().updateStageAndRound(
                    session.sessionId,
                    NegotiationStage.PLAYER_NEGOTIATION.name,
                    session.currentRound,
                    currentGameDate().toString()
                )
                return@withContext SellerEvaluationResult(
                    decision = decision,
                    valuation = valuation
                )
            }

            val sellerClub = offer.fromClubId?.let {
                databaseManager.saveClubStateDao().getByClub(saveId, it)
            } ?: return@withContext null

            val currentRound = (databaseManager.offerRoundDao().getMaxRound(saveId, offerId) ?: 0) + 1

            // 调用卖方谈判引擎
            val decision = clubNegotiator.evaluateOffer(
                offerFee = offer.fee,
                offerWage = offer.wageOffer,
                currentRound = currentRound,
                valuation = valuation,
                sellerClub = sellerClub,
                buyerClubId = buyerClubId,
                clubRelation = config.sellerEvaluation.defaultClubRelation
            )

            // 更新报价状态 + 会话
            val now = currentGameDate().toString()
            when (decision) {
                is SellerDecision.Accept -> {
                    updateOfferStatus(offerId, OfferStatus.SELLER_ACCEPTED)
                    databaseManager.negotiationSessionDao().updateStageAndRound(
                        session.sessionId,
                        NegotiationStage.PLAYER_NEGOTIATION.name,
                        currentRound,
                        now
                    )
                }
                is SellerDecision.AcceptDirectly -> {
                    updateOfferStatus(offerId, OfferStatus.SELLER_ACCEPTED)
                    databaseManager.negotiationSessionDao().updateStageAndRound(
                        session.sessionId,
                        NegotiationStage.PLAYER_NEGOTIATION.name,
                        currentRound,
                        now
                    )
                }
                is SellerDecision.Reject -> {
                    updateOfferStatus(offerId, OfferStatus.SELLER_REJECTED)
                    databaseManager.negotiationSessionDao().markCollapsed(
                        session.sessionId, NegotiationStage.SELLER_NEGOTIATION.name
                    )
                }
                is SellerDecision.Counter -> {
                    updateOfferStatus(offerId, OfferStatus.SELLER_COUNTERED)
                    // 扣减卖方耐心
                    val newSellerPatience = (session.sellerPatience - config.sellerEvaluation.patienceLossPerRound)
                        .coerceAtLeast(0)
                    databaseManager.negotiationSessionDao().updatePatience(
                        session.sessionId,
                        session.buyerPatience,
                        newSellerPatience,
                        session.playerPatience
                    )
                    databaseManager.negotiationSessionDao().updateStageAndRound(
                        session.sessionId,
                        NegotiationStage.SELLER_NEGOTIATION.name,
                        currentRound,
                        now
                    )
                }
            }

            // 更新最近一轮的反应
            updateLatestRoundReaction(offerId, decision)

            SellerEvaluationResult(decision = decision, valuation = valuation)
        } catch (e: Exception) {
            Log.e(TAG, "卖方评估失败: offerId=$offerId", e)
            null
        }
    }

    // ==================== 3. 玩家响应卖方还价 ====================

    /**
     * 接受卖方还价（V0.1 09 §三 第 4 步：玩家选择接受还价）。
     *
     * 将还价条款写入报价单，进入球员谈判阶段。
     */
    suspend fun acceptCounter(offerId: Int, counter: CounterOffer): Boolean = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext false
        try {
            val offer = databaseManager.saveTransferOfferDao().get(offerId) ?: return@withContext false
            // 用还价条款更新报价单
            databaseManager.saveTransferOfferDao().update(
                offer.copy(
                    fee = counter.fee,
                    wageOffer = counter.wage,
                    signingBonus = counter.signingBonus,
                    agentCommission = counter.agentCommission,
                    status = OfferStatus.SELLER_ACCEPTED.name
                )
            )
            // 更新会话阶段
            val session = databaseManager.negotiationSessionDao().getByOffer(saveId, offerId)
            session?.let {
                databaseManager.negotiationSessionDao().updateStageAndRound(
                    it.sessionId,
                    NegotiationStage.PLAYER_NEGOTIATION.name,
                    it.currentRound,
                    currentGameDate().toString()
                )
            }
            // 记录新一轮（玩家接受还价）
            databaseManager.offerRoundDao().insert(
                OfferRoundEntity(
                    saveId = saveId,
                    offerId = offerId,
                    roundNumber = (databaseManager.offerRoundDao().getMaxRound(saveId, offerId) ?: 0) + 1,
                    roundType = "TRANSFER_NEGOTIATION",
                    proposer = "SELLER",
                    fee = counter.fee,
                    wage = counter.wage,
                    contractYears = offer.contractYears ?: 3,
                    signingBonus = counter.signingBonus,
                    agentCommission = counter.agentCommission,
                    rolePromise = offer.rolePromise ?: RolePromise.ROTATION.name,
                    reaction = "ACCEPT",
                    reactionMessage = "玩家接受还价",
                    createdDate = currentGameDate().toString()
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "接受还价失败: offerId=$offerId", e)
            false
        }
    }

    /**
     * 修改后重报（V0.1 09 §三 第 4 步：玩家修改后重新报价）。
     *
     * 轮次 +1，重新触发卖方评估。
     */
    suspend fun modifyAndReoffer(
        offerId: Int,
        newFee: Int,
        newWage: Int,
        newSigningBonus: Int? = null,
        newAgentCommission: Int? = null,
        newRolePromise: RolePromise? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext false
        try {
            val offer = databaseManager.saveTransferOfferDao().get(offerId) ?: return@withContext false
            val session = databaseManager.negotiationSessionDao().getByOffer(saveId, offerId)
                ?: return@withContext false

            // 校验轮次上限
            val nextRound = (databaseManager.offerRoundDao().getMaxRound(saveId, offerId) ?: 0) + 1
            if (nextRound > config.sellerEvaluation.maxNegotiationRounds) {
                return@withContext false
            }

            // 更新报价单
            databaseManager.saveTransferOfferDao().update(
                offer.copy(
                    fee = newFee,
                    wageOffer = newWage,
                    signingBonus = newSigningBonus ?: offer.signingBonus,
                    agentCommission = newAgentCommission ?: offer.agentCommission,
                    rolePromise = newRolePromise?.name ?: offer.rolePromise,
                    status = OfferStatus.SUBMITTED.name,
                    currentRound = nextRound
                )
            )

            // 记录新一轮玩家报价
            databaseManager.offerRoundDao().insert(
                OfferRoundEntity(
                    saveId = saveId,
                    offerId = offerId,
                    roundNumber = nextRound,
                    roundType = "TRANSFER_NEGOTIATION",
                    proposer = "BUYER",
                    fee = newFee,
                    wage = newWage,
                    contractYears = offer.contractYears ?: 3,
                    signingBonus = newSigningBonus ?: offer.signingBonus,
                    agentCommission = newAgentCommission ?: offer.agentCommission,
                    rolePromise = newRolePromise?.name ?: offer.rolePromise ?: RolePromise.ROTATION.name,
                    reaction = "",
                    reactionMessage = "玩家修改后重报（第 $nextRound 轮）",
                    createdDate = currentGameDate().toString()
                )
            )

            // 扣减买方耐心
            val newBuyerPatience = (session.buyerPatience - config.sellerEvaluation.patienceLossPerRound)
                .coerceAtLeast(0)
            databaseManager.negotiationSessionDao().updatePatience(
                session.sessionId,
                newBuyerPatience,
                session.sellerPatience,
                session.playerPatience
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "修改重报失败: offerId=$offerId", e)
            false
        }
    }

    /**
     * 撤回报价（V0.1 09 §三 谈判破裂流程）。
     */
    suspend fun withdrawOffer(offerId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext false
        try {
            val offer = databaseManager.saveTransferOfferDao().get(offerId) ?: return@withContext false
            databaseManager.saveTransferOfferDao().update(
                offer.copy(status = OfferStatus.WITHDRAWN.name)
            )
            val session = databaseManager.negotiationSessionDao().getByOffer(saveId, offerId)
            session?.let {
                databaseManager.negotiationSessionDao().markCollapsed(
                    it.sessionId, NegotiationStage.SELLER_NEGOTIATION.name
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "撤回报价失败: offerId=$offerId", e)
            false
        }
    }

    // ==================== 4. 球员个人条款谈判 ====================

    /**
     * 球员个人条款谈判（V0.1 09 §三 第 8 步 + §六）。
     *
     * 玩家提出合同条款 → 球员评估（8 因子意愿 + 条款评分）→ 决策。
     *
     * @param offerId 报价 ID
     * @param terms 玩家提出的合同条款
     * @return 球员决策
     */
    suspend fun negotiatePersonalTerms(offerId: Int, terms: ContractTermsEntity): PlayerDecision? =
        withContext(Dispatchers.IO) {
            if (!isSaveReady()) return@withContext null
            try {
                val offer = databaseManager.saveTransferOfferDao().get(offerId) ?: return@withContext null
                val session = databaseManager.negotiationSessionDao().getByOffer(saveId, offerId)
                    ?: return@withContext null
                val player = databaseManager.historyPlayerDao().getPlayer(offer.playerId)
                    ?: return@withContext null
                val attributes = databaseManager.historyPlayerDao().getLatestAttributes(offer.playerId)
                val buyerClub = databaseManager.saveClubStateDao().getByClub(saveId, buyerClubId)
                    ?: return@withContext null
                val buyerClubEntity = databaseManager.historyClubDao().getClub(buyerClubId)
                    ?: return@withContext null

                val valuation = buildValuation(offer, session) ?: return@withContext null

                // 计算同位置主力数（简化：从 save_player_state 查同俱乐部同位置）
                val samePositionMainPlayers = countSamePositionMainPlayers(
                    buyerClubId, player.primaryPosition ?: "CM"
                )

                val decision = personalTermsNegotiator.evaluateTerms(
                    terms = terms,
                    valuation = valuation,
                    player = player,
                    attributes = attributes,
                    buyerClub = buyerClub,
                    buyerClubReputation = buyerClubEntity.reputation,
                    hasChampionsLeague = false, // V1 简化：暂不接入欧战资格判定
                    hasEuropaLeague = false,
                    buyerLeagueId = null, // V1 简化
                    buyerCountry = buyerClubEntity.country,
                    samePositionMainPlayers = samePositionMainPlayers,
                    agentRelation = config.playerWillingness.defaultAgentRelation,
                    currentRound = session.currentRound
                )

                // 持久化合同条款
                databaseManager.contractTermsDao().deleteByOffer(saveId, offerId)
                databaseManager.contractTermsDao().insert(terms.copy(saveId = saveId, offerId = offerId))

                // 更新报价状态
                when (decision) {
                    is PlayerDecision.Accept -> {
                        updateOfferStatus(offerId, OfferStatus.PLAYER_ACCEPTED)
                    }
                    is PlayerDecision.Reject -> {
                        updateOfferStatus(offerId, OfferStatus.PLAYER_REJECTED)
                        databaseManager.negotiationSessionDao().markCollapsed(
                            session.sessionId, NegotiationStage.PLAYER_NEGOTIATION.name
                        )
                    }
                    is PlayerDecision.Counter -> {
                        updateOfferStatus(offerId, OfferStatus.PLAYER_NEGOTIATING)
                        // 扣减球员耐心
                        val newPlayerPatience = (session.playerPatience - config.sellerEvaluation.patienceLossPerRound)
                            .coerceAtLeast(0)
                        databaseManager.negotiationSessionDao().updatePatience(
                            session.sessionId,
                            session.buyerPatience,
                            session.sellerPatience,
                            newPlayerPatience
                        )
                    }
                }

                decision
            } catch (e: Exception) {
                Log.e(TAG, "球员条款谈判失败: offerId=$offerId", e)
                null
            }
        }

    // ==================== 5. 转会完成 ====================

    /**
     * 完成转会（V0.1 09 §三 第 10-12 步）。
     *
     * 1. 更新球员俱乐部与合同
     * 2. 扣减买方预算 / 增加卖方预算
     * 3. 生成新闻
     * 4. 更新报价状态为 COMPLETED
     */
    suspend fun completeTransfer(offerId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext false
        try {
            val offer = databaseManager.saveTransferOfferDao().get(offerId) ?: return@withContext false
            val terms = databaseManager.contractTermsDao().getByOffer(saveId, offerId)
                ?: return@withContext false
            val player = databaseManager.historyPlayerDao().getPlayer(offer.playerId) ?: return@withContext false
            val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, offer.playerId)
                ?: return@withContext false

            val fromClubId = state.currentClubId
            val toClubId = buyerClubId
            val currentDate = currentGameDate()
            val contractUntil = currentDate.plusYears(terms.contractYears.toLong()).toString()

            // 1. 更新球员俱乐部与合同
            databaseManager.savePlayerStateDao().updateClub(saveId, offer.playerId, toClubId)
            databaseManager.savePlayerStateDao().update(
                state.copy(
                    currentClubId = toClubId,
                    wage = terms.weeklyWage,
                    contractUntil = contractUntil,
                    squadRole = when (RolePromise.valueOf(terms.rolePromise)) {
                        RolePromise.KEY_PLAYER -> "key_player"
                        RolePromise.STARTER -> "starter"
                        RolePromise.ROTATION -> "rotation"
                        RolePromise.BACKUP -> "backup"
                        RolePromise.ACADEMY_DEV -> "prospect"
                    }
                )
            )

            // 2. 扣减买方预算
            val buyerClub = databaseManager.saveClubStateDao().getByClub(saveId, toClubId)
            if (buyerClub != null) {
                val totalSpent = offer.fee + offer.signingBonus + offer.agentCommission
                databaseManager.saveClubStateDao().updateTransferBudget(
                    saveId, toClubId, buyerClub.transferBudget - totalSpent
                )
                databaseManager.saveClubStateDao().updateBalance(
                    saveId, toClubId, buyerClub.balance - totalSpent
                )
            }

            // 3. 增加卖方预算（有卖方时）
            if (fromClubId != null && fromClubId != toClubId) {
                val sellerClub = databaseManager.saveClubStateDao().getByClub(saveId, fromClubId)
                if (sellerClub != null) {
                    databaseManager.saveClubStateDao().updateTransferBudget(
                        saveId, fromClubId, sellerClub.transferBudget + offer.fee
                    )
                    databaseManager.saveClubStateDao().updateBalance(
                        saveId, fromClubId, sellerClub.balance + offer.fee
                    )
                }
            }

            // 4. 生成新闻
            val fromClubName = fromClubId?.let {
                databaseManager.historyClubDao().getClub(it)?.clubName
            } ?: "自由身"
            val toClubName = databaseManager.historyClubDao().getClub(toClubId)?.clubName ?: "未知俱乐部"
            databaseManager.saveNewsDao().insert(
                SaveNewsEntity(
                    saveId = saveId,
                    newsDate = currentDate.toString(),
                    title = "${player.realName} 加盟 $toClubName",
                    body = "${player.realName} 从 $fromClubName 转会至 $toClubName，转会费 ${formatMoney(offer.fee)}。" +
                        "合同为期 ${terms.contractYears} 年，周薪 ${formatMoney(terms.weeklyWage)}。",
                    newsType = "transfer",
                    relatedPlayerId = offer.playerId,
                    relatedClubId = toClubId
                )
            )

            // 5. 更新报价状态
            updateOfferStatus(offerId, OfferStatus.COMPLETED)

            Log.d(TAG, "转会完成: playerId=${offer.playerId}, from=$fromClubId, to=$toClubId, fee=${offer.fee}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "转会完成失败: offerId=$offerId", e)
            false
        }
    }

    // ==================== 查询方法 ====================

    /** 获取报价 */
    suspend fun getOffer(offerId: Int): SaveTransferOfferEntity? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        databaseManager.saveTransferOfferDao().get(offerId)
    }

    /** 获取谈判会话 */
    suspend fun getSession(offerId: Int): NegotiationSessionEntity? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        databaseManager.negotiationSessionDao().getByOffer(saveId, offerId)
    }

    /** 获取谈判历史轮次 */
    suspend fun getRounds(offerId: Int): List<OfferRoundEntity> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()
        databaseManager.offerRoundDao().getByOffer(saveId, offerId)
    }

    /** 获取合同条款 */
    suspend fun getContractTerms(offerId: Int): ContractTermsEntity? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        databaseManager.contractTermsDao().getByOffer(saveId, offerId)
    }

    /** 获取玩家俱乐部的活跃报价 */
    suspend fun getActiveOffers(): List<SaveTransferOfferEntity> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()
        databaseManager.saveTransferOfferDao().getByClub(saveId, buyerClubId)
            .filterNot { OfferStatus.TERMINAL.contains(OfferStatus.valueOf(it.status)) }
    }

    // ==================== 内部工具 ====================

    /** save.db 是否就绪 */
    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    /** 当前游戏日期 */
    private suspend fun currentGameDate(): LocalDate {
        return runCatching {
            val dateStr = databaseManager.getSaveDatabaseOrNull()?.saveWorldStateDao()?.get()?.currentDate
            if (dateStr.isNullOrBlank()) LocalDate.now()
            else LocalDate.parse(dateStr.take(10))
        }.getOrElse { LocalDate.now() }
    }

    /** 校验报价类型与球员匹配 */
    private fun validateOfferTypeForPlayer(
        offerType: OfferType,
        state: SavePlayerStateEntity
    ): String? {
        return when (offerType) {
            OfferType.FREE_SIGNING -> if (state.currentClubId != null) "自由签约仅适用无主球员" else null
            OfferType.PERMANENT -> if (state.currentClubId == null) "永久转会需有主球员" else null
            OfferType.LOAN, OfferType.LOAN_WITH_BUYOUT ->
                if (state.currentClubId == null) "租借需有主球员" else null
            OfferType.PRE_CONTRACT -> null // V1 简化：不严格校验合同剩余
        }
    }

    /** 根据报价 + 会话重建估价（轻量版，使用缓存值） */
    private suspend fun buildValuation(
        offer: SaveTransferOfferEntity,
        session: NegotiationSessionEntity
    ): PlayerValuation? {
        val player = databaseManager.historyPlayerDao().getPlayer(offer.playerId) ?: return null
        val attributes = databaseManager.historyPlayerDao().getLatestAttributes(offer.playerId)
        val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, offer.playerId) ?: return null
        val sellerClub = state.currentClubId?.let {
            databaseManager.saveClubStateDao().getByClub(saveId, it)
        }
        val currentDate = currentGameDate()
        return estimator.estimate(
            player = player,
            attributes = attributes,
            state = state,
            sellerClub = sellerClub,
            currentDate = currentDate,
            currentYear = currentDate.year
        )
    }

    /** 更新报价状态 */
    private suspend fun updateOfferStatus(offerId: Int, status: OfferStatus) {
        databaseManager.saveTransferOfferDao().updateStatus(offerId, status.name)
    }

    /** 更新最近一轮的反应 */
    private suspend fun updateLatestRoundReaction(offerId: Int, decision: SellerDecision) {
        val rounds = databaseManager.offerRoundDao().getByOffer(saveId, offerId)
        val latest = rounds.maxByOrNull { it.roundNumber } ?: return
        val (reaction, message) = when (decision) {
            is SellerDecision.Accept -> "ACCEPT" to decision.message
            is SellerDecision.AcceptDirectly -> "ACCEPT" to "自由签约，无卖方"
            is SellerDecision.Reject -> "REJECT" to decision.reason
            is SellerDecision.Counter -> "COUNTER" to decision.message
        }
        databaseManager.offerRoundDao().insert(
            latest.copy(reaction = reaction, reactionMessage = message)
        )
    }

    /** 统计同位置主力数（简化：从 save_player_state 查同俱乐部球员） */
    private suspend fun countSamePositionMainPlayers(clubId: Int, position: String): Int {
        return runCatching {
            val states = databaseManager.savePlayerStateDao().getByClub(saveId, clubId)
            val playerIds = states.map { it.playerId }
            val players = databaseManager.historyPlayerDao().getPlayersByIds(playerIds)
            players.count { p ->
                p.primaryPosition == position &&
                    states.firstOrNull { it.playerId == p.playerId }?.squadRole in listOf("key_player", "starter")
            }
        }.getOrElse { 0 }
    }

    /** 格式化金额 */
    private fun formatMoney(amount: Int): String {
        return when {
            amount >= 100_000_000 -> String.format("%.2f 亿", amount / 100_000_000.0)
            amount >= 10_000 -> String.format("%.1f 万", amount / 10_000.0)
            else -> amount.toString()
        }
    }

    companion object {
        private const val TAG = "NegotiationRepository"
        private const val DEFAULT_SAVE_ID = 1
        private const val DEFAULT_CLUB_ID = 1
    }
}

/**
 * 报价请求（V0.1 09 §三 第 3 步）。
 */
data class OfferRequest(
    val playerId: Int,
    val offerType: OfferType,
    val transferFee: Int,
    val wageOffer: Int,
    val contractYears: Int,
    val signingBonus: Int,
    val agentCommission: Int,
    val rolePromise: RolePromise
)

/** 报价提交结果 */
sealed class OfferSubmitResult {
    data class Success(val offerId: Int, val valuation: PlayerValuation) : OfferSubmitResult()
    data class Failed(val reason: String) : OfferSubmitResult()
}

/** 卖方评估结果 */
data class SellerEvaluationResult(
    val decision: SellerDecision,
    val valuation: PlayerValuation
)
