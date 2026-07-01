package com.greendynasty.football.ui.squad.data

import com.greendynasty.football.ui.squad.model.SquadTab

/**
 * 球员详情聚合数据（10 个模块）。
 *
 * 由 [SquadRepository.getPlayerDetail] 从 history.db + save.db 多表聚合而来。
 * 各模块按需填充，缺失数据以空集合或 null 体现，UI 层做兜底展示。
 *
 * 10 个模块：基础信息 / 属性面板 / 位置适应 / 成长曲线 / 赛季数据 /
 * 合同信息 / 转会记录 / 伤病记录 / 球探报告 / 训练计划。
 *
 * @property basicInfo 基础信息
 * @property attributes 属性面板（最新赛季属性）
 * @property positionFit 位置适应度
 * @property growthCurve 成长曲线数据点（多赛季 CA/PA）
 * @property seasonStats 赛季数据
 * @property contract 合同信息
 * @property transferHistory 转会记录
 * @property injuryHistory 伤病记录
 * @property scoutReport 球探报告
 * @property trainingPlan 训练计划
 */
data class PlayerDetail(

    val basicInfo: BasicInfo,

    val attributes: AttributePanel,

    val positionFit: List<PositionFit>,

    val growthCurve: List<GrowthCurvePoint>,

    val seasonStats: List<SeasonStats>,

    val contract: ContractInfo,

    val transferHistory: List<TransferRecord>,

    val injuryHistory: List<InjuryRecord>,

    val scoutReport: ScoutReport,

    val trainingPlan: TrainingPlan
)

/** 基础信息模块 */
data class BasicInfo(
    val playerId: Int,
    val name: String,
    val age: Int,
    val nationality: String,
    val secondNationality: String?,
    val position: String,
    val secondaryPositions: List<String>,
    val birthDate: String?,
    val height: Int?,
    val weight: Int?,
    val preferredFoot: String?,
    val personality: String?,
    val portraitPath: String?,
    val shirtNumber: Int?,
    val squadTab: SquadTab
)

/** 属性面板模块（4 类分组：技术 / 身体 / 防守 / 精神 + 门将） */
data class AttributePanel(
    val ca: Int,
    val pa: Int,
    val technical: List<AttributeItem>,
    val physical: List<AttributeItem>,
    val defending: List<AttributeItem>,
    val mental: List<AttributeItem>,
    val goalkeeper: List<AttributeItem>
) {
    /** 6 维度汇总，供属性雷达图使用：进攻/中场/防守/身体/心理/门将 */
    val radarValues: List<RadarValue>
        get() = listOf(
            RadarValue("进攻", attackScore),
            RadarValue("中场", midfieldScore),
            RadarValue("防守", defendingScore),
            RadarValue("身体", physicalScore),
            RadarValue("心理", mentalScore),
            RadarValue("门将", gkScore)
        )

    private val attackScore: Int
        get() = listOf(
            attributesFirst("shooting"), attributesFirst("finishing"),
            attributesFirst("long_shots"), attributesFirst("crossing")
        ).average().toInt()

    private val midfieldScore: Int
        get() = listOf(
            attributesFirst("passing"), attributesFirst("technique"),
            attributesFirst("first_touch"), attributesFirst("dribbling")
        ).average().toInt()

    private val defendingScore: Int
        get() = listOf(
            attributesFirst("defending"), attributesFirst("tackling"),
            attributesFirst("marking"), attributesFirst("positioning")
        ).average().toInt()

    private val physicalScore: Int
        get() = listOf(
            attributesFirst("pace"), attributesFirst("acceleration"),
            attributesFirst("strength"), attributesFirst("stamina")
        ).average().toInt()

    private val mentalScore: Int
        get() = listOf(
            attributesFirst("vision"), attributesFirst("decision"),
            attributesFirst("composure"), attributesFirst("leadership")
        ).average().toInt()

    private val gkScore: Int
        get() = listOf(
            attributesFirst("gk_diving"), attributesFirst("gk_reflexes"),
            attributesFirst("gk_handling"), attributesFirst("gk_positioning")
        ).average().toInt()

    private fun attributesFirst(key: String): Int =
        (technical + physical + defending + mental + goalkeeper).firstOrNull { it.key == key }?.value ?: 0
}

/** 单条属性 */
data class AttributeItem(val key: String, val label: String, val value: Int)

/** 雷达图单维度值 */
data class RadarValue(val label: String, val value: Int)

/** 位置适应度模块 */
data class PositionFit(
    val position: String,
    val familiarity: Int // 0-100，熟悉度
)

/** 成长曲线单点（某赛季的 CA/PA） */
data class GrowthCurvePoint(
    val seasonId: Int,
    val seasonLabel: String,
    val ca: Int,
    val pa: Int
)

/** 赛季数据模块 */
data class SeasonStats(
    val seasonId: Int,
    val seasonLabel: String,
    val clubId: Int,
    val appearances: Int,
    val goals: Int,
    val assists: Int,
    val cleanSheets: Int,
    val yellowCards: Int,
    val redCards: Int,
    val minutesPlayed: Int,
    val avgRating: Double,
    val injuryDays: Int
)

/** 合同信息模块 */
data class ContractInfo(
    val contractUntil: String?,
    val wage: Int,
    val marketValue: Int,
    val isListed: Boolean,
    val isCaptain: Boolean,
    val isLoaned: Boolean,
    val loanClubId: Int?
)

/** 转会记录模块 */
data class TransferRecord(
    val transferDate: String,
    val fromClubId: Int?,
    val toClubId: Int?,
    val fee: Int,
    val transferType: String?,
    val notes: String?
)

/** 伤病记录模块 */
data class InjuryRecord(
    val injuryType: String?,
    val startDate: String?,
    val expectedReturnDate: String?,
    val severity: Int,
    val status: String
)

/** 球探报告模块 */
data class ScoutReport(
    val recommendationLevel: Int, // 0-5 推荐等级
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>
)

/** 训练计划模块 */
data class TrainingPlan(
    val focusArea: String, // SHOOTING/PASSING/DRIBBLING/DEFENDING/PHYSICAL/GK
    val intensity: Int, // 0-100
    val mentorId: Int?,
    val newPositionTraining: String?
)
