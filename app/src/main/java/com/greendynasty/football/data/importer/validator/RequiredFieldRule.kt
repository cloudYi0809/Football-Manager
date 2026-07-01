package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 必填校验：字段非空且非空白
 *
 * @property fields 必填字段名列表
 */
class RequiredFieldRule(val fields: List<String>) : ValidationRule<CsvRow> {

    /** 便捷构造：可变参数 */
    constructor(vararg fields: String) : this(fields.toList())

    override fun validate(row: CsvRow, rowIndex: Int): ValidationResult {
        val result = ValidationResult()
        for (field in fields) {
            val value = row.getString(field)
            if (value.isNullOrBlank()) {
                result.addError(rowIndex, field, "必填字段缺失或为空：$field")
            }
        }
        return result
    }
}
