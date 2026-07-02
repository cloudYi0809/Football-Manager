package com.greendynasty.football.transfer.ai.offer

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.transfer.ai.config.BasicAiConfig
import com.greendynasty.football.transfer.ai.model.AiOffer
import com.greendynasty.football.transfer.ai.model.ClubFinancialState
import com.greendynasty.football.transfer.ai.model.PlayerCandidate
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValueEstimator
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.search.EconomyEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * T13.3 AI 报价生成器（复用 T11 [PlayerValueEstimator]）。
 *
 * 基础版报价策略（V0.2 §六 基础版，统一 80% 市值）：
 * ```
 * max_offer = psychological_price × initial_offer_ratio (0.80)
 * ```
 *
 * 流程：
 * 1. 调用 T11 [PlayerValueEstimator.estimate] 获取卖方心理价位 + 接受区间 + 期望工资
 * 2. 以心理价位 × 80% 作为初始报价
 * 3. 加入 ±5% 随机扰动（增加 AI 不确定性）
 * 4. 受买方转会预算限制（不超过 transferBudgetRemaining）
 *
 * T18 完整版将扩展为 4 种报价策略（按俱乐部画像区分）。
 *
 * @param databaseManager 三库管理入口
 * @param valueEstimator T11 球员估价器
 * @param economyEstimator T10 经济估算器（回退计算）
 * @param config AI 转会配置
 */
class AiOfferGenerator(
    private val databaseManager: DatabaseManager,
    private val valueEstimator: PlayerValueEstimator = PlayerValueEstimator(),
    private val economyEstimator: EconomyEstimator = EconomyEstimator(),
    private val config: BasicAiConfig = BasicAiConfig.DEFAULT
) {

    /**
     * 为候选球员生成 AI 报价。
     *
     * @param saveId 存档 ID
     * @param candidate 候选球员
     * @param sellerClub 卖方俱乐部存档状态（null 表示自由球员）
     * @param financial 买方财政状态
     * @param currentDate 当前游戏日期
     * @param currentYear 当前年份
     * @param random 随机源（便于测试注入）
     * @return AI 报价（含心理价位、转会费、周薪、合同年限）
     */
    suspend fun generateOffer(
        saveId: Int,
        candidate: PlayerCandidate,
        sellerClub: SaveClubStateEntity?,
        financial: ClubFinancialState,
        currentDate: LocalDate,
        currentYear: Int,
        random: Random = Random.Default
    ): AiOffer = withContext(Dispatchers.IO) {
        // 1. 获取球员估价（T11 PlayerValueEstimator）
        val valuation = estimateValuation(saveId, candidate, sellerClub, currentDate, currentYear)

        // 2. 基础版报价 = 心理价位 × 80%
        val baseOffer = (valuation.psychologicalPrice * config.offer.initialOfferRatio).toInt()

        // 3. 加入 ±5% 随机扰动（AI 不确定性）
        val jitter = random.nextDouble(
            -config.offer.offerJitter,
            config.offer.offerJitter
        )
        var offerFee = (baseOffer * (1.0 + jitter)).toInt()

        // 4. 预算限制：不超过剩余转会预算
        if (offerFee > financial.transferBudgetRemaining) {
            offerFee = financial.transferBudgetRemaining
        }

        // 5. 确保非负
        offerFee = offerFee.coerceAtLeast(0)

        AiOffer(
            playerId = candidate.playerId,
            fee = offerFee,
            wage = valuation.expectedWage,
            contractYears = 4, // 基础版固定 4 年合同
            psychologicalPrice = valuation.psychologicalPrice,
            marketValue = valuation.baseValue
        )
    }

    /**
     * 获取候选球员的完整估价（T11 PlayerValueEstimator）。
     *
     * 需要从 history.db 和 save.db 聚合实体数据。
     *
     * @param saveId 存档 ID
     * @param candidate 候选球员
     * @param sellerClub 卖方俱乐部
     * @param currentDate 当前游戏日期
     * @param currentYear 当前年份
     * @return T11 球员估价结果
     */
    suspend fun estimateValuation(
        saveId: Int,
        candidate: PlayerCandidate,
        sellerClub: SaveClubStateEntity?,
        currentDate: LocalDate,
        currentYear: Int
    ): PlayerValuation = withContext(Dispatchers.IO) {
        val player = databaseManager.historyPlayerDao().getPlayer(candidate.playerId)
        val attributes = databaseManager.historyPlayerDao().getLatestAttributes(candidate.playerId)
        val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, candidate.playerId)

        valueEstimator.estimate(
            player = player ?: createFallbackPlayer(candidate),
            attributes = attributes,
            state = state,
            sellerClub = sellerClub,
            currentDate = currentDate,
            currentYear = currentYear
        )
    }

    /**
     * 当 history.db 中找不到球员时的回退构造（极端兜底，正常不会触发）。
     */
    private fun createFallbackPlayer(
        candidate: PlayerCandidate
    ): com.greendynasty.football.data.history.entity.PlayerEntity {
        return com.greendynasty.football.data.history.entity.PlayerEntity(
            playerId = candidate.playerId,
            sourceId = null,
            realName = candidate.playerName,
            displayName = candidate.playerName,
            birthDate = null,
            nationality = candidate.nationality,
            secondNationality = null,
            height = null,
            weight = null,
            preferredFoot = null,
            primaryPosition = candidate.position,
            secondaryPositions = null,
            personality = null,
            portraitPath = null,
            createdAt = null,
            updatedAt = null
        )
    }
}
