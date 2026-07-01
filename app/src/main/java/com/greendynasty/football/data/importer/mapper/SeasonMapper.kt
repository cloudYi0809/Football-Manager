package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.SeasonEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 赛季映射器（object 单例，无状态）
 *
 * 将 seasons.csv 行数据转换为 [SeasonEntity]。
 *
 * CSV 字段映射：
 * - season_id → seasonId（主键）
 * - year_start → yearStart
 * - year_end → yearEnd
 * - label → label（如 "2002/03"）
 * - start_date → startDate（经 FieldConverter.toIsoDate 规范化）
 * - end_date → endDate（经 FieldConverter.toIsoDate 规范化）
 * - is_historical → isHistorical（默认 1，表示历史赛季）
 */
object SeasonMapper : EntityMapper<SeasonEntity> {

    override fun map(row: CsvRow): SeasonEntity {
        return SeasonEntity(
            seasonId = row.getInt("season_id")
                ?: throw IllegalArgumentException("season_id 缺失，无法映射 SeasonEntity"),
            yearStart = row.getInt("year_start")
                ?: throw IllegalArgumentException("year_start 缺失，无法映射 SeasonEntity"),
            yearEnd = row.getInt("year_end")
                ?: throw IllegalArgumentException("year_end 缺失，无法映射 SeasonEntity"),
            label = row.getOrEmpty("label").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("label 必填，无法映射 SeasonEntity"),
            startDate = FieldConverter.toIsoDate(row.get("start_date")),
            endDate = FieldConverter.toIsoDate(row.get("end_date")),
            isHistorical = row.getIntOrDefault("is_historical", 1)
        )
    }
}
