package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.ClubCompetitionSeasonEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 俱乐部-赛事-赛季关联映射器（object 单例，无状态）
 *
 * 将 club_competition_season.csv 行数据转换为 [ClubCompetitionSeasonEntity]。
 *
 * 该表通常由 LeagueImporter 派生（CSV 可能没有独立文件，从 squads 或 clubs 派生），
 * 但也支持独立 CSV 导入。记录某赛季某俱乐部参加了哪些赛事。
 *
 * CSV 字段映射：
 * - season_id → seasonId
 * - competition_id → competitionId
 * - club_id → clubId
 *
 * 注：id 为自增主键，由 Room 写入时分配，Mapper 中不设置。
 */
object ClubCompetitionSeasonMapper : EntityMapper<ClubCompetitionSeasonEntity> {

    override fun map(row: CsvRow): ClubCompetitionSeasonEntity {
        return ClubCompetitionSeasonEntity(
            // id 自增，使用 Entity 默认值 0
            seasonId = row.getInt("season_id")
                ?: throw IllegalArgumentException("season_id 缺失，无法映射 ClubCompetitionSeasonEntity"),
            competitionId = row.getInt("competition_id")
                ?: throw IllegalArgumentException("competition_id 缺失，无法映射 ClubCompetitionSeasonEntity"),
            clubId = row.getInt("club_id")
                ?: throw IllegalArgumentException("club_id 缺失，无法映射 ClubCompetitionSeasonEntity")
        )
    }
}
