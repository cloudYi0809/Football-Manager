package com.greendynasty.football.butterfly.repository

import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.deviation.DeviationCalculator
import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.model.DeviationReport
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.ButterflyEventEntity
import com.greendynasty.football.data.save.entity.ButterflyImpactNodeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * T20 蝴蝶事件视图项（聚合事件 + 影响节点，供 UI 展示）。
 *
 * @param event 蝴蝶事件
 * @param impactNodes 该事件的影响节点列表
 * @param sourcePlayerName 源球员名称（V1 简化：null，V2 联表 history.player）
 * @param sourceClubName 源俱乐部名称
 * @param expectedClubName 预期俱乐部名称
 */
data class ButterflyEventViewItem(
    val event: ButterflyEvent,
    val impactNodes: List<ButterflyImpactNode>,
    val sourcePlayerName: String?,
    val sourceClubName: String?,
    val expectedClubName: String?
) {
    /** 事件分类显示文本。 */
    val categoryDisplay: String get() = event.category.display

    /** 触发类型显示文本。 */
    val triggerTypeDisplay: String get() = event.triggerType.code

    /** 状态显示文本。 */
    val statusDisplay: String get() = event.status.display

    /** 影响节点数。 */
    val impactNodeCount: Int get() = impactNodes.size
}

/**
 * T20 蝴蝶效应仓库（任务要求 7：数据访问层）。
 *
 * 职责：
 * 1. 封装 [ButterflyEventDao] 访问，提供 Flow / suspend 查询接口供 ViewModel 使用
 * 2. Entity ↔ 领域模型转换
 * 3. 提供偏差度量查询（委托 [DeviationCalculator]）
 * 4. 提供预算使用情况查询
 *
 * 三库分离：butterfly_event / butterfly_impact_node 均在 save.db。
 *
 * @param databaseManager 三库管理入口
 * @param config 蝴蝶效应配置
 * @param deviationCalculator 偏差计算器
 */
