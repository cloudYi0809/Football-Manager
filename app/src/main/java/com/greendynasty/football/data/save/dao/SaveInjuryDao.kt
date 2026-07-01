package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveInjuryEntity

/**
 * 球员伤病数据访问对象（save.db）
 * 提供伤病记录的 CRUD，支持按球员、状态查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，活跃伤病列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveInjuryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(injury: SaveInjuryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(injuries: List<SaveInjuryEntity>)

    @Update
    suspend fun update(injury: SaveInjuryEntity)

    @Delete
    suspend fun delete(injury: SaveInjuryEntity)

    @Query("SELECT * FROM save_injury WHERE injury_id = :injuryId")
    suspend fun get(injuryId: Int): SaveInjuryEntity?

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId AND player_id = :playerId AND status = 'active'")
    suspend fun getActiveByPlayer(saveId: Int, playerId: Int): List<SaveInjuryEntity>

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId AND status = 'active' ORDER BY expected_return_date")
    fun observeAllActive(saveId: Int): kotlinx.coroutines.flow.Flow<List<SaveInjuryEntity>>

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId AND status = 'active' ORDER BY expected_return_date")
    suspend fun getAllActive(saveId: Int): List<SaveInjuryEntity>

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId ORDER BY start_date DESC")
    suspend fun getAll(saveId: Int): List<SaveInjuryEntity>

    @Query("UPDATE save_injury SET status = :status WHERE injury_id = :injuryId")
    suspend fun updateStatus(injuryId: Int, status: String)

    @Query("DELETE FROM save_injury WHERE save_id = :saveId AND status = 'recovered'")
    suspend fun deleteRecovered(saveId: Int)
}
