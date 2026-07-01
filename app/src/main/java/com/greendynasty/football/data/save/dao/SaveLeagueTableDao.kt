package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveLeagueTableEntity

/**
 * 存档积分榜数据访问对象（save.db）
 * 提供各联赛积分榜的 CRUD，支持按赛季、赛事查询，并按积分/净胜球排序。
 *
 * 协程规范：写操作与单次查询使用 suspend，积分榜使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveLeagueTableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SaveLeagueTableEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SaveLeagueTableEntity>)

    @Update
    suspend fun update(entry: SaveLeagueTableEntity)

    @Delete
    suspend fun delete(entry: SaveLeagueTableEntity)

    @Query("SELECT * FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId ORDER BY points DESC, goal_difference DESC, goals_for DESC")
    fun observeBySeasonAndCompetition(saveId: Int, seasonId: Int, competitionId: Int): kotlinx.coroutines.flow.Flow<List<SaveLeagueTableEntity>>

    @Query("SELECT * FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId ORDER BY points DESC, goal_difference DESC, goals_for DESC")
    suspend fun getBySeasonAndCompetition(saveId: Int, seasonId: Int, competitionId: Int): List<SaveLeagueTableEntity>

    @Query("SELECT * FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId AND club_id = :clubId")
    suspend fun getByClub(saveId: Int, seasonId: Int, competitionId: Int, clubId: Int): SaveLeagueTableEntity?

    @Query("SELECT * FROM save_league_table WHERE save_id = :saveId AND club_id = :clubId ORDER BY season_id DESC")
    suspend fun getClubHistory(saveId: Int, clubId: Int): List<SaveLeagueTableEntity>

    @Query("SELECT * FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId AND position = :position")
    suspend fun getByPosition(saveId: Int, seasonId: Int, competitionId: Int, position: Int): SaveLeagueTableEntity?

    @Query("UPDATE save_league_table SET position = :position, played = :played, won = :won, drawn = :drawn, lost = :lost, goals_for = :goalsFor, goals_against = :goalsAgainst, goal_difference = :goalDifference, points = :points, form = :form, updated_at = :updatedAt WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId AND club_id = :clubId")
    suspend fun updateStandings(
        saveId: Int,
        seasonId: Int,
        competitionId: Int,
        clubId: Int,
        position: Int,
        played: Int,
        won: Int,
        drawn: Int,
        lost: Int,
        goalsFor: Int,
        goalsAgainst: Int,
        goalDifference: Int,
        points: Int,
        form: String?,
        updatedAt: String
    )

    @Query("UPDATE save_league_table SET position = :position, updated_at = :updatedAt WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId AND club_id = :clubId")
    suspend fun updatePosition(
        saveId: Int,
        seasonId: Int,
        competitionId: Int,
        clubId: Int,
        position: Int,
        updatedAt: String
    )

    @Query("DELETE FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId")
    suspend fun deleteBySeasonAndCompetition(saveId: Int, seasonId: Int, competitionId: Int)

    @Query("DELETE FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun deleteBySeason(saveId: Int, seasonId: Int)

    @Query("SELECT COUNT(*) FROM save_league_table WHERE save_id = :saveId AND season_id = :seasonId AND competition_id = :competitionId")
    suspend fun count(saveId: Int, seasonId: Int, competitionId: Int): Int
}
