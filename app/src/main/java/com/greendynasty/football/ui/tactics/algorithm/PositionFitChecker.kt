package com.greendynasty.football.ui.tactics.algorithm

import com.greendynasty.football.match.api.PlayerAttributes
import com.greendynasty.football.match.api.Position

/**
 * 位置适配度计算器（V0.1 03 §3 战术页 + V0.2 04 §二）。
 *
 * 基于位置权重表计算球员在目标位置的适配度（0-100）：
 * - 同位置：100
 * - 相邻位置：70-90
 * - 同类位置（不同组）：40-60
 * - 不适应：20-40
 *
 * 位置分组：
 * - GK：门将
 * - DEFENSE：CB / LB / RB
 * - MIDFIELD：DM / CM / AM
 * - ATTACK：LW / RW / ST / CF
 *
 * 适配度可被球员属性微调（如多面手属性 workRate / versatility 高则跨位置衰减更小）。
 */
class PositionFitChecker {

    /**
     * 计算球员在目标位置的适配度。
     *
     * @param playerPosition 球员主位置（GK/CB/LB/RB/DM/CM/AM/LW/RW/ST/CF）
     * @param targetPosition 目标位置
     * @param attributes 球员属性（用于微调），可空时仅用位置权重
     * @return 适配度 0-100
     */
    fun calculateFit(
        playerPosition: String,
        targetPosition: String,
        attributes: PlayerAttributes?
    ): Int {
        // 同位置直接返回 100
        if (playerPosition.equals(targetPosition, ignoreCase = true)) return 100

        val from = parsePosition(playerPosition) ?: return BASE_POOR
        val to = parsePosition(targetPosition) ?: return BASE_POOR

        // GK 特殊处理：门将只适配门将
        if (from == Position.GK || to == Position.GK) return BASE_POOR

        // 查位置权重表
        var score = WEIGHT_TABLE[from to to] ?: crossGroupScore(from, to)

        // 属性微调：多面手属性（workRate + teamwork）越高，跨位置衰减越小
        if (attributes != null && score < 100) {
            val versatility = (attributes.workRate + attributes.teamwork) / 2
            val bonus = (versatility - 50).coerceIn(0, 20) / 5 // 0-4 加成
            score = (score + bonus).coerceAtMost(95)
        }

        return score.coerceIn(BASE_POOR, 100)
    }

    /**
     * 计算球员在某位置是否适配（便捷方法）。
     */
    fun isFit(playerPosition: String, targetPosition: String): Boolean =
        calculateFit(playerPosition, targetPosition, null) >= FAIR_THRESHOLD

    /** 跨组适配度：同组内查表，跨组用基础分 */
    private fun crossGroupScore(from: Position, to: Position): Int {
        val fromGroup = groupOf(from)
        val toGroup = groupOf(to)
        return if (fromGroup == toGroup) BASE_SAME_GROUP else BASE_CROSS_GROUP
    }

    /** 位置分组 */
    private fun groupOf(position: Position): PositionGroup = when (position) {
        Position.GK -> PositionGroup.GK
        Position.CB, Position.LB, Position.RB -> PositionGroup.DEFENSE
        Position.DM, Position.CM, Position.AM -> PositionGroup.MIDFIELD
        Position.LW, Position.RW, Position.ST, Position.CF -> PositionGroup.ATTACK
    }

    /** 解析位置字符串 */
    private fun parsePosition(text: String): Position? =
        runCatching { Position.valueOf(text.uppercase().trim()) }.getOrNull()

    companion object {
        /** 适配度阈值 */
        const val FAIR_THRESHOLD = 40

        /** 基础分：同组不同位置 */
        private const val BASE_SAME_GROUP = 55

        /** 基础分：跨组 */
        private const val BASE_CROSS_GROUP = 30

        /** 基础分：完全不适应（GK 与外场互转） */
        private const val BASE_POOR = 20

        /**
         * 位置权重表（from → to）。
         * 覆盖相邻位置的精细适配度，未列出的组合回落到 [crossGroupScore]。
         */
        private val WEIGHT_TABLE: Map<Pair<Position, Position>, Int> = mapOf(
            // ===== 后卫线内部 =====
            // CB
            (Position.CB to Position.LB) to 75,
            (Position.CB to Position.RB) to 75,
            (Position.CB to Position.DM) to 80,
            // LB
            (Position.LB to Position.RB) to 80,
            (Position.LB to Position.CB) to 75,
            (Position.LB to Position.LW) to 70,
            (Position.LB to Position.DM) to 60,
            // RB
            (Position.RB to Position.LB) to 80,
            (Position.RB to Position.CB) to 75,
            (Position.RB to Position.RW) to 70,
            (Position.RB to Position.DM) to 60,

            // ===== 中场线内部 =====
            // DM
            (Position.DM to Position.CM) to 85,
            (Position.DM to Position.CB) to 70,
            (Position.DM to Position.AM) to 70,
            // CM
            (Position.CM to Position.DM) to 85,
            (Position.CM to Position.AM) to 85,
            (Position.CM to Position.LW) to 65,
            (Position.CM to Position.RW) to 65,
            (Position.CM to Position.ST) to 60,
            // AM
            (Position.AM to Position.CM) to 85,
            (Position.AM to Position.DM) to 70,
            (Position.AM to Position.ST) to 75,
            (Position.AM to Position.CF) to 75,
            (Position.AM to Position.LW) to 70,
            (Position.AM to Position.RW) to 70,

            // ===== 前锋线内部 =====
            // LW
            (Position.LW to Position.RW) to 80,
            (Position.LW to Position.ST) to 70,
            (Position.LW to Position.CF) to 75,
            (Position.LW to Position.AM) to 70,
            // RW
            (Position.RW to Position.LW) to 80,
            (Position.RW to Position.ST) to 70,
            (Position.RW to Position.CF) to 75,
            (Position.RW to Position.AM) to 70,
            // ST
            (Position.ST to Position.CF) to 90,
            (Position.ST to Position.AM) to 65,
            (Position.ST to Position.LW) to 65,
            (Position.ST to Position.RW) to 65,
            // CF
            (Position.CF to Position.ST) to 90,
            (Position.CF to Position.AM) to 70,
            (Position.CF to Position.LW) to 65,
            (Position.CF to Position.RW) to 65
        )
    }
}

/** 位置分组 */
private enum class PositionGroup { GK, DEFENSE, MIDFIELD, ATTACK }
