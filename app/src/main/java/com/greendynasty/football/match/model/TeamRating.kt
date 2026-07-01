package com.greendynasty.football.match.model

/**
 * 球队评分（V0.2 04 §三）
 *
 * 由 RatingLayer 计算的双方 attack / defense / control 综合评分，
 * 作为 XGLayer 的输入。所有评分理论上 0-100 区间。
 */
data class TeamRating(
    /** 进攻评分 0-100 */
    val attackScore: Double,
    /** 防守评分 0-100 */
    val defenseScore: Double,
    /** 中场控制评分 0-100 */
    val controlScore: Double,
    /** 士气修正系数 V0.2 0.85-1.15 */
    val moraleFactor: Double,
    /** 体能修正系数 V0.2 0.85-1.10 */
    val conditionFactor: Double,
    /** 战术适配度 0.90-1.10 */
    val tacticalFit: Double,
    /** 各分项明细，便于调试与回归 */
    val breakdown: RatingBreakdown
)

/**
 * 评分分项明细（调试用）
 *
 * 记录进攻 / 防守 / 中场三块各自的子项得分，
 * 用于回归测试与平衡性调参。
 */
data class RatingBreakdown(
    /** 进攻子项：forwardFinish / chanceCreation / wingThreat / midfieldSupply / setPieceAttack / morale / tacticalFit */
    val attackComponents: Map<String, Double>,
    /** 防守子项：centerBack / goalkeeper / defensiveMid / fullback / defensiveShape / setPieceDefense / tacticalFit / condition */
    val defenseComponents: Map<String, Double>,
    /** 中场子项：passing / technique / vision / workRate / pressing / teamwork / tacticalFit */
    val controlComponents: Map<String, Double>
)
