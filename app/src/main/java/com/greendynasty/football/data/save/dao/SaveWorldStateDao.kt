package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveWorldStateEntity

/**
 * 存档世界状态数据访问对象（save.db）
 * 提供当前游戏全局状态的读写，每个存档只有一条世界状态记录。
 *
 * 协程规范：写操作与单次查询使用 suspend，世界状态变化使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveWorldStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SaveWorldStateEntity): Long

    @Update
    suspend fun update(state: SaveWorldStateEntity)

    @Delete
    suspend fun delete(state: SaveWorldStateEntity)

    @Query("SELECT * FROM save_world_state LIMIT 1")
    fun observe(): kotlinx.coroutines.flow.Flow<SaveWorldStateEntity?>

    @Query("SELECT * FROM save_world_state LIMIT 1")
    suspend fun get(): SaveWorldStateEntity?

    @Query("SELECT * FROM save_world_state WHERE save_id = :saveId LIMIT 1")
    suspend fun getById(saveId: Int): SaveWorldStateEntity?

    @Query("UPDATE save_world_state SET current_date = :date, updated_at = :updatedAt WHERE save_id = :saveId")
    suspend fun updateCurrentDate(saveId: Int, date: String, updatedAt: String)

    @Query("UPDATE save_world_state SET current_season_id = :seasonId, updated_at = :updatedAt WHERE save_id = :saveId")
    suspend fun updateSeason(saveId: Int, seasonId: Int, updatedAt: String)

    @Query("SELECT COUNT(*) FROM save_world_state")
    suspend fun count(): Int
}
