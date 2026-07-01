package com.greendynasty.football.simulation.daily

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.simulation.active.ActiveScopeManager
import com.greendynasty.football.simulation.active.ClubSimulationDepth
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.config.ProgressionConfig
import com.greendynasty.football.simulation.matchday.MatchDayExecutor
import com.greendynasty.football.simulation.matchday.MatchDayOutput
import com.greendynasty.football.simulation.stub.AiSchedulerStub
import com.greendynasty.football.simulation.stub.ButterflyEventServiceStub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * 每日任务公共接口（T07 方案 §四）
 *
 * 所有每日任务实现此接口，由 DailyTaskScheduler 按顺序调度。
 */
interface DailyTask {
    /** 任务名称（用于日志） */
    val name: String

    /** 执行任务，返回产生的事件列表 */
    suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent>
}

// ==================== 2. 训练结算 ====================

/**
 * 训练结算任务（V0.1 11 §二.1.2）
 *
 * 活跃范围俱乐部每日训练：
 * - 球员训练进度积累（月结时兑现）
 * - 体能消耗
 * - 比赛日不训练
 */
class TrainingTask(
    private val databaseManager: DatabaseManager,
    private val activeScopeManager: ActiveScopeManager,
    private val config: ProgressionConfig
) : DailyTask {

    override val name = "训练结算"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        if (!config.trainingEnabled) return@withContext emptyList()
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()

        // 获取活跃范围全部俱乐部（V1 简化：从 save_club_state 全量读取）
        val clubs = saveDb.saveClubStateDao().getAll(ctx.saveId)

        for (club in clubs) {
            val depth = activeScopeManager.getClubDepth(club.clubId, ctx)
            if (depth == ClubSimulationDepth.LIGHT || depth == ClubSimulationDepth.MINIMAL) continue

            // 比赛日玩家俱乐部不训练
            if (ctx.isMatchDay && club.clubId == ctx.managerClubId) continue

            val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, club.clubId)
            for (player in players) {
                if (player.injuryStatus != "healthy") continue

                // 训练质量 = 训练等级 × 士气（V1 简化，待 T0Y 训练系统完善）
                val trainingQuality = calculateTrainingQuality(club.dressingRoomMorale)
                val conditionLoss = (5 * trainingQuality).toInt()

                // 体能消耗
                val newCondition = (player.condition - conditionLoss).coerceAtLeast(0)
                saveDb.savePlayerStateDao().updateCondition(ctx.saveId, player.playerId, newCondition)
            }
        }

        events
    }

    private fun calculateTrainingQuality(morale: Int): Double {
        // V1 简化：训练质量 = 基础 0.8 × 士气系数
        val moraleFactor = (morale + 50) / 150.0
        return 0.8 * moraleFactor
    }
}

// ==================== 3. 体能恢复 ====================

/**
 * 体能恢复/下降任务（V0.1 11 §二.1.3）
 *
 * 非比赛日体能恢复，比赛日体能已由 MatchDayExecutor 处理。
 */
class ConditionTask(
    private val databaseManager: DatabaseManager,
    private val activeScopeManager: ActiveScopeManager,
    private val config: ProgressionConfig
) : DailyTask {

    override val name = "体能恢复"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        val clubs = saveDb.saveClubStateDao().getAll(ctx.saveId)

        for (club in clubs) {
            val depth = activeScopeManager.getClubDepth(club.clubId, ctx)
            if (depth == ClubSimulationDepth.MINIMAL) continue

            // 玩家俱乐部比赛日不额外恢复（比赛消耗已在 MatchDayExecutor 处理）
            if (ctx.isMatchDay && club.clubId == ctx.managerClubId) continue

            val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, club.clubId)
            for (player in players) {
                if (player.condition >= 100) continue
                val recovery = config.conditionRecoveryPerDay
                val newCondition = (player.condition + recovery).coerceAtMost(100)
                saveDb.savePlayerStateDao().updateCondition(ctx.saveId, player.playerId, newCondition)
            }
        }
        events
    }
}

// ==================== 4. 伤病恢复 ====================

/**
 * 伤病恢复任务（V0.1 11 §二.1.4）
 *
 * 检查活跃范围俱乐部伤病球员是否到恢复日期。
 */
