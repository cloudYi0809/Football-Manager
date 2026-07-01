package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.SquadMembershipEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 球队成员关系映射器（object 单例，无状态）
 *
 * 将 squads.csv 行数据转换为 [SquadMembershipEntity]。
 * 记录某赛季某球员效力于某俱乐部，包括号码、合同、薪资、是否租借等。
 *
 * CSV 字段映射（按任务规范）：
 * - season_id → seasonId
 * - club_id → clubId
 * - player_id → playerId
 * - shirt_number → squadNumber（球衣号码，兼容 squad_number）
 * - contract_start → joinedDate（加盟日期，经 FieldConverter.toIsoDate 规范化）
 * - contract_end → contractUntil（合同到期，经 FieldConverter.toIsoDate 规范化）
 * - wage → wage（周薪，默认 0）
 * - transfer_fee → marketValue（身价，兼容 market_value）
 * - loan → isLoan（是否租借，0/1 或 true/false）
 * - loan_from_club_id → loanFromClubId
 * - squad_role → squadRole
 *
 * 注：id 为自增主键，由 Room 写入时分配。
 */
object SquadMembershipMapper : EntityMapper<SquadMembershipEntity> {

    override fun map(row: CsvRow): SquadMembershipEntity {
        return SquadMembershipEntity(
            // id 自增，使用 Entity 默认值 0
            seasonId = row.getInt("season_id")
                ?: throw IllegalArgumentException("season_id 缺失，无法映射 SquadMembershipEntity"),
            clubId = row.getInt("club_id")
                ?: throw IllegalArgumentException("club_id 缺失，无法映射 SquadMembershipEntity"),
            playerId = row.getInt("player_id")
                ?: throw IllegalArgumentException("player_id 缺失，无法映射 SquadMembershipEntity"),
            // 兼容 shirt_number / squad_number 两种字段名
            squadNumber = row.getInt("shirt_number") ?: row.getInt("squad_number"),
            joinedDate = FieldConverter.toIsoDate(row.get("contract_start"))
                ?: FieldConverter.toIsoDate(row.get("joined_date")),
            contractUntil = FieldConverter.toIsoDate(row.get("contract_end"))
                ?: FieldConverter.toIsoDate(row.get("contract_until")),
            wage = row.getIntOrDefault("wage", 0),
            // 兼容 transfer_fee / market_value 两种字段名
            marketValue = row.getInt("transfer_fee") ?: row.getInt("market_value") ?: 0,
            // loan 字段：支持 0/1 / true/false / yes/no
            isLoan = parseBooleanToInt(row.get("loan"), default = 0),
            loanFromClubId = row.getInt("loan_from_club_id"),
            squadRole = row.get("squad_role")?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * 将布尔字符串或 0/1 转换为 Int（0 或 1）
     */
    private fun parseBooleanToInt(value: String?, default: Int): Int {
        if (value.isNullOrBlank()) return default
        val trimmed = value.trim().lowercase()
        return when (trimmed) {
            "1", "true", "yes", "y" -> 1
            "0", "false", "no", "n" -> 0
            else -> default
        }
    }
}
