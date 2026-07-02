package com.greendynasty.football.growth.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 成长快照数据访问对象（save.db，T09 方案 §三.7）
 *
 * 提供月度快照的批量写入、按球员 / 赛季 / 日期查询、停滞月数统计等。
 * 唯一索引 (save_id, player_id, snapshot_date) 配合 REPLACE 策略保证幂等。
 */
@Dao
interface GrowthSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<GrowthSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: GrowthSnapshotEntity): Long

    /** 观察球员全部成长快照（按日期升序，供 T04 成长曲线） */
    @Query("SELECT * FROM growth_snapshot WHERE save_id = :saveId AND player_id = :playerId ORDER BY snapshot_date ASC")
    fun observeByPlayer(saveId: Int, playerId: Int): Flow<List<GrowthSnapshotEntity>>

    /** 查询球员全部成长快照 */
    @Query("SELECT * FROM growth_snapshot WHERE save_id = :saveId AND player_id = :playerId ORDER BY snapshot_date ASC")
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<GrowthSnapshotEntity>

    /** 查询球员某赛季快照 */
    @Query("SELECT * FROM growth_snapshot WHERE save_id = :saveId AND player_id = :playerId AND season_id = :seasonId ORDER BY snapshot_date ASC")
    suspend fun getByPlayerSeason(saveId: Int, playerId: Int, seasonId: Int): List<GrowthSnapshotEntity>

    /** 查询球员最新快照 */
    @Query("SELECT * FROM growth_snapshot WHERE save_id = :saveId AND player_id = :playerId ORDER BY snapshot_date DESC LIMIT 1")
    suspend fun getLatest(saveId: Int, playerId: Int): GrowthSnapshotEntity?

    /** 查询某赛季某日期全部球员快照（T19 赛季归档用） */
    @Query("SELECT * FROM growth_snapshot WHERE save_id = :saveId AND season_id = :seasonId AND snapshot_date = :date")
    suspend fun getBySeasonDate(saveId: Int, seasonId: Int, date: String): List<GrowthSnapshotEntity>

    /** 统计球员在指定日期后 CA 增长低于阈值的月数（停滞检测） */
    @Query("SELECT COUNT(*) FROM growth_snapshot WHERE save_id = :saveId AND player_id = :playerId AND snapshot_date >= :startDate AND ca_delta < :deltaThreshold")
    suspend fun countStagnationMonths(saveId: Int, playerId: Int, startDate: String, deltaThreshold: Int): Int

    /** 删除指定日期前的旧快照（保留策略） */
    @Query("DELETE FROM growth_snapshot WHERE save_id = :saveId AND snapshot_date < :beforeDate")
    suspend fun deleteBefore(saveId: Int, beforeDate: String): Int

    /** 查询俱乐部某月全部快照（月结报告） */
    @Query("SELECT * FROM growth_snapshot WHERE save_id = :saveId AND club_id = :clubId AND snapshot_date = :date ORDER BY ca_delta DESC")
    suspend fun getByClubDate(saveId: Int, clubId: Int, date: String): List<GrowthSnapshotEntity>
}
