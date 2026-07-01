package com.greendynasty.football.simulation.weekly

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.config.ProgressionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 每周任务调度器（T07 方案 §五 WeeklyTaskExecutor）
 *
 * V0.1 11 §二.2 每周任务（周一执行），共 6 项：
 * 1. 训练周报（玩家俱乐部）
 * 2. 青训周报
 * 3. 球探周报
 * 4. 更衣室状态
 * 5. 数据分析报告
 * 6. 联赛新闻
 *
 * 仅在周一（isWeekStart=true）执行。V1 简化实现：
 * - 训练/青训/球探周报：基于存档状态生成摘要新闻
 * - 更衣室状态：根据球员士气均值更新俱乐部更衣室士气
 * - 数据分析报告：V1 stub
 * - 联赛新闻：生成 1 条联赛动态新闻
 *
 * @param databaseManager 三库管理入口
 * @param config 推进配置
 */
class WeeklyTaskScheduler(
    private val databaseManager: DatabaseManager,
    private val config: ProgressionConfig
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 执行 6 项每周任务
     *
     * @param ctx 推进上下文
     * @return 每周任务产生的事件列表
     */
    suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        val dateStr = dateFormatter.format(ctx.currentDate)
        val random = Random(ctx.randomSeed + 7L)

        // 1. 训练周报（玩家俱乐部）
        try {
            val trainingReport = generateTrainingReport(ctx)
            saveDb.saveNewsDao().insert(
                SaveNewsEntity(
                    saveId = ctx.saveId,
                    newsDate = dateStr,
                    title = "本周训练报告",
                    body = trainingReport,
                    newsType = "training",
                    relatedPlayerId = null,
                    relatedClubId = ctx.managerClubId,
                    isRead = 0
                )
            )
            events.add(
                AdvanceEvent(
                    type = AdvanceEventType.TRAINING_COMPLETE,
                    description = "本周训练报告：$trainingReport",
                    clubId = ctx.managerClubId,
                    playerId = null,
                    priority = EventPriority.MEDIUM
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "训练周报生成失败: ${e.message}")
        }

        // 2. 青训周报（V1 stub）
        // TODO: T16 青训学院接入后生成详细青训周报

        // 3. 球探周报（V1 stub）
        // TODO: T14 球探任务接入后生成本周球探报告汇总

        // 4. 更衣室状态（根据球员士气均值更新俱乐部更衣室士气）
        try {
            updateDressingRoomStatus(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "更衣室状态更新失败: ${e.message}")
        }

        // 5. 数据分析报告（V1 stub）
        // TODO: T0D 数据分析接入后生成报告

        // 6. 联赛新闻（生成 1 条联赛动态）
        try {
            if (random.nextDouble() < 0.6) {
                val leagueNewsTitles = listOf(
                    "本周联赛最佳阵容出炉",
                    "联赛射手榜更新",
                    "本轮比赛前瞻分析",
                    "联赛转会动态汇总"
                )
                val title = leagueNewsTitles.random(random)
                saveDb.saveNewsDao().insert(
                    SaveNewsEntity(
                        saveId = ctx.saveId,
                        newsDate = dateStr,
                        title = title,
                        body = "$dateStr - $title",
                        newsType = "league",
                        relatedPlayerId = null,
                        relatedClubId = null,
                        isRead = 0
                    )
                )
                events.add(
                    AdvanceEvent(
                        type = AdvanceEventType.NEWS_PUBLISHED,
                        description = title,
                        clubId = null,
                        playerId = null,
                        priority = EventPriority.LOW
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "联赛新闻生成失败: ${e.message}")
        }

        events
    }

    /**
     * 生成训练周报摘要
     * V1 简化：基于玩家俱乐部球员的体能均值生成报告
     */
    private suspend fun generateTrainingReport(ctx: AdvanceContext): String {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return "无数据"
        val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, ctx.managerClubId)
        if (players.isEmpty()) return "阵容为空"

        val avgCondition = players.map { it.condition }.average().toInt()
        val avgMorale = players.map { it.morale }.average().toInt()
        val injuredCount = players.count { it.injuryStatus != "healthy" }

        return "平均体能 $avgCondition，平均士气 $avgMorale，伤病 $injuredCount 人"
    }

    /**
     * 更新更衣室状态
     * V1 简化：更衣室士气 = 球员士气均值
     */
    private suspend fun updateDressingRoomStatus(ctx: AdvanceContext) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, ctx.managerClubId)
        if (players.isEmpty()) return

        val avgMorale = players.map { it.morale }.average().toInt().coerceIn(0, 100)
        val club = saveDb.saveClubStateDao().getByClub(ctx.saveId, ctx.managerClubId) ?: return
        if (club.dressingRoomMorale != avgMorale) {
            saveDb.saveClubStateDao().update(
                club.copy(dressingRoomMorale = avgMorale)
            )
        }
    }

    companion object {
        private const val TAG = "WeeklyTaskScheduler"
    }
}
