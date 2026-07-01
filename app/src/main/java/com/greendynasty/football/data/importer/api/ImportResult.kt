package com.greendynasty.football.data.importer.api

import com.greendynasty.football.data.importer.validator.ValidationError

/**
 * 单个 Importer 的导入结果
 *
 * 由 [com.greendynasty.football.data.importer.importer.BaseImporter.import] 返回，
 * 被 [DataImportManager] 收集后聚合到 [ImportReport]。
 *
 * @param importerName Importer 名称（如 "PlayerImporter"）
 * @param totalRows    处理的总行数
 * @param successRows  成功写入行数
 * @param failedRows   失败行数（校验失败 / 写入失败）
 * @param errors       错误明细（最多 [com.greendynasty.football.data.importer.config.ImportConfig.max_errors] 条）
 * @param durationMs   耗时（毫秒）
 */
data class ImportResult(
    val importerName: String,
    val totalRows: Int,
    val successRows: Int,
    val failedRows: Int,
    val errors: List<ValidationError>,
    val durationMs: Long
) {

    /** 是否完全成功（无失败行 + 无错误） */
    val isSuccess: Boolean get() = failedRows == 0 && errors.isEmpty()

    /** 简要描述 */
    fun summary(): String =
        "[$importerName] 总计 $totalRows / 成功 $successRows / 失败 $failedRows / 耗时 ${durationMs}ms"
}
