package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.ClubAiProfileEntity

/**
 * 俱乐部 AI 画像数据访问对象（save.db，V0.2）
 * 提供 AI 俱乐部决策偏好的 CRUD。
 *
 * 协程规范：写操作与单次查询使用 suspend，画像列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface ClubAiProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ClubAiProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ClubAiProfileEntity>)

    @Update
    suspend fun update(profile: ClubAiProfileEntity)

    @Delete
    suspend fun delete(profile: ClubAiProfileEntity)

    @Query("SELECT * FROM club_ai_profile WHERE club_id = :clubId")
    suspend fun get(clubId: Int): ClubAiProfileEntity?

    @Query("SELECT * FROM club_ai_profile WHERE club_id = :clubId")
    fun observe(clubId: Int): kotlinx.coroutines.flow.Flow<ClubAiProfileEntity?>

    @Query("SELECT * FROM club_ai_profile ORDER BY ambition DESC")
    suspend fun getAll(): List<ClubAiProfileEntity>

    @Query("SELECT * FROM club_ai_profile WHERE ambition >= :minAmbition ORDER BY ambition DESC")
    suspend fun getAmbitiousClubs(minAmbition: Int): List<ClubAiProfileEntity>

    @Query("UPDATE club_ai_profile SET patience_with_manager = :patience WHERE club_id = :clubId")
    suspend fun updatePatience(clubId: Int, patience: Int)

    @Query("SELECT COUNT(*) FROM club_ai_profile")
    suspend fun count(): Int

    // ===== T18 扩展查询接口 =====

    /** 观察全部画像（按 ambition 降序，Flow 驱动 UI） */
    @Query("SELECT * FROM club_ai_profile ORDER BY ambition DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<ClubAiProfileEntity>>

    /** 按性格查询画像列表（T18 6 种性格筛选） */
    @Query("SELECT * FROM club_ai_profile WHERE club_personality = :personality ORDER BY ambition DESC")
    suspend fun getByPersonality(personality: String): List<ClubAiProfileEntity>

    /** 按战术风格查询画像列表（T18 8 种战术筛选） */
    @Query("SELECT * FROM club_ai_profile WHERE tactical_identity = :tacticalIdentity")
    suspend fun getByTacticalIdentity(tacticalIdentity: String): List<ClubAiProfileEntity>

    /** 按长期目标查询画像列表 */
    @Query("SELECT * FROM club_ai_profile WHERE long_term_goal = :goal")
    suspend fun getByLongTermGoal(goal: String): List<ClubAiProfileEntity>

    /** 批量插入或更新（T18 画像生成器初始化用） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<ClubAiProfileEntity>)

    /** 删除所有画像（重置用） */
    @Query("DELETE FROM club_ai_profile")
    suspend fun clearAll()
}
