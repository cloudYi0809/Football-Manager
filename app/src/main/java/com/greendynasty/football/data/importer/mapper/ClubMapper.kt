package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 俱乐部基础信息映射器（object 单例，无状态）
 *
 * 将 clubs.csv 行数据转换为 [ClubEntity]。
 *
 * CSV 字段映射：
 * - club_id → clubId（主键）
 * - source_id → sourceId
 * - club_name → clubName
 * - country → country
 * - city → city
 * - founded_year → foundedYear
 * - reputation → reputation（默认 50）
 * - stadium_name → stadiumName
 * - stadium_capacity → stadiumCapacity
 * - training_level → trainingLevel（默认 50）
 * - youth_level → youthLevel（默认 50）
 * - finance_level → financeLevel（默认 50）
 * - logo_path → logoPath
 * - kit_path → kitPath
 * - created_at → createdAt
 * - updated_at → updatedAt
 */
object ClubMapper : EntityMapper<ClubEntity> {

    override fun map(row: CsvRow): ClubEntity {
        return ClubEntity(
            clubId = row.getInt("club_id")
                ?: throw IllegalArgumentException("club_id 缺失，无法映射 ClubEntity"),
            sourceId = row.get("source_id")?.takeIf { it.isNotBlank() },
            clubName = row.getOrEmpty("club_name").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("club_name 必填，无法映射 ClubEntity"),
            country = row.get("country")?.takeIf { it.isNotBlank() },
            city = row.get("city")?.takeIf { it.isNotBlank() },
            foundedYear = row.getInt("founded_year"),
            reputation = row.getIntOrDefault("reputation", 50),
            stadiumName = row.get("stadium_name")?.takeIf { it.isNotBlank() },
            stadiumCapacity = row.getInt("stadium_capacity"),
            trainingLevel = row.getIntOrDefault("training_level", 50),
            youthLevel = row.getIntOrDefault("youth_level", 50),
            financeLevel = row.getIntOrDefault("finance_level", 50),
            logoPath = row.get("logo_path")?.takeIf { it.isNotBlank() },
            kitPath = row.get("kit_path")?.takeIf { it.isNotBlank() },
            createdAt = FieldConverter.toIsoDate(row.get("created_at")),
            updatedAt = FieldConverter.toIsoDate(row.get("updated_at"))
        )
    }
}