class InjuryRecoveryTask(
    private val databaseManager: DatabaseManager,
    private val activeScopeManager: ActiveScopeManager,
    private val config: ProgressionConfig
) : DailyTask {

    override val name = "伤病恢复"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()

        // 查询全部活跃伤病
        val activeInjuries = saveDb.saveInjuryDao().getAllActive(ctx.saveId)

        for (injury in activeInjuries) {
            val expectedReturn = try {
                LocalDate.parse(injury.expectedReturnDate)
            } catch (e: Exception) {
                continue
            }

            // 检查是否到恢复日期
            if (ctx.nextDate.isAfter(expectedReturn) || ctx.nextDate.isEqual(expectedReturn)) {
                // 恢复
                saveDb.saveInjuryDao().updateStatus(injury.injuryId, "recovered")
                saveDb.savePlayerStateDao().updateInjuryStatus(ctx.saveId, injury.playerId, "healthy", null)

                // 体能恢复到 70-85
                val recoveryCondition = if (injury.recurrenceRisk < 30) 85 else 70
                saveDb.savePlayerStateDao().updateCondition(ctx.saveId, injury.playerId, recoveryCondition)

                events.add(
                    AdvanceEvent(
                        type = AdvanceEventType.INJURY_RECOVERED,
                        description = "球员 ${injury.playerId} 伤病恢复",
                        clubId = null,
                        playerId = injury.playerId,
                        priority = EventPriority.MEDIUM
                    )
                )
            }
        }
        events
    }
}

// ==================== 5. 球员士气变化 ====================

/**
 * 球员士气变化任务（V0.1 11 §二.1.5）
 *
 * 活跃范围球员士气自然衰减/恢复。
 */
class MoraleTask(
    private val databaseManager: DatabaseManager,
    private val activeScopeManager: ActiveScopeManager,
    private val config: ProgressionConfig
) : DailyTask {

    override val name = "士气变化"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        val clubs = saveDb.saveClubStateDao().getAll(ctx.saveId)

        for (club in clubs) {
            val depth = activeScopeManager.getClubDepth(club.clubId, ctx)
            if (depth == ClubSimulationDepth.MINIMAL) continue

            val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, club.clubId)
            for (player in players) {
                // 士气向俱乐部更衣室士气靠拢（V1 简化）
                val targetMorale = club.dressingRoomMorale
                val diff = targetMorale - player.morale
                if (abs(diff) > 2) {
                    val change = (diff * config.moraleDecayRate).toInt()
                    val newMorale = (player.morale + change).coerceIn(0, 100)
                    saveDb.savePlayerStateDao().updateMorale(ctx.saveId, player.playerId, newMorale)
                }
            }
        }
        events
    }
}

// ==================== 6. 球探任务推进 ====================

/**
 * 球探任务推进（V0.1 11 §二.1.6）
 * V1 stub：待 T14 球探任务系统实现后接入。
 */
class ScoutTaskProgress(
    private val databaseManager: DatabaseManager
) : DailyTask {

    override val name = "球探任务"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> {
        // TODO: T14 球探任务系统接入后实现每日球探任务推进
        return emptyList()
    }
}

// ==================== 7. 青训成长 ====================

/**
 * 青训成长任务（V0.1 11 §二.1.7）
 * V1 stub：每日轻量，月结重。待 T16 青训学院系统实现后接入。
 */
class YouthGrowthTask(
    private val databaseManager: DatabaseManager
) : DailyTask {

    override val name = "青训成长"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> {
        // TODO: T16 青训学院系统接入后实现每日青训成长
        return emptyList()
    }
}

// ==================== 8. 转会报价处理 ====================

/**
 * 转会报价处理任务（V0.1 11 §二.1.8）
 * 仅转会窗开启时执行。V1 stub：待 T10-T13 转会系统实现后接入。
 */
class TransferOfferTask(
    private val databaseManager: DatabaseManager
) : DailyTask {

    override val name = "转会报价"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> {
        if (!ctx.isTransferWindowOpen) return emptyList()
        // TODO: T10-T13 转会系统接入后实现每日转会报价处理
        return emptyList()
    }
}

// ==================== 9. AI 俱乐部行动 ====================

