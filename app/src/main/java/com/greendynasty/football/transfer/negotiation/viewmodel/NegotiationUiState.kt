package com.greendynasty.football.transfer.negotiation.viewmodel

import com.greendynasty.football.transfer.negotiation.engine.CounterOffer
import com.greendynasty.football.transfer.negotiation.engine.PlayerDecision
import com.greendynasty.football.transfer.negotiation.engine.SellerDecision
import com.greendynasty.football.transfer.negotiation.estimator.PlayerValuation
import com.greendynasty.football.transfer.negotiation.model.ContractTermsEntity
import com.greendynasty.football.transfer.negotiation.model.NegotiationSessionEntity
import com.greendynasty.football.transfer.negotiation.model.OfferRoundEntity
import com.greendynasty.football.transfer.negotiation.model.OfferStatus
import com.greendynasty.football.transfer.negotiation.model.OfferType
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import com.greendynasty.football.data.save.entity.SaveTransferOfferEntity

/**
 * T11 报价谈判 UI 状态集合。
 */

/** 报价表单 UI 状态 */
data class OfferFormState(
    val playerId: Int = 0,
    val playerName: String = "",
    val offerType: OfferType = OfferType.PERMANENT,
    val transferFee: String = "",
    val wageOffer: String = "",
    val contractYears: Int = 4,
    val signingBonus: String = "0",
    val agentCommission: String = "0",
    val rolePromise: RolePromise = RolePromise.ROTATION,
    /** 建议值（来自估价） */
    val suggestedFee: Int = 0,
    val suggestedWage: Int = 0,
    val psychologicalPrice: Int = 0,
    val expectedWage: Int = 0
) {
    /** 转会费整数 */
    fun feeInt(): Int = transferFee.toIntOrNull() ?: 0
    /** 工资整数 */
    fun wageInt(): Int = wageOffer.toIntOrNull() ?: 0
    /** 签字费整数 */
    fun signingBonusInt(): Int = signingBonus.toIntOrNull() ?: 0
    /** 佣金整数 */
    fun agentCommissionInt(): Int = agentCommission.toIntOrNull() ?: 0
}

/** 谈判页面 UI 状态 */
sealed class NegotiationUiState {
    /** 空闲 */
    object Idle : NegotiationUiState()

    /** 加载中 */
    object Loading : NegotiationUiState()

    /** 报价已提交，等待卖方评估 */
    data class Submitted(val offerId: Int, val valuation: PlayerValuation) : NegotiationUiState()

    /** 卖方反应 */
    data class SellerReaction(
        val offerId: Int,
        val decision: SellerDecision,
        val valuation: PlayerValuation,
        val session: NegotiationSessionEntity,
        val rounds: List<OfferRoundEntity>
    ) : NegotiationUiState()

    /** 球员个人条款谈判 */
    data class PersonalTerms(
        val offerId: Int,
        val offer: SaveTransferOfferEntity,
        val valuation: PlayerValuation
    ) : NegotiationUiState()

    /** 球员反应 */
    data class PlayerReaction(
        val offerId: Int,
        val decision: PlayerDecision,
        val valuation: PlayerValuation
    ) : NegotiationUiState()

    /** 转会完成 */
    data class Completed(val offerId: Int, val summary: String) : NegotiationUiState()

    /** 谈判破裂 / 拒绝 / 撤回 */
    data class Failed(val offerId: Int, val reason: String) : NegotiationUiState()

    /** 错误 */
    data class Error(val message: String) : NegotiationUiState()
}

/** 合同条款表单 UI 状态 */
data class ContractTermsFormState(
    val weeklyWage: String = "",
    val contractYears: Int = 4,
    val signingBonus: String = "0",
    val agentCommission: String = "0",
    val appearanceBonus: String = "0",
    val goalBonus: String = "0",
    val assistBonus: String = "0",
    val cleanSheetBonus: String = "0",
    val loyaltyBonus: String = "0",
    // 特殊条款
    val releaseClauseEnabled: Boolean = false,
    val releaseClause: String = "",
    val relegationReleaseEnabled: Boolean = false,
    val relegationRelease: String = "",
    val uclRaiseEnabled: Boolean = false,
    val uclRaisePercent: String = "10",
    val annualRaiseEnabled: Boolean = false,
    val annualRaisePercent: String = "5",
    val extensionOption: Boolean = false,
    val buybackEnabled: Boolean = false,
    val buybackClause: String = "",
    val sellOnEnabled: Boolean = false,
    val sellOnPercent: String = "10",
    val rolePromise: RolePromise = RolePromise.ROTATION
) {
    fun toEntity(saveId: Int, offerId: Int): ContractTermsEntity = ContractTermsEntity(
        saveId = saveId,
        offerId = offerId,
        weeklyWage = weeklyWage.toIntOrNull() ?: 0,
        contractYears = contractYears,
        signingBonus = signingBonus.toIntOrNull() ?: 0,
        agentCommission = agentCommission.toIntOrNull() ?: 0,
        appearanceBonus = appearanceBonus.toIntOrNull() ?: 0,
        goalBonus = goalBonus.toIntOrNull() ?: 0,
        assistBonus = assistBonus.toIntOrNull() ?: 0,
        cleanSheetBonus = cleanSheetBonus.toIntOrNull() ?: 0,
        loyaltyBonus = loyaltyBonus.toIntOrNull() ?: 0,
        releaseClause = if (releaseClauseEnabled) releaseClause.toIntOrNull() else null,
        relegationReleaseClause = if (relegationReleaseEnabled) relegationRelease.toIntOrNull() else null,
        uclRaisePercent = if (uclRaiseEnabled) uclRaisePercent.toIntOrNull() else null,
        annualRaisePercent = if (annualRaiseEnabled) annualRaisePercent.toIntOrNull() else null,
        contractExtensionOption = extensionOption,
        buybackClause = if (buybackEnabled) buybackClause.toIntOrNull() else null,
        sellOnPercent = if (sellOnEnabled) sellOnPercent.toIntOrNull() else null,
        rolePromise = rolePromise.name
    )
}
