package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.ScoutAssignmentEntity

/**
 * 球探任务数据访问对象（save.db）
 * 提供球探任务的 CRUD，支持按球探、状态查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，活跃任务列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface ScoutAssignmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: ScoutAssignmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignments: List<ScoutAssignmentEntity>)

    @Update
    suspend fun update(assignment: ScoutAssignmentEntity)

    @Delete
    suspend fun delete(assignment: ScoutAssignmentEntity)

    @Query("SELECT * FROM scout_assignment WHERE assignment_id = :assignmentId")
    suspend fun get(assignmentId: Int): ScoutAssignmentEntity?

    @Query("SELECT * FROM scout_assignment WHERE save_id = :saveId AND scout_id = :scoutId ORDER BY start_date DESC")
    suspend fun getByScout(saveId: Int, scoutId: Int): List<ScoutAssignmentEntity>

    @Query("SELECT * FROM scout_assignment WHERE save_id = :saveId AND status = :status ORDER BY start_date DESC")
    fun observeByStatus(saveId: Int, status: String): kotlinx.coroutines.flow.Flow<List<ScoutAssignmentEntity>>

    @Query("SELECT * FROM scout_assignment WHERE save_id = :saveId AND status = :status ORDER BY start_date DESC")
    suspend fun getByStatus(saveId: Int, status: String): List<ScoutAssignmentEntity>

    @Query("SELECT * FROM scout_assignment WHERE save_id = :saveId ORDER BY start_date DESC")
    suspend fun getAll(saveId: Int): List<ScoutAssignmentEntity>

    @Query("UPDATE scout_assignment SET status = :status, progress = :progress WHERE assignment_id = :assignmentId")
    suspend fun updateProgress(assignmentId: Int, status: String, progress: Int)

    @Query("SELECT COUNT(*) FROM scout_assignment WHERE save_id = :saveId AND status = 'active'")
    suspend fun countActive(saveId: Int): Int
}
