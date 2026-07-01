package com.greendynasty.football.data.save.management.integrity

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.management.backup.SaveBackupManager
import com.greendynasty.football.data.save.management.config.SaveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 存档修复服务（T03）
 *
 * V0.2 §九 三级修复策略：
 * - [RepairLevel.LEVEL_1_LIGHT]：回退到最近 checkpoint
 * - [RepairLevel.LEVEL_2_MEDIUM]：回退 + 重建 cache.db
 * - [RepairLevel.LEVEL_3_HEAVY]：重建 save.db（从 checkpoint + history.db 重新初始化；
 *   当前实现为回退到最早可用 checkpoint/备份并重建缓存）
 *
 * 修复流程统一为：关闭当前存档连接 → 安全复制（临时文件 + 重命名）覆盖 save.db → 重新打开存档。
 *
 * @param databaseManager 三库管理入口
 * @param backupManager 备份管理器（checkpoint 不可用时回退到备份）
 * @param config 存档配置
 */
class SaveRepairService(
    private val databaseManager: DatabaseManager,
    private val backupManager: SaveBackupManager,
    private val config: SaveConfig = SaveConfig()
) {

    /**
     * 执行修复。
     *
     * @param saveId 存档 ID
     * @param level 修复级别
     * @return 修复结果
     */
    suspend fun repair(saveId: String, level: RepairLevel): RepairResult =
        withContext(Dispatchers.IO) {
            when (level) {
                RepairLevel.LEVEL_1_LIGHT -> repairLight(saveId)
                RepairLevel.LEVEL_2_MEDIUM -> repairMedium(saveId)
                RepairLevel.LEVEL_3_HEAVY -> repairHeavy(saveId)
            }
        }

    /** 一级修复：回退到最近 checkpoint */
    private suspend fun repairLight(saveId: String): RepairResult {
        val restored = restoreFromLatestCheckpoint(saveId)
        return if (restored) {
            RepairResult.Success("已回退到最近 checkpoint")
        } else {
            // checkpoint 不可用时尝试备份
            val backupRestored = restoreFromLatestBackup(saveId)
            if (backupRestored) {
                RepairResult.PartialRepair("无可用 checkpoint，已从最近备份恢复")
            } else {
                RepairResult.Failed("无可用 checkpoint 或备份")
            }
        }
    }

    /** 二级修复：回退 + 重建 cache */
    private suspend fun repairMedium(saveId: String): RepairResult {
        val lightResult = repairLight(saveId)
        return try {
            databaseManager.rebuildCache()
            lightResult
        } catch (e: Exception) {
            RepairResult.PartialRepair("${lightResult.message}；cache 重建失败：${e.message}")
        }
    }

    /** 三级修复：重建 save.db（从 checkpoint + history.db 重新初始化） */
    private suspend fun repairHeavy(saveId: String): RepairResult {
        // 1. 优先尝试从最早的 checkpoint 完整重建
        val rebuilt = rebuildFromEarliestCheckpoint(saveId)
        if (rebuilt) {
            return try {
                databaseManager.rebuildCache()
                RepairResult.Success("已从 checkpoint 重建 save.db 并重建缓存")
            } catch (e: Exception) {
                RepairResult.PartialRepair("已从 checkpoint 重建 save.db，但 cache 重建失败：${e.message}")
            }
        }
        // 2. checkpoint 不可用时回退到最近备份
        val backupRestored = restoreFromLatestBackup(saveId)
        return if (backupRestored) {
            try {
                databaseManager.rebuildCache()
                RepairResult.PartialRepair("无可用 checkpoint，已从备份重建并重建缓存")
            } catch (e: Exception) {
                RepairResult.PartialRepair("已从备份重建 save.db，但 cache 重建失败：${e.message}")
            }
        } else {
            RepairResult.Failed("无可用 checkpoint 或备份，无法重建 save.db")
        }
    }

    /**
     * 从最近 checkpoint 恢复 save.db。
     * 流程：关闭当前连接 → 复制 checkpoint 文件 → 重新打开。
     */
    private suspend fun restoreFromLatestCheckpoint(saveId: String): Boolean {
        return try {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return false
            val checkpoint = saveDb.checkpointDao().getLatest(saveId) ?: return false
            val checkpointFile = File(checkpoint.filePath)
            if (!checkpointFile.exists()) return false
            copyFileToSave(saveId, checkpointFile)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从最早 checkpoint 重建 save.db（三级修复：取最早可用的完整快照）。
     */
    private suspend fun rebuildFromEarliestCheckpoint(saveId: String): Boolean {
        return try {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return false
            // 按日期升序取最早可用 checkpoint（最完整的初始快照）
            val checkpoints = saveDb.checkpointDao().getAll(saveId)
                .sortedBy { it.checkpointDate }
            if (checkpoints.isEmpty()) return false
            val earliest = checkpoints.first()
            val checkpointFile = File(earliest.filePath)
            if (!checkpointFile.exists()) return false
            copyFileToSave(saveId, checkpointFile)
        } catch (e: Exception) {
            false
        }
    }

    /** 从最近备份恢复（恢复前关闭存档连接） */
    private suspend fun restoreFromLatestBackup(saveId: String): Boolean {
        val backups = backupManager.listBackups(saveId)
        if (backups.isEmpty()) return false
        val latest = backups.first()
        databaseManager.closeSave()
        return backupManager.restoreFromBackup(saveId, latest.backupId)
    }

    /**
     * 安全复制文件到 save.db 路径：
     * 关闭连接 → 写临时文件 → 重命名 → 清理 WAL/SHM → 重新打开存档。
     */
    private fun copyFileToSave(saveId: String, source: File): Boolean {
        return try {
            databaseManager.closeSave()
            val appContext = databaseManager.getAppContext()
            val targetFile = File(SaveDatabase.getSaveDir(appContext), "save_$saveId.db")
            val tempFile = File(SaveDatabase.getSaveDir(appContext), "save_$saveId.db.tmp")

            source.inputStream().use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            // 清理 WAL/SHM（重新打开后由 Room 重新生成）
            File(targetFile.absolutePath + "-wal").delete()
            File(targetFile.absolutePath + "-shm").delete()
            // 重新打开存档
            databaseManager.openSave(saveId)
            true
        } catch (e: Exception) {
            // 尝试重新打开存档，避免连接处于关闭状态
            try { databaseManager.openSave(saveId) } catch (_: Exception) {}
            false
        }
    }
}

/**
 * 修复级别（V0.2 §九 三级修复）
 */
enum class RepairLevel {
    /** 一级：回退到最近 checkpoint */
    LEVEL_1_LIGHT,

    /** 二级：回退 + 重建 cache */
    LEVEL_2_MEDIUM,

    /** 三级：重建 save.db */
    LEVEL_3_HEAVY
}

/**
 * 修复结果
 */
sealed class RepairResult {
    /** 结果说明 */
    abstract val message: String

    /** 修复成功 */
    data class Success(override val message: String) : RepairResult()

    /** 部分修复（如 checkpoint 不可用改用备份，或 cache 重建失败） */
    data class PartialRepair(override val message: String) : RepairResult()

    /** 修复失败 */
    data class Failed(override val message: String) : RepairResult()
}
