package com.greendynasty.football.ai.profile.model

import com.greendynasty.football.data.save.entity.ClubAiProfileEntity

/**
 * T18 俱乐部画像主数据类（V0.2 05 §二 + §三）。
 *
 * 聚合：
 * - **性格画像**：[personality]（6 种之一）
 * - **决策偏好**：[ambition] / [financialPower] / [riskTolerance] / [wageStrictness] / [patienceWithManager]
 * - **战术风格倾向**：[tacticalIdentity]（8 种之一）
 * - **球员类型偏好**：[playerArchetype]（5 种之一）
 * - **青训/转会预算分配**：[transferBudgetRatio] / [youthInvestmentRatio]
 * - **长期目标**：[longTermGoal] + [targetSeasons]（3-5 年）
 *
 * 该模型由 [com.greendynasty.football.ai.profile.repository.ClubProfileRepository] 从
 * [ClubAiProfileEntity] 转换得到，供 ViewModel / UI / T13 AI 转会查询使用。
 *
 * 与 [ClubAiProfileEntity] 的区别：
 * - Entity 是 Room 持久化层，字段以基本类型（String/Int）存储枚举名
 * - Model 是业务领域层，字段为枚举类型，便于业务代码引用
 *
 * @property clubId 俱乐部 ID
 * @property personality 俱乐部性格（6 种）
 * @property tacticalIdentity 战术风格倾向（8 种）
 * @property playerArchetype 球员类型偏好（5 种）
 * @property longTermGoal 长期目标（6 种）
 * @property targetSeasons 长期目标赛季数（3-5）
 * @property ambition 野心 0-100
 * @property financialPower 财力 0-100
 * @property youthPreference 青训偏好 0-100
 * @property starPreference 球星偏好 0-100
 * @property resalePreference 转售偏好 0-100
 * @property domesticPreference 本土球员偏好 0-100
 * @property riskTolerance 风险容忍 0-100
 * @property wageStrictness 工资纪律 0-100
 * @property patienceWithManager 主帅耐心 0-100
 * @property transferBudgetRatio 转会预算分配偏好 0-100
 * @property youthInvestmentRatio 青训投入偏好 0-100
 */
