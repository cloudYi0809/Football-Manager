package com.greendynasty.football.data.cache.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.cache.entity.ImagePathCacheEntity

/**
 * 图片路径缓存数据访问对象（cache.db，可重建）
 * 提供图片路径的缓存读写，避免重复查找文件。
 */
@Dao
interface ImagePathCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cache: ImagePathCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(caches: List<ImagePathCacheEntity>)

    @Update
    fun update(cache: ImagePathCacheEntity)

    @Delete
    fun delete(cache: ImagePathCacheEntity)

    @Query("SELECT * FROM image_path_cache WHERE entity_type = :entityType AND entity_id = :entityId")
    fun get(entityType: String, entityId: Int): List<ImagePathCacheEntity>

    @Query("SELECT * FROM image_path_cache WHERE entity_type = :entityType AND entity_id = :entityId AND image_type = :imageType LIMIT 1")
    fun get(entityType: String, entityId: Int, imageType: String): ImagePathCacheEntity?

    @Query("SELECT * FROM image_path_cache WHERE entity_type = :entityType")
    fun getByType(entityType: String): List<ImagePathCacheEntity>

    @Query("DELETE FROM image_path_cache WHERE entity_type = :entityType AND entity_id = :entityId")
    fun deleteByEntity(entityType: String, entityId: Int)

    @Query("DELETE FROM image_path_cache")
    fun clear()

    @Query("SELECT COUNT(*) FROM image_path_cache")
    fun count(): Int
}
