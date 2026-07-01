package com.greendynasty.football.simulation.perf

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.PerfLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 性能日志记录器（V0.2 §长程性能 + T07 方案 §十二）
 *
 * 记录关键操作（每日推进、比赛模拟、转会处理）的耗时，
 * 写入 save.db 的 perf_log 表，用于长程性能监控和 P95 统计。
 *
 * @param databaseManager 三库管理入口
 */
class PerfLogger(
    private val databaseManager: DatabaseManager
) {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * 记录一条性能日志
     *
     * @param actionType 操作类型（daily_advance / match_sim / transfer / save_load）
     * @param durationMs 耗时（毫秒）
     * @param saveId 数据表存档 ID（仅用于日志展示）
     */
    suspend fun log(actionType: String, durationMs: Long, saveId: Int) = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext
        try {
            val manifest = saveDb.saveManifestDao().get()
            val now = dateTimeFormatter.format(LocalDateTime.now())
            saveDb.perfLogDao().insert(
                PerfLogEntity(
                    saveId = manifest?.saveId,
                    logDate = now,
                    actionType = actionType,
                    durationMs = durationMs.toInt(),
                    memoryMb = null,
                    dbSizeMb = null,
                    extraJson = "saveId=$saveId"
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "性能日志写入失败: action=$actionType, ${e.message}")
        }
    }

    /**
     * 记录性能告警（不写库，仅 Log.warn）
     */
    fun warn(message: String) {
        Log.w(TAG, "[PERF WARN] $message")
    }

    companion object {
        private const val TAG = "PerfLogger"
    }
}
