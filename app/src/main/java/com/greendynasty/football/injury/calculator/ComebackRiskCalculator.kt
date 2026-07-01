package com.greendynasty.football.injury.calculator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.injury.model.ForceReturnRisk
import com.greendynasty.football.injury.model.InjuryConfig
import com.greendynasty.football.injury.model.InjurySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 强行复出风险计算器（T08.4）
 *
 * 实现 V0.1 07 §六.3 强行复出风险评估，返回风险详情供 UI 展示与玩家二次确认：
 * - 复发概率（进度越低越高）
 * - 加重概率（进度 <50% 才有，可能升级为更严重伤病）
 * - 永久能力下降概率（仅重伤及以上 + 进度 <50%）
 * - CA 临时下降（进度越低惩罚越大，按严重度 5-20 点）
 * - 比赛中再伤概率翻倍系数（默认 2.5）
 * - 球员士气下降（默认 -10）
 *
 * 风险代价铁律：强行复出后 1 个月内必须有可见负面后果。
 */
class ComebackRiskCalculator(
    private val databaseManager: DatabaseManager,
    private val config: InjuryConfig = InjuryConfig.getDefault()
) {

    /**
     * 评估强行复出风险（仅查询不修改）
     *
     * @param injuryId 伤病记录 ID
     * @param currentDate 当前日期
     */
    suspend fun evaluate(injuryId: Int, currentDate: LocalDate): ForceReturnRisk =
        withContext(Dispatchers.IO) {
            val injury = databaseManager.saveInjuryDao().get(injuryId)
                ?: return@withContext ForceReturnRisk.invalid()
            evaluateFor(injury)
        }

    /** 直接基于伤病实体评估（避免重复查询） */
    fun evaluateFor(injury: SaveInjuryEntity): ForceReturnRisk {
        if (injury.status !in listOf("active", "recovering")) return ForceReturnRisk.invalid()

        val severityName = InjurySeverity.fromCode(injury.severity).name
        val progress = injury.recoveryProgress

        // 1. 复发概率（进度 0 → base，进度 100 → 0，线性）
        val recurrenceProbability = calculateRecurrenceProbability(progress, severityName)

        // 2. 加重概率（仅进度 <50%）
        val aggravationProbability = calculateAggravationProbability(progress, severityName)

        // 3. 永久能力下降概率（仅重伤及以上 + 进度 <50%）
        val permanentLossProbability = calculatePermanentLossProbability(progress, severityName)

        // 4. 比赛中再伤概率翻倍系数
        val matchReinjuryMultiplier = config.forceReturn.matchReinjuryMultiplier

        // 5. CA 临时下降
        val caPenalty = calculateCaPenalty(progress, severityName)

        // 6. 球员士气下降
        val moraleDrop = config.forceReturn.moraleDrop

        return ForceReturnRisk(
            valid = true,
            currentProgress = progress,
            recurrenceProbability = recurrenceProbability,
            aggravationProbability = aggravationProbability,
            permanentLossProbability = permanentLossProbability,
            matchReinjuryMultiplier = matchReinjuryMultiplier,
            caPenalty = caPenalty,
            moraleDrop = moraleDrop,
            severity = severityName,
            warnings = generateWarnings(
                recurrenceProbability, aggravationProbability, permanentLossProbability, severityName
            )
        )
    }

    /** 复发概率 = base × (1 - progress/100) */
    private fun calculateRecurrenceProbability(progress: Int, severity: String): Double {
        val base = config.forceReturn.recurrenceBaseBySeverity[severity] ?: 0.3
        return (base * (1.0 - progress / 100.0)).coerceIn(0.0, 1.0)
    }

    /** 加重概率 = base × (1 - progress/50)，仅进度 <50% */
    private fun calculateAggravationProbability(progress: Int, severity: String): Double {
        if (progress >= 50) return 0.0
        val base = config.forceReturn.aggravationBaseBySeverity[severity] ?: 0.15
        return (base * (1.0 - progress / 50.0)).coerceIn(0.0, 1.0)
    }

    /** 永久能力下降概率（仅重伤及以上 + 进度 <50%） */
    private fun calculatePermanentLossProbability(progress: Int, severity: String): Double {
        if (severity !in listOf("MAJOR", "CAREER_THREATENING")) return 0.0
        if (progress >= 50) return 0.0
        val base = config.forceReturn.permanentLossBase[severity] ?: 0.1
        return (base * (1.0 - progress / 50.0)).coerceIn(0.0, 1.0)
    }

    /** CA 临时下降 = maxPenalty × (1 - progress/100) */
    private fun calculateCaPenalty(progress: Int, severity: String): Int {
        val maxPenalty = config.forceReturn.maxCaPenalty[severity] ?: 10
        return (maxPenalty * (1.0 - progress / 100.0)).toInt().coerceAtLeast(0)
    }

    private fun generateWarnings(
        recur: Double, aggr: Double, perm: Double, severity: String
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (recur > 0.3) warnings.add("复发概率高（${(recur * 100).toInt()}%），可能延长伤停")
        if (aggr > 0.15) warnings.add("加重风险存在（${(aggr * 100).toInt()}%），可能升级为更严重伤病")
        if (perm > 0.1) warnings.add("永久能力下降风险（${(perm * 100).toInt()}%），可能影响职业生涯")
        if (severity == "CAREER_THREATENING") warnings.add("职业威胁伤强行复出极不推荐")
        return warnings
    }
}
