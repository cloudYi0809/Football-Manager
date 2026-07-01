package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.HistoricalProspectMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 历史新星池导入器（historical_prospects.csv → historical_prospect_pool 表）
 *
 * 存储未来可被发现的历史新星（如年轻时的梅西、C罗等），玩家通过球探可在
 * 特定年份发现。default_transfer_path 为 T20 蝴蝶引擎的历史基线路径。
 *
 * 依赖：players 必须先导入（player_id 外键）。
 */
class ProspectImporter(
    private val prospectsCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入历史新星池",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = prospectsCsv,
            mapper = HistoricalProspectMapper,
            rules = prospectRules(),
            batchWriter = { batch -> writer.insertProspects(batch) },
            stageLabel = "导入历史新星 historical_prospects.csv"
        )
    )

    private fun prospectRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("player_id", "discoverable_from", "initial_region_code"),
        RangeCheckRule("legend_level", 0, 5),
        RangeCheckRule("default_breakthrough_year", 1990, 2050)
    )
}
