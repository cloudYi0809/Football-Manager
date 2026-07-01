package com.greendynasty.football.data.save.management.backup

/**
 * 备份操作结果（T03）
 */
sealed class BackupResult {

    /** 备份成功 */
    data class Success(val backupId: String) : BackupResult()

    /** 备份失败 */
    data class Failure(val error: String) : BackupResult()

    /** 跳过备份（如距上次备份时间过短） */
    data class Skipped(val reason: String) : BackupResult()
}
