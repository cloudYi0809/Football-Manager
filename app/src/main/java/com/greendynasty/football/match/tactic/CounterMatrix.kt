package com.greendynasty.football.match.tactic

import com.greendynasty.football.match.api.TacticStyle

/**
 * 战术克制矩阵（V0.2 04 §四 共 7 组克制关系）
 *
 * 返回我方战术对对方战术的 xG 修正乘子，范围严格限制在 0.85-1.15。
 *
 * 7 组克制关系（严格按 V0.2 §四）：
 * 1. 高位压迫 克 控球组织（前场抢断打乱控球）
 * 2. 快速反击 克 高位压迫（身后空当利用）
 * 3. 防守反击 克 控球组织（断球后反击）
 * 4. 边路传中 克 防守反击/密集防守（拉开宽度破密集）
 * 5. 中路渗透 被 防守反击/双后腰 克（中路被堵死）
 * 6. 长传冲吊 克 控球组织/短传后卫（冲击短出球后卫）
 * 7. 巨星自由发挥 克 防守反击/人盯人（个人能力破密集）
 *
 * 注：未登记的组合返回 1.0（中性）。
 */
object CounterMatrix {

    /** 克制修正下限（V0.2 04 §四） */
    private const val MIN_MODIFIER = 0.85
    /** 克制修正上限（V0.2 04 §四） */
    private const val MAX_MODIFIER = 1.15

    /**
     * 克制条目：我方风格 克制 / 被克制 对方风格时的修正。
     *
     * @param myStyle 我方战术风格
     * @param opponentStyle 对方战术风格
     * @param modifier 修正乘子（>1.0 表示我方占优，<1.0 表示我方被克）
     */
    private data class CounterEntry(
        val myStyle: TacticStyle,
        val opponentStyle: TacticStyle,
        val modifier: Double
    )

    /** 7 组克制关系（myStyle_vs_opponentStyle） */
    private val entries: List<CounterEntry> = listOf(
        // 1. 高位压迫 克 控球组织：前场抢断打乱控球节奏
        CounterEntry(TacticStyle.HIGH_PRESS, TacticStyle.POSSESSION, 1.12),
        // 2. 快速反击 克 高位压迫：利用高位防线身后空当
        CounterEntry(TacticStyle.COUNTER_ATTACK, TacticStyle.HIGH_PRESS, 1.15),
        // 3. 防守反击 克 控球组织：断球后快速反击
        CounterEntry(TacticStyle.DEFENSIVE_COUNTER, TacticStyle.POSSESSION, 1.10),
        // 4. 边路传中 克 防守反击（密集防守）：拉开宽度破密集防守
        CounterEntry(TacticStyle.WING_CROSS, TacticStyle.DEFENSIVE_COUNTER, 1.12),
        // 5. 中路渗透 被 防守反击（双后腰）克：中路被双后腰堵死
        CounterEntry(TacticStyle.CENTRAL_PENETRATION, TacticStyle.DEFENSIVE_COUNTER, 0.88),
        // 6. 长传冲吊 克 控球组织（短出球后卫）：冲击短传出球后卫
        CounterEntry(TacticStyle.LONG_BALL, TacticStyle.POSSESSION, 1.10),
        // 7. 巨星自由发挥 克 防守反击（人盯人）：个人能力破密集防守
        CounterEntry(TacticStyle.STAR_FREE, TacticStyle.DEFENSIVE_COUNTER, 1.12)
    )

    /** 反向查询用：精确匹配表 */
    private val lookup: Map<Pair<TacticStyle, TacticStyle>, Double> =
        entries.associate { (it.myStyle to it.opponentStyle) to it.modifier }

    /**
     * 查询我方战术对对方战术的克制修正乘子。
     *
     * @param myStyle 我方战术风格
     * @param opponentStyle 对方战术风格
     * @return 修正乘子 0.85-1.15（未登记组合返回 1.0）
     */
    fun getModifier(myStyle: TacticStyle, opponentStyle: TacticStyle): Double {
        val direct = lookup[myStyle to opponentStyle]
        if (direct != null) return direct.coerceIn(MIN_MODIFIER, MAX_MODIFIER)
        // 反向：若对方克我方，则我方修正取倒数并 clamp
        val reverse = lookup[opponentStyle to myStyle]
        if (reverse != null && reverse < 1.0) {
            // 对方克我方（reverse<1.0 表示对方对我是 0.88），我方应被削弱
            return reverse.coerceIn(MIN_MODIFIER, MAX_MODIFIER)
        }
        return 1.0
    }

    /** 全部克制条目（调试 / 展示用） */
    fun allEntries(): List<String> = entries.map {
        "${it.myStyle}_vs_${it.opponentStyle} -> ${it.modifier}"
    }

    /** 克制修正区间 */
    fun range(): ClosedFloatingPointRange<Double> = MIN_MODIFIER..MAX_MODIFIER
}
