package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

/**
 * 球员数据访问对象（history.db 只读）
 * 提供球员基础信息和属性的查询，支持按位置、国籍筛选。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface PlayerDao {

    // 按 player_id 查询单个球员
    @Query("SELECT * FROM player WHERE player_id = :playerId")
    suspend fun getPlayer(playerId: Int): PlayerEntity?

    // 查询全部球员（按 player_id 排序）
    @Query("SELECT * FROM player ORDER BY player_id")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    // 按国籍筛选球员
    @Query("SELECT * FROM player WHERE nationality = :nationality ORDER BY real_name")
    fun getPlayersByNationality(nationality: String): Flow<List<PlayerEntity>>

    // 按主要位置筛选球员
    @Query("SELECT * FROM player WHERE primary_position = :position ORDER BY real_name")
    fun getPlayersByPosition(position: String): Flow<List<PlayerEntity>>

    // 按姓名模糊搜索球员
    @Query("SELECT * FROM player WHERE real_name LIKE '%' || :name || '%' ORDER BY real_name")
    fun searchPlayersByName(name: String): Flow<List<PlayerEntity>>

    // 查询某球员在某赛季的属性
    @Query("SELECT * FROM player_attributes WHERE player_id = :playerId AND season_id = :seasonId")
    suspend fun getAttributes(playerId: Int, seasonId: Int): PlayerAttributesEntity?

    // 查询某球员最新赛季属性（T08 伤病系统风险计算使用，取 season_id 最大的一条）
    @Query("SELECT * FROM player_attributes WHERE player_id = :playerId ORDER BY season_id DESC LIMIT 1")
    suspend fun getLatestAttributes(playerId: Int): PlayerAttributesEntity?

    // 查询某球员的全部赛季属性（按赛季排序）
    @Query("SELECT * FROM player_attributes WHERE player_id = :playerId ORDER BY season_id")
    fun getAllAttributes(playerId: Int): Flow<List<PlayerAttributesEntity>>

    // 查询某赛季全部球员属性
    @Transaction
    @Query("SELECT * FROM player_attributes WHERE season_id = :seasonId")
    fun getAttributesBySeason(seasonId: Int): Flow<List<PlayerAttributesEntity>>

    // 球员总数
    @Query("SELECT COUNT(*) FROM player")
    suspend fun count(): Int

    // T09 成长月结：批量查询球员基础信息（避免 N+1）
    @Query("SELECT * FROM player WHERE player_id IN (:playerIds)")
    suspend fun getPlayersByIds(playerIds: List<Int>): List<PlayerEntity>

    // T09 成长月结：批量查询球员最新赛季属性（避免 N+1）
    @Query("SELECT * FROM player_attributes WHERE player_id IN (:playerIds) ORDER BY season_id DESC")
    suspend fun getLatestAttributesBatch(playerIds: List<Int>): List<PlayerAttributesEntity>
}
