package com.greendynasty.football.data.migration

import androidx.room.migration.Migration

/**
 * 迁移注册表
 *
 * 集中注册 save.db 的所有 Room 迁移脚本，供 [com.greendynasty.football.data.save.SaveDatabase]
 * 通过 `.addMigrations(*ALL_MIGRATIONS)` 引用。
 *
 * 当前 Room schema version = 1，无已生效的迁移脚本。
 * [MIGRATION_1_2] 为占位脚本，待未来 schema 升级时启用。
 *
 * 参考 V0.2 §四 迁移策略与 T00 实现方案 §五。
 */
object MigrationRegistry {

    /**
     * V1 -> V2 迁移脚本（占位）。
     * 当前未启用：[SchemaVersion.CURRENT] = 1，Room version 也为 1，无需迁移。
     * 若未来 Room version 升级到 2，将此对象加入 [ALL_MIGRATIONS] 即可生效。
     */
    @Suppress("unused")
    private val MIGRATION_1_2 = SaveMigrations.MIGRATION_1_2_PLACEHOLDER

    /**
     * 全部迁移脚本数组。
     *
     * 当前为空数组，因为 Room schema version=1 时不存在 V1->V2 的实际迁移需求。
     * 未来版本升级后追加：arrayOf(SaveMigrations.MIGRATION_1_2_PLACEHOLDER)
     */
    val ALL_MIGRATIONS: Array<Migration> = emptyArray()
}
