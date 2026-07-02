package com.greendynasty.football.divergence.archive

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T21 分歧归档数据访问对象（save.db，任务 T21.4：分歧归档 + 查询）。
 *
 * 提供归档记录的写入与查询接口：
 * - 赛季归档时批量写入
 * - 玩家查询历史分歧时按赛季 / 分类 / 时间筛选
 *
 * 协程规范：写操作与单次查询使用 suspend，归档列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface DivergenceArchiveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DivergenceArchiveEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DivergenceArchiveEntity>)

    @Query("SELECT * FROM divergence_archive WHERE archive_id = :archiveId")
    suspend fun get(archiveId: String): DivergenceArchiveEntity?

    @Query("SELECT * FROM divergence_archive WHERE save_id = :saveId ORDER BY trigger_date DESC")
    suspend fun getAll(saveId: String): List<DivergenceArchiveEntity>

    @Query("SELECT * FROM divergence_archive WHERE save_id = :saveId ORDER BY trigger_date DESC")
    fun observeAll(saveId: String): Flow<List<DivergenceArchiveEntity>>

    @Query("SELECT * FROM divergence_archive WHERE save_id = :saveId AND season_id = :seasonId ORDER BY trigger_date DESC")
    suspend fun getBySeason(saveId: String, seasonId: Int): List<DivergenceArchiveEntity>

    @Query("SELECT * FROM divergence_archive WHERE save_id = :saveId AND season_id = :seasonId ORDER BY trigger_date DESC")
    fun observeBySeason(saveId: String, seasonId: Int): Flow<List<DivergenceArchiveEntity>>

    @Query("SELECT * FROM divergence_archive WHERE save_id = :saveId AND category = :category ORDER BY trigger_date DESC")
    suspend fun getByCategory(saveId: String, category: String): List<DivergenceArchiveEntity>

    @Query("SELECT * FROM divergence_archive WHERE save_id = :saveId AND has_major_replacement = :hasReplacement ORDER BY trigger_date DESC")
    suspend fun getByReplacement(saveId: String, hasReplacement: Int): List<DivergenceArchiveEntity>

    @Query("SELECT COUNT(*) FROM divergence_archive WHERE save_id = :saveId")
    suspend fun countBySaveId(saveId: String): Int

    @Query("SELECT COUNT(*) FROM divergence_archive WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun countBySeason(saveId: String, seasonId: Int): Int

    @Query("SELECT COUNT(*) FROM divergence_archive WHERE save_id = :saveId AND has_major_replacement = 0")
    suspend fun countNoReplacement(saveId: String): Int

    @Query("SELECT COUNT(*) FROM divergence_archive WHERE save_id = :saveId AND has_major_replacement = 1")
    suspend fun countWithReplacement(saveId: String): Int

    @Query("SELECT DISTINCT season_id FROM divergence_archive WHERE save_id = :saveId ORDER BY season_id DESC")
    suspend fun getArchivedSeasons(saveId: String): List<Int>

    @Query("DELETE FROM divergence_archive WHERE save_id = :saveId")
    suspend fun deleteBySaveId(saveId: String)
}
