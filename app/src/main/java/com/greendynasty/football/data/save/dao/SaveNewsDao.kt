package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.SaveNewsEntity

/**
 * 存档新闻数据访问对象（save.db）
 * 提供游戏新闻消息的 CRUD，支持按日期、类型、已读状态查询。
 *
 * 协程规范：写操作与单次查询使用 suspend，新闻列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface SaveNewsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(news: SaveNewsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(news: List<SaveNewsEntity>)

    @Update
    suspend fun update(news: SaveNewsEntity)

    @Delete
    suspend fun delete(news: SaveNewsEntity)

    @Query("SELECT * FROM save_news WHERE news_id = :newsId")
    suspend fun get(newsId: Int): SaveNewsEntity?

    @Query("SELECT * FROM save_news WHERE save_id = :saveId ORDER BY news_date DESC LIMIT :limit")
    fun observeRecent(saveId: Int, limit: Int): kotlinx.coroutines.flow.Flow<List<SaveNewsEntity>>

    @Query("SELECT * FROM save_news WHERE save_id = :saveId ORDER BY news_date DESC LIMIT :limit")
    suspend fun getRecent(saveId: Int, limit: Int): List<SaveNewsEntity>

    @Query("SELECT * FROM save_news WHERE save_id = :saveId AND is_read = 0 ORDER BY news_date DESC")
    fun observeUnread(saveId: Int): kotlinx.coroutines.flow.Flow<List<SaveNewsEntity>>

    @Query("SELECT * FROM save_news WHERE save_id = :saveId AND is_read = 0 ORDER BY news_date DESC")
    suspend fun getUnread(saveId: Int): List<SaveNewsEntity>

    @Query("SELECT * FROM save_news WHERE save_id = :saveId AND news_type = :type ORDER BY news_date DESC")
    suspend fun getByType(saveId: Int, type: String): List<SaveNewsEntity>

    @Query("SELECT * FROM save_news WHERE save_id = :saveId AND related_player_id = :playerId ORDER BY news_date DESC")
    suspend fun getByPlayer(saveId: Int, playerId: Int): List<SaveNewsEntity>

    @Query("UPDATE save_news SET is_read = 1 WHERE news_id = :newsId")
    suspend fun markAsRead(newsId: Int)

    @Query("UPDATE save_news SET is_read = 1 WHERE save_id = :saveId")
    suspend fun markAllAsRead(saveId: Int)

    @Query("SELECT COUNT(*) FROM save_news WHERE save_id = :saveId AND is_read = 0")
    suspend fun countUnread(saveId: Int): Int

    @Query("DELETE FROM save_news WHERE save_id = :saveId AND is_read = 1 AND news_date < :beforeDate")
    suspend fun deleteOldRead(saveId: Int, beforeDate: String)
}
