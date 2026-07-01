package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.ScoutReportEntity

/**
 * 球探报告数据访问对象（save.db）
 * 提供球探报告的 CRUD，支持按球员、球探查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，最新报告列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface ScoutReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ScoutReportEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<ScoutReportEntity>)

    @Update
    suspend fun update(report: ScoutReportEntity)

    @Delete
    suspend fun delete(report: ScoutReportEntity)

    @Query("SELECT * FROM scout_report WHERE report_id = :reportId")
    suspend fun get(reportId: Int): ScoutReportEntity?

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId AND player_id = :playerId ORDER BY report_date DESC")
    fun observeByPlayer(saveId: Int, playerId: Int): kotlinx.coroutines.flow.Flow<List<ScoutReportEntity>>

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId AND player_id = :playerId ORDER BY report_date DESC")
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<ScoutReportEntity>

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId AND scout_id = :scoutId ORDER BY report_date DESC")
    suspend fun getByScout(saveId: Int, scoutId: Int): List<ScoutReportEntity>

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId AND assignment_id = :assignmentId ORDER BY report_date DESC")
    suspend fun getByAssignment(saveId: Int, assignmentId: Int): List<ScoutReportEntity>

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId ORDER BY report_date DESC LIMIT :limit")
    fun observeRecent(saveId: Int, limit: Int): kotlinx.coroutines.flow.Flow<List<ScoutReportEntity>>

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId ORDER BY report_date DESC LIMIT :limit")
    suspend fun getRecent(saveId: Int, limit: Int): List<ScoutReportEntity>

    @Query("SELECT * FROM scout_report WHERE save_id = :saveId ORDER BY report_date DESC")
    suspend fun getAll(saveId: Int): List<ScoutReportEntity>

    @Query("SELECT COUNT(*) FROM scout_report WHERE save_id = :saveId")
    suspend fun count(saveId: Int): Int
}
