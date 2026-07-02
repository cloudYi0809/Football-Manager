package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.greendynasty.football.data.save.entity.CompressedMatchEntity

/**
 * 压缩比赛事件数据访问对象（save.db，V0.2 §七.2）
 *
 * 提供压缩后比赛事件的读写，用于赛季归档（写入）与历史查询（读取）。
 *
 * 协程规范：写操作与单次查询使用 suspend。
 *
 * T19 新增。
 */
@Dao
interface CompressedMatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CompressedMatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CompressedMatchEntity>)

    @Query("SELECT * FROM compressed_match WHERE match_id = :matchId LIMIT 1")
    suspend fun getByMatch(matchId: Int): CompressedMatchEntity?

    @Query("SELECT * FROM compressed_match WHERE save_id = :saveId AND season_id = :seasonId ORDER BY match_id")
    suspend fun getBySeason(saveId: Int, seasonId: Int): List<CompressedMatchEntity>

    @Query("SELECT COUNT(*) FROM compressed_match WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun countBySeason(saveId: Int, seasonId: Int): Int

    @Query("DELETE FROM compressed_match WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun deleteBySeason(saveId: Int, seasonId: Int)
}
