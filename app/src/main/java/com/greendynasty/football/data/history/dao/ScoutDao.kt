package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import com.greendynasty.football.data.history.entity.ScoutEntity
import kotlinx.coroutines.flow.Flow

/**
 * 球探数据访问对象（history.db 只读）
 * 提供球探信息查询，包括按俱乐部、国籍查询球探。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface ScoutDao {

    // 按 scout_id 查询单个球探
    @Query("SELECT * FROM scout WHERE scout_id = :scoutId")
    suspend fun getScout(scoutId: Int): ScoutEntity?

    // 查询某俱乐部的全部球探
    @Query("SELECT * FROM scout WHERE current_club_id = :clubId")
    fun getScoutsByClub(clubId: Int): Flow<List<ScoutEntity>>

    // 查询全部球探（按声望倒序）
    @Query("SELECT * FROM scout ORDER BY reputation DESC")
    fun getAllScouts(): Flow<List<ScoutEntity>>

    // 按国籍筛选球探
    @Query("SELECT * FROM scout WHERE nationality = :nationality ORDER BY name")
    fun getScoutsByNationality(nationality: String): Flow<List<ScoutEntity>>

    // 球探总数
    @Query("SELECT COUNT(*) FROM scout")
    suspend fun count(): Int
}
