package com.greendynasty.football.ai.profile.model

/**
 * T18 球员类型偏好枚举（V0.2 05 §五 球员筛选偏好）。
 *
 * 性格影响目标球员筛选：
 * - 青训派偏好年轻球员
 * - 金元偏好成名球星
 * - 黑店偏好高潜力低身价
 *
 * @property label 中文标签
 * @property description 偏好描述
 * @property ageRange 偏好年龄区间（null 表示无限制）
 * @property minCa 最小期望 CA（null 表示无限制）
 * @property minPa 最小期望 PA（null 表示无限制）
 * @property maxMarketValueRatio 最大可承受身价占预算比例（防破预算）
 * @property compatiblePersonalities 倾向搭配的俱乐部性格
 */
enum class PlayerArchetype(
    val label: String,
    val description: String,
    val ageRange: IntRange?,
    val minCa: Int?,
    val minPa: Int?,
    val maxMarketValueRatio: Double,
    val compatiblePersonalities: List<ClubPersonality>
) {
    /** 成名球星：高 CA、当打之年、商业价值高 */
    STAR(
        label = "成名球星",
        description = "高 CA 当打之年，商业价值高，转会费高",
        ageRange = 24..30,
        minCa = 80,
        minPa = null,
        maxMarketValueRatio = 0.6,
        compatiblePersonalities = listOf(ClubPersonality.MONEY_DRIVEN, ClubPersonality.AGGRESSIVE)
    ),

    /** 妖人小妖：高 PA、低年龄、有成长空间 */
    WONDERKID(
        label = "妖人小妖",
        description = "低年龄高 PA，有成长空间，潜力优先于即战力",
        ageRange = 16..21,
        minCa = null,
        minPa = 80,
        maxMarketValueRatio = 0.4,
        compatiblePersonalities = listOf(ClubPersonality.YOUTH_ADVOCATE, ClubPersonality.IDEALIST)
    ),

    /** 即战力替补：中等 CA、可轮换、身价适中 */
    SQUAD(
        label = "即战力替补",
        description = "中等 CA 可轮换，身价适中，填补阵容厚度",
        ageRange = 22..29,
        minCa = 65,
        minPa = null,
        maxMarketValueRatio = 0.3,
        compatiblePersonalities = listOf(ClubPersonality.PRAGMATIC, ClubPersonality.CONSERVATIVE)
    ),

    /** 老将经验：高 CA、年龄偏大、短约低费 */
    VETERAN(
        label = "老将经验",
        description = "年龄偏大但经验丰富，短约低费，提供更衣室领导力",
        ageRange = 32..37,
        minCa = 70,
        minPa = null,
        maxMarketValueRatio = 0.2,
        compatiblePersonalities = listOf(ClubPersonality.CONSERVATIVE, ClubPersonality.PRAGMATIC)
    ),

    /** 捡漏宝：低身价但有性价比，可转售变现 */
    BARGAIN(
        label = "捡漏宝",
        description = "低身价但性价比高，可转售变现，黑店首选",
        ageRange = 18..26,
        minCa = 60,
        minPa = 70,
        maxMarketValueRatio = 0.25,
        compatiblePersonalities = listOf(ClubPersonality.YOUTH_ADVOCATE, ClubPersonality.PRAGMATIC)
    );

    companion object {
        /** 由字符串安全解析（DB 字段 → 枚举），未匹配返回 null。 */
        fun fromString(value: String?): PlayerArchetype? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
