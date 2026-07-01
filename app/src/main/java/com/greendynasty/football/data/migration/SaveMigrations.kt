package com.greendynasty.football.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * save.db 迁移脚本集合（占位实现）
 *
 * 当前 Room schema version = 1，暂无已落地的迁移脚本。
 * 文件预留接口与示例（MIGRATION_1_2_PLACEHOLDER），未来需要从 V1 升级到 V2 时：
 * 1. 修改 [SchemaVersion.CURRENT] 为 2
 * 2. 在 [SaveMigrations.MIGRATION_1_2_PLACEHOLDER] 中实现具体 SQL
 * 3. 将其加入 [MigrationRegistry.ALL_MIGRATIONS]
 *
 * 参考 V0.2 §四 迁移策略与 T00 实现方案 §五。
 */
object SaveMigrations {

    /**
     * V1 -> V2 迁移占位脚本。
     *
     * 预期变更（来自 T00 实现方案 §五）：
     * - 新增 club_ai_profile 表（V0.2 §二）
     * - 新增 butterfly_event / butterfly_impact_node 表（V0.2 §六）
     * - 新增 checkpoint 表（V0.2 §六）
     * - 新增 season_archive 表（V0.2 §七）
     * - 新增 perf_log 表（V0.2 §十）
     * - save_manifest 增加 last_checkpoint_id 字段
     *
     * 当前 Room version=1 已包含上述表，故此脚本仅在 version 升级到 2 时才需要执行。
     */
    val MIGRATION_1_2_PLACEHOLDER: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 占位实现：当前 version=1 已包含全部 V0.2 表，无需实际迁移。
            // 未来若 schema 真正升级，在此追加 ALTER TABLE / CREATE TABLE 语句。
        }
    }
}
