package com.greendynasty.football.growth.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 月度出场时间数据访问对象（save.db，T09 方案 §三.7）
 */
@Dao
interface MonthlyPlayingTimeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MonthlyPlayingTimeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<MonthlyPlayingTimeEntity>)

    @Query("SELECT * FROM monthly_playing_time WHERE save_id = :saveId AND player_id = :playerId AND month = :month LIMIT 1")
    suspend fun get(saveId: Int, playerId: Int, month: String): MonthlyPlayingTimeEntity?

    @Query("SELECT * FROM monthly_playing_time WHERE save_id = :saveId AND club_id = :clubId AND month = :month")
    suspend fun getByClubMonth(saveId: Int, clubId: Int, month: String): List<MonthlyPlayingTimeEntity>

    @Query("DELETE FROM monthly_playing_time WHERE save_id = :saveId AND month < :beforeMonth")
    suspend fun deleteBeforeMonth(saveId: Int, beforeMonth: String): Int
}
