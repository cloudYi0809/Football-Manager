package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.CompetitionEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 赛事映射器（object 单例，无状态）
 *
 * 将 competitions.csv 行数据转换为 [CompetitionEntity]。
 *
 * CSV 字段映射：
 * - competition_id → competitionId（主键）
 * - name → name（赛事名称，如 "Premier League"）
 * - country → country
 * - type → type（league / cup / super_cup 等）
 * - reputation → reputation（默认 50）
 * - level → level（赛事级别，默认 1）
 * - rules_json → rulesJson（赛事规则 JSON 字符串）
 */
object CompetitionMapper : EntityMapper<CompetitionEntity> {

    override fun map(row: CsvRow): CompetitionEntity {
        return CompetitionEntity(
            competitionId = row.getInt("competition_id")
                ?: throw IllegalArgumentException("competition_id 缺失，无法映射 CompetitionEntity"),
            name = row.getOrEmpty("name").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("name 必填，无法映射 CompetitionEntity"),
            country = row.get("country")?.takeIf { it.isNotBlank() },
            type = row.get("type")?.takeIf { it.isNotBlank() },
            reputation = row.getIntOrDefault("reputation", 50),
            level = row.getIntOrDefault("level", 1),
            rulesJson = row.get("rules_json")?.takeIf { it.isNotBlank() }
        )
    }
}
