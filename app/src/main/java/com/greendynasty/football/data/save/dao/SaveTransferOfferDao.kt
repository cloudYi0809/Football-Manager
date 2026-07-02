package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveTransferOfferEntity

/**
 * 转会报价数据访问对象（save.db）
 * 提供转会报价的 CRUD，支持按状态、球员、俱乐部查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，待处理报价列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveTransferOfferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(offer: SaveTransferOfferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<SaveTransferOfferEntity>)

    @Update
    suspend fun update(offer: SaveTransferOfferEntity)

    @Delete
    suspend fun delete(offer: SaveTransferOfferEntity)

    @Query("SELECT * FROM save_transfer_offer WHERE offer_id = :offerId")
    suspend fun get(offerId: Int): SaveTransferOfferEntity?

    @Query("SELECT * FROM save_transfer_offer WHERE save_id = :saveId AND status = :status ORDER BY created_date DESC")
    fun observeByStatus(saveId: Int, status: String): kotlinx.coroutines.flow.Flow<List<SaveTransferOfferEntity>>

    @Query("SELECT * FROM save_transfer_offer WHERE save_id = :saveId AND status = :status ORDER BY created_date DESC")
    suspend fun getByStatus(saveId: Int, status: String): List<SaveTransferOfferEntity>

    @Query("SELECT * FROM save_transfer_offer WHERE save_id = :saveId AND player_id = :playerId ORDER BY created_date DESC")
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<SaveTransferOfferEntity>

    @Query("SELECT * FROM save_transfer_offer WHERE save_id = :saveId AND (from_club_id = :clubId OR to_club_id = :clubId) ORDER BY created_date DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<SaveTransferOfferEntity>

    @Query("SELECT * FROM save_transfer_offer WHERE save_id = :saveId ORDER BY created_date DESC")
    suspend fun getAll(saveId: Int): List<SaveTransferOfferEntity>

    @Query("UPDATE save_transfer_offer SET status = :status WHERE offer_id = :offerId")
    suspend fun updateStatus(offerId: Int, status: String)

    @Query("SELECT COUNT(*) FROM save_transfer_offer WHERE save_id = :saveId AND status = 'pending'")
    suspend fun countPending(saveId: Int): Int

    // T19 赛季归档：清理已完成/已拒绝且超出保留期的报价
    @Query(
        "DELETE FROM save_transfer_offer WHERE save_id = :saveId " +
            "AND status IN ('completed', 'rejected') AND created_date < :beforeDate"
    )
    suspend fun deleteCompletedAndExpired(saveId: Int, beforeDate: String): Int
}
