package com.greendynasty.football.transfer.negotiation.estimator

import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.negotiation.config.NegotiationConfig
import com.greendynasty.football.transfer.negotiation.model.PlayerImportance
import com.greendynasty.football.transfer.search.EconomyEstimator
import java.time.LocalDate
import java.time.Period

/**
 * T11.1 球员估价计算器（V0.2 `07_经济通胀_身价_工资模型.md` + V0.1 `09_转会_合同_经纪人系统.md` §四.1）。
 *
 * 估价公式（4 因子修正）：
 * ```
 * psychological_price = base_value × importance_multiplier × contract_multiplier × potential_multiplier
 * ```
 *
 * 其中：
 * - `base_value`：T10 [EconomyEstimator.estimateMarketValue] 计算的市场价值（V0.2 §五）
 * - `importance_multiplier`：5 档球员重要性系数（KEY=1.50 / STARTER=1.20 / ROTATION=1.00 / BACKUP=0.80 / LISTED=0.60）
 * - `contract_multiplier`：合同剩余系数（剩 6 月 0.5 / 12 月 0.75 / 24 月 1.0 / 36 月 1.15 / 更长 1.25）
 * - `potential_multiplier`：潜力系数（U19 高潜 1.40 / U22 中潜 1.20 / U24 低潜 1.10 / 默认 1.00）
 *
 * 卖方接受区间 = [psychological_price × 0.6, psychological_price × 1.5]
 *
 * @param config 谈判配置
 * @param economyEstimator T10 经济估算器（复用 base_value 计算）
 */
