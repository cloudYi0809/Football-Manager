package com.greendynasty.football.transfer.negotiation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 报价轮次表（save.db）
 *
 * 记录谈判每一轮的提议方、条款、反应。
 * 一个谈判会话可有多轮（最多 [NegotiationSessionEntity.maxRounds] 轮）。
 *
 * V0.1 09 §四/§五/§六 多轮谈判历史载体：
 * - 玩家出价 → 卖方还价 → 玩家再出价 …
 * - 经纪人还价 → 玩家调整 → 经纪人再还价 …
 */
@Entity(
    tableName = "offer_round",
    foreignKeys = [
        ForeignKey(
            entity = com.greendynasty.football.data.save.entity.SaveTransferOfferEntity::class,
            parentColumns = ["offer_id"],
            childColumns = ["offer_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["offer_id", "round_number"])]
)
data class OfferRoundEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "round_id")
    val roundId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "offer_id")
    val offerId: Int,

    /** 第几轮（1 起） */
    @ColumnInfo(name = "round_number")
    val roundNumber: Int,

    /** 轮次类型：TRANSFER_NEGOTIATION / CONTRACT_NEGOTIATION */
    @ColumnInfo(name = "round_type")
    val roundType: String = "TRANSFER_NEGOTIATION",

    /** 提议方：BUYER / SELLER / PLAYER */
    @ColumnInfo(name = "proposer")
    val proposer: String,

    /** 本轮转会费 */
    @ColumnInfo(name = "fee")
    val fee: Int,

    /** 本轮周薪 */
    @ColumnInfo(name = "wage")
    val wage: Int,

    /** 合同年限 */
    @ColumnInfo(name = "contract_years")
    val contractYears: Int,

    /** 签字费 */
    @ColumnInfo(name = "signing_bonus")
    val signingBonus: Int,

    /** 经纪人佣金 */
    @ColumnInfo(name = "agent_commission")
    val agentCommission: Int,

    /** 角色承诺 RolePromise.name */
    @ColumnInfo(name = "role_promise")
    val rolePromise: String,

    /** 反应 Reaction.name */
    @ColumnInfo(name = "reaction")
    val reaction: String,

    /** 反应文案 */
    @ColumnInfo(name = "reaction_message")
    val reactionMessage: String,

    @ColumnInfo(name = "created_date")
    val createdDate: String
)
