package com.greendynasty.football.youth.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.youth.model.YouthAcademyInvestmentEntity
import com.greendynasty.football.youth.model.YouthAcademyStateEntity
import com.greendynasty.football.youth.model.YouthEventEntity
import com.greendynasty.football.youth.model.YouthPlayerEntity
import kotlinx.coroutines.flow.Flow

/**
 * T16 青训学院 DAO（save.db，V0.1 08 §二.1 + T16 方案 §三.8）。
 *
 * 协程规范：写操作与单次查询使用 suspend，列表查询使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface YouthAcademyStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(academy: YouthAcademyStateEntity): Long

    @Update
    suspend fun update(academy: YouthAcademyStateEntity)

    @Query("SELECT * FROM youth_academy_state WHERE save_id = :saveId AND club_id = :clubId LIMIT 1")
    suspend fun getByClub(saveId: Int, clubId: Int): YouthAcademyStateEntity?

    @Query("SELECT * FROM youth_academy_state WHERE save_id = :saveId AND club_id = :clubId LIMIT 1")
    fun observeByClub(saveId: Int, clubId: Int): Flow<YouthAcademyStateEntity?>

    @Query("SELECT * FROM youth_academy_state WHERE save_id = :saveId")
    suspend fun getAllBySave(saveId: Int): List<YouthAcademyStateEntity>

    @Query("UPDATE youth_academy_state SET style_change_cooldown = :cooldown WHERE academy_id = :academyId")
    suspend fun updateStyleCooldown(academyId: Int, cooldown: Int)

    @Query("UPDATE youth_academy_state SET last_production_month = :month WHERE academy_id = :academyId")
    suspend fun updateLastProductionMonth(academyId: Int, month: String)

    @Query("UPDATE youth_academy_state SET youth_level = :level WHERE academy_id = :academyId")
    suspend fun updateYouthLevel(academyId: Int, level: Int)

    @Query("UPDATE youth_academy_state SET training_facility = :level WHERE academy_id = :academyId")
    suspend fun updateTrainingFacility(academyId: Int, level: Int)

    @Query("UPDATE youth_academy_state SET u18_coach_quality = :level WHERE academy_id = :academyId")
    suspend fun updateU18CoachQuality(academyId: Int, level: Int)

    @Query("UPDATE youth_academy_state SET u21_coach_quality = :level WHERE academy_id = :academyId")
    suspend fun updateU21CoachQuality(academyId: Int, level: Int)

    @Query("UPDATE youth_academy_state SET recruitment_range = :range WHERE academy_id = :academyId")
    suspend fun updateRecruitmentRange(academyId: Int, range: String)

    @Query("UPDATE youth_academy_state SET academy_reputation = :reputation WHERE academy_id = :academyId")
    suspend fun updateReputation(academyId: Int, reputation: Int)
}

/**
 * T16 青训球员 DAO（save.db，V0.1 08 §二.3 + T16 方案 §三.8）。
 */
