package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.api.ImportProgressCallback
import com.greendynasty.football.data.importer.api.ImportResult
import com.greendynasty.football.data.importer.mapper.EntityMapper
import com.greendynasty.football.data.importer.parser.CsvParser
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.Severity
import com.greendynasty.football.data.importer.validator.ValidationError
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 导入器抽象基类（多步骤流式导入）
 *
 * 每个 [BaseImporter] 子类对应一个导入阶段（如导入球员、导入俱乐部），
 * 通过 [steps] 返回一个或多个 [ImportStep] 实现单 CSV 或多 CSV 导入。
 *
 * 流程（每个 [ImportStep]）：
 * 1. 流式读取 CSV（[CsvParser]），控内存，适用于 10000+ 行大数据量
 * 2. 逐行校验（[DataValidator] + [ValidationRule]）
 * 3. 校验通过则映射为 Entity（[EntityMapper]）
 * 4. 按 [batchSize] 缓冲，批量写入（[HistoryDbWriter]）
 * 5. 整批写入失败时退化为逐条写入，定位错误行
 * 6. 错误数达 [maxErrors] 时提前终止该阶段
 *
 * 单个 Importer 失败不影响其他 Importer（由 [com.greendynasty.football.data.importer.api.DataImportManager] 隔离）。
 *
 * @param stageName  阶段名称（如 "导入球员"），用于进度回调与日志
 * @param validator  数据校验器
 * @param writer     history.db 写入器
 * @param batchSize  每批写入行数（默认 500）
 * @param maxErrors  阶段内最大错误数，超过即提前终止（默认 100）
 */
