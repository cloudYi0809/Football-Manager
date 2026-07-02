package com.greendynasty.football.youth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T16 青训学院存档状态表（save.db，V0.1 08 §二.1 + T16 方案 §三.1）
 *
 * 与 history.db 的 [com.greendynasty.football.data.history.entity.YouthAcademyEntity]（只读模板）
 * 独立：history 表存俱乐部青训的基础配置（开档数据），save 表存玩家存档中的运行时状态
 * （投资升级后的等级、风格切换、冷却、月度预算等可变字段）。
 *
 * 9 项配置：青训等级 / 训练设施 / 招募范围 / 青训声望 / 青训风格 / 月度预算 /
 * U18 教练质量 / U21 教练质量 / 国家人才池加成。
 *
 * 一个俱乐部在每个存档中只有一条青训学院状态记录。
 */
@Entity(
    tableName = "youth_academy_state",
    indices = [Index(value = ["save_id", "club_id"], unique = true)]
)
data class YouthAcademyStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "academy_id")
    val academyId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 青训等级 1-100。 */
    @ColumnInfo(name = "youth_level")
    val youthLevel: Int = 50,

    /** 训练设施 1-100。 */
    @ColumnInfo(name = "training_facility")
    val trainingFacility: Int = 50,

    /** 招募范围：LOCAL / REGIONAL / NATIONAL / INTERNATIONAL，见 [RecruitmentRange]。 */
    @ColumnInfo(name = "recruitment_range")
    val recruitmentRange: String = "LOCAL",

    /** 青训声望 1-100。 */
    @ColumnInfo(name = "academy_reputation")
    val academyReputation: Int = 50,

    /** 青训风格：见 [AcademyStyle]。 */
    @ColumnInfo(name = "academy_style")
    val academyStyle: String = "TECHNICAL",

    /** 月度预算（欧元，每月扣除）。 */
    @ColumnInfo(name = "monthly_budget")
    val monthlyBudget: Int = 50_000,

    /** U18 教练质量 1-100。 */
    @ColumnInfo(name = "u18_coach_quality")
    val u18CoachQuality: Int = 50,

    /** U21 教练质量 1-100。 */
    @ColumnInfo(name = "u21_coach_quality")
    val u21CoachQuality: Int = 50,

    /** 国家人才池加成 1-100（按俱乐部所在国家，开档时初始化）。 */
    @ColumnInfo(name = "nation_talent_pool_bonus")
    val nationTalentPoolBonus: Int = 50,

    /** 风格切换冷却（剩余月数，每月递减 1）。 */
    @ColumnInfo(name = "style_change_cooldown")
    val styleChangeCooldown: Int = 0,

    /** 上次产出月份（yyyy-MM，防同月重复触发）。 */
    @ColumnInfo(name = "last_production_month")
    val lastProductionMonth: String? = null
)
