package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.entity.CheckpointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Checkpoint 类型
 *
 * 参考 V0.2 §六 存档 checkpoint 策略：
 * - LIGHT：每 30 天轻量检查点（保存前快照，用于回滚）
 * - SEASON：每赛季结束检查点（完整赛季结束状态）
 * - MIGRATION：迁移前备份检查点（完整备份，迁移失败时回滚）
 * - USER：用户手动备份检查点（完整备份）
 *
 * @param code 存储编码（写入 checkpoint.checkpoint_type 字段）
 * @param maxRetained 该类型最大保留数量（超过则清理旧的）
 */
enum class CheckpointType(val code: String, val maxRetained: Int) {

    /** 轻量检查点：保存前快照，用于回滚 */
    LIGHT("light", 4),

    /** 赛季检查点：每赛季结束完整状态 */
    SEASON("season", 5),

    /** 迁移检查点：迁移前完整备份 */
    MIGRATION("migration", 2),

    /** 用户检查点：用户手动完整备份 */
    USER("user", 10)
}

/**
 * Checkpoint 摘要信息（用于列表展示）
 *
 * @param checkpointId 检查点唯一标识
 * @param saveId 所属存档 ID
 * @param type 检查点类型
 * @param checkpointDate 检查点对应游戏内日期
 * @param createdAt 检查点创建时间（现实时间）
 * @param fileSizeBytes 备份文件大小（字节）
 */
data class CheckpointInfo(
    val checkpointId: String,
    val saveId: String,
    val type: CheckpointType,
    val checkpointDate: String,
    val createdAt: String,
    val fileSizeBytes: Long
)

/**
 * Checkpoint 管理器
 *
 * 通过文件复制实现 checkpoint：保存前将 save.db 复制为 checkpoint 文件，
 * 保存失败时可通过 [rollbackToCheckpoint] 恢复到检查点状态。
 *
 * 实现策略：简单可靠（文件级复制，不依赖增量快照）。
 *
 * 文件路径：
 * - 存档文件：filesDir/saves/save_{saveId}.db
 * - checkpoint 文件：filesDir/saves/checkpoints/{saveId}/cp_{checkpointId}.db
 *
 * @param context 应用上下文
 * @param databaseManager 三库管理入口（用于访问 save.db 读写 checkpoint 记录）
 */
