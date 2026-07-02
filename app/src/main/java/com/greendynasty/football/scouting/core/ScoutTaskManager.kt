package com.greendynasty.football.scouting.core

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.data.SaveScoutTaskEntity
import com.greendynasty.football.scouting.model.BudgetLevel
import com.greendynasty.football.scouting.model.DispatchResult
import com.greendynasty.football.scouting.model.DispatchTaskRequest
import com.greendynasty.football.scouting.model.ScoutStatus
import com.greendynasty.football.scouting.model.ScoutTaskStatus
import com.greendynasty.football.scouting.model.ScoutTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T14 球探任务管理器（V0.2 08 §三.4 + §七.2 状态机）。
 *
 * 职责：
 * 1. 任务派遣校验 + 创建（8 种任务类型 × 15 地区 × 30/60/90 天 × 低/中/高预算）
 * 2. 任务取消（状态 → CANCELLED，球探 → IDLE）
 * 3. 任务状态机推进（PENDING → IN_PROGRESS → COMPLETED/CANCELLED）
 * 4. 任务并行限制（每俱乐部 ≤ [ScoutConfig.maxActiveTasksPerClub]）
 *
 * 任务状态机：
 * - PENDING（待开始）→ 推进开始 → IN_PROGRESS
 * - IN_PROGRESS → 到期 → COMPLETED
 * - IN_PROGRESS → 取消 → CANCELLED
 *
 * 球探状态机：
 * - IDLE → 派遣 → ON_TASK
 * - ON_TASK → 完成/取消 → IDLE
 *
 * @param databaseManager 三库管理入口
 * @param config 球探配置
 * @param knowledgeManager 地区知识管理器（用于更新球探状态）
 */
