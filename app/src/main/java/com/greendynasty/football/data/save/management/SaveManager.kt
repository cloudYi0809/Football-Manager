package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseConfig
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.management.model.SaveCreateResult
import com.greendynasty.football.data.save.management.model.SaveInfo
import com.greendynasty.football.data.save.management.model.SaveLoadResult
import com.greendynasty.football.data.save.management.model.SaveMeta
import com.greendynasty.football.data.save.management.model.SaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 存档管理器（统一入口，单例）
 *
 * 协调所有子管理器（[SaveCreator] / [SaveLoader] / [SaveWriter] / [SaveDeleter] / [CheckpointManager]），
 * 对外提供存档全生命周期管理：创建、加载、保存、切换、删除、列表、查询。
 *
 * 职责：
 * - 维护当前加载存档 ID（[currentSaveId]）与状态（[currentState]）
 * - 并发保护：globalLock 保护状态变更与 DB 连接；per-save Mutex 防止同时写入同一存档
 * - 状态变更通知：通过 [currentState]（StateFlow）对外暴露，UI 可观察
 * - 存档数量上限校验（[MAX_SAVE_COUNT]）
 *
 * 使用方式：
 * ```
 * val saveManager = SaveManager.getInstance(context)
 * saveManager.createSave(CreateSaveRequest(...))
 * saveManager.saveSave(SaveTrigger.MANUAL)
 * saveManager.listSaves()
 * ```
 *
 * 注意：同时只能加载一个存档（DatabaseManager 单例 + currentSaveId 单值）。
 */
class SaveManager private constructor(private val context: Context) {

    private val databaseManager = DatabaseManager.getInstance(context)
    private val ioDispatcher = Dispatchers.IO

    /** 当前加载的存档 ID（null 表示未加载） */
    @Volatile
    private var currentSaveId: String? = null

    /** 当前存档状态（StateFlow 对外暴露，UI 可观察） */
    private val _state = MutableStateFlow(SaveState.UNLOADED)
    val currentState: StateFlow<SaveState> = _state.asStateFlow()

    /** 全局锁：保护 currentSaveId / 状态变更与 DB 连接（单连接约束） */
    private val globalLock = Mutex()

    /** per-save 锁：防止同时写入同一存档 */
    private val saveLocks = mutableMapOf<String, Mutex>()
    private val saveLocksGuard = Any()

    /** 子管理器（懒加载） */
    private val saveCreator by lazy { SaveCreator(context, databaseManager) }
    private val saveLoader by lazy { SaveLoader(context, databaseManager) }
    private val saveWriter by lazy { SaveWriter(context, databaseManager) }
    private val saveDeleter by lazy { SaveDeleter(context, databaseManager) }
    private val checkpointManager by lazy { CheckpointManager(context, databaseManager) }

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== 公共属性 ====================

    /** 当前加载的存档 ID（只读） */
    val currentSaveIdValue: String? get() = currentSaveId

    // ==================== 创建存档 ====================

    /**
     * 创建新存档
     *
     * 流程：校验存档数量上限 -> 委托 [SaveCreator] 创建 -> 设置当前存档
     *
     * @param request 创建存档请求
     * @return 创建结果（Success 携带 saveId / Failure 携带错误信息）
     */
    suspend fun createSave(request: CreateSaveRequest): SaveCreateResult = withContext(ioDispatcher) {
        globalLock.withLock {
            // 1. 校验存档数量上限
            val saveCount = countSaveFiles()
            if (saveCount >= MAX_SAVE_COUNT) {
                return@withLock SaveCreateResult.Failure(
                    "存档数量已达上限 $MAX_SAVE_COUNT，请先删除旧存档"
                )
            }

            // 2. 状态转换：UNLOADED -> LOADING
            transitionState(SaveState.LOADING)

            // 3. 委托创建
            val result = saveCreator.create(request)
            when (result) {
                is SaveCreateResult.Success -> {
                    currentSaveId = result.saveId
                    transitionState(SaveState.LOADED)
                    notifyStateChange()
                }
                is SaveCreateResult.Failure -> {
                    currentSaveId = null
                    transitionState(SaveState.UNLOADED)
                }
            }
            result
        }
    }

