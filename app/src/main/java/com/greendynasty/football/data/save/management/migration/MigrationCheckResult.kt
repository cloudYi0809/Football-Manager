package com.greendynasty.football.data.save.management.migration

/**
 * 迁移检测结果（T03）
 *
 * 描述存档 schema 版本与当前 app 支持版本之间的关系，
 * 对应 V0.2 §四 迁移策略的三种类型（AUTO / MANUAL / INCOMPATIBLE）。
 */
sealed class MigrationCheckResult {

    /** 无需迁移：存档版本已是当前版本 */
    data class NoMigrationNeeded(val version: Int) : MigrationCheckResult()

    /** 可自动迁移：存档版本低于当前版本且存在自动迁移脚本 */
    data class AutoMigrationAvailable(val from: Int, val to: Int) : MigrationCheckResult()

    /** 需手动迁移：存在需人工处理的迁移步骤 */
    data class ManualMigrationRequired(val from: Int, val to: Int) : MigrationCheckResult()

    /** 不兼容：存档版本高于 app 支持版本或存在不兼容迁移步骤 */
    data class Incompatible(val from: Int, val to: Int) : MigrationCheckResult()
}
