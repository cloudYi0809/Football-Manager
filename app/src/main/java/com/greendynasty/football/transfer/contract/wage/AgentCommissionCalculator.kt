package com.greendynasty.football.transfer.contract.wage

import com.greendynasty.football.data.history.entity.AgentEntity
import com.greendynasty.football.transfer.contract.config.ContractRenewalConfig

/**
 * T12.4 经纪人佣金计算器（V0.1 `09_转会_合同_经纪人系统.md` §七 5 型经纪人）。
 *
 * 严格依据 V0.2 `07_经济通胀_身价_工资模型.md` §九 + V0.1 09 §七：
 * ```
 * agent_commission = annual_wage × commission_rate × agent_greed_adjust
 * ```
 *
 * 其中：
 * - `annual_wage = weekly_wage × 52`
 * - `commission_rate`：按合同年限递增（长约佣金高，1 年 5% / 5 年 15%）
 * - `agent_greed_adjust`：5 型经纪人风格调整（GREEDY +20% / STAR +15% / DISRUPTIVE +10% / RELATIONAL -5% / ACADEMY -5%）
 *
 * 无经纪人时佣金为 0（青年球员 / 自由球员直接签约）。
 *
 * @property config 佣金配置
 */
class AgentCommissionCalculator(
    private val config: ContractRenewalConfig = ContractRenewalConfig.DEFAULT
) {

    /**
     * 计算续约经纪人佣金（V0.1 09 §七）。
     *
     * @param weeklyWage 续约后周薪
     * @param contractYears 合同年限
     * @param agent 经纪人实体（null 表示无经纪人）
     * @return 经纪人佣金（整数）
     */
    fun calculate(
        weeklyWage: Int,
        contractYears: Int,
        agent: AgentEntity?
    ): CommissionBreakdown {
        // 无经纪人 → 佣金为 0
        if (agent == null) {
            return CommissionBreakdown(
                commission = config.commission.noAgentCommission,
                annualWage = weeklyWage * 52,
                commissionRate = 0.0,
                agentStyleAdjust = 0.0,
                agentStyle = "NONE",
                agentName = null
            )
        }

        // 1. 年化工资
        val annualWage = weeklyWage * 52

        // 2. 按合同年限的佣金系数
        val yearsFactor = config.commission.yearsCommissionFactor[contractYears]
            ?: config.commission.defaultYearsCommissionFactor

        // 3. 经纪人风格佣金调整
        val agentStyle = agent.style?.uppercase() ?: "UNKNOWN"
        val agentStyleAdjust = config.commission.agentStyleAdjust[agentStyle]
            ?: config.commission.defaultAgentStyleAdjust

        // 4. 贪婪度属性修正（贪婪度 50 为基准，每偏离 50 调整 0.5%）
        val greedAttributeAdjust = (agent.greed - 50) / 200.0

        // 5. 综合佣金率
        val commissionRate = (yearsFactor + agentStyleAdjust + greedAttributeAdjust)
            .coerceAtLeast(0.0)

        // 6. 计算佣金
        val rawCommission = (annualWage * commissionRate).toInt()
        val commission = rawCommission.coerceIn(
            config.commission.minCommission,
            config.commission.maxCommission
        )

        return CommissionBreakdown(
            commission = commission,
            annualWage = annualWage,
            commissionRate = commissionRate,
            agentStyleAdjust = agentStyleAdjust + greedAttributeAdjust,
            agentStyle = agentStyle,
            agentName = agent.name
        )
    }

    /**
     * 推荐佣金（玩家编辑条款时的建议值）。
     *
     * 等同于 [calculate]，但用于 UI 表单初始化。
     */
    fun recommendCommission(
        weeklyWage: Int,
        contractYears: Int,
        agent: AgentEntity?
    ): Int = calculate(weeklyWage, contractYears, agent).commission
}

/**
 * 佣金计算明细（用于 UI 展示与单测）。
 */
data class CommissionBreakdown(
    /** 最终佣金金额 */
    val commission: Int,
    /** 年化工资（周薪 × 52） */
    val annualWage: Int,
    /** 综合佣金率 */
    val commissionRate: Double,
    /** 经纪人风格调整（含贪婪度属性修正） */
    val agentStyleAdjust: Double,
    /** 经纪人风格 */
    val agentStyle: String,
    /** 经纪人姓名 */
    val agentName: String?
) {
    /** 格式化展示 */
    override fun toString(): String =
        "佣金 $commission = 年薪 $annualWage × 费率 ${(commissionRate * 100).toInt()}%" +
            (if (agentName != null) "（$agentName / $agentStyle）" else "（无经纪人）")
}
