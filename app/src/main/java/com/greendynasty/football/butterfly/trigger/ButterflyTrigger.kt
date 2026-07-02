package com.greendynasty.football.butterfly.trigger

import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyEventStatus
import com.greendynasty.football.butterfly.model.ButterflyTriggerType
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T20 蝴蝶事件触发检测器 + 规则引擎（任务要求 2 + 5：触发判定 + 规则引擎）。
 *
 * V1 简化范围（严格遵循 T20 方案 §三 + §十四 V1 范围明确）：
 * - **触发来源 1**：T15 ButterflyEffectMarker 已写入的 pending 事件（DB 队列模式）
 * - **触发来源 2**：本检测器主动扫描关键历史转会被打断（V1 骨架，依赖 history.transfer_history）
 * - 不做完整 TransferBreakDetector / ProspectSignedDetector / ManagerLeaveDetector 三类独立检测器（V2 实现）
 * - V1 主要职责：拾取 pending 事件 + 主动检测基础转会打断
 *
 * 触发条件（V0.2 §二.1 三类蝴蝶）：
 * 1. 关键转会被打断（球员未按历史到达预期俱乐部）
 * 2. 历史新星提前签约（T15 已处理，本检测器拾取 pending）
 * 3. 豪门主帅提前离任（V2 实现）
 *
 * @param databaseManager 三库管理入口
 * @param config 蝴蝶效应配置
 */
