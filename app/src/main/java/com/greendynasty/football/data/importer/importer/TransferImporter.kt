package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.TransferHistoryMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 历史转会导入器（transfers.csv → transfer_history 表）
 *
 * 记录历史真实转会，包括转会费、类型、是否被蝴蝶效应打断等。
 * transfer_history 为 T20 蝴蝶引擎的历史基线数据。
 *
 * 依赖：players、clubs、seasons 必须先导入（外键约束）。
 */
class TransferImporter(
    private val transfersCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入历史转会",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = transfersCsv,
            mapper = TransferHistoryMapper,
            rules = transferRules(),
            batchWriter = { batch -> writer.insertTransfers(batch) },
            stageLabel = "导入转会 transfers.csv"
        )
    )

    private fun transferRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("player_id", "transfer_date"),
        RangeCheckRule("fee", 0, 1_000_000_000)
    )
}
