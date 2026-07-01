package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * 比赛数据访问对象（history.db 只读）
 * 提供历史比赛赛程和比分查询，支持按赛季、赛事、俱乐部、日期筛选。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface MatchDao {

    // 按 match_id 查询单场比赛
    @Query("SELECT * FROM match WHERE match_id = :matchId")
    suspend fun getMatch(matchId: Int): MatchEntity?

    // 查询某赛季某赛事的全部比赛（按日期排序）
    @Query("SELECT * FROM match WHERE season_id = :seasonId AND competition_id = :competitionId ORDER BY match_date")
    fun getMatchesBySeasonAndCompetition(seasonId: Int, competitionId: Int): Flow<List<MatchEntity>>

    // 查询某赛季的全部比赛（按日期排序）
    @Query("SELECT * FROM match WHERE season_id = :seasonId ORDER BY match_date")
    fun getMatchesBySeason(seasonId: Int): Flow<List<MatchEntity>>

    // 查询某俱乐部某赛季的主场比赛
    @Query("SELECT * FROM match WHERE home_club_id = :clubId AND season_id = :seasonId ORDER BY match_date")
    fun getHomeMatches(clubId: Int, seasonId: Int): Flow<List<MatchEntity>>

    // 查询某俱乐部某赛季的客场比赛
    @Query("SELECT * FROM match WHERE away_club_id = :clubId AND season_id = :seasonId ORDER BY match_date")
    fun getAwayMatches(clubId: Int, seasonId: Int): Flow<List<MatchEntity>>

    // 查询某俱乐部某赛季的全部比赛（主场+客场）
    @Transaction
    @Query("SELECT * FROM match WHERE (home_club_id = :clubId OR away_club_id = :clubId) AND season_id = :seasonId ORDER BY match_date")
    fun getClubMatches(clubId: Int, seasonId: Int): Flow<List<MatchEntity>>

    // 按日期范围查询比赛
    @Transaction
    @Query("SELECT * FROM match WHERE match_date BETWEEN :startDate AND :endDate ORDER BY match_date")
    fun getMatchesByDateRange(startDate: String, endDate: String): Flow<List<MatchEntity>>

    // 比赛总数
    @Query("SELECT COUNT(*) FROM match")
    suspend fun count(): Int
}
