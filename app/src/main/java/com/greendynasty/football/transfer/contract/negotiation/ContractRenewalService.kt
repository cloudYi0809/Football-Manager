package com.greendynasty.football.transfer.contract.negotiation

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.contract.config.ContractRenewalConfig
import com.greendynasty.football.transfer.contract.model.ContractRenewalEntity
import com.greendynasty.football.transfer.contract.model.InitiationType
import com.greendynasty.football.transfer.contract.model.RenewalContext
import com.greendynasty.football.transfer.contract.model.RenewalSpecialTerms
import com.greendynasty.football.transfer.contract.model.RenewalStatus
import com.greendynasty.football.transfer.contract.wage.AgentCommissionCalculator
import com.greendynasty.football.transfer.contract.wage.WageBreakdown
import com.greendynasty.football.transfer.contract.wage.WageCalculator
import com.greendynasty.football.transfer.negotiation.config.NegotiationConfig
import com.greendynasty.football.transfer.negotiation.engine.PersonalTermsNegotiator
import com.greendynasty.football.transfer.negotiation.engine.PlayerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.model.PlayerImportance
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * T12.2 合同续约谈判服务（V0.1 `09_转会_合同_经纪人系统.md` §六）。
 *
 * 复用 T11 [PersonalTermsNegotiator] 进行球员条款评估，但简化为 1-2 回合（任务要求）。
 *
 * 续约谈判流程：
 * 1. [initiateRenewal]：发起续约 → 计算球员要求（WageCalculator 5 因子）+ 续约意愿 → 创建续约报价（DRAFT）
 * 2. [submitRenewalOffer]：玩家提交条款（年限/工资/违约金/佣金）→ 调用 T11 PersonalTermsNegotiator 评估
 * 3. [acceptCounter]：球员还价后，玩家接受 → [completeRenewal]
 * 4. [withdrawRenewal]：玩家撤回 → 关系受损
 *
 * 与 T11 谈判的差异：
 * - 无卖方俱乐部（球员已在俱乐部）
 * - 谈判焦点在工资/年限/违约金/特殊条款，无转会费
 * - 球员议价权基于合同剩余（剩少 → 议价权高）
 * - 续约特有条款（涨薪/退役/青训保护）通过评分加成体现
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 *
 * @property databaseManager 三库管理入口
 * @param saveId 当前存档 ID
 * @param clubId 玩家俱乐部 ID
 * @property config 续约配置
 * @property wageCalculator T12.1 工资计算器
 * @property commissionCalculator T12.4 经纪人佣金计算器
 * @property personalTermsNegotiator T11 个人条款谈判引擎（复用）
 */
