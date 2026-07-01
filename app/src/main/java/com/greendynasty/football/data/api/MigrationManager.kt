package com.greendynasty.football.data.api

import com.greendynasty.football.data.migration.MigrationRegistry
import com.greendynasty.football.data.migration.SchemaVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 迁移管理器（简化版框架）
 *
 * 负责 save.db 的 schema 版本迁移，检测存档 schema_version 是否需要升级，
 * 并在需要时执行已注册的迁移脚本。
 *
 * 当前状态：
 * - Room schema version = 1（[SchemaVersion.CURRENT]）
 * - [MigrationRegistry.ALL_MIGRATIONS] 为空，无需迁移
 * - 框架已预留，未来版本升级时只需在 [MigrationRegistry] 中注册迁移脚本
 *
 * 迁移流程（V0.2 §五）：
 * 1. 检测 save_manifest.schema_version
 * 2. 若版本 < 当前版本，查找迁移脚本
 * 3. 创建迁移前 checkpoint 备份
 * 4. 执行迁移脚本
 * 5. 校验完整性
 * 6. 失败则回滚
 * 7. 成功则更新 schema_version
 *
 * 注意：当前为简化版框架，仅实现步骤 1-2 的检测逻辑。
 * T10 将完整实现 checkpoint 备份、迁移执行、回滚等流程。
 *
 * @param databaseManager 三库管理入口
 */
class MigrationManager(private val databaseManager: DatabaseManager) {

    /**
     * 检测当前存档是否需要迁移。
     * 需在存档已打开后调用。
     *
     * @return MigrationCheckResult 检测结果
     */
    suspend fun checkMigrationNeeded(): MigrationCheckResult = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: return@withContext MigrationCheckResult.NoSaveLoaded

        val manifest = saveDb.saveManifestDao().get()
            ?: return@withContext MigrationCheckResult.Error("save_manifest 不存在，无法检测版本")

        val currentVersion = manifest.schemaVersion
        val targetVersion = SchemaVersion.CURRENT

        when {
            currentVersion == targetVersion -> {
                // 版本一致，无需迁移
                MigrationCheckResult.UpToDate(currentVersion)
            }
            currentVersion < targetVersion -> {
                // 存档版本较低，需要迁移
                MigrationCheckResult.NeedsMigration(
                    fromVersion = currentVersion,
                    toVersion = targetVersion
                )
            }
            else -> {
                // 存档版本高于当前 app 支持的版本（未来版本创建的存档）
                MigrationCheckResult.Incompatible(
                    saveVersion = currentVersion,
                    appVersion = targetVersion
                )
            }
        }
    }

    /**
     * 执行迁移（简化版框架）。
     *
     * 当前仅支持 v1，无实际迁移脚本需要执行。
     * 未来版本升级后，此方法将：
     * 1. 创建迁移前 checkpoint
     * 2. 逐版本执行 MigrationRegistry 中注册的迁移脚本
     * 3. 校验完整性
     * 4. 失败回滚
     * 5. 更新 schema_version
     *
     * @param fromVersion 起始版本
     * @param toVersion 目标版本
     * @return MigrationResult 迁移结果
     */
    suspend fun migrate(fromVersion: Int, toVersion: Int): MigrationResult = withContext(Dispatchers.IO) {
        // 1. 参数校验
        if (fromVersion >= toVersion) {
            return@withContext MigrationResult.Error("起始版本($fromVersion) >= 目标版本($toVersion)，无需迁移")
        }

        // 2. 检查版本是否合法
        if (!SchemaVersion.isValid(fromVersion) || !SchemaVersion.isValid(toVersion)) {
            return@withContext MigrationResult.Error("版本号不合法：$fromVersion -> $toVersion")
        }

        // 3. 检查迁移脚本是否已注册
        val availableMigrations = MigrationRegistry.ALL_MIGRATIONS
        if (availableMigrations.isEmpty()) {
            return@withContext MigrationResult.Error(
                "无已注册的迁移脚本（$fromVersion -> $toVersion）。" +
                    "当前 SchemaVersion.CURRENT=${SchemaVersion.CURRENT}，Room version=1，暂不支持迁移。"
            )
        }

        // 4. 获取存档数据库
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: return@withContext MigrationResult.Error("存档未加载，请先 openSave()")

        // TODO: T10 完整实现以下流程：
        // - 创建迁移前 checkpoint（CheckpointManager.createCheckpoint(MIGRATION)）
        // - 校验剩余存储空间（至少 100MB）
        // - 逐版本执行迁移脚本
        // - 校验关键表完整性（SaveIntegrityChecker）
        // - 失败回滚（CheckpointManager.restoreFromCheckpoint）
        // - 成功后更新 save_manifest.schema_version

        // 当前简化版：直接返回成功（无实际迁移需要执行）
        MigrationResult.Success(fromVersion, toVersion)
    }

    /**
     * 获取当前存档的 schema 版本。
     *
     * @return schema 版本号，若存档未加载或 manifest 不存在则返回 null
     */
    suspend fun getCurrentSchemaVersion(): Int? = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null
        saveDb.saveManifestDao().get()?.schemaVersion
    }

    /**
     * 获取已注册的迁移脚本数量。
     *
     * @return 迁移脚本数组长度
     */
    fun getRegisteredMigrationCount(): Int = MigrationRegistry.ALL_MIGRATIONS.size

    companion object {
        /**
         * 获取当前 app 支持的最高 schema 版本。
         *
         * @return SchemaVersion.CURRENT
         */
        fun getCurrentSchemaVersionStatic(): Int = SchemaVersion.CURRENT
    }
}

/**
 * 迁移检测结果
 */
sealed class MigrationCheckResult {
    /** 存档未加载 */
    object NoSaveLoaded : MigrationCheckResult()

    /** 版本已是最新的，无需迁移 */
    data class UpToDate(val version: Int) : MigrationCheckResult()

    /** 需要迁移 */
    data class NeedsMigration(
        val fromVersion: Int,
        val toVersion: Int
    ) : MigrationCheckResult()

    /** 存档版本高于 app 支持版本，不兼容 */
    data class Incompatible(
        val saveVersion: Int,
        val appVersion: Int
    ) : MigrationCheckResult()

    /** 检测出错 */
    data class Error(val message: String) : MigrationCheckResult()
}

/**
 * 迁移执行结果
 */
sealed class MigrationResult {
    /** 迁移成功 */
    data class Success(val from: Int, val to: Int) : MigrationResult()

    /** 迁移失败 */
    data class Error(val message: String) : MigrationResult()

    /** 迁移不兼容（无法自动升级） */
    data class Incompatible(val from: Int, val to: Int) : MigrationResult()
}
