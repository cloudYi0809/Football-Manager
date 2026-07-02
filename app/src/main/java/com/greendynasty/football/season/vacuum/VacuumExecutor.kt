package com.greendynasty.football.season.vacuum

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.season.archive.VacuumResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * T19 VACUUM 执行器（V0.2 §七.6）
 *
 * SQLite VACUUM 重建数据库文件，回收已删除数据留下的碎片空间。
 * 归档清理后执行 VACUUM 可显著减少 save.db 体积（预期回收 20-40%）。
 *
 * 注意：
 * - VACUUM 必须在事务外执行（SQLite 限制）
 * - VACUUM 期间数据库锁定，可能耗时数百毫秒至数秒
 * - 仅在 [VacuumConfig.enabled] = true 时执行
 *
 * @param databaseManager 三库管理入口
 */
class VacuumExecutor(
    private val databaseManager: DatabaseManager
) {

    /**
     * 执行 VACUUM 压缩 save.db。
     *
     * @return VACUUM 结果（含前后体积与耗时）
     */
    suspend fun vacuum(): VacuumResult = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: return@withContext VacuumResult(0.0, 0.0, 0.0, 0L)

        val sizeBefore = getDbSize()

        val startTime = System.currentTimeMillis()

        try {
            // VACUUM 必须在事务外执行
            val db = saveDb.openHelper.writableDatabase
            db.execSQL("VACUUM")
        } catch (e: Exception) {
            Log.e(TAG, "VACUUM 执行失败：${e.message}", e)
        }

        val duration = System.currentTimeMillis() - startTime
        val sizeAfter = getDbSize()

        Log.i(TAG, "VACUUM 完成：${sizeBefore}MB → ${sizeAfter}MB（回收 ${sizeBefore - sizeAfter}MB，耗时 ${duration}ms）")

        VacuumResult(
            sizeBeforeMb = sizeBefore,
            sizeAfterMb = sizeAfter,
            savedMb = sizeBefore - sizeAfter,
            durationMs = duration
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
        private const val TAG = "VacuumExecutor"
    }
}
