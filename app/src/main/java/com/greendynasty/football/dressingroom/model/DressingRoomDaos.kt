package com.greendynasty.football.dressingroom.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T23 更衣室模块 DAO 集合（save.db）。
 *
 * 严格依据 `/Users/yi/Desktop/足球经理/开发文档/T23_更衣室_实现方案.md` §三.3 关键 DAO 示例，
 * 并按 T23 任务要求聚焦 5 张表。
 *
 * 协程规范：写操作与单次查询使用 suspend，列表查询使用 Flow 观察以驱动 UI 刷新。
 *
 * 共 5 个 DAO：
 * 1. [PlayerMoraleDao] - 球员士气 CRUD + 按俱乐部/球员查询 + 士气增量应用
 * 2. [PlayerChemistryDao] - 球员化学反应 CRUD + 按俱乐部/球员查询
 * 3. [DressingRoomAtmosphereDao] - 氛围快照写入 + 最近快照查询
 * 4. [DressingRoomLeaderDao] - 领袖 CRUD + 当前活跃领袖查询
 * 5. [PlayerEmotionEventDao] - 情绪事件 CRUD + 按类型/赛季查询 + 计数
 */

// ==================== 1. 球员士气 DAO ====================

@Dao
interface PlayerMoraleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayerMoraleEntity): Long

    @Query("SELECT * FROM player_morale WHERE save_id = :saveId AND player_id = :playerId LIMIT 1")
    suspend fun getByPlayer(saveId: Int, playerId: Int): PlayerMoraleEntity?

    @Query("SELECT * FROM player_morale WHERE save_id = :saveId AND club_id = :clubId ORDER BY morale ASC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<PlayerMoraleEntity>

    @Query("SELECT * FROM player_morale WHERE save_id = :saveId AND club_id = :clubId ORDER BY morale ASC")
    fun observeByClub(saveId: Int, clubId: Int): Flow<List<PlayerMoraleEntity>>

    @Query("""
        SELECT * FROM player_morale
        WHERE save_id = :saveId AND club_id = :clubId
        AND morale_level IN ('LOW','EXTREME_LOW')
        ORDER BY morale ASC
    """)
    suspend fun getUnhappyPlayers(saveId: Int, clubId: Int): List<PlayerMoraleEntity>

    @Query("SELECT AVG(morale) FROM player_morale WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun getAverageMorale(saveId: Int, clubId: Int): Double?

    @Query("""
        SELECT * FROM player_morale
        WHERE save_id = :saveId AND club_id = :clubId
        AND unrest_accumulator >= :threshold
        AND pending_conversation = 0
    """)
    suspend fun getPlayersNeedingTalk(saveId: Int, clubId: Int, threshold: Int): List<PlayerMoraleEntity>

    @Query("""
        UPDATE player_morale
        SET morale = :morale,
            morale_level = :level,
            last_updated_date = :date
        WHERE save_id = :saveId AND player_id = :playerId
    """)
    suspend fun updateMorale(saveId: Int, playerId: Int, morale: Int, level: String, date: String)

    @Query("""
        UPDATE player_morale
        SET unrest_accumulator = unrest_accumulator + :delta,
            pending_conversation = :pending
        WHERE save_id = :saveId AND player_id = :playerId
    """)
    suspend fun adjustUnrest(saveId: Int, playerId: Int, delta: Int, pending: Boolean)

    @Query("""
        UPDATE player_morale
        SET consecutive_starts = :starts, consecutive_benched = :benched
        WHERE save_id = :saveId AND player_id = :playerId
    """)
    suspend fun updateConsecutiveCounts(saveId: Int, playerId: Int, starts: Int, benched: Int)

    @Query("DELETE FROM player_morale WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun deleteByClub(saveId: Int, clubId: Int)
}

// ==================== 2. 球员化学反应 DAO ====================

@Dao
interface PlayerChemistryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayerChemistryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PlayerChemistryEntity>)

    @Query("SELECT * FROM player_chemistry WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun getByClub(saveId: Int, clubId: Int): List<PlayerChemistryEntity>

    @Query("""
        SELECT * FROM player_chemistry
        WHERE save_id = :saveId
        AND (player_a_id = :playerId OR player_b_id = :playerId)
    """)
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<PlayerChemistryEntity>

    @Query("SELECT AVG(chemistry_score) FROM player_chemistry WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun getClubChemistryAverage(saveId: Int, clubId: Int): Double?

    @Query("DELETE FROM player_chemistry WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun deleteByClub(saveId: Int, clubId: Int)
}

// ==================== 3. 更衣室氛围 DAO ====================

@Dao
interface DressingRoomAtmosphereDao {

    @Insert
    suspend fun insert(entity: DressingRoomAtmosphereEntity): Long

    @Query("""
        SELECT * FROM dressing_room_atmosphere
        WHERE save_id = :saveId AND club_id = :clubId
        ORDER BY snapshot_date DESC
        LIMIT :limit
    """)
    suspend fun getLatest(saveId: Int, clubId: Int, limit: Int = 12): List<DressingRoomAtmosphereEntity>

    @Query("""
        SELECT * FROM dressing_room_atmosphere
        WHERE save_id = :saveId AND club_id = :clubId
        ORDER BY snapshot_date DESC
        LIMIT :limit
    """)
    fun observeLatest(saveId: Int, clubId: Int, limit: Int = 12): Flow<List<DressingRoomAtmosphereEntity>>
}

// ==================== 4. 更衣室领袖 DAO ====================

@Dao
interface DressingRoomLeaderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DressingRoomLeaderEntity): Long

    @Query("""
        SELECT * FROM dressing_room_leader
        WHERE save_id = :saveId AND club_id = :clubId AND status = 'ACTIVE'
        ORDER BY leader_role
    """)
    suspend fun getActive(saveId: Int, clubId: Int): List<DressingRoomLeaderEntity>

    @Query("""
        SELECT * FROM dressing_room_leader
        WHERE save_id = :saveId AND club_id = :clubId AND status = 'ACTIVE'
        ORDER BY leader_role
    """)
    fun observeActive(saveId: Int, clubId: Int): Flow<List<DressingRoomLeaderEntity>>

    @Query("""
        SELECT * FROM dressing_room_leader
        WHERE save_id = :saveId AND club_id = :clubId AND leader_role = :role AND status = 'ACTIVE'
        LIMIT 1
    """)
    suspend fun getByRole(saveId: Int, clubId: Int, role: String): DressingRoomLeaderEntity?

    @Query("""
        UPDATE dressing_room_leader
        SET status = 'REVOKED', revoked_date = :date, revoked_reason = :reason
        WHERE id = :leaderId
    """)
    suspend fun revoke(leaderId: Long, date: String, reason: String)

    @Query("""
        UPDATE dressing_room_leader
        SET status = 'REVOKED', revoked_date = :date, revoked_reason = :reason
        WHERE save_id = :saveId AND club_id = :clubId AND status = 'ACTIVE'
    """)
    suspend fun revokeAll(saveId: Int, clubId: Int, date: String, reason: String)
}

// ==================== 5. 球员情绪事件 DAO ====================

@Dao
interface PlayerEmotionEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlayerEmotionEventEntity): Long

    @Query("""
        SELECT * FROM player_emotion_event
        WHERE save_id = :saveId AND club_id = :clubId
        ORDER BY event_date DESC
        LIMIT :limit
    """)
    suspend fun getRecent(saveId: Int, clubId: Int, limit: Int): List<PlayerEmotionEventEntity>

    @Query("""
        SELECT * FROM player_emotion_event
        WHERE save_id = :saveId AND club_id = :clubId
        ORDER BY event_date DESC
        LIMIT :limit
    """)
    fun observeRecent(saveId: Int, clubId: Int, limit: Int = 50): Flow<List<PlayerEmotionEventEntity>>

    @Query("""
        SELECT * FROM player_emotion_event
        WHERE save_id = :saveId AND club_id = :clubId AND player_id = :playerId
        ORDER BY event_date DESC
    """)
    suspend fun getByPlayer(saveId: Int, clubId: Int, playerId: Int): List<PlayerEmotionEventEntity>

    @Query("""
        SELECT COUNT(*) FROM player_emotion_event
        WHERE save_id = :saveId AND club_id = :clubId
        AND event_type = :eventType AND event_season = :season
    """)
    suspend fun countByTypeThisSeason(saveId: Int, clubId: Int, eventType: String, season: Int): Int

    @Query("""
        SELECT * FROM player_emotion_event
        WHERE save_id = :saveId AND club_id = :clubId AND event_season = :season
        ORDER BY event_date DESC
    """)
    suspend fun getBySeason(saveId: Int, clubId: Int, season: Int): List<PlayerEmotionEventEntity>

    @Query("""
        UPDATE player_emotion_event
        SET resolved = 1, resolution = :resolution, resolved_date = :date
        WHERE id = :eventId
    """)
    suspend fun resolve(eventId: Long, resolution: String, date: String)
}
