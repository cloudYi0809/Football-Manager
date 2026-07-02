package com.greendynasty.football.scouting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * T14 球探雇佣记录 DAO（save.db）。
 *
 * 协程规范：写操作与单次查询使用 suspend，球探列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveScoutHiredDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hired: SaveScoutHiredEntity): Long

    @Update
    suspend fun update(hired: SaveScoutHiredEntity)

    @Query("SELECT * FROM scout_hired WHERE hired_id = :hiredId")
    suspend fun get(hiredId: Int): SaveScoutHiredEntity?

    @Query("SELECT * FROM scout_hired WHERE save_id = :saveId AND club_id = :clubId AND status != 'RELEASED' ORDER BY hired_date DESC")
    fun observeByClub(saveId: Int, clubId: Int): Flow<List<SaveScoutHiredEntity>>

    @Query("SELECT * FROM scout_hired WHERE save_id = :saveId AND club_id = :clubId AND status != 'RELEASED' ORDER BY hired_date DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<SaveScoutHiredEntity>

    @Query("SELECT * FROM scout_hired WHERE save_id = :saveId AND scout_id = :scoutId AND club_id = :clubId AND status != 'RELEASED' LIMIT 1")
    suspend fun getByScout(saveId: Int, scoutId: Int, clubId: Int): SaveScoutHiredEntity?

    @Query("SELECT COUNT(*) FROM scout_hired WHERE save_id = :saveId AND club_id = :clubId AND status != 'RELEASED'")
    suspend fun countByClub(saveId: Int, clubId: Int): Int

    @Query("UPDATE scout_hired SET status = :status WHERE save_id = :saveId AND hired_id = :hiredId")
    suspend fun updateStatus(saveId: Int, hiredId: Int, status: String)

    @Query("UPDATE scout_hired SET status = :status, morale = :morale WHERE save_id = :saveId AND hired_id = :hiredId")
    suspend fun updateStatusAndMorale(saveId: Int, hiredId: Int, status: String, morale: Int)

    @Query("UPDATE scout_hired SET wage = :wage, contract_expire_date = :expireDate WHERE save_id = :saveId AND hired_id = :hiredId")
    suspend fun renewContract(saveId: Int, hiredId: Int, wage: Int, expireDate: String)
}

/**
 * T14 球探地区知识 DAO（save.db，V0.2 08 §三.2）。
 */
@Dao
interface SaveScoutRegionKnowledgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(knowledge: SaveScoutRegionKnowledgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(knowledge: List<SaveScoutRegionKnowledgeEntity>)

    @Query("SELECT * FROM scout_region_knowledge WHERE save_id = :saveId AND hired_id = :hiredId")
    suspend fun getByHired(saveId: Int, hiredId: Int): List<SaveScoutRegionKnowledgeEntity>

    @Query("SELECT * FROM scout_region_knowledge WHERE save_id = :saveId AND hired_id = :hiredId AND region_code = :regionCode LIMIT 1")
    suspend fun get(saveId: Int, hiredId: Int, regionCode: String): SaveScoutRegionKnowledgeEntity?

    @Query("UPDATE scout_region_knowledge SET knowledge_value = :value WHERE save_id = :saveId AND hired_id = :hiredId AND region_code = :regionCode")
    suspend fun updateValue(saveId: Int, hiredId: Int, regionCode: String, value: Int)

    @Query("DELETE FROM scout_region_knowledge WHERE save_id = :saveId AND hired_id = :hiredId")
    suspend fun deleteByHired(saveId: Int, hiredId: Int)
}

/**
 * T14 球探任务 DAO（save.db，V0.2 08 §三.4）。
 */
