package com.greendynasty.football.data.api

import com.greendynasty.football.data.save.entity.SaveManifestEntity
import com.greendynasty.football.data.save.entity.SaveWorldStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 存档管理器（简化版，T03 再细化）
 *
 * 负责存档的创建、加载、删除、列表等高层操作，底层委托 [DatabaseManager] 管理数据库连接。
 *
 * 存档文件路径：filesDir/saves/save_{saveId}.db
 * 每个存档对应一个独立的 .db 文件，通过 saveId（UUID）区分。
 *
 * 使用方式：
 * ```
 * val saveManager = SaveManager(DatabaseManager.getInstance(context))
 * val saveId = saveManager.createSave(saveName = "我的王朝", managerClubId = 100)
 * saveManager.loadSave(saveId)
 * saveManager.deleteSave(saveId)
 * ```
 *
 * 注意：当前为简化版，仅实现存档元数据的创建与基础管理。
 * T03 将细化：从 history.db 复制初始球员/俱乐部状态、创建首个 checkpoint、完整性检测等。
 *
 * @param databaseManager 三库管理入口
 */
class SaveManager(private val databaseManager: DatabaseManager) {

    /** 日期时间格式化器（ISO 格式） */
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // ==================== 存档创建 ====================

    /**
     * 创建新存档。
     *
     * 流程：
     * 1. 生成 saveId（UUID）
     * 2. 通过 DatabaseManager 打开（创建）save.db 文件
     * 3. 写入 save_manifest（存档元数据）
     * 4. 写入 save_world_state（世界状态）
     *
     * @param saveName 存档名称（玩家可见）
     * @param managerClubId 玩家执教的俱乐部 ID
     * @param scenarioConfig 场景配置 JSON（可选，用于自定义开档参数）
     * @param dataPackId 数据包 ID（可选，标识使用的数据包）
     * @return 新创建的存档 ID（UUID）
     */
    suspend fun createSave(
        saveName: String,
        managerClubId: Int,
        scenarioConfig: String? = null,
        dataPackId: String? = null
    ): String = withContext(Dispatchers.IO) {
        // 1. 生成存档 UUID
        val saveId = UUID.randomUUID().toString()

        // 2. 打开（创建）存档数据库
        databaseManager.openSave(saveId)
        val saveDb = databaseManager.getSaveDatabase()

        // 3. 当前时间戳
        val now = LocalDateTime.now()
        val nowDateTime = dateTimeFormatter.format(now)

        // 4. 写入 save_manifest（存档元数据）
        val manifest = SaveManifestEntity(
            saveId = saveId,
            gameVersion = GAME_VERSION,
            schemaVersion = DatabaseConfig.SAVE_SCHEMA_VERSION,
            dataPackId = dataPackId,
            dataPackVersion = null,
            currentDate = DatabaseConfig.DEFAULT_START_DATE,
            createdAt = nowDateTime,
            lastPlayedAt = nowDateTime,
            lastCheckpointId = null
        )
        saveDb.saveManifestDao().insert(manifest)

        // 5. 写入 save_world_state（世界状态）
        val worldState = SaveWorldStateEntity(
            saveName = saveName,
            currentDate = DatabaseConfig.DEFAULT_START_DATE,
            currentSeasonId = DatabaseConfig.DEFAULT_START_SEASON_ID,
            managerClubId = managerClubId,
            mode = DatabaseConfig.DEFAULT_MODE,
            scenarioId = null,
            configJson = scenarioConfig,
            createdAt = nowDateTime,
            updatedAt = nowDateTime
        )
        saveDb.saveWorldStateDao().insert(worldState)

        // TODO: T03 细化以下步骤：
        // - 从 history.db 复制初始球员状态到 save_player_state
        // - 从 history.db 复制初始俱乐部状态到 save_club_state
        // - 创建首个 checkpoint（migration 类型）
        // - 重建 cache.db 缓存

        saveId
    }

    /**
     * 创建新存档（使用指定的 saveId）。
     * 用于从备份恢复或导入存档时指定 saveId。
     *
     * @param saveId 指定的存档 ID
     * @param saveName 存档名称
     * @param managerClubId 玩家执教的俱乐部 ID
     * @param scenarioConfig 场景配置 JSON（可选）
     * @param dataPackId 数据包 ID（可选）
     * @return 存档 ID
     */
    suspend fun createSave(
        saveId: String,
        saveName: String,
        managerClubId: Int,
        scenarioConfig: String? = null,
        dataPackId: String? = null
    ): String = withContext(Dispatchers.IO) {
        databaseManager.openSave(saveId)
        val saveDb = databaseManager.getSaveDatabase()

        val now = LocalDateTime.now()
        val nowDateTime = dateTimeFormatter.format(now)

        val manifest = SaveManifestEntity(
            saveId = saveId,
            gameVersion = GAME_VERSION,
            schemaVersion = DatabaseConfig.SAVE_SCHEMA_VERSION,
            dataPackId = dataPackId,
            dataPackVersion = null,
            currentDate = DatabaseConfig.DEFAULT_START_DATE,
            createdAt = nowDateTime,
            lastPlayedAt = nowDateTime,
            lastCheckpointId = null
        )
        saveDb.saveManifestDao().insert(manifest)

        val worldState = SaveWorldStateEntity(
            saveName = saveName,
            currentDate = DatabaseConfig.DEFAULT_START_DATE,
            currentSeasonId = DatabaseConfig.DEFAULT_START_SEASON_ID,
            managerClubId = managerClubId,
            mode = DatabaseConfig.DEFAULT_MODE,
            scenarioId = null,
            configJson = scenarioConfig,
            createdAt = nowDateTime,
            updatedAt = nowDateTime
        )
        saveDb.saveWorldStateDao().insert(worldState)

        saveId
    }

