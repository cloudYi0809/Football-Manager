package com.greendynasty.football.simulation.api

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.simulation.active.ActiveScopeManager
import com.greendynasty.football.simulation.config.ProgressionConfig
import com.greendynasty.football.simulation.daily.DailyTaskScheduler
import com.greendynasty.football.simulation.monthly.MonthlyTaskScheduler
import com.greendynasty.football.simulation.perf.PerfLogger
import com.greendynasty.football.simulation.rollback.AdvanceRollback
import com.greendynasty.football.simulation.season.SeasonTaskScheduler
import com.greendynasty.football.simulation.weekly.WeeklyTaskScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 每日推进入口调度器（T07 方案 §三 DailyAdvanceScheduler）
 *
 * 核心职责：
 * 1. 创建推进前快照（用于失败回滚）
 * 2. 构建推进上下文 AdvanceContext
 * 3. 按顺序执行每日/每周/每月/赛季任务
 * 4. 聚合结果 + 更新存档日期
 * 5. 性能监控
 *
 * V0.1 11 §十："每日推进前可自动保存。如果推进失败，回滚到上一日。"
 *
 * @param databaseManager 三库管理入口
 * @param dailyScheduler 每日任务调度器（13 项）
 * @param weeklyScheduler 每周任务调度器（6 项）
 * @param monthlyScheduler 每月任务调度器（8 项）
 * @param seasonScheduler 赛季结束任务调度器（9 项）
 * @param activeScopeManager 活跃范围管理器
 * @param rollbackService 回滚服务
 * @param config 推进配置
 * @param perfLogger 性能日志记录器
 */
