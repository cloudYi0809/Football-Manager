package com.greendynasty.football.ai.profile.model

/**
 * T18 俱乐部长期目标枚举（V0.2 05 §三 长期战略）。
 *
 * 每家俱乐部有 3-5 年长期目标，影响：
 * - 转会预算分配（夺冠型投入更多）
 * - 青训投入（培养新星型投入更多）
 * - 战术风格（保级型更保守）
 * - 主帅耐心（争冠型耐心更低）
 *
 * @property label 中文标签
 * @property description 目标描述
 * @property targetSeasons 默认目标赛季数（3-5 年）
 * @property minReputation 适配的最低声望（generator 推断用）
 * @property compatiblePersonalities 倾向搭配的俱乐部性格
 */
enum class LongTermGoal(
    val label: String,
    val description: String,
    val targetSeasons: Int,
    val minReputation: Int,
    val compatiblePersonalities: List<ClubPersonality>
) {
    /** 夺冠：联赛冠军 + 欧冠资格 */
    WIN_TITLE(
        label = "夺冠",
        description = "3-5 年内夺得联赛冠军并跻身欧冠淘汰赛",
        targetSeasons = 4,
        minReputation = 75,
        compatiblePersonalities = listOf(ClubPersonality.AGGRESSIVE, ClubPersonality.MONEY_DRIVEN, ClubPersonality.IDEALIST)
    ),

    /** 联赛前半：稳定欧战资格 */
    TOP_HALF(
        label = "联赛前半",
        description = "稳定在联赛上半区，争取欧战资格",
        targetSeasons = 3,
        minReputation = 55,
        compatiblePersonalities = listOf(ClubPersonality.PRAGMATIC, ClubPersonality.AGGRESSIVE, ClubPersonality.IDEALIST)
    ),

    /** 保级：避免降级，量入为出 */
    AVOID_RELEGATION(
        label = "保级",
        description = "避免降级，量入为出，优先即战力与老将",
        targetSeasons = 3,
        minReputation = 0,
        compatiblePersonalities = listOf(ClubPersonality.CONSERVATIVE, ClubPersonality.PRAGMATIC)
    ),

    /** 培养新星：青训造血 + 卖人变现 */
    DEVELOP_YOUTH(
        label = "培养新星",
        description = "投资青训，培养并卖出高价球员，建立人才流水线",
        targetSeasons = 5,
        minReputation = 40,
        compatiblePersonalities = listOf(ClubPersonality.YOUTH_ADVOCATE, ClubPersonality.IDEALIST)
    ),

    /** 财务平衡：量入为出，控制工资占比 */
    FINANCIAL_BALANCE(
        label = "财务平衡",
        description = "维持工资/收入比 ≤ 70%，量入为出，避免亏损",
        targetSeasons = 4,
        minReputation = 30,
        compatiblePersonalities = listOf(ClubPersonality.CONSERVATIVE, ClubPersonality.PRAGMATIC)
    ),

    /** 商业品牌：扩大商业收入与全球影响力 */
    BUILD_BRAND(
        label = "商业品牌",
        description = "签约高商业价值球星，扩大全球影响力与商业收入",
        targetSeasons = 4,
        minReputation = 70,
        compatiblePersonalities = listOf(ClubPersonality.MONEY_DRIVEN, ClubPersonality.AGGRESSIVE)
    );

    companion object {
        /** 由字符串安全解析（DB 字段 → 枚举），未匹配返回 null。 */
        fun fromString(value: String?): LongTermGoal? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
