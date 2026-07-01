package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.StaffEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 教练/员工映射器（object 单例，无状态）
 *
 * 将 staff.csv 行数据转换为 [StaffEntity]。
 * 存储教练、队医、分析师等俱乐部工作人员的信息。
 *
 * CSV 字段映射：
 * - name → name（员工姓名，必填）
 * - role → role（角色：manager / coach / physio / analyst / scout 等）
 * - nationality → nationality
 * - age → age
 * - current_club_id → currentClubId（外键 → club）
 * - ability → ability（当前能力，默认 50）
 * - potential → potential（潜力，默认 50）
 * - reputation → reputation（声望，默认 50）
 * - salary → salary（薪资，默认 0）
 * - contract_until → contractUntil（合同到期，经 FieldConverter.toIsoDate 规范化）
 * - attributes_json → attributesJson（属性 JSON 字符串）
 *
 * 注：staff_id 为自增主键，由 Room 写入时分配。
 */
object StaffMapper : EntityMapper<StaffEntity> {

    override fun map(row: CsvRow): StaffEntity {
        return StaffEntity(
            // staff_id 自增，使用 Entity 默认值 0
            name = row.getOrEmpty("name").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("name 必填，无法映射 StaffEntity"),
            role = row.get("role")?.takeIf { it.isNotBlank() },
            nationality = row.get("nationality")?.takeIf { it.isNotBlank() },
            age = row.getInt("age"),
            currentClubId = row.getInt("current_club_id"),
            ability = row.getIntOrDefault("ability", 50),
            potential = row.getIntOrDefault("potential", 50),
            reputation = row.getIntOrDefault("reputation", 50),
            salary = row.getIntOrDefault("salary", 0),
            contractUntil = FieldConverter.toIsoDate(row.get("contract_until")),
            attributesJson = row.get("attributes_json")?.takeIf { it.isNotBlank() }
        )
    }
}
