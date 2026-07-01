package com.greendynasty.football.data.cache.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.cache.entity.StatsCacheEntity

/**
 * 统计缓存数据访问对象（cache.db，可重建）
 * 提供各类统计数据的缓存读写，如射手榜、助攻榜等。
 */
@Dao
interface StatsCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cache: StatsCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(caches: List<StatsCacheEntity>)

    @Update
    fun update(cache: StatsCacheEntity)

    @Delete
    fun delete(cache: StatsCacheEntity)

    @Query("SELECT * FROM stats_cache WHERE cache_key = :cacheKey")
    fun get(cacheKey: String): StatsCacheEntity?

    @Query("SELECT * FROM stats_cache ORDER BY updated_at DESC")
    fun getAll(): List<StatsCacheEntity>

    @Query("DELETE FROM stats_cache WHERE cache_key = :cacheKey")
    fun deleteByKey(cacheKey: String)

    @Query("DELETE FROM stats_cache")
    fun clear()

    @Query("SELECT COUNT(*) FROM stats_cache")
    fun count(): Int
}
