package com.greendynasty.football.transfer.negotiation.engine

import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.transfer.negotiation.config.NegotiationConfig
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.model.PlayerImportance
import kotlin.random.Random

/**
 * T11.3 卖方俱乐部谈判引擎（V0.1 `09_转会_合同_经纪人系统.md` §四）。
 *
 * 状态机：
 * ```
 * 玩家报价 → [evaluateOffer]
 *   ├── 概率 ≥ accept_threshold (0.70) → ACCEPT（卖方接受）
 *   ├── 概率 ≤ reject_threshold (0.25)
 *   │   ├── 已达 max_rounds → REJECT（直接拒绝，结束）
 *   │   └── 未达上限 → COUNTER（还价）
 *   └── 中间区间 → COUNTER（还价）
 * ```
 *
 * 6 因子接受概率公式（V0.1 09 §四）：
 * ```
 * P = price_ratio × 0.35
 *   + contract_remain × 0.15
 *   + leave_desire × 0.15
 *   + financial_pressure × 0.15
 *   + importance_reverse × 0.15
 *   + club_relation × 0.05
 * ```
 *
 * 多轮拉锯：
 * - 每轮扣减耐心 [SellerEvaluationParams.patienceLossPerRound]
 * - 还价让步幅度随轮次增加（roundsUsed × 0.1 加成）
 * - 还价区间 = [心理价位 × (1 - 让步幅度), 心理价位]
 * - 达到 [maxRounds] 后不再还价，直接拒绝
 *
 * AI 不确定性：
 * - 还价时加入 ±5% 随机扰动（[counterRandomJitter]）
 * - 接受阈值小幅波动（±2%）模拟卖方情绪
 *
 * @param config 谈判配置
 */
