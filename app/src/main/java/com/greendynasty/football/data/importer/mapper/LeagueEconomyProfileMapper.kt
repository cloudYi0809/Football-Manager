package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.save.entity.LeagueEconomyProfileEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 联赛经济画像映射器（object 单例，无状态）
 *
 * 将 league_economy_profile.csv 行数据转换为 [LeagueEconomyProfileEntity]。
 * 记录各联赛的经济参数，用于 AI 俱乐部预算和身价计算。
 *
 * CSV 字段映射：
 * - league_id → leagueId（联赛 ID，主键，外键 → competition）
 * - base_multiplier → baseMultiplier（基础乘数，如英超=1.10 / 西甲=1.00）
 * - growth_rate → growthRate（年增长率）
 * - volatility → volatility（波动率，默认 0.0）
 * - notes → notes（备注）
 *
 * 容错：Double 字段缺失时使用 0.0 作为默认值。
 * CsvRow 无原生 getDouble 方法，使用 get + toDoubleOrNull 内联转换。
 */
object LeagueEconomyProfileMapper : EntityMapper<LeagueEconomyProfileEntity> {

    override fun map(row: CsvRow): LeagueEconomyProfileEntity {
        return LeagueEconomyProfileEntity(
            leagueId = row.getInt("league_id")
                ?: throw IllegalArgumentException("league_id 缺失，无法映射 LeagueEconomyProfileEntity"),
            baseMultiplier = getDoubleOrDefault(row, "base_multiplier", 0.0),
            growthRate = getDoubleOrDefault(row, "growth_rate", 0.0),
            volatility = getDoubleOrDefault(row, "volatility", 0.0),
            notes = row.get("notes")?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * 从 CsvRow 读取 Double 字段，缺失或格式错误时返回默认值
     */
    private fun getDoubleOrDefault(row: CsvRow, column: String, default: Double): Double {
        return row.get(column)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: default
    }
}
