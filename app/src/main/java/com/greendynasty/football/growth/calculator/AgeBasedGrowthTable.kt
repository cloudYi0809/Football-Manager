package com.greendynasty.football.growth.calculator

import com.greendynasty.football.growth.model.GrowthPhase

/**
 * 7 档年龄成长系数表（V0.2 §球员成长 7 档年龄表）
 *
 * 严格对齐 T09 方案 §十一.2 测试用例 TC10-TC16 的期望 ageBase：
 * - 14 岁 EXPLOSIVE → 1.50
 * - 16 岁 RAPID → 1.30
 * - 19 岁 STEADY → 1.10
 * - 23 岁 PRE_PRIME → 0.60
 * - 27 岁 PRIME → 0.60
 * - 30 岁 PRE_DECLINE → 0.20
 * - 34 岁 DECLINE → -0.60
 *
 * ageBase 是月度成长公式的核心系数：caDelta = ageBase × weightedScore × scaleFactor。
 * 衰退期 ageBase 为负，驱动老将能力下滑。
 */
object AgeBasedGrowthTable {

    /**
     * 按年龄阶段获取成长系数。
     *
     * @param phase 7 档年龄阶段
     * @return 成长系数（衰退期为负）
     */
    fun getGrowthFactor(phase: GrowthPhase): Double = when (phase) {
        GrowthPhase.EXPLOSIVE -> 1.50   // 14-15 爆发成长期
        GrowthPhase.RAPID -> 1.30       // 16-17 高速成长期
        GrowthPhase.STEADY -> 1.10      // 18-20 稳定成长期
        GrowthPhase.PRE_PRIME -> 0.60   // 21-23 成熟前期
        GrowthPhase.PRIME -> 0.60       // 24-27 巅峰期
        GrowthPhase.PRE_DECLINE -> 0.20 // 28-31 衰退前期
        GrowthPhase.DECLINE -> -0.60    // 32+ 衰退期
    }

    /**
     * 退役年龄判定（基于位置与伤病历史简化）
     *
     * - 门将退役较晚（37+）
     * - 普通球员 35+
     * - 重伤历史丰富者提前
     *
     * @param primaryPosition 主要位置
     * @param retireAgeBase 基础退役年龄（来自 PlayerEntity.retireAgeBase）
     * @param majorInjuryCount 重伤历史次数
     * @return 退役年龄
     */
    fun getRetireAge(primaryPosition: String?, retireAgeBase: Int, majorInjuryCount: Int): Int {
        var age = retireAgeBase.coerceIn(33, 40)
        // 门将退役较晚
        if (primaryPosition?.uppercase() == "GK") {
            age = (age + 2).coerceAtMost(42)
        }
        // 重伤历史每 2 次提前 1 年退役
        age -= (majorInjuryCount / 2)
        return age.coerceAtLeast(30)
    }
}
