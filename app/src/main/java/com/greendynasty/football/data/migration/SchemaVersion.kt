package com.greendynasty.football.data.migration

/**
 * Schema 版本枚举与常量（save.db 迁移框架）
 *
 * 用于标识存档数据库的 schema 版本，支持按版本号顺序迁移。
 * V0.1 初始 schema 为 1，V0.2 增补表后仍为 1（room schema 未变更），
 * 未来若新增字段/表/索引需要迁移，则递增到 2。
 */
object SchemaVersion {

    /** V0.1 初始 schema 版本 */
    const val V1 = 1

    /** V0.2 schema 版本（增补 club_ai_profile / butterfly / checkpoint 等表，但保持 room version=1） */
    const val V2 = 2

    /** 当前 app 支持的最高 schema 版本 */
    const val CURRENT = V1

    /**
     * 校验版本号是否合法。
     * @param version 待校验的版本号
     * @return true 表示版本号在已知范围内
     */
    fun isValid(version: Int): Boolean = version in V1..V2
}

/**
 * 迁移类型分类（V0.2 §四）
 */
enum class MigrationType {
    /** 自动迁移：新增 nullable 字段/表/索引/缓存字段 */
    AUTO,

    /** 手动迁移：字段拆分/表结构重构/枚举值改变 */
    MANUAL,

    /** 不兼容：提示无法升级，提供只读打开 */
    INCOMPATIBLE
}

/**
 * 迁移步骤描述
 *
 * @param fromVersion 起始版本
 * @param toVersion 目标版本
 * @param type 迁移类型
 * @param description 描述
 */
data class MigrationStep(
    val fromVersion: Int,
    val toVersion: Int,
    val type: MigrationType,
    val description: String
)