    // ==================== 加载存档 ====================

    /**
     * 加载存档
     *
     * 流程：若当前已加载其他存档先关闭 -> 状态 LOADING -> 委托 [SaveLoader] -> 设置当前存档
     *
     * @param saveId 存档 ID
     * @return 加载结果（Success/NeedsMigration/Corrupted/Failure）
     */
    suspend fun loadSave(saveId: String): SaveLoadResult = withContext(ioDispatcher) {
        globalLock.withLock {
            // 1. 若当前已加载其他存档，先关闭
            if (currentSaveId != null && currentSaveId != saveId) {
                closeCurrentSaveInternal()
            }

            // 2. 状态转换：-> LOADING
            transitionState(SaveState.LOADING)

            // 3. 委托加载
            val result = saveLoader.load(saveId)
            when (result) {
                is SaveLoadResult.Success -> {
                    currentSaveId = saveId
                    transitionState(SaveState.LOADED)
                    notifyStateChange()
                }
                is SaveLoadResult.NeedsMigration,
                is SaveLoadResult.Corrupted,
                is SaveLoadResult.Failure -> {
                    currentSaveId = null
                    transitionState(if (result is SaveLoadResult.Corrupted) SaveState.ERROR else SaveState.UNLOADED)
                }
            }
            result
        }
    }

    // ==================== 保存存档 ====================

    /**
     * 保存当前存档（异步，不阻塞 UI）
     *
     * 并发保护：per-save Mutex 防止同时写入同一存档；globalLock 保护 DB 连接。
     *
     * @param trigger 保存触发类型
     * @return 保存结果（Success/Failure/RolledBack）
     */
    suspend fun saveSave(trigger: SaveTrigger): SaveResult = withContext(ioDispatcher) {
        val saveId = currentSaveId ?: return@withContext SaveResult.Failure("无已加载存档")
        val lock = getSaveLock(saveId)

        globalLock.withLock {
            lock.withLock {
                // 二次校验：确保保存期间存档未切换
                if (currentSaveId != saveId) {
                    return@withContext SaveResult.Failure("存档已切换，保存取消")
                }

                transitionState(SaveState.SAVING)
                val result = saveWriter.save(saveId, trigger)
                when (result) {
                    is SaveResult.Success,
                    is SaveResult.Failure -> {
                        transitionState(SaveState.LOADED)
                    }
                    is SaveResult.RolledBack -> {
                        // 回滚后 DB 已关闭，回到未加载状态
                        currentSaveId = null
                        transitionState(SaveState.UNLOADED)
                    }
                }
                notifyStateChange()
                result
            }
        }
    }

    // ==================== 切换存档 ====================

