package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.greendynasty.football.data.save.entity.SaveScheduleStateEntity

/**
 * 存档赛程生成状态数据访问对象（save.db）
 *
 * 用于查询某存档某赛季的赛程是否已生成、生成时间等。每个 (save_id, season_id) 唯一。
 *
 * T06 新增。
 */
@Dao
interface SaveScheduleStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SaveScheduleStateEntity): Long

    @Query("SELECT * FROM save_schedule_state WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun get(saveId: Int, seasonId: Int): SaveScheduleStateEntity?

    @Query("SELECT * FROM save_schedule_state WHERE save_id = :saveId ORDER BY season_id DESC")
    suspend fun getBySave(saveId: Int): List<SaveScheduleStateEntity>

    @Query("DELETE FROM save_schedule_state WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun delete(saveId: Int, seasonId: Int)
}
