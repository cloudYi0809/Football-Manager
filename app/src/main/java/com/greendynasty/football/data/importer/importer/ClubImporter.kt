package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.ClubMapper
import com.greendynasty.football.data.importer.mapper.EntityMapper
import com.greendynasty.football.data.importer.mapper.YouthAcademyMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 俱乐部导入器（clubs.csv + youth_academy.csv）
 *
 * 步骤 1：导入俱乐部基础信息（club 表）
 * 步骤 2：导入青训学院配置（youth_academy 表）
 *
 * 依赖：无（基础表，最先导入）。
 */
class ClubImporter(
    private val clubsCsv: File,
    private val youthAcademyCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入俱乐部",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = clubsCsv,
            mapper = ClubMapper,
            rules = clubRules(),
            batchWriter = { batch -> writer.insertClubs(batch) },
            stageLabel = "导入俱乐部 clubs.csv"
        ),
        ImportStep(
            csvFile = youthAcademyCsv,
            mapper = YouthAcademyMapper,
            rules = youthAcademyRules(),
            batchWriter = { batch -> writer.insertYouthAcademies(batch) },
            stageLabel = "导入青训学院 youth_academy.csv"
        )
    )

    private fun clubRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("club_id", "club_name"),
        RangeCheckRule("reputation", 1, 100),
        RangeCheckRule("training_level", 1, 100),
        RangeCheckRule("youth_level", 1, 100),
        RangeCheckRule("finance_level", 1, 100),
        RangeCheckRule("founded_year", 1800, 2025)
    )

    private fun youthAcademyRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("club_id"),
        RangeCheckRule("youth_level", 1, 100),
        RangeCheckRule("training_level", 1, 100),
        RangeCheckRule("academy_reputation", 1, 100)
    )
}