class ButterflyTrigger(
    private val databaseManager: DatabaseManager,
    private val config: ButterflyConfig = ButterflyConfig.DEFAULT
) {

    /**
     * V1 触发检测入口（由 T07 每日推进调用 / 由 ButterflyEventService.checkAndProcessTriggers 调用）。
     *
     * 行为：
     * 1. 拾取当前存档中 status=pending 的蝴蝶事件（T15 写入的）
     * 2. 转换为领域模型返回
     *
     * V1 不主动扫描历史转会打断（依赖 history.transfer_history DAO 的 getKeyTransfersAroundDate，
     * 该查询 V2 实现；V1 仅处理 T15 已写入的 pending 事件）。
     *
     * @param ctx 推进上下文
     * @return 待处理的蝴蝶事件列表
     */
    suspend fun detectTriggers(ctx: AdvanceContext): List<ButterflyEvent> =
        withContext(Dispatchers.IO) {
            val dao = databaseManager.butterflyEventDao()
            // 拾取 pending 事件（T15 ButterflyEffectMarker 写入的）
            val pendingEntities = dao.getByStatus(ctx.saveUuid, ButterflyEventStatus.PENDING.code)
            pendingEntities.map { ButterflyEvent.fromEntity(it) }
        }

    /**
     * 主动检测关键历史转会被打断（V1 骨架，V2 完整实现）。
     *
     * V0.2 §二.1 + §十："第一版只做三类蝴蝶"之一。
     *
     * V1 简化：仅返回空列表（history.transfer_history 的按日期范围查询 V2 实现）。
     * 完整实现需：
     * 1. 查询 history.transfer_history 在 ctx.currentDate 附近的关键转会
     * 2. 检查球员当前俱乐部（save_player_state）是否为预期俱乐部
     * 3. 若不匹配且重要度 ≥ minTriggerImportance，创建蝴蝶事件
     *
     * @param ctx 推进上下文
     * @return 新检测到的蝴蝶事件列表（V1 返回空）
     */
    suspend fun detectTransferBreaks(ctx: AdvanceContext): List<ButterflyEvent> =
        withContext(Dispatchers.IO) {
            // V1 骨架：完整检测由 V2 实现（需 TransferHistoryDao.getKeyTransfersAroundDate）
            emptyList()
        }

    /**
     * 检查某球员是否已触发过蝴蝶事件（去重用）。
     *
     * V0.2 §三：每球员最多 1 个蝴蝶事件，避免级联。
     *
     * @param saveUuid 存档 UUID
     * @param playerId 球员 ID
     * @return true 表示该球员已有蝴蝶事件
     */
    suspend fun isPlayerTriggered(saveUuid: String, playerId: Int): Boolean =
        withContext(Dispatchers.IO) {
            databaseManager.butterflyEventDao().getBySourcePlayer(saveUuid, playerId) != null
        }

    /**
     * 规则引擎：判定是否应创建蝴蝶事件（任务要求 2：规则引擎）。
     *
     * 规则：
     * 1. 重要度 ≥ [ButterflyConfig.minTriggerImportance]
     * 2. 当前赛季事件数 < [ButterflyConfig.maxEventsPerSeason]
     * 3. 该球员未触发过（去重）
     *
     * @param saveUuid 存档 UUID
     * @param playerId 球员 ID
     * @param importance 事件重要度
     * @return true 表示应创建事件
     */
    suspend fun shouldCreateEvent(
        saveUuid: String,
        playerId: Int?,
        importance: Int
    ): Boolean = withContext(Dispatchers.IO) {
        // 规则 1：重要度阈值
        if (importance < config.minTriggerImportance) return@withContext false

        // 规则 2：赛季事件预算
        val eventCount = databaseManager.butterflyEventDao().countBySaveId(saveUuid)
        if (eventCount >= config.maxEventsPerSeason) return@withContext false

        // 规则 3：球员去重
        if (playerId != null) {
            val existing = databaseManager.butterflyEventDao().getBySourcePlayer(saveUuid, playerId)
            if (existing != null) return@withContext false
        }

        true
    }

    /**
     * 计算事件重要度（V0.2 §三 importance 字段）。
     *
     * 基于球员声望、转会费、俱乐部级别综合计算。
     * V1 简化：使用配置默认值，V2 接入 history.player.reputation 计算。
     *
     * @param playerId 球员 ID
     * @param transferFee 转会费（欧元）
     * @param clubReputation 预期俱乐部声望 0-100
     * @return 重要度 0-100
     */
    fun calculateImportance(
        playerId: Int?,
        transferFee: Long,
        clubReputation: Int
    ): Int {
        // V1 简化公式（V0.2 §三）：
        // transferFeeWeight = min(fee / 50_000_000, 1.0) * 30  // 5000万满分 30
        // clubLevelWeight = if (reputation > 80) 30 else 15
        // playerReputation V1 用默认值 40（V2 接入 history.player.reputation）
        val playerReputation = 40
        val transferFeeWeight = minOf(transferFee.toDouble() / 50_000_000.0, 1.0) * 30
        val clubLevelWeight = if (clubReputation > 80) 30 else 15
        val raw = playerReputation * 0.4 + transferFeeWeight + clubLevelWeight
        return raw.toInt().coerceIn(0, 100)
    }

    /**
     * 计算事件影响预算（V0.2 §三.3）。
     *
     * 重要度越高，预算越大。
     *
     * @param importance 事件重要度
     * @return 影响预算
     */
    fun calculateImpactBudget(importance: Int): Int {
        return importance.coerceIn(config.defaultImpactBudget / 2, config.defaultImpactBudget * 2)
    }

    /**
     * 构建事件摘要文案（V0.2 §八 模板）。
     */
    fun buildSummary(
        triggerType: ButterflyTriggerType,
        playerName: String,
        expectedClubName: String,
        date: LocalDate
    ): String {
        return when (triggerType) {
            ButterflyTriggerType.HISTORICAL_TRANSFER_BROKEN ->
                "$date：$playerName 未按历史转会至 $expectedClubName，历史轨迹已偏离"
            ButterflyTriggerType.PROSPECT_SIGNED_EARLY ->
                "$date：历史新星 $playerName 被提前签约，原本的 $expectedClubName 错失了这位天才"
            ButterflyTriggerType.MANAGER_LEAVE_EARLY ->
                "$date：$expectedClubName 主帅提前离任，俱乐部策略可能发生调整"
            ButterflyTriggerType.GENERIC ->
                "$date：$playerName 的历史路径已被改变（$expectedClubName）"
        }
    }
}
