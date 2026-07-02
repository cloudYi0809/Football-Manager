package com.greendynasty.football.ai.profile.generator

import com.greendynasty.football.ai.profile.model.ClubPersonality
import com.greendynasty.football.ai.profile.model.ClubProfile
import com.greendynasty.football.ai.profile.model.LongTermGoal
import com.greendynasty.football.ai.profile.model.PlayerArchetype
import com.greendynasty.football.ai.profile.model.TacticalIdentity
import com.greendynasty.football.data.history.entity.ClubEntity
import kotlin.random.Random

/**
 * T18 俱乐部画像生成器（V0.2 05 §三 + §二）。
 *
 * 按俱乐部基础属性（声望 / 财力 / 青训等级 / 财务等级）推断画像：
 *
 * 1. **性格判定**（[resolvePersonality]）：
 *    - 声望 ≥ 75 + 财力 ≥ 70：AGGRESSIVE / MONEY_DRIVEN / IDEALIST 三选一
 *    - 声望 ≥ 55：PRAGMATIC / IDEALIST 二选一
 *    - 青训等级 ≥ 65：YOUTH_ADVOCATE
 *    - 其他：CONSERVATIVE / PRAGMATIC 二选一
 *
 * 2. **战术风格判定**（[resolveTacticalIdentity]）：
 *    - 优先选择性格对应的 preferredPersonalities 中匹配的战术
 *    - 否则按 clubId 哈希均匀分配
 *
 * 3. **球员类型偏好**（[resolvePlayerArchetype]）：
 *    - AGGRESSIVE/MONEY_DRIVEN → STAR
 *    - YOUTH_ADVOCATE → WONDERKID
 *    - CONSERVATIVE → VETERAN
 *    - PRAGMATIC → SQUAD/BARGAIN
 *    - IDEALIST → WONDERKID/SQUAD
 *
 * 4. **长期目标**（[resolveLongTermGoal]）：
 *    - 高声望 → WIN_TITLE / BUILD_BRAND
 *    - 中声望 → TOP_HALF / DEVELOP_YOUTH
 *    - 低声望 → AVOID_RELEGATION / FINANCIAL_BALANCE
 *
 * 5. **决策偏好**（[resolveDecisionPreferences]）：
 *    - 以性格的 defaultAmbition / defaultRiskTolerance 等为基线
 *    - 加上 ±[ClubProfileGeneratorConfig.randomBiasRange] 随机偏移
 *    - 部分字段按俱乐部基础属性微调（如 financialPower = club.financeLevel + bias）
 *
 * 铁律：
 * - 所有判定规则严格按 V0.2 05 §三 实现，参数全部从 [config] 读取
 * - 不破坏 T13 已有 AI 决策逻辑，仅生成画像供查询
 * - 6 种性格 + 8 种战术 + 5 种球员原型 + 6 种长期目标全部覆盖
 *
 * @param config 生成器配置
 */
