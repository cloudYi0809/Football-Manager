package com.greendynasty.football.transfer.ai.decision

import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.ai.config.BasicAiConfig
import com.greendynasty.football.transfer.ai.model.AiOffer
import com.greendynasty.football.transfer.ai.model.ClubFinancialState
import com.greendynasty.football.transfer.ai.model.ConstraintViolation
import com.greendynasty.football.transfer.ai.model.PlayerCandidate
import com.greendynasty.football.transfer.ai.model.PositionNeed
import com.greendynasty.football.transfer.ai.model.SellDecision
import com.greendynasty.football.transfer.ai.model.TargetScoreBreakdown
import com.greendynasty.football.transfer.ai.model.TransferTarget
import com.greendynasty.football.transfer.ai.offer.AiOfferGenerator
import com.greendynasty.football.transfer.negotiation.engine.ClubNegotiator
import com.greendynasty.football.transfer.negotiation.engine.SellerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.search.EconomyEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.random.Random

/**
 * T13.4 AI 转会决策引擎（V0.2 `05_AI俱乐部决策模型.md` §五/§七 基础版）。
 *
 * 整合三大决策能力：
 * 1. **买方目标评分**（9 因子，固定权重）→ [scoreTarget]
 * 2. **卖方卖人决策**（6 因子，无特殊规则）→ [shouldSell]
 * 3. **卖方报价响应**（复用 T11 [ClubNegotiator]）→ [evaluateSellerResponse]
 *
 * 外加防崩坏约束检查（基础版 3 条）→ [checkConstraints]
 *
 * AI 不确定性：
 * - 目标评分 ±2% 随机扰动
 * - 卖人评分 ±3% 随机扰动
 * - 卖方响应复用 T11 ClubNegotiator 内置 ±5% 扰动
 *
 * @param offerGenerator AI 报价生成器（含 T11 估价）
 * @param clubNegotiator T11 卖方谈判引擎（复用）
 * @param economyEstimator T10 经济估算器
 * @param config AI 转会配置
 */
