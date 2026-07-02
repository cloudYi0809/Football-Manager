package com.greendynasty.football.divergence.archive

import android.util.Log
import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.model.ButterflyEventStatus
import com.greendynasty.football.butterfly.repository.ButterflyRepository
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.divergence.DivergenceTimelineComposer
import com.greendynasty.football.divergence.generator.DivergenceTextGenerator
import com.greendynasty.football.divergence.model.DivergenceArchiveSummary
import com.greendynasty.football.divergence.model.DivergenceLog
import com.greendynasty.football.divergence.model.NoReplacementRecord
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * T21 分歧归档服务（任务 T21.4 + T21.5）。
 *
 * 职责：
 * 1. **赛季归档**：赛季结束时将当前赛季的蝴蝶事件归档到 divergence_archive 表
 *    （复用 T19 SeasonArchiver 机制：在 save.db 事务内执行）
 * 2. **无重大替代记录**：识别并记录"历史分歧未产生重大替代"的事件
 *    （V0.2 §七：允许蝴蝶事件不产生连锁反应）
 * 3. **归档后状态更新**：将原 butterfly_event 状态更新为 ARCHIVED
 *
 * 归档流程：
 * 1. 从 ButterflyRepository 加载当前存档全部事件
 * 2. 通过 DivergenceTimelineComposer 生成分歧日志（含文案 + 路径 + 影响摘要）
 * 3. 转换为 DivergenceArchiveEntity 批量写入
 * 4. 更新原事件状态为 ARCHIVED
 * 5. 返回归档统计（含"无重大替代"记录数）
 *
 * @param databaseManager 三库管理入口
 * @param butterflyRepository T20 蝴蝶事件仓库（复用查询能力）
 * @param textGenerator 分歧文案生成器
 * @param composer 时间线组合器
 */
