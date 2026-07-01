package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.TransferHistoryEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 历史转会记录映射器（object 单例，无状态）
 *
 * 将 transfers.csv 行数据转换为 [TransferHistoryEntity]。
 * 记录历史真实转会，包括转会费、类型、是否被蝴蝶效应打断等。
 *
 * CSV 字段映射：
 * - player_id → playerId
 * - from_club_id → fromClubId
 * - to_club_id → toClubId
 * - transfer_date → transferDate（经 FieldConverter.toIsoDate 规范化）
 * - fee → fee（转会费，默认 0）
 * - transfer_type → transferType（permanent / loan / free 等）
 * - season_id → seasonId
 * - is_historical → isHistorical（默认 1）
 * - was_interrupted → wasInterrupted（默认 0，蝴蝶效应标记）
 * - notes → notes
 *
 * 注：transfer_id 为自增主键，由 Room 写入时分配。
 */
object TransferHistoryMapper : EntityMapper<TransferHistoryEntity> {

    override fun map(row: CsvRow): TransferHistoryEntity {
        return TransferHistoryEntity(
            // transfer_id 自增，使用 Entity 默认值 0
            playerId = row.getInt("player_id")
                ?: throw IllegalArgumentException("player_id 缺失，无法映射 TransferHistoryEntity"),
            fromClubId = row.getInt("from_club_id"),
            toClubId = row.getInt("to_club_id"),
            transferDate = FieldConverter.toIsoDate(row.get("transfer_date"))
                ?: row.getOrEmpty("transfer_date").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("transfer_date 必填，无法映射 TransferHistoryEntity"),
            fee = row.getIntOrDefault("fee", 0),
            transferType = row.get("transfer_type")?.takeIf { it.isNotBlank() },
            seasonId = row.getInt("season_id"),
            isHistorical = row.getIntOrDefault("is_historical", 1),
            wasInterrupted = row.getIntOrDefault("was_interrupted", 0),
            notes = row.get("notes")?.takeIf { it.isNotBlank() }
        )
    }
}
