package com.greendynasty.football.data.importer.validator

/**
 * 校验规则接口
 *
 * 所有具体校验规则（必填/范围/外键/唯一/枚举/日期顺序）均实现此接口，
 * 由 [DataValidator] 统一调度。
 *
 * @param T 行数据类型（通常为 [com.greendynasty.football.data.importer.parser.CsvRow]）
 */
interface ValidationRule<T> {

    /**
     * 校验单行数据。
     *
     * @param row 行数据
     * @param rowIndex 行号（从 1 开始）
     * @return 校验结果（含错误列表；无错误时返回空结果）
     */
    fun validate(row: T, rowIndex: Int): ValidationResult
}
