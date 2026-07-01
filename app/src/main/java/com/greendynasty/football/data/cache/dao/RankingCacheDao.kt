package com.greendynasty.football.data.cache.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.cache.entity.RankingCacheEntity

/**
 * 积分榜缓存数据访问对象（cache.db，可重建）
 * 提供各联赛/赛事积分榜的缓存读写。
 */
@Dao
interface RankingCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cache: RankingCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(caches: List<RankingCacheEntity>)

    @Update
    fun update(cache: RankingCacheEntity)

    @Delete
    fun delete(cache: RankingCacheEntity)

    @Query("SELECT * FROM ranking_cache WHERE cache_key = :cacheKey")
    fun get(cacheKey: String): RankingCacheEntity?

    @Query("SELECT * FROM ranking_cache ORDER BY updated_at DESC")
    fun getAll(): List<RankingCacheEntity>

    @Query("SELECT * FROM ranking_cache WHERE expires_at IS NOT NULL AND expires_at < :now")
    fun getExpired(now: String): List<RankingCacheEntity>

    @Query("DELETE FROM ranking_cache WHERE cache_key = :cacheKey")
    fun deleteByKey(cacheKey: String)

    @Query("DELETE FROM ranking_cache WHERE expires_at IS NOT NULL AND expires_at < :now")
    fun deleteExpired(now: String)

    @Query("DELETE FROM ranking_cache")
    fun clear()

    @Query("SELECT COUNT(*) FROM ranking_cache")
    fun count(): Int
}