class ButterflyRepository(
    private val databaseManager: DatabaseManager,
    private val config: ButterflyConfig = ButterflyConfig.DEFAULT,
    private val deviationCalculator: DeviationCalculator = DeviationCalculator(config)
) {

    // ==================== 1. 事件查询 ====================

    /**
     * 观察当前存档全部蝴蝶事件（Flow 驱动 UI 刷新）。
     */
    fun observeAllEvents(saveUuid: String): Flow<List<ButterflyEvent>> {
        return databaseManager.butterflyEventDao().observeAll(saveUuid).map { entities ->
            entities.map { ButterflyEvent.fromEntity(it) }
        }
    }

    /**
     * 观察最近 N 条蝴蝶事件。
     */
    fun observeRecentEvents(saveUuid: String, limit: Int = 50): Flow<List<ButterflyEvent>> {
        return databaseManager.butterflyEventDao().observeRecent(saveUuid, limit).map { entities ->
            entities.map { ButterflyEvent.fromEntity(it) }
        }
    }

    /**
     * 一次性加载全部蝴蝶事件。
     */
    suspend fun getAllEvents(saveUuid: String): List<ButterflyEvent> = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().getAll(saveUuid).map { ButterflyEvent.fromEntity(it) }
    }

    /**
     * 加载单个事件详情。
     */
    suspend fun getEvent(eventId: String): ButterflyEvent? = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().get(eventId)?.let { ButterflyEvent.fromEntity(it) }
    }

    /**
     * 观察某事件的影响节点（详情页时间轴）。
     */
    fun observeImpactNodes(eventId: String): Flow<List<ButterflyImpactNode>> {
        return databaseManager.butterflyEventDao().observeNodes(eventId).map { entities ->
            entities.map { ButterflyImpactNode.fromEntity(it) }
        }
    }

    /**
     * 一次性加载某事件的影响节点。
     */
    suspend fun getImpactNodes(eventId: String): List<ButterflyImpactNode> =
        withContext(Dispatchers.IO) {
            databaseManager.butterflyEventDao().getNodes(eventId).map {
                ButterflyImpactNode.fromEntity(it)
            }
        }

    /**
     * 加载事件详情视图项（事件 + 影响节点 + 名称解析）。
     *
     * V1 简化：sourcePlayerName / sourceClubName 暂不联表 history.player（V2 实现），
     * 仅返回 ID 占位文本。
     */
    suspend fun getEventDetail(eventId: String): ButterflyEventViewItem? =
        withContext(Dispatchers.IO) {
            val eventEntity = databaseManager.butterflyEventDao().get(eventId)
                ?: return@withContext null
            val event = ButterflyEvent.fromEntity(eventEntity)
            val nodeEntities = databaseManager.butterflyEventDao().getNodes(eventId)
            val nodes = nodeEntities.map { ButterflyImpactNode.fromEntity(it) }

            ButterflyEventViewItem(
                event = event,
                impactNodes = nodes,
                sourcePlayerName = event.sourcePlayerId?.let { "球员#$it" },
                sourceClubName = event.sourceClubId?.let { "俱乐部#$it" },
                expectedClubName = event.expectedClubId?.let { "俱乐部#$it" }
            )
        }

    /**
     * 加载事件列表视图项（含影响节点数）。
     */
    suspend fun getEventViewItems(saveUuid: String): List<ButterflyEventViewItem> =
        withContext(Dispatchers.IO) {
            val dao = databaseManager.butterflyEventDao()
            val events = dao.getAll(saveUuid).map { ButterflyEvent.fromEntity(it) }
            events.map { event ->
                val nodes = dao.getNodes(event.eventId).map { ButterflyImpactNode.fromEntity(it) }
                ButterflyEventViewItem(
                    event = event,
                    impactNodes = nodes,
                    sourcePlayerName = event.sourcePlayerId?.let { "球员#$it" },
                    sourceClubName = event.sourceClubId?.let { "俱乐部#$it" },
                    expectedClubName = event.expectedClubId?.let { "俱乐部#$it" }
                )
            }
        }

    // ==================== 2. 偏差度量 ====================

    /**
     * 获取当前存档的历史偏差报告（任务要求 4：0-100 度量）。
     */
    suspend fun getDeviationReport(saveUuid: String): DeviationReport =
        withContext(Dispatchers.IO) {
            val dao = databaseManager.butterflyEventDao()
            val eventCount = dao.countBySaveId(saveUuid)
            val totalImportance = dao.sumImportanceBySaveId(saveUuid)
            val nodeCount = dao.countNodesBySaveId(saveUuid)
            deviationCalculator.calculateCumulativeDeviation(
                eventCount = eventCount,
                totalImportance = totalImportance,
                nodeCount = nodeCount
            )
        }

    // ==================== 3. 预算查询 ====================

    /**
     * 当前存档事件数。
     */
    suspend fun getEventCount(saveUuid: String): Int = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().countBySaveId(saveUuid)
    }

    /**
     * 当前存档影响节点数。
     */
    suspend fun getNodeCount(saveUuid: String): Int = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().countNodesBySaveId(saveUuid)
    }

    /**
     * 当前存档 pending 事件数。
     */
    suspend fun getPendingCount(saveUuid: String): Int = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().countPending(saveUuid)
    }

    /**
     * 是否还能创建新事件（预算检查）。
     */
    suspend fun canCreateEvent(saveUuid: String): Boolean = withContext(Dispatchers.IO) {
        val count = databaseManager.butterflyEventDao().countBySaveId(saveUuid)
        count < config.maxEventsPerSeason
    }

    // ==================== 4. 写入（供 ButterflyEventService 调用） ====================

    /**
     * 插入蝴蝶事件（V1：仅写 entity，影响节点由 service 单独写入）。
     */
    suspend fun insertEvent(event: ButterflyEvent) = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().insert(event.toEntity())
    }

    /**
     * 插入影响节点。
     */
    suspend fun insertNode(node: ButterflyImpactNode) = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().insertNode(node.toEntity())
    }

    /**
     * 批量插入影响节点。
     */
    suspend fun insertNodes(nodes: List<ButterflyImpactNode>) = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().insertAllNodes(nodes.map { it.toEntity() })
    }

    /**
     * 更新事件状态。
     */
    suspend fun updateEventStatus(eventId: String, status: String) = withContext(Dispatchers.IO) {
        databaseManager.butterflyEventDao().updateStatus(eventId, status)
    }

    /**
     * 更新节点状态。
     */
    suspend fun updateNodeStatus(nodeId: String, status: String, summary: String?) =
        withContext(Dispatchers.IO) {
            databaseManager.butterflyEventDao().updateNodeStatus(nodeId, status, summary)
        }
}
