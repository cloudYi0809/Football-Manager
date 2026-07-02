package com.greendynasty.football.season.cleanup

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.season.config.ArchiveConfig
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T19 数据清理器（V0.2 §七.5）
 *
 * 赛季归档时清理 6 类过期数据，控制 save.db 体积增长：
 * 1. 过期已读新闻（保留最近 N 赛季）
 * 2. 过期待办（V1 暂无 todo 表，跳过）
 * 3. 过期球探报告（保留最近 N 月）
 * 4. 过期转会报价（已完成/已拒绝 + 超出保留期）
 * 5. 过期 AI 决策日志（保留最近 N 赛季，通过 SupportSQLiteDatabase 直接删除）
 * 6. 过期性能日志（保留最近 N 赛季）
 *
 * @param databaseManager 三库管理入口
 * @param config 归档配置（清理保留期参数）
 */
class DataCleaner(
    private val databaseManager: DatabaseManager,
    private val config: ArchiveConfig
) {

    /**
     * 清理过期数据。
     *
     * @param ctx 推进上下文
     * @return 清理的记录总数
     */
    suspend fun clean(ctx: AdvanceContext): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0
        var cleanedCount = 0
        val cleanup = config.cleanup

        try {
            val currentDate = ctx.currentDate

            // 1. 删除过期已读新闻（保留最近 N 赛季 ≈ N 年）
            val newsCutoff = currentDate.minusYears(cleanup.newsKeepSeasons.toLong())
            runCatching {
                cleanedCount += saveDb.saveNewsDao()
                    .deleteOldRead(ctx.saveId, newsCutoff.toString())
            }.onFailure {
                Log.w(TAG, "清理过期新闻失败：${it.message}")
            }

            // 2. 过期待办（V1 暂无 todo 表，跳过）
            // TODO: T22 待办系统接入后实现

            // 3. 删除过期球探报告（保留最近 N 月）
            val scoutCutoff = currentDate.minusMonths(cleanup.scoutReportKeepMonths.toLong())
            runCatching {
                cleanedCount += saveDb.scoutReportDao()
                    .deleteBefore(ctx.saveId, scoutCutoff.toString())
            }.onFailure {
                Log.w(TAG, "清理球探报告失败：${it.message}")
            }

            // 4. 删除过期转会报价（已完成/已拒绝 + 超出保留期）
            val transferCutoff = currentDate.minusMonths(cleanup.transferOfferKeepMonths.toLong())
            runCatching {
                cleanedCount += saveDb.saveTransferOfferDao()
                    .deleteCompletedAndExpired(ctx.saveId, transferCutoff.toString())
            }.onFailure {
                Log.w(TAG, "清理转会报价失败：${it.message}")
            }

            // 5. 删除过期 AI 决策日志（保留最近 N 赛季 ≈ N 年）
            // ai_decision_log 表无独立 DAO，通过 SupportSQLiteDatabase 直接删除
            // 注意：ai_decision_log.save_id 为 String 类型（UUID），使用 ctx.saveUuid
            val aiLogCutoff = currentDate.minusYears(cleanup.aiLogKeepSeasons.toLong())
            runCatching {
                val db = saveDb.openHelper.writableDatabase
                val cursor = db.delete(
                    "ai_decision_log",
                    "save_id = ? AND decision_date < ?",
                    arrayOf(ctx.saveUuid, aiLogCutoff.toString())
                )
                cleanedCount += cursor
            }.onFailure {
                Log.w(TAG, "清理 AI 决策日志失败：${it.message}")
            }

            // 6. 删除过期性能日志（保留最近 N 赛季 ≈ N 年）
            val perfLogCutoff = currentDate.minusYears(cleanup.perfLogKeepSeasons.toLong())
            runCatching {
                cleanedCount += saveDb.perfLogDao()
                    .deleteOldLogs(ctx.saveUuid, perfLogCutoff.toString())
            }.onFailure {
                Log.w(TAG, "清理性能日志失败：${it.message}")
            }

            Log.i(TAG, "赛季 ${ctx.currentSeasonId} 数据清理完成：$cleanedCount 条记录")
        } catch (e: Exception) {
            Log.e(TAG, "数据清理失败：${e.message}", e)
        }

        cleanedCount
    }

    companion object {
        private const val TAG = "DataCleaner"
    }
}
