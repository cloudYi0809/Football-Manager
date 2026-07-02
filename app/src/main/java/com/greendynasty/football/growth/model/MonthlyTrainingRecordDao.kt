package com.greendynasty.football.growth.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 月度训练记录数据访问对象（save.db，T09 方案 §三.7）
 */
@Dao
interface MonthlyTrainingRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MonthlyTrainingRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<MonthlyTrainingRecordEntity>)

    @Query("SELECT * FROM monthly_training_record WHERE save_id = :saveId AND player_id = :playerId AND month = :month LIMIT 1")
    suspend fun get(saveId: Int, playerId: Int, month: String): MonthlyTrainingRecordEntity?

    @Query("SELECT * FROM monthly_training_record WHERE save_id = :saveId AND club_id = :clubId AND month = :month")
    suspend fun getByClubMonth(saveId: Int, clubId: Int, month: String): List<MonthlyTrainingRecordEntity>

    @Query("DELETE FROM monthly_training_record WHERE save_id = :saveId AND month < :beforeMonth")
    suspend fun deleteBeforeMonth(saveId: Int, beforeMonth: String): Int
}
