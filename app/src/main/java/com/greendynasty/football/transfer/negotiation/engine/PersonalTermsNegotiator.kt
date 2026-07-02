package com.greendynasty.football.transfer.negotiation.engine

import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.transfer.negotiation.config.NegotiationConfig
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.model.ContractTermsEntity
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import kotlin.random.Random

/**
 * T11.4 球员个人条款谈判引擎（V0.1 `09_转会_合同_经纪人系统.md` §五/§六）。
 *
 * 流程：
 * ```
 * 卖方接受 → 球员评估合同条款
 *   ├── 综合意愿 ≥ accept_threshold (0.60) → ACCEPT（球员接受）
 *   ├── 综合意愿 ≤ reject_threshold (0.25) → REJECT（球员拒绝，结束）
 *   └── 中间区间 → COUNTER（球员还价）
 * ```
 *
 * 8 因子加盟意愿公式（V0.1 09 §五）：
 * ```
 * W = club_rep × 0.20
 *   + wage × 0.20
 *   + playing_chance × 0.15
 *   + euro × 0.15
 *   + league × 0.10
 *   + ambition × 0.10
 *   + adaptation × 0.05
 *   + agent_relation × 0.05
 * ```
 *
 * 球员条款评分（V0.1 09 §六）：
 * ```
 * score = wage × 0.40 + years × 0.20 + role × 0.20 + bonus × 0.10 + special × 0.10
 * ```
 *
 * 还价：在玩家出价与期望值之间插值，偏向期望值（含贪婪度修正）
 *
 * @param config 谈判配置
 */
