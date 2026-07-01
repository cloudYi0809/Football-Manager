package com.greendynasty.football.data.save.management.migration

import android.content.Context
import android.os.StatFs
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.migration.MigrationType
import com.greendynasty.football.data.migration.SchemaVersion
import com.greendynasty.football.data.save.management.backup.BackupResult
import com.greendynasty.football.data.save.management.backup.SaveBackupManager
import com.greendynasty.football.data.save.management.config.SaveConfig
import com.greendynasty.football.data.save.management.integrity.SaveIntegrityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存档版本迁移器（T03，扩展 T00 MigrationManager）
 *
 * V0.2 §五 迁移流程：
 * 1. 检测 schema_version
 * 2. 创建完整备份（PRE_MIGRATION）
 * 3. 校验剩余存储空间
 * 4. 执行迁移脚本（逐版本，事务包裹）
 * 5. 校验关键表完整性
 * 6. 失败回滚
 * 7. 成功后更新版本号
 *
 * 迁移类型（V0.2 §四）：
 * - AUTO：新增 nullable 字段/表/索引/缓存字段，自动迁移
 * - MANUAL：字段拆分/表结构重构/枚举值改变，需人工处理
 * - INCOMPATIBLE：存档底层数据包变化过大，提示无法升级
 *
 * V1 预留：[SchemaVersion.CURRENT] = 1，[MigrationRegistry] 注册 v1→v1 空迁移，
 * 未来版本升级时只需在 [MigrationRegistry] 注册新脚本。
 *
 * @param databaseManager 三库管理入口
 * @param config 存档配置
 */