class CheckpointManager(
    private val context: Context,
    private val databaseManager: DatabaseManager
) {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * 创建 checkpoint
     *
     * 流程：
     * 1. 生成 checkpointId
     * 2. 创建 checkpoint 目录
     * 3. 复制当前 save.db 到 checkpoint 文件
     * 4. 计算 MD5 校验和
     * 5. 写入 checkpoint 表记录
     * 6. 更新 save_manifest.last_checkpoint_id
     * 7. 清理过期 checkpoint
     *
     * @param saveId 存档 ID
     * @param type 检查点类型
     * @return 新创建的 checkpoint ID
     */
    suspend fun createCheckpoint(saveId: String, type: CheckpointType): String =
        withContext(Dispatchers.IO) {
            val checkpointId = "cp_${UUID.randomUUID().toString().take(8)}"
            val now = dateTimeFormatter.format(LocalDateTime.now())
            val saveDb = databaseManager.getSaveDatabase()
            val manifest = saveDb.saveManifestDao().get()
                ?: error("无法创建 checkpoint：save_manifest 不存在")

            // 1. 创建 checkpoint 目录
            val checkpointDir = getCheckpointDir(saveId, checkpointId)
            checkpointDir.mkdirs()

            // 2. 复制当前 save.db 到 checkpoint 文件
            val currentDbFile = getSaveFile(saveId)
            val backupDbFile = File(checkpointDir, "cp_$checkpointId.db")
            currentDbFile.copyTo(backupDbFile, overwrite = true)

            // 3. 计算 checksum
            val checksum = calculateMD5(backupDbFile)

            // 4. 写入 checkpoint 表记录
            saveDb.checkpointDao().insert(
                CheckpointEntity(
                    checkpointId = checkpointId,
                    saveId = saveId,
                    checkpointType = type.code,
                    checkpointDate = manifest.currentDate ?: "",
                    filePath = backupDbFile.absolutePath,
                    checksum = checksum,
                    createdAt = now
                )
            )

            // 5. 更新 save_manifest.last_checkpoint_id
            saveDb.saveManifestDao().updateLastCheckpoint(saveId, checkpointId)

            // 6. 清理过期 checkpoint
            cleanOldCheckpoints(saveId, type.maxRetained)

            Log.d("CheckpointManager", "checkpoint 创建成功：$checkpointId（type=$type, saveId=$saveId）")
            checkpointId
        }

    /**
     * 回滚到指定 checkpoint
     *
     * 流程：
     * 1. 查找 checkpoint 记录
     * 2. 校验文件存在与 checksum
     * 3. 关闭当前 save.db 连接
     * 4. 复制 checkpoint 文件覆盖 save.db
     * （重新打开由 [SaveLoader] / [SaveManager] 处理）
     *
     * @param checkpointId 检查点 ID
     * @return true 表示回滚成功，false 表示 checkpoint 不存在或校验失败
     */
    suspend fun rollbackToCheckpoint(checkpointId: String): Boolean = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: return@withContext false
        val cp = saveDb.checkpointDao().getById(checkpointId)
            ?: return@withContext false
        val backupFile = File(cp.filePath)
        if (!backupFile.exists()) {
            Log.e("CheckpointManager", "checkpoint 文件不存在：${cp.filePath}")
            return@withContext false
        }

        // 校验 checksum
        if (!cp.checksum.isNullOrEmpty()) {
            val currentChecksum = calculateMD5(backupFile)
            if (currentChecksum != cp.checksum) {
                Log.e("CheckpointManager", "checkpoint checksum 校验失败")
                return@withContext false
            }
        }

        // 关闭当前 DB 连接，覆盖文件
        val saveId = cp.saveId
        databaseManager.closeSave()
        val currentDbFile = getSaveFile(saveId)
        // 同时清理 WAL/SHM 临时文件
        File(currentDbFile.absolutePath + "-wal").delete()
        File(currentDbFile.absolutePath + "-shm").delete()
        backupFile.copyTo(currentDbFile, overwrite = true)

        Log.d("CheckpointManager", "回滚成功：$checkpointId -> saveId=$saveId")
        true
    }

    /**
     * 列出指定存档的所有 checkpoint
     *
     * @param saveId 存档 ID
     * @return checkpoint 摘要列表（按日期降序）
     */
    suspend fun listCheckpoints(saveId: String): List<CheckpointInfo> = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        saveDb.checkpointDao().getAll(saveId).map { cp ->
            CheckpointInfo(
                checkpointId = cp.checkpointId,
                saveId = cp.saveId,
                type = CheckpointType.values().find { it.code == cp.checkpointType } ?: CheckpointType.LIGHT,
                checkpointDate = cp.checkpointDate,
                createdAt = cp.createdAt ?: "",
                fileSizeBytes = File(cp.filePath).takeIf { it.exists() }?.length() ?: 0L
            )
        }
    }

    /**
     * 清理指定存档的旧 checkpoint（保留最新 N 个，按类型）
     *
     * @param saveId 存档 ID
     * @param keepCount 保留的最新 checkpoint 数量
     */
    suspend fun cleanOldCheckpoints(saveId: String, keepCount: Int) = withContext(Dispatchers.IO) {
        if (keepCount < 0) return@withContext
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext

        // 按类型分别清理
        CheckpointType.values().forEach { type ->
            val all = saveDb.checkpointDao().getByType(saveId, type.code)
            if (all.size > type.maxRetained) {
                // 超过保留数量的旧 checkpoint（列表已按日期降序，跳过前 maxRetained 个）
                val toDelete = all.drop(type.maxRetained)
                toDelete.forEach { cp ->
                    File(cp.filePath).delete()
                    File(cp.filePath).parentFile?.let { dir ->
                        if (dir.exists() && dir.listFiles()?.isEmpty() == true) dir.delete()
                    }
                    saveDb.checkpointDao().delete(cp)
                }
            }
        }
    }

    /**
     * 删除指定存档的全部 checkpoint（删除存档时调用）
     *
     * @param saveId 存档 ID
     */
    suspend fun deleteAllCheckpoints(saveId: String) = withContext(Dispatchers.IO) {
        val checkpointRoot = File(getSavesDir(), "checkpoints/$saveId")
        if (checkpointRoot.exists()) {
            checkpointRoot.deleteRecursively()
        }
    }

    // ==================== 路径工具 ====================

    /** 获取存档文件：filesDir/saves/save_{saveId}.db */
    private fun getSaveFile(saveId: String): File =
        File(SaveDatabase.getSaveDir(context), "save_$saveId.db")

    /** 获取存档目录：filesDir/saves/ */
    private fun getSavesDir(): File = SaveDatabase.getSaveDir(context)

    /** 获取 checkpoint 目录：filesDir/saves/checkpoints/{saveId}/{checkpointId}/ */
    private fun getCheckpointDir(saveId: String, checkpointId: String): File =
        File(getSavesDir(), "checkpoints/$saveId/$checkpointId")

    // ==================== 校验和工具 ====================

    /**
     * 计算文件 MD5 校验和
     *
     * @param file 待计算文件
     * @return 16 进制 MD5 字符串
     */
    private fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
