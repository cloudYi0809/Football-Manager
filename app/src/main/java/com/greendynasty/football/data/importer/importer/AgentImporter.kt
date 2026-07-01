package com.greendynasty.football.data.importer.importer

import com.greendynasty.football.data.importer.mapper.AgentMapper
import com.greendynasty.football.data.importer.mapper.EntityMapper
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.RangeCheckRule
import com.greendynasty.football.data.importer.validator.RequiredFieldRule
import com.greendynasty.football.data.importer.validator.ValidationRule
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import com.greendynasty.football.data.importer.writer.PlayerAgentRow
import java.io.File

/**
 * 经纪人导入器（agents.csv + player_agent.csv）
 *
 * 步骤 1：导入经纪人基础信息（agent 表）
 * 步骤 2：导入球员-经纪人关联（player_agent 表，无 Room Entity，直接 ContentValues 写入）
 *
 * player_agent 为多对多关联表，记录球员与经纪人的代理关系。
 *
 * 依赖：players 必须先导入（player_agent 引用 player_id）。
 */
class AgentImporter(
    private val agentsCsv: File,
    private val playerAgentCsv: File,
    validator: DataValidator,
    writer: HistoryDbWriter,
    batchSize: Int = 500,
    maxErrors: Int = 100
) : BaseImporter(
    stageName = "导入经纪人",
    validator = validator,
    writer = writer,
    batchSize = batchSize,
    maxErrors = maxErrors
) {

    override fun steps(): List<ImportStep<*>> = listOf(
        ImportStep(
            csvFile = agentsCsv,
            mapper = AgentMapper,
            rules = agentRules(),
            batchWriter = { batch -> writer.insertAgents(batch) },
            stageLabel = "导入经纪人 agents.csv"
        ),
        ImportStep(
            csvFile = playerAgentCsv,
            mapper = PlayerAgentMapper,
            rules = playerAgentRules(),
            batchWriter = { batch -> writer.insertPlayerAgents(batch) },
            stageLabel = "导入球员经纪人关联 player_agent.csv"
        )
    )

    private fun agentRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("name"),
        RangeCheckRule("greed", 1, 100),
        RangeCheckRule("negotiation", 1, 100),
        RangeCheckRule("media_influence", 1, 100),
        RangeCheckRule("relationship_level", 1, 100)
    )

    private fun playerAgentRules(): List<ValidationRule<CsvRow>> = listOf(
        RequiredFieldRule("player_id", "agent_id")
    )

    /**
     * 球员-经纪人关联映射器（私有 object）
     *
     * player_agent 表无 Room Entity，导入时直接用
     * [PlayerAgentRow] 作为载体，通过 ContentValues 写入。
     */
    private object PlayerAgentMapper : EntityMapper<PlayerAgentRow> {
        override fun map(row: CsvRow): PlayerAgentRow {
            return PlayerAgentRow(
                playerId = row.getInt("player_id")
                    ?: throw IllegalArgumentException("player_id 缺失，无法映射 PlayerAgentRow"),
                agentId = row.getInt("agent_id")
                    ?: throw IllegalArgumentException("agent_id 缺失，无法映射 PlayerAgentRow")
            )
        }
    }
}
