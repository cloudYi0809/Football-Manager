package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import com.greendynasty.football.data.history.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

/**
 * 经纪人数据访问对象（history.db 只读）
 * 提供球员经纪人信息查询，支持按国籍筛选、按关系等级排名。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface AgentDao {

    // 按 agent_id 查询单个经纪人
    @Query("SELECT * FROM agent WHERE agent_id = :agentId")
    suspend fun getAgent(agentId: Int): AgentEntity?

    // 查询全部经纪人（按姓名排序）
    @Query("SELECT * FROM agent ORDER BY name")
    fun getAllAgents(): Flow<List<AgentEntity>>

    // 按国籍筛选经纪人
    @Query("SELECT * FROM agent WHERE nationality = :nationality ORDER BY name")
    fun getAgentsByNationality(nationality: String): Flow<List<AgentEntity>>

    // 查询关系等级最高的 N 个经纪人
    @Query("SELECT * FROM agent ORDER BY relationship_level DESC LIMIT :limit")
    fun getTopAgents(limit: Int): Flow<List<AgentEntity>>

    // 经纪人总数
    @Query("SELECT COUNT(*) FROM agent")
    suspend fun count(): Int
}
