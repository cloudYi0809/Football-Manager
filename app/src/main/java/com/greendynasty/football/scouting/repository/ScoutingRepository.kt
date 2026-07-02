package com.greendynasty.football.scouting.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.data.SaveScoutEventEntity
import com.greendynasty.football.scouting.data.SaveScoutReportEntity
import com.greendynasty.football.scouting.data.SaveScoutTaskEntity
import com.greendynasty.football.scouting.model.ScoutEventItem
import com.greendynasty.football.scouting.model.ScoutReportDetail
import com.greendynasty.football.scouting.model.ScoutReportLevel
import com.greendynasty.football.scouting.model.ScoutRegionCode
import com.greendynasty.football.scouting.model.ScoutTaskItem
import com.greendynasty.football.scouting.model.ScoutTaskType
import com.greendynasty.football.scouting.model.ScoutWithKnowledge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * T14 球探仓库（V0.2 08 §三 + §四 + §七 + T14 方案 §六 UI 数据层）。
 *
 * 职责：
 * 1. 封装 DAO 访问，提供 Flow / suspend 查询接口供 ViewModel 使用
 * 2. Entity → ViewModel 数据转换（含球探姓名/进度/剩余天数等派生字段）
 * 3. 加载报告详情（含球员基础信息 + 升级进度）
 *
 * 三库分离：history.scout / history.player 只读，save.* 可写。
 *
 * @param databaseManager 三库管理入口
 * @param config 球探配置
 */
