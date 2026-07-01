package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.AgentEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 经纪人映射器（object 单例，无状态）
 *
 * 将 agents.csv 行数据转换为 [AgentEntity]。
 * 存储球员经纪人的信息，包括贪婪度、谈判能力、媒体影响力等。
 *
 * CSV 字段映射：
 * - name → name（经纪人姓名，必填）
 * - nationality → nationality
 * - greed → greed（贪婪度，默认 50）
 * - negotiation → negotiation（谈判能力，默认 50）
 * - media_influence → mediaInfluence（媒体影响力，默认 50）
 * - relationship_level → relationshipLevel（关系等级，默认 50）
 * - style → style（风格，如 aggressive / friendly / pragmatic）
 *
 * 注：agent_id 为自增主键，由 Room 写入时分配。
 */
object AgentMapper : EntityMapper<AgentEntity> {

    override fun map(row: CsvRow): AgentEntity {
        return AgentEntity(
            // agent_id 自增，使用 Entity 默认值 0
            name = row.getOrEmpty("name").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("name 必填，无法映射 AgentEntity"),
            nationality = row.get("nationality")?.takeIf { it.isNotBlank() },
            greed = row.getIntOrDefault("greed", 50),
            negotiation = row.getIntOrDefault("negotiation", 50),
            mediaInfluence = row.getIntOrDefault("media_influence", 50),
            relationshipLevel = row.getIntOrDefault("relationship_level", 50),
            style = row.get("style")?.takeIf { it.isNotBlank() }
        )
    }
}
