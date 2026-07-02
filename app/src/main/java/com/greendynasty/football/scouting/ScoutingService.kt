package com.greendynasty.football.scouting

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.core.PlayerDiscoveryEngine
import com.greendynasty.football.scouting.core.RegionTalentDensityProvider
import com.greendynasty.football.scouting.core.ScoutRegionKnowledgeManager
import com.greendynasty.football.scouting.core.ScoutReportGenerator
import com.greendynasty.football.scouting.core.ScoutTaskManager
import com.greendynasty.football.scouting.core.YouthTournamentScanner
import com.greendynasty.football.scouting.data.SaveScoutReportEntity
import com.greendynasty.football.scouting.integration.NoOpProspectDiscoveryBridge
import com.greendynasty.football.scouting.integration.ProspectDiscoveryBridge
import com.greendynasty.football.scouting.model.DispatchResult
import com.greendynasty.football.scouting.model.DispatchTaskRequest
import com.greendynasty.football.scouting.model.HireScoutResult
import com.greendynasty.football.scouting.model.ScoutingAdvanceResult
import com.greendynasty.football.scouting.model.ScoutTaskType
import com.greendynasty.football.scouting.model.ScoutWithKnowledge
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T14 球探系统入口服务（V0.2 08 §三 + §四 + §七 + T14 方案 §四.8）。
 *
 * 集成所有球探组件，对外提供统一 API：
 * ```
 * ScoutingService
 *   ├── ScoutRegionKnowledgeManager    # 球探雇佣 + 地区知识
 *   ├── ScoutTaskManager               # 任务派遣 + 状态机
 *   ├── PlayerDiscoveryEngine          # 7 因子发现概率
 *   ├── ScoutReportGenerator           # 5 级报告生成/升级
 *   └── YouthTournamentScanner         # 青年赛事扫描
 * ```
 *
 * 集成入口：
 * 1. T07 每日推进 → [advanceDaily]（球探推进作为每日推进子任务）
 * 2. UI 球探中心页 → [dispatchTask] / [cancelTask] / [hireScout] / [releaseScout]
 * 3. T10 转会搜索 → [getReportForPlayer]（报告等级影响搜索结果显示）
 * 4. T15 历史新星 → 通过 [ProspectDiscoveryBridge] 接入
 *
 * 性能：单球探推进 ≤ [ScoutConfig.scoutAdvanceBudgetMs] ms；总耗时 ≤ 6 × 50ms = 300ms / 俱乐部。
 *
 * @param databaseManager 三库管理入口
 * @param config 球探配置
 * @param prospectBridge 历史新星衔接桥（默认 NoOp，T15 完成后注入真实实现）
 */
