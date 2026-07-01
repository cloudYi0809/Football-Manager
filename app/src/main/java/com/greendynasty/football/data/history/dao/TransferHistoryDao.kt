package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 历史转会记录数据访问对象（history.db 只读）
 * 提供历史转会记录查询，用于对比存档中的转会是否改变了历史。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface TransferHistoryDao {

    // 按 transfer_id 查询单条转会记录
    @Query("SELECT * FROM transfer_history WHERE transfer_id = :transferId")
    suspend fun getTransfer(transferId: Int): TransferHistoryEntity?

    // 查询某球员的全部转会记录（按日期排序）
    @Query("SELECT * FROM transfer_history WHERE player_id = :playerId ORDER BY transfer_date")
    fun getTransfersByPlayer(playerId: Int): Flow<List<TransferHistoryEntity>>

    // 查询某赛季的全部转会记录（按日期排序）
    @Query("SELECT * FROM transfer_history WHERE season_id = :seasonId ORDER BY transfer_date")
    fun getTransfersBySeason(seasonId: Int): Flow<List<TransferHistoryEntity>>

    // 按日期范围查询转会记录
    @Transaction
    @Query("SELECT * FROM transfer_history WHERE transfer_date BETWEEN :startDate AND :endDate ORDER BY transfer_date")
    fun getTransfersByDateRange(startDate: String, endDate: String): Flow<List<TransferHistoryEntity>>

    // 查询某俱乐部相关的全部转会（转入或转出）
    @Transaction
    @Query("SELECT * FROM transfer_history WHERE to_club_id = :clubId OR from_club_id = :clubId ORDER BY transfer_date DESC")
    fun getTransfersByClub(clubId: Int): Flow<List<TransferHistoryEntity>>

    // 转会记录总数
    @Query("SELECT COUNT(*) FROM transfer_history")
    suspend fun count(): Int
}