    // ==================== 存档加载 ====================

    /**
     * 加载存档。
     *
     * 流程：
     * 1. 通过 DatabaseManager 打开 save.db
     * 2. 读取 save_manifest 校验存档有效性
     * 3. 更新 last_played_at 时间戳
     *
     * @param saveId 存档 ID
     * @return LoadResult 加载结果
     */
    suspend fun loadSave(saveId: String): LoadResult = withContext(Dispatchers.IO) {
        // 1. 检查存档文件是否存在
        val saveFile = getSaveFile(saveId)
        if (!saveFile.exists()) {
            return@withContext LoadResult.Failed("存档文件不存在：${saveFile.absolutePath}")
        }

        // 2. 打开存档数据库
        databaseManager.openSave(saveId)
        val saveDb = databaseManager.getSaveDatabase()

        // 3. 读取 manifest 校验
        val manifest = saveDb.saveManifestDao().get()
        if (manifest == null) {
            databaseManager.closeSave()
            return@withContext LoadResult.Failed("存档元数据（save_manifest）不存在")
        }

        // 4. 校验 world_state
        val worldState = saveDb.saveWorldStateDao().get()
        if (worldState == null) {
            databaseManager.closeSave()
            return@withContext LoadResult.Failed("存档世界状态（save_world_state）不存在")
        }

        // 5. 更新 last_played_at
        val nowDateTime = dateTimeFormatter.format(LocalDateTime.now())
        saveDb.saveManifestDao().updateLastPlayed(saveId, nowDateTime)

        // TODO: T03 细化：
        // - 完整性检测（SaveIntegrityChecker）
        // - schema_version 迁移（MigrationManager）
        // - 重建 cache.db 缓存（CacheRebuilder）

        LoadResult.Success(
            saveId = saveId,
            saveName = worldState.saveName,
            currentDate = manifest.currentDate ?: DatabaseConfig.DEFAULT_START_DATE,
            managerClubId = worldState.managerClubId
        )
    }

    // ==================== 存档删除 ====================

    /**
     * 删除存档。
     * 关闭连接 + 删除 .db 文件及 WAL/SHM 临时文件。
     *
     * @param saveId 存档 ID
     * @return true 表示删除成功
     */
    suspend fun deleteSave(saveId: String): Boolean = withContext(Dispatchers.IO) {
        databaseManager.deleteSave(saveId)
    }

    // ==================== 存档列表 ====================

    /**
     * 列出所有本地存档。
     *
     * @return 存档摘要列表，按最后修改时间降序排列
     */
    suspend fun listSaves(): List<SaveSummary> = withContext(Dispatchers.IO) {
        databaseManager.listAllSaves()
    }

    // ==================== 存档信息查询 ====================

    /**
     * 获取存档的详细信息（打开存档后调用）。
     *
     * @return 存档详情，若存档未加载则返回 null
     */
    suspend fun getSaveInfo(): SaveInfo? = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null

        val manifest = saveDb.saveManifestDao().get() ?: return@withContext null
        val worldState = saveDb.saveWorldStateDao().get() ?: return@withContext null

        SaveInfo(
            saveId = manifest.saveId,
            saveName = worldState.saveName,
            gameVersion = manifest.gameVersion,
            schemaVersion = manifest.schemaVersion,
            currentDate = manifest.currentDate ?: DatabaseConfig.DEFAULT_START_DATE,
            managerClubId = worldState.managerClubId,
            currentSeasonId = worldState.currentSeasonId,
            createdAt = manifest.createdAt,
            lastPlayedAt = manifest.lastPlayedAt,
            dataPackId = manifest.dataPackId
        )
    }

    // ==================== 存档目录管理 ====================

    /**
     * 获取存档文件路径。
     * 路径格式：filesDir/saves/save_{saveId}.db
     *
     * @param saveId 存档 ID
     * @return 存档文件对象
     */
    fun getSaveFile(saveId: String): File {
        return File(getSavesDir(), "save_$saveId.db")
    }

    /**
     * 获取存档目录。
     * 路径格式：filesDir/saves/
     *
     * @return 存档目录对象
     */
    fun getSavesDir(): File {
        val context = databaseManager.getAppContext()
        val dir = File(context.filesDir, DatabaseConfig.SAVE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    companion object {
        /** 当前游戏版本号（与 build.gradle.kts versionName 保持一致） */
        private const val GAME_VERSION = "0.1.0"
    }
}

/**
 * 存档加载结果
 */
sealed class LoadResult {
    /** 加载成功 */
    data class Success(
        val saveId: String,
        val saveName: String,
        val currentDate: String,
        val managerClubId: Int
    ) : LoadResult()

    /** 加载失败 */
    data class Failed(val reason: String) : LoadResult()
}

/**
 * 存档详情信息
 *
 * @param saveId 存档 UUID
 * @param saveName 存档名称
 * @param gameVersion 创建时的游戏版本
 * @param schemaVersion 存档 schema 版本
 * @param currentDate 游戏内当前日期
 * @param managerClubId 玩家执教的俱乐部 ID
 * @param currentSeasonId 当前赛季 ID
 * @param createdAt 创建时间
 * @param lastPlayedAt 最后游玩时间
 * @param dataPackId 数据包 ID
 */
data class SaveInfo(
    val saveId: String,
    val saveName: String,
    val gameVersion: String,
    val schemaVersion: Int,
    val currentDate: String,
    val managerClubId: Int,
    val currentSeasonId: Int,
    val createdAt: String,
    val lastPlayedAt: String,
    val dataPackId: String?
)
