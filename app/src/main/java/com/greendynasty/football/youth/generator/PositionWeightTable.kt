package com.greendynasty.football.youth.generator

import com.greendynasty.football.youth.model.AcademyStyle
import kotlin.random.Random

/**
 * T16 青训风格位置权重表（V0.1 08 §二.2 + T16 方案 §四.4）
 *
 * 按风格权重随机抽取产出位置，体现俱乐部差异化打法。
 * 例如：技术流派向前腰/中场倾斜，防守流派向中卫/后腰倾斜。
 *
 * 副位置选择规则：从同位置族中抽取 1-2 个（用于多面手培养）。
 */
class PositionWeightTable {

    /**
     * 按风格权重随机抽取主位置。
     *
     * @param style 青训风格
     * @return 位置代码（ST / LW / RW / AM / CM / DM / CB / LB / RB / GK）
     */
    fun pickPosition(style: AcademyStyle): String {
        val weights = style.positionWeights
        val totalWeight = weights.values.sum()
        var roll = Random.nextDouble() * totalWeight

        for ((pos, weight) in weights) {
            roll -= weight
            if (roll <= 0) return pos
        }
        return weights.keys.first()
    }

    /**
     * 选择副位置（同位置族）。
     *
     * @param primary 主位置代码
     * @return 1-2 个副位置（不含主位置）
     */
    fun pickAlternativePositions(primary: String): List<String> {
        val group = positionGroup(primary)
        return group.filter { it != primary }.shuffled().take(2)
    }

    /** 位置族（用于副位置选择）。 */
    private fun positionGroup(primary: String): List<String> = when (primary) {
        "ST", "CF" -> listOf("ST", "CF", "LW", "RW")
        "LW", "RW" -> listOf("LW", "RW", "AM", "ST")
        "AM", "CM" -> listOf("AM", "CM", "DM", "LW", "RW")
        "DM" -> listOf("DM", "CM", "CB")
        "CB" -> listOf("CB", "DM")
        "LB", "RB", "LWB", "RWB" -> listOf("LB", "RB", "LWB", "RWB")
        "GK" -> listOf("GK")
        else -> listOf(primary)
    }
}
