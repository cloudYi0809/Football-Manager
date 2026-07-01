package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity

/**
 * 球员存档状态数据访问对象（save.db）
 * 提供球员当前状态的 CRUD，支持按俱乐部、存档 ID 查询球员状态。
 *
 * 协程规范：写操作与单次查询使用 suspend，阵容/球员列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SavePlayerStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SavePlayerStateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<SavePlayerStateEntity>)

    @Update
    suspend fun update(state: SavePlayerStateEntity)

    @Delete
    suspend fun delete(state: SavePlayerStateEntity)

    @Query("SELECT * FROM save_player_state WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun getByPlayer(saveId: Int, playerId: Int): SavePlayerStateEntity?

    @Query("SELECT * FROM save_player_state WHERE save_id = :saveId AND current_club_id = :clubId ORDER BY squad_role")
    fun observeByClub(saveId: Int, clubId: Int): kotlinx.coroutines.flow.Flow<List<SavePlayerStateEntity>>

    @Query("SELECT * FROM save_player_state WHERE save_id = :saveId AND current_club_id = :clubId ORDER BY squad_role")
    suspend fun getByClub(saveId: Int, clubId: Int): List<SavePlayerStateEntity>

    @Query("SELECT * FROM save_player_state WHERE save_id = :saveId AND career_status = :status")
    suspend fun getByStatus(saveId: Int, status: String): List<SavePlayerStateEntity>

    @Query("SELECT * FROM save_player_state WHERE save_id = :saveId")
    suspend fun getAll(saveId: Int): List<SavePlayerStateEntity>

    @Query("UPDATE save_player_state SET current_ca = :ca WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun updateCa(saveId: Int, playerId: Int, ca: Int)

    @Query("UPDATE save_player_state SET morale = :morale WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun updateMorale(saveId: Int, playerId: Int, morale: Int)

    @Query("UPDATE save_player_state SET condition = :condition WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun updateCondition(saveId: Int, playerId: Int, condition: Int)

    @Query("UPDATE save_player_state SET injury_status = :status, injury_until = :until WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun updateInjuryStatus(saveId: Int, playerId: Int, status: String, until: String?)

    @Query("UPDATE save_player_state SET current_club_id = :clubId WHERE save_id = :saveId AND player_id = :playerId")
    suspend fun updateClub(saveId: Int, playerId: Int, clubId: Int?)

    @Query("SELECT COUNT(*) FROM save_player_state WHERE save_id = :saveId")
    suspend fun count(saveId: Int): Int
}
