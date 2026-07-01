package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.PerfLogEntity

/**
 * 性能日志数据访问对象（save.db，V0.2）
 * 提供性能日志的 CRUD，用于长程性能监控。
 *
 * 协程规范：写操作与单次查询使用 suspend，日志列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface PerfLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PerfLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<PerfLogEntity>)

    @Update
    suspend fun update(log: PerfLogEntity)

    @Delete
    suspend fun delete(log: PerfLogEntity)

    @Query("SELECT * FROM perf_log WHERE id = :id")
    suspend fun get(id: Int): PerfLogEntity?

    @Query("SELECT * FROM perf_log WHERE save_id = :saveId ORDER BY log_date DESC LIMIT :limit")
    fun observeRecent(saveId: String, limit: Int): kotlinx.coroutines.flow.Flow<List<PerfLogEntity>>

    @Query("SELECT * FROM perf_log WHERE save_id = :saveId ORDER BY log_date DESC LIMIT :limit")
    suspend fun getRecent(saveId: String, limit: Int): List<PerfLogEntity>

    @Query("SELECT * FROM perf_log WHERE save_id = :saveId AND action_type = :actionType ORDER BY log_date DESC")
    suspend fun getByAction(saveId: String, actionType: String): List<PerfLogEntity>

    @Query("SELECT * FROM perf_log WHERE save_id = :saveId ORDER BY log_date DESC")
    suspend fun getAll(saveId: String): List<PerfLogEntity>

    @Query("SELECT AVG(duration_ms) FROM perf_log WHERE save_id = :saveId AND action_type = :actionType")
    suspend fun getAvgDuration(saveId: String, actionType: String): Double?

    @Query("SELECT MAX(db_size_mb) FROM perf_log WHERE save_id = :saveId")
    suspend fun getMaxDbSize(saveId: String): Double?

    @Query("DELETE FROM perf_log WHERE save_id = :saveId AND log_date < :beforeDate")
    suspend fun deleteOldLogs(saveId: String, beforeDate: String)

    @Query("SELECT COUNT(*) FROM perf_log WHERE save_id = :saveId")
    suspend fun count(saveId: String): Int
}