class DivergenceArchiveService(
    private val databaseManager: DatabaseManager,
    private val butterflyRepository: ButterflyRepository,
    private val textGenerator: DivergenceTextGenerator = DivergenceTextGenerator(),
    private val composer: DivergenceTimelineComposer = DivergenceTimelineComposer(textGenerator)
) {

    private val logger = Logger.getLogger("DivergenceArchiveService")

    /**
     * 归档当前赛季的分歧日志（由 T19 SeasonArchiver 在赛季归档时调用）。
     *
     * @param ctx 推进上下文（isSeasonEnd=true 时调用）
     * @return 归档统计
     */
    suspend fun archiveSeason(ctx: AdvanceContext): DivergenceArchiveSummary =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val saveDb = databaseManager.getSaveDatabaseOrNull()
                ?: return@withContext DivergenceArchiveSummary(
                    seasonId = ctx.currentSeasonId,
                    totalEvents = 0,
                    archivedEvents = 0,
                    noReplacementCount = 0,
                    withReplacementCount = 0,
                    archivedAt = ctx.currentDate.toString()
                )

            try {
                // 1. 加载当前存档全部蝴蝶事件视图项
                val viewItems = butterflyRepository.getEventViewItems(ctx.saveUuid)
                val totalEvents = viewItems.size

                if (totalEvents == 0) {
                    Log.i(TAG, "赛季 ${ctx.currentSeasonId} 无蝴蝶事件，跳过分歧归档")
                    return@withContext DivergenceArchiveSummary(
                        seasonId = ctx.currentSeasonId,
                        totalEvents = 0,
                        archivedEvents = 0,
                        noReplacementCount = 0,
                        withReplacementCount = 0,
                        archivedAt = ctx.currentDate.toString()
                    )
                }

                // 2. 通过 composer 组合为分歧日志（生成文案 + 路径 + 影响摘要）
                val timelineItems = composer.compose(viewItems)

                // 3. 转换为归档实体并批量写入
                val archiveEntities = timelineItems.map { item ->
                    val log = item.log
                    DivergenceArchiveEntity(
                        archiveId = UUID.randomUUID().toString(),
                        saveId = ctx.saveUuid,
                        seasonId = ctx.currentSeasonId,
                        eventId = log.eventId,
                        triggerDate = log.triggerDate.toString(),
                        category = log.category.code,
                        triggerType = log.triggerType.code,
                        importance = log.importance,
                        originalPath = log.originalPath,
                        currentPath = log.currentPath,
                        impactSummary = log.impactSummary,
                        divergenceText = log.divergenceText,
                        hasMajorReplacement = if (log.hasMajorReplacement) 1 else 0,
                        archivedAt = ctx.currentDate.toString()
                    )
                }

                val archiveDao = saveDb.divergenceArchiveDao()
                archiveDao.insertAll(archiveEntities)

                // 4. 更新原蝴蝶事件状态为 ARCHIVED
                for (item in timelineItems) {
                    butterflyRepository.updateEventStatus(
                        item.log.eventId,
                        ButterflyEventStatus.ARCHIVED.code
                    )
                }

                // 5. 统计"无重大替代"记录
                val noReplacementCount = archiveEntities.count { it.hasMajorReplacement == 0 }
                val withReplacementCount = archiveEntities.count { it.hasMajorReplacement == 1 }

                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "赛季 ${ctx.currentSeasonId} 分歧归档完成：" +
                    "归档 ${archiveEntities.size} / $totalEvents 事件 / " +
                    "无重大替代 $noReplacementCount 条 / " +
                    "有重大替代 $withReplacementCount 条 / " +
                    "耗时 ${duration}ms")

                DivergenceArchiveSummary(
                    seasonId = ctx.currentSeasonId,
                    totalEvents = totalEvents,
                    archivedEvents = archiveEntities.size,
                    noReplacementCount = noReplacementCount,
                    withReplacementCount = withReplacementCount,
                    archivedAt = ctx.currentDate.toString()
                )
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "分歧归档失败：${e.message}", e)
                Log.e(TAG, "分歧归档失败：${e.message}", e)
                DivergenceArchiveSummary(
                    seasonId = ctx.currentSeasonId,
                    totalEvents = 0,
                    archivedEvents = 0,
                    noReplacementCount = 0,
                    withReplacementCount = 0,
                    archivedAt = ctx.currentDate.toString()
                )
            }
        }

    /**
     * 查询当前存档的"历史分歧未产生重大替代"记录（任务 T21.5）。
     *
     * 用于玩家查看哪些蝴蝶事件没有产生连锁反应。
     *
     * @param saveUuid 存档 UUID
     * @return 无重大替代记录列表
     */
    suspend fun getNoReplacementRecords(saveUuid: String): List<NoReplacementRecord> =
        withContext(Dispatchers.IO) {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
            val archiveDao = saveDb.divergenceArchiveDao()
            val noReplacementEntities = archiveDao.getByReplacement(saveUuid, 0)

            noReplacementEntities.map { entity ->
                NoReplacementRecord(
                    eventId = entity.eventId,
                    triggerDate = runCatching { LocalDate.parse(entity.triggerDate) }
                        .getOrDefault(LocalDate.now()),
                    summary = entity.divergenceText,
                    reason = entity.impactSummary,
                    checkedNodeCount = 0 // V1 简化：不存储节点数，从影响摘要推导
                )
            }
        }

    /**
     * 查询当前存档全部归档记录（任务 T21.4：归档查询）。
     *
     * @param saveUuid 存档 UUID
     * @return 归档实体列表（按触发日期倒序）
     */
    suspend fun getArchivedDivergences(saveUuid: String): List<DivergenceArchiveEntity> =
        withContext(Dispatchers.IO) {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
            saveDb.divergenceArchiveDao().getAll(saveUuid)
        }

    /**
     * 按赛季查询归档记录。
     *
     * @param saveUuid 存档 UUID
     * @param seasonId 赛季 ID
     * @return 该赛季的归档实体列表
     */
    suspend fun getArchivedDivergencesBySeason(
        saveUuid: String,
        seasonId: Int
    ): List<DivergenceArchiveEntity> = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        saveDb.divergenceArchiveDao().getBySeason(saveUuid, seasonId)
    }

    /**
     * 获取已归档的赛季列表（用于时间线筛选）。
     *
     * @param saveUuid 存档 UUID
     * @return 已归档赛季 ID 列表（倒序）
     */
    suspend fun getArchivedSeasons(saveUuid: String): List<Int> =
        withContext(Dispatchers.IO) {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
            saveDb.divergenceArchiveDao().getArchivedSeasons(saveUuid)
        }

    companion object {
        private const val TAG = "DivergenceArchiveSvc"

        /**
         * 创建 [DivergenceArchiveService] 工厂方法。
         *
         * @param databaseManager 三库管理入口
         * @param butterflyRepository T20 蝴蝶事件仓库
         * @return 完整装配的 DivergenceArchiveService 实例
         */
        fun create(
            databaseManager: DatabaseManager,
            butterflyRepository: ButterflyRepository
        ): DivergenceArchiveService {
            val textGenerator = DivergenceTextGenerator()
            val composer = DivergenceTimelineComposer(textGenerator)
            return DivergenceArchiveService(
                databaseManager = databaseManager,
                butterflyRepository = butterflyRepository,
                textGenerator = textGenerator,
                composer = composer
            )
        }
    }
}
