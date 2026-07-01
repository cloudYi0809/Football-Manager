package com.greendynasty.football.match.core

import com.greendynasty.football.match.api.CompetitionContext
import com.greendynasty.football.match.api.Importance
import com.greendynasty.football.match.api.MatchInput
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.config.TacticalModifier
import com.greendynasty.football.match.model.TeamRating
import com.greendynasty.football.match.model.XGResult
import com.greendynasty.football.match.model.XgModifiers
import kotlin.math.abs

/**
 * Layer 2 xG 生成层（V0.2 04 §五）
 *
 * 把双方 TeamRating 映射为预期进球 xG，
 * 严格按 V0.2 §五.2 公式实现，并应用主场优势与极端比赛检测。
 */
class XGLayer(private val config: MatchConfig = MatchConfig.DEFAULT) {

    /**
     * 生成双方 xG。
     *
     * 公式（V0.2 04 §五.2）：
     * xg = base_xg
     *    * clamp(attack_defense_ratio, 0.55, 1.75)
     *    * clamp(0.85 + (control_ratio - 1.0) * 0.25, 0.80, 1.20)
     *    * tactical_modifier
     *    * morale_modifier
     *    * condition_modifier
     *    * match_importance_modifier
     *
     * 主场优势：home_xg × [MatchConfig.homeAdvantage]（默认 1.10）
     */
    fun generate(home: TeamRating, away: TeamRating, input: MatchInput): XGResult {
        val ctx = input.competition

        // 1. 基础 xG（V0.2 04 §五.1）
        val homeBase = config.leagueAvgHomeXg
        val awayBase = config.leagueAvgAwayXg

        // 2. 强弱修正（V0.2 04 §五.2）
        val homeAttackDefRatio = safeDiv(home.attackScore, away.defenseScore)
        val awayAttackDefRatio = safeDiv(away.attackScore, home.defenseScore)
        val homeControlRatio = safeDiv(home.controlScore, away.controlScore)
        val awayControlRatio = safeDiv(away.controlScore, home.controlScore)

        val homeTacticalMod = calculateTacticalModifier(input.homeTeam.tactic.style, input.awayTeam.tactic.style)
        val awayTacticalMod = calculateTacticalModifier(input.awayTeam.tactic.style, input.homeTeam.tactic.style)

        val homeMoraleMod = home.moraleFactor
        val awayMoraleMod = away.moraleFactor
        val homeCondMod = home.conditionFactor
        val awayCondMod = away.conditionFactor

        val homeImportanceMod = calculateImportanceModifier(ctx, isHome = true)
        val awayImportanceMod = calculateImportanceModifier(ctx, isHome = false)

        // 3. xG 计算（V0.2 04 §五.2 公式）
        val homeAdClamped = homeAttackDefRatio.coerceIn(config.attackDefRatioMin, config.attackDefRatioMax)
        val awayAdClamped = awayAttackDefRatio.coerceIn(config.attackDefRatioMin, config.attackDefRatioMax)

        val homeCtrlMod = (0.85 + (homeControlRatio - 1.0) * config.controlRatioFactor)
            .coerceIn(config.controlRatioMin, config.controlRatioMax)
        val awayCtrlMod = (0.85 + (awayControlRatio - 1.0) * config.controlRatioFactor)
            .coerceIn(config.controlRatioMin, config.controlRatioMax)

        var homeXg = homeBase * homeAdClamped * homeCtrlMod *
            homeTacticalMod * homeMoraleMod * homeCondMod * homeImportanceMod

        var awayXg = awayBase * awayAdClamped * awayCtrlMod *
            awayTacticalMod * awayMoraleMod * awayCondMod * awayImportanceMod

        // 主场优势：home_xg × 1.10
        homeXg *= config.homeAdvantage

        // 4. 极端比赛检测（V0.2 04 §五.3）
        val isExtreme = isExtremeMatch(home, away)

        // 5. 上下限（V0.2 04 §五.3）
        val maxXg = if (isExtreme) config.maxExtremeXg else config.maxRegularXg
        val finalHomeXg = homeXg.coerceIn(config.minXg, maxXg)
        val finalAwayXg = awayXg.coerceIn(config.minXg, maxXg)

        return XGResult(
            homeXg = finalHomeXg,
            awayXg = finalAwayXg,
            homeModifiers = XgModifiers(
                attackDefenseRatio = homeAdClamped,
                controlRatio = homeCtrlMod,
                tacticalModifier = homeTacticalMod,
                moraleModifier = homeMoraleMod,
                conditionModifier = homeCondMod,
                matchImportanceModifier = homeImportanceMod
            ),
            awayModifiers = XgModifiers(
                attackDefenseRatio = awayAdClamped,
                controlRatio = awayCtrlMod,
                tacticalModifier = awayTacticalMod,
                moraleModifier = awayMoraleMod,
                conditionModifier = awayCondMod,
                matchImportanceModifier = awayImportanceMod
            ),
            isExtremeMatch = isExtreme
        )
    }

    /**
     * 极端强弱差检测（V0.2 04 §五.3）
     *
     * "极端强弱差可突破，但需触发 rare_extreme_match 标记"
     */
    private fun isExtremeMatch(home: TeamRating, away: TeamRating): Boolean {
        val ratingDiff = abs(
            (home.attackScore + home.defenseScore) - (away.attackScore + away.defenseScore)
        )
        return ratingDiff > config.extremeMatchThreshold
    }

    /**
     * 战术克制综合修正
     *
     * 从 [MatchConfig.counterMatrix] 查询我方战术对对方战术的修正项，
     * 将射门/单刀/传中/中路/反击等正向 delta 汇总为 xG 乘子。
     */
    private fun calculateTacticalModifier(myStyle: TacticStyle, opponentStyle: TacticStyle): Double {
        val key = "${myStyle}_$opponentStyle"
        val mod: TacticalModifier = config.counterMatrix[key] ?: TacticalModifier.NEUTRAL

        val positiveDelta = mod.myShotEventsDelta +
            mod.mySingleChanceDelta +
            mod.myCrossEventsDelta +
            mod.myCentralChanceDelta +
            mod.myCounterEventsDelta
        val negativeDelta = mod.opponentControlDelta * 0.5 + mod.opponentKeyPlayerLimited * 0.5

        return (1.0 + positiveDelta - negativeDelta).coerceIn(0.85, 1.15)
    }

    /**
     * 比赛重要性修正（V0.2 04 §五.2）
     *
     * 决赛/德比/保级战提升 xG（球队更拼）。
     */
    private fun calculateImportanceModifier(ctx: CompetitionContext, isHome: Boolean): Double {
        return when {
            ctx.isFinal || ctx.importance == Importance.FINAL -> 1.05
            ctx.isRelegationBattle || ctx.importance == Importance.RELEGATION_BATTLE -> 1.04
            ctx.isDerby || ctx.importance == Importance.DERBY -> 1.03
            else -> 1.0
        }
    }

    /** 安全除法，避免除零 */
    private fun safeDiv(a: Double, b: Double): Double =
        if (b <= 0.0) 1.0 else a / b
}
