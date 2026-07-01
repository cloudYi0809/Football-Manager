package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 引用完整性校验：字段值必须存在于外键集合中
 *
 * 支持懒加载外键集合：通过 provider 延迟到首次校验时加载，
 * 避免在基础表导入前过早查询数据库。
 *
 * 空值不触发外键校验（由 [RequiredFieldRule] 处理必填场景）。
 *
 * @property field 外键字段名
 */
class ForeignKeyRule(
    val field: String,
    private val idsProvider: () -> Set<Int>
) : ValidationRule<CsvRow> {

    /** 便捷构造：直接传入预加载的外键集合 */
    constructor(field: String, validIds: Set<Int>) : this(field, { validIds })

    /** 懒加载的外键集合（首次访问时通过 provider 加载） */
    val validIds: Set<Int> by lazy { idsProvider() }

    override fun validate(row: CsvRow, rowIndex: Int): ValidationResult {
        val result = ValidationResult()
        val raw = row.getString(field)
        // 允许空外键（由 RequiredFieldRule 处理必填场景）
        if (raw.isNullOrBlank()) return result
        val value = raw.trim().toIntOrNull()
        if (value == null) {
            result.addError(rowIndex, field, "外键非整数：$field=$raw")
            return result
        }
        if (!validIds.contains(value)) {
            result.addError(rowIndex, field, "外键不存在：$field=$value")
        }
        return result
    }
}