class ScoutTaskManager(
    private val databaseManager: DatabaseManager,
    private val config: ScoutConfig = ScoutConfig.DEFAULT,
    private val knowledgeManager: ScoutRegionKnowledgeManager
) {

    /**
     * 派遣球探任务（V0.2 08 §三.4）。
     *
     * 校验流程：
     * 1. 球探存在且状态为 IDLE
     * 2. 任务周期 ∈ {30, 60, 90}
     * 3. 位置搜索 → 必须指定 targetPosition
     * 4. 俱乐部观察 → 必须指定 targetClubId
     * 5. 青年赛事观察 → 必须指定 youthTournamentId
     * 6. 俱乐部活跃任务数 < [ScoutConfig.maxActiveTasksPerClub]
     *
     * 创建流程：
     * 1. 计算 endDate = startDate + durationDays
     * 2. 插入 scout_task 记录
     * 3. 球探状态 → ON_TASK
     *
     * @param request 派遣请求
     * @return 派遣结果（含 taskId）
     */
    suspend fun dispatchTask(request: DispatchTaskRequest): DispatchResult =
        withContext(Dispatchers.IO) {
            val hiredDao = databaseManager.saveScoutHiredDao()
            val taskDao = databaseManager.saveScoutTaskDao()

            // 1. 校验球探状态（通过 saveId + scoutId + clubId 查找雇佣记录）
            val hired = hiredDao.getByScout(request.saveId, request.scoutId, request.clubId)
                ?: return@withContext DispatchResult(false, "球探雇佣记录不存在")
            if (hired.status != ScoutStatus.IDLE.code) {
                return@withContext DispatchResult(false, "球探正在执行任务，无法派遣")
            }

            // 2. 校验任务周期
            if (request.durationDays !in config.allowedDurations) {
                return@withContext DispatchResult(
                    false, "周期必须为 ${config.allowedDurations} 之一"
                )
            }

            // 3. 校验任务类型专属参数
            when (request.taskType) {
                ScoutTaskType.POSITION_SEARCH -> {
                    if (request.targetPosition.isNullOrBlank()) {
                        return@withContext DispatchResult(false, "位置搜索必须指定目标位置")
                    }
                }
                ScoutTaskType.CLUB_OBSERVATION -> {
                    if (request.targetClubId == null) {
                        return@withContext DispatchResult(false, "俱乐部观察必须指定目标俱乐部")
                    }
                }
                ScoutTaskType.YOUTH_TOURNAMENT -> {
                    if (request.youthTournamentId.isNullOrBlank()) {
                        return@withContext DispatchResult(false, "青年赛事观察必须指定赛事")
                    }
                }
                else -> { /* 其他任务类型无专属参数 */ }
            }

            // 4. 校验任务并行上限
            val activeCount = taskDao.countActiveByClub(request.saveId, request.clubId)
            if (activeCount >= config.maxActiveTasksPerClub) {
                return@withContext DispatchResult(
                    false, "进行中任务已达上限 ${config.maxActiveTasksPerClub}"
                )
            }

            // 5. 计算结束日期
            val endDate = request.startDate.plusDays(request.durationDays.toLong())

            // 6. 创建任务
            val task = SaveScoutTaskEntity(
                saveId = request.saveId,
                clubId = request.clubId,
                hiredId = hired.hiredId,
                scoutId = request.scoutId,
                taskType = request.taskType.name,
                regionCode = request.regionCode,
                targetPosition = request.targetPosition,
                ageMin = request.ageMin,
                ageMax = request.ageMax,
                durationDays = request.durationDays,
                budgetLevel = request.budgetLevel.name,
                startDate = request.startDate.toString(),
                endDate = endDate.toString(),
                status = ScoutTaskStatus.IN_PROGRESS.code,
                targetClubId = request.targetClubId,
                youthTournamentId = request.youthTournamentId
            )
            val taskId = taskDao.insert(task)

            // 7. 球探状态 → ON_TASK
            hiredDao.updateStatus(request.saveId, hired.hiredId, ScoutStatus.ON_TASK.code)

            DispatchResult(true, "任务已派遣", taskId)
        }

    /**
     * 取消任务（V0.2 08 §三.4）。
     *
     * 流程：任务 → CANCELLED，球探 → IDLE。
     *
     * @param saveId 存档 ID
     * @param taskId 任务 ID
     * @param currentDate 当前日期
     */
    suspend fun cancelTask(saveId: Int, taskId: Int, currentDate: LocalDate): DispatchResult =
        withContext(Dispatchers.IO) {
            val taskDao = databaseManager.saveScoutTaskDao()
            val task = taskDao.get(taskId) ?: return@withContext DispatchResult(false, "任务不存在")

            if (task.status != ScoutTaskStatus.IN_PROGRESS.code &&
                task.status != ScoutTaskStatus.PENDING.code
            ) {
                return@withContext DispatchResult(false, "任务状态不允许取消")
            }

            taskDao.updateStatus(saveId, taskId, ScoutTaskStatus.CANCELLED.code, currentDate.toString())
            knowledgeManager.updateScoutStatus(saveId, task.hiredId, ScoutStatus.IDLE)

            DispatchResult(true, "任务已取消", taskId.toLong())
        }

    /**
     * 完成任务（任务到期时由 [ScoutingService.advanceDaily] 调用）。
     *
     * 流程：任务 → COMPLETED，球探 → IDLE。
     */
    suspend fun completeTask(saveId: Int, taskId: Int, currentDate: LocalDate) =
        withContext(Dispatchers.IO) {
            val taskDao = databaseManager.saveScoutTaskDao()
            val task = taskDao.get(taskId) ?: return@withContext
            taskDao.updateStatus(saveId, taskId, ScoutTaskStatus.COMPLETED.code, currentDate.toString())
            knowledgeManager.updateScoutStatus(saveId, task.hiredId, ScoutStatus.IDLE)
        }

    /**
     * 计算任务预算成本（财务系统扣款用，V0.2 08 §三.4）。
     *
     * 成本 = durationDays × dailyBudgetCost × budgetLevel.costMultiplier
     */
    fun calculateBudgetCost(durationDays: Int, budgetLevel: BudgetLevel): Int {
        return (durationDays * config.dailyBudgetCost * budgetLevel.costMultiplier).toInt()
    }

    /**
     * 获取俱乐部所有任务（含历史，按开始日期倒序）。
     */
    fun observeClubTasks(saveId: Int, clubId: Int) =
        databaseManager.saveScoutTaskDao().observeByClub(saveId, clubId)

    /**
     * 获取俱乐部活跃任务（PENDING + IN_PROGRESS）。
     */
    fun observeActiveClubTasks(saveId: Int, clubId: Int) =
        databaseManager.saveScoutTaskDao().observeActiveByClub(saveId, clubId)
}
