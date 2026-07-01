package com.greendynasty.football.data.save.management.backup

import android.content.Context
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.management.config.SaveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 存档自动备份管理器（T03）
 *
 * 通过文件复制将 save_<saveId>.db 备份到 filesDir/saves/backups/{saveId}/ 目录。
 *
 * 备份文件命名：
 * - 数据库文件：backup_{timestamp}.db
 * - 元信息文件：backup_{timestamp}.meta.json（序列化 [BackupInfo]）
 *
 * 触发场景（V0.2 §九）：
 * - 每日推进后自动备份（[PeriodicBackupScheduler] 判断触发时机）
 * - 手动备份
 * - 迁移前备份（[SaveMigrator] 调用）
 *
 * 安全策略：先写入临时文件（.tmp）再重命名为目标文件，避免写一半导致备份/存档损坏。
 *
 * @param context 上下文
 * @param config 存档配置
 */
class SaveBackupManager(
    private val context: Context,
    private val config: SaveConfig = SaveConfig()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * 创建备份。
     *
     * @param saveId 存档 ID
     * @param reason 备份原因，默认 [REASON_MANUAL]（可传 [REASON_PERIODIC] / [REASON_PRE_MIGRATION] 等）
     * @return 备份结果
     */
    suspend fun createBackup(saveId: String, reason: String = REASON_MANUAL): BackupResult =
        withContext(Dispatchers.IO) {
            val sourceFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")
            if (!sourceFile.exists()) {
                return@withContext BackupResult.Failure("存档文件不存在：${sourceFile.absolutePath}")
            }

            val timestamp = System.currentTimeMillis()
            val backupId = "backup_$timestamp"
            val saveBackupDir = getBackupDir(context, saveId)
            saveBackupDir.mkdirs()

            val backupDbFile = File(saveBackupDir, "$backupId.db")
            val tempDbFile = File(saveBackupDir, "$backupId.db.tmp")

            try {
                // 1. 先写入临时数据库文件，再重命名
                sourceFile.inputStream().use { input ->
                    tempDbFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tempDbFile.renameTo(backupDbFile)) {
                    // 部分文件系统 rename 失败时回退到复制后删除临时文件
                    tempDbFile.copyTo(backupDbFile, overwrite = true)
                    tempDbFile.delete()
                }

                // 2. 写入元信息（同样先写临时文件再重命名）
                val info = BackupInfo(
                    backupId = backupId,
                    saveId = saveId,
                    createdAt = timestamp,
                    fileSizeBytes = backupDbFile.length(),
                    reason = reason
                )
                val metaFile = File(saveBackupDir, "$backupId.meta.json")
                val metaTemp = File(saveBackupDir, "$backupId.meta.json.tmp")
                metaTemp.writeText(json.encodeToString(BackupInfo.serializer(), info))
                if (!metaTemp.renameTo(metaFile)) {
                    metaTemp.copyTo(metaFile, overwrite = true)
                    metaTemp.delete()
                }

                // 3. 清理旧备份（保留最近 N 个 + 清理超期备份）
                cleanOldBackups(saveId)

                BackupResult.Success(backupId)
            } catch (e: Exception) {
                tempDbFile.delete()
                File(saveBackupDir, "$backupId.db.tmp").delete()
                File(saveBackupDir, "$backupId.meta.json.tmp").delete()
                BackupResult.Failure("备份失败：${e.message}")
            }
        }

    /**
     * 列出存档的所有备份（按创建时间倒序）。
     *
     * @param saveId 存档 ID
     * @return 备份信息列表
     */
    suspend fun listBackups(saveId: String): List<BackupInfo> = withContext(Dispatchers.IO) {
        val saveBackupDir = getBackupDir(context, saveId)
        if (!saveBackupDir.exists()) return@withContext emptyList()
        listBackupsInternal(saveBackupDir)
    }

    /**
     * 从备份恢复存档。
     *
     * 注意：恢复前必须先关闭存档数据库连接（由调用方负责，或通过 [com.greendynasty.football.data.api.DatabaseManager.closeSave]）。
     *
     * @param saveId 存档 ID
     * @param backupId 备份 ID
     * @return true 表示恢复成功
     */
    suspend fun restoreFromBackup(saveId: String, backupId: String): Boolean =
        withContext(Dispatchers.IO) {
            val saveBackupDir = getBackupDir(context, saveId)
            val backupFile = File(saveBackupDir, "$backupId.db")
            if (!backupFile.exists()) return@withContext false

            val targetFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")
            val tempFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db.tmp")

            try {
                // 先写临时文件再重命名，避免覆盖失败导致存档损坏
                backupFile.inputStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                // 同步清理 WAL/SHM 临时文件（恢复后会由 Room 重新生成）
                File(targetFile.absolutePath + "-wal").delete()
                File(targetFile.absolutePath + "-shm").delete()
                true
            } catch (e: Exception) {
                tempFile.delete()
                false
            }
        }

    /**
     * 清理旧备份。
     *
     * 规则（V0.2 §九）：
     * - 保留最近 [keepCount] 个备份（默认 [SaveConfig.backupKeepCount] = 5）
     * - 删除超过 [SaveConfig.backupIntervalDays] 天的备份（默认 7 天）
     *
     * @param saveId 存档 ID
     * @param keepCount 保留份数
     */
    suspend fun cleanOldBackups(saveId: String, keepCount: Int = config.backupKeepCount) =
        withContext(Dispatchers.IO) {
            val saveBackupDir = getBackupDir(context, saveId)
            if (!saveBackupDir.exists()) return@withContext

            val backups = listBackupsInternal(saveBackupDir)
            if (backups.isEmpty()) return@withContext

            // 1. 保留最近 keepCount 个，删除多余的
            if (backups.size > keepCount) {
                backups.drop(keepCount).forEach { info ->
                    removeBackup(saveBackupDir, info.backupId)
                }
            }

            // 2. 删除超过 backupIntervalDays 天的备份
            val maxAgeMs = config.backupIntervalDays * 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            // 重新读取（上一步可能已删除部分）
            listBackupsInternal(saveBackupDir).forEach { info ->
                if (now - info.createdAt > maxAgeMs) {
                    removeBackup(saveBackupDir, info.backupId)
                }
            }
        }

    /** 读取指定备份目录下的全部备份元信息（按创建时间倒序） */
    private fun listBackupsInternal(saveBackupDir: File): List<BackupInfo> {
        return saveBackupDir.listFiles { f -> f.name.endsWith(".meta.json") }
            ?.mapNotNull { metaFile ->
                try {
                    json.decodeFromString(BackupInfo.serializer(), metaFile.readText())
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /** 删除一个备份及其元信息与临时文件 */
    private fun removeBackup(saveBackupDir: File, backupId: String) {
        File(saveBackupDir, "$backupId.db").delete()
        File(saveBackupDir, "$backupId.meta.json").delete()
        File(saveBackupDir, "$backupId.db.tmp").delete()
    }

    companion object {
        /** 定期备份（每日推进后） */
        const val REASON_PERIODIC = "PERIODIC"

        /** 手动备份 */
        const val REASON_MANUAL = "MANUAL"

        /** 迁移前备份 */
        const val REASON_PRE_MIGRATION = "PRE_MIGRATION"

        /** 赛季结束备份 */
        const val REASON_SEASON_END = "SEASON_END"

        /**
         * 备份根目录：filesDir/saves/backups/
         */
        fun getBackupRootDir(context: Context): File {
            val dir = File(context.filesDir, "saves/backups")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        /**
         * 指定存档的备份目录：filesDir/saves/backups/{saveId}/
         */
        fun getBackupDir(context: Context, saveId: String): File {
            val dir = File(getBackupRootDir(context), saveId)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }
}
