package com.greendynasty.football.data.importer.config

import kotlinx.serialization.Serializable

/**
 * 导入配置（import_config.json 映射）
 *
 * 控制数据导入过程中的批量大小、错误上限、checksum 校验开关、
 * 严格模式与 Importer 执行顺序等可调参数。
 *
 * 字段命名沿用 JSON snake_case，便于直接与 import_config.json 字段一一对应。
 */
@Serializable
data class ImportConfig(
    /** 单批写入数据库的行数 */
    val batch_size: Int = 500,
    /** 单阶段允许的最大错误数，超过即中止该阶段 */
    val max_errors: Int = 100,
    /** 是否启用 SHA-256 checksum 校验 */
    val enable_checksum: Boolean = true,
    /** 严格模式：开启后所有 WARNING 也按 ERROR 处理 */
    val strict_mode: Boolean = false,
    /** Importer 执行顺序（依赖顺序：基础表 → 关系表 → 派生表） */
    val import_order: List<String> = emptyList()
)
