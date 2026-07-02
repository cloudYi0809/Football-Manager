package com.greendynasty.football.prospect.path

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.ButterflyEventEntity
import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.data.ProspectStateEntity
import com.greendynasty.football.prospect.model.ProspectConfig
import com.greendynasty.football.prospect.model.ProspectPathEventType
import com.greendynasty.football.prospect.model.ProspectStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/**
 * T15 蝴蝶效应标记器（V0.2 06 §二.1 历史新星提前签约）。
 *
 * V1 简化范围（严格遵循 T15 方案 §十三 V1 范围明确）：
 * - 仅标记 + 通知（写入 butterfly_event + 更新 prospect_state.butterfly_triggered）
 * - 不做完整因果链传播（T20/T21 实现）
 * - 单次触发只生成 1 个蝴蝶事件（避免级联）
 *
 * 触发场景：
 * 1. 玩家提前签约历史新星（早于 defaultBreakthroughYear 或非默认一线队）
 * 2. AI 俱乐部抢签历史新星
 * 3. 默认路径被打断（球员当前俱乐部与历史转会 fromClubId 不匹配）
 *
 * @param databaseManager 三库管理入口
 * @param config 历史新星池配置
 */
class ButterflyEffectMarker(
    private val databaseManager: DatabaseManager,
    private val config: ProspectConfig = ProspectConfig.DEFAULT
) {

    /**
     * V0.2 06 §二.1 历史新星提前签约触发蝴蝶效应（V1 简化版）。
     *
     * 行为：
     * 1. 创建 ButterflyEventEntity（status=pending，等待 T20 处理）
     * 2. 更新 prospect_state.butterfly_triggered = 1, butterfly_event_id = eventId
     * 3. 写入路径事件 BUTTERFLY_TRIGGERED
     *
     * @param saveId 存档 ID
     * @param saveUuid 存档 UUID（butterfly_event.save_id 字段为 UUID）
     * @param prospectId 历史新星 ID
     * @param playerId 球员 ID
     * @param triggerType 触发类型：EARLY_SIGN / AI_SIGN / PATH_INTERRUPTED
     * @param sourceClubId 触发俱乐部 ID（玩家或 AI 俱乐部）
     * @param expectedClubId 原本应该去的俱乐部 ID（默认路径上的）
     * @param currentDate 当前游戏内日期
     * @param summary 事件摘要
     * @return 蝴蝶事件 ID（UUID），若配置关闭触发则返回 null
     */
    suspend fun markButterflyTriggered(
        saveId: Int,
        saveUuid: String,
        prospectId: Int,
        playerId: Int,
        triggerType: String,
        sourceClubId: Int?,
        expectedClubId: Int?,
        currentDate: LocalDate,
        summary: String
    ): String? = withContext(Dispatchers.IO) {
        // 1. 配置开关判定
        val shouldTrigger = when (triggerType) {
            "EARLY_SIGN", "PATH_INTERRUPTED" -> config.triggerButterflyOnEarlySign
            "AI_SIGN" -> config.triggerButterflyOnAiSign
            else -> true
        }
        if (!shouldTrigger) return@withContext null

        // 2. 检查是否已触发过（V1 简化：每新星最多 1 个蝴蝶事件，避免级联）
        val stateDao = databaseManager.prospectStateDao()
        val state = stateDao.get(saveId, prospectId)
        if (state?.butterflyTriggered == 1) return@withContext state.butterflyEventId

        // 3. 创建 ButterflyEventEntity（V1 简化：pending 状态，不传播）
        val eventId = UUID.randomUUID().toString()
        val event = ButterflyEventEntity(
            eventId = eventId,
            saveId = saveUuid,
            triggerType = triggerType,
            sourcePlayerId = playerId,
            sourceClubId = sourceClubId,
            expectedClubId = expectedClubId,
            triggerDate = currentDate.toString(),
            importance = config.butterflyDefaultImportance,
            impactBudget = config.butterflyDefaultImpactBudget,
            maxDepth = config.butterflyDefaultMaxDepth,
            status = "pending",
            summary = summary
        )
        databaseManager.butterflyEventDao().insert(event)

        // 4. 更新星状态：butterfly_triggered = 1, butterfly_event_id = eventId
        stateDao.markButterflyTriggered(saveId, prospectId, eventId)

        // 5. 写入路径事件 BUTTERFLY_TRIGGERED
        databaseManager.prospectPathEventDao().insert(
            ProspectPathEventEntity(
                saveId = saveId,
                prospectId = prospectId,
                playerId = playerId,
                eventType = ProspectPathEventType.BUTTERFLY_TRIGGERED.code,
                eventDate = currentDate.toString(),
                fromClubId = expectedClubId,
                toClubId = sourceClubId,
                isDefaultPath = 0, // 蝴蝶事件 = 干预分支
                summary = summary
            )
        )

        eventId
    }

    /**
     * 检查某历史新星是否已触发蝴蝶效应。
     */
    suspend fun isButterflyTriggered(saveId: Int, prospectId: Int): Boolean =
        withContext(Dispatchers.IO) {
            databaseManager.prospectStateDao().get(saveId, prospectId)?.butterflyTriggered == 1
        }

    /**
     * 获取触发蝴蝶效应的新星列表（用于 UI 展示蝴蝶路径图）。
     */
    suspend fun getButterflyTriggeredProspects(saveId: Int): List<ProspectStateEntity> =
        withContext(Dispatchers.IO) {
            // V1 简化：通过遍历 prospect_state 表筛选 butterfly_triggered = 1 的记录
            // 性能：V1 新星数 ≤ 500，可全表扫描；V2 可加索引优化
            databaseManager.prospectStateDao().getAll(saveId).filter { it.butterflyTriggered == 1 }
        }
}
