package com.greendynasty.football.transfer.contract.viewmodel

import com.greendynasty.football.transfer.contract.model.ContractReminderEntity
import com.greendynasty.football.transfer.contract.model.ContractRenewalEntity
import com.greendynasty.football.transfer.contract.model.RenewalContext
import com.greendynasty.football.transfer.contract.wage.WageBreakdown
import com.greendynasty.football.transfer.negotiation.model.RolePromise

/**
 * T12 合同续约 UI 状态集合（V0.1 `09_转会_合同_经纪人系统.md` §六）。
 */

/**
 * 续约表单 UI 状态（玩家编辑条款）。
 */
data class RenewalFormState(
    val playerId: Int = 0,
    val playerName: String = "",
    val weeklyWage: String = "",
    val contractYears: Int = 3,
    val signingBonus: String = "0",
    val rolePromise: RolePromise = RolePromise.STARTER,
    val releaseClause: String = "",
    val releaseClauseEnabled: Boolean = false,
    // 续约特有 3 项条款（可空，玩家勾选启用）
    val performanceRaiseEnabled: Boolean = false,
    val performanceRaisePercent: String = "10",
    val veteranClauseEnabled: Boolean = false,
    val academyProtectionEnabled: Boolean = false,
    /** 球员要求（由 WageCalculator 算出，作为建议值） */
    val demandsWage: Int = 0,
    val demandsMaxYears: Int = 0,
    /** 期望工资（5 因子公式算出） */
    val expectedWage: Int = 0,
    /** 当前工资 */
    val currentWage: Int = 0,
    /** 合同剩余月数 */
    val monthsRemaining: Int = 0,
    /** 续约意愿 0-1 */
    val willingness: Double = 0.0,
    /** 工资分解（用于 UI 展示 5 因子） */
    val wageBreakdown: WageBreakdown? = null
) {
    /** 工资整数 */
    fun wageInt(): Int = weeklyWage.toIntOrNull() ?: 0
    /** 签字费整数 */
    fun signingBonusInt(): Int = signingBonus.toIntOrNull() ?: 0
    /** 违约金整数 */
    fun releaseClauseInt(): Int? = if (releaseClauseEnabled) releaseClause.toIntOrNull() else null
}

/**
 * 续约谈判页面 UI 状态。
 */
sealed class ContractRenewalUiState {
    /** 空闲（无续约进行中） */
    object Idle : ContractRenewalUiState()

    /** 加载中 */
    object Loading : ContractRenewalUiState()

    /** 续约已发起，等待玩家提交报价 */
    data class Initiated(
        val renewalId: Int,
        val demandsWage: Int,
        val demandsMaxYears: Int,
        val expectedWage: Int,
        val willingness: Double,
        val wageBreakdown: WageBreakdown,
        val monthsRemaining: Int
    ) : ContractRenewalUiState()

    /** 球员还价 */
    data class PlayerCountered(
        val renewalId: Int,
        val counterWeeklyWage: Int,
        val counterContractYears: Int,
        val counterSigningBonus: Int,
        val counterAgentCommission: Int,
        val message: String,
        val willingness: Double
    ) : ContractRenewalUiState()

    /** 续约完成 */
    data class Completed(
        val renewalId: Int,
        val newWage: Int,
        val newContractUntil: String,
        val newSquadRole: String,
        val wageChangePercent: Double,
        val message: String
    ) : ContractRenewalUiState()

    /** 续约破裂 / 拒绝 / 撤回 */
    data class Failed(val renewalId: Int, val reason: String) : ContractRenewalUiState()

    /** 错误 */
    data class Error(val message: String) : ContractRenewalUiState()
}

/**
 * 到期预警列表项（UI 展示用）。
 */
data class ReminderItem(
    val reminder: ContractReminderEntity,
    val playerName: String,
    val playerPosition: String?,
    val playerAge: Int,
    val currentWage: Int,
    val squadRole: String?
)

/**
 * 续约记录列表项（UI 展示用）。
 */
data class RenewalHistoryItem(
    val renewal: ContractRenewalEntity,
    val playerName: String
)
