package com.greendynasty.football.transfer.contract.repository

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.contract.config.ContractRenewalConfig
import com.greendynasty.football.transfer.contract.expiry.ContractExpiryService
import com.greendynasty.football.transfer.contract.expiry.ExpiryResult
import com.greendynasty.football.transfer.contract.model.ContractReminderEntity
import com.greendynasty.football.transfer.contract.model.ContractRenewalEntity
import com.greendynasty.football.transfer.contract.model.InitiationType
import com.greendynasty.football.transfer.contract.model.RenewalContext
import com.greendynasty.football.transfer.contract.model.RenewalSpecialTerms
import com.greendynasty.football.transfer.contract.model.RenewalStatus
import com.greendynasty.football.transfer.contract.negotiation.ContractRenewalService
import com.greendynasty.football.transfer.contract.negotiation.RenewalCompleteResult
import com.greendynasty.football.transfer.contract.negotiation.RenewalInitiateResult
import com.greendynasty.football.transfer.contract.negotiation.RenewalSubmitResult
import com.greendynasty.football.transfer.contract.wage.AgentCommissionCalculator
import com.greendynasty.football.transfer.contract.wage.WageBreakdown
import com.greendynasty.football.transfer.contract.wage.WageCalculator
import com.greendynasty.football.transfer.negotiation.config.NegotiationConfig
import com.greendynasty.football.transfer.negotiation.model.RolePromise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * T12 合同续约数据仓库（V0.1 `09_转会_合同_经纪人系统.md` §六 + §三）。
 *
 * 整合 [ContractRenewalService]（续约谈判）与 [ContractExpiryService]（到期处理），
 * 对外提供：发起续约 / 提交报价 / 接受还价 / 撤回 / 续约完成 /
 * 到期提醒检查 / 合同到期处理 / 自由球员释放 等能力。
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 *
 * 使用入口：
 * 1. T12 续约页（玩家发起续约）→ [initiateRenewal] / [submitRenewalOffer] / [acceptCounter] / [withdrawRenewal]
 * 2. T07 每日推进 → [checkReminders]（生成 12/6/1 月提醒）
 * 3. T07 月度推进 → [processExpiry]（Bosman 预合同 + 自由球员释放）
 * 4. 续约预警列表 → [observeActiveReminders]
 *
 * @param databaseManager 三库管理入口
 * @param saveId 当前存档 ID
 * @param clubId 玩家俱乐部 ID
 * @param config 续约配置
 * @param negotiationConfig T11 谈判配置（复用 PersonalTermsNegotiator）
 */
