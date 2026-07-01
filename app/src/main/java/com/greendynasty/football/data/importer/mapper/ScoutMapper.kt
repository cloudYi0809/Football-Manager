package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.ScoutEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 球探映射器（object 单例，无状态）
 *
 * 将 scouts.csv 行数据转换为 [ScoutEntity]。
 * 存储球探的姓名、国籍、能力值（判断当前能力、判断潜力、谈判等）。
 *
 * CSV 字段映射：
 * - name → name（球探姓名，必填）
 * - nationality → nationality
 * - age → age
 * - current_club_id → currentClubId（外键 → club）
 * - judging_current_ability → judgingCurrentAbility（默认 50）
 * - judging_potential → judgingPotential（默认 50）
 * - adaptability → adaptability（默认 50）
 * - negotiation → negotiation（默认 50）
 * - network_level → networkLevel（默认 50）
 * - reputation → reputation（默认 50）
 * - salary → salary（默认 0）
 *
 * 注：scout_id 为自增主键，由 Room 写入时分配。
 */
object ScoutMapper : EntityMapper<ScoutEntity> {

    override fun map(row: CsvRow): ScoutEntity {
        return ScoutEntity(
            // scout_id 自增，使用 Entity 默认值 0
            name = row.getOrEmpty("name").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("name 必填，无法映射 ScoutEntity"),
            nationality = row.get("nationality")?.takeIf { it.isNotBlank() },
            age = row.getInt("age"),
            currentClubId = row.getInt("current_club_id"),
            judgingCurrentAbility = row.getIntOrDefault("judging_current_ability", 50),
            judgingPotential = row.getIntOrDefault("judging_potential", 50),
            adaptability = row.getIntOrDefault("adaptability", 50),
            negotiation = row.getIntOrDefault("negotiation", 50),
            networkLevel = row.getIntOrDefault("network_level", 50),
            reputation = row.getIntOrDefault("reputation", 50),
            salary = row.getIntOrDefault("salary", 0)
        )
    }
}
