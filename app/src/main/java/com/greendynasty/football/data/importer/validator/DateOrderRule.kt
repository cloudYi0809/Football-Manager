package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 日期顺序校验：startField 必须 <= endField
 *
 * 任一日期缺失或格式非法时跳过（由其他规则处理必填与格式校验）。
 *
 * @property startField 起始日期字段
 * @property endField 结束日期字段
 */
class DateOrderRule(
    val startField: String,
    val endField: String
) : ValidationRule<CsvRow> {

    override fun validate(row: CsvRow, rowIndex: Int): ValidationResult {
        val result = ValidationResult()
        val start = row.getDate(startField)
        val end = row.getDate(endField)
        // 任一日期缺失或非法跳过（由其他规则处理）
        if (start == null || end == null) return result
        if (start.isAfter(end)) {
            result.addError(
                rowIndex,
                "$startField,$endField",
                "日期顺序错误：$startField=$start 晚于 $endField=$end"
            )
        }
        return result
    }
}
