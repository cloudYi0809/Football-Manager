package com.greendynasty.football.board.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T22 董事会模块 DAO 集合（save.db）。
 *
 * 严格依据 `/Users/yi/Desktop/足球经理/开发文档/T22_董事会_实现方案.md` §三.7 关键 DAO 示例。
 *
 * 协程规范：写操作与单次查询使用 suspend，列表查询使用 Flow 观察以驱动 UI 刷新。
 *
 * 共 6 个 DAO：
 * 1. [SeasonTargetDao] - 赛季目标 CRUD + 评估结果更新
 * 2. [LongTermGoalDao] - 长期目标 CRUD + 进度更新
 * 3. [BoardSatisfactionDao] - 满意度快照写入 + 历史曲线查询
 * 4. [BoardConfidenceDao] - 信心值读写 + 警告等级更新
 * 5. [BudgetRequestDao] - 预算申请记录 + 冷却期校验
 * 6. [BoardEventDao] - 董事会事件记录 + 玩家响应更新
 */

// ==================== 1. 赛季目标 DAO ====================

@Dao
interface SeasonTargetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: SeasonTargetEntity): Long

    @Query("SELECT * FROM board_season_target WHERE save_id = :saveId AND club_id = :clubId AND season_id = :seasonId LIMIT 1")
    suspend fun getBySeason(saveId: Int, clubId: Int, seasonId: Int): SeasonTargetEntity?

    @Query("SELECT * FROM board_season_target WHERE save_id = :saveId AND club_id = :clubId AND season_id = :seasonId LIMIT 1")
    fun observeBySeason(saveId: Int, clubId: Int, seasonId: Int): Flow<SeasonTargetEntity?>

    @Query("SELECT * FROM board_season_target WHERE save_id = :saveId AND club_id = :clubId ORDER BY season_id DESC LIMIT :limit")
    suspend fun getHistory(saveId: Int, clubId: Int, limit: Int = 10): List<SeasonTargetEntity>

    @Query("UPDATE board_season_target SET evaluation_status = :status, evaluation_score = :score, evaluated_at = :date WHERE id = :id")
    suspend fun updateEvaluation(id: Int, status: String, score: Double, date: String)

    @Query("SELECT COUNT(*) FROM board_season_target WHERE save_id = :saveId AND club_id = :clubId AND evaluation_status = 'FAILED' AND season_id >= :sinceSeasonId")
    suspend fun countCoreFailedSeasons(saveId: Int, clubId: Int, sinceSeasonId: Int): Int
}

// ==================== 2. 长期目标 DAO ====================

@Dao
interface LongTermGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: LongTermGoalEntity): Long

    @Query("SELECT * FROM board_long_term_goal WHERE save_id = :saveId AND club_id = :clubId AND status = 'ACTIVE' ORDER BY target_year ASC")
    suspend fun getActiveGoals(saveId: Int, clubId: Int): List<LongTermGoalEntity>

    @Query("SELECT * FROM board_long_term_goal WHERE save_id = :saveId AND club_id = :clubId AND status = 'ACTIVE' ORDER BY target_year ASC")
    fun observeActiveGoals(saveId: Int, clubId: Int): Flow<List<LongTermGoalEntity>>

    @Query("UPDATE board_long_term_goal SET current_metric = :currentMetric, progress_percent = :progressPercent WHERE id = :id")
    suspend fun updateProgress(id: Int, currentMetric: Double, progressPercent: Double)

    @Query("UPDATE board_long_term_goal SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)
}

// ==================== 3. 满意度快照 DAO ====================

@Dao
interface BoardSatisfactionDao {

    @Insert
    suspend fun insert(snapshot: BoardSatisfactionEntity): Long

    @Query("SELECT * FROM board_satisfaction_snapshot WHERE save_id = :saveId AND club_id = :clubId ORDER BY snapshot_date DESC LIMIT :limit")
    suspend fun getLatest(saveId: Int, clubId: Int, limit: Int = 12): List<BoardSatisfactionEntity>

