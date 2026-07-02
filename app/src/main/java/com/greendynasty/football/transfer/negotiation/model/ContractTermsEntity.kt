package com.greendynasty.football.transfer.negotiation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 合同条款表（save.db）
 *
 * V0.1 09 §六 完整合同条款：
 * - 9 项基础条款：周薪/年限/签字费/佣金/出场奖金/进球奖金/助攻奖金/零封奖金/忠诚奖金
 * - 7 项特殊条款：解约金/降级解约/欧冠涨薪/年度涨薪/续约选项/回购/二次分成
 * - 5 档角色承诺：[RolePromise]
 *
 * 一个报价对应一份合同条款（玩家最终接受的版本）。
 */
@Entity(
    tableName = "contract_terms",
    foreignKeys = [
        ForeignKey(
            entity = com.greendynasty.football.data.save.entity.SaveTransferOfferEntity::class,
            parentColumns = ["offer_id"],
            childColumns = ["offer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["offer_id"])]
)
data class ContractTermsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "terms_id")
    val termsId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "offer_id")
    val offerId: Int,

    // —— 基础条款 9 项 ——
    @ColumnInfo(name = "weekly_wage")
    val weeklyWage: Int,

    @ColumnInfo(name = "contract_years")
    val contractYears: Int,

    @ColumnInfo(name = "signing_bonus")
    val signingBonus: Int,

    @ColumnInfo(name = "agent_commission")
    val agentCommission: Int,

    @ColumnInfo(name = "appearance_bonus")
    val appearanceBonus: Int = 0,

    @ColumnInfo(name = "goal_bonus")
    val goalBonus: Int = 0,

    @ColumnInfo(name = "assist_bonus")
    val assistBonus: Int = 0,

    @ColumnInfo(name = "clean_sheet_bonus")
    val cleanSheetBonus: Int = 0,

    @ColumnInfo(name = "loyalty_bonus")
    val loyaltyBonus: Int = 0,

    // —— 特殊条款 7 项（可空）——
    @ColumnInfo(name = "release_clause")
    val releaseClause: Int? = null,

    @ColumnInfo(name = "relegation_release_clause")
    val relegationReleaseClause: Int? = null,

    @ColumnInfo(name = "ucl_raise_percent")
    val uclRaisePercent: Int? = null,

    @ColumnInfo(name = "annual_raise_percent")
    val annualRaisePercent: Int? = null,

    @ColumnInfo(name = "contract_extension_option")
    val contractExtensionOption: Boolean = false,

    @ColumnInfo(name = "buyback_clause")
    val buybackClause: Int? = null,

    @ColumnInfo(name = "sell_on_percent")
    val sellOnPercent: Int? = null,

    // —— 角色承诺 ——
    @ColumnInfo(name = "role_promise")
    val rolePromise: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
