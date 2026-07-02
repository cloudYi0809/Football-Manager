package com.greendynasty.football.butterfly

import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.deviation.DeviationCalculator
import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyEventStatus
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.model.ButterflyResult
import com.greendynasty.football.butterfly.model.ButterflyTriggerType
import com.greendynasty.football.butterfly.model.DeviationReport
import com.greendynasty.football.butterfly.propagation.PropagationEngine
import com.greendynasty.football.butterfly.repository.ButterflyRepository
import com.greendynasty.football.butterfly.trigger.ButterflyTrigger
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger

/**
 * T20 蝴蝶事件服务（任务要求 7：入口服务 + T15 集成）。
 *
 * V0.2 06 §八 入口服务 + T20 方案 §八。
 *
 * 职责：
 * 1. **T15 集成**：提供 [recordEvent] 供 T15 ButterflyEffectMarker 调用记录事件
 * 2. **触发处理**：[checkAndProcessTriggers] 由 T07 每日推进调用，拾取 pending 事件并生成影响节点
 * 3. **历史改写检查**：[isEventRewritten] 供 T07 HistoryEventTask 调用，避免重复触发已改写的历史事件
 * 4. **偏差度量查询**：[getDeviationReport] 供 UI 仪表盘展示
 *
 * V1 简化范围（严格遵循 T20 方案 §十四 V1 范围明确）：
 * - 单层影响（仅生成 depth=0 节点）
 * - 偏差度量 0-100
 * - 通知存入事件表 + UI 展示
 * - 不做完整因果链 / 替代转会 / 回退机制（V2 实现）
 *
 * @param databaseManager 三库管理入口
 * @param config 蝴蝶效应配置
 */
