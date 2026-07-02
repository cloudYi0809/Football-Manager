package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 转会报价表（save.db）
 * 记录存档中的转会/租借报价，包括报价金额、工资、合同年限、状态等。
 */
@Entity(
    tableName = "save_transfer_offer",
    indices = [
        Index(value = ["save_id", "status"]),
        Index(value = ["save_id", "player_id"])
    ]
)
data class SaveTransferOfferEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "offer_id")
    val offerId: Int = 0, // 报价自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "player_id")
    val playerId: Int, // 目标球员 ID

    @ColumnInfo(name = "from_club_id")
    val fromClubId: Int?, // 卖方俱乐部 ID

    @ColumnInfo(name = "to_club_id")
    val toClubId: Int?, // 买方俱乐部 ID

    @ColumnInfo(name = "offer_type")
    val offerType: String, // 报价类型：transfer / loan / free

    @ColumnInfo(name = "fee")
    val fee: Int = 0, // 转会费

    @ColumnInfo(name = "wage_offer")
    val wageOffer: Int = 0, // 提供周薪

    @ColumnInfo(name = "contract_years")
    val contractYears: Int?, // 合同年限

    @ColumnInfo(name = "status")
    val status: String = "pending", // 状态：pending / accepted / rejected / completed

    @ColumnInfo(name = "created_date")
    val createdDate: String, // 报价创建日期

    @ColumnInfo(name = "expires_date")
    val expiresDate: String?, // 报价截止日期

    // —— T11 报价谈判扩展字段（带默认值，兼容 T10 既有调用）——
    @ColumnInfo(name = "negotiation_type")
    val negotiationType: String = "PERMANENT", // OfferType.name：PERMANENT/LOAN/LOAN_WITH_BUYOUT/FREE_SIGNING/PRE_CONTRACT

    @ColumnInfo(name = "signing_bonus")
    val signingBonus: Int = 0, // 签字费

    @ColumnInfo(name = "agent_commission")
    val agentCommission: Int = 0, // 经纪人佣金

    @ColumnInfo(name = "role_promise")
    val rolePromise: String? = null, // RolePromise.name：KEY_PLAYER/STARTER/ROTATION/BACKUP/ACADEMY_DEV

    @ColumnInfo(name = "current_round")
    val currentRound: Int = 0, // 当前谈判轮次

    @ColumnInfo(name = "psychological_price")
    val psychologicalPrice: Int = 0 // 缓存卖方心理价位（避免每轮重算）
)
