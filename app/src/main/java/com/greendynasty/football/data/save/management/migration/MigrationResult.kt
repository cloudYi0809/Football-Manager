package com.greendynasty.football.data.save.management.migration

/**
 * 迁移执行结果（T03）
 */
sealed class MigrationResult {

    /** 迁移成功 */
    data class Success(val toVersion: Int) : MigrationResult()

    /** 部分成功：迁移完成但产生告警 */
    data class PartialSuccess(val warnings: List<String>) : MigrationResult()

    /** 迁移失败 */
    data class Failure(val error: String) : MigrationResult()
}
