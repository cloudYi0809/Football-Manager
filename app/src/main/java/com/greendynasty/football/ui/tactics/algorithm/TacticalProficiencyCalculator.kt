package com.greendynasty.football.ui.tactics.algorithm

import com.greendynasty.football.match.api.Mentality
import com.greendynasty.football.match.api.PassStyle
import com.greendynasty.football.match.api.PlayerAttributes
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.ui.tactics.data.PlayerWithPosition
import com.greendynasty.football.ui.tactics.model.TacticalParameterModifiers
import com.greendynasty.football.ui.tactics.model.TacticalParameters
import com.greendynasty.football.ui.tactics.model.TacticalSetup
import com.greendynasty.football.ui.tactics.model.TacticalStyleDef
import kotlin.math.abs

/**
 * 战术熟练度计算器（V0.1 03 §3 + V0.2 04 §四）。
 *
 * 实时计算当前战术设置与球员阵容的匹配度，输出 0-100 分。
 * V0.1 03 §3 铁律："改变战术参数实时更新战术熟练度提示"。
 *
 * 三维加权（V0.1 T05 方案 §五）：
 * - 阵型适配 formationFit（权重 0.35）
 * - 球员属性匹配 playerAttributeFit（权重 0.45）
 * - 参数合理性 parameterReasonableness（权重 0.20）
 *
 * 同时输出体能风险等级（战术过于激进时提示）。
 */
