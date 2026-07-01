package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 球员伤病数据访问对象（save.db）
 * 提供伤病记录的 CRUD，支持按球员、状态、俱乐部查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，活跃伤病列表使用 Flow 观察以驱动 UI 刷新。
 *
 * T08 扩展：新增按俱乐部查询活跃伤病、球员伤病历史、恢复进度批量更新、
 * 永久影响结算标记、区间伤病计数等接口，支撑伤病系统全流程。
 */
@Dao
interface SaveInjuryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(injury: SaveInjuryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(injuries: List<SaveInjuryEntity>)

    @Update
    suspend fun update(injury: SaveInjuryEntity)

    @Delete
    suspend fun delete(injury: SaveInjuryEntity)

    @Query("SELECT * FROM save_injury WHERE injury_id = :injuryId")
    suspend fun get(injuryId: Int): SaveInjuryEntity?

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId AND player_id = :playerId AND status = 'active'")
    suspend fun getActiveByPlayer(saveId: Int, playerId: Int): List<SaveInjuryEntity>

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId AND status = 'active' ORDER BY expected_return_date")
    fun observeAllActive(saveId: Int): Flow<List<SaveInjuryEntity>>

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId AND status = 'active' ORDER BY expected_return_date")
    suspend fun getAllActive(saveId: Int): List<SaveInjuryEntity>

    @Query("SELECT * FROM save_injury WHERE save_id = :saveId ORDER BY start_date DESC")
    suspend fun getAll(saveId: Int): List<SaveInjuryEntity>

    @Query("UPDATE save_injury SET status = :status WHERE injury_id = :injuryId")
    suspend fun updateStatus(injuryId: Int, status: String)

    @Query("DELETE FROM save_injury WHERE save_id = :saveId AND status = 'recovered'")
    suspend fun deleteRecovered(saveId: Int)

    // ==================== T08 新增接口 ====================

    /** 查询球员当前活跃伤病（含 active/recovering/returned_early/recurred），最多 1 条 */
    @Query(
        "SELECT * FROM save_injury WHERE save_id = :saveId AND player_id = :playerId " +
            "AND status IN ('active','recovering','returned_early','recurred') " +
            "ORDER BY start_date DESC LIMIT 1"
    )
    suspend fun getActiveInjury(saveId: Int, playerId: Int): SaveInjuryEntity?

    /** 查询俱乐部全部活跃伤病（按预计复出日期升序） */
    @Query(
        "SELECT * FROM save_injury WHERE save_id = :saveId AND club_id = :clubId " +
            "AND status IN ('active','recovering','returned_early','recurred') " +
            "ORDER BY expected_return_date ASC"
    )
    suspend fun getActiveInjuriesByClub(saveId: Int, clubId: Int): List<SaveInjuryEntity>

    /** 观察俱乐部活跃伤病（Flow 驱动 UI） */
    @Query(
        "SELECT * FROM save_injury WHERE save_id = :saveId AND club_id = :clubId " +
            "AND status IN ('active','recovering','returned_early','recurred') " +
            "ORDER BY expected_return_date ASC"
    )
    fun observeActiveInjuries(saveId: Int, clubId: Int): Flow<List<SaveInjuryEntity>>

    /** 球员伤病历史（含已恢复，按开始日期倒序） */
    @Query(
        "SELECT * FROM save_injury WHERE save_id = :saveId AND player_id = :playerId " +
            "ORDER BY start_date DESC"
    )
    suspend fun getPlayerInjuryHistory(saveId: Int, playerId: Int): List<SaveInjuryEntity>

    /** 区间内俱乐部伤病发生次数（用于赛季统计 / Gate 2 验收） */
    @Query(
        "SELECT COUNT(*) FROM save_injury WHERE save_id = :saveId AND club_id = :clubId " +
            "AND start_date BETWEEN :startDate AND :endDate"
    )
    suspend fun countInjuriesInPeriod(
        saveId: Int, clubId: Int, startDate: String, endDate: String
    ): Int

    /** 球员重伤及以上历史（用于永久影响 / 复发评估） */
    @Query(
        "SELECT * FROM save_injury WHERE save_id = :saveId AND player_id = :playerId " +
            "AND severity IN (3, 4) AND status = 'recovered' ORDER BY start_date DESC"
    )
    suspend fun getMajorInjuryHistory(saveId: Int, playerId: Int): List<SaveInjuryEntity>

    /** 更新恢复进度（T07 每日推进调用） */
    @Query(
        "UPDATE save_injury SET recovery_progress = :progress, recovery_elapsed_days = :elapsed, " +
            "status = :status WHERE injury_id = :injuryId"
    )
    suspend fun updateRecoveryProgress(injuryId: Int, progress: Int, elapsed: Int, status: String)

    /** 更新状态并回填实际复出日 */
    @Query(
        "UPDATE save_injury SET status = :status, actual_return_date = :returnDate " +
            "WHERE injury_id = :injuryId"
    )
    suspend fun updateStatusAndReturn(injuryId: Int, status: String, returnDate: String?)

    /** 标记永久影响已结算 */
    @Query(
        "UPDATE save_injury SET permanent_impact_applied = 1 WHERE injury_id = :injuryId"
    )
    suspend fun markPermanentImpactEvaluated(injuryId: Int)

    /** 按严重度统计已恢复伤病数（赛季统计） */
    @Query(
        "SELECT COUNT(*) FROM save_injury WHERE save_id = :saveId AND club_id = :clubId " +
            "AND severity = :severity AND status = 'recovered'"
    )
    suspend fun countRecoveredBySeverity(saveId: Int, clubId: Int, severity: Int): Int
}