abstract class BaseImporter(
    val stageName: String,
    val validator: DataValidator,
    val writer: HistoryDbWriter,
    val batchSize: Int = 500,
    val maxErrors: Int = 100
) {

    /**
     * 子类返回本阶段需要执行的一个或多个导入步骤。
     *
     * 单 CSV 导入器返回 1 个步骤；多 CSV 导入器（如 PlayerImporter 同时导入
     * players.csv 与 player_attributes.csv）返回多个步骤。
     */
    protected abstract fun steps(): List<ImportStep<*>>

    /**
     * 执行导入：依次运行所有步骤，聚合结果。
     *
     * @param progressCallback 进度回调（可空）
     * @return 本阶段导入结果
     */
    suspend fun import(progressCallback: ImportProgressCallback? = null): ImportResult =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            val allErrors = mutableListOf<ValidationError>()
            var totalAll = 0
            var successAll = 0
            var failedAll = 0

            for (step in steps()) {
                // 错误数已达上限，跳过后续步骤
                if (allErrors.size >= maxErrors) {
                    progressCallback?.onError("$stageName 错误数已达 $maxErrors，跳过步骤：${step.stageLabel}")
                    break
                }
                val (total, success, failed) = runStep(step, progressCallback, allErrors)
                totalAll += total
                successAll += success
                failedAll += failed
            }

            ImportResult(
                importerName = stageName,
                totalRows = totalAll,
                successRows = successAll,
                failedRows = failedAll,
                errors = allErrors.toList(),
                durationMs = System.currentTimeMillis() - startMs
            )
        }

    /**
     * 运行单个导入步骤：流式读取 CSV → 校验 → 映射 → 批量写入。
     *
     * @return Triple(totalRows, successRows, failedRows)
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runStep(
        step: ImportStep<T>,
        callback: ImportProgressCallback?,
        errors: MutableList<ValidationError>
    ): Triple<Int, Int, Int> {
        callback?.onStage(step.stageLabel)

        // CSV 文件不存在时跳过（部分 CSV 为可选）
        if (!step.csvFile.exists()) {
            callback?.onError("CSV 文件不存在，跳过：${step.csvFile.name}")
            return Triple(0, 0, 0)
        }

        var total = 0
        var success = 0
        var failed = 0
        val buffer = mutableListOf<T>()
        // 记录当前缓冲区首行在文件中的行号，用于写入失败时定位错误行
        var batchStartRowIndex = 0

        try {
            CsvParser(step.csvFile.inputStream()).use { parser ->
                parser.readHeader()
                var row = parser.readRow()
                while (row != null) {
                    total++
                    try {
                        // 1. 校验
                        val vr = validator.validateRow(row, step.rules)
                        // SKIP：整行跳过，不计入成功也不计入失败
                        if (vr.errors.any { it.severity == Severity.SKIP }) {
                            errors.addAll(vr.errors)
                            row = parser.readRow()
                            continue
                        }
                        // ERROR：计入失败
                        if (vr.hasErrors()) {
                            errors.addAll(vr.errors.filter { it.severity == Severity.ERROR })
                            failed++
                            if (errors.size >= maxErrors) {
                                callback?.onError("${step.stageLabel} 错误数达 $maxErrors，提前终止")
                                break
                            }
                            row = parser.readRow()
                            continue
                        }
                        // WARNING：记录但继续处理
                        errors.addAll(vr.errors.filter { it.severity == Severity.WARNING })

                        // 2. 映射为 Entity
                        val entity = step.mapper.map(row)
                        if (buffer.isEmpty()) {
                            batchStartRowIndex = row.rowIndex
                        }
                        buffer.add(entity)

                        // 3. 缓冲满则批量写入
                        if (buffer.size >= batchSize) {
                            success += writeBatchSafely(buffer, step.batchWriter, errors, batchStartRowIndex)
                            buffer.clear()
                            callback?.onProgress(total, -1, step.stageLabel)
                        }
                    } catch (e: IllegalArgumentException) {
                        // 映射失败（必填字段缺失等）
                        errors.add(
                            ValidationError(
                                row.rowIndex,
                                "_mapping",
                                e.message ?: "Entity 映射失败",
                                Severity.ERROR
                            )
                        )
                        failed++
                        if (errors.size >= maxErrors) {
                            callback?.onError("${step.stageLabel} 错误数达 $maxErrors，提前终止")
                            break
                        }
                    }
                    row = parser.readRow()
                }

                // 4. 写入剩余缓冲
                if (buffer.isNotEmpty()) {
                    success += writeBatchSafely(buffer, step.batchWriter, errors, batchStartRowIndex)
                    buffer.clear()
                    callback?.onProgress(total, -1, step.stageLabel)
                }
            }
        } catch (e: Exception) {
            errors.add(
                ValidationError(
                    0,
                    "_io",
                    "${step.stageLabel} IO 异常：${e.message}",
                    Severity.ERROR
                )
            )
            callback?.onError("${step.stageLabel} 异常：${e.message}")
        }

        return Triple(total, success, failed)
    }

    /**
     * 批量写入 + 错误恢复：
     * 1. 先尝试整批写入（性能最优）
     * 2. 整批失败 → 退化为逐条写入，定位错误行
     *
     * @param items      待写入列表
     * @param batchWriter 批量写入函数（调用 HistoryDbWriter.insertXxx）
     * @param errors     错误收集
     * @param startIndex 本批首行行号（用于错误定位）
     * @return 成功写入的行数
     */
    private suspend fun <T> writeBatchSafely(
        items: List<T>,
        batchWriter: suspend (List<T>) -> Unit,
        errors: MutableList<ValidationError>,
        startIndex: Int
    ): Int {
        return try {
            batchWriter(items)
            items.size
        } catch (e: Exception) {
            // 整批失败：退化为逐条写入
            var count = 0
            items.forEachIndexed { i, item ->
                try {
                    batchWriter(listOf(item))
                    count++
                } catch (e2: Exception) {
                    errors.add(
                        ValidationError(
                            startIndex + i,
                            "_write",
                            e2.message ?: "写入失败",
                            Severity.ERROR
                        )
                    )
                }
            }
            count
        }
    }

    /**
     * 导入步骤（单 CSV 文件的处理配置）
     *
     * @param csvFile     CSV 文件
     * @param mapper      Entity 映射器
     * @param rules       校验规则列表
     * @param batchWriter 批量写入函数（如 `{ list -> writer.insertPlayers(list) }`）
     * @param stageLabel  步骤标签（如 "导入球员 players.csv"）
     */
    data class ImportStep<T>(
        val csvFile: File,
        val mapper: EntityMapper<T>,
        val rules: List<ValidationRule<CsvRow>>,
        val batchWriter: suspend (List<T>) -> Unit,
        val stageLabel: String
    )
}