class PlayerValueEstimator(
    private val config: NegotiationConfig = NegotiationConfig.DEFAULT,
    private val economyEstimator: EconomyEstimator = EconomyEstimator()
) {

    /**
     * 计算球员完整估价（含心理价位 + 接受区间）。
     *
     * @param player 球员基础信息（history.db）
     * @param attributes 球员最新赛季属性（含 CA/PA/野心/伤病倾向等）
     * @param state 球员存档状态（含合同/士气/身价等）
     * @param sellerClub 卖方俱乐部存档状态（用于重要性判定）
     * @param currentDate 当前游戏日期
     * @param currentYear 当前年份（经济指数）
     * @return 估价结果
     */
    fun estimate(
        player: PlayerEntity,
        attributes: PlayerAttributesEntity?,
        state: SavePlayerStateEntity?,
        sellerClub: SaveClubStateEntity?,
        currentDate: LocalDate,
        currentYear: Int
    ): PlayerValuation {
        val ca = state?.currentCa ?: attributes?.ca ?: 50
        val pa = state?.currentPa ?: attributes?.pa ?: ca
        val age = computeAge(player.birthDate, currentDate)
        val position = player.primaryPosition ?: "CM"
        val contractUntil = state?.contractUntil

        // 1. base_value（市场价值，T10 EconomyEstimator）
        val baseValue = if (state?.marketValue != null && state.marketValue > 0) {
            state.marketValue
        } else {
            economyEstimator.estimateMarketValue(ca, pa, age, position, contractUntil, currentYear)
        }

        // 2. 重要性系数
        val importance = resolveImportance(state, sellerClub)
        val importanceMultiplier = config.psychologicalPrice.importancePriceMultiplier[importance.priceMultiplierKey]
            ?: 1.0

        // 3. 合同系数
        val contractMultiplier = calcContractMultiplier(contractUntil, currentDate)

        // 4. 潜力系数
        val potentialMultiplier = calcPotentialMultiplier(age, pa, ca)

        // 心理价位
        val psychologicalPrice = (baseValue * importanceMultiplier * contractMultiplier * potentialMultiplier)
            .toInt()
            .coerceAtLeast(config.psychologicalPrice.minTransferFee)

        // 接受区间：[心理价位 × 0.6, 心理价位 × 1.5]
        val acceptRange = PriceRange(
            lower = (psychologicalPrice * 0.6).toInt(),
            upper = (psychologicalPrice * 1.5).toInt()
        )

        // 期望工资（T10 EconomyEstimator）
        val expectedWage = economyEstimator.estimateExpectedWage(
            ca = ca,
            position = position,
            squadRole = state?.squadRole,
            currentYear = currentYear
        )

        return PlayerValuation(
            baseValue = baseValue,
            psychologicalPrice = psychologicalPrice,
            acceptRange = acceptRange,
            expectedWage = expectedWage,
            importance = importance,
            importanceMultiplier = importanceMultiplier,
            contractMultiplier = contractMultiplier,
            potentialMultiplier = potentialMultiplier,
            age = age,
            ca = ca,
            pa = pa
        )
    }

    /**
     * 仅计算心理价位（轻量版，用于还价生成时重算）。
     */
    fun calcPsychologicalPrice(
        baseValue: Int,
        importance: PlayerImportance,
        contractUntil: String?,
        age: Int,
        pa: Int,
        ca: Int,
        currentDate: LocalDate
    ): Int {
        val importanceMultiplier = config.psychologicalPrice.importancePriceMultiplier[importance.priceMultiplierKey] ?: 1.0
        val contractMultiplier = calcContractMultiplier(contractUntil, currentDate)
        val potentialMultiplier = calcPotentialMultiplier(age, pa, ca)
        return (baseValue * importanceMultiplier * contractMultiplier * potentialMultiplier)
            .toInt()
            .coerceAtLeast(config.psychologicalPrice.minTransferFee)
    }

    /**
     * 解析球员重要性（5 档）。
     *
     * V0.1 09 §四.2：
     * - 挂牌球员优先 → LISTED
     * - 队内角色：key_player → KEY / starter → STARTER / rotation → ROTATION / backup,prospect → BACKUP
     */
    fun resolveImportance(
        state: SavePlayerStateEntity?,
        sellerClub: SaveClubStateEntity?
    ): PlayerImportance {
        // 挂牌球员优先（V1 阶段无 isListedForTransfer 字段，使用 squad_role = "listed" 约定）
        val role = state?.squadRole?.lowercase()
        if (role == "listed" || role == "transfer_listed") return PlayerImportance.LISTED
        return when (role) {
            "key_player", "key", "core" -> PlayerImportance.KEY
            "starter", "first_team" -> PlayerImportance.STARTER
            "rotation" -> PlayerImportance.ROTATION
            "backup", "prospect", "youth", "substitute" -> PlayerImportance.BACKUP
            null -> PlayerImportance.ROTATION
            else -> PlayerImportance.ROTATION
        }
    }

    /**
     * 合同系数（V0.1 09 §四.1，与身价修正方向一致但卖方视角更激进）。
     *
     * | 合同剩余 | 系数 |
     * |---|---|
     * | ≤ 6 个月 | 0.50（半年到期大幅降价） |
     * | ≤ 12 个月 | 0.75 |
     * | ≤ 24 个月 | 1.00 |
     * | ≤ 36 个月 | 1.15 |
     * | 更长 | 1.25 |
     */
    fun calcContractMultiplier(contractUntil: String?, currentDate: LocalDate): Double {
        if (contractUntil.isNullOrBlank()) return 0.50 // 自由球员
        val months = monthsUntilContractEnd(contractUntil, currentDate)
        return when {
            months <= 6 -> 0.50
            months <= 12 -> 0.75
            months <= 24 -> 1.00
            months <= 36 -> 1.15
            else -> 1.25
        }
    }

    /**
     * 潜力系数（V0.1 09 §四.1）。
     *
     * | 年龄 | PA-CA 差 | 系数 |
     * |---|---|---|
     * | U19 | > 20 | 1.40 |
     * | U22 | > 15 | 1.20 |
     * | U24 | > 10 | 1.10 |
     * | 其他 | - | 1.00 |
     */
    fun calcPotentialMultiplier(age: Int, pa: Int, ca: Int): Double {
        val gap = (pa - ca).coerceAtLeast(0)
        val p = config.psychologicalPrice
        return when {
            age <= 19 && gap > p.potentialGapHigh -> p.potentialU19HighGap
            age <= 22 && gap > p.potentialGapMid -> p.potentialU22MidGap
            age <= 24 && gap > p.potentialGapLow -> p.potentialU24LowGap
            else -> p.potentialDefault
        }
    }

    /**
     * 计算合同剩余月数。
     */
    fun monthsUntilContractEnd(contractUntil: String?, currentDate: LocalDate): Int {
        if (contractUntil.isNullOrBlank()) return 0
        return runCatching {
            val end = LocalDate.parse(contractUntil.take(10))
            Period.between(currentDate, end).let {
                it.years * 12 + it.months
            }.coerceAtLeast(0)
        }.getOrElse { 0 }
    }

    /** 计算年龄 */
    fun computeAge(birthDate: String?, currentDate: LocalDate): Int {
        if (birthDate.isNullOrBlank()) return 18
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrElse { 18 }
    }
}

/**
 * 球员估价结果。
 *
 * @property baseValue 市场价值（T10）
 * @property psychologicalPrice 心理价位（卖方愿接受的基准价）
 * @property acceptRange 卖方接受区间
 * @property expectedWage 期望周薪（T10）
 * @property importance 5 档球员重要性
 * @property importanceMultiplier 重要性系数
 * @property contractMultiplier 合同系数
 * @property potentialMultiplier 潜力系数
 * @property age 年龄
 * @property ca 当前能力值
 * @property pa 潜力值
 */
data class PlayerValuation(
    val baseValue: Int,
    val psychologicalPrice: Int,
    val acceptRange: PriceRange,
    val expectedWage: Int,
    val importance: PlayerImportance,
    val importanceMultiplier: Double,
    val contractMultiplier: Double,
    val potentialMultiplier: Double,
    val age: Int,
    val ca: Int,
    val pa: Int
)

/**
 * 价格区间。
 */
data class PriceRange(
    val lower: Int,
    val upper: Int
) {
    /** 是否包含某价格 */
    fun contains(price: Int): Boolean = price in lower..upper

    /** 价格相对下限的比率 */
    fun ratioToLower(price: Int): Double =
        if (lower > 0) price.toDouble() / lower else 0.0
}
