package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.ButterflyEventEntity
import com.greendynasty.football.data.save.entity.ButterflyImpactNodeEntity

/**
 * 蝴蝶效应事件数据访问对象（save.db，V0.2）
 * 提供蝴蝶效应事件和影响节点的 CRUD。
 *
 * 协程规范：写操作与单次查询使用 suspend，事件/节点列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface ButterflyEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ButterflyEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<ButterflyEventEntity>)

    @Update
    suspend fun update(event: ButterflyEventEntity)

    @Delete
    suspend fun delete(event: ButterflyEventEntity)

    @Query("SELECT * FROM butterfly_event WHERE event_id = :eventId")
    suspend fun get(eventId: String): ButterflyEventEntity?

    @Query("SELECT * FROM butterfly_event WHERE save_id = :saveId AND status = :status ORDER BY trigger_date DESC")
    fun observeByStatus(saveId: String, status: String): kotlinx.coroutines.flow.Flow<List<ButterflyEventEntity>>

    @Query("SELECT * FROM butterfly_event WHERE save_id = :saveId AND status = :status ORDER BY trigger_date DESC")
    suspend fun getByStatus(saveId: String, status: String): List<ButterflyEventEntity>

    @Query("SELECT * FROM butterfly_event WHERE save_id = :saveId ORDER BY trigger_date DESC")
    suspend fun getAll(saveId: String): List<ButterflyEventEntity>

    @Query("UPDATE butterfly_event SET status = :status WHERE event_id = :eventId")
    suspend fun updateStatus(eventId: String, status: String)

    // ========== 影响节点 ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: ButterflyImpactNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllNodes(nodes: List<ButterflyImpactNodeEntity>)

    @Query("SELECT * FROM butterfly_impact_node WHERE event_id = :eventId ORDER BY depth")
    fun observeNodes(eventId: String): kotlinx.coroutines.flow.Flow<List<ButterflyImpactNodeEntity>>

    @Query("SELECT * FROM butterfly_impact_node WHERE event_id = :eventId ORDER BY depth")
    suspend fun getNodes(eventId: String): List<ButterflyImpactNodeEntity>

    @Query("SELECT * FROM butterfly_impact_node WHERE event_id = :eventId AND depth = :depth")
    suspend fun getNodesByDepth(eventId: String, depth: Int): List<ButterflyImpactNodeEntity>

    @Query("SELECT * FROM butterfly_impact_node WHERE target_player_id = :playerId AND status = 'pending'")
    suspend fun getPendingNodesForPlayer(playerId: Int): List<ButterflyImpactNodeEntity>

    @Query("UPDATE butterfly_impact_node SET status = :status, result_summary = :summary WHERE node_id = :nodeId")
    suspend fun updateNodeStatus(nodeId: String, status: String, summary: String?)

    @Query("SELECT COUNT(*) FROM butterfly_event WHERE save_id = :saveId AND status = 'pending'")
    suspend fun countPending(saveId: String): Int
}
