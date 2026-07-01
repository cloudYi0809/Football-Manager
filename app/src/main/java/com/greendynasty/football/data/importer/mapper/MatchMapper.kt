package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 比赛映射器（object 单例，无状态）
 *
 * 将 matches.csv 行数据转换为 [MatchEntity]。
 * 存储历史真实比赛赛程与比分，以及模拟比分（用于对比）。
 *
 * CSV 字段映射：
 * - season_id → seasonId
 * - competition_id → competitionId
 * - match_date → matchDate（经 FieldConverter.toIsoDate 规范化）
 * - home_club_id → homeClubId
 * - away_club_id → awayClubId
 * - home_score_real → homeScoreReal（历史真实比分）
 * - away_score_real → awayScoreReal
 * - home_score_sim → homeScoreSim（模拟比分，导入时通常为空）
 * - away_score_sim → awayScoreSim
 * - status → status（默认 scheduled）
 * - is_historical → isHistorical（默认 1）
 * - match_stats_json → matchStatsJson
 *
 * 注：match_id 为自增主键，由 Room 写入时分配。
 */
object MatchMapper : EntityMapper<MatchEntity> {

    override fun map(row: CsvRow): MatchEntity {
        return MatchEntity(
            // match_id 自增，使用 Entity 默认值 0
            seasonId = row.getInt("season_id")
                ?: throw IllegalArgumentException("season_id 缺失，无法映射 MatchEntity"),
            competitionId = row.getInt("competition_id")
                ?: throw IllegalArgumentException("competition_id 缺失，无法映射 MatchEntity"),
            matchDate = FieldConverter.toIsoDate(row.get("match_date"))
                ?: row.getOrEmpty("match_date").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("match_date 必填，无法映射 MatchEntity"),
            homeClubId = row.getInt("home_club_id")
                ?: throw IllegalArgumentException("home_club_id 缺失，无法映射 MatchEntity"),
            awayClubId = row.getInt("away_club_id")
                ?: throw IllegalArgumentException("away_club_id 缺失，无法映射 MatchEntity"),
            homeScoreReal = row.getInt("home_score_real"),
            awayScoreReal = row.getInt("away_score_real"),
            homeScoreSim = row.getInt("home_score_sim"),
            awayScoreSim = row.getInt("away_score_sim"),
            status = row.get("status")?.takeIf { it.isNotBlank() } ?: "scheduled",
            isHistorical = row.getIntOrDefault("is_historical", 1),
            matchStatsJson = row.get("match_stats_json")?.takeIf { it.isNotBlank() }
        )
    }
}
