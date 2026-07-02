package com.greendynasty.football.data.cache.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.cache.entity.PlayerSearchIndexEntity

/**
 * 球员搜索索引数据访问对象（cache.db，可重建）
 * 提供转会市场球员搜索的快速索引查询，支持按名称、位置、CA、俱乐部筛选。
 */
@Dao
interface PlayerSearchIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(index: PlayerSearchIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(indices: List<PlayerSearchIndexEntity>)

    @Update
    fun update(index: PlayerSearchIndexEntity)

    @Delete
    fun delete(index: PlayerSearchIndexEntity)

    @Query("SELECT * FROM player_search_index WHERE player_id = :playerId")
    fun get(playerId: Int): PlayerSearchIndexEntity?

    @Query("SELECT * FROM player_search_index WHERE search_tokens LIKE '%' || :keyword || '%' ORDER BY current_ca DESC")
    fun search(keyword: String): List<PlayerSearchIndexEntity>

    @Query("SELECT * FROM player_search_index WHERE position = :position ORDER BY current_ca DESC LIMIT :limit")
    fun getByPosition(position: String, limit: Int): List<PlayerSearchIndexEntity>

    @Query("SELECT * FROM player_search_index WHERE current_ca BETWEEN :minCa AND :maxCa ORDER BY current_ca DESC LIMIT :limit")
    fun getByCaRange(minCa: Int, maxCa: Int, limit: Int): List<PlayerSearchIndexEntity>

    @Query("SELECT * FROM player_search_index WHERE age BETWEEN :minAge AND :maxAge ORDER BY current_ca DESC LIMIT :limit")
    fun getByAgeRange(minAge: Int, maxAge: Int, limit: Int): List<PlayerSearchIndexEntity>

    @Query("SELECT * FROM player_search_index WHERE current_club_id = :clubId ORDER BY current_ca DESC")
    fun getByClub(clubId: Int): List<PlayerSearchIndexEntity>

    @Query("SELECT * FROM player_search_index ORDER BY current_ca DESC LIMIT :limit")
    fun getTopPlayers(limit: Int): List<PlayerSearchIndexEntity>

    // T10 转会搜索：复合索引查询（位置/CA/年龄/身价 + 分页）
    @Query(
        """
        SELECT * FROM player_search_index
        WHERE (:positionsEmpty = 1 OR position IN (:positions))
          AND (:caMin IS NULL OR current_ca >= :caMin)
          AND (:caMax IS NULL OR current_ca <= :caMax)
          AND (:ageMin IS NULL OR age >= :ageMin)
          AND (:ageMax IS NULL OR age <= :ageMax)
          AND (:maxValue IS NULL OR market_value <= :maxValue)
        ORDER BY current_ca DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchMulti(
        positions: List<String>,
        positionsEmpty: Int,
        caMin: Int?,
        caMax: Int?,
        ageMin: Int?,
        ageMax: Int?,
        maxValue: Int?,
        limit: Int,
        offset: Int
    ): List<PlayerSearchIndexEntity>

    @Query("SELECT COUNT(*) FROM player_search_index")
    fun count(): Int

    @Query("DELETE FROM player_search_index")
    fun clear()
}
