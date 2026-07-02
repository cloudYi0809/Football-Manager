package com.greendynasty.football.economy.calculator

import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.model.ClubFinancialState
import com.greendynasty.football.economy.model.FinancialHealthReport
import com.greendynasty.football.economy.model.FinancialWarningLevel

/**
 * T17 财政安全线检查器（V0.2 §十 4 档预警）。
 *
 * 严格依据 V0.2 §十 工资/收入比阈值：
 *
 * | 比率 | 等级 | 措施 |
 * |------|------|------|
 * | < 55% | 健康 | 无 |
 * | 55%-70% | 可接受 | 关注工资增长趋势 |
 * | 70%-85% | 风险 | 降低买人欲望 / 考虑出售高薪替补 |
 * | > 85% | 高危 | 停止买人 / 提高卖人概率 / 优先清理高薪替补 / 董事会限制预算 |
 *
 * AI 俱乐部超过 85% 后：
 * - 降低买人欲望
 * - 提高卖人概率
 * - 优先清理高薪替补
 * - 董事会可能限制预算
 *
 * 供 T07 每月任务调用（财政检查）+ T18 AI 决策调用（预算约束）。
 *
 * @property config 经济配置
 */
class FinancialHealthChecker(
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    /**
     * 检查俱乐部财政健康状况。
     *
     * @param financial 俱乐部财政状态
     * @return 财政健康报告
     */
    fun check(financial: ClubFinancialState): FinancialHealthReport {
        val ratio = financial.wageToIncomeRatio
        val params = config.financialHealth
        val level = when {
            ratio < params.healthyThreshold -> FinancialWarningLevel.HEALTHY
            ratio < params.acceptableThreshold -> FinancialWarningLevel.ACCEPTABLE
            ratio < params.riskThreshold -> FinancialWarningLevel.RISK
            else -> FinancialWarningLevel.HIGH_RISK
        }

        return FinancialHealthReport(
            clubId = financial.clubId,
            wageToIncomeRatio = ratio,
            level = level,
            recommendations = generateRecommendations(level)
        )
    }

    /**
     * 生成建议措施列表（V0.2 §十）。
     */
    private fun generateRecommendations(level: FinancialWarningLevel): List<String> {
        return when (level) {
            FinancialWarningLevel.HEALTHY -> emptyList()
            FinancialWarningLevel.ACCEPTABLE -> listOf("关注工资增长趋势")
            FinancialWarningLevel.RISK -> listOf(
                "降低买人欲望",
                "考虑出售高薪替补"
            )
            FinancialWarningLevel.HIGH_RISK -> listOf(
                "停止买人",
                "提高卖人概率",
                "优先清理高薪替补",
                "董事会限制预算"
            )
        }
    }
}
