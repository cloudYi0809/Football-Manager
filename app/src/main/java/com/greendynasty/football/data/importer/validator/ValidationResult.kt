package com.greendynasty.football.data.importer.validator

/**
 * 校验错误严重程度
 */
enum class Severity {
    /** 错误：行需跳过，不计入成功 */
    ERROR,

    /** 警告：记录但继续处理 */
    WARNING,

    /** 跳过：整行跳过不写入（如配置标记的忽略行） */
    SKIP
}

/**
 * 校验错误
 *
 * @property rowIndex 行号（从 1 开始）
 * @property field 出错字段（多字段联合校验时为逗号分隔）
 * @property message 错误描述
 * @property severity 严重程度
 */
data class ValidationError(
    val rowIndex: Int,
    val field: String,
    val message: String,
    val severity: Severity
)

/**
 * 校验结果
 *
 * 收集一行或多行在校验过程中产生的全部错误/警告/跳过记录。
 * 默认空列表表示校验通过。
 */
class ValidationResult(
    val errors: MutableList<ValidationError> = mutableListOf()
) {

    /**
     * 添加一条错误。
     * @param row 行号
     * @param field 字段名
     * @param msg 错误描述
     * @param severity 严重程度，默认 [Severity.ERROR]
     */
    fun addError(row: Int, field: String, msg: String, severity: Severity = Severity.ERROR) {
        errors.add(ValidationError(row, field, msg, severity))
    }

    /** 合并其他校验结果（将其错误追加到当前结果） */
    fun merge(other: ValidationResult) {
        errors.addAll(other.errors)
    }

    /** 是否存在 ERROR 级别错误 */
    fun hasErrors(): Boolean = errors.any { it.severity == Severity.ERROR }

    /** ERROR 级别错误数量 */
    fun errorCount(): Int = errors.count { it.severity == Severity.ERROR }

    /** 生成摘要：错误 / 警告 / 跳过 计数 */
    fun summary(): String {
        val errorCount = errors.count { it.severity == Severity.ERROR }
        val warningCount = errors.count { it.severity == Severity.WARNING }
        val skipCount = errors.count { it.severity == Severity.SKIP }
        return "错误 $errorCount / 警告 $warningCount / 跳过 $skipCount"
    }

    companion object {
        /** 构造一个无错误的通过结果 */
        fun ok(): ValidationResult = ValidationResult()
    }
}
