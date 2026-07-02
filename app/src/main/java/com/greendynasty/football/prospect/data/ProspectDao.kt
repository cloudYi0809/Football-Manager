package com.greendynasty.football.prospect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * T15 历史新星存档状态 DAO（save.db，V0.2 08 §三 + 06 §二.1）。
 *
 * 协程规范：写操作与单次查询使用 suspend，新星列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface ProspectStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: ProspectStateEntity): Long

    @Update
    suspend fun update(state: ProspectStateEntity)

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND prospect_id = :prospectId LIMIT 1")
    suspend fun get(saveId: Int, prospectId: Int): ProspectStateEntity?

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND player_id = :playerId LIMIT 1")
    suspend fun getByPlayer(saveId: Int, playerId: Int): ProspectStateEntity?

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId ORDER BY activated_date DESC")
    fun observeAll(saveId: Int): Flow<List<ProspectStateEntity>>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId ORDER BY activated_date DESC")
    suspend fun getAll(saveId: Int): List<ProspectStateEntity>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND status = :status ORDER BY activated_date DESC")
    suspend fun getByStatus(saveId: Int, status: String): List<ProspectStateEntity>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND status IN ('ACTIVE','DISCOVERED','DEFAULT_PATH','SIGNED_EARLY') ORDER BY activated_date DESC")
    fun observeActiveAndDiscovered(saveId: Int): Flow<List<ProspectStateEntity>>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND region_code = :regionCode AND status = 'ACTIVE'")
    suspend fun getActiveUndiscoveredInRegion(saveId: Int, regionCode: String): List<ProspectStateEntity>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND region_code = :regionCode AND status = 'ACTIVE'")
    fun observeActiveUndiscoveredInRegion(saveId: Int, regionCode: String): Flow<List<ProspectStateEntity>>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND status = 'ACTIVE'")
    suspend fun getActiveUndiscovered(saveId: Int): List<ProspectStateEntity>

    @Query("SELECT * FROM prospect_state WHERE save_id = :saveId AND status IN ('DISCOVERED','DEFAULT_PATH','SIGNED_EARLY')")
    suspend fun getDiscovered(saveId: Int): List<ProspectStateEntity>

    @Query("UPDATE prospect_state SET status = :status WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun updateStatus(saveId: Int, prospectId: Int, status: String)

    @Query("UPDATE prospect_state SET discovered_by_club_id = :clubId, discovered_date = :date, status = 'DISCOVERED' WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun markDiscovered(saveId: Int, prospectId: Int, clubId: Int, date: String)

    @Query("UPDATE prospect_state SET current_path = :path WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun updateCurrentPath(saveId: Int, prospectId: Int, path: String)

    @Query("UPDATE prospect_state SET last_path_event_date = :date WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun updateLastPathEventDate(saveId: Int, prospectId: Int, date: String)

    @Query("UPDATE prospect_state SET butterfly_triggered = 1, butterfly_event_id = :eventId WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun markButterflyTriggered(saveId: Int, prospectId: Int, eventId: String)

    @Query("UPDATE prospect_state SET current_ca = :ca, current_pa = :pa, current_club_id = :clubId WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun updateSnapshot(saveId: Int, prospectId: Int, ca: Int, pa: Int, clubId: Int?)

    @Query("SELECT COUNT(*) FROM prospect_state WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun exists(saveId: Int, prospectId: Int): Int
}

/**
 * T15 历史新星路径事件 DAO（save.db，V0.2 08 §三）。
 */
@Dao
interface ProspectPathEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ProspectPathEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<ProspectPathEventEntity>)

    @Query("SELECT * FROM prospect_path_event WHERE save_id = :saveId AND prospect_id = :prospectId ORDER BY event_date ASC, event_id ASC")
    suspend fun getByProspect(saveId: Int, prospectId: Int): List<ProspectPathEventEntity>

    @Query("SELECT * FROM prospect_path_event WHERE save_id = :saveId AND prospect_id = :prospectId ORDER BY event_date ASC, event_id ASC")
    fun observeByProspect(saveId: Int, prospectId: Int): Flow<List<ProspectPathEventEntity>>

    @Query("SELECT * FROM prospect_path_event WHERE save_id = :saveId ORDER BY event_date DESC LIMIT :limit")
    fun observeRecent(saveId: Int, limit: Int): Flow<List<ProspectPathEventEntity>>

    @Query("SELECT * FROM prospect_path_event WHERE save_id = :saveId AND event_type = :type ORDER BY event_date DESC")
    suspend fun getByType(saveId: Int, type: String): List<ProspectPathEventEntity>

    @Query("SELECT COUNT(*) FROM prospect_path_event WHERE save_id = :saveId AND prospect_id = :prospectId AND event_type = 'TRANSFER' AND to_club_id = :toClubId")
    suspend fun countTransferTo(saveId: Int, prospectId: Int, toClubId: Int): Int

    @Query("SELECT COUNT(*) FROM prospect_path_event WHERE save_id = :saveId AND prospect_id = :prospectId")
    suspend fun countByProspect(saveId: Int, prospectId: Int): Int
}
