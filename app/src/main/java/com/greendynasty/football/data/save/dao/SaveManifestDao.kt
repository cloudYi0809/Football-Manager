package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveManifestEntity

/**
 * 存档元数据数据访问对象（save.db）
 * 提供存档元数据的 CRUD 和更新操作（last_played_at、last_checkpoint_id、schema_version 等）。
 *
 * 协程规范：写操作与单次查询使用 suspend，列表查询使用 Flow。
 */
@Dao
interface SaveManifestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manifest: SaveManifestEntity)

    @Update
    suspend fun update(manifest: SaveManifestEntity)

    @Delete
    suspend fun delete(manifest: SaveManifestEntity)

    @Query("SELECT * FROM save_manifest WHERE save_id = :saveId")
    suspend fun get(saveId: String): SaveManifestEntity?

    @Query("SELECT * FROM save_manifest LIMIT 1")
    suspend fun get(): SaveManifestEntity?

    @Query("UPDATE save_manifest SET last_played_at = :timestamp WHERE save_id = :saveId")
    suspend fun updateLastPlayed(saveId: String, timestamp: String)

    @Query("UPDATE save_manifest SET last_checkpoint_id = :checkpointId WHERE save_id = :saveId")
    suspend fun updateLastCheckpoint(saveId: String, checkpointId: String)

    @Query("UPDATE save_manifest SET schema_version = :version WHERE save_id = :saveId")
    suspend fun updateSchemaVersion(saveId: String, version: Int)

    @Query("UPDATE save_manifest SET current_date = :date WHERE save_id = :saveId")
    suspend fun updateCurrentDate(saveId: String, date: String)

    @Query("SELECT COUNT(*) FROM save_manifest")
    suspend fun count(): Int
}
