package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.CheckpointEntity

/**
 * 存档检查点数据访问对象（save.db，V0.2）
 * 提供检查点的 CRUD，支持按类型、日期查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，检查点列表使用 Flow 观察以驱动 UI 刷新。
 * 支持四种类型：light / season / migration / user。
 */
@Dao
interface CheckpointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkpoint: CheckpointEntity)

    @Update
    suspend fun update(checkpoint: CheckpointEntity)

    @Delete
    suspend fun delete(checkpoint: CheckpointEntity)

    @Query("SELECT * FROM checkpoint WHERE checkpoint_id = :checkpointId")
    suspend fun getById(checkpointId: String): CheckpointEntity?

    @Query("SELECT * FROM checkpoint WHERE save_id = :saveId ORDER BY checkpoint_date DESC LIMIT 1")
    suspend fun getLatest(saveId: String): CheckpointEntity?

    @Query("SELECT * FROM checkpoint WHERE save_id = :saveId AND checkpoint_type = :type ORDER BY checkpoint_date DESC")
    fun observeByType(saveId: String, type: String): kotlinx.coroutines.flow.Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoint WHERE save_id = :saveId AND checkpoint_type = :type ORDER BY checkpoint_date DESC")
    suspend fun getByType(saveId: String, type: String): List<CheckpointEntity>

    @Query("SELECT * FROM checkpoint WHERE save_id = :saveId AND checkpoint_type = :type ORDER BY checkpoint_date DESC LIMIT :limit")
    suspend fun getLatestByType(saveId: String, type: String, limit: Int): List<CheckpointEntity>

    @Query("SELECT * FROM checkpoint WHERE save_id = :saveId ORDER BY checkpoint_date DESC")
    suspend fun getAll(saveId: String): List<CheckpointEntity>

    @Query("SELECT * FROM checkpoint WHERE save_id = :saveId AND checkpoint_type = :type ORDER BY checkpoint_date DESC LIMIT :offset, :count")
    suspend fun getOldByType(saveId: String, type: String, offset: Int, count: Int): List<CheckpointEntity>

    @Query("SELECT COUNT(*) FROM checkpoint WHERE save_id = :saveId")
    suspend fun count(saveId: String): Int
}
