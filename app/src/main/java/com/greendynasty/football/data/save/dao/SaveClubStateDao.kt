package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveClubStateEntity

/**
 * 俱乐部存档状态数据访问对象（save.db）
 * 提供俱乐部当前状态的 CRUD，包括预算、满意度等更新。
 *
 * 协程规范：写操作与单次查询使用 suspend，俱乐部列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveClubStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SaveClubStateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<SaveClubStateEntity>)

    @Update
    suspend fun update(state: SaveClubStateEntity)

    @Delete
    suspend fun delete(state: SaveClubStateEntity)

    @Query("SELECT * FROM save_club_state WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun getByClub(saveId: Int, clubId: Int): SaveClubStateEntity?

    @Query("SELECT * FROM save_club_state WHERE save_id = :saveId AND club_id = :clubId")
    fun observeByClub(saveId: Int, clubId: Int): kotlinx.coroutines.flow.Flow<SaveClubStateEntity?>

    @Query("SELECT * FROM save_club_state WHERE save_id = :saveId")
    suspend fun getAll(saveId: Int): List<SaveClubStateEntity>

    @Query("UPDATE save_club_state SET balance = :balance WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun updateBalance(saveId: Int, clubId: Int, balance: Int)

    @Query("UPDATE save_club_state SET transfer_budget = :budget WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun updateTransferBudget(saveId: Int, clubId: Int, budget: Int)

    @Query("UPDATE save_club_state SET wage_budget = :budget WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun updateWageBudget(saveId: Int, clubId: Int, budget: Int)

    @Query("UPDATE save_club_state SET board_satisfaction = :satisfaction WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun updateBoardSatisfaction(saveId: Int, clubId: Int, satisfaction: Int)

    @Query("SELECT COUNT(*) FROM save_club_state WHERE save_id = :saveId")
    suspend fun count(saveId: Int): Int
}
