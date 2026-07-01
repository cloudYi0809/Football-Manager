package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.ClubCompetitionSeasonMapper
import com.greendynasty.football.data.importer.mapper.CompetitionMapper
import com.greendynasty.football.data.importer.mapper.SeasonMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import java.io.File

/**
 * 联赛结构导入器（seasons.csv + competitions.csv + club_competition_season.csv）
 *
 * 步骤 1：导入赛季（season 表）
 * 步骤 2：导入赛事（competition 表）
 * 步骤 3：导入俱乐部-赛事-赛季关联（club_competition_season 表）
 *
 * 依赖：clubs 必须先导入（club_competition_season 引用 club_id）。
 */
class LeagueImporter(
    private val seasonsCsv: File,
    private val competitionsCsv: File,
    private val clubCompetitionSeasonCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入联赛结构",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = seasonsCsv,
            mapper = SeasonMapper,
            rules = seasonRules(),
            batchWriter = { batch -> writer.insertSeasons(batch) },
            stageLabel = "导入赛季 seasons.csv"
        ),
        ImportStep(
            csvFile = competitionsCsv,
            mapper = CompetitionMapper,
            rules = competitionRules(),
            batchWriter = { batch -> writer.insertCompetitions(batch) },
            stageLabel = "导入赛事 competitions.csv"
        ),
        ImportStep(
            csvFile = clubCompetitionSeasonCsv,
            mapper = ClubCompetitionSeasonMapper,
            rules = clubCompetitionSeasonRules(),
            batchWriter = { batch -> writer.insertClubCompetitionSeasons(batch) },
            stageLabel = "导入俱乐部赛事关联 club_competition_season.csv"
        )
    )

    private fun seasonRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("season_id", "year_start", "year_end", "label"),
        RangeCheckRule("year_start", 1990, 2050),
        RangeCheckRule("year_end", 1990, 2050)
    )

    private fun competitionRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("competition_id", "name"),
        RangeCheckRule("reputation", 1, 100),
        RangeCheckRule("level", 1, 10)
    )

    private fun clubCompetitionSeasonRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("season_id", "competition_id", "club_id")
    )
}