@Dao
interface SaveScoutTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: SaveScoutTaskEntity): Long

    @Update
    suspend fun update(task: SaveScoutTaskEntity)

    @Query("SELECT * FROM scout_task WHERE task_id = :taskId")
    suspend fun get(taskId: Int): SaveScoutTaskEntity?

    @Query("SELECT * FROM scout_task WHERE save_id = :saveId AND status = 'IN_PROGRESS' ORDER BY start_date DESC")
    suspend fun getInProgress(saveId: Int): List<SaveScoutTaskEntity>

    @Query("SELECT * FROM scout_task WHERE save_id = :saveId AND club_id = :clubId ORDER BY start_date DESC")
    fun observeByClub(saveId: Int, clubId: Int): Flow<List<SaveScoutTaskEntity>>

    @Query("SELECT * FROM scout_task WHERE save_id = :saveId AND club_id = :clubId AND status IN ('PENDING','IN_PROGRESS') ORDER BY start_date DESC")
    fun observeActiveByClub(saveId: Int, clubId: Int): Flow<List<SaveScoutTaskEntity>>

    @Query("SELECT * FROM scout_task WHERE save_id = :saveId AND club_id = :clubId AND status IN ('PENDING','IN_PROGRESS')")
    suspend fun getActiveByClub(saveId: Int, clubId: Int): List<SaveScoutTaskEntity>

    @Query("SELECT COUNT(*) FROM scout_task WHERE save_id = :saveId AND club_id = :clubId AND status IN ('PENDING','IN_PROGRESS')")
    suspend fun countActiveByClub(saveId: Int, clubId: Int): Int

    @Query("UPDATE scout_task SET status = :status, last_report_date = :date WHERE save_id = :saveId AND task_id = :taskId")
    suspend fun updateStatus(saveId: Int, taskId: Int, status: String, date: String)

    @Query("UPDATE scout_task SET elapsed_days = :elapsed, last_report_date = :date, report_count = report_count + :reportInc WHERE save_id = :saveId AND task_id = :taskId")
    suspend fun updateElapsed(saveId: Int, taskId: Int, elapsed: Int, date: String, reportInc: Int = 0)

    @Query("UPDATE scout_task SET status = 'CANCELLED' WHERE save_id = :saveId AND hired_id = :hiredId AND status = 'IN_PROGRESS'")
    suspend fun cancelTasksByHired(saveId: Int, hiredId: Int)
}

/**
 * T14 球探报告 DAO（save.db，V0.2 08 §四）。
 */
@Dao
interface SaveScoutReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: SaveScoutReportEntity): Long

    @Update
    suspend fun update(report: SaveScoutReportEntity)

    @Query("SELECT * FROM scout_report_detail WHERE report_id = :reportId")
    suspend fun get(reportId: Int): SaveScoutReportEntity?

    @Query("SELECT * FROM scout_report_detail WHERE save_id = :saveId AND player_id = :playerId AND club_id = :clubId LIMIT 1")
    suspend fun getByPlayer(saveId: Int, playerId: Int, clubId: Int): SaveScoutReportEntity?

    @Query("SELECT * FROM scout_report_detail WHERE save_id = :saveId AND task_id = :taskId ORDER BY created_date DESC")
    suspend fun getByTask(saveId: Int, taskId: Int): List<SaveScoutReportEntity>

    @Query("SELECT * FROM scout_report_detail WHERE save_id = :saveId AND club_id = :clubId ORDER BY created_date DESC LIMIT :limit")
    fun observeRecent(saveId: Int, clubId: Int, limit: Int): Flow<List<SaveScoutReportEntity>>

    @Query("SELECT * FROM scout_report_detail WHERE save_id = :saveId AND club_id = :clubId ORDER BY created_date DESC")
    fun observeByClub(saveId: Int, clubId: Int): Flow<List<SaveScoutReportEntity>>

    @Query("SELECT * FROM scout_report_detail WHERE save_id = :saveId AND club_id = :clubId ORDER BY created_date DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<SaveScoutReportEntity>

    @Query("UPDATE scout_report_detail SET observation_days = :days, last_updated_date = :date WHERE save_id = :saveId AND report_id = :reportId")
    suspend fun incrementObservationDays(saveId: Int, reportId: Int, days: Int, date: String)

    @Query("UPDATE scout_report_detail SET scout_recommendation = :level WHERE save_id = :saveId AND report_id = :reportId")
    suspend fun updateRecommendation(saveId: Int, reportId: Int, level: Int)

    @Query("SELECT COUNT(*) FROM scout_report_detail WHERE save_id = :saveId AND task_id = :taskId")
    suspend fun countByTask(saveId: Int, taskId: Int): Int
}

/**
 * T14 球探事件 DAO（save.db，V0.2 08 §七）。
 */
@Dao
interface SaveScoutEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SaveScoutEventEntity): Long

    @Query("SELECT * FROM scout_event WHERE save_id = :saveId ORDER BY event_date DESC LIMIT :limit")
    fun observeRecent(saveId: Int, limit: Int): Flow<List<SaveScoutEventEntity>>

    @Query("SELECT * FROM scout_event WHERE save_id = :saveId AND hired_id = :hiredId ORDER BY event_date DESC")
    suspend fun getByHired(saveId: Int, hiredId: Int): List<SaveScoutEventEntity>

    @Query("UPDATE scout_event SET read = 1 WHERE save_id = :saveId AND event_id = :eventId")
    suspend fun markRead(saveId: Int, eventId: Int)

    @Query("SELECT COUNT(*) FROM scout_event WHERE save_id = :saveId AND read = 0")
    suspend fun countUnread(saveId: Int): Int
}
