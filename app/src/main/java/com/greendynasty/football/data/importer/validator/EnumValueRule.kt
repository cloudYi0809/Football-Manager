package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 枚举值校验：字段值必须在允许集合内
 *
 * 空值不触发枚举校验（由 [RequiredFieldRule] 处理必填场景）。
 *
 * @property field 字段名
 * @property allowedValues 允许的枚举值集合
 */
class EnumValueRule(
    val field: String,
    val allowedValues: Set<String>
) : ValidationRule<CsvRow> {

    /** 便捷构造：可变参数 */
    constructor(field: String, vararg allowed: String) : this(field, allowed.toSet())

    override fun validate(row: CsvRow, rowIndex: Int): ValidationResult {
        val result = ValidationResult()
        val raw = row.getString(field)
        // 空值跳过枚举校验（由 RequiredFieldRule 处理必填）
        if (raw.isNullOrBlank()) return result
        val value = raw.trim()
        if (value !in allowedValues) {
            result.addError(rowIndex, field, "枚举值非法：$field=$value，允许值：$allowedValues")
        }
        return result
    }
}