class ScoutingRepository(
    private val databaseManager: DatabaseManager,
    private val config: ScoutConfig = ScoutConfig.DEFAULT
) {

    // ==================== 1. 球探列表 ====================

    /**
     * 加载俱乐部所有可用球探（含地区知识汇总）。
     */
    suspend fun listScouts(saveId: Int, clubId: Int): List<ScoutWithKnowledge> =
        withContext(Dispatchers.IO) {
            val hiredDao = databaseManager.saveScoutHiredDao()
            val knowledgeDao = databaseManager.saveScoutRegionKnowledgeDao()
            val historyScoutDao = databaseManager.historyScoutDao()

            val hiredList = hiredDao.getByClub(saveId, clubId)
            hiredList.mapNotNull { hired ->
                val scout = historyScoutDao.getScout(hired.scoutId) ?: return@mapNotNull null
                val knowledge = knowledgeDao.getByHired(saveId, hired.hiredId)
                ScoutWithKnowledge(scout = scout, hired = hired, regionKnowledge = knowledge)
            }
        }

    // ==================== 2. 任务列表 ====================

    /**
     * 观察俱乐部所有任务（按开始日期倒序，Flow 驱动 UI 刷新）。
     */
    fun observeClubTasks(saveId: Int, clubId: Int): Flow<List<ScoutTaskItem>> {
        return databaseManager.saveScoutTaskDao().observeByClub(saveId, clubId).map { tasks ->
            tasks.map { buildTaskItem(it) }
        }
    }

    /**
     * 观察俱乐部活跃任务（PENDING + IN_PROGRESS）。
     */
    fun observeActiveClubTasks(saveId: Int, clubId: Int): Flow<List<ScoutTaskItem>> {
        return databaseManager.saveScoutTaskDao().observeActiveByClub(saveId, clubId).map { tasks ->
            tasks.map { buildTaskItem(it) }
        }
    }

    /** 构建任务视图模型（含球探姓名/进度/剩余天数/已发现数）。 */
    private suspend fun buildTaskItem(task: SaveScoutTaskEntity): ScoutTaskItem {
        val scoutName = databaseManager.historyScoutDao().getScout(task.scoutId)?.name ?: "未知球探"
        val taskTypeDisplay = runCatching { ScoutTaskType.valueOf(task.taskType) }
            .getOrNull()?.displayName ?: task.taskType
        val regionDisplay = ScoutRegionCode.fromCode(task.regionCode)?.displayName ?: task.regionCode
        val progressPercent = if (task.durationDays > 0) {
            (task.elapsedDays * 100 / task.durationDays).coerceIn(0, 100)
        } else 0
        val remainingDays = (task.durationDays - task.elapsedDays).coerceAtLeast(0)
        val discoveredCount = databaseManager.saveScoutReportDao().countByTask(task.saveId, task.taskId)

        return ScoutTaskItem(
            task = task,
            scoutName = scoutName,
            taskTypeDisplay = taskTypeDisplay,
            regionDisplay = regionDisplay,
            progressPercent = progressPercent,
            remainingDays = remainingDays,
            discoveredCount = discoveredCount
        )
    }

    // ==================== 3. 报告列表 ====================

    /**
     * 观察俱乐部最新报告（按创建日期倒序，Flow 驱动 UI 刷新）。
     */
    fun observeClubReports(saveId: Int, clubId: Int, limit: Int = 50): Flow<List<SaveScoutReportEntity>> {
        return databaseManager.saveScoutReportDao().observeRecent(saveId, clubId, limit)
    }

    /**
     * 加载报告详情（含球员基础信息 + 升级进度，V0.2 08 §四）。
     */
    suspend fun getReportDetail(saveId: Int, reportId: Int): ScoutReportDetail? =
        withContext(Dispatchers.IO) {
            val report = databaseManager.saveScoutReportDao().get(reportId) ?: return@withContext null
            val player = databaseManager.historyPlayerDao().getPlayer(report.playerId)
            val playerState = databaseManager.savePlayerStateDao().getByPlayer(saveId, report.playerId)
            val scoutName = databaseManager.historyScoutDao().getScout(report.scoutId)?.name ?: "未知球探"

            // 计算升级到下一级所需观察天数
            val nextLevelThreshold = when (report.reportLevel) {
                1 -> config.upgradeThresholdL2
                2 -> config.upgradeThresholdL3
                3 -> config.upgradeThresholdL4
                4 -> config.upgradeThresholdL5
                else -> config.upgradeThresholdL5 // 已满级
            }
            val currentLevelDisplay = ScoutReportLevel.fromLevel(report.reportLevel)?.displayName
                ?: "等级 ${report.reportLevel}"

            ScoutReportDetail(
                report = report,
                player = player,
                playerState = playerState,
                scoutName = scoutName,
                nextLevelThreshold = nextLevelThreshold,
                currentLevelDisplay = currentLevelDisplay
            )
        }

    // ==================== 4. 事件列表 ====================

    /**
     * 观察球探事件（按发生日期倒序，Flow 驱动 UI 刷新）。
     */
    fun observeRecentEvents(saveId: Int, limit: Int = 30): Flow<List<ScoutEventItem>> {
        return databaseManager.saveScoutEventDao().observeRecent(saveId, limit).map { events ->
            events.map { buildEventItem(it) }
        }
    }

    /** 构建事件视图模型（含事件类型显示名 + 球员姓名）。 */
    private suspend fun buildEventItem(event: SaveScoutEventEntity): ScoutEventItem {
        val typeDisplay = runCatching {
            com.greendynasty.football.scouting.model.ScoutEventType.valueOf(event.eventType).display
        }.getOrDefault(event.eventType)
        val playerName = event.playerId?.let { pid ->
            databaseManager.historyPlayerDao().getPlayer(pid)?.let { it.displayName ?: it.realName }
        }
        return ScoutEventItem(
            event = event,
            eventTypeDisplay = typeDisplay,
            playerName = playerName
        )
    }

    // ==================== 5. 标记已读 ====================

    /** 标记球探事件为已读。 */
    suspend fun markEventRead(saveId: Int, eventId: Int) = withContext(Dispatchers.IO) {
        databaseManager.saveScoutEventDao().markRead(saveId, eventId)
    }
}