@Dao
interface YouthPlayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: YouthPlayerEntity): Long

    @Update
    suspend fun update(player: YouthPlayerEntity)

    @Delete
    suspend fun delete(player: YouthPlayerEntity)

    @Query("SELECT * FROM youth_player WHERE youth_player_id = :id LIMIT 1")
    suspend fun getById(id: Int): YouthPlayerEntity?

    @Query("SELECT * FROM youth_player WHERE youth_player_id = :id LIMIT 1")
    fun observeById(id: Int): Flow<YouthPlayerEntity?>

    @Query("SELECT * FROM youth_player WHERE save_id = :saveId AND club_id = :clubId ORDER BY potential_pa DESC, current_ca DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<YouthPlayerEntity>

    @Query("SELECT * FROM youth_player WHERE save_id = :saveId AND club_id = :clubId ORDER BY potential_pa DESC, current_ca DESC")
    fun observeByClub(saveId: Int, clubId: Int): Flow<List<YouthPlayerEntity>>

    @Query("SELECT * FROM youth_player WHERE save_id = :saveId AND club_id = :clubId AND tier = :tier ORDER BY potential_pa DESC")
    suspend fun getByTier(saveId: Int, clubId: Int, tier: String): List<YouthPlayerEntity>

    @Query("SELECT * FROM youth_player WHERE save_id = :saveId AND club_id = :clubId AND tier = :tier ORDER BY potential_pa DESC")
    fun observeByTier(saveId: Int, clubId: Int, tier: String): Flow<List<YouthPlayerEntity>>

    @Query("SELECT * FROM youth_player WHERE save_id = :saveId AND status = :status")
    suspend fun getByStatus(saveId: Int, status: String): List<YouthPlayerEntity>

    @Query("SELECT * FROM youth_player WHERE save_id = :saveId AND club_id = :clubId AND potential_pa >= :minPa ORDER BY potential_pa DESC")
    suspend fun getHighPotentialByClub(saveId: Int, clubId: Int, minPa: Int): List<YouthPlayerEntity>

    @Query("SELECT COUNT(*) FROM youth_player WHERE save_id = :saveId AND club_id = :clubId AND tier = :tier")
    suspend fun countByTier(saveId: Int, clubId: Int, tier: String): Int

    @Query("SELECT COUNT(*) FROM youth_player WHERE save_id = :saveId AND club_id = :clubId AND hidden_tags LIKE '%GENIUS%' AND generated_date LIKE :monthPrefix")
    suspend fun countGeniusInMonth(saveId: Int, clubId: Int, monthPrefix: String): Int

    @Query("UPDATE youth_player SET current_ca = :ca WHERE youth_player_id = :id")
    suspend fun updateCa(id: Int, ca: Int)

    @Query("UPDATE youth_player SET status = :status WHERE youth_player_id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("UPDATE youth_player SET tier = :tier WHERE youth_player_id = :id")
    suspend fun updateTier(id: Int, tier: String)

    @Query("UPDATE youth_player SET is_key_prospect = :isKey WHERE youth_player_id = :id")
    suspend fun updateKeyProspect(id: Int, isKey: Int)

    @Query("UPDATE youth_player SET training_focus = :focus WHERE youth_player_id = :id")
    suspend fun updateTrainingFocus(id: Int, focus: String)

    @Query("UPDATE youth_player SET mentor_player_id = :mentorId WHERE youth_player_id = :id")
    suspend fun updateMentor(id: Int, mentorId: Int?)

    @Query("UPDATE youth_player SET contract_type = :type, contract_until = :until, wage = :wage WHERE youth_player_id = :id")
    suspend fun updateContract(id: Int, type: String, until: String?, wage: Int)

    @Query("UPDATE youth_player SET player_id = :playerId WHERE youth_player_id = :id")
    suspend fun updatePlayerId(id: Int, playerId: Int)

    @Query("UPDATE youth_player SET monthly_growth_log_json = :logJson WHERE youth_player_id = :id")
    suspend fun updateGrowthLog(id: Int, logJson: String)

    @Query("SELECT COUNT(*) FROM youth_player WHERE save_id = :saveId AND mentor_player_id = :mentorId")
    suspend fun countMenteesByMentor(saveId: Int, mentorId: Int): Int
}

/**
 * T16 青训事件 DAO（save.db，V0.1 08 §二.6）。
 */
@Dao
interface YouthEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: YouthEventEntity): Long

    @Update
    suspend fun update(event: YouthEventEntity)

    @Query("SELECT * FROM youth_event WHERE event_id = :id LIMIT 1")
    suspend fun getById(id: Int): YouthEventEntity?

    @Query("SELECT * FROM youth_event WHERE save_id = :saveId AND club_id = :clubId ORDER BY trigger_date DESC, event_id DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<YouthEventEntity>

    @Query("SELECT * FROM youth_event WHERE save_id = :saveId AND club_id = :clubId AND status = 'PENDING' ORDER BY importance DESC, trigger_date DESC")
    fun observePending(saveId: Int, clubId: Int): Flow<List<YouthEventEntity>>

    @Query("SELECT * FROM youth_event WHERE save_id = :saveId AND club_id = :clubId ORDER BY trigger_date DESC, event_id DESC LIMIT :limit")
    fun observeRecent(saveId: Int, clubId: Int, limit: Int): Flow<List<YouthEventEntity>>

    @Query("SELECT COUNT(*) FROM youth_event WHERE save_id = :saveId AND club_id = :clubId AND event_type = :type AND trigger_date LIKE :yearPrefix")
    suspend fun countInYear(saveId: Int, clubId: Int, type: String, yearPrefix: String): Int

    @Query("SELECT COUNT(*) FROM youth_event WHERE save_id = :saveId AND club_id = :clubId AND event_type = :type AND trigger_date >= :sinceDate")
    suspend fun countSinceDate(saveId: Int, clubId: Int, type: String, sinceDate: String): Int

    @Query("UPDATE youth_event SET status = :status, resolved_date = :date, outcome_summary = :summary WHERE event_id = :id")
    suspend fun resolve(id: Int, status: String, date: String, summary: String?)
}

/**
 * T16 青训投资记录 DAO（save.db，V0.1 08 §二.2）。
 */
@Dao
interface YouthAcademyInvestmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(investment: YouthAcademyInvestmentEntity): Long

    @Query("SELECT * FROM youth_academy_investment WHERE save_id = :saveId AND club_id = :clubId ORDER BY invest_date DESC")
    suspend fun getByClub(saveId: Int, clubId: Int): List<YouthAcademyInvestmentEntity>

    @Query("SELECT * FROM youth_academy_investment WHERE save_id = :saveId AND club_id = :clubId ORDER BY invest_date DESC")
    fun observeByClub(saveId: Int, clubId: Int): Flow<List<YouthAcademyInvestmentEntity>>

    @Query("SELECT COUNT(*) FROM youth_academy_investment WHERE save_id = :saveId AND club_id = :clubId AND invest_field = :field")
    suspend fun countByField(saveId: Int, clubId: Int, field: String): Int
}
