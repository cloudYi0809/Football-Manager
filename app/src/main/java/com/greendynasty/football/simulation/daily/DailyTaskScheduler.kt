package com.greendynasty.football.simulation.daily

import android.util.Log
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.api.MatchResultSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 每日任务调度器（T07 方案 §四 DailyTaskExecutor）
 *
 * 严格按 V0.1 11 §二.1 定义的顺序调度 13 项每日任务，确保依赖关系正确：
 * 1. 日期 +1（在 DailyAdvanceScheduler 已完成）
 * 2. 训练结算（仅活跃范围俱乐部）
 * 3. 体能恢复或下降（活跃范围）
 * 4. 伤病恢复（活跃范围）
 * 5. 球员士气变化（活跃范围）
 * 6. 球探任务推进（仅玩家俱乐部）
 * 7. 青训成长（活跃范围，每日轻量，月结重）
 * 8. 转会报价处理（仅转会窗开启时）
 * 9. AI 俱乐部行动（活跃范围深度，其他范围浅）
 * 10. 新闻生成
 * 11. 比赛检查（关键，若今日有比赛则调用引擎）
 * 12. 历史事件检查（V0.2 蝴蝶事件触发）
 * 13. 待办事项刷新
 *
 * @param trainingTask 训练结算
 * @param conditionTask 体能恢复
 * @param injuryRecoveryTask 伤病恢复
 * @param moraleTask 士气变化
 * @param scoutTaskProgress 球探任务
 * @param youthGrowthTask 青训成长
 * @param transferOfferTask 转会报价
 * @param aiClubTask AI 俱乐部行动
 * @param newsTask 新闻生成
 * @param matchCheckTask 比赛检查
 * @param historyEventTask 历史事件
 * @param todoRefreshTask 待办刷新
 */
class DailyTaskScheduler(
    private val trainingTask: TrainingTask,
    private val conditionTask: ConditionTask,
    private val injuryRecoveryTask: InjuryRecoveryTask,
    private val moraleTask: MoraleTask,
    private val scoutTaskProgress: ScoutTaskProgress,
    private val youthGrowthTask: YouthGrowthTask,
    private val transferOfferTask: TransferOfferTask,
    private val aiClubTask: AiClubTask,
    private val newsTask: NewsTask,
    private val matchCheckTask: MatchCheckTask,
    private val historyEventTask: HistoryEventTask,
    private val todoRefreshTask: TodoRefreshTask
) {

    /**
     * 执行 13 项每日任务（V0.1 11 §二.1）
     *
     * 严格按顺序执行，确保依赖关系正确。
     * 任一任务异常不中断后续任务（容错），仅记录告警事件。
     *
     * @param ctx 推进上下文
     * @return 当日产生的全部事件 + 比赛结果摘要
     */
    suspend fun execute(ctx: AdvanceContext): DailyTaskOutput = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val matches = mutableListOf<MatchResultSummary>()

        // 2. 训练结算（仅活跃范围俱乐部）
        events += safeExecute(trainingTask, ctx, events)

        // 3. 体能恢复或下降（活跃范围）
        events += safeExecute(conditionTask, ctx, events)

        // 4. 伤病恢复（活跃范围）
        events += safeExecute(injuryRecoveryTask, ctx, events)

        // 5. 球员士气变化（活跃范围）
        events += safeExecute(moraleTask, ctx, events)

        // 6. 球探任务推进（仅玩家俱乐部）
        events += safeExecute(scoutTaskProgress, ctx, events)

        // 7. 青训成长（活跃范围，每日轻量，月结重）
        events += safeExecute(youthGrowthTask, ctx, events)

        // 8. 转会报价处理（仅转会窗开启时）
        if (ctx.isTransferWindowOpen) {
            events += safeExecute(transferOfferTask, ctx, events)
        }

        // 9. AI 俱乐部行动（活跃范围深度，其他范围浅）
        events += safeExecute(aiClubTask, ctx, events)

        // 10. 新闻生成
        events += safeExecute(newsTask, ctx, events)

        // 11. 比赛检查（关键，若今日有比赛则调用引擎）
        val matchEvents = safeExecute(matchCheckTask, ctx, events)
        events += matchEvents
        // 提取比赛结果摘要
        matchCheckTask.lastMatchDayOutput?.matches?.let { matches.addAll(it) }

        // 12. 历史事件检查（V0.2 蝴蝶事件触发）
        events += safeExecute(historyEventTask, ctx, events)

        // 13. 待办事项刷新
        safeExecute(todoRefreshTask, ctx, events)

        DailyTaskOutput(events = events, matches = matches)
    }

    /**
     * 安全执行单个任务：捕获异常，失败时记录告警事件，不中断后续任务
     */
    private suspend fun safeExecute(
        task: DailyTask,
        ctx: AdvanceContext,
        events: MutableList<AdvanceEvent>
    ): List<AdvanceEvent> {
        return try {
            task.execute(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "任务[${task.name}]执行失败: ${e.message}", e)
            events.add(
                AdvanceEvent(
                    type = AdvanceEventType.AI_ACTION,
                    description = "任务[${task.name}]执行失败：${e.message}",
                    clubId = null,
                    playerId = null,
                    priority = EventPriority.HIGH
                )
            )
            emptyList()
        }
    }

    companion object {
        private const val TAG = "DailyTaskScheduler"
    }
}

/**
 * 每日任务执行结果
 */
data class DailyTaskOutput(
    val events: List<AdvanceEvent>,
    val matches: List<MatchResultSummary>
)
