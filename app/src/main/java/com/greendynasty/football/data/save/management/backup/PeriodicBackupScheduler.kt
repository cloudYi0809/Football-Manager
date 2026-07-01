package com.greendynasty.football.data.save.management.backup

import android.content.Context
import com.greendynasty.football.data.save.management.config.SaveConfig

/**
 * 定期备份调度器（T03）
 *
 * 规则（V0.2 §九 + T03）：
 * - 每日推进后自动备份：距上次备份超过 1 天（24 小时）则触发
 * - 保留最近 [SaveConfig.backupKeepCount] 个备份（由 [SaveBackupManager.cleanOldBackups] 执行）
 * - 超过 [SaveConfig.backupIntervalDays] 天的备份自动清理（由 [SaveBackupManager.cleanOldBackups] 执行）
 *
 * 调度判断为纯文件系统操作，非 suspend，可在主线程调用。
 *
 * @param context 上下文
 * @param config 存档配置
 */
class PeriodicBackupScheduler(
    private val context: Context,
    private val config: SaveConfig = SaveConfig()
) {

    /**
     * 判断指定存档是否需要备份。
     *
     * 规则：无备份存在，或最近一次备份距今超过 1 天（24 小时）则返回 true。
     * 配合 [SaveBackupManager.cleanOldBackups] 实现每日备份 + 旧备份自动清理。
     *
     * @param saveId 存档 ID
     * @return true 表示应执行备份
     */
    fun shouldBackup(saveId: String): Boolean {
        val saveBackupDir = SaveBackupManager.getBackupDir(context, saveId)
        val metaFiles = saveBackupDir.listFiles { f -> f.name.endsWith(".meta.json") }
            ?: return true

        if (metaFiles.isEmpty()) return true

        // 取最近一次备份的文件修改时间
        val lastBackupTime = metaFiles.maxOf { it.lastModified() }
        val now = System.currentTimeMillis()
        return (now - lastBackupTime) >= ONE_DAY_MS
    }

    companion object {
        /** 一天的毫秒数 */
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}
