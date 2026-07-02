package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveMatchEntity

/**
 * 存档比赛数据访问对象（save.db）
 * 提供存档内比赛赛程和比分的 CRUD，支持按赛季、赛事、俱乐部、状态查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，赛程列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveMatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: SaveMatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(matches: List<SaveMatchEntity>)

    @Update
    suspend fun update(match: SaveMatchEntity)

    @Delete
    suspend fun delete(match: SaveMatchEntity)

    @Query("SELECT * FROM save_match WHERE match_id = :matchId")
    suspend fun get(matchId: Int): SaveMatchEntity?

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND season_id = :seasonId ORDER BY match_date")
    suspend fun getBySeason(saveId: Int, seasonId: Int): List<SaveMatchEntity>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId ORDER BY match_date")
    fun observeBySeasonAndCompetition(saveId: Int, seasonId: Int, competitionId: Int): kotlinx.coroutines.flow.Flow<List<SaveMatchEntity>>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId ORDER BY match_date")
    suspend fun getBySeasonAndCompetition(saveId: Int, seasonId: Int, competitionId: Int): List<SaveMatchEntity>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND (home_club_id = :clubId OR away_club_id = :clubId) AND season_id = :seasonId ORDER BY match_date")
    fun observeByClub(saveId: Int, clubId: Int, seasonId: Int): kotlinx.coroutines.flow.Flow<List<SaveMatchEntity>>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND (home_club_id = :clubId OR away_club_id = :clubId) AND season_id = :seasonId ORDER BY match_date")
    suspend fun getByClub(saveId: Int, clubId: Int, seasonId: Int): List<SaveMatchEntity>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND status = :status ORDER BY match_date")
    suspend fun getByStatus(saveId: Int, status: String): List<SaveMatchEntity>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND match_date BETWEEN :startDate AND :endDate ORDER BY match_date")
    suspend fun getByDateRange(saveId: Int, startDate: String, endDate: String): List<SaveMatchEntity>

    @Query("SELECT * FROM save_match WHERE save_id = :saveId AND is_player_match = 1 ORDER BY match_date DESC LIMIT :limit")
    suspend fun getRecentPlayerMatches(saveId: Int, limit: Int): List<SaveMatchEntity>

    @Query("UPDATE save_match SET home_score = :homeScore, away_score = :awayScore, status = :status, match_stats_json = :statsJson, updated_at = :updatedAt WHERE match_id = :matchId")
    suspend fun updateResult(
        matchId: Int,
        homeScore: Int,
        awayScore: Int,
        status: String,
        statsJson: String?,
        updatedAt: String
    )

    @Query("UPDATE save_match SET status = :status WHERE match_id = :matchId")
    suspend fun updateStatus(matchId: Int, status: String)

    @Query("DELETE FROM save_match WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun deleteBySeason(saveId: Int, seasonId: Int)

    @Query("SELECT COUNT(*) FROM save_match WHERE save_id = :saveId AND status = 'scheduled'")
    suspend fun countScheduled(saveId: Int): Int

    @Query("SELECT COUNT(*) FROM save_match WHERE save_id = :saveId")
    suspend fun count(saveId: Int): Int

    // T19 赛季归档：压缩比赛事件后清空详细统计字段以回收空间
    @Query("UPDATE save_match SET match_stats_json = NULL WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun clearMatchStatsBySeason(saveId: Int, seasonId: Int)
}
