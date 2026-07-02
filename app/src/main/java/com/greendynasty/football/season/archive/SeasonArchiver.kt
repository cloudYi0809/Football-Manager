package com.greendynasty.football.season.archive

import android.util.Log
import androidx.room.withTransaction
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SeasonArchiveEntity
import com.greendynasty.football.season.cleanup.DataCleaner
import com.greendynasty.football.season.compress.EventCompressor
import com.greendynasty.football.season.config.ArchiveConfig
import com.greendynasty.football.season.ranking.RankingFreezer
import com.greendynasty.football.season.stats.StatsAggregator
import com.greendynasty.football.season.summary.SeasonSummaryGenerator
import com.greendynasty.football.season.vacuum.VacuumExecutor
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.stub.SeasonArchiverStub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * T19 赛季归档器（V0.2 §七 入口）
 *
 * 赛季结束时由 T07 [com.greendynasty.football.simulation.season.SeasonTaskScheduler] 调用，
 * 执行完整 6 步归档流水线 + 球员属性回写：
 *
 * 1. [SeasonSummaryGenerator]   生成赛季摘要（积分榜/射手榜/转会/财政/奖项/升降级）
 * 2. [EventCompressor]          压缩比赛事件（仅保留比分+进球+红黄牌+Top N 评分）
 * 3. [StatsAggregator]          聚合球员赛季统计
 * 4. [RankingFreezer]           冻结榜单到 cache.db ranking_cache
 * 5. [DataCleaner]              清理 6 类过期数据
 * 6. [VacuumExecutor]           VACUUM 压缩回收空间
 * + [PlayerAttributeWriter]     球员属性回写 history.db
 *
 * 事务策略：步骤 1-5 在 save.db 事务内执行，VACUUM 在事务外执行（SQLite 限制）。
 * 属性回写独立于 save.db 事务（写 history.db）。
 *
 * 继承 [SeasonArchiverStub] 以便由 [SeasonTaskScheduler] 注入替换。
 *
 * @param databaseManager 三库管理入口
 * @param config 归档配置
 * @param summaryGenerator 赛季摘要生成器
 * @param eventCompressor 事件压缩器
 * @param statsAggregator 统计聚合器
 * @param rankingFreezer 榜单冻结器
 * @param dataCleaner 数据清理器
 * @param vacuumExecutor VACUUM 执行器
 * @param playerAttributeWriter 球员属性回写器
 */
