package com.greendynasty.football.youth.growth

import com.greendynasty.football.growth.model.GrowthResult
import com.greendynasty.football.youth.model.YouthAcademyConfig
import com.greendynasty.football.youth.model.YouthPlayerEntity

/**
 * T16 青训球员异常保护（V0.2 §十五 + T16 方案 §五.3 + §十四.1）
 *
 * 防止"小妖全部满潜"问题，叠加在 T0Y 成长结果之上，不修改 T0Y 内部逻辑。
 *
 * 4 重保护：
 * 1. 单月成长上限（青训球员 +1 至 +3）
 *    - 14-15 岁：+2
 *    - 16+ 岁：+3
 * 2. 接近 PA 时成长放缓
 *    - gap ≤ 3：caDelta / 3
 *    - gap ≤ 5：caDelta / 2
 *    - gap ≤ 0：caDelta = 0
 * 3. 低职业态度降低成长（< 30 时成长减半）
 * 4. 导师加成（仅正向，乘以 (1 + bonus)）
 *
 * 红线：单月成长不超过 3。
 *
 * @param config 青训学院配置
 */
class YouthAnomalyGuard(
    private val config: YouthAcademyConfig = YouthAcademyConfig.getDefault()
) {

    /**
     * 应用青训版异常保护。
     *
     * @param result T0Y GrowthCalculator 输出的成长结果
     * @param player 青训球员
     * @param age 球员年龄
     * @param mentorBonus 导师加成（0.0-0.1，无导师为 0.0）
     * @return 调整后的成长结果（仅修改 caDelta，其他字段不变）
     */
    fun apply(
        result: GrowthResult,
        player: YouthPlayerEntity,
        age: Int,
        mentorBonus: Double
    ): GrowthResult {
        var caDelta = result.caDelta

        // 1. 单月成长上限（青训球员 +1 至 +3）
        val maxMonthlyGrowth = when (age) {
            in 14..15 -> config.maxMonthlyGrowthAge14_15
            else -> config.maxMonthlyGrowthAge16Plus
        }
        if (caDelta > maxMonthlyGrowth) {
            caDelta = maxMonthlyGrowth
        }

        // 2. 接近 PA 时成长放缓
        val gapToPotential = player.potentialPa - player.currentCa
        caDelta = when {
            gapToPotential <= 0 -> 0
            gapToPotential <= config.potentialSlowdownThreshold3 -> caDelta / 3
            gapToPotential <= config.potentialSlowdownThreshold5 -> caDelta / 2
            else -> caDelta
        }

        // 3. 低职业态度降低成长（< 30 时成长减半）
        if (player.professionalism < config.lowProfessionalismThreshold) {
            caDelta = (caDelta * config.lowProfessionalismGrowthMultiplier).toInt()
        }

        // 4. 重点培养加成（成长 +15%）
        if (player.keyProspect) {
            caDelta = (caDelta * (1 + config.youthGrowthBonus)).toInt()
        }

        // 5. 导师加成（仅正向，乘以 (1 + bonus)）
        if (mentorBonus > 0) {
            caDelta = (caDelta * (1 + mentorBonus)).toInt()
        }

        // 红线：单月成长不超过 maxMonthlyGrowth
        caDelta = caDelta.coerceIn(0, maxMonthlyGrowth)

        return result.copy(caDelta = caDelta)
    }
}
