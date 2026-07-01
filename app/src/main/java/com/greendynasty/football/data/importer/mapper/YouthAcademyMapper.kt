package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.YouthAcademyEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 青训学院映射器（object 单例，无状态）
 *
 * 将 youth_academy.csv 行数据转换为 [YouthAcademyEntity]。
 * 存储俱乐部青训学院的等级、招募范围、教练质量等。
 *
 * CSV 字段映射：
 * - club_id → clubId（主键，外键 → club）
 * - youth_level → youthLevel（青训等级，默认 50）
 * - training_level → trainingLevel（训练等级，默认 50）
 * - recruitment_range → recruitmentRange（招募范围）
 * - academy_reputation → academyReputation（学院声望，默认 50）
 * - academy_style → academyStyle（学院风格）
 * - monthly_cost → monthlyCost（月度开销，默认 0）
 * - u18_coach_quality → u18CoachQuality（U18 教练质量，默认 50）
 * - u21_coach_quality → u21CoachQuality（U21 教练质量，默认 50）
 */
object YouthAcademyMapper : EntityMapper<YouthAcademyEntity> {

    override fun map(row: CsvRow): YouthAcademyEntity {
        return YouthAcademyEntity(
            clubId = row.getInt("club_id")
                ?: throw IllegalArgumentException("club_id 缺失，无法映射 YouthAcademyEntity"),
            youthLevel = row.getIntOrDefault("youth_level", 50),
            trainingLevel = row.getIntOrDefault("training_level", 50),
            recruitmentRange = row.get("recruitment_range")?.takeIf { it.isNotBlank() },
            academyReputation = row.getIntOrDefault("academy_reputation", 50),
            academyStyle = row.get("academy_style")?.takeIf { it.isNotBlank() },
            monthlyCost = row.getIntOrDefault("monthly_cost", 0),
            u18CoachQuality = row.getIntOrDefault("u18_coach_quality", 50),
            u21CoachQuality = row.getIntOrDefault("u21_coach_quality", 50)
        )
    }
}
