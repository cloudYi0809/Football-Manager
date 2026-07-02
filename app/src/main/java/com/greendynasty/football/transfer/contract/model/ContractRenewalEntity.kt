package com.greendynasty.football.transfer.contract.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 合同续约报价表（save.db）。
 *
 * 记录一次续约的完整状态：当前合同 / 提议条款 / 球员要求 / 续约意愿 / 状态机。
 * 一个 [com.greendynasty.football.data.save.entity.SavePlayerStateEntity] 在进行中可有多条历史报价，
 * 但同一时刻只能有一条 ACTIVE 状态的报价（由业务层校验）。
 *
 * T12.2 续约谈判简化为 1-2 回合：每条报价即一次谈判会话，
 * 谈判轮次通过 [currentRound] 字段追踪，避免单独建 rounds 表。
 */
@Entity(
    tableName = "contract_renewal",
    indices = [
        Index(value = ["save_id", "player_id"]),
        Index(value = ["save_id", "club_id", "status"]),
        Index(value = ["save_id", "status"])
    ]
)
data class ContractRenewalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "renewal_id")
    val renewalId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int, // 玩家俱乐部 ID

    // —— 触发信息 ——
    @ColumnInfo(name = "initiation_type")
    val initiationType: String, // InitiationType.name

    @ColumnInfo(name = "trigger_reason")
    val triggerReason: String? = null, // 触发原因

    // —— 当前合同（续约前快照）——
    @ColumnInfo(name = "current_wage")
    val currentWage: Int,

    @ColumnInfo(name = "current_contract_until")
    val currentContractUntil: String?,

    @ColumnInfo(name = "current_squad_role")
    val currentSquadRole: String? = null,

    // —— 提议条款（玩家提交）——
    @ColumnInfo(name = "proposed_wage")
    val proposedWage: Int = 0,

    @ColumnInfo(name = "proposed_contract_years")
    val proposedContractYears: Int = 0,

    @ColumnInfo(name = "proposed_signing_bonus")
    val proposedSigningBonus: Int = 0,

    @ColumnInfo(name = "proposed_agent_commission")
    val proposedAgentCommission: Int = 0,

    @ColumnInfo(name = "proposed_release_clause")
    val proposedReleaseClause: Int? = null, // 违约金

    @ColumnInfo(name = "role_promise")
    val rolePromise: String, // RolePromise.name（复用 T11）

    // —— 球员要求（由 WageCalculator 算出）——
    @ColumnInfo(name = "demands_wage")
    val demandsWage: Int = 0, // 球员要求工资

    @ColumnInfo(name = "demands_max_years")
    val demandsMaxYears: Int = 0, // 球员要求最长年限

    @ColumnInfo(name = "willingness_score")
    val willingnessScore: Double = 0.0, // 续约意愿 0-1

    // —— 状态机 ——
    @ColumnInfo(name = "status")
    val status: String = RenewalStatus.DRAFT.name,

    @ColumnInfo(name = "current_round")
    val currentRound: Int = 0, // 当前谈判轮次

    // —— 时间戳 ——
    @ColumnInfo(name = "created_date")
    val createdDate: String,

    @ColumnInfo(name = "expires_date")
    val expiresDate: String? = null, // 报价有效期

    @ColumnInfo(name = "completed_date")
    val completedDate: String? = null, // 续约完成日

    // —— 续约结果（COMPLETED 时填）——
    @ColumnInfo(name = "new_contract_until")
    val newContractUntil: String? = null, // 续约后到期日

    @ColumnInfo(name = "wage_change_percent")
    val wageChangePercent: Double = 0.0 // 工资变化百分比
)
