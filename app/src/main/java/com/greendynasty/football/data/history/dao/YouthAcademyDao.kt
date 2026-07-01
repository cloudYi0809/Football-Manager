package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import com.greendynasty.football.data.history.entity.YouthAcademyEntity
import kotlinx.coroutines.flow.Flow

/**
 * 青训学院数据访问对象（history.db 只读）
 * 提供俱乐部青训学院信息查询，包括等级、招募范围、教练质量等。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface YouthAcademyDao {

    // 按 club_id 查询某俱乐部的青训学院
    @Query("SELECT * FROM youth_academy WHERE club_id = :clubId")
    suspend fun getByClub(clubId: Int): YouthAcademyEntity?

    // 查询全部青训学院（按学院声望倒序）
    @Query("SELECT * FROM youth_academy ORDER BY academy_reputation DESC")
    fun getAll(): Flow<List<YouthAcademyEntity>>

    // 按青训等级筛选（按等级倒序）
    @Query("SELECT * FROM youth_academy WHERE youth_level >= :minLevel ORDER BY youth_level DESC")
    fun getByYouthLevel(minLevel: Int): Flow<List<YouthAcademyEntity>>

    // 按学院声望筛选（按声望倒序）
    @Query("SELECT * FROM youth_academy WHERE academy_reputation >= :minReputation ORDER BY academy_reputation DESC")
    fun getByReputation(minReputation: Int): Flow<List<YouthAcademyEntity>>

    // 青训学院总数
    @Query("SELECT COUNT(*) FROM youth_academy")
    suspend fun count(): Int
}