class AiTransferDecisionEngine(
    private val offerGenerator: AiOfferGenerator,
    private val clubNegotiator: ClubNegotiator = ClubNegotiator(),
    private val economyEstimator: EconomyEstimator = EconomyEstimator(),
    private val config: BasicAiConfig = BasicAiConfig.DEFAULT
) {

    /**
     * T11 估价器工具实例（用于合同剩余月数计算等工具方法）。
     * 无状态，可安全复用。
     */
    private val valueEstimatorUtil = com.greendynasty.football.transfer.negotiation.estimator.PlayerValueEstimator()

    // ==================== 1. 买方目标评分（9 因子） ====================

    /**
     * 对候选球员进行 9 因子目标评分（V0.2 §五 基础版固定权重）。
     *
     * target_score =
     *   position_need * 0.25
     * + current_ability_fit * 0.20
     * + potential_fit * 0.15
     * + price_value * 0.15
     * + wage_affordability * 0.10
     * + age_fit * 0.05
     * + tactical_fit * 0.05
     * + nationality_fit * 0.03
     * + commercial_value * 0.02
     *
     * @param candidate 候选球员
     * @param positionNeed 位置需求
     * @param financial 买方财政状态
     * @param offer AI 报价（含心理价位、转会费）
     * @param random 随机源
     * @return 转会目标评分结果
     */
    suspend fun scoreTarget(
        candidate: PlayerCandidate,
        positionNeed: PositionNeed,
        financial: ClubFinancialState,
        offer: AiOffer,
        random: Random = Random.Default
    ): TransferTarget = withContext(Dispatchers.IO) {
        val w = config.targetScoreWeights

        // 9 因子计算
        val positionNeedScore = (positionNeed.needScore / 100.0).coerceIn(0.0, 1.0)
        val currentAbilityFit = calculateCurrentAbilityFit(candidate)
        val potentialFit = calculatePotentialFit(candidate)
        val priceValue = calculatePriceValue(offer, financial)
        val wageAffordability = calculateWageAffordability(offer, financial)
        val ageFit = calculateAgeFit(candidate)
        val tacticalFit = 0.5 // 基础版固定 0.5
        val nationalityFit = 0.5 // 基础版固定 0.5
        val commercialValue = calculateCommercialValue(candidate)

        // 加权求和
        val rawScore = (
            positionNeedScore * w.positionNeed +
                currentAbilityFit * w.currentAbilityFit +
                potentialFit * w.potentialFit +
                priceValue * w.priceValue +
                wageAffordability * w.wageAffordability +
                ageFit * w.ageFit +
                tacticalFit * w.tacticalFit +
                nationalityFit * w.nationalityFit +
                commercialValue * w.commercialValue
            ) * 100

        // AI 不确定性：±2% 随机扰动
        val jitter = random.nextDouble(
            -config.randomness.targetScoreJitter,
            config.randomness.targetScoreJitter
        )
        val targetScore = (rawScore * (1.0 + jitter)).coerceIn(0.0, 100.0)

        // 可承受性判断
        val isAffordable = offer.fee <= financial.transferBudgetRemaining &&
            offer.wage <= financial.wageBudgetRemaining.coerceAtLeast(1)

        TransferTarget(
            playerId = candidate.playerId,
            targetScore = targetScore,
            scoreBreakdown = TargetScoreBreakdown(
                positionNeedScore = positionNeedScore,
                currentAbilityFit = currentAbilityFit,
                potentialFit = potentialFit,
                priceValue = priceValue,
                wageAffordability = wageAffordability,
                ageFit = ageFit,
                tacticalFit = tacticalFit,
                nationalityFit = nationalityFit,
                commercialValue = commercialValue
            ),
            estimatedValue = offer.marketValue,
            psychologicalPrice = offer.psychologicalPrice,
            maxOffer = offer.fee,
            expectedWage = offer.wage,
            isAffordable = isAffordable
        )
    }

    /**
     * 因子 2：当前能力匹配度（V0.2 §五）。
     *
     * 基础版期望 CA = 75（T18 才按俱乐部类型区分）。
     * fit = 1.0 - |CA - expectedCa| / 30，clamp 0-1。
     */
    private fun calculateCurrentAbilityFit(candidate: PlayerCandidate): Double {
        val expectedCa = config.expectedCa
        return (1.0 - abs(candidate.currentCa - expectedCa) / 30.0).coerceIn(0.0, 1.0)
    }

    /**
     * 因子 3：潜力匹配度（V0.2 §五）。
     *
     * fit = (PA - CA) / 30，clamp 0-1（成长空间越大越好）。
     */
    private fun calculatePotentialFit(candidate: PlayerCandidate): Double {
        val growthRoom = (candidate.potentialPa - candidate.currentCa).coerceAtLeast(0)
        return (growthRoom / 30.0).coerceIn(0.0, 1.0)
    }

    /**
     * 因子 4：身价合理性（V0.2 §五）。
     *
     * fit = 1.0 - 报价 / 剩余转会预算，clamp 0-1（报价占预算越少越合理）。
     */
    private fun calculatePriceValue(offer: AiOffer, financial: ClubFinancialState): Double {
        val budget = financial.transferBudgetRemaining.coerceAtLeast(1)
        return (1.0 - offer.fee.toDouble() / budget).coerceIn(0.0, 1.0)
    }

    /**
     * 因子 5：工资可承受性（V0.2 §五）。
     *
     * fit = 1.0 - 期望工资 / 剩余工资预算，clamp 0-1。
     */
    private fun calculateWageAffordability(offer: AiOffer, financial: ClubFinancialState): Double {
        val budget = financial.wageBudgetRemaining.coerceAtLeast(1)
        return (1.0 - offer.wage.toDouble() / budget).coerceIn(0.0, 1.0)
    }

    /**
     * 因子 6：年龄匹配度（V0.2 §五）。
     *
     * 20-28 岁黄金期 → 1.0，其他 → 0.5。
     */
    private fun calculateAgeFit(candidate: PlayerCandidate): Double {
        return if (candidate.age in 20..28) 1.0 else 0.5
    }

    /**
     * 因子 9：商业价值（V0.2 §五）。
     *
     * 由声望推算，clamp 0-1。
     */
    private fun calculateCommercialValue(candidate: PlayerCandidate): Double {
        return (candidate.reputation / 100.0).coerceIn(0.0, 1.0)
    }

    // ==================== 2. 卖方卖人决策（6 因子） ====================

    /**
     * 卖人决策（V0.2 §七 6 因子，基础版无特殊规则）。
     *
     * sell_score =
     *   offer_value_ratio * 0.25
     * + player_unhappy * 0.20
     * + contract_expiry_risk * 0.15
     * + squad_depth_cover * 0.15
     * + financial_pressure * 0.15
     * + age_decline_risk * 0.10
     *
     * @param player 球员存档状态
     * @param offerAmount 报价金额
     * @param marketValue 市场价值
     * @param financial 卖方财政状态
     * @param squadDepth 位置厚度（位置 → 人数）
     * @param playerPosition 球员主要位置（用于查询位置厚度）
     * @param birthDate 球员出生日期（用于年龄计算）
     * @param currentDate 当前游戏日期
     * @param random 随机源
     * @return 卖人决策
     */
    suspend fun shouldSell(
        player: SavePlayerStateEntity,
        offerAmount: Int,
        marketValue: Int,
        financial: ClubFinancialState,
        squadDepth: Map<String, Int>,
        playerPosition: String,
        birthDate: String?,
        currentDate: LocalDate,
        random: Random = Random.Default
    ): SellDecision = withContext(Dispatchers.IO) {
        val w = config.sellScoreWeights

        // 6 因子计算
        val offerValueRatio = (offerAmount.toDouble() / marketValue.coerceAtLeast(1))
            .coerceIn(0.0, 2.0)

        val playerUnhappy = when {
            player.morale < 30 -> 1.0
            player.morale < 50 -> 0.5
            else -> 0.0
        }

        val contractExpiryRisk = calculateContractExpiryRisk(player, currentDate)
        val squadDepthCover = calculateSquadDepthCover(playerPosition, squadDepth)
        val financialPressure = calculateFinancialPressure(financial)
        val ageDeclineRisk = calculateAgeDeclineRisk(birthDate, currentDate)

        val rawScore = (
            offerValueRatio * w.offerValueRatio +
                playerUnhappy * w.playerUnhappy +
                contractExpiryRisk * w.contractExpiryRisk +
                squadDepthCover * w.squadDepthCover +
                financialPressure * w.financialPressure +
                ageDeclineRisk * w.ageDeclineRisk
            ) * 100

        // AI 不确定性：±3% 随机扰动
        val jitter = random.nextDouble(
            -config.randomness.sellScoreJitter,
            config.randomness.sellScoreJitter
        )
        val sellScore = (rawScore * (1.0 + jitter)).coerceIn(0.0, 100.0)

        val shouldSell = sellScore >= config.thresholds.sellThreshold

        SellDecision(
            playerId = player.playerId,
            sellScore = sellScore,
            shouldSell = shouldSell,
            reason = "卖人评分 ${sellScore.toInt()}（报价/市值=${"%.1f".format(offerValueRatio)}，" +
                "士气=${player.morale}，合同风险=${"%.1f".format(contractExpiryRisk)}）"
        )
    }

    /**
     * 因子 3：合同到期风险（卖方视角）。
     *
     * | 合同剩余 | 风险 |
     * |---|---|
     * | ≤ 6 个月 | 1.0 |
     * | ≤ 12 个月 | 0.8 |
     * | ≤ 24 个月 | 0.5 |
     * | 更长 | 0.1 |
     */
    private fun calculateContractExpiryRisk(
        player: SavePlayerStateEntity,
        currentDate: LocalDate
    ): Double {
        if (player.contractUntil.isNullOrBlank()) return 1.0
        val months = valueEstimatorUtil.monthsUntilContractEnd(player.contractUntil, currentDate)
        return when {
            months <= 0 -> 1.0
            months <= 6 -> 0.8
            months <= 12 -> 0.5
            else -> 0.1
        }
    }

    /**
     * 因子 4：位置厚度覆盖（同位置人数越多越可卖）。
     *
     * depth / 3，clamp 0-1。
     */
    private fun calculateSquadDepthCover(
        playerPosition: String,
        squadDepth: Map<String, Int>
    ): Double {
        val depth = squadDepth[playerPosition] ?: 0
        return (depth / 3.0).coerceIn(0.0, 1.0)
    }

    /**
     * 因子 5：财政压力（V0.2 §七）。
     *
     * | 工资/收入比 | 压力 |
     * |---|---|
     * | > 0.85 | 1.0 |
     * | > 0.70 | 0.5 |
     * | 其他 | 0.0 |
     */
    private fun calculateFinancialPressure(financial: ClubFinancialState): Double {
        return when {
            financial.wageToIncomeRatio > 0.85 -> 1.0
            financial.wageToIncomeRatio > 0.70 -> 0.5
            else -> 0.0
        }
    }

    /**
     * 因子 6：年龄下滑风险（V0.2 §七）。
     *
     * | 年龄 | 风险 |
     * |---|---|
     * | > 33 | 0.9 |
     * | > 30 | 0.5 |
     * | 其他 | 0.1 |
     */
    private fun calculateAgeDeclineRisk(birthDate: String?, currentDate: LocalDate): Double {
        if (birthDate.isNullOrBlank()) return 0.1
        val age = runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrElse { 25 }
        return when {
            age > 33 -> 0.9
            age > 30 -> 0.5
            else -> 0.1
        }
    }

    // ==================== 3. 卖方报价响应（复用 T11 ClubNegotiator） ====================

    /**
     * 卖方对买方报价的响应（复用 T11 [ClubNegotiator.evaluateOffer]）。
     *
     * 决策树：
     * - 接受概率 ≥ 0.70 → ACCEPT（卖方接受）
     * - 接受概率 ≤ 0.25 且已达上限 → REJECT
     * - 中间区间 → COUNTER（还价）
     *
     * @param offer 买方报价
     * @param valuation 球员估价（含心理价位）
     * @param sellerClub 卖方俱乐部
     * @param buyerClubId 买方俱乐部 ID
     * @param random 随机源
     * @return T11 卖方决策（Accept / Reject / Counter）
     */
    suspend fun evaluateSellerResponse(
        offer: AiOffer,
        valuation: PlayerValuation,
        sellerClub: SaveClubStateEntity?,
        buyerClubId: Int,
        random: Random = Random.Default
    ): SellerDecision = withContext(Dispatchers.IO) {
        // 自由球员无卖方 → 直接接受
        if (sellerClub == null) {
            return@withContext SellerDecision.AcceptDirectly
        }

        clubNegotiator.evaluateOffer(
            offerFee = offer.fee,
            offerWage = offer.wage,
            currentRound = 1,
            valuation = valuation,
            sellerClub = sellerClub,
            buyerClubId = buyerClubId,
            clubRelation = 0.5, // 基础版默认关系值
            random = random
        )
    }

    // ==================== 4. 防崩坏约束（基础版 3 条） ====================

    /**
     * 防崩坏约束检查（V0.2 §八 基础版 3 条）。
     *
     * 1. 每窗交易数量限制
     * 2. 预算限制
     * 3. 位置优先级（非急需位置需更高评分）
     *
     * @param target 转会目标
     * @param windowTxCount 本窗已交易次数
     * @param topNeedPosition 最急需位置（null 表示无急需位置）
     * @param targetPosition 目标球员位置
     * @return 违反约束时返回 [ConstraintViolation]，通过返回 null
     */
    fun checkConstraints(
        target: TransferTarget,
        windowTxCount: Int,
        topNeedPosition: String?,
        targetPosition: String
    ): ConstraintViolation? {
        // 1. 窗口交易数量限制
        if (windowTxCount >= config.constraints.maxTransfersPerWindow) {
            return ConstraintViolation(
                "WINDOW_LIMIT",
                "本窗已交易 $windowTxCount 次，达到上限 ${config.constraints.maxTransfersPerWindow}"
            )
        }

        // 2. 预算限制（maxOffer 已在报价生成时限制，此处二次校验）
        if (target.maxOffer < 0) {
            return ConstraintViolation("BUDGET", "报价为负，预算不足")
        }

        // 3. 位置优先级（非急需位置要求更高评分）
        if (topNeedPosition != null && targetPosition != topNeedPosition) {
            if (target.targetScore < config.constraints.nonUrgentPositionMinScore) {
                return ConstraintViolation(
                    "POSITION_PRIORITY",
                    "非急需位置 $targetPosition，评分 ${target.targetScore.toInt()} 不足"
                )
            }
        }

        return null
    }
}