class TacticalProficiencyCalculator(
    private val positionFitChecker: PositionFitChecker = PositionFitChecker()
) {

    /**
     * 计算战术熟练度。
     *
     * @param setup 战术设置
     * @param players 全部可选球员（含属性），用于查询首发球员详情
     * @return 熟练度 0-100
     */
    fun calculate(setup: TacticalSetup, players: List<PlayerWithPosition>): Double {
        val styleDef = TacticalStyleDef.from(setup.style)

        // 1. 阵型适配：风格兼容阵型 + 首发 11 人位置适配度
        val formationFit = calculateFormationFit(setup, styleDef, players)

        // 2. 球员属性匹配：首发球员属性与战术风格关键属性的契合度
        val playerAttributeFit = calculatePlayerAttributeFit(setup.style, setup, players)

        // 3. 参数合理性：当前参数与风格推荐参数的偏离度
        val parameterReasonableness = calculateParameterReasonableness(
            setup.parameters, styleDef.modifiers
        )

        val overall = (
            formationFit * WEIGHT_FORMATION +
                playerAttributeFit * WEIGHT_PLAYER +
                parameterReasonableness * WEIGHT_PARAMETER
            ) * 100.0

        return overall.coerceIn(0.0, 100.0)
    }

    /**
     * 计算体能风险等级（V0.1 03 §3"战术过于激进时提示体能风险"）。
     */
    fun calculateRiskLevel(parameters: TacticalParameters): RiskLevel {
        val score = parameters.aggressionScore
        return when {
            score >= TacticalParameters.HIGH_RISK_THRESHOLD -> RiskLevel.HIGH
            score >= TacticalParameters.MEDIUM_RISK_THRESHOLD -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    // ==================== 三维计算 ====================

    /** 阵型适配：风格兼容阵型（0.5 权重）+ 首发 11 人位置适配度均值（0.5 权重） */
    private fun calculateFormationFit(
        setup: TacticalSetup,
        styleDef: TacticalStyleDef,
        players: List<PlayerWithPosition>
    ): Double {
        // 风格兼容阵型
        val compatibility = if (styleDef.isCompatibleWith(setup.formation)) 1.0 else 0.6

        // 首发 11 人位置适配度均值
        val playerMap = players.associateBy { it.playerId }
        val fitScores = setup.starting11.mapNotNull { slot ->
            val player = slot.playerId?.let { playerMap[it] } ?: return@mapNotNull null
            positionFitChecker.calculateFit(player.position, slot.position.name, player.attributes)
        }
        val avgFit = if (fitScores.isEmpty()) 50.0 else fitScores.average()
        val fitNormalized = (avgFit / 100.0).coerceIn(0.0, 1.0)

        return compatibility * 0.5 + fitNormalized * 0.5
    }

    /** 球员属性匹配：首发球员属性与战术风格关键属性的契合度 */
    private fun calculatePlayerAttributeFit(
        style: TacticStyle,
        setup: TacticalSetup,
        players: List<PlayerWithPosition>
    ): Double {
        val playerMap = players.associateBy { it.playerId }
        val keyAttributes = keyAttributesOf(style)

        val scores = setup.starting11.mapNotNull { slot ->
            val player = slot.playerId?.let { playerMap[it] } ?: return@mapNotNull null
            val attrs = player.attributes ?: return@mapNotNull null
            calculateAttributeMatch(attrs, keyAttributes) / 100.0
        }

        return if (scores.isEmpty()) 0.5 else scores.average().coerceIn(0.0, 1.0)
    }

    /** 参数合理性：当前参数与风格推荐参数的偏离度 */
    private fun calculateParameterReasonableness(
        parameters: TacticalParameters,
        modifiers: TacticalParameterModifiers
    ): Double {
        // 数值参数偏离（节奏/压迫/防线），每偏离 1 扣 0.05，上限 0.4
        val tempoDelta = abs(parameters.tempo - (5 + modifiers.tempoDelta))
        val pressingDelta = abs(parameters.pressingIntensity - (5 + modifiers.pressingDelta))
        val lineDelta = abs(parameters.defensiveLine - (5 + modifiers.defensiveLineDelta))
        val numericPenalty = (tempoDelta + pressingDelta + lineDelta) * 0.05
        val numericScore = (1.0 - numericPenalty).coerceIn(0.4, 1.0)

        // 传球风格匹配
        val passScore = if (parameters.passStyle == modifiers.preferredPassStyle) 1.0 else 0.7

        // 心态匹配
        val mentalityScore = if (parameters.mentality == modifiers.preferredMentality) 1.0 else 0.8

        return (numericScore * 0.5 + passScore * 0.25 + mentalityScore * 0.25).coerceIn(0.0, 1.0)
    }

    // ==================== 属性匹配 ====================

    /** 战术风格的关键属性权重（属性名 → 权重） */
    private fun keyAttributesOf(style: TacticStyle): Map<String, Double> = when (style) {
        TacticStyle.POSSESSION -> mapOf(
            "passing" to 1.0, "technique" to 0.9, "vision" to 0.9,
            "teamwork" to 0.7, "composure" to 0.6
        )
        TacticStyle.COUNTER_ATTACK -> mapOf(
            "pace" to 1.0, "acceleration" to 0.9, "passing" to 0.8,
            "dribbling" to 0.7, "vision" to 0.7
        )
        TacticStyle.HIGH_PRESS -> mapOf(
            "pressing" to 1.0, "workRate" to 1.0, "aggression" to 0.8,
            "stamina" to 0.8, "tackling" to 0.7
        )
        TacticStyle.DEFENSIVE_COUNTER -> mapOf(
            "tackling" to 1.0, "marking" to 0.9, "interceptions" to 0.9,
            "pace" to 0.7, "positioning" to 0.8
        )
        TacticStyle.WING_CROSS -> mapOf(
            "crossing" to 1.0, "dribbling" to 0.8, "pace" to 0.8,
            "technique" to 0.7, "heading" to 0.7
        )
        TacticStyle.CENTRAL_PENETRATION -> mapOf(
            "passing" to 1.0, "vision" to 1.0, "technique" to 0.9,
            "dribbling" to 0.8, "composure" to 0.7
        )
        TacticStyle.LONG_BALL -> mapOf(
            "passing" to 0.9, "heading" to 1.0, "strength" to 0.9,
            "jumping" to 0.8, "positioning" to 0.7
        )
        TacticStyle.STAR_FREE -> mapOf(
            "dribbling" to 1.0, "technique" to 1.0, "finishing" to 0.9,
            "vision" to 0.8, "flair" to 0.8
        )
    }

    /** 计算球员属性与关键属性的匹配度（0-100） */
    private fun calculateAttributeMatch(
        attrs: PlayerAttributes,
        keyAttrs: Map<String, Double>
    ): Double {
        var totalWeight = 0.0
        var totalScore = 0.0
        keyAttrs.forEach { (name, weight) ->
            val value = attributeValue(attrs, name)
            totalWeight += weight
            totalScore += (value / 100.0) * weight
        }
        return if (totalWeight == 0.0) 50.0
        else (totalScore / totalWeight * 100.0).coerceIn(0.0, 100.0)
    }

    /** 按属性名取值（统一属性访问） */
    private fun attributeValue(attrs: PlayerAttributes, name: String): Int = when (name) {
        "finishing" -> attrs.finishing
        "shotPower" -> attrs.shotPower
        "longShots" -> attrs.longShots
        "pace" -> attrs.pace
        "acceleration" -> attrs.acceleration
        "dribbling" -> attrs.dribbling
        "heading" -> attrs.heading
        "passing" -> attrs.passing
        "technique" -> attrs.technique
        "vision" -> attrs.vision
        "workRate" -> attrs.workRate
        "pressing" -> attrs.pressing
        "teamwork" -> attrs.teamwork
        "tackling" -> attrs.tackling
        "marking" -> attrs.marking
        "interceptions" -> attrs.interceptions
        "standingTackle" -> attrs.standingTackle
        "slidingTackle" -> attrs.slidingTackle
        "aggression" -> attrs.aggression
        "composure" -> attrs.composure
        "leadership" -> attrs.leadership
        "consistency" -> attrs.consistency
        else -> 50
    }

    companion object {
        /** 阵型适配权重 */
        private const val WEIGHT_FORMATION = 0.35

        /** 球员属性匹配权重 */
        private const val WEIGHT_PLAYER = 0.45

        /** 参数合理性权重 */
        private const val WEIGHT_PARAMETER = 0.20
    }
}

/**
 * 体能风险等级（V0.1 03 §3）。
 */
enum class RiskLevel(val label: String, val description: String) {
    LOW("低风险", "战术保守，体能消耗可控"),
    MEDIUM("中风险", "战术偏激进，注意体能分配"),
    HIGH("高风险", "战术过于激进，下半场体能风险高")
}
