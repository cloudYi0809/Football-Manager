package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.StaffMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 教练/员工导入器（staff.csv → staff 表）
 *
 * 存储教练、队医、分析师等俱乐部工作人员的信息。
 *
 * 依赖：clubs 必须先导入（current_club_id 外键）。
 */
class StaffImporter(
    private val staffCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入教练员工",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = staffCsv,
            mapper = StaffMapper,
            rules = staffRules(),
            batchWriter = { batch -> writer.insertStaff(batch) },
            stageLabel = "导入员工 staff.csv"
        )
    )

    private fun staffRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("name"),
        RangeCheckRule("age", 25, 80),
        RangeCheckRule("ability", 1, 100),
        RangeCheckRule("potential", 1, 100),
        RangeCheckRule("reputation", 1, 100)
    )
}