class ContractRepository(
    private val databaseManager: DatabaseManager,
    val saveId: Int = DEFAULT_SAVE_ID,
    val clubId: Int = DEFAULT_CLUB_ID,
    private val config: ContractRenewalConfig = ContractRenewalConfig.DEFAULT,
    private val negotiationConfig: NegotiationConfig = NegotiationConfig.DEFAULT
) {
    private val wageCalculator = WageCalculator(config)
    private val commissionCalculator = AgentCommissionCalculator(config)
    private val renewalService = ContractRenewalService(
        databaseManager, saveId, clubId, config, negotiationConfig,
        wageCalculator, commissionCalculator
    )
    private val expiryService = ContractExpiryService(databaseManager, saveId, clubId, config)

    // ==================== 1. 续约发起 / 报价 / 接受 / 撤回 ====================

    /**
     * 发起续约（V0.1 09 §六）。
     *
     * @param ctx 续约上下文
     * @param playerId 球员 ID
     * @param initiationType 触发类型
     * @param triggerReason 触发原因
     * @see ContractRenewalService.initiateRenewal
     */
    suspend fun initiateRenewal(
        ctx: RenewalContext,
        playerId: Int,
        initiationType: InitiationType = InitiationType.PLAYER_INITIATED,
        triggerReason: String? = null
    ): RenewalInitiateResult = renewalService.initiateRenewal(ctx, playerId, initiationType, triggerReason)

    /**
     * 玩家提交续约报价（V0.1 09 §六）。
     *
     * @param ctx 续约上下文
     * @param renewalId 续约报价 ID
     * @param weeklyWage 提议周薪
     * @param contractYears 提议年限
     * @param signingBonus 签字费
     * @param rolePromise 角色承诺
     * @param releaseClause 违约金（可空）
     * @param specialTerms 续约特有条款（可空）
     * @see ContractRenewalService.submitRenewalOffer
     */
    suspend fun submitRenewalOffer(
        ctx: RenewalContext,
        renewalId: Int,
        weeklyWage: Int,
        contractYears: Int,
        signingBonus: Int,
        rolePromise: RolePromise,
        releaseClause: Int? = null,
        specialTerms: RenewalSpecialTerms? = null
    ): RenewalSubmitResult = renewalService.submitRenewalOffer(
        ctx, renewalId, weeklyWage, contractYears, signingBonus,
        rolePromise, releaseClause, specialTerms
    )

    /**
     * 玩家接受球员还价（V0.1 09 §六）。
     *
     * @see ContractRenewalService.acceptCounter
     */
    suspend fun acceptCounter(
        ctx: RenewalContext,
        renewalId: Int,
        counterWeeklyWage: Int,
        counterContractYears: Int,
        counterSigningBonus: Int,
        counterAgentCommission: Int
    ): RenewalCompleteResult? = renewalService.acceptCounter(
        ctx, renewalId, counterWeeklyWage, counterContractYears,
        counterSigningBonus, counterAgentCommission
    )

    /**
     * 玩家撤回续约（V0.1 09 §六）。
     *
     * @see ContractRenewalService.withdrawRenewal
     */
    suspend fun withdrawRenewal(ctx: RenewalContext, renewalId: Int): Boolean =
        renewalService.withdrawRenewal(ctx, renewalId)

    // ==================== 2. 到期提醒 / 到期处理 ====================

    /**
     * 续约提醒检查（V0.1 09 §六，T07 每日推进调用）。
     *
     * @see ContractExpiryService.checkReminders
     */
    suspend fun checkReminders(ctx: RenewalContext): List<ContractReminderEntity> =
        expiryService.checkReminders(ctx)

    /**
     * 合同到期处理（V0.1 09 §三 + §六，T07 月度推进调用）。
     *
     * @see ContractExpiryService.processExpiry
     */
    suspend fun processExpiry(ctx: RenewalContext): List<ExpiryResult> =
        expiryService.processExpiry(ctx)

    /**
     * 释放球员为自由球员（V0.1 09 §六，衔接 T11 自由签约池）。
     *
     * @see ContractExpiryService.releaseAsFreeAgent
     */
    suspend fun releaseAsFreeAgent(ctx: RenewalContext, playerId: Int): ExpiryResult.Released? =
        expiryService.releaseAsFreeAgent(ctx, playerId)

    // ==================== 3. 查询方法 ====================

    /** 获取续约报价 */
    suspend fun getRenewal(renewalId: Int): ContractRenewalEntity? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        databaseManager.contractRenewalDao().get(renewalId)
    }

    /** 获取球员当前活跃续约 */
    suspend fun getActiveRenewalByPlayer(playerId: Int): ContractRenewalEntity? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        databaseManager.contractRenewalDao().getActiveByPlayer(saveId, playerId)
    }

    /** 获取球员历史续约记录 */
    suspend fun getRenewalsByPlayer(playerId: Int): List<ContractRenewalEntity> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()
        databaseManager.contractRenewalDao().getByPlayer(saveId, playerId)
    }

    /** 获取俱乐部下未处理的提醒（Flow，驱动 UI 刷新） */
    fun observeActiveReminders(): Flow<List<ContractReminderEntity>> =
        databaseManager.contractReminderDao().observeActiveByClub(saveId, clubId)

    /** 获取俱乐部下未处理的提醒（一次性查询） */
    suspend fun getActiveReminders(): List<ContractReminderEntity> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()
        databaseManager.contractReminderDao().getActiveByClub(saveId, clubId)
    }

    /** 获取俱乐部活跃续约（Flow，驱动 UI 刷新） */
    fun observeActiveRenewals(): Flow<List<ContractRenewalEntity>> =
        databaseManager.contractRenewalDao().observeByClubAndStatus(
            saveId, clubId, RenewalStatus.ACTIVE.map { it.name }
        )

    /** 获取俱乐部历史续约（一次性查询） */
    suspend fun getRenewalsByClub(): List<ContractRenewalEntity> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()
        databaseManager.contractRenewalDao().getByClub(saveId, clubId)
    }

    /** 标记提醒已处理（玩家点击忽略或处理） */
    suspend fun markReminderHandled(reminderId: Int) = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext
        databaseManager.contractReminderDao().markHandled(reminderId)
    }

    /** 获取球员存档状态 */
    suspend fun getPlayerState(playerId: Int): SavePlayerStateEntity? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
    }

    /**
     * 构建续约上下文（从游戏世界状态读取当前日期）。
     *
     * @param seasonId 当前赛季 ID
     */
    suspend fun buildContext(seasonId: Int): RenewalContext = withContext(Dispatchers.IO) {
        val currentDate = currentGameDate()
        RenewalContext(
            saveId = saveId,
            clubId = clubId,
            currentDate = currentDate,
            seasonId = seasonId
        )
    }

    // ==================== 内部工具 ====================

    /** save.db 是否就绪 */
    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    /** 当前游戏日期（从世界状态读取） */
    private suspend fun currentGameDate(): LocalDate {
        return runCatching {
            val dateStr = databaseManager.getSaveDatabaseOrNull()?.saveWorldStateDao()?.get()?.currentDate
            if (dateStr.isNullOrBlank()) LocalDate.now()
            else LocalDate.parse(dateStr.take(10))
        }.getOrElse { LocalDate.now() }
    }

    companion object {
        private const val TAG = "ContractRepository"
        private const val DEFAULT_SAVE_ID = 1
        private const val DEFAULT_CLUB_ID = 1
    }
}