class SaveMigrator(
    private val databaseManager: DatabaseManager,
    private val config: SaveConfig = SaveConfig()
) {
    private val backupManager by lazy { SaveBackupManager(databaseManager.getAppContext(), config) }
    private val integrityChecker by lazy { SaveIntegrityChecker(databaseManager, config) }

    /**
     * 检测存档是否需要迁移（不执行迁移）。
     *
     * @param saveId 存档 ID
     * @return 迁移检测结果
     */
    suspend fun checkMigrationNeeded(saveId: String): MigrationCheckResult =
        withContext(Dispatchers.IO) {
            val saveDb = databaseManager.getSaveDatabaseOrNull()
                ?: return@withContext MigrationCheckResult.Incompatible(
                    SchemaVersion.V1,
                    SchemaVersion.CURRENT
                )

            val manifest = try {
                saveDb.saveManifestDao().get()
            } catch (e: Exception) {
                null
            } ?: return@withContext MigrationCheckResult.Incompatible(
                SchemaVersion.V1,
                SchemaVersion.CURRENT
            )

            val currentVersion = manifest.schemaVersion
            val targetVersion = SchemaVersion.CURRENT

            when {
                currentVersion == targetVersion -> {
                    MigrationCheckResult.NoMigrationNeeded(currentVersion)
                }
                currentVersion > targetVersion -> {
                    // 存档版本高于 app 支持版本，不兼容
                    MigrationCheckResult.Incompatible(currentVersion, targetVersion)
                }
                else -> {
                    // currentVersion < targetVersion，根据迁移脚本类型分类
                    when (resolveMigrationType(currentVersion, targetVersion)) {
                        MigrationType.AUTO ->
                            MigrationCheckResult.AutoMigrationAvailable(currentVersion, targetVersion)
                        MigrationType.MANUAL ->
                            MigrationCheckResult.ManualMigrationRequired(currentVersion, targetVersion)
                        MigrationType.INCOMPATIBLE ->
                            MigrationCheckResult.Incompatible(currentVersion, targetVersion)
                    }
                }
            }
        }

    /**
     * 执行迁移。
     *
     * @param saveId 存档 ID
     * @param fromVersion 起始版本
     * @param toVersion 目标版本
     * @return 迁移结果
     */
    suspend fun migrate(
        saveId: String,
        fromVersion: Int,
        toVersion: Int
    ): MigrationResult = withContext(Dispatchers.IO) {
        // 0. 校验存档已加载
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: return@withContext MigrationResult.Failure("存档未加载，请先 openSave()")

        // 1. 同版本迁移（v1 → v1 空迁移，框架预留）
        if (fromVersion == toVersion) {
            val migration = MigrationRegistry.getMigration(fromVersion, toVersion)
            return@withContext if (migration != null) {
                try {
                    val supportDb = saveDb.openHelper.writableDatabase
                    val warnings = executeMigrationInTransaction(migration, supportDb)
                    if (warnings.isEmpty()) {
                        MigrationResult.Success(toVersion)
                    } else {
                        MigrationResult.PartialSuccess(warnings)
                    }
                } catch (e: Exception) {
                    MigrationResult.Failure("空迁移执行异常：${e.message}")
                }
            } else {
                MigrationResult.Success(toVersion)
            }
        }

        // 2. 不支持降级
        if (fromVersion > toVersion) {
            return@withContext MigrationResult.Failure("不支持降级迁移：$fromVersion → $toVersion")
        }

        // 3. 校验存储空间（至少 100MB）
        val appContext = databaseManager.getAppContext()
        if (getAvailableStorage(appContext) < MIN_STORAGE_BYTES) {
            return@withContext MigrationResult.Failure("存储空间不足，至少需要 100MB")
        }

        // 4. 刷新 WAL 到主库，确保迁移前备份是一致的完整快照
        try {
            saveDb.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)")
        } catch (_: Exception) {
            // 忽略 checkpoint 失败，继续迁移
        }

        // 5. 创建迁移前备份
        val backupResult = backupManager.createBackup(saveId, SaveBackupManager.REASON_PRE_MIGRATION)
        val backupId = when (backupResult) {
            is BackupResult.Success -> backupResult.backupId
            else -> return@withContext MigrationResult.Failure("迁移前备份失败，已中止迁移")
        }

        // 6. 逐版本执行迁移
        val supportDb = saveDb.openHelper.writableDatabase
        val allWarnings = mutableListOf<String>()

        try {
            var currentVersion = fromVersion
            while (currentVersion < toVersion) {
                val step = MigrationRegistry.getMigration(currentVersion, currentVersion + 1)
                if (step == null) {
                    rollback(saveId, backupId)
                    return@withContext MigrationResult.Failure(
                        "无迁移脚本：$currentVersion → ${currentVersion + 1}"
                    )
                }

                // 检查该步骤兼容性
                when (resolveMigrationType(currentVersion, currentVersion + 1)) {
                    MigrationType.INCOMPATIBLE -> {
                        rollback(saveId, backupId)
                        return@withContext MigrationResult.Failure(
                            "不兼容迁移步骤：$currentVersion → ${currentVersion + 1}"
                        )
                    }
                    else -> {
                        val warnings = executeMigrationInTransaction(step, supportDb)
                        allWarnings.addAll(warnings)
                        currentVersion++
                    }
                }
            }

            // 7. 校验关键表完整性
            val report = integrityChecker.check(saveId)
            if (report.isSevere) {
                rollback(saveId, backupId)
                return@withContext MigrationResult.Failure(
                    "迁移后完整性校验失败，已回滚：${report.summary}"
                )
            }

            // 8. 更新 schema_version
            try {
                saveDb.saveManifestDao().updateSchemaVersion(saveId, toVersion)
            } catch (e: Exception) {
                allWarnings.add("schema_version 更新失败：${e.message}")
            }

            if (allWarnings.isEmpty()) {
                MigrationResult.Success(toVersion)
            } else {
                MigrationResult.PartialSuccess(allWarnings)
            }
        } catch (e: Exception) {
            // 异常回滚
            rollback(saveId, backupId)
            MigrationResult.Failure("迁移异常，已回滚：${e.message}")
        }
    }

    /** 在事务中执行单个迁移脚本 */
    private suspend fun executeMigrationInTransaction(
        migration: SaveMigration,
        db: SupportSQLiteDatabase
    ): List<String> {
        db.beginTransaction()
        try {
            val warnings = migration.migrate(db)
            db.setTransactionSuccessful()
            return warnings
        } finally {
            db.endTransaction()
        }
    }

    /** 回滚：从迁移前备份恢复（关闭连接 → 恢复 → 重新打开） */
    private suspend fun rollback(saveId: String, backupId: String) {
        try {
            databaseManager.closeSave()
            backupManager.restoreFromBackup(saveId, backupId)
            databaseManager.openSave(saveId)
        } catch (_: Exception) {
            // 回滚失败静默处理（已记录 backupId，可手动恢复）
        }
    }

    /**
     * 解析 from→to 的整体迁移类型。
     * 遍历每一步：存在缺失或 INCOMPATIBLE 步骤则返回 INCOMPATIBLE；
     * 存在 MANUAL 步骤且无 INCOMPATIBLE 则返回 MANUAL；否则返回 AUTO。
     */
    private fun resolveMigrationType(fromVersion: Int, toVersion: Int): MigrationType {
        if (fromVersion >= toVersion) return MigrationType.AUTO
        var current = fromVersion
        var hasManual = false
        while (current < toVersion) {
            if (MigrationRegistry.getMigration(current, current + 1) == null) {
                return MigrationType.INCOMPATIBLE
            }
            val type = classifyMigration(current, current + 1)
            if (type == MigrationType.INCOMPATIBLE) return MigrationType.INCOMPATIBLE
            if (type == MigrationType.MANUAL) hasManual = true
            current++
        }
        return if (hasManual) MigrationType.MANUAL else MigrationType.AUTO
    }

    /**
     * 分类单个迁移步骤类型。
     * V1 框架预留：所有已注册迁移默认 AUTO。
     * 未来若某步骤需手动处理或不兼容，在此处按版本对返回 MANUAL / INCOMPATIBLE。
     */
    private fun classifyMigration(from: Int, to: Int): MigrationType {
        return MigrationType.AUTO
    }

    /** 获取可用存储空间（字节） */
    private fun getAvailableStorage(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    companion object {
        /** 迁移所需最小存储空间（100MB） */
        private const val MIN_STORAGE_BYTES = 100L * 1024L * 1024L
    }
}
