package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.EntityMapper
import com.greendynasty.football.data.importer.mapper.ScoutMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import com.greendynasty.football.data.importer.writer.ScoutRegionKnowledgeRow
import java.io.File

/**
 * 球探导入器（scouts.csv + scout_region_knowledge.csv）
 *
 * 步骤 1：导入球探基础信息（scout 表）
 * 步骤 2：导入球探区域知识（scout_region_knowledge 表，无 Room Entity，直接 ContentValues 写入）
 *
 * scout_region_knowledge 记录每个球探对不同区域的知识等级，
 * 影响 T14 球探任务在该区域的发现概率。
 *
 * 依赖：clubs 必须先导入（current_club_id 外键）。
 */
class ScoutImporter(
    private val scoutsCsv: File,
    private val regionKnowledgeCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入球探",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = scoutsCsv,
            mapper = ScoutMapper,
            rules = scoutRules(),
            batchWriter = { batch -> writer.insertScouts(batch) },
            stageLabel = "导入球探 scouts.csv"
        ),
        ImportStep(
            csvFile = regionKnowledgeCsv,
            mapper = ScoutRegionKnowledgeMapper,
            rules = regionKnowledgeRules(),
            batchWriter = { batch -> writer.insertScoutRegionKnowledge(batch) },
            stageLabel = "导入球探区域知识 scout_region_knowledge.csv"
        )
    )

    private fun scoutRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("name"),
        RangeCheckRule("age", 25, 80),
        RangeCheckRule("judging_current_ability", 1, 100),
        RangeCheckRule("judging_potential", 1, 100),
        RangeCheckRule("adaptability", 1, 100),
        RangeCheckRule("negotiation", 1, 100),
        RangeCheckRule("network_level", 1, 100),
        RangeCheckRule("reputation", 1, 100)
    )

    private fun regionKnowledgeRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("scout_id", "region_code"),
        RangeCheckRule("knowledge_level", 0, 100)
    )

    /**
     * 球探区域知识映射器（私有 object）
     *
     * scout_region_knowledge 表无 Room Entity，导入时直接用
     * [ScoutRegionKnowledgeRow] 作为载体，通过 ContentValues 写入。
     */
    private object ScoutRegionKnowledgeMapper : EntityMapper<ScoutRegionKnowledgeRow> {
        override fun map(row: CsvRow): ScoutRegionKnowledgeRow {
            return ScoutRegionKnowledgeRow(
                scoutId = row.getInt("scout_id")
                    ?: throw IllegalArgumentException("scout_id 缺失，无法映射 ScoutRegionKnowledgeRow"),
                regionCode = row.getOrEmpty("region_code").takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("region_code 必填，无法映射 ScoutRegionKnowledgeRow"),
                knowledgeLevel = row.getIntOrDefault("knowledge_level", 50)
            )
        }
    }
}
