package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 俱乐部 AI 画像表（save.db，V0.2）
 * 记录 AI 俱乐部的决策偏好，用于 AI 转会、青训、战术等决策。
 * 每个俱乐部一条记录，各偏好值范围 0-100。
 */
@Entity(tableName = "club_ai_profile")
data class ClubAiProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "club_id")
    val clubId: Int, // 俱乐部 ID

    @ColumnInfo(name = "ambition")
    val ambition: Int = 50, // 雄心（影响引援力度）

    @ColumnInfo(name = "financial_power")
    val financialPower: Int = 50, // 财力（影响预算规模）

    @ColumnInfo(name = "youth_preference")
    val youthPreference: Int = 50, // 青训偏好（倾向培养年轻人）

    @ColumnInfo(name = "star_preference")
    val starPreference: Int = 50, // 球星偏好（倾向购买成名球星）

    @ColumnInfo(name = "resale_preference")
    val resalePreference: Int = 50, // 转售偏好（低买高卖倾向）

    @ColumnInfo(name = "domestic_preference")
    val domesticPreference: Int = 50, // 本土球员偏好

    @ColumnInfo(name = "tactical_identity")
    val tacticalIdentity: String?, // 战术风格标识（如 tiki_taka / gegenpress）

    @ColumnInfo(name = "risk_tolerance")
    val riskTolerance: Int = 50, // 风险承受度

    @ColumnInfo(name = "wage_strictness")
    val wageStrictness: Int = 50, // 工资纪律（越严越不愿破工资结构）

    @ColumnInfo(name = "patience_with_manager")
    val patienceWithManager: Int = 50, // 对主帅的耐心（影响解雇概率）

    // ===== T18 AI 俱乐部画像扩展字段（V0.2 05 §二 + §三） =====

    /** 俱乐部性格标识（T18，6 种性格枚举名，详见 [com.greendynasty.football.ai.profile.model.ClubPersonality]） */
    @ColumnInfo(name = "club_personality")
    val clubPersonality: String? = null, // CONSERVATIVE / AGGRESSIVE / PRAGMATIC / IDEALIST / MONEY_DRIVEN / YOUTH_ADVOCATE

    /** 长期目标标识（T18，6 种长期目标枚举名，详见 [com.greendynasty.football.ai.profile.model.LongTermGoal]） */
    @ColumnInfo(name = "long_term_goal")
    val longTermGoal: String? = null, // WIN_TITLE / AVOID_RELEGATION / DEVELOP_YOUTH / FINANCIAL_BALANCE / BUILD_BRAND / TOP_HALF

    /** 长期目标赛季数（T18，3-5 年） */
    @ColumnInfo(name = "target_seasons")
    val targetSeasons: Int = 3, // 默认 3 年

    /** 球员类型偏好标识（T18，5 种球员原型，详见 [com.greendynasty.football.ai.profile.model.PlayerArchetype]） */
    @ColumnInfo(name = "player_archetype")
    val playerArchetype: String? = null, // STAR / WONDERKID / SQUAD / VETERAN / BARGAIN

    /** 转会预算分配偏好（T18，0-100，越高越倾向把预算投入转会市场而非青训） */
    @ColumnInfo(name = "transfer_budget_ratio")
    val transferBudgetRatio: Int = 50,

    /** 青训投入偏好（T18，0-100，越高越倾向投资青训学院） */
    @ColumnInfo(name = "youth_investment_ratio")
    val youthInvestmentRatio: Int = 50
)
