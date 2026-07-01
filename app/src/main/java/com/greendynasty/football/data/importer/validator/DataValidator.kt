package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 校验器入口
 *
 * 将一组 [ValidationRule] 应用到单行或流式行序列，汇总校验结果。
 *
 * 协程友好：所有方法为同步实现，应在 [kotlinx.coroutines.Dispatchers.IO] 上调用
 * （因 [validateAll] 接收的 Sequence 可能来自阻塞式 IO 的 [CsvParser]）。
 */
class DataValidator {

    /**
     * 校验单行。
     *
     * 依次应用全部规则，合并各规则产生的错误。
     * 若任一规则产生 [Severity.SKIP]，提前终止该行后续规则校验。
     *
     * @param row CSV 行数据
     * @param rules 校验规则列表
     * @return 校验结果（含该行全部错误）
     */
    fun validateRow(row: CsvRow, rules: List<ValidationRule<CsvRow>>): ValidationResult {
        val result = ValidationResult()
        for (rule in rules) {
            val sub = rule.validate(row, row.rowIndex)
            result.merge(sub)
            // 规则判定为 SKIP 时提前终止该行校验
            if (sub.errors.any { it.severity == Severity.SKIP }) break
        }
        return result
    }

    /**
     * 批量校验（流式）。
     *
     * 遍历行序列逐行校验并汇总，不会一次性载入全部行到内存，
     * 适用于 10000+ 行的大数据量校验。
     *
     * @param rows 行序列（通常来自 CsvParser）
     * @param rules 校验规则列表
     * @return 汇总校验结果
     */
    fun validateAll(
        rows: Sequence<CsvRow>,
        rules: List<ValidationRule<CsvRow>>
    ): ValidationResult {
        val result = ValidationResult()
        for (row in rows) {
            val sub = validateRow(row, rules)
            result.merge(sub)
        }
        return result
    }
}
