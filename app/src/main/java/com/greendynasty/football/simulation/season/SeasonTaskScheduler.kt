package com.greendynasty.football.simulation.season

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.data.save.management.CheckpointManager
import com.greendynasty.football.data.save.management.CheckpointType
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.config.ProgressionConfig
import com.greendynasty.football.simulation.stub.SeasonArchiverStub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * 赛季结束任务调度器（T07 方案 §七 SeasonEndExecutor）
 *
 * V0.1 11 §二.4 赛季结束任务（9 项）：
 * 1. 联赛排名结算
 * 2. 欧战资格
 * 3. 奖杯归属
 * 4. 球员奖项（金靴、助攻王等）
 * 5. 金球候选
 * 6. 退役名单
 * 7. 新星刷新
 * 8. 新赛季预算
 * 9. 升降级 + 赛程生成
 *
 * V0.2 §七：赛季结束强制归档 + 强制备份。
 *
 * V1 简化实现：
 * - 任务 1-5：从 save_league_table 读取排名生成赛季结算新闻（奖项/欧战资格 stub）
 * - 任务 6：退役处理 stub（T09 接入后实现）
 * - 任务 7-9：stub（T19 赛季归档 + T06 赛程生成器接入后实现）
 *
 * @param databaseManager 三库管理入口
 * @param checkpointManager checkpoint 管理器（强制备份）
 * @param config 推进配置
 * @param seasonArchiver 赛季归档 stub（T19）
 */
class SeasonTaskScheduler(
    private val databaseManager: DatabaseManager,
    private val checkpointManager: CheckpointManager,
    private val config: ProgressionConfig,
    private val seasonArchiver: SeasonArchiverStub
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 执行 9 项赛季结束任务
     *
     * @param ctx 推进上下文（isSeasonEnd=true 时调用）
     * @return 赛季结束产生的事件列表
     */
    suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        val dateStr = dateFormatter.format(ctx.currentDate)

        // 0. 赛季结束前强制备份（V0.1 11 §十 + V0.2 §七）
        if (config.forcedCheckpoint) {
            try {
                checkpointManager.createCheckpoint(ctx.saveUuid, CheckpointType.SEASON)
                Log.i(TAG, "赛季结束强制 checkpoint 已创建")
            } catch (e: Exception) {
                Log.e(TAG, "赛季结束 checkpoint 创建失败: ${e.message}", e)
            }
        }

        // 1. 联赛排名结算
        try {
            val standings = saveDb.saveLeagueTableDao()
                .getBySeasonAndCompetition(ctx.saveId, ctx.currentSeasonId, 1)
                .sortedBy { it.position }

            if (standings.isNotEmpty()) {
                val champion = standings.first()
                val championName = getClubName(champion.clubId) ?: "俱乐部${champion.clubId}"

                saveDb.saveNewsDao().insert(
                    SaveNewsEntity(
                        saveId = ctx.saveId,
                        newsDate = dateStr,
                        title = "${ctx.currentSeasonId} 赛季结束",
                        body = "冠军：$championName（${champion.points} 分）",
                        newsType = "season_end",
                        relatedPlayerId = null,
                        relatedClubId = champion.clubId,
                        isRead = 0
                    )
                )

                events.add(
                    AdvanceEvent(
                        type = AdvanceEventType.HISTORICAL_EVENT,
                        description = "${ctx.currentSeasonId} 赛季结束，冠军：$championName（${champion.points} 分）",
                        clubId = champion.clubId,
                        playerId = null,
                        priority = EventPriority.URGENT
                    )
                )

                // 2. 欧战资格（前 4 名）
                val europeanQualifiers = standings.take(4)
                val qualifierNames = mutableListOf<String>()
                for (qualifier in europeanQualifiers) {
                    qualifierNames.add(getClubName(qualifier.clubId) ?: "俱乐部${qualifier.clubId}")
                }
                events.add(
                    AdvanceEvent(
                        type = AdvanceEventType.HISTORICAL_EVENT,
                        description = "欧战资格：${qualifierNames.joinToString()}",
                        clubId = null,
                        playerId = null,
                        priority = EventPriority.HIGH
                    )
                )

                // 3. 奖杯归属（冠军）
                events.add(
                    AdvanceEvent(
                        type = AdvanceEventType.HISTORICAL_EVENT,
                        description = "联赛冠军奖杯：$championName",
                        clubId = champion.clubId,
                        playerId = null,
                        priority = EventPriority.HIGH
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "联赛排名结算失败: ${e.message}")
        }

        // 4. 球员奖项（金靴、助攻王等）- V1 stub
        // TODO: T0D 数据分析接入后统计赛季球员数据并颁发奖项

        // 5. 金球候选 - V1 stub
        // TODO: T0D 数据分析接入后评选金球候选

        // 6. 退役名单 - V1 stub
        // TODO: T09 成长系统接入后处理退役（年龄 > config.retirementAgeThreshold 的球员）

        // 7. 新星刷新 - V1 stub
        // TODO: T15 历史新星池接入后刷新下赛季新星（config.youthRefreshCount 个）

        // 8. 新赛季预算 - V1 stub
        // TODO: T17 经济系统接入后计算新赛季预算

        // 9. 升降级 + 赛程生成 - V1 stub
        // TODO: T06 赛程生成器接入后生成新赛季赛程

        // V0.2 §七：赛季归档
        if (config.archiveSeason) {
            try {
                seasonArchiver.archiveSeason(ctx)
                Log.i(TAG, "赛季归档完成")
            } catch (e: Exception) {
                Log.w(TAG, "赛季归档失败: ${e.message}")
            }
        }

        events
    }

    /**
     * 获取俱乐部名称
     */
    private suspend fun getClubName(clubId: Int): String? {
        return try {
            databaseManager.historyClubDao().getClub(clubId)?.clubName
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "SeasonTaskScheduler"
    }
}