class ContractRenewalService(
    private val databaseManager: DatabaseManager,
    private val saveId: Int,
    private val clubId: Int,
    private val config: ContractRenewalConfig = ContractRenewalConfig.DEFAULT,
    private val negotiationConfig: NegotiationConfig = NegotiationConfig.DEFAULT,
    private val wageCalculator: WageCalculator = WageCalculator(config),
    private val commissionCalculator: AgentCommissionCalculator = AgentCommissionCalculator(config),
    private val personalTermsNegotiator: PersonalTermsNegotiator = PersonalTermsNegotiator(negotiationConfig)
) {

    // ==================== 1. 发起续约 ====================

    /**
     * 发起续约（V0.1 09 §六）。
     *
     * 流程：
     * 1. 校验球员属于本俱乐部 + 无进行中续约
     * 2. 计算续约要求（WageCalculator 5 因子）
     * 3. 计算续约意愿（简化版：基于俱乐部声望 + 球员野心）
     * 4. 创建续约报价（DRAFT）
     *
     * @param ctx 续约上下文
     * @param playerId 球员 ID
     * @param initiationType 触发类型
     * @param triggerReason 触发原因
     * @return 发起结果
     */
    suspend fun initiateRenewal(
        ctx: RenewalContext,
        playerId: Int,
        initiationType: InitiationType = InitiationType.PLAYER_INITIATED,
        triggerReason: String? = null
    ): RenewalInitiateResult = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext RenewalInitiateResult.Failed("存档未加载")

        try {
            // 1. 校验球员
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)
                ?: return@withContext RenewalInitiateResult.Failed("球员不存在")
            val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
                ?: return@withContext RenewalInitiateResult.Failed("球员存档状态不存在")
            if (state.currentClubId != clubId) {
                return@withContext RenewalInitiateResult.Failed("球员不属于本俱乐部")
            }

            // 2. 校验是否有进行中的续约
            val active = databaseManager.contractRenewalDao().getActiveByPlayer(saveId, playerId)
            if (active != null) {
                return@withContext RenewalInitiateResult.Failed("该球员已有进行中的续约谈判")
            }

            // 3. 计算续约要求（WageCalculator 5 因子）
            val clubEntity = databaseManager.historyClubDao().getClub(clubId)
                ?: return@withContext RenewalInitiateResult.Failed("俱乐部不存在")
            val saveClub = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
            val attributes = databaseManager.historyPlayerDao().getLatestAttributes(playerId)

            val wageBreakdown = wageCalculator.calculateExpectedWage(
                player = state,
                clubEntity = clubEntity,
                saveClubState = saveClub,
                leagueId = resolveClubLeagueId(clubId),
                currentYear = ctx.currentDate.year
            )

            // 4. 计算合同剩余月数 + 要求工资
            val monthsRemaining = monthsUntilContractEnd(state.contractUntil, ctx.currentDate)
            val demandsWage = wageCalculator.calculateDemandsWage(
                expectedWage = wageBreakdown.expectedWage,
                squadRole = state.squadRole,
                monthsRemaining = monthsRemaining
            )
            val demandsMaxYears = resolveDemandsMaxYears(player, ctx.currentDate)

            // 5. 计算续约意愿（简化版）
            val willingness = calculateWillingness(state, attributes, clubEntity, saveClub, ctx)

            // 6. 校验意愿
            if (willingness < config.willingness.minWillingnessToNegotiate) {
                // 球员拒绝续约 → 生成新闻
                generateNews(
                    ctx = ctx,
                    playerId = playerId,
                    title = "${player.realName} 拒绝续约",
                    body = "${player.realName} 表示暂时无意与俱乐部续约（意愿 ${"%.0f".format(willingness * 100)}%）。",
                    type = "RENEWAL_REJECTED"
                )
                return@withContext RenewalInitiateResult.PlayerRejected(
                    "球员无意续约（意愿 ${"%.0f".format(willingness * 100)}%）",
                    willingness
                )
            }

            // 7. 创建续约报价（DRAFT）
            val now = ctx.currentDate.toString()
            val expiresDate = ctx.currentDate.plusDays(config.offer.validityDays.toLong()).toString()
            val renewalId = databaseManager.contractRenewalDao().insert(
                ContractRenewalEntity(
                    renewalId = 0,
                    saveId = saveId,
                    playerId = playerId,
                    clubId = clubId,
                    initiationType = initiationType.name,
                    triggerReason = triggerReason,
                    currentWage = state.wage,
                    currentContractUntil = state.contractUntil,
                    currentSquadRole = state.squadRole,
                    proposedWage = 0,
                    proposedContractYears = 0,
                    proposedSigningBonus = 0,
                    proposedAgentCommission = 0,
                    proposedReleaseClause = null,
                    rolePromise = RolePromise.STARTER.name,
                    demandsWage = demandsWage,
                    demandsMaxYears = demandsMaxYears,
                    willingnessScore = willingness,
                    status = RenewalStatus.DRAFT.name,
                    currentRound = 0,
                    createdDate = now,
                    expiresDate = expiresDate
                )
            ).toInt()

            Log.d(TAG, "续约已发起: renewalId=$renewalId, playerId=$playerId, demandsWage=$demandsWage, willingness=$willingness")
            RenewalInitiateResult.Success(
                renewalId = renewalId,
                demandsWage = demandsWage,
                demandsMaxYears = demandsMaxYears,
                expectedWage = wageBreakdown.expectedWage,
                willingness = willingness,
                wageBreakdown = wageBreakdown,
                monthsRemaining = monthsRemaining
            )
        } catch (e: Exception) {
            Log.e(TAG, "发起续约失败: playerId=$playerId", e)
            RenewalInitiateResult.Failed("发起续约失败：${e.message}")
        }
    }

    // ==================== 2. 提交续约报价 ====================

    /**
     * 玩家提交续约报价（V0.1 09 §六）。
     *
     * 流程：
     * 1. 校验工资预算 + 转会预算（签字费 + 佣金）
     * 2. 更新续约报价条款（年限/工资/违约金/佣金）
     * 3. 调用 T11 PersonalTermsNegotiator 评估（复用谈判引擎）
     * 4. 续约特有条款（涨薪/退役/青训保护）通过评分加成体现
     *
     * @param ctx 续约上下文
     * @param renewalId 续约报价 ID
     * @param weeklyWage 提议周薪
     * @param contractYears 提议年限
     * @param signingBonus 签字费
     * @param rolePromise 角色承诺
     * @param releaseClause 违约金（可空）
     * @param specialTerms 续约特有条款（可空）
     * @return 提交结果
     */
    suspend fun submitRenewalOffer(
        ctx: RenewalContext,
        renewalId: Int,
        weeklyWage: Int,
        contractYears: Int,
        signingBonus: Int,
        rolePromise: RolePromise,
        releaseClause: Int? = null,
        specialTerms: RenewalSpecialTerms? = null
    ): RenewalSubmitResult = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext RenewalSubmitResult.Failed("存档未加载")

        try {
            val renewal = databaseManager.contractRenewalDao().get(renewalId)
                ?: return@withContext RenewalSubmitResult.Failed("续约报价不存在")
            if (RenewalStatus.TERMINAL.contains(RenewalStatus.valueOf(renewal.status))) {
                return@withContext RenewalSubmitResult.Failed("续约已结束（${renewal.status}）")
            }

            // 1. 校验工资预算（年化增量）
            val saveClub = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
                ?: return@withContext RenewalSubmitResult.Failed("俱乐部不存在")
            val wageIncrease = (weeklyWage - renewal.currentWage).coerceAtLeast(0)
            val annualWageImpact = wageIncrease * 52
            if (annualWageImpact > saveClub.wageBudget) {
                return@withContext RenewalSubmitResult.Failed(
                    "工资预算不足（年化增量 $annualWageImpact，剩余 ${saveClub.wageBudget}）"
                )
            }

            // 2. 计算经纪人佣金（T12.4）
            val agent = renewal.let { r ->
                // V1 简化：通过 history.agent 表查询（暂无 player→agent 关联，取 null）
                // 真实场景应由 T11 经纪人系统提供 player.agentId
                null
            }
            val commissionBreakdown = commissionCalculator.calculate(
                weeklyWage = weeklyWage,
                contractYears = contractYears,
                agent = agent
            )

            // 3. 校验签字费 + 佣金预算
            val upfrontCost = signingBonus + commissionBreakdown.commission
            if (upfrontCost > saveClub.transferBudget) {
                return@withContext RenewalSubmitResult.Failed(
                    "转会预算不足（签字费+佣金 $upfrontCost，剩余 ${saveClub.transferBudget}）"
                )
            }

            // 4. 更新续约报价
            val updatedRenewal = renewal.copy(
                proposedWage = weeklyWage,
                proposedContractYears = contractYears,
                proposedSigningBonus = signingBonus,
                proposedAgentCommission = commissionBreakdown.commission,
                proposedReleaseClause = releaseClause,
                rolePromise = rolePromise.name,
                status = RenewalStatus.SUBMITTED.name,
                currentRound = renewal.currentRound + 1
            )
            databaseManager.contractRenewalDao().update(updatedRenewal)

            // 5. 调用 T11 PersonalTermsNegotiator 评估
            val decision = evaluateWithT11(ctx, updatedRenewal, specialTerms)

            // 6. 根据决策更新状态
            when (decision) {
                is PlayerDecision.Accept -> {
                    // 球员接受 → 直接完成续约
                    val completeResult = completeRenewal(ctx, updatedRenewal, decision)
                    RenewalSubmitResult.Accepted(
                        message = decision.message,
                        completeResult = completeResult
                    )
                }
                is PlayerDecision.Reject -> {
                    databaseManager.contractRenewalDao().updateStatus(
                        renewalId, RenewalStatus.PLAYER_REJECTED.name
                    )
                    RenewalSubmitResult.Rejected(decision.reason)
                }
                is PlayerDecision.Counter -> {
                    // 检查是否已达最大轮次
                    val newRound = updatedRenewal.currentRound
                    if (newRound >= config.negotiation.maxRounds) {
                        // 达到上限 → 强制破裂
                        databaseManager.contractRenewalDao().updateStatus(
                            renewalId, RenewalStatus.COLLAPSED.name
                        )
                        RenewalSubmitResult.Rejected("已达最大谈判轮次（${config.negotiation.maxRounds} 轮），谈判破裂")
                    } else {
                        databaseManager.contractRenewalDao().updateStatus(
                            renewalId, RenewalStatus.PLAYER_COUNTERED.name
                        )
                        RenewalSubmitResult.Counter(
                            counterWeeklyWage = decision.counterTerms.weeklyWage,
                            counterContractYears = decision.counterTerms.contractYears,
                            counterSigningBonus = decision.counterTerms.signingBonus,
                            counterAgentCommission = decision.counterTerms.agentCommission,
                            message = decision.message,
                            willingness = decision.willingness
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提交续约报价失败: renewalId=$renewalId", e)
            RenewalSubmitResult.Failed("提交续约报价失败：${e.message}")
        }
    }

    // ==================== 3. 接受还价 ====================

    /**
     * 玩家接受球员还价（V0.1 09 §六）。
     *
     * 用还价条款直接完成续约。
     */
    suspend fun acceptCounter(
        ctx: RenewalContext,
        renewalId: Int,
        counterWeeklyWage: Int,
        counterContractYears: Int,
        counterSigningBonus: Int,
        counterAgentCommission: Int
    ): RenewalCompleteResult? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        try {
            val renewal = databaseManager.contractRenewalDao().get(renewalId)
                ?: return@withContext null
            // 直接完成续约
            val fakeDecision = PlayerDecision.Accept(
                willingness = renewal.willingnessScore,
                termsScore = 1.0,
                message = "玩家接受还价"
            )
            completeRenewal(
                ctx,
                renewal.copy(
                    proposedWage = counterWeeklyWage,
                    proposedContractYears = counterContractYears,
                    proposedSigningBonus = counterSigningBonus,
                    proposedAgentCommission = counterAgentCommission
                ),
                fakeDecision
            )
        } catch (e: Exception) {
            Log.e(TAG, "接受还价失败: renewalId=$renewalId", e)
            null
        }
    }

    // ==================== 4. 撤回续约 ====================

    /**
     * 玩家撤回续约（V0.1 09 §六 谈判破裂流程）。
     *
     * - 续约状态 → WITHDRAWN
     * - 球员意愿高时撤回 → 士气下降
     */
    suspend fun withdrawRenewal(ctx: RenewalContext, renewalId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext false
        try {
            val renewal = databaseManager.contractRenewalDao().get(renewalId) ?: return@withContext false
            databaseManager.contractRenewalDao().updateStatus(renewalId, RenewalStatus.WITHDRAWN.name)

            // 球员意愿高时撤回 → 士气下降
            if (renewal.willingnessScore > 0.6) {
                val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, renewal.playerId)
                if (state != null) {
                    val newMorale = (state.morale - 5).coerceIn(0, 100)
                    databaseManager.savePlayerStateDao().updateMorale(saveId, renewal.playerId, newMorale)

                    val player = databaseManager.historyPlayerDao().getPlayer(renewal.playerId)
                    generateNews(
                        ctx = ctx,
                        playerId = renewal.playerId,
                        title = "${player?.realName ?: "球员"} 续约被撤回",
                        body = "俱乐部撤回了 ${player?.realName ?: "球员"} 的续约报价，球员士气下降。",
                        type = "RENEWAL_WITHDRAWN"
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "撤回续约失败: renewalId=$renewalId", e)
            false
        }
    }

    // ==================== 5. 续约完成 ====================

    /**
     * 续约完成（V0.1 09 §六）。
     *
     * 1. 计算新合同到期日
     * 2. 更新球员合同（工资/到期日/角色）
     * 3. 扣减预算（工资年化增量 + 签字费 + 佣金）
     * 4. 更新续约报价状态 → COMPLETED
     * 5. 标记续约提醒已处理
     * 6. 生成新闻
     *
     * @param ctx 续约上下文
     * @param renewal 续约报价（含玩家接受的条款）
     * @param decision 球员决策
     * @return 续约完成结果
     */
    private suspend fun completeRenewal(
        ctx: RenewalContext,
        renewal: ContractRenewalEntity,
        decision: PlayerDecision.Accept
    ): RenewalCompleteResult {
        val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, renewal.playerId)
            ?: throw IllegalStateException("球员存档状态不存在: ${renewal.playerId}")

        // 1. 计算新合同到期日
        val newContractUntil = ctx.currentDate.plusYears(renewal.proposedContractYears.toLong()).toString()

        // 2. 更新球员合同（工资/到期日/角色）
        val newSquadRole = RolePromise.valueOf(renewal.rolePromise).squadRole
        databaseManager.savePlayerStateDao().update(
            state.copy(
                wage = renewal.proposedWage,
                contractUntil = newContractUntil,
                squadRole = newSquadRole
            )
        )

        // 3. 扣减预算
        val saveClub = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        if (saveClub != null) {
            // 工资预算扣减（年化增量）
            val wageIncrease = (renewal.proposedWage - renewal.currentWage).coerceAtLeast(0)
            val annualWageImpact = wageIncrease * 52
            databaseManager.saveClubStateDao().updateWageBudget(
                saveId, clubId, (saveClub.wageBudget - annualWageImpact).coerceAtLeast(0)
            )
            // 转会预算扣减（签字费 + 佣金）
            val upfrontCost = renewal.proposedSigningBonus + renewal.proposedAgentCommission
            if (upfrontCost > 0) {
                databaseManager.saveClubStateDao().updateTransferBudget(
                    saveId, clubId, (saveClub.transferBudget - upfrontCost).coerceAtLeast(0)
                )
            }
        }

        // 4. 工资变化百分比
        val wageChangePercent = if (renewal.currentWage > 0) {
            (renewal.proposedWage - renewal.currentWage).toDouble() / renewal.currentWage * 100
        } else 0.0

        // 5. 更新续约报价状态 → COMPLETED
        databaseManager.contractRenewalDao().update(
            renewal.copy(
                status = RenewalStatus.COMPLETED.name,
                completedDate = ctx.currentDate.toString(),
                newContractUntil = newContractUntil,
                wageChangePercent = wageChangePercent
            )
        )

        // 6. 标记续约提醒已处理
        databaseManager.contractReminderDao().markHandledByPlayer(saveId, renewal.playerId)

        // 7. 生成新闻
        val player = databaseManager.historyPlayerDao().getPlayer(renewal.playerId)
        val clubName = databaseManager.historyClubDao().getClub(clubId)?.clubName ?: "俱乐部"
        val changeText = when {
            wageChangePercent >= 30 -> "大幅涨薪续约"
            wageChangePercent >= 10 -> "涨薪续约"
            wageChangePercent >= 0 -> "续约"
            else -> "降薪续约"
        }
        val newUntilYear = newContractUntil.take(4)
        generateNews(
            ctx = ctx,
            playerId = renewal.playerId,
            title = "${player?.realName ?: "球员"} 与 $clubName $changeText",
            body = "${player?.realName ?: "球员"} 与 $clubName 续约 ${renewal.proposedContractYears} 年，" +
                "新合同至 $newUntilYear 年，周薪 ${formatMoney(renewal.proposedWage)}。",
            type = "CONTRACT_RENEWAL"
        )

        Log.d(
            TAG,
            "续约完成: playerId=${renewal.playerId}, newWage=${renewal.proposedWage}, " +
                "newUntil=$newContractUntil, change=${"%.1f".format(wageChangePercent)}%"
        )

        return RenewalCompleteResult(
            renewalId = renewal.renewalId,
            playerId = renewal.playerId,
            newWage = renewal.proposedWage,
            newContractUntil = newContractUntil,
            newSquadRole = newSquadRole,
            wageChangePercent = wageChangePercent,
            signingBonus = renewal.proposedSigningBonus,
            agentCommission = renewal.proposedAgentCommission,
            message = decision.message
        )
    }

    // ==================== 内部工具 ====================

    /**
     * 调用 T11 PersonalTermsNegotiator 评估条款（复用谈判引擎）。
     *
     * 复用方式：
     * 1. 构造 PlayerValuation（用 WageCalculator 的期望工资覆盖 T11 估值）
     * 2. 调用 [PersonalTermsNegotiator.evaluateTerms]
     * 3. 续约特有条款通过条款评分加成体现（在调用前调整 terms）
     */
    private suspend fun evaluateWithT11(
        ctx: RenewalContext,
        renewal: ContractRenewalEntity,
        specialTerms: RenewalSpecialTerms?
    ): PlayerDecision {
        val player = databaseManager.historyPlayerDao().getPlayer(renewal.playerId)
            ?: throw IllegalStateException("球员不存在: ${renewal.playerId}")
        val attributes = databaseManager.historyPlayerDao().getLatestAttributes(renewal.playerId)
        val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, renewal.playerId)
            ?: throw IllegalStateException("球员存档状态不存在")
        val saveClub = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
            ?: throw IllegalStateException("俱乐部存档不存在")
        val clubEntity = databaseManager.historyClubDao().getClub(clubId)
            ?: throw IllegalStateException("俱乐部不存在")

        // 构造 PlayerValuation：使用 WageCalculator 的期望工资作为 expectedWage
        val wageBreakdown = wageCalculator.calculateExpectedWage(
            player = state,
            clubEntity = clubEntity,
            saveClubState = saveClub,
            leagueId = resolveClubLeagueId(clubId),
            currentYear = ctx.currentDate.year
        )
        val age = computeAge(player.birthDate, ctx.currentDate)
        val importance = resolveImportance(state)
        val valuation = PlayerValuation(
            baseValue = state.marketValue,
            psychologicalPrice = state.marketValue, // 续约不涉及转会费
            acceptRange = com.greendynasty.football.transfer.negotiation.estimator.PriceRange(0, Int.MAX_VALUE),
            expectedWage = wageBreakdown.expectedWage,
            importance = importance,
            importanceMultiplier = 1.0,
            contractMultiplier = 1.0,
            potentialMultiplier = 1.0,
            age = age,
            ca = state.currentCa,
            pa = state.currentPa
        )

        // 构造 ContractTermsEntity（复用 T11）
        val terms = com.greendynasty.football.transfer.negotiation.model.ContractTermsEntity(
            termsId = 0,
            saveId = saveId,
            offerId = -renewal.renewalId, // 负数标记为续约条款
            weeklyWage = renewal.proposedWage,
            contractYears = renewal.proposedContractYears,
            signingBonus = renewal.proposedSigningBonus,
            agentCommission = renewal.proposedAgentCommission,
            rolePromise = renewal.rolePromise,
            releaseClause = renewal.proposedReleaseClause
        )

        // 同位置主力数（简化：查同俱乐部同位置）
        val samePositionMainPlayers = countSamePositionMainPlayers(
            clubId, player.primaryPosition ?: "CM"
        )

        // 调用 T11 PersonalTermsNegotiator
        val decision = personalTermsNegotiator.evaluateTerms(
            terms = terms,
            valuation = valuation,
            player = player,
            attributes = attributes,
            buyerClub = saveClub,
            buyerClubReputation = clubEntity.reputation,
            hasChampionsLeague = false, // V1 简化
            hasEuropaLeague = false,
            buyerLeagueId = resolveClubLeagueId(clubId),
            buyerCountry = clubEntity.country,
            samePositionMainPlayers = samePositionMainPlayers,
            agentRelation = negotiationConfig.playerWillingness.defaultAgentRelation,
            currentRound = renewal.currentRound
        )

        // 续约特有条款评分加成（影响接受概率）
        // 如果含对球员有利的条款（涨薪/青训保护），且决策是 Counter，则有概率升级为 Accept
        if (specialTerms != null && specialTerms.hasAny() && decision is PlayerDecision.Counter) {
            val bonus = scoreRenewalSpecialTerms(specialTerms)
            if (bonus > 0.10 && decision.willingness > config.negotiation.playerAcceptThreshold - 0.10) {
                // 升级为 Accept
                return PlayerDecision.Accept(
                    willingness = decision.willingness,
                    termsScore = decision.termsScore + bonus,
                    message = "球员接受了你的合同条款（含续约特有条款加成）"
                )
            }
        }

        return decision
    }

    /**
     * 续约特有条款评分（V0.1 09 §六）。
     *
     * - 涨薪条款（表现达标自动涨薪）→ 对球员有利 → +0.20
     * - 退役条款（老将一年一签）→ 对俱乐部有利 → -0.10
     * - 青训保护条款（特殊解约金）→ 对球员有利 → +0.15
     */
    private fun scoreRenewalSpecialTerms(specialTerms: RenewalSpecialTerms): Double {
        var score = 0.0
        specialTerms.performanceRaiseClause?.let {
            score += config.negotiation.performanceRaiseBonus
        }
        specialTerms.veteranClause?.let {
            score += config.negotiation.veteranClausePenalty
        }
        specialTerms.academyProtectionClause?.let {
            score += config.negotiation.academyProtectionBonus
        }
        return score
    }

    /**
     * 续约意愿计算（简化版）。
     *
     * 综合俱乐部声望（0-1）+ 工资满意度（0-1）- 野心扣减。
     * 真实 8 因子公式在 V0.1 09 §五，这里简化为续约场景的核心因子。
     */
    private suspend fun calculateWillingness(
        state: SavePlayerStateEntity,
        attributes: PlayerAttributesEntity?,
        clubEntity: com.greendynasty.football.data.history.entity.ClubEntity,
        saveClub: SaveClubStateEntity?,
        ctx: RenewalContext
    ): Double {
        // 因子 1：俱乐部声望（0-1）
        val reputation = (saveClub?.reputation ?: clubEntity.reputation) / 100.0
        // 因子 2：当前工资满意度（当前工资 vs 期望工资）
        val wageBreakdown = wageCalculator.calculateExpectedWage(
            player = state,
            clubEntity = clubEntity,
            saveClubState = saveClub,
            leagueId = resolveClubLeagueId(clubId),
            currentYear = ctx.currentDate.year
        )
        val wageSatisfaction = (state.wage.toDouble() / wageBreakdown.expectedWage.coerceAtLeast(1))
            .coerceIn(0.0, 1.5) / 1.5
        // 因子 3：球员野心（高野心 → 续约意愿降低）
        val ambition = (attributes?.ambition ?: 50) / 100.0
        val ambitionPenalty = ambition * config.willingness.ambitionPenaltyFactor * 100

        // 综合意愿（简化）：声望 × 0.4 + 工资满意度 × 0.4 + (1 - 野心扣减) × 0.2
        val willingness = (reputation * 0.4 + wageSatisfaction * 0.4 + (1.0 - ambitionPenalty) * 0.2)
            .coerceIn(0.0, 1.0)
        return willingness
    }

    /** 解析球员要求年限（按年龄） */
    private fun resolveDemandsMaxYears(player: PlayerEntity, currentDate: LocalDate): Int {
        val age = computeAge(player.birthDate, currentDate)
        return when {
            age >= 32 -> 1
            age >= 28 -> 2
            age >= 24 -> 3
            else -> 4
        }
    }

    /** 解析俱乐部所在联赛 ID（V1 简化：暂用俱乐部 country 推断） */
    private suspend fun resolveClubLeagueId(clubId: Int): String? {
        return runCatching {
            val club = databaseManager.historyClubDao().getClub(clubId)
            // V1 简化：按国家推断联赛（英国→EPL / 西班牙→LaLiga / ...）
            when (club?.country?.lowercase()) {
                "england" -> "EPL"
                "spain" -> "LaLiga"
                "italy" -> "SerieA"
                "germany" -> "Bundesliga"
                "france" -> "Ligue1"
                "netherlands" -> "Eredivisie"
                "portugal" -> "PrimeiraLiga"
                "brazil" -> "Brasileirao"
                "argentina" -> "Argentino"
                else -> null
            }
        }.getOrNull()
    }

    /** 解析球员重要性（复用 T11 5 档） */
    private fun resolveImportance(state: SavePlayerStateEntity): PlayerImportance {
        val role = state.squadRole?.lowercase()
        return when (role) {
            "listed", "transfer_listed" -> PlayerImportance.LISTED
            "key_player", "key", "core" -> PlayerImportance.KEY
            "starter", "first_team" -> PlayerImportance.STARTER
            "rotation" -> PlayerImportance.ROTATION
            "backup", "prospect", "youth", "substitute" -> PlayerImportance.BACKUP
            else -> PlayerImportance.ROTATION
        }
    }

    /** 统计同位置主力数 */
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

    /** 计算合同剩余月数 */
    private fun monthsUntilContractEnd(contractUntil: String?, currentDate: LocalDate): Int {
        if (contractUntil.isNullOrBlank()) return 0
        return runCatching {
            val end = LocalDate.parse(contractUntil.take(10))
            Period.between(currentDate, end).let {
                it.years * 12 + it.months
            }.coerceAtLeast(0)
        }.getOrElse { 0 }
    }

    /** 计算年龄 */
    private fun computeAge(birthDate: String?, currentDate: LocalDate): Int {
        if (birthDate.isNullOrBlank()) return 18
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrElse { 18 }
    }

    /** 生成新闻 */
    private suspend fun generateNews(
        ctx: RenewalContext,
        playerId: Int,
        title: String,
        body: String,
        type: String
    ) {
        databaseManager.saveNewsDao().insert(
            SaveNewsEntity(
                saveId = saveId,
                newsDate = ctx.currentDate.toString(),
                title = title,
                body = body,
                newsType = type,
                relatedPlayerId = playerId,
                relatedClubId = clubId
            )
        )
    }

    /** 格式化金额 */
    private fun formatMoney(amount: Int): String {
        return when {
            amount >= 100_000_000 -> String.format("%.2f 亿", amount / 100_000_000.0)
            amount >= 10_000 -> String.format("%.1f 万", amount / 10_000.0)
            else -> amount.toString()
        }
    }

    /** save.db 是否就绪 */
    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    companion object {
        private const val TAG = "ContractRenewalService"
    }
}