    @Query("SELECT * FROM board_satisfaction_snapshot WHERE save_id = :saveId AND club_id = :clubId ORDER BY snapshot_date DESC LIMIT :limit")
    fun observeLatest(saveId: Int, clubId: Int, limit: Int = 12): Flow<List<BoardSatisfactionEntity>>
}

// ==================== 4. 董事会信心值 DAO ====================

@Dao
interface BoardConfidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(confidence: BoardConfidenceEntity): Long

    @Query("SELECT * FROM board_confidence WHERE save_id = :saveId AND club_id = :clubId AND season_id = :seasonId LIMIT 1")
    suspend fun getBySeason(saveId: Int, clubId: Int, seasonId: Int): BoardConfidenceEntity?

    @Query("SELECT * FROM board_confidence WHERE save_id = :saveId AND club_id = :clubId AND season_id = :seasonId LIMIT 1")
    fun observeBySeason(saveId: Int, clubId: Int, seasonId: Int): Flow<BoardConfidenceEntity?>

    @Query("SELECT * FROM board_confidence WHERE save_id = :saveId AND club_id = :clubId ORDER BY season_id DESC LIMIT 1")
    suspend fun getLatest(saveId: Int, clubId: Int): BoardConfidenceEntity?

    @Query("UPDATE board_confidence SET confidence_value = :value, warning_level = :warningLevel, consecutive_core_failed_seasons = :consecutiveFailed, last_updated_at = :date WHERE id = :id")
    suspend fun updateConfidence(
        id: Int,
        value: Int,
        warningLevel: String,
        consecutiveFailed: Int,
        date: String
    )
}

// ==================== 5. 预算申请 DAO ====================

@Dao
interface BudgetRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: BudgetRequestEntity): Long

    @Query("SELECT * FROM board_budget_request WHERE save_id = :saveId AND club_id = :clubId ORDER BY request_date DESC")
    suspend fun getAll(saveId: Int, clubId: Int): List<BudgetRequestEntity>

    @Query("SELECT * FROM board_budget_request WHERE save_id = :saveId AND club_id = :clubId ORDER BY request_date DESC")
    fun observeAll(saveId: Int, clubId: Int): Flow<List<BudgetRequestEntity>>

    @Query("SELECT * FROM board_budget_request WHERE save_id = :saveId AND club_id = :clubId AND status = 'PENDING' ORDER BY request_date DESC")
    suspend fun getPending(saveId: Int, clubId: Int): List<BudgetRequestEntity>

    @Query("SELECT * FROM board_budget_request WHERE save_id = :saveId AND club_id = :clubId AND request_type = :type AND request_date >= :sinceDate AND status IN ('APPROVED','REJECTED','NEGOTIATED') ORDER BY request_date DESC LIMIT 1")
    suspend fun getLatestReviewed(saveId: Int, clubId: Int, type: String, sinceDate: String): BudgetRequestEntity?
}

// ==================== 6. 董事会事件 DAO ====================

@Dao
interface BoardEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BoardEventEntity): Long

    @Query("SELECT * FROM board_event WHERE save_id = :saveId AND club_id = :clubId ORDER BY event_date DESC LIMIT :limit")
    suspend fun getRecent(saveId: Int, clubId: Int, limit: Int = 50): List<BoardEventEntity>

    @Query("SELECT * FROM board_event WHERE save_id = :saveId AND club_id = :clubId ORDER BY event_date DESC LIMIT :limit")
    fun observeRecent(saveId: Int, clubId: Int, limit: Int = 50): Flow<List<BoardEventEntity>>

    @Query("SELECT * FROM board_event WHERE save_id = :saveId AND club_id = :clubId AND player_action_required = 1 AND resolved_at IS NULL ORDER BY event_date DESC")
    suspend fun getUnresolvedActionRequired(saveId: Int, clubId: Int): List<BoardEventEntity>

    @Query("UPDATE board_event SET player_response = :response, resolved_at = :resolvedAt WHERE id = :id")
    suspend fun resolveEvent(id: Int, response: String, resolvedAt: String)
}