class ClubNegotiator(
    private val config: NegotiationConfig = NegotiationConfig.DEFAULT
) {

    /**
     * 评估玩家本轮报价，决策接受 / 拒绝 / 还价。
     *
     * @param offerFee 玩家本轮报价的转会费
     * @param offerWage 玩家本轮报价的周薪
     * @param currentRound 当前轮次（1 起）
     * @param valuation 球员估价（含心理价位）
     * @param sellerClub 卖方俱乐部存档状态
     * @param buyerClubId 买方俱乐部 ID
     * @param clubRelation 俱乐部关系 0-1（无数据时 0.5）
     * @param random 随机源（便于测试注入）
     */
    fun evaluateOffer(
        offerFee: Int,
        offerWage: Int,
        currentRound: Int,
        valuation: PlayerValuation,
        sellerClub: SaveClubStateEntity?,
        buyerClubId: Int,
        clubRelation: Double = config.sellerEvaluation.defaultClubRelation,
        random: Random = Random.Default
    ): SellerDecision {
        // 自由签约无卖方 → 直接接受
        if (sellerClub == null) {
            return SellerDecision.AcceptDirectly
        }

        val psychologicalPrice = valuation.psychologicalPrice
        val params = config.sellerEvaluation

        // 1. 计算 6 因子接受概率
        val probability = calcAcceptanceProbability(
            offerFee = offerFee,
            psychologicalPrice = psychologicalPrice,
            valuation = valuation,
            sellerClub = sellerClub,
            clubRelation = clubRelation
        )

        // 2. 接受阈值小幅波动（模拟卖方情绪）
        val acceptThreshold = (params.acceptThreshold + random.nextDouble(-0.02, 0.02))
            .coerceIn(0.65, 0.75)
        val rejectThreshold = params.rejectThreshold

        // 3. 决策树
        return when {
            probability >= acceptThreshold -> {
                // 高概率直接接受
                SellerDecision.Accept(
                    psychologicalPrice = psychologicalPrice,
                    probability = probability,
                    message = "卖方接受了你的报价（接受概率 ${(probability * 100).toInt()}%）"
                )
            }
            probability <= rejectThreshold && currentRound >= params.maxNegotiationRounds -> {
                // 已达上限且概率低 → 直接拒绝
                SellerDecision.Reject(
                    reason = "报价与心理价位差距过大，卖方拒绝继续谈判",
                    probability = probability
                )
            }
            probability <= rejectThreshold -> {
                // 概率低但还有轮次 → 给一次还价机会
                val counter = generateCounterOffer(
                    offerFee = offerFee,
                    offerWage = offerWage,
                    psychologicalPrice = psychologicalPrice,
                    currentRound = currentRound,
                    random = random
                )
                SellerDecision.Counter(
                    counter = counter,
                    probability = probability,
                    message = "卖方认为报价偏低，提出还价"
                )
            }
            else -> {
                // 中间区间 → 还价
                val counter = generateCounterOffer(
                    offerFee = offerFee,
                    offerWage = offerWage,
                    psychologicalPrice = psychologicalPrice,
                    currentRound = currentRound,
                    random = random
                )
                SellerDecision.Counter(
                    counter = counter,
                    probability = probability,
                    message = "卖方对报价有异议，提出还价（接受概率 ${(probability * 100).toInt()}%）"
                )
            }
        }
    }

    /**
     * 6 因子接受概率公式（V0.1 09 §四）。
     *
     * P = price_ratio × 0.35 + contract_remain × 0.15 + leave_desire × 0.15
     *   + financial_pressure × 0.15 + importance_reverse × 0.15 + club_relation × 0.05
     */
    fun calcAcceptanceProbability(
        offerFee: Int,
        psychologicalPrice: Int,
        valuation: PlayerValuation,
        sellerClub: SaveClubStateEntity,
        clubRelation: Double
    ): Double {
        // 因子 1：报价/心理价位（0-1.5 clamp）
        val priceRatio = (offerFee.toDouble() / psychologicalPrice.coerceAtLeast(1))
            .coerceIn(0.0, 1.5)

        // 因子 2：合同剩余影响（剩余越短越想卖）
        val contractRemain = calcContractRemainFactor(valuation)

        // 因子 3：球员离队意愿（0-1，越高越想卖）
        val leaveDesire = calcLeaveDesire(valuation)

        // 因子 4：俱乐部财政压力（0-1，越高越想卖）
        val financialPressure = calcFinancialPressure(sellerClub)

        // 因子 5：球员重要性反向（越重要越不想卖）
        val importanceReverse = valuation.importance.reverseScore

        // 因子 6：俱乐部关系（0-1，关系好更易成交）
        val relation = clubRelation.coerceIn(0.0, 1.0)

        val probability = (
            priceRatio * 0.35 +
                contractRemain * 0.15 +
                leaveDesire * 0.15 +
                financialPressure * 0.15 +
                importanceReverse * 0.15 +
                relation * 0.05
            )
        return probability.coerceIn(0.0, 1.0)
    }

    /**
     * 合同剩余影响因子（V0.1 09 §四）。
     *
     * | 合同剩余 | 因子 |
     * |---|---|
     * | ≤ 6 个月 | 1.0（半年到期强烈想卖） |
     * | ≤ 12 个月 | 0.8 |
     * | ≤ 24 个月 | 0.5 |
     * | 更长 | 0.2（长合同不急） |
     */
    private fun calcContractRemainFactor(valuation: PlayerValuation): Double {
        // 通过 contractMultiplier 反推（与估价一致）
        return when (valuation.contractMultiplier) {
            0.50 -> 1.0
            0.75 -> 0.8
            1.00 -> 0.5
            1.15 -> 0.3
            1.25 -> 0.2
            else -> 0.5
        }
    }

    /**
     * 球员离队意愿（V0.1 09 §四）。
     *
     * 基于球员士气、上场时间、野心综合计算。
     */
    private fun calcLeaveDesire(valuation: PlayerValuation): Double {
        val params = config.sellerEvaluation
        // 士气因子（valuation 不含 morale，使用 importance 间接估算）
        val moraleFactor = when (valuation.importance) {
            PlayerImportance.LISTED -> params.leaveDesireMoraleLow
            PlayerImportance.BACKUP -> params.leaveDesireMoraleMid
            PlayerImportance.ROTATION -> params.leaveDesireMoraleNormal
            PlayerImportance.STARTER -> params.leaveDesireMoraleHigh
            PlayerImportance.KEY -> params.leaveDesireMoraleHigh
        }
        // 上场时间因子（替补/潜力股更想离队）
        val playtimeFactor = when (valuation.importance) {
            PlayerImportance.LISTED, PlayerImportance.BACKUP -> params.leaveDesireBackupBonus
            PlayerImportance.ROTATION -> 0.3
            PlayerImportance.STARTER, PlayerImportance.KEY -> params.leaveDesireStarterBonus
        }
        return (moraleFactor * 0.6 + playtimeFactor * 0.4).coerceIn(0.0, 1.0)
    }

    /**
     * 俱乐部财政压力（V0.1 09 §四）。
     *
     * V1 简化：基于俱乐部余额相对转会预算的比率。
     */
    private fun calcFinancialPressure(sellerClub: SaveClubStateEntity): Double {
        val params = config.sellerEvaluation
        // V1 简化：余额 < 转会预算的 50% 视为高财政压力
        val ratio = if (sellerClub.transferBudget > 0) {
            sellerClub.balance.toDouble() / sellerClub.transferBudget
        } else {
            1.0
        }
        return when {
            ratio < 0.0 -> params.financialPressureHigh.let { 1.0 } // 负债
            ratio < 0.5 -> 0.6
            ratio < 1.0 -> 0.3
            else -> params.financialPressureNormal
        }.coerceIn(0.0, 1.0)
    }

    /**
     * 生成还价（V0.1 09 §四 还价算法）。
     *
     * 还价区间 = [心理价位 × (1 - 让步幅度), 心理价位]
     * 让步幅度 = base_concession_rate × (1 + roundsUsed × 0.1)（越往后越让步）
     * 还价 = lerp(玩家报价, 心理价位, lerp_factor) + 随机扰动
     *
     * @param offerFee 玩家本轮报价
     * @param offerWage 玩家本轮工资报价
     * @param psychologicalPrice 心理价位
     * @param currentRound 当前轮次（1 起）
     * @param random 随机源
     */
    fun generateCounterOffer(
        offerFee: Int,
        offerWage: Int,
        psychologicalPrice: Int,
        currentRound: Int,
        random: Random = Random.Default
    ): CounterOffer {
        val params = config.sellerEvaluation

        // 让步幅度随轮次增加
        val concessionRate = params.counterConcessionRate * (1 + (currentRound - 1) * 0.1)
        val lowerBound = (psychologicalPrice * (1 - concessionRate)).toInt()
        val upperBound = psychologicalPrice

        // 还价 = 在玩家报价与心理价位之间插值，偏向心理价位
        val lerpFactor = params.counterLerpFactor.coerceIn(0.0, 1.0)
        var counterFee = lerp(offerFee.toDouble(), psychologicalPrice.toDouble(), lerpFactor)

        // 加入 ±5% 随机扰动
        val jitter = random.nextDouble(-params.counterRandomJitter, params.counterRandomJitter)
        counterFee *= (1.0 + jitter)

        // clamp 到还价区间
        counterFee = counterFee.coerceIn(lowerBound.toDouble(), upperBound.toDouble())

        // 工资/签字费按比例调整（卖方不关心工资，略降）
        val counterWage = (offerWage * 0.95).toInt().coerceAtLeast(offerWage / 2)

        return CounterOffer(
            fee = counterFee.toInt(),
            wage = counterWage,
            signingBonus = 0,
            agentCommission = 0,
            rolePromise = null,
            message = "卖方还价：要求转会费 ${formatMoney(counterFee.toInt())}"
        )
    }

    /** 线性插值 */
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /** 格式化金额显示 */
    private fun formatMoney(amount: Int): String {
        return when {
            amount >= 100_000_000 -> String.format("%.2f 亿", amount / 100_000_000.0)
            amount >= 10_000 -> String.format("%.1f 万", amount / 10_000.0)
            else -> amount.toString()
        }
    }
}

/**
 * 卖方决策（密封类，V0.1 09 §四）。
 */
sealed class SellerDecision {
    /** 自由签约无卖方，直接接受 */
    object AcceptDirectly : SellerDecision()

    /** 卖方接受报价 */
    data class Accept(
        val psychologicalPrice: Int,
        val probability: Double,
        val message: String
    ) : SellerDecision()

    /** 卖方拒绝（终止谈判） */
    data class Reject(
        val reason: String,
        val probability: Double
    ) : SellerDecision()

    /** 卖方还价 */
    data class Counter(
        val counter: CounterOffer,
        val probability: Double,
        val message: String
    ) : SellerDecision()
}

/**
 * 还价条款。
 */
data class CounterOffer(
    val fee: Int,
    val wage: Int,
    val signingBonus: Int,
    val agentCommission: Int,
    val rolePromise: String?,
    val message: String
)