class ScoutingService(
    private val databaseManager: DatabaseManager,
    private val config: ScoutConfig = ScoutConfig.DEFAULT,
    private val prospectBridge: ProspectDiscoveryBridge = NoOpProspectDiscoveryBridge()
) {
    private val densityProvider = RegionTalentDensityProvider(config)
    private val knowledgeManager = ScoutRegionKnowledgeManager(databaseManager, config)
    private val taskManager = ScoutTaskManager(databaseManager, config, knowledgeManager)
    private val discoveryEngine = PlayerDiscoveryEngine(databaseManager, densityProvider, prospectBridge, config)
    private val reportGenerator = ScoutReportGenerator(databaseManager, config)
    private val youthScanner = YouthTournamentScanner(databaseManager, config)

    // ==================== 1. 球探雇佣管理 ====================

    /** 雇佣球探（V0.2 08 §三.1）。 */
    suspend fun hireScout(
        saveId: Int,
        clubId: Int,
        scoutId: Int,
        currentDate: LocalDate
    ): HireScoutResult = knowledgeManager.hireScout(saveId, clubId, scoutId, currentDate)

    /** 解雇球探（V0.2 08 §三.1）。 */
    suspend fun releaseScout(saveId: Int, hiredId: Int) =
        knowledgeManager.releaseScout(saveId, hiredId)

    /** 列出俱乐部所有可用球探（含地区知识汇总）。 */
    suspend fun listScouts(saveId: Int, clubId: Int): List<ScoutWithKnowledge> =
        knowledgeManager.listScouts(saveId, clubId)

    // ==================== 2. 任务派遣 / 取消 ====================

    /** 派遣球探任务（V0.2 08 §三.4）。 */
    suspend fun dispatchTask(request: DispatchTaskRequest): DispatchResult =
        taskManager.dispatchTask(request)

    /** 取消任务（V0.2 08 §三.4）。 */
    suspend fun cancelTask(saveId: Int, taskId: Int, currentDate: LocalDate): DispatchResult =
        taskManager.cancelTask(saveId, taskId, currentDate)

    // ==================== 3. 每日推进（T07 调用） ====================

    /**
     * 球探每日推进（V0.2 08 §三.5 + §七，由 T07 每日推进第 4 步调用）。
     *
     * 流程：
     * 1. 拉取所有进行中任务
     * 2. 每个任务：
     *    - 递增已推进天数
     *    - 调用 [PlayerDiscoveryEngine.tryDiscover] 概率性发现球员
     *    - 发现球员 → [ScoutReportGenerator.generateInitialReport]（等级 1）
     *    - 调用 [ScoutReportGenerator.tryUpgradeReports] 升级现有报告
     *    - 青年赛事任务 → [YouthTournamentScanner.scan] 触发事件
     *    - 任务到期 → COMPLETED + 球探 IDLE
     * 3. 返回推进结果列表（供 T07 转换为新闻/待办）
     *
     * 性能：单球探 ≤ 50ms；6 球探 ≤ 300ms。
     */
    suspend fun advanceDaily(ctx: AdvanceContext): List<ScoutingAdvanceResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ScoutingAdvanceResult>()
            val taskDao = databaseManager.saveScoutTaskDao()
            val reportDao = databaseManager.saveScoutReportDao()
            val eventDao = databaseManager.saveScoutEventDao()
            val hiredDao = databaseManager.saveScoutHiredDao()

            val activeTasks = taskDao.getInProgress(ctx.saveId)
            if (activeTasks.isEmpty()) return@withContext results

            for (task in activeTasks) {
                val taskStart = System.currentTimeMillis()

                // 1. 递增已推进天数
                val newElapsed = task.elapsedDays + 1
                val isCompleted = newElapsed >= task.durationDays

                // 2. 触发发现判定
                val discoveries = try {
                    discoveryEngine.tryDiscover(task, ctx.currentDate)
                } catch (e: Exception) {
                    Log.w(TAG, "发现判定失败 taskId=${task.taskId}: ${e.message}")
                    emptyList()
                }

                var reportInc = 0
                for (discovery in discoveries) {
                    // 3. 生成初次报告（等级 1）
                    val hired = hiredDao.get(task.hiredId)
                    if (hired == null) continue
                    val report = reportGenerator.generateInitialReport(
                        task, discovery, hired, ctx.currentDate
                    )
                    val reportId = reportDao.insert(report)
                    val savedReport = report.copy(reportId = reportId.toInt())
                    results.add(ScoutingAdvanceResult.Discovered(discovery, savedReport))
                    reportInc++
                }

                // 4. 报告升级判定
                try {
                    val upgraded = reportGenerator.tryUpgradeReports(task, ctx.currentDate)
                    for ((report, oldLevel) in upgraded) {
                        results.add(ScoutingAdvanceResult.ReportUpgraded(report, oldLevel, report.reportLevel))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "报告升级失败 taskId=${task.taskId}: ${e.message}")
                }

                // 5. 青年赛事任务：扫描事件
                if (task.taskType == ScoutTaskType.YOUTH_TOURNAMENT.name) {
                    try {
                        val events = youthScanner.scan(task, ctx.currentDate)
                        for (event in events) {
                            eventDao.insert(event)
                            results.add(ScoutingAdvanceResult.YouthEvent(event))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "青年赛事扫描失败 taskId=${task.taskId}: ${e.message}")
                    }
                }

                // 6. 更新任务状态
                if (isCompleted) {
                    taskManager.completeTask(ctx.saveId, task.taskId, ctx.currentDate)
                    val completedTask = task.copy(
                        elapsedDays = newElapsed,
                        status = "COMPLETED",
                        lastReportDate = ctx.currentDate.toString(),
                        reportCount = task.reportCount + reportInc
                    )
                    results.add(ScoutingAdvanceResult.TaskCompleted(completedTask))
                } else {
                    taskDao.updateElapsed(
                        ctx.saveId, task.taskId, newElapsed,
                        ctx.currentDate.toString(), reportInc
                    )
                }

                // 7. 性能保护：单球探推进超过阈值记录慢日志
                val cost = System.currentTimeMillis() - taskStart
                if (cost > config.scoutAdvanceBudgetMs) {
                    Log.w(TAG, "球探推进慢: taskId=${task.taskId} cost=${cost}ms (阈值=${config.scoutAdvanceBudgetMs}ms)")
                }
            }

            results
        }

    // ==================== 4. 报告查询（T10 调用） ====================

    /**
     * 获取俱乐部对某球员的最新报告（V0.2 08 §四，供 T10 转会搜索联查）。
     *
     * T10 球员卡片渲染时调用：
     * - 无报告：CA/PA 显示"未知"
     * - 等级 1：仅显示姓名/年龄/地区/位置/初步特点
     * - 等级 2：显示 CA/PA 区间（±12）
     * - 等级 3：显示较窄 CA/PA（±7）+ 性格 + 签约难度
     * - 等级 4-5：显示隐藏标签、成长速度等
     */
    suspend fun getReportForPlayer(saveId: Int, clubId: Int, playerId: Int): SaveScoutReportEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.saveScoutReportDao().getByPlayer(saveId, playerId, clubId)
        }

    // ==================== 5. 球探推荐（玩家手动标记） ====================

    /** 设置球探推荐等级（0-100，玩家手动标记）。 */
    suspend fun setScoutRecommendation(saveId: Int, reportId: Int, level: Int) =
        withContext(Dispatchers.IO) {
            databaseManager.saveScoutReportDao()
                .updateRecommendation(saveId, reportId, level.coerceIn(0, 100))
        }

    companion object {
        private const val TAG = "ScoutingService"
    }
}
