package com.greendynasty.football.transfer.contract.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 合同续约报价数据访问对象（save.db）。
 *
 * 提供续约报价的 CRUD，支持按球员、俱乐部、状态查询。
 * 协程规范：写操作与单次查询使用 suspend，活跃报价列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface ContractRenewalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(renewal: ContractRenewalEntity): Long

    @Update
    suspend fun update(renewal: ContractRenewalEntity)

    @Query("SELECT * FROM contract_renewal WHERE renewal_id = :renewalId")
    suspend fun get(renewalId: Int): ContractRenewalEntity?

    @Query("SELECT * FROM contract_renewal WHERE save_id = :saveId AND player_id = :playerId ORDER BY created_date DESC")
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<ContractRenewalEntity>

    @Query("SELECT * FROM contract_renewal WHERE save_id = :saveId AND player_id = :playerId AND status = 'COMPLETED' ORDER BY created_date DESC LIMIT 1")
    suspend fun getLastCompletedByPlayer(saveId: Int, playerId: Int): ContractRenewalEntity?

    @Query("SELECT * FROM contract_renewal WHERE save_id = :saveId AND club_id = :clubId AND status IN (:statuses) ORDER BY created_date DESC")
    suspend fun getByClubAndStatus(
        saveId: Int,
        clubId: Int,
        statuses: List<String>
    ): List<ContractRenewalEntity>

    @Query("SELECT * FROM contract_renewal WHERE save_id = :saveId AND club_id = :clubId AND status IN (:statuses) ORDER BY created_date DESC")
    fun observeByClubAndStatus(
        saveId: Int,
        clubId: Int,
        statuses: List<String>
    ): Flow<List<ContractRenewalEntity>>

    @Query("SELECT * FROM contract_renewal WHERE save_id = :saveId AND club_id = :clubId ORDER BY created_date DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<ContractRenewalEntity>

    @Query("SELECT * FROM contract_renewal WHERE save_id = :saveId AND player_id = :playerId AND status IN ('SUBMITTED','PLAYER_EVALUATING','PLAYER_COUNTERED') ORDER BY created_date DESC LIMIT 1")
    suspend fun getActiveByPlayer(saveId: Int, playerId: Int): ContractRenewalEntity?

    @Query("UPDATE contract_renewal SET status = :status WHERE renewal_id = :renewalId")
    suspend fun updateStatus(renewalId: Int, status: String)

    @Query("DELETE FROM contract_renewal WHERE save_id = :saveId AND status IN ('COMPLETED','COLLAPSED','WITHDRAWN','EXPIRED','PLAYER_REJECTED') AND created_date < :beforeDate")
    suspend fun deleteOldTerminal(saveId: Int, beforeDate: String)
}

/**
 * 合同到期提醒数据访问对象（save.db）。
 */
@Dao
interface ContractReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ContractReminderEntity): Long

    @Update
    suspend fun update(reminder: ContractReminderEntity)

    @Query("SELECT * FROM contract_reminder WHERE reminder_id = :reminderId")
    suspend fun get(reminderId: Int): ContractReminderEntity?

    @Query("SELECT * FROM contract_reminder WHERE save_id = :saveId AND club_id = :clubId AND is_handled = 0 ORDER BY months_remaining ASC")
    suspend fun getActiveByClub(saveId: Int, clubId: Int): List<ContractReminderEntity>

    @Query("SELECT * FROM contract_reminder WHERE save_id = :saveId AND club_id = :clubId AND is_handled = 0 ORDER BY months_remaining ASC")
    fun observeActiveByClub(saveId: Int, clubId: Int): Flow<List<ContractReminderEntity>>

    @Query("SELECT * FROM contract_reminder WHERE save_id = :saveId AND player_id = :playerId AND is_handled = 0 ORDER BY trigger_date DESC LIMIT 1")
    suspend fun getLatestByPlayer(saveId: Int, playerId: Int): ContractReminderEntity?

    @Query("SELECT * FROM contract_reminder WHERE save_id = :saveId AND player_id = :playerId AND level = :level AND is_handled = 0 LIMIT 1")
    suspend fun getActiveByPlayerAndLevel(
        saveId: Int,
        playerId: Int,
        level: String
    ): ContractReminderEntity?

    @Query("SELECT * FROM contract_reminder WHERE save_id = :saveId AND is_handled = 0 AND months_remaining <= :threshold ORDER BY months_remaining ASC")
    suspend fun getCriticalReminders(saveId: Int, threshold: Int): List<ContractReminderEntity>

    @Query("UPDATE contract_reminder SET is_handled = 1 WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun markHandledByPlayer(saveId: Int, playerId: Int)

    @Query("UPDATE contract_reminder SET is_handled = 1 WHERE reminder_id = :reminderId")
    suspend fun markHandled(reminderId: Int)

    @Query("DELETE FROM contract_reminder WHERE save_id = :saveId AND is_handled = 1 AND trigger_date < :beforeDate")
    suspend fun deleteOldHandled(saveId: Int, beforeDate: String)
}
