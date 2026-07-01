package com.greendynasty.football.data.save.management.integrity

import androidx.sqlite.db.SupportSQLiteDatabase
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.management.config.SaveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 存档完整性检测器（T03）
 *
 * V0.2 §八 完整性检测 7 项：
 * 1. 文件存在性：save_<saveId>.db 文件是否存在
 * 2. 文件可读性：能否打开数据库
 * 3. 表完整性：PRAGMA integrity_check
 * 4. 元信息完整性：save_manifest 表存在且非空
 * 5. 世界状态完整性：save_world_state 表存在且非空
 * 6. 引用完整性：球员状态引用的 club_id 在 save_club_state 中存在
 * 7. 大小检查：存档大小 ≤ [SaveConfig.maxSaveSizeMb]（默认 80MB，硬限制）
 *
 * PRAGMA 命令直接通过 [SupportSQLiteDatabase] 执行（不经过 Room DAO 抽象）。
 * 所有 IO 操作在 [Dispatchers.IO] 上执行。
 *
 * @param databaseManager 三库管理入口
 * @param config 存档配置
 */
class SaveIntegrityChecker(
    private val databaseManager: DatabaseManager,
    private val config: SaveConfig = SaveConfig()
) {

    /**
     * 执行完整性检测。
     *
     * @param saveId 存档 ID
     * @return 完整性报告
     */
    suspend fun check(saveId: String): IntegrityReport = withContext(Dispatchers.IO) {
        val appContext = databaseManager.getAppContext()
        val saveFile = File(SaveDatabase.getSaveDir(appContext), "save_$saveId.db")
        val checks = mutableListOf<IntegrityCheck>()

        // 1. 文件存在性
        val fileExists = saveFile.exists()
        checks.add(
            IntegrityCheck(
                checkName = CHECK_FILE_EXISTS,
                passed = fileExists,
                message = if (fileExists) "存档文件存在" else "存档文件不存在：${saveFile.absolutePath}"
            )
        )
        if (!fileExists) {
            return@withContext IntegrityReport(saveId, checks, IntegrityStatus.UNREADABLE)
        }

        // 2. 文件可读性（尝试打开数据库并执行一次查询）
        val supportDb: SupportSQLiteDatabase = try {
            databaseManager.getSaveDatabase().openHelper.readableDatabase
        } catch (e: Exception) {
            checks.add(
                IntegrityCheck(
                    checkName = CHECK_DB_READABLE,
                    passed = false,
                    message = "数据库无法打开：${e.message}"
                )
            )
            return@withContext IntegrityReport(saveId, checks, IntegrityStatus.UNREADABLE)
        }
        val readable = try {
            supportDb.query("SELECT 1").use { cursor -> cursor.moveToFirst() }
            true
        } catch (e: Exception) {
            false
        }
        checks.add(
            IntegrityCheck(
                checkName = CHECK_DB_READABLE,
                passed = readable,
                message = if (readable) "数据库可读" else "数据库查询失败"
            )
        )
        if (!readable) {
            return@withContext IntegrityReport(saveId, checks, IntegrityStatus.UNREADABLE)
        }

        // 3. 表完整性：PRAGMA integrity_check
        val integrityOk = try {
            supportDb.query("PRAGMA integrity_check").use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getString(0)?.equals("ok", ignoreCase = true) == true
            }
        } catch (e: Exception) {
            false
        }
        checks.add(
            IntegrityCheck(
                checkName = CHECK_INTEGRITY,
                passed = integrityOk,
                message = if (integrityOk) "integrity_check = ok" else "integrity_check 报告异常"
            )
        )

        // 4. 元信息完整性：save_manifest 表存在且非空
        val manifestOk = tableExists(supportDb, "save_manifest") &&
            rowCount(supportDb, "save_manifest") > 0
        checks.add(
            IntegrityCheck(
                checkName = CHECK_MANIFEST,
                passed = manifestOk,
                message = if (manifestOk) "save_manifest 存在且非空" else "save_manifest 缺失或为空"
            )
        )

        // 5. 世界状态完整性：save_world_state 表存在且非空
        val worldStateOk = tableExists(supportDb, "save_world_state") &&
            rowCount(supportDb, "save_world_state") > 0
        checks.add(
            IntegrityCheck(
                checkName = CHECK_WORLD_STATE,
                passed = worldStateOk,
                message = if (worldStateOk) "save_world_state 存在且非空" else "save_world_state 缺失或为空"
            )
        )

        // 6. 引用完整性：球员状态引用的 club_id 在 save_club_state 中存在
        val orphanCount = try {
            if (tableExists(supportDb, "save_player_state") &&
                tableExists(supportDb, "save_club_state")
            ) {
                supportDb.query(
                    """
                    SELECT COUNT(*) FROM save_player_state sps
                    WHERE sps.current_club_id IS NOT NULL
                      AND sps.current_club_id NOT IN (SELECT club_id FROM save_club_state)
                    """.trimIndent()
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
            } else {
                -1 // 表缺失，单独标记
            }
        } catch (e: Exception) {
            -1
        }
        val refOk = orphanCount == 0
        checks.add(
            IntegrityCheck(
                checkName = CHECK_REFERENTIAL,
                passed = refOk,
                message = when {
                    orphanCount < 0 -> "引用完整性检测无法执行（表缺失）"
                    orphanCount == 0 -> "球员状态外键引用完整"
                    else -> "球员状态有 $orphanCount 条孤儿记录（club_id 不存在）"
                }
            )
        )

        // 7. 大小检查：存档大小 ≤ 80MB（硬限制）
        val sizeBytes = saveFile.length()
        val maxBytes = config.maxSaveSizeMb.toLong() * 1024L * 1024L
        val sizeOk = sizeBytes <= maxBytes
        checks.add(
            IntegrityCheck(
                checkName = CHECK_SIZE,
                passed = sizeOk,
                message = if (sizeOk) {
                    "存档大小 %.2f MB（≤ ${config.maxSaveSizeMb}MB）".format(
                        sizeBytes / 1024.0 / 1024.0
                    )
                } else {
                    "存档大小 %.2f MB 超过上限 ${config.maxSaveSizeMb}MB".format(
                        sizeBytes / 1024.0 / 1024.0
                    )
                }
            )
        )

        IntegrityReport(saveId, checks, computeOverallStatus(checks))
    }

    /** 检测表是否存在（表名为内部常量，直接内联） */
    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        return db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'"
        ).use { cursor -> cursor.moveToFirst() }
    }

    /** 查询表行数 */
    private fun rowCount(db: SupportSQLiteDatabase, tableName: String): Int {
        return db.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** 根据各项结果计算总体状态 */
    private fun computeOverallStatus(checks: List<IntegrityCheck>): IntegrityStatus {
        if (checks.all { it.passed }) return IntegrityStatus.OK

        // 文件不可读 / 数据库无法打开 → UNREADABLE
        if (checks.any {
                !it.passed && (it.checkName == CHECK_FILE_EXISTS || it.checkName == CHECK_DB_READABLE)
            }) {
            return IntegrityStatus.UNREADABLE
        }
        // 表完整性 / 元信息 / 世界状态失败 → CORRUPTED
        if (checks.any {
                !it.passed && (it.checkName == CHECK_INTEGRITY ||
                    it.checkName == CHECK_MANIFEST ||
                    it.checkName == CHECK_WORLD_STATE)
            }) {
            return IntegrityStatus.CORRUPTED
        }
        // 其余失败（引用完整性告警、大小超限）→ WARNING
        return IntegrityStatus.WARNING
    }

    companion object {
        private const val CHECK_FILE_EXISTS = "文件存在性"
        private const val CHECK_DB_READABLE = "文件可读性"
        private const val CHECK_INTEGRITY = "表完整性"
        private const val CHECK_MANIFEST = "元信息完整性"
        private const val CHECK_WORLD_STATE = "世界状态完整性"
        private const val CHECK_REFERENTIAL = "引用完整性"
        private const val CHECK_SIZE = "大小检查"
    }
}
