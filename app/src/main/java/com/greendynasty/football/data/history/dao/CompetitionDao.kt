package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.ClubCompetitionSeasonEntity
import com.greendynasty.football.data.history.entity.CompetitionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 赛事数据访问对象（history.db 只读）
 * 提供赛事和俱乐部-赛事-赛季关联查询。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface CompetitionDao {

    // 按 competition_id 查询单个赛事
    @Query("SELECT * FROM competition WHERE competition_id = :competitionId")
    suspend fun getCompetition(competitionId: Int): CompetitionEntity?

    // 查询全部赛事（按名称排序）
    @Query("SELECT * FROM competition ORDER BY name")
    fun getAllCompetitions(): Flow<List<CompetitionEntity>>

    // 按国家筛选赛事（按级别排序）
    @Query("SELECT * FROM competition WHERE country = :country ORDER BY level")
    fun getCompetitionsByCountry(country: String): Flow<List<CompetitionEntity>>

    // 查询某赛季某赛事的全部参赛俱乐部
    @Transaction
    @Query("SELECT * FROM club_competition_season WHERE season_id = :seasonId AND competition_id = :competitionId")
    fun getClubsInCompetition(seasonId: Int, competitionId: Int): Flow<List<ClubCompetitionSeasonEntity>>

    // 查询某赛季某俱乐部参加的全部赛事
    @Transaction
    @Query("SELECT * FROM club_competition_season WHERE season_id = :seasonId AND club_id = :clubId")
    fun getCompetitionsForClub(seasonId: Int, clubId: Int): Flow<List<ClubCompetitionSeasonEntity>>

    // 赛事总数
    @Query("SELECT COUNT(*) FROM competition")
    suspend fun count(): Int
}
