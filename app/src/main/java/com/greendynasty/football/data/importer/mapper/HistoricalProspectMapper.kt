package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 历史新星池映射器（object 单例，无状态）
 *
 * 将 historical_prospects.csv 行数据转换为 [HistoricalProspectPoolEntity]。
 * 存储未来可被发现的历史新星（如年轻时的梅西、C罗等），玩家通过球探可在特定年份发现。
 *
 * 关键 CSV 字段映射：
 * - player_id → playerId（外键 → player）
 * - discoverable_from → discoverableFrom（可发现起始日期/年份，必填）
 * - initial_region_code → initialRegionCode（初始区域代码，必填）
 * - default_youth_club_id → defaultYouthClubId（默认青训俱乐部 ID）
 * - default_first_team_club_id → defaultFirstTeamClubId（默认一线队俱乐部 ID）
 * - default_breakthrough_year → defaultBreakthroughYear（默认突破年份）
 * - default_transfer_path → defaultTransferPath（默认转会路径 JSON）
 * - legend_level → legendLevel（传奇等级 0-5，默认 0）
 * - tags → tags（标签，逗号分隔）
 * - hidden_until_discovered → hiddenUntilDiscovered（默认 1，隐藏直到被发现）
 * - created_scenario → createdScenario（创建场景标识）
 *
 * 注：prospect_id 为自增主键，由 Room 写入时分配。
 *
 * 蝴蝶效应关联：default_transfer_path 为 T20 蝴蝶引擎的历史基线路径。
 */
object HistoricalProspectMapper : EntityMapper<HistoricalProspectPoolEntity> {

    override fun map(row: CsvRow): HistoricalProspectPoolEntity {
        return HistoricalProspectPoolEntity(
            // prospect_id 自增，使用 Entity 默认值 0
            playerId = row.getInt("player_id")
                ?: throw IllegalArgumentException("player_id 缺失，无法映射 HistoricalProspectPoolEntity"),
            discoverableFrom = row.getOrEmpty("discoverable_from").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("discoverable_from 必填，无法映射 HistoricalProspectPoolEntity"),
            defaultYouthClubId = row.getInt("default_youth_club_id"),
            defaultFirstTeamClubId = row.getInt("default_first_team_club_id"),
            defaultBreakthroughYear = row.getInt("default_breakthrough_year") ?: 0,
            // 默认转会路径（JSON 字符串，T20 蝴蝶引擎读取作为基线）
            defaultTransferPath = row.get("default_transfer_path")?.takeIf { it.isNotBlank() },
            initialRegionCode = row.getOrEmpty("initial_region_code").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("initial_region_code 必填，无法映射 HistoricalProspectPoolEntity"),
            hiddenUntilDiscovered = row.getIntOrDefault("hidden_until_discovered", 1),
            // 传奇等级（0=普通 / 1-3=球星 / 4-5=传奇），默认 0
            legendLevel = row.getIntOrDefault("legend_level", 0),
            createdScenario = row.get("created_scenario")?.takeIf { it.isNotBlank() },
            // 标签（逗号分隔，如 "wonderkid,brazilian,forward"）
            tags = row.get("tags")?.takeIf { it.isNotBlank() }
        )
    }
}
