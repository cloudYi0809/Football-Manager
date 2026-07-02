package com.greendynasty.football.transfer.negotiation.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 谈判会话数据访问对象（save.db）
 *
 * 提供谈判会话的 CRUD，支持按报价 / 阶段查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，活跃会话列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface NegotiationSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: NegotiationSessionEntity): Long

    @Update
    suspend fun update(session: NegotiationSessionEntity)

    @Query("SELECT * FROM negotiation_session WHERE session_id = :sessionId")
    suspend fun get(sessionId: Int): NegotiationSessionEntity?

    @Query("SELECT * FROM negotiation_session WHERE save_id = :saveId AND offer_id = :offerId LIMIT 1")
    suspend fun getByOffer(saveId: Int, offerId: Int): NegotiationSessionEntity?

    @Query("SELECT * FROM negotiation_session WHERE save_id = :saveId AND player_id = :playerId ORDER BY started_date DESC LIMIT 1")
    suspend fun getLatestByPlayer(saveId: Int, playerId: Int): NegotiationSessionEntity?

    @Query("SELECT * FROM negotiation_session WHERE save_id = :saveId AND is_collapsed = 0 ORDER BY last_updated_date DESC")
    suspend fun getActive(saveId: Int): List<NegotiationSessionEntity>

    @Query("UPDATE negotiation_session SET stage = :stage, current_round = :round, last_updated_date = :date WHERE session_id = :sessionId")
    suspend fun updateStageAndRound(sessionId: Int, stage: String, round: Int, date: String)

    @Query("UPDATE negotiation_session SET buyer_patience = :buyer, seller_patience = :seller, player_patience = :player WHERE session_id = :sessionId")
    suspend fun updatePatience(sessionId: Int, buyer: Int, seller: Int, player: Int)

    @Query("UPDATE negotiation_session SET is_collapsed = 1, stage = :stage WHERE session_id = :sessionId")
    suspend fun markCollapsed(sessionId: Int, stage: String)

    @Query("UPDATE negotiation_session SET cached_psychological_price = :price, cached_expected_wage = :wage WHERE session_id = :sessionId")
    suspend fun updateCachedPrice(sessionId: Int, price: Int, wage: Int)
}
