package com.greendynasty.football.data.importer.api

import com.greendynasty.football.data.importer.validator.ValidationError

/**
 * 完整数据包导入报告
 *
 * 由 [DataImportManager.importFromAssets] / [importFromDirectory] 返回，
 * 聚合所有 Importer 的 [ImportResult] 与跨表校验错误。
 *
 * @param packId      数据包 ID（来自 manifest.json）
 * @param startTime   导入开始时间戳（ms）
 * @param endTime     导入结束时间戳（ms）
 * @param results     各 Importer 的结果列表
 * @param totalRows   全部处理行数
 * @param successRows 全部成功行数
 * @param failedRows  全部失败行数
 * @param errors      全部错误明细（聚合各 Importer 的 errors）
 */
data class ImportReport(
    val packId: String,
    val startTime: Long,
    val endTime: Long,
    val results: List<ImportResult>,
    val totalRows: Int,
    val successRows: Int,
    val failedRows: Int,
    val errors: List<ValidationError>
) {

    /** 总耗时（ms） */
    val durationMs: Long get() = endTime - startTime

    /** 是否完全成功（所有 Importer 成功 + 无错误） */
    val isSuccess: Boolean get() = failedRows == 0 && errors.isEmpty()

    /** 汇总描述（用于日志与 UI 摘要） */
    fun summary(): String = buildString {
        appendLine("=== 数据导入报告 ===")
        appendLine("数据包: $packId")
        appendLine("耗时: ${durationMs}ms")
        appendLine("总行数: $totalRows / 成功: $successRows / 失败: $failedRows")
        appendLine("结果: ${if (isSuccess) "成功" else "存在失败"}")
        if (results.isNotEmpty()) {
            appendLine("各 Importer 明细:")
            results.forEach { appendLine("  - ${it.summary()}") }
        }
        if (errors.isNotEmpty()) {
            appendLine("错误数: ${errors.size}（详见 errors 字段）")
        }
    }.trimEnd()

    companion object {
        /** 构造空报告（导入未开始时使用） */
        fun empty(packId: String = "unknown"): ImportReport = ImportReport(
            packId = packId,
            startTime = 0L,
            endTime = 0L,
            results = emptyList(),
            totalRows = 0,
            successRows = 0,
            failedRows = 0,
            errors = emptyList()
        )
    }
}
