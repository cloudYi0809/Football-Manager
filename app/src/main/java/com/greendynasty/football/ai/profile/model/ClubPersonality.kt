package com.greendynasty.football.ai.profile.model

/**
 * T18 俱乐部性格枚举（V0.2 05 §二 + §三 6 种性格画像）。
 *
 * 每家 AI 俱乐部有独特性格，影响：
 * - 转会预算分配（transfer_budget_ratio）
 * - 青训投入（youth_investment_ratio）
 * - 战术风格倾向（tactical_identity）
 * - 球员类型偏好（player_archetype）
 * - 风险容忍度（risk_tolerance）
 *
 * 铁律：6 种性格覆盖足坛主流俱乐部形态，可由 [com.greendynasty.football.ai.profile.generator.ClubProfileGenerator]
 * 按声望 / 财力 / 青训等级等基础属性推断，亦可人工指定。
 *
 * @property label 中文标签
 * @property description 性格描述（用于 UI 展示）
 * @property defaultAmbition 默认野心基线（0-100，由 generator 在此基础上 ± 随机偏移）
 * @property defaultRiskTolerance 默认风险容忍度基线
 * @property defaultWageStrictness 默认工资纪律基线
 * @property defaultPatience 默认主帅耐心基线
 */
enum class ClubPersonality(
    val label: String,
    val description: String,
    val defaultAmbition: Int,
    val defaultRiskTolerance: Int,
    val defaultWageStrictness: Int,
    val defaultPatience: Int
) {
    /** 保守型：注重防守，老牌俱乐部（如马竞、国际米兰） */
    CONSERVATIVE(
        label = "保守",
        description = "稳字当头，注重防守结构与青训造血，引援偏老兵即战力",
        defaultAmbition = 55,
        defaultRiskTolerance = 30,
        defaultWageStrictness = 70,
        defaultPatience = 65
    ),

    /** 激进型：进攻至上，野心勃勃（如利物浦、曼城） */
    AGGRESSIVE(
        label = "激进",
        description = "高位压迫，进攻至上，敢于豪赌明星球员，主帅耐心较低",
        defaultAmbition = 85,
        defaultRiskTolerance = 75,
        defaultWageStrictness = 40,
        defaultPatience = 35
    ),

    /** 务实型：以结果为导向（如尤文图斯、本菲卡） */
    PRAGMATIC(
        label = "务实",
        description = "结果导向，注重即战力与战术适配，灵活切换战术风格",
        defaultAmbition = 65,
        defaultRiskTolerance = 50,
        defaultWageStrictness = 60,
        defaultPatience = 55
    ),

    /** 理想主义型：传控艺术足球（如巴萨、阿森纳） */
    IDEALIST(
        label = "理想主义",
        description = "传控艺术足球，重视青训与战术纯粹性，对主帅哲学要求高",
        defaultAmbition = 70,
        defaultRiskTolerance = 55,
        defaultWageStrictness = 50,
        defaultPatience = 60
    ),

    /** 金元型：资金充裕，砸钱买球星（如巴黎、切尔西） */
    MONEY_DRIVEN(
        label = "金元",
        description = "财大气粗，倾向成名球星与商业价值高的引援，工资纪律宽松",
        defaultAmbition = 88,
        defaultRiskTolerance = 80,
        defaultWageStrictness = 30,
        defaultPatience = 30
    ),

    /** 青训派：重视青训与本土球员（如阿贾克斯、毕尔巴鄂） */
    YOUTH_ADVOCATE(
        label = "青训派",
        description = "重视青训造血与本土球员培养，主力来自自家学院，卖人变现能力强",
        defaultAmbition = 60,
        defaultRiskTolerance = 45,
        defaultWageStrictness = 65,
        defaultPatience = 70
    );

    companion object {
        /** 由字符串安全解析（DB 字段 → 枚举），未匹配返回 null。 */
        fun fromString(value: String?): ClubPersonality? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
