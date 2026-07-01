package com.greendynasty.football.data.save.management.migration

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 存档迁移脚本接口（T03）
 *
 * 单个版本到版本的迁移逻辑，直接通过 [SupportSQLiteDatabase] 执行 DDL/DML。
 * 返回迁移过程中产生的告警信息列表（空列表表示无告警）。
 *
 * 实现方应在 [migrate] 内完成单步迁移，由 [SaveMigrator] 负责事务包裹与回滚。
 */
interface SaveMigration {

    /**
     * 执行迁移。
     *
     * @param database 存档数据库（SupportSQLiteDatabase）
     * @return 告警信息列表，空表示无告警
     */
    suspend fun migrate(database: SupportSQLiteDatabase): List<String>
}
