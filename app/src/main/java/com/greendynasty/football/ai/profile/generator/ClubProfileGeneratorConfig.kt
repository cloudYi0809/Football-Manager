package com.greendynasty.football.ai.profile.generator

/**
 * T18 俱乐部画像生成器配置（V0.2 05 §三 + T18 方案 §九）。
 *
 * 所有参数集中配置化，便于调参不改架构（铁律）。
 *
 * @property randomBiasRange 各偏好字段的随机偏移范围（±范围，避免画像完全确定）
 * @property reputationEliteThreshold 声望 ≥ 此值判定为豪门候选
 * @property reputationMidTierThreshold 声望 ≥ 此值判定为中游候选
 * @property reputationLowTierThreshold 声望 < 此值判定为保级候选
 * @property financialPowerEliteThreshold 财力 ≥ 此值判定为豪门/金元候选
 * @property youthLevelHighThreshold 青训等级 ≥ 此值判定为青训派候选
 * @property resaleMidTierThreshold 转售偏好中位值
 * @property personalityWeightByReputation 是否按声望加权性格选择（高声望倾向 AGGRESSIVE/MONEY_DRIVEN）
 */
data class ClubProfileGeneratorConfig(
    val randomBiasRange: Int = 15,
    val reputationEliteThreshold: Int = 75,
    val reputationMidTierThreshold: Int = 55,
    val reputationLowTierThreshold: Int = 40,
    val financialPowerEliteThreshold: Int = 70,
    val youthLevelHighThreshold: Int = 65,
    val resaleMidTierThreshold: Int = 50,
    val personalityWeightByReputation: Boolean = true
) {
    companion object {
        /** 默认配置（V0.2 推荐参数） */
        val DEFAULT = ClubProfileGeneratorConfig()
    }
}