// ==================== 续约结果类型 ====================

/** 续约发起结果 */
sealed class RenewalInitiateResult {
    /** 发起成功，返回续约 ID + 球员要求 */
    data class Success(
        val renewalId: Int,
        val demandsWage: Int,
        val demandsMaxYears: Int,
        val expectedWage: Int,
        val willingness: Double,
        val wageBreakdown: WageBreakdown,
        val monthsRemaining: Int
    ) : RenewalInitiateResult()

    /** 球员拒绝续约（意愿不足） */
    data class PlayerRejected(val message: String, val willingness: Double) : RenewalInitiateResult()

    /** 发起失败 */
    data class Failed(val reason: String) : RenewalInitiateResult()
}

/** 续约报价提交结果 */
sealed class RenewalSubmitResult {
    /** 球员接受 → 续约完成 */
    data class Accepted(val message: String, val completeResult: RenewalCompleteResult) : RenewalSubmitResult()

    /** 球员还价 */
    data class Counter(
        val counterWeeklyWage: Int,
        val counterContractYears: Int,
        val counterSigningBonus: Int,
        val counterAgentCommission: Int,
        val message: String,
        val willingness: Double
    ) : RenewalSubmitResult()

    /** 球员拒绝 */
    data class Rejected(val reason: String) : RenewalSubmitResult()

    /** 提交失败 */
    data class Failed(val reason: String) : RenewalSubmitResult()
}

/** 续约完成结果 */
data class RenewalCompleteResult(
    val renewalId: Int,
    val playerId: Int,
    val newWage: Int,
    val newContractUntil: String,
    val newSquadRole: String,
    val wageChangePercent: Double,
    val signingBonus: Int,
    val agentCommission: Int,
    val message: String
)