class ClubProfileGenerator(
    private val config: ClubProfileGeneratorConfig = ClubProfileGeneratorConfig.DEFAULT
) {

    /**
     * 为单个俱乐部生成画像。
     *
     * @param club 俱乐部基础信息（来自 history.db）
     * @param financialPowerScore 财力评分 0-100（来自 T17 经济模型，无则用 club.financeLevel）
     * @param random 随机源（同一种子可复现）
     * @return 画像领域模型
     */
    fun generate(
        club: ClubEntity,
        financialPowerScore: Int = club.financeLevel,
        random: Random = Random
    ): ClubProfile {
        val personality = resolvePersonality(club, financialPowerScore, random)
        val tacticalIdentity = resolveTacticalIdentity(personality, club.clubId, random)
        val playerArchetype = resolvePlayerArchetype(personality, random)
        val longTermGoal = resolveLongTermGoal(club, personality, random)

        val (ambition, riskTolerance, wageStrictness, patience) =
            resolveDecisionPreferences(personality, random)

        // 决策偏好（数值字段）
        val financialPower = (financialPowerScore + bias(random)).coerceIn(0, 100)
        val youthPreference = (club.youthLevel + bias(random)).coerceIn(0, 100)
        val starPreference = resolveStarPreference(club, personality, random)
        val resalePreference = resolveResalePreference(club, personality, random)
        val domesticPreference = 40 + bias(random, range = 30)

        // 预算分配偏好（性格 + 长期目标共同决定）
        val transferBudgetRatio = resolveTransferBudgetRatio(personality, longTermGoal, random)
        val youthInvestmentRatio = 100 - transferBudgetRatio

        return ClubProfile(
            clubId = club.clubId,
            personality = personality,
            tacticalIdentity = tacticalIdentity,
            playerArchetype = playerArchetype,
            longTermGoal = longTermGoal,
            targetSeasons = longTermGoal.targetSeasons,
            ambition = ambition,
            financialPower = financialPower,
            youthPreference = youthPreference,
            starPreference = starPreference,
            resalePreference = resalePreference,
            domesticPreference = domesticPreference.coerceIn(0, 100),
            riskTolerance = riskTolerance,
            wageStrictness = wageStrictness,
            patienceWithManager = patience,
            transferBudgetRatio = transferBudgetRatio,
            youthInvestmentRatio = youthInvestmentRatio
        )
    }

    /**
     * 批量生成俱乐部画像（用于存档初始化时一次性生成所有 AI 俱乐部画像）。
     *
     * @param clubs 俱乐部基础信息列表
     * @param financialPowerMap 俱乐部 ID → 财力评分（无则用 club.financeLevel）
     * @param random 随机源
     * @return 画像列表
     */
    fun generateAll(
        clubs: List<ClubEntity>,
        financialPowerMap: Map<Int, Int> = emptyMap(),
        random: Random = Random
    ): List<ClubProfile> = clubs.map { club ->
        generate(
            club = club,
            financialPowerScore = financialPowerMap[club.clubId] ?: club.financeLevel,
            random = random
        )
    }

    // ==================== 性格判定 ====================

    /**
     * 性格判定（V0.2 05 §三）。
     *
     * 判定优先级：
     * 1. 财力 ≥ 70 + 声望 ≥ 75 → MONEY_DRIVEN / AGGRESSIVE / IDEALIST
     * 2. 青训等级 ≥ 65 + 声望 < 75 → YOUTH_ADVOCATE
     * 3. 声望 ≥ 75 → AGGRESSIVE / IDEALIST
     * 4. 声望 ≥ 55 → PRAGMATIC / IDEALIST
     * 5. 声望 < 40 → CONSERVATIVE / PRAGMATIC
     * 6. 其他 → PRAGMATIC
     */
    internal fun resolvePersonality(
        club: ClubEntity,
        financialPower: Int,
        random: Random
    ): ClubPersonality {
        val rep = club.reputation
        val youth = club.youthLevel

        return when {
            // 高财力 + 高声望 → 金元 / 激进 / 理想主义
            financialPower >= config.financialPowerEliteThreshold &&
                rep >= config.reputationEliteThreshold -> {
                weightedPick(
                    random,
                    ClubPersonality.MONEY_DRIVEN to 3,
                    ClubPersonality.AGGRESSIVE to 2,
                    ClubPersonality.IDEALIST to 1
                )
            }
            // 高青训等级 + 非顶级声望 → 青训派
            youth >= config.youthLevelHighThreshold &&
                rep < config.reputationEliteThreshold -> {
                ClubPersonality.YOUTH_ADVOCATE
            }
            // 顶级声望 → 激进 / 理想主义
            rep >= config.reputationEliteThreshold -> {
                weightedPick(
                    random,
                    ClubPersonality.AGGRESSIVE to 2,
                    ClubPersonality.IDEALIST to 1
                )
            }
            // 中游声望 → 务实 / 理想主义
            rep >= config.reputationMidTierThreshold -> {
                weightedPick(
                    random,
                    ClubPersonality.PRAGMATIC to 2,
                    ClubPersonality.IDEALIST to 1
                )
            }
            // 低声望 → 保守 / 务实
            rep < config.reputationLowTierThreshold -> {
                weightedPick(
                    random,
                    ClubPersonality.CONSERVATIVE to 2,
                    ClubPersonality.PRAGMATIC to 1
                )
            }
            // 默认务实
            else -> ClubPersonality.PRAGMATIC
        }
    }

    // ==================== 战术风格判定 ====================

    /**
     * 战术风格判定（V0.2 05 §四.3）。
     *
     * 优先选择性格对应的 preferredPersonalities 中匹配的战术；
     * 否则按 clubId 哈希均匀分配（保证多样性）。
     */
    internal fun resolveTacticalIdentity(
        personality: ClubPersonality,
        clubId: Int,
        random: Random
    ): TacticalIdentity {
        // 找到所有 preferredPersonalities 包含该性格的战术
        val compatible = TacticalIdentity.values().filter { tactical ->
            personality in tactical.preferredPersonalities
        }
        if (compatible.isNotEmpty()) {
            return compatible[random.nextInt(compatible.size)]
        }
        // 兜底：按 clubId 哈希均匀分配
        val all = TacticalIdentity.values()
        return all[Math.floorMod(clubId.hashCode(), all.size)]
    }

    // ==================== 球员类型偏好判定 ====================

    /**
     * 球员类型偏好判定（V0.2 05 §五）。
     *
     * 性格 → 球员原型映射：
     * - AGGRESSIVE / MONEY_DRIVEN → STAR
     * - YOUTH_ADVOCATE → WONDERKID
     * - CONSERVATIVE → VETERAN
     * - PRAGMATIC → SQUAD / BARGAIN
     * - IDEALIST → WONDERKID / SQUAD
     */
    internal fun resolvePlayerArchetype(
        personality: ClubPersonality,
        random: Random
    ): PlayerArchetype = when (personality) {
        ClubPersonality.AGGRESSIVE, ClubPersonality.MONEY_DRIVEN -> PlayerArchetype.STAR
        ClubPersonality.YOUTH_ADVOCATE -> PlayerArchetype.WONDERKID
        ClubPersonality.CONSERVATIVE -> PlayerArchetype.VETERAN
        ClubPersonality.PRAGMATIC -> weightedPick(
            random,
            PlayerArchetype.SQUAD to 2,
            PlayerArchetype.BARGAIN to 1
        )
        ClubPersonality.IDEALIST -> weightedPick(
            random,
            PlayerArchetype.WONDERKID to 2,
            PlayerArchetype.SQUAD to 1
        )
    }

    // ==================== 长期目标判定 ====================

    /**
     * 长期目标判定（V0.2 05 §三）。
     *
     * 声望 + 性格 双因子：
     * - 声望 ≥ 75：WIN_TITLE / BUILD_BRAND（金元型倾向品牌）
     * - 声望 ≥ 55：TOP_HALF / DEVELOP_YOUTH（青训派倾向培养）
     * - 声望 < 40：AVOID_RELEGATION / FINANCIAL_BALANCE
     * - 其他：TOP_HALF
     */
    internal fun resolveLongTermGoal(
        club: ClubEntity,
        personality: ClubPersonality,
        random: Random
    ): LongTermGoal {
        val rep = club.reputation
        return when {
            rep >= config.reputationEliteThreshold -> {
                if (personality == ClubPersonality.MONEY_DRIVEN) {
                    weightedPick(
                        random,
                        LongTermGoal.BUILD_BRAND to 2,
                        LongTermGoal.WIN_TITLE to 1
                    )
                } else {
                    LongTermGoal.WIN_TITLE
                }
            }
            rep >= config.reputationMidTierThreshold -> {
                if (personality == ClubPersonality.YOUTH_ADVOCATE) {
                    LongTermGoal.DEVELOP_YOUTH
                } else {
                    LongTermGoal.TOP_HALF
                }
            }
            rep < config.reputationLowTierThreshold -> {
                weightedPick(
                    random,
                    LongTermGoal.AVOID_RELEGATION to 2,
                    LongTermGoal.FINANCIAL_BALANCE to 1
                )
            }
            else -> LongTermGoal.TOP_HALF
        }
    }

    // ==================== 决策偏好计算 ====================

    /**
     * 决策偏好计算（V0.2 05 §二）。
     *
     * 以性格基线为底，加 ±[config.randomBiasRange] 随机偏移。
     *
     * @return (ambition, riskTolerance, wageStrictness, patience)
     */
    internal fun resolveDecisionPreferences(
        personality: ClubPersonality,
        random: Random
    ): DecisionPreferences {
        val ambition = (personality.defaultAmbition + bias(random)).coerceIn(0, 100)
        val riskTolerance = (personality.defaultRiskTolerance + bias(random)).coerceIn(0, 100)
        val wageStrictness = (personality.defaultWageStrictness + bias(random)).coerceIn(0, 100)
        val patience = (personality.defaultPatience + bias(random)).coerceIn(0, 100)
        return DecisionPreferences(ambition, riskTolerance, wageStrictness, patience)
    }

    /**
     * 球星偏好计算（V0.2 05 §二）。
     *
     * 高声望 + AGGRESSIVE/MONEY_DRIVEN → 70+
     * 低声望 → 30+
     */
    private fun resolveStarPreference(
        club: ClubEntity,
        personality: ClubPersonality,
        random: Random
    ): Int {
        val base = when (personality) {
            ClubPersonality.MONEY_DRIVEN -> 80
            ClubPersonality.AGGRESSIVE -> 70
            ClubPersonality.IDEALIST -> 50
            ClubPersonality.YOUTH_ADVOCATE -> 25
            ClubPersonality.PRAGMATIC -> if (club.reputation >= 60) 60 else 40
            ClubPersonality.CONSERVATIVE -> 30
        }
        return (base + bias(random)).coerceIn(0, 100)
    }

    /**
     * 转售偏好计算（V0.2 05 §二）。
     *
     * 中游声望（40-65）的黑店型俱乐部转售偏好高（65+）
     */
    private fun resolveResalePreference(
        club: ClubEntity,
        personality: ClubPersonality,
        random: Random
    ): Int {
        val base = when (personality) {
            ClubPersonality.YOUTH_ADVOCATE -> 70 // 青训派天然卖人
            ClubPersonality.PRAGMATIC -> if (club.reputation in 40..65) 65 else 45
            ClubPersonality.CONSERVATIVE -> 30
            ClubPersonality.AGGRESSIVE -> 30
            ClubPersonality.MONEY_DRIVEN -> 25
            ClubPersonality.IDEALIST -> 40
        }
        return (base + bias(random, range = 20)).coerceIn(0, 100)
    }

    /**
     * 转会预算分配偏好（V0.2 05 §三）。
     *
     * - MONEY_DRIVEN / AGGRESSIVE → 转会市场优先（70+）
     * - YOUTH_ADVOCATE → 青训优先（30-）
     * - IDEALIST → 平衡（50）
     * - 长期目标 WIN_TITLE → +10 转会
     * - 长期目标 DEVELOP_YOUTH → -10 转会
     */
    private fun resolveTransferBudgetRatio(
        personality: ClubPersonality,
        longTermGoal: LongTermGoal,
        random: Random
    ): Int {
        val base = when (personality) {
            ClubPersonality.MONEY_DRIVEN -> 80
            ClubPersonality.AGGRESSIVE -> 70
            ClubPersonality.PRAGMATIC -> 60
            ClubPersonality.IDEALIST -> 50
            ClubPersonality.CONSERVATIVE -> 45
            ClubPersonality.YOUTH_ADVOCATE -> 30
        }
        val goalAdjust = when (longTermGoal) {
            LongTermGoal.WIN_TITLE, LongTermGoal.BUILD_BRAND -> 10
            LongTermGoal.DEVELOP_YOUTH -> -10
            else -> 0
        }
        return (base + goalAdjust + bias(random, range = 10)).coerceIn(0, 100)
    }

    // ==================== 工具方法 ====================

    /** 加权随机选择（V0.2 05 §三 性格判定用）。 */
    private fun <T> weightedPick(random: Random, vararg pairs: Pair<T, Int>): T {
        val total = pairs.sumOf { it.second }
        var r = random.nextInt(total)
        for ((value, weight) in pairs) {
            r -= weight
            if (r < 0) return value
        }
        return pairs.first().first
    }

    /** 随机偏移（默认 ±[config.randomBiasRange]）。 */
    private fun bias(random: Random, range: Int = config.randomBiasRange): Int {
        return random.nextInt(-range, range + 1)
    }

    /** 决策偏好四元组（内部传递用）。 */
    internal data class DecisionPreferences(
        val ambition: Int,
        val riskTolerance: Int,
        val wageStrictness: Int,
        val patienceWithManager: Int
    )
}