data class ClubProfile(
    val clubId: Int,
    val personality: ClubPersonality,
    val tacticalIdentity: TacticalIdentity,
    val playerArchetype: PlayerArchetype,
    val longTermGoal: LongTermGoal,
    val targetSeasons: Int,
    val ambition: Int,
    val financialPower: Int,
    val youthPreference: Int,
    val starPreference: Int,
    val resalePreference: Int,
    val domesticPreference: Int,
    val riskTolerance: Int,
    val wageStrictness: Int,
    val patienceWithManager: Int,
    val transferBudgetRatio: Int,
    val youthInvestmentRatio: Int
) {

    /**
     * 兼容 V0.2 05 §二 5 种 ClubType（由性格 + 偏好推导）。
     *
     * 推导规则：
     * - 野心 > 75 + 球星偏好 > 65 + 财力 > 70 → ELITE_CONTENDER
     * - 青训偏好 > 65 + 转售偏好 < 50 → YOUTH_DEVELOPER
     * - 转售偏好 > 65 + 青训偏好 > 45 → PROFIT_MAKER
     * - 财力 < 40 + 工资纪律 > 60 → RELEGATION_FIGHTER
     * - 财力 > 80 + 球星偏好 > 70 + 工资纪律 < 40 → MONEY_EXPANSION
     * - 其他 → RELEGATION_FIGHTER（默认保守）
     */
    val clubType: ClubType
        get() = when {
            ambition > 75 && starPreference > 65 && financialPower > 70 -> ClubType.ELITE_CONTENDER
            youthPreference > 65 && resalePreference < 50 -> ClubType.YOUTH_DEVELOPER
            resalePreference > 65 && youthPreference > 45 -> ClubType.PROFIT_MAKER
            financialPower < 40 && wageStrictness > 60 -> ClubType.RELEGATION_FIGHTER
            financialPower > 80 && starPreference > 70 && wageStrictness < 40 -> ClubType.MONEY_EXPANSION
            else -> ClubType.RELEGATION_FIGHTER
        }

    companion object {
        /**
         * 由 [ClubAiProfileEntity] 转换为 [ClubProfile]。
         *
         * - 字符串字段安全解析为枚举（未匹配时按 club_id 哈希回退到默认值，保证多样性）
         * - 数值字段 clamp 0-100
         *
         * @param entity 数据库实体
         * @return 画像领域模型
         */
        fun fromEntity(entity: ClubAiProfileEntity): ClubProfile {
            val fallbackPersonality = ClubPersonality.values()
                .getOrElse(entity.clubId.hashCode().let { Math.floorMod(it, ClubPersonality.values().size) }) {
                    ClubPersonality.PRAGMATIC
                }
            val fallbackTactical = TacticalIdentity.values()
                .getOrElse(entity.clubId.hashCode().let { Math.floorMod(it, TacticalIdentity.values().size) }) {
                    TacticalIdentity.COUNTER_ATTACK
                }
            val fallbackArchetype = PlayerArchetype.values()
                .getOrElse(entity.clubId.hashCode().let { Math.floorMod(it, PlayerArchetype.values().size) }) {
                    PlayerArchetype.SQUAD
                }
            val fallbackGoal = LongTermGoal.values()
                .getOrElse(entity.clubId.hashCode().let { Math.floorMod(it, LongTermGoal.values().size) }) {
                    LongTermGoal.TOP_HALF
                }

            return ClubProfile(
                clubId = entity.clubId,
                personality = ClubPersonality.fromString(entity.clubPersonality) ?: fallbackPersonality,
                tacticalIdentity = TacticalIdentity.fromString(entity.tacticalIdentity) ?: fallbackTactical,
                playerArchetype = PlayerArchetype.fromString(entity.playerArchetype) ?: fallbackArchetype,
                longTermGoal = LongTermGoal.fromString(entity.longTermGoal) ?: fallbackGoal,
                targetSeasons = entity.targetSeasons.coerceIn(3, 5),
                ambition = entity.ambition.coerceIn(0, 100),
                financialPower = entity.financialPower.coerceIn(0, 100),
                youthPreference = entity.youthPreference.coerceIn(0, 100),
                starPreference = entity.starPreference.coerceIn(0, 100),
                resalePreference = entity.resalePreference.coerceIn(0, 100),
                domesticPreference = entity.domesticPreference.coerceIn(0, 100),
                riskTolerance = entity.riskTolerance.coerceIn(0, 100),
                wageStrictness = entity.wageStrictness.coerceIn(0, 100),
                patienceWithManager = entity.patienceWithManager.coerceIn(0, 100),
                transferBudgetRatio = entity.transferBudgetRatio.coerceIn(0, 100),
                youthInvestmentRatio = entity.youthInvestmentRatio.coerceIn(0, 100)
            )
        }

        /**
         * 由 [ClubProfile] 转换为 [ClubAiProfileEntity] 用于持久化。
         */
        fun toEntity(profile: ClubProfile): ClubAiProfileEntity = ClubAiProfileEntity(
            clubId = profile.clubId,
            ambition = profile.ambition,
            financialPower = profile.financialPower,
            youthPreference = profile.youthPreference,
            starPreference = profile.starPreference,
            resalePreference = profile.resalePreference,
            domesticPreference = profile.domesticPreference,
            tacticalIdentity = profile.tacticalIdentity.name,
            riskTolerance = profile.riskTolerance,
            wageStrictness = profile.wageStrictness,
            patienceWithManager = profile.patienceWithManager,
            clubPersonality = profile.personality.name,
            longTermGoal = profile.longTermGoal.name,
            targetSeasons = profile.targetSeasons,
            playerArchetype = profile.playerArchetype.name,
            transferBudgetRatio = profile.transferBudgetRatio,
            youthInvestmentRatio = profile.youthInvestmentRatio
        )
    }
}

/**
 * V0.2 05 §三 5 种俱乐部类型（计算得出，影响 target_score 权重调整）。
 *
 * 注意：与 [ClubPersonality] 不同，[ClubType] 是基于多项偏好聚合后的"行为类型"，
 * 主要用于 [com.greendynasty.football.transfer.ai.decision.AiTransferDecisionEngine]
 * 调整 target_score 权重时的分类（V0.2 05 §五）。
 *
 * T18 提供该枚举作为 T13 决策引擎预留接口，T13 当前未使用，T19+ 可消费。
 */
enum class ClubType(val label: String) {
    /** 豪门争冠型 */
    ELITE_CONTENDER("豪门争冠"),

    /** 青训培养型 */
    YOUTH_DEVELOPER("青训培养"),

    /** 黑店型（低买高卖） */
    PROFIT_MAKER("黑店"),

    /** 保级务实型 */
    RELEGATION_FIGHTER("保级务实"),

    /** 金元扩张型 */
    MONEY_EXPANSION("金元扩张")
}
