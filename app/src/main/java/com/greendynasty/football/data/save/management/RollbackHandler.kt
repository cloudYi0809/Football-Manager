package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.CheckpointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 保存失败回滚处理器
 *
 * 当 [SaveWriter] 保存过程中发生异常时，调用本处理器从最近的 checkpoint 恢复存档，
 * 保证存档数据回到保存前的稳定状态，避免半写入导致的数据损坏。
 *
 * 回滚策略（优先级从高到低）：
 * 1. save_manifest.last_checkpoint_id 指向的 checkpoint
 * 2. 最近的 LIGHT 类型 checkpoint（保存前快照）
 * 3. 最近的任意类型 checkpoint
 * 4. 无可用 checkpoint 时无法回滚（首次保存场景，记录日志）
 *
 * @param context 应用上下文
 * @param databaseManager 三库管理入口
 * @param checkpointManager checkpoint 管理器
 */
class RollbackHandler(
    private val context: Context,
    private val databaseManager: DatabaseManager,
    private val checkpointManager: CheckpointManager
) {

    /**
     * 执行回滚
     *
     * @param saveId 存档 ID
     * @return 回滚使用的 checkpoint ID，null 表示无可用 checkpoint 或回滚失败
     */
    suspend fun rollback(saveId: String): String? = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull()
        if (saveDb == null) {
            // save.db 未打开（可能保存失败时已关闭），尝试通过 checkpoint 表外文件回滚
            // 此处依赖 checkpointManager.rollbackToCheckpoint 需要 DB 打开，故无法继续
            Log.e("RollbackHandler", "回滚失败：save.db 未加载（saveId=$saveId）")
            return@withContext null
        }

        // 1. 优先使用 manifest.last_checkpoint_id
        val manifest = saveDb.saveManifestDao().get()
        var target: CheckpointEntity? = null
        if (manifest != null && !manifest.lastCheckpointId.isNullOrEmpty()) {
            target = saveDb.checkpointDao().getById(manifest.lastCheckpointId)
        }

        // 2. 退而求其次：最近的 LIGHT 类型 checkpoint
        if (target == null) {
            val lightCheckpoints = saveDb.checkpointDao().getByType(saveId, CheckpointType.LIGHT.code)
            target = lightCheckpoints.firstOrNull()
        }

        // 3. 再退一步：最近的任意类型 checkpoint
        if (target == null) {
            target = saveDb.checkpointDao().getLatest(saveId)
        }

        // 4. 无可用 checkpoint
        if (target == null) {
            Log.w("RollbackHandler", "无可用 checkpoint，无法回滚（saveId=$saveId，可能是首次保存）")
            return@withContext null
        }

        val checkpointId = target.checkpointId
        val success = checkpointManager.rollbackToCheckpoint(checkpointId)
        if (success) {
            Log.d("RollbackHandler", "回滚成功：saveId=$saveId, checkpointId=$checkpointId")
            checkpointId
        } else {
            Log.e("RollbackHandler", "回滚失败：checkpointId=$checkpointId")
            null
        }
    }
}