class PersonalTermsNegotiator(
    private val config: NegotiationConfig = NegotiationConfig.DEFAULT
) {

    /**
     * 评估玩家提出的合同条款，决策接受 / 拒绝 / 还价。
     *
     * @param terms 玩家提出的合同条款
     * @param valuation 球员估价（含期望工资）
     * @param player 球员基础信息
     * @param attributes 球员属性（含野心等）
     * @param buyerClub 买方俱乐部存档
     * @param buyerClubReputation 买方俱乐部声望 0-100
     * @param hasChampionsLeague 买方是否有欧冠资格
     * @param hasEuropaLeague 买方是否有欧联资格
     * @param buyerLeagueId 买方所在联赛 ID
     * @param buyerCountry 买方所在国家
     * @param samePositionMainPlayers 同位置主力数（用于上场机会计算）
     * @param agentRelation 经纪人关系 0-1
     * @param currentRound 当前轮次
     * @param random 随机源
     */
    fun evaluateTerms(
        terms: ContractTermsEntity,
        valuation: PlayerValuation,
        player: PlayerEntity,
        attributes: PlayerAttributesEntity?,
        buyerClub: SaveClubStateEntity,
        buyerClubReputation: Int,
        hasChampionsLeague: Boolean,
        hasEuropaLeague: Boolean,
        buyerLeagueId: String?,
        buyerCountry: String?,
        samePositionMainPlayers: Int,
        agentRelation: Double,
        currentRound: Int,
        random: Random = Random.Default
    ): PlayerDecision {
        // 1. 计算 8 因子加盟意愿
        val willingness = calcWillingness(
            terms = terms,
            valuation = valuation,
            player = player,
            attributes = attributes,
            buyerClub = buyerClub,
            buyerClubReputation = buyerClubReputation,
            hasChampionsLeague = hasChampionsLeague,
            hasEuropaLeague = hasEuropaLeague,
            buyerLeagueId = buyerLeagueId,
            buyerCountry = buyerCountry,
            samePositionMainPlayers = samePositionMainPlayers,
            agentRelation = agentRelation
        )

        // 2. 计算球员条款评分（V0.1 09 §六）
        val termsScore = scoreTermsForPlayer(terms, valuation)

        // 综合分数（意愿 × 0.6 + 条款评分 × 0.4）
        val combinedScore = (willingness * 0.6 + termsScore * 0.4).coerceIn(0.0, 1.5)

        // 3. 阈值小幅波动（模拟球员情绪）
        val acceptThreshold = (config.contract.playerAcceptThreshold + random.nextDouble(-0.02, 0.02))
            .coerceIn(0.55, 0.65)
        val rejectThreshold = (config.contract.playerRejectThreshold + random.nextDouble(-0.01, 0.01))
            .coerceIn(0.20, 0.30)

        // 4. 决策
        return when {
            combinedScore >= acceptThreshold -> {
                PlayerDecision.Accept(
                    willingness = willingness,
                    termsScore = termsScore,
                    message = "球员接受了你的合同条款（综合分 ${(combinedScore * 100).toInt()}%）"
                )
            }
            combinedScore <= rejectThreshold -> {
                PlayerDecision.Reject(
                    willingness = willingness,
                    termsScore = termsScore,
                    reason = "球员对合同条款非常不满，拒绝加盟"
                )
            }
            else -> {
                // 还价
                val counterTerms = generateCounterTerms(
                    playerTerms = terms,
                    valuation = valuation,
                    willingness = willingness,
                    currentRound = currentRound,
                    random = random
                )
                PlayerDecision.Counter(
                    counterTerms = counterTerms,
                    willingness = willingness,
                    termsScore = termsScore,
                    message = "球员对部分条款有异议，提出还价（综合分 ${(combinedScore * 100).toInt()}%）"
                )
            }
        }
    }

    /**
     * 8 因子加盟意愿（V0.1 09 §五）。
     */
    fun calcWillingness(
        terms: ContractTermsEntity,
        valuation: PlayerValuation,
        player: PlayerEntity,
        attributes: PlayerAttributesEntity?,
        buyerClub: SaveClubStateEntity,
        buyerClubReputation: Int,
        hasChampionsLeague: Boolean,
        hasEuropaLeague: Boolean,
        buyerLeagueId: String?,
        buyerCountry: String?,
        samePositionMainPlayers: Int,
        agentRelation: Double
    ): Double {
        // 因子 1：俱乐部声望（0-1）
        val clubReputation = (buyerClubReputation / 100.0).coerceIn(0.0, 1.0)

        // 因子 2：工资吸引力（报价 vs 期望工资）
        val expectedWage = valuation.expectedWage.coerceAtLeast(1)
        val wageAttractiveness = (terms.weeklyWage.toDouble() / expectedWage).coerceIn(0.0, 1.5)

        // 因子 3：上场机会（角色承诺 vs 同位置竞争）
        val playingChance = calcPlayingChance(terms.rolePromise, samePositionMainPlayers)

        // 因子 4：欧战资格
        val europeanQualification = when {
            hasChampionsLeague -> 1.0
            hasEuropaLeague -> 0.7
            else -> 0.3
        }

        // 因子 5：联赛吸引力
        val leagueAttractiveness = buyerLeagueId?.let {
            config.playerWillingness.leagueAttractiveness[it]
        } ?: config.playerWillingness.defaultLeagueAttractiveness

        // 因子 6：球员野心（高野心球员更看重声望+欧战）
        val ambition = (attributes?.ambition ?: 50) / 100.0

        // 因子 7：语言/国家适应
        val adaptation = calcAdaptation(player.nationality, buyerCountry)

        // 因子 8：经纪人关系
        val agentRel = agentRelation.coerceIn(0.0, 1.0)

        val willingness = (
            clubReputation * 0.20 +
                wageAttractiveness * 0.20 +
                playingChance * 0.15 +
                europeanQualification * 0.15 +
                leagueAttractiveness * 0.10 +
                ambition * 0.10 +
                adaptation * 0.05 +
                agentRel * 0.05
            )
        return willingness.coerceIn(0.0, 1.0)
    }

    /**
     * 上场机会计算（V0.1 09 §五 因子 3）。
     *
     * 角色承诺越高，上场机会越大；同位置主力多则竞争惩罚。
     */
    private fun calcPlayingChance(rolePromiseName: String, samePositionMainPlayers: Int): Double {
        val promiseScore = when (RolePromise.valueOf(rolePromiseName)) {
            RolePromise.KEY_PLAYER -> 1.0
            RolePromise.STARTER -> 0.9
            RolePromise.ROTATION -> 0.6
            RolePromise.BACKUP -> 0.3
            RolePromise.ACADEMY_DEV -> 0.2
        }
        val competitionPenalty = when (samePositionMainPlayers) {
            in 2..Int.MAX_VALUE -> config.playerWillingness.competitionPenaltyHigh
            1 -> config.playerWillingness.competitionPenaltyLow
            else -> 0.0
        }
        return (promiseScore - competitionPenalty).coerceIn(0.0, 1.0)
    }

    /**
     * 语言/国家适应（V0.1 09 §五 因子 7）。
     *
     * 同国籍满分，同语言区 0.7，其他 0.4。
     */
    private fun calcAdaptation(playerNationality: String?, buyerCountry: String?): Double {
        if (playerNationality.isNullOrBlank() || buyerCountry.isNullOrBlank()) return 0.5
        if (playerNationality.equals(buyerCountry, ignoreCase = true)) return 1.0
        // 同语言区简化映射
        val sameLanguageGroup = isSameLanguageGroup(playerNationality, buyerCountry)
        return if (sameLanguageGroup) 0.7 else 0.4
    }

    /** 同语言区判定（简化版） */
    private fun isSameLanguageGroup(n1: String, n2: String): Boolean {
        val groups = listOf(
            setOf("England", "Scotland", "Ireland", "Wales", "USA", "Australia"),
            setOf("Spain", "Argentina", "Mexico", "Colombia", "Uruguay", "Chile", "Portugal", "Brazil"),
            setOf("France", "Belgium", "Switzerland", "Canada"),
            setOf("Germany", "Austria"),
            setOf("Italy", "San Marino"),
            setOf("Netherlands", "Belgium"),
            setOf("Sweden", "Norway", "Denmark", "Iceland"),
            setOf("China", "Taiwan", "Singapore")
        )
        return groups.any { n1 in it && n2 in it }
    }

    /**
     * 球员条款评分（V0.1 09 §六）。
     *
     * score = wage × 0.40 + years × 0.20 + role × 0.20 + bonus × 0.10 + special × 0.10
     */
    fun scoreTermsForPlayer(
        terms: ContractTermsEntity,
        valuation: PlayerValuation
    ): Double {
        val expectedWage = valuation.expectedWage.coerceAtLeast(1)

        // 工资得分（0-1.5）
        val wageScore = (terms.weeklyWage.toDouble() / expectedWage).coerceIn(0.0, 1.5) * 0.40

        // 年限得分（球员偏好 3-5 年）
        val yearsScore = when (terms.contractYears) {
            in config.contract.preferredYearsRange -> 1.0
            in config.contract.secondaryYearsRange -> 0.7
            else -> 0.4
        } * 0.20

        // 角色承诺得分
        val roleScore = when (RolePromise.valueOf(terms.rolePromise)) {
            RolePromise.KEY_PLAYER -> 1.0
            RolePromise.STARTER -> 0.9
            RolePromise.ROTATION -> 0.6
            RolePromise.BACKUP -> 0.4
            RolePromise.ACADEMY_DEV -> 0.3
        } * 0.20

        // 签字费得分
        val bonusScore = (terms.signingBonus.toDouble() / (expectedWage * 4).coerceAtLeast(1))
            .coerceIn(0.0, 1.0) * 0.10

        // 特殊条款得分
        val specialScore = scoreSpecialTerms(terms) * 0.10

        return (wageScore + yearsScore + roleScore + bonusScore + specialScore).coerceIn(0.0, 1.5)
    }

    /**
     * 特殊条款评分（V0.1 09 §六 7 项特殊条款）。
     *
     * - 解约金：+0.25（对球员有利，可离队）
     * - 降级解约金：+0.15
     * - 欧冠涨薪：+0.15
     * - 年度涨薪：+0.15
     * - 续约选项：+0.10
     * - 回购条款：-0.10（对球员略不利，卖方保留）
     * - 二次转会分成：+0.10（对球员有利）
     */
    private fun scoreSpecialTerms(terms: ContractTermsEntity): Double {
        var score = 0.0
        terms.releaseClause?.let { score += 0.25 }
        terms.relegationReleaseClause?.let { score += 0.15 }
        terms.uclRaisePercent?.let { score += 0.15 }
        terms.annualRaisePercent?.let { score += 0.15 }
        if (terms.contractExtensionOption) score += 0.10
        terms.buybackClause?.let { score -= 0.10 }
        terms.sellOnPercent?.let { score += 0.10 }
        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 生成球员还价条款（V0.1 09 §六 还价算法）。
     *
     * 还价工资 = (玩家出价 + 期望工资 × (1 + 贪婪修正)) / 2
     * 还价签字费 = 玩家出价 × 1.20
     * 还价佣金 = 玩家出价 × 1.15
     *
     * @param playerTerms 玩家本轮提出的条款
     * @param valuation 球员估价
     * @param willingness 当前加盟意愿（越低还价越高）
     * @param currentRound 当前轮次
     * @param random 随机源
     */
    fun generateCounterTerms(
        playerTerms: ContractTermsEntity,
        valuation: PlayerValuation,
        willingness: Double,
        currentRound: Int,
        random: Random = Random.Default
    ): ContractTermsEntity {
        val expectedWage = valuation.expectedWage

        // 还价工资：在玩家出价和期望工资之间，偏向期望工资上浮
        // 意愿越低，越倾向期望工资上限；轮次越往后，越让步
        val roundConcession = (currentRound - 1) * 0.05
        val willingnessFactor = (1.0 - willingness).coerceIn(0.0, 0.5) // 意愿低 → 还价高
        val greedAdjustment = 0.10 + willingnessFactor - roundConcession

        val targetWage = (expectedWage * (1 + greedAdjustment)).toInt()
        val counterWage = ((playerTerms.weeklyWage + targetWage) / 2)
            .toInt()
            .coerceAtLeast(playerTerms.weeklyWage) // 还价必须 ≥ 玩家出价

        // 还价签字费（上浮 20%）
        val counterBonus = (playerTerms.signingBonus * config.contract.counterBonusMultiplier).toInt()

        // 还价佣金（上浮 15%）
        val counterCommission = (playerTerms.agentCommission * config.contract.counterCommissionMultiplier).toInt()

        // 加入小幅随机扰动
        val jitter = random.nextDouble(-0.03, 0.03)
        val finalWage = (counterWage * (1 + jitter)).toInt().coerceAtLeast(playerTerms.weeklyWage)

        return playerTerms.copy(
            weeklyWage = finalWage,
            signingBonus = counterBonus,
            agentCommission = counterCommission
        )
    }
}

/**
 * 球员决策（密封类，V0.1 09 §五/§六）。
 */
sealed class PlayerDecision {
    /** 球员接受合同条款 */
    data class Accept(
        val willingness: Double,
        val termsScore: Double,
        val message: String
    ) : PlayerDecision()

    /** 球员拒绝（终止谈判） */
    data class Reject(
        val willingness: Double,
        val termsScore: Double,
        val reason: String
    ) : PlayerDecision()

    /** 球员还价 */
    data class Counter(
        val counterTerms: ContractTermsEntity,
        val willingness: Double,
        val termsScore: Double,
        val message: String
    ) : PlayerDecision()
}