class SeasonArchiver(
    private val databaseManager: DatabaseManager,
    private val config: ArchiveConfig,
    private val summaryGenerator: SeasonSummaryGenerator,
    private val eventCompressor: EventCompressor,
    private val statsAggregator: StatsAggregator,
    private val rankingFreezer: RankingFreezer,
    private val dataCleaner: DataCleaner,
    private val vacuumExecutor: VacuumExecutor,
    private val playerAttributeWriter: PlayerAttributeWriter
) : SeasonArchiverStub() {

    /**
     * 归档赛季数据（由 SeasonTaskScheduler 调用）。
     *
     * 内部调用 [archiveSeasonWithResult] 并丢弃结果，兼容 stub 接口。
     */
    override suspend fun archiveSeason(ctx: AdvanceContext) {
        archiveSeasonWithResult(ctx)
    }

    /**
     * 归档赛季数据并返回完整结果。
     *
     * @param ctx 推进上下文（isSeasonEnd=true 时调用）
     * @return 归档结果（含摘要/压缩数/清理数/体积指标）
     */
    suspend fun archiveSeasonWithResult(ctx: AdvanceContext): ArchiveResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val sizeBefore = getDbSize()

            val saveDb = databaseManager.getSaveDatabaseOrNull()
                ?: return@withContext ArchiveResult(
                    seasonId = ctx.currentSeasonId,
                    archiveId = 0,
                    summary = com.greendynasty.football.season.summary.SeasonSummary(
                        seasonId = ctx.currentSeasonId,
                        seasonLabel = "赛季${ctx.currentSeasonId}",
                        leagueStandings = emptyList(),
                        topScorers = emptyList(),
                        topAssists = emptyList(),
                        transfers = com.greendynasty.football.season.summary.TransferSummary(0, 0L, 0, emptyList()),
                        managerClubFinancial = com.greendynasty.football.season.summary.ClubFinancialSummary(
                            ctx.managerClubId, "俱乐部${ctx.managerClubId}", 0, 0, 0, 0, 0
                        ),
                        awards = emptyList(),
                        promotions = emptyList(),
                        relegations = emptyList()
                    ),
                    compressedMatches = 0,
                    cleanedRecords = 0,
                    attributesWritten = 0,
                    sizeBeforeMb = sizeBefore,
                    sizeAfterMb = sizeBefore,
                    durationMs = System.currentTimeMillis() - startTime
                )

            // 1. 生成赛季摘要（事务外，可能涉及 history.db 只读查询）
            val summary = summaryGenerator.generate(ctx)
            val summaryJson = summaryGenerator.serialize(summary)

            // 2-5. 在 save.db 事务内执行压缩/聚合/冻结/清理
            var compressedMatches = 0
            var cleanedRecords = 0

            saveDb.withTransaction {
                // 2. 压缩比赛事件
                compressedMatches = eventCompressor.compress(ctx)

                // 3. 聚合统计
                statsAggregator.aggregate(ctx)

                // 4. 冻结榜单（写 cache.db，独立于 save.db 事务）
                rankingFreezer.freeze(ctx, summary)

                // 5. 清理过期数据
                cleanedRecords = dataCleaner.clean(ctx)
            }

            // + 球员属性回写 history.db（独立事务，解除只读）
            val attributesWritten = playerAttributeWriter.writeAttributes(ctx)

            // 6. VACUUM 压缩（必须在事务外执行）
            if (config.vacuum.enabled && config.vacuum.runAfterArchive) {
                vacuumExecutor.vacuum()
            }

            // 7. 保存归档记录到 season_archive 表
            val archiveId = saveDb.seasonArchiveDao().insert(
                SeasonArchiveEntity(
                    saveId = ctx.saveUuid,
                    seasonId = ctx.currentSeasonId,
                    archiveType = "full",
                    summaryJson = summaryJson,
                    createdAt = ctx.currentDate.toString()
                )
            ).toInt()

            val duration = System.currentTimeMillis() - startTime
            val sizeAfter = getDbSize()

            Log.i(TAG, "赛季 ${ctx.currentSeasonId} 归档完成：" +
                "压缩 $compressedMatches 场 / 清理 $cleanedRecords 条 / " +
                "回写 $attributesWritten 条属性 / " +
                "${sizeBefore}MB → ${sizeAfter}MB / 耗时 ${duration}ms")

            ArchiveResult(
                seasonId = ctx.currentSeasonId,
                archiveId = archiveId,
                summary = summary,
                compressedMatches = compressedMatches,
                cleanedRecords = cleanedRecords,
                attributesWritten = attributesWritten,
                sizeBeforeMb = sizeBefore,
                sizeAfterMb = sizeAfter,
                durationMs = duration
            )
        }

    /**
     * V0.2 §七 体积监控
     *
     * 每次归档后检查 save.db 体积，判断是否接近或超过 80MB 红线。
     *
     * @param ctx 推进上下文
     * @return 体积监控报告
     */
    suspend fun checkSize(ctx: AdvanceContext): SizeReport = withContext(Dispatchers.IO) {
        val currentSize = getDbSize()
        val saveDb = databaseManager.getSaveDatabaseOrNull()
        val seasonsArchived = saveDb?.seasonArchiveDao()?.count(ctx.saveUuid) ?: 0

        val maxSize = config.size.maxSaveSizeMb
        val warningThreshold = maxSize * config.size.warningThresholdRatio

        SizeReport(
            currentSizeMb = currentSize,
            seasonsArchived = seasonsArchived,
            maxSizeMb = maxSize,
            isApproachingLimit = currentSize > warningThreshold,
            isOverLimit = currentSize > maxSize
        )
    }

    /**
     * 获取当前 save.db 文件体积（MB）。
     */
    private fun getDbSize(): Double {
        return try {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return 0.0
            val dbPath = saveDb.openHelper.writableDatabase.path ?: return 0.0
            val dbFile = File(dbPath)
            dbFile.length() / (1024.0 * 1024.0)
        } catch (e: Exception) {
            Log.w(TAG, "获取数据库体积失败：${e.message}")
            0.0
        }
    }

    companion object {
        private const val TAG = "SeasonArchiver"

        /**
         * 创建 [SeasonArchiver] 工厂方法。
         *
         * 使用默认配置创建全部子组件并注入。
         *
         * @param databaseManager 三库管理入口
         * @param config 归档配置（默认 [ArchiveConfig.DEFAULT]）
         * @return 完整装配的 SeasonArchiver 实例
         */
        fun create(
            databaseManager: DatabaseManager,
            config: ArchiveConfig = ArchiveConfig.DEFAULT
        ): SeasonArchiver {
            return SeasonArchiver(
                databaseManager = databaseManager,
                config = config,
                summaryGenerator = SeasonSummaryGenerator(databaseManager),
                eventCompressor = EventCompressor(databaseManager, config),
                statsAggregator = StatsAggregator(databaseManager),
                rankingFreezer = RankingFreezer(databaseManager),
                dataCleaner = DataCleaner(databaseManager, config),
                vacuumExecutor = VacuumExecutor(databaseManager),
                playerAttributeWriter = PlayerAttributeWriter(databaseManager)
            )
        }
    }
}
