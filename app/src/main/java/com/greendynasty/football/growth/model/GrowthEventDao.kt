package com.greendynasty.football.growth.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 成长事件数据访问对象（save.db，T09 方案 §三.7）
 */
@Dao
interface GrowthEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GrowthEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<GrowthEventEntity>): List<Long>

    /** 观察球员成长事件流（按日期倒序） */
    @Query("SELECT * FROM growth_event WHERE save_id = :saveId AND player_id = :playerId ORDER BY trigger_date DESC")
    fun observeByPlayer(saveId: Int, playerId: Int): Flow<List<GrowthEventEntity>>

    /** 查询球员成长事件 */
    @Query("SELECT * FROM growth_event WHERE save_id = :saveId AND player_id = :playerId ORDER BY trigger_date DESC")
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<GrowthEventEntity>

    /** 查询俱乐部近期事件 */
    @Query("SELECT * FROM growth_event WHERE save_id = :saveId AND club_id = :clubId ORDER BY trigger_date DESC LIMIT :limit")
    suspend fun getByClubRecent(saveId: Int, clubId: Int, limit: Int): List<GrowthEventEntity>

    /** 查询同月同类型事件（去重判定用） */
    @Query("SELECT * FROM growth_event WHERE save_id = :saveId AND player_id = :playerId AND trigger_date = :date AND event_type = :type LIMIT 1")
    suspend fun getByPlayerDateType(saveId: Int, playerId: Int, date: String, type: String): GrowthEventEntity?

    /** 统计球员近期同类型事件数 */
    @Query("SELECT COUNT(*) FROM growth_event WHERE save_id = :saveId AND player_id = :playerId AND event_type = :type AND trigger_date >= :startDate")
    suspend fun countRecentType(saveId: Int, playerId: Int, type: String, startDate: String): Int

    /** 标记事件已读 */
    @Query("UPDATE growth_event SET is_read = 1 WHERE event_id IN (:ids)")
    suspend fun markRead(ids: List<Int>): Int
}
