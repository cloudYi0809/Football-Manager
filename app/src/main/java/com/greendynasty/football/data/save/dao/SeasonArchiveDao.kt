package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SeasonArchiveEntity

/**
 * 赛季归档数据访问对象（save.db，V0.2）
 * 提供赛季归档记录的 CRUD，用于历史赛季数据查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，归档列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SeasonArchiveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(archive: SeasonArchiveEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(archives: List<SeasonArchiveEntity>)

    @Update
    suspend fun update(archive: SeasonArchiveEntity)

    @Delete
    suspend fun delete(archive: SeasonArchiveEntity)

    @Query("SELECT * FROM season_archive WHERE archive_id = :archiveId")
    suspend fun get(archiveId: Int): SeasonArchiveEntity?

    @Query("SELECT * FROM season_archive WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun getBySeason(saveId: String, seasonId: Int): List<SeasonArchiveEntity>

    @Query("SELECT * FROM season_archive WHERE save_id = :saveId ORDER BY season_id DESC")
    fun observeAll(saveId: String): kotlinx.coroutines.flow.Flow<List<SeasonArchiveEntity>>

    @Query("SELECT * FROM season_archive WHERE save_id = :saveId ORDER BY season_id DESC")
    suspend fun getAll(saveId: String): List<SeasonArchiveEntity>

    @Query("SELECT * FROM season_archive WHERE save_id = :saveId AND archive_type = :type ORDER BY season_id DESC")
    suspend fun getByType(saveId: String, type: String): List<SeasonArchiveEntity>

    @Query("SELECT COUNT(*) FROM season_archive WHERE save_id = :saveId")
    suspend fun count(saveId: String): Int
}