class ButterflyEventService(
    private val databaseManager: DatabaseManager,
    private val config: ButterflyConfig = ButterflyConfig.DEFAULT
) {
    private val logger = Logger.getLogger("ButterflyEventService")

    private val deviationCalculator = DeviationCalculator(config)
    private val propagationEngine = PropagationEngine(config)
    private val trigger = ButterflyTrigger(databaseManager, config)
    private val repository = ButterflyRepository(databaseManager, config, deviationCalculator)

    // ==================== 1. T15 集成：事件记录接口 ====================

    /**
     * 记录蝴蝶事件（T15 ButterflyEffectMarker 调用入口）。
     *
     * V0.2 06 §二.1 + T20 方案 §十三 T15 衔接。
     *
     * 行为：
     * 1. 规则引擎校验（重要度阈值 / 预算 / 去重）
     * 2. 创建 ButterflyEvent（status=pending）
     * 3. 写入 butterfly_event 表
     * 4. 立即处理：生成影响节点 + 更新状态为 completed
     *
     * 与 T15 ButterflyEffectMarker.markButterflyTriggered 的关系：
     * - T15 可选择调用本方法（推荐）或直接写 DAO（兼容旧路径）
     * - 本方法额外做规则校验 + 影响生成 + 偏差更新
     *
     * @param saveUuid 存档 UUID（butterfly_event.save_id 字段）
     * @param triggerType 触发类型 code（对应 [ButterflyTriggerType.code]）
     * @param sourcePlayerId 触发球员 ID
     * @param sourceClubId 触发俱乐部 ID
     * @param expectedClubId 原本应该去的俱乐部 ID
     * @param currentDate 当前游戏内日期
     * @param importance 事件重要度 0-100
     * @param summary 事件摘要
     * @return 事件 ID（UUID），若规则校验未通过则返回 null
     */
    suspend fun recordEvent(
        saveUuid: String,
        triggerType: String,
        sourcePlayerId: Int?,
        sourceClubId: Int?,
        expectedClubId: Int?,
        currentDate: LocalDate,
        importance: Int,
        summary: String
    ): String? = withContext(Dispatchers.IO) {
        // 1. 解析触发类型
        val type = parseTriggerType(triggerType)

        // 2. 规则引擎校验
        val shouldCreate = trigger.shouldCreateEvent(saveUuid, sourcePlayerId, importance)
        if (!shouldCreate) {
            logger.log(Level.FINE, "蝴蝶事件规则校验未通过，跳过：$summary")
            return@withContext null
        }

        // 3. 创建事件
        val impactBudget = trigger.calculateImpactBudget(importance)
        val event = ButterflyEvent.create(
            saveId = saveUuid,
            triggerType = type,
            sourcePlayerId = sourcePlayerId,
            sourceClubId = sourceClubId,
            expectedClubId = expectedClubId,
            triggerDate = currentDate,
            importance = importance,
            impactBudget = impactBudget,
            summary = summary,
            triggerReason = summary
        )

        // 4. 写入事件
        repository.insertEvent(event)

        // 5. 立即处理：生成影响节点
        processEvent(event)

        // 6. 返回事件 ID
        event.eventId
    }

    // ==================== 2. 触发处理（T07 每日推进调用） ====================

    /**
     * 检查并处理触发（V0.2 06 §八 入口服务）。
     *
     * 由 T07 每日推进调用：
     * 1. 拾取 pending 事件（T15 写入的）
     * 2. 对每个事件生成影响节点
     * 3. 更新事件状态为 completed
     * 4. 返回处理结果列表
     *
     * @param ctx 推进上下文
     * @return 处理结果列表
     */
    suspend fun checkAndProcessTriggers(ctx: AdvanceContext): List<ButterflyResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ButterflyResult>()

            // 1. 检测触发（拾取 pending 事件）
            val pendingEvents = trigger.detectTriggers(ctx)

            for (event in pendingEvents) {
                // 2. 预算检查（节点预算）
                val currentNodeCount = repository.getNodeCount(ctx.saveUuid)
                if (currentNodeCount >= config.maxNodesPerSeason) {
                    logger.log(Level.FINE, "节点预算已满（$currentNodeCount），跳过事件 ${event.eventId}")
                    continue
                }

                // 3. 处理事件（生成影响节点 + 更新状态）
                val result = processEvent(event)
                if (result != null) {
                    results.add(result)
                }
            }

            results
        }

    /**
     * 处理单个事件：生成影响节点 + 更新状态为 completed。
     *
     * @param event 待处理事件
     * @return 处理结果，若事件已处理则返回 null
     */
    private suspend fun processEvent(event: ButterflyEvent): ButterflyResult? {
        // 1. 状态检查
        if (event.status == ButterflyEventStatus.COMPLETED) {
            return null
        }

        // 2. 更新为处理中
        repository.updateEventStatus(event.eventId, ButterflyEventStatus.PROCESSING.code)

        // 3. 生成影响节点（V1 单层）
        val impactNodes = propagationEngine.propagate(event)

        // 4. 持久化影响节点
        if (impactNodes.isNotEmpty()) {
            repository.insertNodes(impactNodes)
        }

        // 5. 更新事件状态为 completed
        repository.updateEventStatus(event.eventId, ButterflyEventStatus.COMPLETED.code)

        // 6. 计算偏差增量
        val deviationDelta = deviationCalculator.calculateEventDeviationDelta(event.importance)

        // 7. 计算剩余预算
        val remainingBudget = config.maxNodesPerSeason - repository.getNodeCount(event.saveId)

        return ButterflyResult(
            event = event.copy(
                status = ButterflyEventStatus.COMPLETED,
                deviationValue = deviationDelta
            ),
            impactNodes = impactNodes,
            budgetUsed = impactNodes.size,
            budgetRemaining = remainingBudget,
            deviationDelta = deviationDelta
        )
    }

    // ==================== 3. 历史改写检查（T07 HistoryEventTask 调用） ====================

    /**
     * 检查某历史事件是否已被蝴蝶效应改写（V0.2 06 §八 isEventRewritten）。
     *
     * 供 T07 HistoryEventTask 调用：若某历史事件已被改写，则不再触发原始历史。
     *
     * V1 简化：通过 source_player_id 检查该球员是否已有蝴蝶事件。
     *
     * @param playerId 历史事件关联的球员 ID
     * @param saveUuid 存档 UUID
     * @return true 表示已被改写（不再触发原始历史）
     */
    suspend fun isEventRewritten(playerId: Int, saveUuid: String): Boolean =
        withContext(Dispatchers.IO) {
            trigger.isPlayerTriggered(saveUuid, playerId)
        }

    // ==================== 4. 偏差度量查询（UI 调用） ====================

    /**
     * 获取当前存档的历史偏差报告（任务要求 4：0-100 度量）。
     *
     * @param saveUuid 存档 UUID
     * @return 偏差报告
     */
    suspend fun getDeviationReport(saveUuid: String): DeviationReport =
        withContext(Dispatchers.IO) {
            repository.getDeviationReport(saveUuid)
        }

    // ==================== 5. 便捷查询 ====================

    /**
     * 当前存档事件总数。
     */
    suspend fun getEventCount(saveUuid: String): Int = repository.getEventCount(saveUuid)

    /**
     * 当前存档影响节点总数。
     */
    suspend fun getNodeCount(saveUuid: String): Int = repository.getNodeCount(saveUuid)

    /**
     * 当前存档 pending 事件数。
     */
    suspend fun getPendingCount(saveUuid: String): Int = repository.getPendingCount(saveUuid)

    /**
     * 获取蝴蝶事件仓库（供 ViewModel 使用）。
     */
    fun getRepository(): ButterflyRepository = repository

    /**
     * 获取触发检测器（供外部调用规则引擎）。
     */
    fun getTrigger(): ButterflyTrigger = trigger

    // ==================== 工具 ====================

    /**
     * 解析触发类型 code（兼容 T15 写入的 EARLY_SIGN / AI_SIGN / PATH_INTERRUPTED）。
     *
     * T15 ButterflyEffectMarker 写入的 triggerType 字段值为：
     * - "EARLY_SIGN" / "AI_SIGN" / "PATH_INTERRUPTED"（T15 自定义）
     * - 本服务统一归类为 GENERIC
     *
     * V2 可建立 T15 triggerType → T20 ButterflyTriggerType 的精确映射表。
     */
    private fun parseTriggerType(code: String): ButterflyTriggerType {
        // 先尝试精确匹配 T20 code
        ButterflyTriggerType.values().firstOrNull { it.code == code }?.let { return it }
        // 兼容 T15 自定义 code
        return when (code) {
            "EARLY_SIGN", "AI_SIGN" -> ButterflyTriggerType.PROSPECT_SIGNED_EARLY
            "PATH_INTERRUPTED" -> ButterflyTriggerType.HISTORICAL_TRANSFER_BROKEN
            else -> ButterflyTriggerType.GENERIC
        }
    }
}
