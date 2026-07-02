package com.greendynasty.football.media.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * T24 媒体模块 DAO 集合（save.db）。
 *
 * 严格依据 `/Users/yi/Desktop/足球经理/开发文档/T24_媒体_实现方案.md` §三.5 关键 DAO 示例裁剪，
 * 按 T24 任务要求聚焦 4 张表（媒体新闻 / 舆论值 / 采访 / 采访回答）。
 *
 * 协程规范：写操作与单次查询使用 suspend，列表查询使用 Flow 观察以驱动 UI 刷新。
 *
 * 共 4 个 DAO：
 * 1. [MediaNewsDao] - 媒体新闻 CRUD + 按日期 / 分类 / 已读状态查询 + 过期清理
 * 2. [MediaOpinionDao] - 舆论值 CRUD + 按俱乐部查询 + 舆论值增量应用
 * 3. [MediaInterviewDao] - 采访会话 CRUD + 待处理采访查询 + 进度更新
 * 4. [MediaInterviewAnswerDao] - 采访回答历史 CRUD
 */

// ==================== 1. 媒体新闻 DAO ====================

@Dao
interface MediaNewsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(news: MediaNewsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(news: List<MediaNewsEntity>)

    @Update
    suspend fun update(news: MediaNewsEntity)

    @Query("SELECT * FROM media_news WHERE news_id = :newsId")
    suspend fun get(newsId: Long): MediaNewsEntity?

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId
        ORDER BY news_date DESC, importance DESC
        LIMIT :limit
    """)
    suspend fun getRecent(saveId: Int, limit: Int): List<MediaNewsEntity>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId
        ORDER BY news_date DESC, importance DESC
        LIMIT :limit
    """)
    fun observeRecent(saveId: Int, limit: Int): Flow<List<MediaNewsEntity>>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId AND news_date = :date
        ORDER BY importance DESC
    """)
    suspend fun getByDate(saveId: Int, date: String): List<MediaNewsEntity>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId AND news_date BETWEEN :from AND :to
        ORDER BY news_date DESC, importance DESC
        LIMIT :limit
    """)
    suspend fun getByDateRange(saveId: Int, from: String, to: String, limit: Int): List<MediaNewsEntity>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId AND category = :category
        ORDER BY news_date DESC, importance DESC
        LIMIT :limit
    """)
    suspend fun getByCategory(saveId: Int, category: String, limit: Int): List<MediaNewsEntity>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId AND is_read = 0
        ORDER BY news_date DESC, importance DESC
    """)
    fun observeUnread(saveId: Int): Flow<List<MediaNewsEntity>>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId AND related_player_id = :playerId
        ORDER BY news_date DESC
    """)
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<MediaNewsEntity>

    @Query("""
        SELECT * FROM media_news
        WHERE save_id = :saveId AND related_club_id = :clubId
        ORDER BY news_date DESC, importance DESC
        LIMIT :limit
    """)
    suspend fun getByClub(saveId: Int, clubId: Int, limit: Int): List<MediaNewsEntity>

    @Query("UPDATE media_news SET is_read = 1 WHERE news_id = :newsId")
    suspend fun markAsRead(newsId: Long)

    @Query("UPDATE media_news SET is_read = 1 WHERE save_id = :saveId")
    suspend fun markAllAsRead(saveId: Int)

    @Query("UPDATE media_news SET impact_applied = 1 WHERE news_id = :newsId")
    suspend fun markImpactApplied(newsId: Long)

    @Query("SELECT COUNT(*) FROM media_news WHERE save_id = :saveId AND is_read = 0")
    suspend fun countUnread(saveId: Int): Int

    @Query("SELECT COUNT(*) FROM media_news WHERE save_id = :saveId AND is_read = 0")
    fun observeUnreadCount(saveId: Int): Flow<Int>

    @Query("DELETE FROM media_news WHERE save_id = :saveId AND expire_date < :date")
    suspend fun deleteExpired(saveId: Int, date: String): Int

    @Query("DELETE FROM media_news WHERE save_id = :saveId")
    suspend fun deleteBySave(saveId: Int)
}

// ==================== 2. 媒体舆论值 DAO ====================

@Dao
interface MediaOpinionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(opinion: MediaOpinionEntity): Long

    @Query("SELECT * FROM media_opinion WHERE save_id = :saveId AND club_id = :clubId LIMIT 1")
    suspend fun get(saveId: Int, clubId: Int): MediaOpinionEntity?

    @Query("SELECT * FROM media_opinion WHERE save_id = :saveId AND club_id = :clubId LIMIT 1")
    fun observe(saveId: Int, clubId: Int): Flow<MediaOpinionEntity?>

    @Query("""
        UPDATE media_opinion
        SET opinion_value = :value,
            peak_value = MAX(peak_value, :value),
            trough_value = MIN(trough_value, :value),
            last_interaction_date = :date
        WHERE save_id = :saveId AND club_id = :clubId
    """)
    suspend fun updateValue(saveId: Int, clubId: Int, value: Int, date: String)

    @Query("""
        UPDATE media_opinion
        SET total_news_count = total_news_count + :total,
            positive_news_count = positive_news_count + :positive,
            negative_news_count = negative_news_count + :negative
        WHERE save_id = :saveId AND club_id = :clubId
    """)
    suspend fun incrementNewsCount(
        saveId: Int, clubId: Int, total: Int, positive: Int, negative: Int
    )

    @Query("DELETE FROM media_opinion WHERE save_id = :saveId")
    suspend fun deleteBySave(saveId: Int)
}

// ==================== 3. 采访会话 DAO ====================

@Dao
interface MediaInterviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interview: MediaInterviewEntity): Long

    @Update
    suspend fun update(interview: MediaInterviewEntity)

    @Query("SELECT * FROM media_interview WHERE interview_id = :interviewId")
    suspend fun get(interviewId: Long): MediaInterviewEntity?

    @Query("SELECT * FROM media_interview WHERE interview_id = :interviewId")
    fun observe(interviewId: Long): Flow<MediaInterviewEntity?>

    @Query("""
        SELECT * FROM media_interview
        WHERE save_id = :saveId AND club_id = :clubId AND status = :status
        ORDER BY scheduled_date
    """)
    suspend fun getByStatus(saveId: Int, clubId: Int, status: String): List<MediaInterviewEntity>

    @Query("""
        SELECT * FROM media_interview
        WHERE save_id = :saveId AND club_id = :clubId AND status = :status
        ORDER BY scheduled_date
        LIMIT 1
    """)
    suspend fun getNextByStatus(saveId: Int, clubId: Int, status: String): MediaInterviewEntity?

    @Query("""
        SELECT * FROM media_interview
        WHERE save_id = :saveId AND club_id = :clubId AND status IN ('pending', 'in_progress')
        ORDER BY scheduled_date
    """)
    suspend fun getActive(saveId: Int, clubId: Int): List<MediaInterviewEntity>

    @Query("""
        SELECT * FROM media_interview
        WHERE save_id = :saveId AND club_id = :clubId AND status IN ('pending', 'in_progress')
        ORDER BY scheduled_date
    """)
    fun observeActive(saveId: Int, clubId: Int): Flow<List<MediaInterviewEntity>>

    @Query("""
        SELECT * FROM media_interview
        WHERE save_id = :saveId AND club_id = :clubId
        ORDER BY scheduled_date DESC
        LIMIT :limit
    """)
    suspend fun getRecent(saveId: Int, clubId: Int, limit: Int): List<MediaInterviewEntity>

    @Query("DELETE FROM media_interview WHERE save_id = :saveId")
    suspend fun deleteBySave(saveId: Int)
}

// ==================== 4. 采访回答历史 DAO ====================

@Dao
interface MediaInterviewAnswerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(answer: MediaInterviewAnswerEntity): Long

    @Query("SELECT * FROM media_interview_answer WHERE save_id = :saveId AND interview_id = :interviewId ORDER BY answer_id")
    suspend fun getByInterview(saveId: Int, interviewId: Long): List<MediaInterviewAnswerEntity>

    @Query("""
        SELECT * FROM media_interview_answer
        WHERE save_id = :saveId
        ORDER BY answer_id DESC
        LIMIT :limit
    """)
    suspend fun getRecent(saveId: Int, limit: Int): List<MediaInterviewAnswerEntity>

    @Query("DELETE FROM media_interview_answer WHERE save_id = :saveId")
    suspend fun deleteBySave(saveId: Int)
}
