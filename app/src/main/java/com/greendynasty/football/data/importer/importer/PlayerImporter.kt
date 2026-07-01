package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.PlayerAttributesMapper
import com.greendynasty.football.data.importer.mapper.PlayerMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.UniqueKeyRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 球员导入器（players.csv + player_attributes.csv）
 *
 * 步骤 1：导入球员基础信息（player 表，10000+ 行，流式处理）
 * 步骤 2：导入球员属性（player_attributes 表，按赛季）
 *
 * players.csv 是数据量最大的 CSV（通常 10000+ 行），必须流式处理。
 * BaseImporter 已内置流式读取与分批写入，无需额外处理。
 *
 * 依赖：无（基础表，与 clubs 并列最先导入）。
 */
class PlayerImporter(
    private val playersCsv: File,
    private val attributesCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入球员",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = playersCsv,
            mapper = PlayerMapper,
            rules = playerRules(),
            batchWriter = { batch -> writer.insertPlayers(batch) },
            stageLabel = "导入球员 players.csv"
        ),
        ImportStep(
            csvFile = attributesCsv,
            mapper = PlayerAttributesMapper,
            rules = attributesRules(),
            batchWriter = { batch -> writer.insertPlayerAttributes(batch) },
            stageLabel = "导入球员属性 player_attributes.csv"
        )
    )

    private fun playerRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("player_id", "real_name", "primary_position"),
        UniqueKeyRule("player_id"),
        RangeCheckRule("height_cm", 150, 220),
        RangeCheckRule("weight_kg", 50, 110),
        RangeCheckRule("retire_age_base", 30, 42)
    )

    private fun attributesRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("player_id", "season_id"),
        UniqueKeyRule("player_id", "season_id"),
        RangeCheckRule("ca", 1, 200),
        RangeCheckRule("pa", 1, 200)
    )
}
