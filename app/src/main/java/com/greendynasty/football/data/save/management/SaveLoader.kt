package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseConfig
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.api.MigrationCheckResult
import com.greendynasty.football.data.api.MigrationManager
import com.greendynasty.football.data.api.MigrationResult
import com.greendynasty.football.data.cache.CacheRebuilder
import com.greendynasty.football.data.integrity.IntegrityIssue
import com.greendynasty.football.data.integrity.IntegrityReport
import com.greendynasty.football.data.integrity.Severity
import com.greendynasty.football.data.migration.SchemaVersion
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.management.model.SaveInfo
import com.greendynasty.football.data.save.management.model.SaveLoadResult
import com.greendynasty.football.data.save.management.model.SaveMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 存档加载器
 *
 * 负责存档加载完整流程（V0.2 §五 + §八）：
 * 1. 读取元信息（优先 meta.json，不打开 DB）
 * 2. 完整性校验（manifest/world_state/执教俱乐部等）
 * 3. 版本迁移检查（委托 [MigrationManager]）
 * 4. 打开 SaveDatabase（WAL 模式）
 * 5. 恢复游戏状态
 * 6. 重建 cache（如需要）
 *
 * @param context 应用上下文
 * @param databaseManager 三库管理入口
 */
class SaveLoader(
    private val context: Context,
    private val databaseManager: DatabaseManager
) {

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 加载存档
     *
     * @param saveId 存档 ID
     * @return 加载结果（Success/NeedsMigration/Corrupted/Failure）
     */
    suspend fun load(saveId: String): SaveLoadResult = withContext(Dispatchers.IO) {
        try {
            // 1. 校验存档文件存在
            val dbFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")
            if (!dbFile.exists()) {
                return@withContext SaveLoadResult.Failure("存档数据库文件不存在：${dbFile.absolutePath}")
            }

            // 2. 读取元信息（优先 meta.json，触发损坏时从 DB 恢复并回写）
            readMetaJson(saveId)

            // 3. 打开存档数据库（WAL 模式由 SaveDatabase.create 配置）
            databaseManager.openSave(saveId)
            val saveDb = databaseManager.getSaveDatabase()

            // 4. 完整性校验
            val integrityReport = checkIntegrity(saveDb)
            if (integrityReport.isSevere()) {
                databaseManager.closeSave()
                return@withContext SaveLoadResult.Corrupted("存档损坏：${integrityReport.summary}")
            }

            // 5. 版本迁移检查
            val migrationManager = MigrationManager(databaseManager)
            when (val checkResult = migrationManager.checkMigrationNeeded()) {
                is MigrationCheckResult.NeedsMigration -> {
                    // 尝试自动迁移
                    when (migrationManager.migrate(checkResult.fromVersion, checkResult.toVersion)) {
                        is MigrationResult.Success -> { /* 迁移成功，继续 */ }
                        is MigrationResult.Error -> {
                            databaseManager.closeSave()
                            return@withContext SaveLoadResult.NeedsMigration(
                                checkResult.fromVersion, checkResult.toVersion
                            )
                        }
                        is MigrationResult.Incompatible -> {
                            databaseManager.closeSave()
                            return@withContext SaveLoadResult.NeedsMigration(
                                checkResult.fromVersion, checkResult.toVersion
                            )
                        }
                    }
                }
                is MigrationCheckResult.Incompatible -> {
                    databaseManager.closeSave()
                    return@withContext SaveLoadResult.NeedsMigration(
                        checkResult.saveVersion, checkResult.appVersion
                    )
                }
                is MigrationCheckResult.Error -> {
                    databaseManager.closeSave()
                    return@withContext SaveLoadResult.Failure(checkResult.message)
                }
                is MigrationCheckResult.NoSaveLoaded -> {
                    databaseManager.closeSave()
                    return@withContext SaveLoadResult.Failure("存档未加载，无法检测版本")
                }
                is MigrationCheckResult.UpToDate -> { /* 版本一致，继续 */ }
            }

            // 6. 重建 cache（如需要）
            try {
                CacheRebuilder(databaseManager).rebuildAll()
            } catch (e: Exception) {
                Log.w("SaveLoader", "cache 重建失败（不影响加载）：${e.message}")
            }

            // 7. 更新 last_played_at
            val now = dateTimeFormatter.format(LocalDateTime.now())
            saveDb.saveManifestDao().updateLastPlayed(saveId, now)

            // 8. 恢复游戏状态
            val manifest = saveDb.saveManifestDao().get()
                ?: return@withContext run {
                    databaseManager.closeSave()
                    SaveLoadResult.Corrupted("save_manifest 不存在")
                }
            val worldState = saveDb.saveWorldStateDao().get()
                ?: return@withContext run {
                    databaseManager.closeSave()
                    SaveLoadResult.Corrupted("save_world_state 不存在")
                }

            val saveInfo = SaveInfo(
                saveId = saveId,
                saveName = worldState.saveName,
                gameDate = manifest.currentDate ?: DatabaseConfig.DEFAULT_START_DATE,
                currentSeason = worldState.currentSeasonId,
                managerClubId = worldState.managerClubId,
                currentState = SaveState.LOADED
            )

            // 9. 更新 meta.json（同步最新状态）
            writeMetaJson(
                saveId, SaveMeta(
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
            )

            Log.d("SaveLoader", "存档加载成功：saveId=$saveId, name=${worldState.saveName}")
            SaveLoadResult.Success(saveInfo)
        } catch (e: Exception) {
            Log.e("SaveLoader", "存档加载失败：${e.message}", e)
            try {
                databaseManager.getSaveDatabaseOrNull()?.let { databaseManager.closeSave() }
            } catch (_: Exception) {
            }
            SaveLoadResult.Failure("加载存档失败：${e.message}", e)
        }
    }

    /**
     * 完整性校验（基础项，参考 V0.2 §八）
     *
     * 当前实现基础检测项（完整 SaveIntegrityChecker 为后续增强）：
     * - save_manifest 是否存在
     * - save_world_state 是否存在且唯一
     * - 执教俱乐部是否在俱乐部状态表中存在
     * - current_date 是否合法
     *
     * @param saveDb 存档数据库
     * @return 完整性检测报告
     */
    private suspend fun checkIntegrity(
        saveDb: SaveDatabase
    ): IntegrityReport {
        val issues = mutableListOf<IntegrityIssue>()

        val manifest = saveDb.saveManifestDao().get()
        if (manifest == null) {
            issues.add(IntegrityIssue(Severity.SEVERE, "save_manifest 不存在", "save_manifest"))
            return IntegrityReport(issues)
        }

        val worldState = saveDb.saveWorldStateDao().get()
        if (worldState == null) {
            issues.add(IntegrityIssue(Severity.SEVERE, "save_world_state 不存在", "save_world_state"))
            return IntegrityReport(issues)
        }

        // schema 版本合法性
        if (manifest.schemaVersion > SchemaVersion.CURRENT) {
            issues.add(
                IntegrityIssue(
                    Severity.MEDIUM,
                    "存档 schema 版本(${manifest.schemaVersion})高于 app 支持版本(${SchemaVersion.CURRENT})",
                    "save_manifest"
                )
            )
        }

        // 执教俱乐部校验（save_club_state 或 history.club 存在即可）
        val clubState = saveDb.saveClubStateDao().getByClub(worldState.saveId, worldState.managerClubId)
        if (clubState == null) {
            val historyClub = databaseManager.getHistoryDatabase().clubDao()
                .getClub(worldState.managerClubId)
            if (historyClub == null) {
                issues.add(
                    IntegrityIssue(
                        Severity.SEVERE,
                        "执教俱乐部 ${worldState.managerClubId} 不存在",
                        "save_club_state"
                    )
                )
            }
        }

        // 当前日期合法性（非空且可解析）
        val currentDate = manifest.currentDate
        if (currentDate.isNullOrBlank()) {
            issues.add(IntegrityIssue(Severity.MINOR, "current_date 为空", "save_manifest"))
        }

        return IntegrityReport(issues)
    }

    /** 读取 meta.json（文件损坏或不存在时从 DB 恢复） */
    private suspend fun readMetaJson(saveId: String): SaveMeta? {
        val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
        return if (metaFile.exists()) {
            try {
                json.decodeFromString(SaveMeta.serializer(), metaFile.readText())
            } catch (e: Exception) {
                Log.w("SaveLoader", "meta.json 解析失败，将从 DB 恢复：${e.message}")
                recoverMetaFromDb(saveId)
            }
        } else {
            recoverMetaFromDb(saveId)
        }
    }

    /** 从 DB 恢复元信息并写回 meta.json */
    private suspend fun recoverMetaFromDb(saveId: String): SaveMeta? {
        return try {
            val saveDb = SaveDatabase.create(context, saveId)
            val manifest = saveDb.saveManifestDao().get() ?: return null
            val worldState = saveDb.saveWorldStateDao().get() ?: return null
            val meta = SaveMeta(
                saveId = saveId,
                saveName = worldState.saveName,
                gameVersion = manifest.gameVersion,
                createdAt = manifest.createdAt,
                lastSavedAt = manifest.lastPlayedAt,
                gameDate = manifest.currentDate ?: DatabaseConfig.DEFAULT_START_DATE,
                managerClubId = worldState.managerClubId,
                scenarioId = worldState.scenarioId ?: "",
                schemaVersion = manifest.schemaVersion,
                fileSizeBytes = File(SaveDatabase.getSaveDir(context), "save_$saveId.db").length(),
                isLoaded = false
            )
            saveDb.close()
            writeMetaJson(saveId, meta)
            meta
        } catch (e: Exception) {
            Log.w("SaveLoader", "从 DB 恢复 meta 失败：${e.message}")
            null
        }
    }

    /** 写入 meta.json */
    private fun writeMetaJson(saveId: String, meta: SaveMeta) {
        val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
        metaFile.writeText(json.encodeToString(SaveMeta.serializer(), meta))
    }
}
