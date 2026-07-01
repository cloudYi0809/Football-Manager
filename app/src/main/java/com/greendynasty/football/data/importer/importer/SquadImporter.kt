package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.SquadMembershipMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 球队成员导入器（squads.csv → squad_membership 表）
 *
 * 记录某赛季某球员效力于某俱乐部，包括球衣号码、合同、薪资、是否租借等。
 * 数据量较大（单赛季 10000+ 行），采用流式处理。
 *
 * 依赖：seasons、clubs、players 必须先导入（外键约束）。
 */
class SquadImporter(
    private val squadsCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入球队阵容",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = squadsCsv,
            mapper = SquadMembershipMapper,
            rules = squadRules(),
            batchWriter = { batch -> writer.insertSquads(batch) },
            stageLabel = "导入阵容 squads.csv"
        )
    )

    private fun squadRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("season_id", "club_id", "player_id"),
        RangeCheckRule("wage", 0, 10_000_000),
        RangeCheckRule("shirt_number", 1, 99)
    )
}
