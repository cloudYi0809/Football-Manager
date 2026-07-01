package com.greendynasty.football.match.model

/**
 * xG 生成结果（V0.2 04 §五）
 *
 * 由 XGLayer 根据双方 TeamRating 与赛事上下文计算得出，
 * 包含主客队预期进球及全部修正系数，供 PoissonLayer 采样使用。
 */
data class XGResult(
    /** 主队预期进球 */
    val homeXg: Double,
    /** 客队预期进球 */
    val awayXg: Double,
    /** 主队 xG 修正明细 */
    val homeModifiers: XgModifiers,
    /** 客队 xG 修正明细 */
    val awayModifiers: XgModifiers,
    /** 是否触发 rare_extreme_match（V0.2 04 §五.3） */
    val isExtremeMatch: Boolean
)

/**
 * xG 修正系数明细（V0.2 04 §五.2）
 *
 * 对应公式：xg = base_xg * attackDefRatio * controlMod * tacticalMod * moraleMod * conditionMod * importanceMod
 */
data class XgModifiers(
    /** attack_defense_ratio = team_attack / opponent_defense */
    val attackDefenseRatio: Double,
    /** control_ratio 修正后的乘子（已 clamp） */
    val controlRatio: Double,
    /** 战术克制综合修正 */
    val tacticalModifier: Double,
    /** 士气修正 */
    val moraleModifier: Double,
    /** 体能修正 */
    val conditionModifier: Double,
    /** 比赛重要性修正（决赛/德比/保级战） */
    val matchImportanceModifier: Double
)
