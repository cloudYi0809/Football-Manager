package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import kotlinx.coroutines.flow.Flow

/**
 * 历史新星池数据访问对象（history.db 只读，V0.2）
 * 提供未来可被发现的历史新星查询，按可发现日期、地区、传奇等级筛选。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface HistoricalProspectDao {

    // 按 prospect_id 查询单个新星
    @Query("SELECT * FROM historical_prospect_pool WHERE prospect_id = :prospectId")
    suspend fun getProspect(prospectId: Int): HistoricalProspectPoolEntity?

    // 按 player_id 查询新星记录
    @Query("SELECT * FROM historical_prospect_pool WHERE player_id = :playerId LIMIT 1")
    suspend fun getProspectByPlayer(playerId: Int): HistoricalProspectPoolEntity?

    // 查询在某日期后可发现的新星（含隐藏标记过滤）
    @Transaction
    @Query("SELECT * FROM historical_prospect_pool WHERE discoverable_from <= :date AND hidden_until_discovered = 1")
    fun getDiscoverableProspects(date: String): Flow<List<HistoricalProspectPoolEntity>>

    // T15 历史新星池：按可发现日期同步查询（一次性返回，供 ProspectPoolManager 激活使用）
    @Query("SELECT * FROM historical_prospect_pool WHERE discoverable_from <= :date ORDER BY discoverable_from")
    suspend fun getAllProspectsSync(date: String): List<HistoricalProspectPoolEntity>

    // 按地区代码筛选新星
    @Query("SELECT * FROM historical_prospect_pool WHERE initial_region_code = :regionCode")
    fun getProspectsByRegion(regionCode: String): Flow<List<HistoricalProspectPoolEntity>>

    // 按传奇等级筛选新星（按等级倒序）
    @Query("SELECT * FROM historical_prospect_pool WHERE legend_level >= :minLevel ORDER BY legend_level DESC")
    fun getLegendProspects(minLevel: Int): Flow<List<HistoricalProspectPoolEntity>>

    // 查询全部新星
    @Query("SELECT * FROM historical_prospect_pool ORDER BY prospect_id")
    fun getAllProspects(): Flow<List<HistoricalProspectPoolEntity>>

    // 新星总数
    @Query("SELECT COUNT(*) FROM historical_prospect_pool")
    suspend fun count(): Int
}
