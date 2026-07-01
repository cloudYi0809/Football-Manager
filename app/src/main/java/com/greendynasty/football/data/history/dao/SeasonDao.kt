package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import com.greendynasty.football.data.history.entity.SeasonEntity
import kotlinx.coroutines.flow.Flow

/**
 * 赛季数据访问对象（history.db 只读）
 * 提供赛季信息查询，支持按年份定位赛季。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface SeasonDao {

    // 按 season_id 查询单个赛季
    @Query("SELECT * FROM season WHERE season_id = :seasonId")
    suspend fun getSeason(seasonId: Int): SeasonEntity?

    // 查询全部赛季（按起始年份排序）
    @Query("SELECT * FROM season ORDER BY year_start")
    fun getAllSeasons(): Flow<List<SeasonEntity>>

    // 按起始年份查询赛季
    @Query("SELECT * FROM season WHERE year_start = :year LIMIT 1")
    suspend fun getSeasonByYear(year: Int): SeasonEntity?

    // 赛季总数
    @Query("SELECT COUNT(*) FROM season")
    suspend fun count(): Int
}