/**
 * AI 俱乐部行动任务（V0.1 11 §二.1.9）
 * 活跃范围深度模拟，非活跃范围简化。
 */
class AiClubTask(
    private val aiScheduler: AiSchedulerStub,
    private val databaseManager: DatabaseManager
) : DailyTask {

    override val name = "AI俱乐部行动"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()

        // 活跃范围：调用 AI 调度器 stub
        aiScheduler.onDailyAdvance(ctx.currentDate, ctx.saveId)

        // TODO: T18 接入后处理非活跃范围俱乐部的简化 AI 行动（合同到期检查等）

        events
    }
}

// ==================== 10. 新闻生成 ====================

/**
 * 新闻生成任务（V0.1 11 §二.1.10）
 * V1 简化：基于新闻生成概率随机生成普通新闻。
 */
class NewsTask(
    private val databaseManager: DatabaseManager,
    private val config: ProgressionConfig
) : DailyTask {

    override val name = "新闻生成"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        val random = Random(ctx.randomSeed)
        val dateStr = ctx.currentDate.toString()

        // 按概率生成新闻
        if (random.nextDouble() < config.newsGenerationRate) {
            val newsTitles = listOf(
                "联赛进入新阶段" to "league",
                "转会市场动态" to "transfer",
                "俱乐部日常训练" to "training",
                "球迷关注球队表现" to "fan"
            )
            val (title, type) = newsTitles.random(random)

            saveDb.saveNewsDao().insert(
                SaveNewsEntity(
                    saveId = ctx.saveId,
                    newsDate = dateStr,
                    title = title,
                    body = "$dateStr - $title",
                    newsType = type,
                    relatedPlayerId = null,
                    relatedClubId = ctx.managerClubId,
                    isRead = 0
                )
            )

            events.add(
                AdvanceEvent(
                    type = AdvanceEventType.NEWS_PUBLISHED,
                    description = title,
                    clubId = ctx.managerClubId,
                    playerId = null,
                    priority = EventPriority.LOW
                )
            )
        }

        events
    }
}

// ==================== 11. 比赛检查 ====================

/**
 * 比赛检查任务（V0.1 11 §二.1.11）
 * 委托 MatchDayExecutor 执行当日全部比赛模拟。
 */
class MatchCheckTask(
    private val matchDayExecutor: MatchDayExecutor
) : DailyTask {

    override val name = "比赛检查"

    /** 上次执行的比赛结果（供 Scheduler 聚合） */
    var lastMatchDayOutput: MatchDayOutput? = null
        private set

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> {
        val output = matchDayExecutor.execute(ctx)
        lastMatchDayOutput = output
        return output.events
    }
}

// ==================== 12. 历史事件检查 ====================

/**
 * 历史事件检查任务（V0.1 11 §二.1.12）
 * 检查历史新星是否到出现时间，V0.2 蝴蝶事件集成。
 */
class HistoryEventTask(
    private val databaseManager: DatabaseManager,
    private val butterflyService: ButterflyEventServiceStub
) : DailyTask {

    override val name = "历史事件"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val dateStr = ctx.currentDate.toString()

        // 检查历史新星是否到发现时间
        try {
            val prospects = databaseManager.historyProspectDao()
                .getDiscoverableProspects(dateStr)
            // Flow 需要 first() 收集，V1 简化为空（避免阻塞）
            // TODO: T15 历史新星池接入后完善发现逻辑
        } catch (e: Exception) {
            Log.w("HistoryEventTask", "历史新星查询失败: ${e.message}")
        }

        // TODO: T20 蝴蝶效应系统接入后检查历史事件触发
        // V1 stub：butterflyService.isEventRewritten 返回 false

        events
    }
}

// ==================== 13. 待办事项刷新 ====================

/**
 * 待办事项刷新任务（V0.1 11 §二.1.13）
 * V1 简化：生成基础待办列表。
 */
class TodoRefreshTask(
    private val databaseManager: DatabaseManager
) : DailyTask {

    override val name = "待办刷新"

    override suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> {
        // V1 简化：待办由 ViewModel 层根据当前状态动态生成
        // TODO: 待办系统完善后在此生成每日待办（合同到期/伤病/转会等）
        return emptyList()
    }
}
