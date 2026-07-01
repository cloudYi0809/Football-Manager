package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveCupTieEntity
import kotlinx.coroutines.flow.Flow

/**
 * 存档杯赛对阵表数据访问对象（save.db）
 *
 * 提供淘汰赛对阵的 CRUD，支持按赛事、阶段查询，并通过 Flow 观察以驱动 UI 刷新。
 * 晋级方回填使用 [updateWinner] 与 [updateClubSlot] 增量更新。
 *
 * T06 新增。
 */
@Dao
interface SaveCupTieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tie: SaveCupTieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ties: List<SaveCupTieEntity>)

    @Update
    suspend fun update(tie: SaveCupTieEntity)

    @Query("SELECT * FROM save_cup_tie WHERE tie_id = :tieId")
    suspend fun get(tieId: String): SaveCupTieEntity?

    @Query("SELECT * FROM save_cup_tie WHERE save_id = :saveId AND competition_id = :competitionId ORDER BY stage_order, slot_index")
    fun observeByCompetition(saveId: Int, competitionId: Int): Flow<List<SaveCupTieEntity>>

    @Query("SELECT * FROM save_cup_tie WHERE save_id = :saveId AND competition_id = :competitionId ORDER BY stage_order, slot_index")
    suspend fun getByCompetition(saveId: Int, competitionId: Int): List<SaveCupTieEntity>

    @Query("SELECT * FROM save_cup_tie WHERE save_id = :saveId AND competition_id = :competitionId AND stage = :stage ORDER BY slot_index")
    suspend fun getByStage(saveId: Int, competitionId: Int, stage: String): List<SaveCupTieEntity>

    @Query("SELECT * FROM save_cup_tie WHERE save_id = :saveId AND season_id = :seasonId ORDER BY competition_id, stage_order, slot_index")
    suspend fun getBySeason(saveId: Int, seasonId: Int): List<SaveCupTieEntity>

    @Query("UPDATE save_cup_tie SET winner_club_id = :winnerClubId, aggregate_home_score = :aggHome, aggregate_away_score = :aggAway, updated_at = :updatedAt WHERE tie_id = :tieId")
    suspend fun updateWinner(
        tieId: String,
        winnerClubId: Int?,
        aggHome: Int?,
        aggAway: Int?,
        updatedAt: String
    )

    /** 占据下一轮对阵的空位（home 或 away 中先为 null 的位置） */
    @Query("UPDATE save_cup_tie SET home_club_id = :clubId, updated_at = :updatedAt WHERE tie_id = :tieId AND home_club_id IS NULL")
    suspend fun fillHomeSlot(tieId: String, clubId: Int, updatedAt: String): Int

    @Query("UPDATE save_cup_tie SET away_club_id = :clubId, updated_at = :updatedAt WHERE tie_id = :tieId AND away_club_id IS NULL")
    suspend fun fillAwaySlot(tieId: String, clubId: Int, updatedAt: String): Int

    @Query("UPDATE save_cup_tie SET first_leg_match_id = :matchId, updated_at = :updatedAt WHERE tie_id = :tieId")
    suspend fun setFirstLegMatchId(tieId: String, matchId: Int, updatedAt: String)

    @Query("UPDATE save_cup_tie SET second_leg_match_id = :matchId, updated_at = :updatedAt WHERE tie_id = :tieId")
    suspend fun setSecondLegMatchId(tieId: String, matchId: Int, updatedAt: String)

    @Query("DELETE FROM save_cup_tie WHERE save_id = :saveId AND season_id = :seasonId")
    suspend fun deleteBySeason(saveId: Int, seasonId: Int)

    @Query("SELECT COUNT(*) FROM save_cup_tie WHERE save_id = :saveId AND competition_id = :competitionId")
    suspend fun count(saveId: Int, competitionId: Int): Int
}
