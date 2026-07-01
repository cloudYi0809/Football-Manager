package com.greendynasty.football.data.api

/**
 * 三库配置常量（history.db / save.db / cache.db）
 *
 * 集中管理数据库文件名前缀、版本号、日志模式、备份保留策略等配置。
 * 由 [DatabaseManager] 和迁移/完整性组件引用。
 */
object DatabaseConfig {

    // ==================== 数据库文件名 ====================

    /** history.db 文件名（只读，随数据包提供） */
    const val HISTORY_DB_NAME = "history.db"

    /** cache.db 文件名（可重建） */
    const val CACHE_DB_NAME = "cache.db"

    /** save.db 文件名前缀，实际文件名形如 save_<saveId>.db */
    const val SAVE_DB_PREFIX = "save_"

    /** save.db 文件名后缀 */
    const val SAVE_DB_SUFFIX = ".db"

    /** 存档文件所在子目录（位于 filesDir 下） */
    const val SAVE_DIR = "saves"

    /** checkpoint 备份所在子目录（位于 filesDir 下） */
    const val CHECKPOINT_DIR = "checkpoints"

    // ==================== Schema 版本 ====================

    /** history.db 当前 schema 版本 */
    const val HISTORY_SCHEMA_VERSION = 1

    /** save.db 当前 schema 版本（V0.2 增补表后为 1，未来迁移到 2） */
    const val SAVE_SCHEMA_VERSION = 1

    /** cache.db 当前 schema 版本 */
    const val CACHE_SCHEMA_VERSION = 1

    // ==================== 日志模式 ====================

    /** history.db 使用 TRUNCATE 模式（只读库节省空间） */
    const val HISTORY_JOURNAL_MODE = "TRUNCATE"

    /** save.db 使用 WAL 模式（提升并发写入性能） */
    const val SAVE_JOURNAL_MODE = "WAL"

    /** cache.db 使用 TRUNCATE 模式（可重建，节省空间） */
    const val CACHE_JOURNAL_MODE = "TRUNCATE"

    // ==================== Checkpoint 保留策略 ====================

    /** light 类型 checkpoint 最大保留数（每 30 天一个） */
    const val MAX_LIGHT_CHECKPOINTS = 4

    /** season 类型 checkpoint 最大保留数（每赛季一个） */
    const val MAX_SEASON_CHECKPOINTS = 5

    /** migration 类型 checkpoint 最大保留数（迁移前备份） */
    const val MAX_MIGRATION_CHECKPOINTS = 2

    /** user 类型 checkpoint 最大保留数（用户手动备份） */
    const val MAX_USER_CHECKPOINTS = 10

    // ==================== 性能/容量阈值 ====================

    /** 单存档体积警告阈值（MB），20 年后建议不超过 80MB */
    const val SAVE_DB_SIZE_WARNING_MB = 80

    /** 迁移前最小剩余存储空间（字节，100MB） */
    const val MIGRATION_MIN_FREE_BYTES = 100L * 1024 * 1024

    /** 长程存档年数上限（用于归档/清理策略） */
    const val LONG_TERM_SAVE_YEARS = 20

    // ==================== 默认开档参数 ====================

    /** 默认开档日期（2002/03 赛季前） */
    const val DEFAULT_START_DATE = "2002-07-01"

    /** 默认开档赛季 ID */
    const val DEFAULT_START_SEASON_ID = 1

    /** 默认游戏模式 */
    const val DEFAULT_MODE = "career"
}
