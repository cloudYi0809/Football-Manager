package com.greendynasty.football.transfer.negotiation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 谈判会话表（save.db）
 *
 * 记录一次报价的多轮谈判状态：当前阶段、轮次、各方耐心等。
 * 一个 [com.greendynasty.football.data.save.entity.SaveTransferOfferEntity] 对应一个会话。
 *
 * V0.1 09 §三 12 步流程的会话载体：
 * - 卖方评估 → 卖方谈判 → 球员意愿 → 球员谈判 → 体检 → 完成
 * - 多轮拉锯支持（最多 [maxRounds] 轮）
 * - 耐心机制：每轮扣减，归零自动破裂
 */
@Entity(
    tableName = "negotiation_session",
    indices = [
        Index(value = ["save_id", "offer_id"]),
        Index(value = ["save_id", "stage"])
    ]
)
data class NegotiationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "offer_id")
    val offerId: Int, // 关联 save_transfer_offer.offer_id

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "agent_id")
    val agentId: Int? = null, // 经纪人 ID（无经纪人为 null）

    @ColumnInfo(name = "stage")
    val stage: String = NegotiationStage.SELLER_EVALUATION.name,

    @ColumnInfo(name = "current_round")
    val currentRound: Int = 0,

    @ColumnInfo(name = "max_rounds")
    val maxRounds: Int = 5,

    /** 买方耐心 0-100 */
    @ColumnInfo(name = "buyer_patience")
    val buyerPatience: Int = 100,

    /** 卖方耐心 0-100 */
    @ColumnInfo(name = "seller_patience")
    val sellerPatience: Int = 100,

    /** 球员耐心 0-100 */
    @ColumnInfo(name = "player_patience")
    val playerPatience: Int = 100,

    /** 关系影响累计（破裂时扣） */
    @ColumnInfo(name = "relationship_impact")
    val relationshipImpact: Int = 0,

    /** 缓存的心理价位（避免每轮重算） */
    @ColumnInfo(name = "cached_psychological_price")
    val cachedPsychologicalPrice: Int = 0,

    /** 缓存的期望工资（避免每轮重算） */
    @ColumnInfo(name = "cached_expected_wage")
    val cachedExpectedWage: Int = 0,

    @ColumnInfo(name = "started_date")
    val startedDate: String,

    @ColumnInfo(name = "last_updated_date")
    val lastUpdatedDate: String,

    @ColumnInfo(name = "is_collapsed")
    val isCollapsed: Boolean = false
)