class DailyAdvanceScheduler(
    private val databaseManager: DatabaseManager,
    private val dailyScheduler: DailyTaskScheduler,
    private val weeklyScheduler: WeeklyTaskScheduler,
    private val monthlyScheduler: MonthlyTaskScheduler,
    private val seasonScheduler: SeasonTaskScheduler,
    private val activeScopeManager: ActiveScopeManager,
    private val rollbackService: AdvanceRollback,
    private val config: ProgressionConfig,
    private val perfLogger: PerfLogger
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 推进一天（V0.1 11 §十）
     *
     * 流程：
     * 1. 创建推进前快照（可选轻量 checkpoint）
     * 2. 构建推进上下文
     * 3. 执行每日任务（13 项）
     * 4. 每周任务（若周一）
     * 5. 每月任务（若月初）
     * 6. 赛季结束任务（若赛季最后一日）
     * 7. 聚合结果 + 更新存档日期
     * 8. 性能记录
     *
     * 若推进失败，回滚到上一日。
     *
     * @return 推进结果（含事件/比赛/新闻/待办/耗时，失败时 rollbackReason 非空）
     */
    suspend fun advanceOneDay(): AdvanceResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. 读取当前存档状态
        val saveState = readCurrentSaveState()
        if (saveState == null) {
            return@withContext AdvanceResult(
                newDate = LocalDate.now(),
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = System.currentTimeMillis() - startTime,
                rollbackReason = "无法读取存档状态"
            )
        }

        val currentDate = saveState.currentDate
        val nextDate = currentDate.plusDays(1)

        // 2. 创建推进前快照（用于失败回滚）
        val snapshot = rollbackService.createPreAdvanceSnapshot(
            currentDate = currentDate,
            saveUuid = saveState.saveUuid,
            createLightCheckpoint = config.lightCheckpointOnAdvance
        )

        // 3. 构建推进上下文
        val context = try {
            buildAdvanceContext(saveState, currentDate, nextDate)
        } catch (e: Exception) {
            Log.e(TAG, "构建推进上下文失败: ${e.message}", e)
            return@withContext AdvanceResult(
                newDate = currentDate,
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = System.currentTimeMillis() - startTime,
                rollbackReason = "构建上下文失败：${e.message}"
            )
        }

        // 4. 执行任务（失败回滚）
        return@withContext try {
            // 4.1 执行每日任务（13 项）
            val dailyOutput = dailyScheduler.execute(context)
            val dailyEvents = dailyOutput.events
            val matches = dailyOutput.matches

            // 4.2 每周任务（若周一）
            val weeklyEvents = if (context.isWeekStart) {
                weeklyScheduler.execute(context)
            } else {
                emptyList()
            }

            // 4.3 每月任务（若月初）
            val monthlyEvents = if (context.isMonthStart) {
                monthlyScheduler.execute(context)
            } else {
                emptyList()
            }

            // 4.4 赛季结束任务（若赛季最后一日）
            val seasonEvents = if (context.isSeasonEnd) {
                seasonScheduler.execute(context)
            } else {
                emptyList()
            }

            // 5. 聚合结果
            val allEvents = dailyEvents + weeklyEvents + monthlyEvents + seasonEvents
            val news = extractNews(allEvents)
            val todos = refreshTodos(context)

            // 6. 更新存档当前日期
            updateSaveDate(saveState, nextDate)

            // 7. 性能记录
            val duration = System.currentTimeMillis() - startTime
            perfLogger.log("daily_advance", duration, context.saveId)

            // 8. 性能告警
            if (duration > config.perfWarningMs) {
                perfLogger.warn("daily_advance 耗时 ${duration}ms 超过告警阈值 ${config.perfWarningMs}ms")
            }
            if (duration > config.perfCriticalMs) {
                perfLogger.warn("daily_advance 耗时 ${duration}ms 超过临界阈值 ${config.perfCriticalMs}ms（P95 红线）")
            }

            AdvanceResult(
                newDate = nextDate,
                events = allEvents,
                matches = matches,
                news = news,
                todos = todos,
                durationMs = duration
            )
        } catch (e: Exception) {
            // 回滚到上一日
            Log.e(TAG, "推进失败，开始回滚: ${e.message}", e)
            rollbackService.rollback(snapshot)
            AdvanceResult(
                newDate = currentDate,
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = System.currentTimeMillis() - startTime,
                rollbackReason = "推进失败：${e.message}"
            )
        }
    }

    /**
     * 推进多天（玩家可一次推进到下一比赛日/月底/转会窗结束）
     *
     * @param targetDate 目标日期（推进到此日期的前一天停止）
     * @return 每日推进结果列表
     */
    suspend fun advanceUntil(targetDate: LocalDate): List<AdvanceResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AdvanceResult>()

        while (true) {
            val currentState = readCurrentSaveState() ?: break
            if (!currentState.currentDate.isBefore(targetDate)) break

            val result = advanceOneDay()
            results.add(result)

            // 若中途回滚，停止推进
            if (result.rollbackReason != null) break
        }

        results
    }

    /**
     * 推进到下一比赛日（最常用，玩家点"下一场"）
     *
     * @return 最后一次推进结果（即比赛日当天）
     */
    suspend fun advanceToNextMatch(): AdvanceResult = withContext(Dispatchers.IO) {
        val saveState = readCurrentSaveState()
            ?: return@withContext AdvanceResult(
                newDate = LocalDate.now(),
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = 0,
                rollbackReason = "无法读取存档状态"
            )

        val nextMatchDate = findNextMatchDate(saveState)
        if (nextMatchDate == null) {
            // 无下一场比赛，推进 7 天
            val targetDate = saveState.currentDate.plusDays(7)
            val results = advanceUntil(targetDate)
            return@withContext results.lastOrNull() ?: AdvanceResult(
                newDate = saveState.currentDate,
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = 0
            )
        }

        val results = advanceUntil(nextMatchDate)
        results.lastOrNull() ?: AdvanceResult(
            newDate = saveState.currentDate,
            events = emptyList(),
            matches = emptyList(),
            news = emptyList(),
            todos = emptyList(),
            durationMs = 0
        )
    }

    /**
     * 推进到指定日期
     *
     * @param targetDate 目标日期
     * @return 最后一次推进结果
     */
    suspend fun advanceToDate(targetDate: LocalDate): AdvanceResult = withContext(Dispatchers.IO) {
        val results = advanceUntil(targetDate)
        results.lastOrNull() ?: run {
            val saveState = readCurrentSaveState()
            AdvanceResult(
                newDate = saveState?.currentDate ?: LocalDate.now(),
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = 0
            )
        }
    }

    /**
     * 休息到月底（推进到当月最后一天）
     *
     * @return 最后一次推进结果
     */
    suspend fun advanceToEndOfMonth(): AdvanceResult = withContext(Dispatchers.IO) {
        val saveState = readCurrentSaveState()
            ?: return@withContext AdvanceResult(
                newDate = LocalDate.now(),
                events = emptyList(),
                matches = emptyList(),
                news = emptyList(),
                todos = emptyList(),
                durationMs = 0,
                rollbackReason = "无法读取存档状态"
            )

        val currentDate = saveState.currentDate
        val endOfMonth = currentDate.withDayOfMonth(currentDate.lengthOfMonth())
        advanceToDate(endOfMonth)
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 读取当前存档状态（日期/赛季/玩家俱乐部/UUID）
     */
    private suspend fun readCurrentSaveState(): SaveStateSnapshot? {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return null
        val worldState = saveDb.saveWorldStateDao().get() ?: return null
        val manifest = saveDb.saveManifestDao().get() ?: return null

        val currentDate = try {
            LocalDate.parse(worldState.currentDate)
        } catch (e: Exception) {
            Log.e(TAG, "解析当前日期失败: ${worldState.currentDate}", e)
            return null
        }

        return SaveStateSnapshot(
            saveId = worldState.saveId,
            saveUuid = manifest.saveId,
            currentDate = currentDate,
            currentSeasonId = worldState.currentSeasonId,
            managerClubId = worldState.managerClubId
        )
    }

    /**
     * 构建推进上下文
     */
    private suspend fun buildAdvanceContext(
        saveState: SaveStateSnapshot,
        currentDate: LocalDate,
        nextDate: LocalDate
    ): AdvanceContext {
        val isMatchDay = isMatchDayForManager(saveState, currentDate)
        val isWeekStart = nextDate.dayOfWeek == DayOfWeek.MONDAY
        val isMonthStart = nextDate.dayOfMonth == 1
        val isSeasonEnd = config.isSeasonEndDate(nextDate)
        val isTransferWindowOpen = config.isTransferWindowOpen(currentDate)

        return AdvanceContext(
            saveId = saveState.saveId,
            saveUuid = saveState.saveUuid,
            currentDate = currentDate,
            nextDate = nextDate,
            currentSeasonId = saveState.currentSeasonId,
            managerClubId = saveState.managerClubId,
            activeLeagueIds = activeScopeManager.getActiveLeagueIds(),
            isTransferWindowOpen = isTransferWindowOpen,
            isMatchDay = isMatchDay,
            isWeekStart = isWeekStart,
            isMonthStart = isMonthStart,
            isSeasonEnd = isSeasonEnd,
            randomSeed = currentDate.toEpochDay()
        )
    }

    /**
     * 判断玩家俱乐部今日是否有比赛
     */
    private suspend fun isMatchDayForManager(saveState: SaveStateSnapshot, date: LocalDate): Boolean {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return false
        val dateStr = dateFormatter.format(date)
        val matches = saveDb.saveMatchDao()
            .getByDateRange(saveState.saveId, dateStr, dateStr)
            .filter { it.status == "scheduled" }
        return matches.any { it.homeClubId == saveState.managerClubId || it.awayClubId == saveState.managerClubId }
    }

    /**
     * 查找玩家俱乐部的下一场比赛日期
     */
    private suspend fun findNextMatchDate(saveState: SaveStateSnapshot): LocalDate? {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return null
        val currentDateStr = dateFormatter.format(saveState.currentDate)

        // 查询玩家俱乐部本赛季全部未完赛比赛
        val matches = saveDb.saveMatchDao()
            .getByClub(saveState.saveId, saveState.managerClubId, saveState.currentSeasonId)
            .filter { it.status == "scheduled" && it.matchDate >= currentDateStr }

        return matches.minByOrNull { it.matchDate }?.let {
            try {
                LocalDate.parse(it.matchDate)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 更新存档当前日期（同时更新 manifest 和 world_state）
     */
    private suspend fun updateSaveDate(saveState: SaveStateSnapshot, newDate: LocalDate) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val dateStr = dateFormatter.format(newDate)
        saveDb.saveManifestDao().updateCurrentDate(saveState.saveUuid, dateStr)
        saveDb.saveWorldStateDao().updateCurrentDate(
            saveId = saveState.saveId,
            date = dateStr,
            updatedAt = dateStr
        )
    }

    /**
     * 从事件列表提取新闻（NEWS_PUBLISHED 类型事件）
     */
    private fun extractNews(events: List<AdvanceEvent>): List<NewsItem> {
        return events
            .filter { it.type == AdvanceEventType.NEWS_PUBLISHED }
            .map {
                NewsItem(
                    title = it.description,
                    body = it.description,
                    newsType = "advance",
                    relatedClubId = it.clubId,
                    relatedPlayerId = it.playerId,
                    date = ""
                )
            }
    }

    /**
     * 刷新待办列表
     * V1 简化：返回空列表，由 ViewModel 层根据当前状态动态生成
     */
    private fun refreshTodos(ctx: AdvanceContext): List<TodoItem> {
        // TODO: 待办系统完善后根据存档状态生成待办（合同到期/伤病/转会等）
        return emptyList()
    }

    companion object {
        private const val TAG = "DailyAdvanceScheduler"
    }
}

/**
 * 存档状态快照（内部读取用）
 */
internal data class SaveStateSnapshot(
    val saveId: Int,
    val saveUuid: String,
    val currentDate: LocalDate,
    val currentSeasonId: Int,
    val managerClubId: Int
)
