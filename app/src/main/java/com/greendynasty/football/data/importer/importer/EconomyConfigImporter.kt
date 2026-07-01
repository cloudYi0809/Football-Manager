package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.EconomyIndexMapper
import com.greendynasty.football.data.importer.mapper.LeagueEconomyProfileMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 经济配置导入器（economy_index.csv + league_economy_profile.csv）
 *
 * 步骤 1：导入全球经济指数（economy_index 表，按年份）
 * 步骤 2：导入联赛经济画像（league_economy_profile 表）
 *
 * 经济指数用于转会费、工资的通胀计算（T11/T13）；
 * 联赛经济画像用于 AI 俱乐部预算和身价计算（T13）。
 *
 * 依赖：competitions 必须先导入（league_economy_profile 引用 competition_id）。
 */
class EconomyConfigImporter(
    private val economyIndexCsv: File,
    private val leagueProfileCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入经济配置",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = economyIndexCsv,
            mapper = EconomyIndexMapper,
            rules = economyIndexRules(),
            batchWriter = { batch -> writer.insertEconomyIndices(batch) },
            stageLabel = "导入经济指数 economy_index.csv"
        ),
        ImportStep(
            csvFile = leagueProfileCsv,
            mapper = LeagueEconomyProfileMapper,
            rules = leagueProfileRules(),
            batchWriter = { batch -> writer.insertLeagueProfiles(batch) },
            stageLabel = "导入联赛经济画像 league_economy_profile.csv"
        )
    )

    private fun economyIndexRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("year"),
        RangeCheckRule("year", 1990, 2050)
    )

    private fun leagueProfileRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("league_id")
    )
}
