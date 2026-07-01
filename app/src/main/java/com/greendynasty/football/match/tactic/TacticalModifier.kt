package com.greendynasty.football.match.tactic

import com.greendynasty.football.match.api.Mentality
import com.greendynasty.football.match.api.Tactic
import com.greendynasty.football.match.api.TacticStyle

/**
 * 战术修正器（V0.2 04 §四）
 *
 * 根据战术风格（8 类）与战术指令（压迫强度 / 防线高度 / 节奏 / 心态），
 * 计算进攻 / 防守 / 控制三块的乘子修正。
 *
 * 命名为 TacticalModifierCalculator 以区分 config 包的
 * [com.greendynasty.football.match.config.TacticalModifier]（后者是战术克制的事件修正数据类）。
 *
 * 8 类战术风格修正严格按 V0.2 §四：
 * - POSSESSION（控球组织）：控球↑、进攻略↑、防守持平
 * - COUNTER_ATTACK（快速反击）：进攻↑、控球↓、防守略↓
 * - HIGH_PRESS（高位压迫）：进攻↑、防守↓（高位风险）、控球↑
 * - DEFENSIVE_COUNTER（防守反击）：防守↑、进攻略↓、控球↓
 * - WING_CROSS（边路传中）：进攻↑（边路）、控球持平
 * - CENTRAL_PENETRATION（中路渗透）：进攻↑（中路）、控球↑
 * - LONG_BALL（长传冲吊）：进攻略↑、控球↓
 * - STAR_FREE（巨星自由发挥）：进攻↑、防守↓、控球↑
 */
class TacticalModifierCalculator {

    /**
     * 计算进攻修正乘子。
     *
     * @param tactic 战术指令
     * @return 进攻乘子（约 0.85-1.20）
     */
    fun calculateAttackModifier(tactic: Tactic): Double {
        // 1. 战术风格基础进攻修正
        var modifier = when (tactic.style) {
            TacticStyle.POSSESSION -> 1.03
            TacticStyle.COUNTER_ATTACK -> 1.10
            TacticStyle.HIGH_PRESS -> 1.08
            TacticStyle.DEFENSIVE_COUNTER -> 0.92
            TacticStyle.WING_CROSS -> 1.06
            TacticStyle.CENTRAL_PENETRATION -> 1.07
            TacticStyle.LONG_BALL -> 1.04
            TacticStyle.STAR_FREE -> 1.12
        }

        // 2. 压迫强度：高位压迫 / 强度越高，进攻越强（每点 +0.5%）
        if (tactic.style == TacticStyle.HIGH_PRESS) {
            modifier += (tactic.pressingIntensity - 5) * 0.008
        }

        // 3. 防线高度：高位（≥8）提升进攻，低位（≤3）削弱进攻
        modifier += when {
            tactic.defensiveLine >= 8 -> 0.03
            tactic.defensiveLine <= 3 -> -0.04
            else -> 0.0
        }

        // 4. 节奏：高节奏提升进攻但风险大（每点 +0.4%）
        modifier += (tactic.tempo - 5) * 0.004

        // 5. 心态修正
        modifier += mentalityAttackDelta(tactic.mentality)

        return modifier.coerceIn(0.85, 1.20)
    }

    /**
     * 计算防守修正乘子。
     *
     * @param tactic 战术指令
     * @return 防守乘子（约 0.85-1.20）
     */
    fun calculateDefenseModifier(tactic: Tactic): Double {
        // 1. 战术风格基础防守修正
        var modifier = when (tactic.style) {
            TacticStyle.POSSESSION -> 1.00
            TacticStyle.COUNTER_ATTACK -> 0.94
            TacticStyle.HIGH_PRESS -> 0.90
            TacticStyle.DEFENSIVE_COUNTER -> 1.12
            TacticStyle.WING_CROSS -> 0.98
            TacticStyle.CENTRAL_PENETRATION -> 0.97
            TacticStyle.LONG_BALL -> 0.96
            TacticStyle.STAR_FREE -> 0.92
        }

        // 2. 防线高度：高位（≥8）削弱防守（身后空当），低位（≤3）增强防守
        modifier += when {
            tactic.defensiveLine >= 8 -> -0.05
            tactic.defensiveLine <= 3 -> 0.05
            else -> 0.0
        }

        // 3. 压迫强度：高位压迫高强度削弱防守（体能消耗大），防守反击相反
        modifier += when (tactic.style) {
            TacticStyle.HIGH_PRESS -> (tactic.pressingIntensity - 5) * -0.006
            TacticStyle.DEFENSIVE_COUNTER -> (tactic.pressingIntensity - 5) * 0.004
            else -> 0.0
        }

        // 4. 节奏：高节奏削弱防守（每点 -0.4%）
        modifier += (tactic.tempo - 5) * -0.004

        // 5. 心态修正
        modifier += mentalityDefenseDelta(tactic.mentality)

        return modifier.coerceIn(0.85, 1.20)
    }

    /**
     * 计算中场控制修正乘子。
     *
     * @param tactic 战术指令
     * @return 控制乘子（约 0.85-1.15）
     */
    fun calculateControlModifier(tactic: Tactic): Double {
        // 1. 战术风格基础控制修正
        var modifier = when (tactic.style) {
            TacticStyle.POSSESSION -> 1.10
            TacticStyle.COUNTER_ATTACK -> 0.88
            TacticStyle.HIGH_PRESS -> 1.04
            TacticStyle.DEFENSIVE_COUNTER -> 0.85
            TacticStyle.WING_CROSS -> 1.00
            TacticStyle.CENTRAL_PENETRATION -> 1.06
            TacticStyle.LONG_BALL -> 0.90
            TacticStyle.STAR_FREE -> 1.05
        }

        // 2. 压迫强度：高位压迫提升控制（抢回球权）
        if (tactic.style == TacticStyle.HIGH_PRESS) {
            modifier += (tactic.pressingIntensity - 5) * 0.005
        }

        // 3. 节奏：高节奏略降控制精度
        modifier += (tactic.tempo - 5) * -0.003

        // 4. 心态修正
        modifier += mentalityControlDelta(tactic.mentality)

        return modifier.coerceIn(0.85, 1.15)
    }

    /** 心态对进攻的偏移 */
    private fun mentalityAttackDelta(mentality: Mentality): Double = when (mentality) {
        Mentality.ALL_ATTACK -> 0.05
        Mentality.BALANCED -> 0.0
        Mentality.ALL_DEFENSE -> -0.06
    }

    /** 心态对防守的偏移 */
    private fun mentalityDefenseDelta(mentality: Mentality): Double = when (mentality) {
        Mentality.ALL_ATTACK -> -0.05
        Mentality.BALANCED -> 0.0
        Mentality.ALL_DEFENSE -> 0.06
    }

    /** 心态对控制的偏移 */
    private fun mentalityControlDelta(mentality: Mentality): Double = when (mentality) {
        Mentality.ALL_ATTACK -> 0.02
        Mentality.BALANCED -> 0.0
        Mentality.ALL_DEFENSE -> -0.02
    }
}
