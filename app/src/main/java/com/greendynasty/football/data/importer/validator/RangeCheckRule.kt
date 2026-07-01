package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 范围校验：数值字段值必须在 [min, max] 范围内
 *
 * 空值不触发范围校验（由 [RequiredFieldRule] 处理必填场景）。
 *
 * @property field 字段名
 * @property min 最小值（含）
 * @property max 最大值（含）
 */
class RangeCheckRule(
    val field: String,
    val min: Int,
    val max: Int
) : ValidationRule<CsvRow> {

    override fun validate(row: CsvRow, rowIndex: Int): ValidationResult {
        val result = ValidationResult()
        val raw = row.getString(field)
        // 空值跳过范围校验（由 RequiredFieldRule 处理必填）
        if (raw.isNullOrBlank()) return result
        val value = raw.trim().toIntOrNull()
        if (value == null) {
            result.addError(rowIndex, field, "字段非整数：$field=$raw")
            return result
        }
        if (value < min) {
            result.addError(rowIndex, field, "值 $value 小于最小值 $min：$field")
        }
        if (value > max) {
            result.addError(rowIndex, field, "值 $value 大于最大值 $max：$field")
        }
        return result
    }
}
