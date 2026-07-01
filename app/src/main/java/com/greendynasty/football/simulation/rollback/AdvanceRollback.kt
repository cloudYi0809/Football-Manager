package com.greendynasty.football.simulation.rollback

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.CheckpointManager
import com.greendynasty.football.data.save.management.CheckpointType
import java.time.LocalDate

/**
 * 推进前快照（T07 方案 §九）
 *
 * 封装推进前的关键状态，用于失败时回滚。
 *
 * @param currentDate 推进前游戏内日期
 * @param saveUuid 存档 UUID
 * @param checkpointId 轻量 checkpoint ID（若创建了）
 */
data class PreAdvanceSnapshot(
    val currentDate: LocalDate,
    val saveUuid: String,
    val checkpointId: String?
)

/**
 * 推进回滚机制（V0.1 11 §十 + T07 方案 §九）
 *
 * V0.1 11 §十："如果推进失败，回滚到上一日。"
 *
 * 策略：
 * - 推进前可选创建轻量 checkpoint（lightCheckpointOnAdvance 配置）
 * - 推进失败时从 checkpoint 恢复
 * - V1 默认关闭轻量 checkpoint（性能考虑），仅赛季结束创建完整 checkpoint
 *
 * @param databaseManager 三库管理入口
 * @param checkpointManager checkpoint 管理器
 */
class AdvanceRollback(
    private val databaseManager: DatabaseManager,
    private val checkpointManager: CheckpointManager
) {

    /**
     * 创建推进前快照
     *
     * @param currentDate 当前游戏内日期
     * @param saveUuid 存档 UUID
     * @param createLightCheckpoint 是否创建轻量 checkpoint
     * @return 推进前快照
     */
    suspend fun createPreAdvanceSnapshot(
        currentDate: LocalDate,
        saveUuid: String,
        createLightCheckpoint: Boolean
    ): PreAdvanceSnapshot {
        val checkpointId = if (createLightCheckpoint) {
            try {
                checkpointManager.createCheckpoint(saveUuid, CheckpointType.LIGHT)
            } catch (e: Exception) {
                Log.w(TAG, "创建轻量 checkpoint 失败（继续推进，回滚能力受限）：${e.message}")
                null
            }
        } else {
            null
        }

        return PreAdvanceSnapshot(
            currentDate = currentDate,
            saveUuid = saveUuid,
            checkpointId = checkpointId
        )
    }

    /**
     * 回滚到推进前状态
     *
     * 流程：
     * 1. 若有 checkpoint，从 checkpoint 恢复
     * 2. 重新打开 save.db
     * 3. 验证日期回滚成功
     *
     * @param snapshot 推进前快照
     * @return true 表示回滚成功
     */
    suspend fun rollback(snapshot: PreAdvanceSnapshot): Boolean {
        val checkpointId = snapshot.checkpointId
        if (checkpointId == null) {
            // 无 checkpoint，只能回滚日期
            Log.w(TAG, "无 checkpoint 可用，仅回滚日期字段")
            return rollbackDateOnly(snapshot)
        }

        return try {
            // 1. 从 checkpoint 恢复（内部会关闭并覆盖 save.db）
            val success = checkpointManager.rollbackToCheckpoint(checkpointId)
            if (!success) {
                Log.e(TAG, "checkpoint 回滚失败")
                return rollbackDateOnly(snapshot)
            }

            // 2. 重新打开 save.db
            databaseManager.openSave(snapshot.saveUuid)

            // 3. 验证日期回滚
            val saveDb = databaseManager.getSaveDatabase()
            val manifest = saveDb.saveManifestDao().get()
            val worldState = saveDb.saveWorldStateDao().get()
            val restoredDate = manifest?.currentDate ?: worldState?.currentDate
            if (restoredDate != snapshot.currentDate.toString()) {
                Log.w(TAG, "回滚后日期校验不一致：expected=${snapshot.currentDate}, actual=$restoredDate")
            }

            Log.i(TAG, "回滚成功：checkpoint=$checkpointId, date=${snapshot.currentDate}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "回滚异常：${e.message}", e)
            rollbackDateOnly(snapshot)
        }
    }

    /**
     * 仅回滚日期字段（无 checkpoint 时的降级方案）
     */
    private suspend fun rollbackDateOnly(snapshot: PreAdvanceSnapshot): Boolean {
        return try {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return false
            val dateStr = snapshot.currentDate.toString()
            saveDb.saveManifestDao().updateCurrentDate(snapshot.saveUuid, dateStr)
            saveDb.saveWorldStateDao().updateCurrentDate(
                saveId = 1, // V1 单存档场景
                date = dateStr,
                updatedAt = dateStr
            )
            Log.i(TAG, "日期回滚成功：$dateStr")
            true
        } catch (e: Exception) {
            Log.e(TAG, "日期回滚失败：${e.message}", e)
            false
        }
    }

    companion object {
        private const val TAG = "AdvanceRollback"
    }
}
