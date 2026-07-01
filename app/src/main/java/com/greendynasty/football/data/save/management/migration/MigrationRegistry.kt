package com.greendynasty.football.data.save.management.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.ConcurrentHashMap

/**
 * 迁移脚本注册表（T03）
 *
 * 集中注册 save.db 的 schema 迁移脚本（[SaveMigration]）。
 * 支持按 (from, to) 版本对查找迁移脚本。
 *
 * 与 T00 的 [com.greendynasty.football.data.migration.MigrationRegistry] 区别：
 * - T00 注册的是 Room [androidx.room.migration.Migration]，供 Room `.addMigrations()` 使用
 * - T03 注册的是 [SaveMigration]（返回告警列表），供 [SaveMigrator] 逐版本执行使用
 *
 * V1 预留：注册 v1 → v1 空迁移，框架就绪；未来版本升级时调用 [registerMigration] 追加真实脚本。
 */
object MigrationRegistry {

    private val migrations = ConcurrentHashMap<Pair<Int, Int>, SaveMigration>()

    init {
        // V1 → V1 空迁移（框架预留，当前 SchemaVersion.CURRENT = 1）
        registerMigration(1, 1, NoOpMigration)
    }

    /**
     * 注册迁移脚本。
     *
     * @param from 起始版本
     * @param to 目标版本
     * @param migration 迁移脚本
     */
    fun registerMigration(from: Int, to: Int, migration: SaveMigration) {
        migrations[Pair(from, to)] = migration
    }

    /**
     * 获取迁移脚本。
     *
     * @param from 起始版本
     * @param to 目标版本
     * @return 迁移脚本，未注册时返回 null
     */
    fun getMigration(from: Int, to: Int): SaveMigration? {
        return migrations[Pair(from, to)]
    }

    /** 已注册的迁移脚本数量 */
    val size: Int get() = migrations.size

    /** V1 → V1 空迁移（无操作，返回空告警列表） */
    private object NoOpMigration : SaveMigration {
        override suspend fun migrate(database: SupportSQLiteDatabase): List<String> = emptyList()
    }
}