    /**
     * 切换存档
     *
     * 流程：保存当前存档 -> 关闭当前 -> 加载新存档
     *
     * @param saveId 目标存档 ID
     * @return 切换结果（Success 携带新存档信息 / Failure 携带错误信息）
     */
    suspend fun switchSave(saveId: String): SaveSwitchResult = withContext(ioDispatcher) {
        globalLock.withLock {
            transitionState(SaveState.SWITCHING)

            try {
                // 1. 保存当前存档（如有）
                val previousSaveId = currentSaveId
                if (previousSaveId != null && previousSaveId != saveId) {
                    try {
                        saveWriter.save(previousSaveId, SaveTrigger.BEFORE_QUIT)
                    } catch (e: Exception) {
                        Log.w("SaveManager", "切换前保存旧存档失败（继续切换）：${e.message}")
                    }
                }

                // 2. 关闭当前存档
                if (previousSaveId != null) {
                    closeCurrentSaveInternal()
                }

                // 3. 加载新存档
                val result = saveLoader.load(saveId)
                when (result) {
                    is SaveLoadResult.Success -> {
                        currentSaveId = saveId
                        transitionState(SaveState.LOADED)
                        notifyStateChange()
                        SaveSwitchResult.Success(result.saveInfo)
                    }
                    else -> {
                        currentSaveId = null
                        transitionState(SaveState.UNLOADED)
                        val error = when (result) {
                            is SaveLoadResult.NeedsMigration -> "目标存档需要版本迁移（${result.fromVersion} -> ${result.toVersion}）"
                            is SaveLoadResult.Corrupted -> "目标存档损坏：${result.error}"
                            is SaveLoadResult.Failure -> "加载目标存档失败：${result.error}"
                            else -> "加载目标存档失败"
                        }
                        SaveSwitchResult.Failure(error)
                    }
                }
            } catch (e: Exception) {
                currentSaveId = null
                transitionState(SaveState.ERROR)
                SaveSwitchResult.Failure("切换存档失败：${e.message}", e)
            }
        }
    }

    // ==================== 删除存档 ====================

    /**
     * 删除存档
     *
     * 流程：若删除的是当前存档先关闭 -> 委托 [SaveDeleter] 删除（二次确认）
     *
     * @param saveId 存档 ID
     * @return true 表示删除成功
     */
    suspend fun deleteSave(saveId: String): Boolean = withContext(ioDispatcher) {
        globalLock.withLock {
            // 若删除的是当前存档，先关闭
            if (currentSaveId == saveId) {
                closeCurrentSaveInternal()
            }
            saveDeleter.delete(saveId, confirmed = true)
        }
    }

    // ==================== 存档列表 ====================

    /**
     * 列出所有本地存档（按最后保存时间降序）
     *
     * 优先读取 save_{saveId}.meta.json（快速，不打开数据库），
     * JSON 缺失或损坏时从 save.db 恢复并回写 JSON。
     *
     * @return 存档元信息列表
     */
    suspend fun listSaves(): List<SaveMeta> = withContext(ioDispatcher) {
        val saveDir = SaveDatabase.getSaveDir(context)
        val dbFiles = saveDir.listFiles { f -> f.name.startsWith("save_") && f.name.endsWith(".db") }
            ?: emptyArray()

        val currentId = currentSaveId
        dbFiles.mapNotNull { dbFile ->
            val saveId = dbFile.name.removePrefix("save_").removeSuffix(".db")
            try {
                readMetaJson(saveId)?.copy(isLoaded = (saveId == currentId))
            } catch (e: Exception) {
                Log.w("SaveManager", "读取存档元信息失败：saveId=$saveId, ${e.message}")
                null
            }
        }.sortedByDescending { it.lastSavedAt }
    }

    // ==================== 当前存档信息 ====================

    /**
     * 获取当前加载存档的运行时信息
     *
     * @return 当前存档信息，若未加载则返回 null
     */
    suspend fun getCurrentSaveInfo(): SaveInfo? = withContext(ioDispatcher) {
        val saveId = currentSaveId ?: return@withContext null
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null

        val manifest = saveDb.saveManifestDao().get() ?: return@withContext null
        val worldState = saveDb.saveWorldStateDao().get() ?: return@withContext null

        SaveInfo(
            saveId = saveId,
            saveName = worldState.saveName,
            gameDate = manifest.currentDate ?: DatabaseConfig.DEFAULT_START_DATE,
            currentSeason = worldState.currentSeasonId,
            managerClubId = worldState.managerClubId,
            currentState = _state.value
        )
    }

    // ==================== 关闭存档 ====================

