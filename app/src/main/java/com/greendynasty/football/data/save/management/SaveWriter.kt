package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseConfig
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.entity.PerfLogEntity
import com.greendynasty.football.data.save.management.model.SaveMeta
import com.greendynasty.football.data.save.management.model.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 保存触发类型
 *
 * 控制保存行为的策略（是否创建 checkpoint、是否记录性能日志等）：
 * - AUTO_DAILY：每日推进后自动保存（高频，跳过 checkpoint 创建）
 * - AUTO_MATCH：比赛前自动保存（中频）
 * - MANUAL：玩家手动保存（低频，创建 checkpoint）
 * - SEASON_END：赛季结束保存（低频，创建 SEASON checkpoint）
 * - BEFORE_QUIT：应用退出前保存（低频，创建 checkpoint）
 */
enum class SaveTrigger {

    /** 每日推进后自动保存 */
    AUTO_DAILY,

    /** 比赛前自动保存 */
    AUTO_MATCH,

    /** 玩家手动保存 */
    MANUAL,

    /** 赛季结束保存 */
    SEASON_END,

    /** 应用退出前保存 */
    BEFORE_QUIT
}

/**
 * 存档保存器（异步）
 *
 * 负责存档保存完整流程（V0.2 §六 + T03 方案）：
 * 1. 创建 checkpoint（委托 [CheckpointManager]，高频触发跳过）
 * 2. 异步写入（Dispatchers.IO，更新 manifest 时间戳）
 * 3. 更新 save_manifest 的 last_saved_at
 * 4. 同步元信息 JSON（双写）
 * 5. 失败回滚（委托 [RollbackHandler]）
 * 6. 记录性能日志
 *
 * 异步保存不阻塞 UI：所有 IO 操作在 Dispatchers.IO 执行，
 * 并发保护由 [SaveManager] 通过 per-save Mutex 保证（同一存档不能同时保存）。
 *
 * @param context 应用上下文
 * @param databaseManager 三库管理入口
 */
class SaveWriter(
    private val context: Context,
    private val databaseManager: DatabaseManager
) {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val checkpointManager = CheckpointManager(context, databaseManager)
    private val rollbackHandler = RollbackHandler(context, databaseManager, checkpointManager)

    /**
     * 保存存档（异步）
     *
     * @param saveId 存档 ID
     * @param trigger 保存触发类型
     * @return 保存结果（Success/Failure/RolledBack）
     */
    suspend fun save(saveId: String, trigger: SaveTrigger): SaveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var checkpointId: String? = null

        try {
            val saveDb = databaseManager.getSaveDatabase()

            // 1. 保存前创建 checkpoint（高频触发跳过以减少开销）
            if (trigger != SaveTrigger.AUTO_DAILY && trigger != SaveTrigger.AUTO_MATCH) {
                try {
                    checkpointId = checkpointManager.createCheckpoint(saveId, CheckpointType.LIGHT)
                } catch (e: Exception) {
                    // checkpoint 创建失败不阻断保存，仅记录警告
                    Log.w("SaveWriter", "保存前 checkpoint 创建失败（继续保存）：${e.message}")
                }
            }

            // 2. 执行保存：更新 manifest 时间戳（游戏状态已通过 DAO 实时写入 save.db）
            val now = dateTimeFormatter.format(LocalDateTime.now())
            val manifest = saveDb.saveManifestDao().get()
            if (manifest != null) {
                // 刷新 current_date（与 world_state 保持一致）+ last_played_at
                val worldState = saveDb.saveWorldStateDao().get()
                val currentDate = worldState?.currentDate ?: manifest.currentDate
                saveDb.saveManifestDao().updateLastPlayed(saveId, now)
                if (currentDate != null) {
                    saveDb.saveManifestDao().updateCurrentDate(saveId, currentDate)
                }
            }

            // 3. 同步元信息 JSON（双写）
            updateMetaJson(saveId, now)

            // 4. 记录性能日志
            val durationMs = (System.currentTimeMillis() - startTime).toInt()
            recordPerfLog(saveDb, saveId, trigger, durationMs)

            // 5. 性能预警
            if (durationMs > AUTO_SAVE_TARGET_MS) {
                Log.w("SaveWriter", "保存耗时 ${durationMs}ms 超过目标 ${AUTO_SAVE_TARGET_MS}ms（trigger=$trigger）")
            }

            Log.d("SaveWriter", "保存成功：saveId=$saveId, trigger=$trigger, ${durationMs}ms")
            SaveResult.Success
        } catch (e: Exception) {
            Log.e("SaveWriter", "保存失败：${e.message}", e)

            // 6. 保存失败回滚
            val cpId = checkpointId
            if (cpId != null) {
                val rollbackResult = try {
                    rollbackHandler.rollback(saveId)
                } catch (re: Exception) {
                    Log.e("SaveWriter", "回滚异常：${re.message}", re)
                    null
                }
                if (rollbackResult != null) {
                    return@withContext SaveResult.RolledBack(rollbackResult)
                }
            }
            // 无 checkpoint 或回滚失败，返回 Failure
            SaveResult.Failure("保存失败：${e.message}", e)
        }
    }

    /**
     * 更新元信息 JSON（双写，保证 DB 与 JSON 一致）
     */
    private suspend fun updateMetaJson(saveId: String, now: String) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val manifest = saveDb.saveManifestDao().get() ?: return
        val worldState = saveDb.saveWorldStateDao().get() ?: return
        val dbFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")

        val meta = SaveMeta(
            saveId = saveId,
            saveName = worldState.saveName,
            gameVersion = manifest.gameVersion,
            createdAt = manifest.createdAt,
            lastSavedAt = now,
            gameDate = manifest.currentDate ?: DatabaseConfig.DEFAULT_START_DATE,
            managerClubId = worldState.managerClubId,
            scenarioId = worldState.scenarioId ?: "",
            schemaVersion = manifest.schemaVersion,
            fileSizeBytes = dbFile.length(),
            isLoaded = true
        )
        val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
        metaFile.writeText(json.encodeToString(SaveMeta.serializer(), meta))
    }

    /**
     * 记录性能日志（失败不影响保存结果）
     */
    private suspend fun recordPerfLog(
        saveDb: SaveDatabase,
        saveId: String,
        trigger: SaveTrigger,
        durationMs: Int
    ) {
        try {
            val dbFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")
            saveDb.perfLogDao().insert(
                PerfLogEntity(
                    saveId = saveId,
                    logDate = dateTimeFormatter.format(LocalDateTime.now()),
                    actionType = "save_${trigger.name.lowercase()}",
                    durationMs = durationMs,
                    memoryMb = null,
                    dbSizeMb = dbFile.length() / (1024.0 * 1024.0),
                    extraJson = null
                )
            )
        } catch (e: Exception) {
            Log.w("SaveWriter", "性能日志记录失败（不影响保存）：${e.message}")
        }
    }

    companion object {
        /** 自动保存目标耗时（毫秒），超过则输出性能预警 */
        private const val AUTO_SAVE_TARGET_MS = 2000
    }
}
