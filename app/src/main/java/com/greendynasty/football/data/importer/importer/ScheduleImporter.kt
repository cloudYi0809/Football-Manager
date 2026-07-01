package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.MatchMapper
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 比赛赛程导入器（matches.csv → match 表）
 *
 * 导入历史真实比赛赛程与比分。matches.csv 通常数据量较大（单赛季 500+ 场），
 * 采用流式处理避免内存溢出。
 *
 * 依赖：seasons、competitions、clubs 必须先导入（外键约束）。
 */
class ScheduleImporter(
    private val matchesCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入比赛赛程",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = matchesCsv,
            mapper = MatchMapper,
            rules = matchRules(),
            batchWriter = { batch -> writer.insertMatches(batch) },
            stageLabel = "导入比赛 matches.csv"
        )
    )

    private fun matchRules(): List<ValidationRule<com.greendynasty.football.data.importer.parser.CsvRow>> = listOf(
        RequiredFieldRule("season_id", "competition_id", "match_date", "home_club_id", "away_club_id"),
        RangeCheckRule("home_score_real", 0, 30),
        RangeCheckRule("away_score_real", 0, 30)
    )
}