    /**
     * 关闭当前存档（应用退出或手动关闭时调用）
     */
    suspend fun closeCurrentSave() = withContext(ioDispatcher) {
        globalLock.withLock {
            closeCurrentSaveInternal()
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 关闭当前存档（内部方法，调用方需持有 globalLock）
     */
    private fun closeCurrentSaveInternal() {
        try {
            databaseManager.getSaveDatabaseOrNull()?.let { databaseManager.closeSave() }
        } catch (e: Exception) {
            Log.w("SaveManager", "关闭存档连接异常：${e.message}")
        }
        currentSaveId = null
        transitionState(SaveState.UNLOADED)
        notifyStateChange()
    }

    /**
     * 状态转换（带合法性校验）
     * 非法转换记录警告但不抛异常（保证异常路径不因状态校验二次失败）。
     */
    private fun transitionState(newState: SaveState) {
        val oldState = _state.value
        if (!SaveState.canTransition(oldState, newState)) {
            Log.w("SaveManager", "状态转换被限制：$oldState -> $newState（强制设置）")
        }
        _state.value = newState
    }

    /** 状态变更通知（当前通过 StateFlow 自动通知，预留扩展点） */
    private fun notifyStateChange() {
        // StateFlow 已自动通知订阅者，此处预留日志/事件总线扩展
        Log.d("SaveManager", "状态变更：state=${_state.value}, currentSaveId=$currentSaveId")
    }

    /** 获取（或创建）指定存档的 Mutex */
    private fun getSaveLock(saveId: String): Mutex {
        return synchronized(saveLocksGuard) {
            saveLocks.getOrPut(saveId) { Mutex() }
        }
    }

    /** 统计本地存档文件数量 */
    private fun countSaveFiles(): Int {
        val saveDir = SaveDatabase.getSaveDir(context)
        return saveDir.listFiles { f -> f.name.startsWith("save_") && f.name.endsWith(".db") }
            ?.size ?: 0
    }

    /** 读取 meta.json */
    private suspend fun readMetaJson(saveId: String): SaveMeta? {
        val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
        return if (metaFile.exists()) {
            try {
                json.decodeFromString(SaveMeta.serializer(), metaFile.readText())
            } catch (e: Exception) {
                recoverMetaFromDb(saveId)
            }
        } else {
            recoverMetaFromDb(saveId)
        }
    }

    /** 从 DB 恢复元信息并回写 meta.json */
    private suspend fun recoverMetaFromDb(saveId: String): SaveMeta? {
        return try {
            val saveDb = SaveDatabase.create(context, saveId)
            val manifest = saveDb.saveManifestDao().get() ?: return null.also { saveDb.close() }
            val worldState = saveDb.saveWorldStateDao().get() ?: return null.also { saveDb.close() }
            val dbFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.db")
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
                fileSizeBytes = dbFile.length(),
                isLoaded = false
            )
            saveDb.close()
            // 回写 JSON
            val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
            metaFile.writeText(json.encodeToString(SaveMeta.serializer(), meta))
            meta
        } catch (e: Exception) {
            Log.w("SaveManager", "从 DB 恢复 meta 失败：saveId=$saveId, ${e.message}")
            null
        }
    }

    companion object {
        /** 单例实例 */
        @Volatile
        private var instance: SaveManager? = null

        /**
         * 获取 SaveManager 单例实例。
         *
         * @param context 任意 Context，内部取 applicationContext
         * @return SaveManager 单例
         */
        fun getInstance(context: Context): SaveManager {
            return instance ?: synchronized(this) {
                instance ?: SaveManager(context.applicationContext).also { instance = it }
            }
        }

        /** 存档数量上限 */
        private const val MAX_SAVE_COUNT = 20
    }
}

/**
 * 切换存档操作结果
 */
sealed class SaveSwitchResult {

    /**
     * 切换成功
     *
     * @param saveInfo 新存档运行时信息
     */
    data class Success(val saveInfo: SaveInfo) : SaveSwitchResult()

    /**
     * 切换失败
     *
     * @param error 错误描述
     * @param cause 原始异常（可选，用于日志）
     */
    data class Failure(val error: String, val cause: Throwable? = null) : SaveSwitchResult()
}
