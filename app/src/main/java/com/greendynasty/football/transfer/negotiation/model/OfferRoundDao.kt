package com.greendynasty.football.transfer.negotiation.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 报价轮次数据访问对象（save.db）
 *
 * 提供谈判每一轮提议的 CRUD，支持按报价查询全部历史轮次。
 */
@Dao
interface OfferRoundDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(round: OfferRoundEntity): Long

    @Query("SELECT * FROM offer_round WHERE save_id = :saveId AND offer_id = :offerId ORDER BY round_number ASC")
    suspend fun getByOffer(saveId: Int, offerId: Int): List<OfferRoundEntity>

    @Query("SELECT MAX(round_number) FROM offer_round WHERE save_id = :saveId AND offer_id = :offerId")
    suspend fun getMaxRound(saveId: Int, offerId: Int): Int?

    @Query("SELECT COUNT(*) FROM offer_round WHERE save_id = :saveId AND offer_id = :offerId")
    suspend fun count(saveId: Int, offerId: Int): Int
}
