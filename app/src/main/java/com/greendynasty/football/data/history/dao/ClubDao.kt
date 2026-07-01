package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.ClubEntity
import kotlinx.coroutines.flow.Flow

/**
 * 俱乐部数据访问对象（history.db 只读）
 * 提供俱乐部查询，支持按国家筛选、按声望排名。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface ClubDao {

    // 按 club_id 查询单个俱乐部
    @Query("SELECT * FROM club WHERE club_id = :clubId")
    suspend fun getClub(clubId: Int): ClubEntity?

    // 查询全部俱乐部（按名称排序）
    @Query("SELECT * FROM club ORDER BY club_name")
    fun getAllClubs(): Flow<List<ClubEntity>>

    // 按国家筛选俱乐部
    @Query("SELECT * FROM club WHERE country = :country ORDER BY club_name")
    fun getClubsByCountry(country: String): Flow<List<ClubEntity>>

    // 查询声望最高的 N 个俱乐部
    @Transaction
    @Query("SELECT * FROM club ORDER BY reputation DESC LIMIT :limit")
    fun getTopClubs(limit: Int): Flow<List<ClubEntity>>

    // 俱乐部总数
    @Query("SELECT COUNT(*) FROM club")
    suspend fun count(): Int
}
