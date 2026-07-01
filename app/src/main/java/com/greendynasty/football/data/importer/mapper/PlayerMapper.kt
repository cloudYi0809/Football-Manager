package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 球员基础信息映射器（object 单例，无状态）
 *
 * 将 players.csv 行数据转换为 [PlayerEntity]。
 *
 * CSV 字段映射：
 * - player_id → playerId
 * - source_id → sourceId
 * - real_name → realName
 * - display_name → displayName
 * - birth_date → birthDate（经 FieldConverter.toIsoDate 规范化为 YYYY-MM-DD）
 * - nationality → nationality
 * - second_nationality → secondNationality
 * - height_cm → height（兼容 height 字段名）
 * - weight_kg → weight（兼容 weight 字段名）
 * - preferred_foot → preferredFoot
 * - primary_position → primaryPosition（经 FieldConverter.normalizePosition 规范化）
 * - secondary_positions → secondaryPositions
 * - personality → personality
 * - retire_age_base → retireAgeBase（默认 35）
 * - portrait_path → portraitPath
 * - created_at → createdAt
 * - updated_at → updatedAt
 */
object PlayerMapper : EntityMapper<PlayerEntity> {

    override fun map(row: CsvRow): PlayerEntity {
        return PlayerEntity(
            playerId = row.getInt("player_id")
                ?: throw IllegalArgumentException("player_id 缺失，无法映射 PlayerEntity"),
            sourceId = row.get("source_id")?.takeIf { it.isNotBlank() },
            realName = row.getOrEmpty("real_name").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("real_name 必填，无法映射 PlayerEntity"),
            displayName = row.get("display_name")?.takeIf { it.isNotBlank() },
            birthDate = FieldConverter.toIsoDate(row.get("birth_date")),
            nationality = row.get("nationality")?.takeIf { it.isNotBlank() },
            secondNationality = row.get("second_nationality")?.takeIf { it.isNotBlank() },
            // 兼容 height_cm / weight_kg 与 height / weight 两种字段名
            height = row.getInt("height_cm") ?: row.getInt("height"),
            weight = row.getInt("weight_kg") ?: row.getInt("weight"),
            preferredFoot = row.get("preferred_foot")?.takeIf { it.isNotBlank() },
            primaryPosition = FieldConverter.normalizePosition(row.get("primary_position"))
                ?: row.get("primary_position")?.takeIf { it.isNotBlank() },
            secondaryPositions = row.get("secondary_positions")?.takeIf { it.isNotBlank() },
            personality = row.get("personality")?.takeIf { it.isNotBlank() },
            retireAgeBase = row.getIntOrDefault("retire_age_base", 35),
            portraitPath = row.get("portrait_path")?.takeIf { it.isNotBlank() },
            createdAt = FieldConverter.toIsoDate(row.get("created_at")),
            updatedAt = FieldConverter.toIsoDate(row.get("updated_at"))
        )
    }
}
