package com.greendynasty.football.growth.calculator

import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.GrowthResult

/**
 * 成长异常保护器（T09.4，V0.2 §十五 异常保护）
 *
 * 防止 4 类异常：
 * 1. 小妖成长过快（单月超上限）—— 19 岁以下 ≤3，23 岁以下 ≤4，27 岁以下 ≤2
 * 2. 老将不衰退（衰退期 CA 不增）—— 28+ 球员 caDelta 强制 ≤ 0
 * 3. 已达潜力继续成长（CA >= PA 时 caDelta 强制 0）
 * 4. 衰退期身体属性不下降（32+ 强制 pace/acceleration -1）
 *
 * 红线：
 * - 单月 CA 增长 ≤ maxMonthlyGrowthYoung（3）
 * - 28+ 球员 CA 不再增长
 * - 已达 PA 球员 CA 不再增长
 */
class GrowthAnomalyGuard(private val config: GrowthConfig) {

    /**
     * 对成长结果施加异常保护。
     *
     * @param input 成长输入
     * @param result 原始成长结果
     * @return 保护后的成长结果
     */
    fun protect(input: GrowthInput, result: GrowthResult): GrowthResult {
        var caDelta = result.caDelta
        var attributeChanges = result.attributeChanges.toMutableMap()
        val notes = result.notes.toMutableList()

        // 1. 单月成长上限（按年龄）
        val maxMonthly = when {
            input.age <= 19 -> config.maxMonthlyGrowthYoung      // 3
            input.age <= 23 -> config.maxMonthlyGrowthMid        // 4
            input.age <= 27 -> config.maxMonthlyGrowthPrime      // 2
            else -> 0                                             // 衰退期不增
        }
        if (caDelta > maxMonthly) {
            notes.add("异常保护：单月成长 ${caDelta} 超上限 ${maxMonthly}，截断")
            caDelta = maxMonthly
        }

        // 2. 衰退期强制不增长（28+ CA 不再 +）
        if (input.age >= config.declineAgeStart && caDelta > 0) {
            notes.add("异常保护：衰退期 CA 不增长")
            caDelta = 0
        }

        // 3. 已达潜力不再增长（CA >= PA 时 caDelta 强制 0）
        if (input.player.currentCa >= input.player.currentPa && caDelta > 0) {
            notes.add("异常保护：已达潜力上限，CA 不再增长")
            caDelta = 0
        }

        // 4. 早衰保护：32+ 强制身体属性下降
        if (input.age >= config.forceDeclineAgeStart) {
            val currentPace = attributeChanges["pace"] ?: 0
            val currentAcc = attributeChanges["acceleration"] ?: 0
            // 确保身体属性至少下降配置的点数
            if (currentPace > -config.forceDeclinePaceDelta) {
                attributeChanges["pace"] = -config.forceDeclinePaceDelta
            }
            if (currentAcc > -config.forceDeclineAccDelta) {
                attributeChanges["acceleration"] = -config.forceDeclineAccDelta
            }
        }

        // 5. 属性值边界保护（单个属性不超过 1-99）
        // 注：实际属性值更新由 Repository 层 coerceIn 处理，此处仅保证变化量合理
        attributeChanges = attributeChanges.mapValues { (_, v) ->
            v.coerceIn(-5, 5)
        }.toMutableMap()

        val caAfter = (result.caBefore + caDelta).coerceAtLeast(1)

        return result.copy(
            caDelta = caDelta,
            caAfter = caAfter,
            attributeChanges = attributeChanges,
            notes = notes
        )
    }
}
