package com.greendynasty.football.growth.model

import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import java.time.LocalDate

/**
 * 成长月结核心数据模型（T09 方案 §四）
 */

/**
 * 成长计算聚合输入（多源数据聚合后的单一对象）
 *
 * 把训练 / 比赛 / 伤病 / 士气 / 青训 / 俱乐部设施等多表数据聚合为
 * [GrowthCalculator] 所需的输入。各 nullable 字段对应尚未实现的下游任务（T16/T23）。
 *
 * @param player 球员存档状态
 * @param playerBase 球员基础资料（history.db）
 * @param attributes 球员当前属性（history.db）
 * @param age 球员年龄
 * @param growthPhase 7 档年龄阶段
 * @param rangeTier 4 档活跃范围
 * @param monthlyTraining 月度训练记录（null 表示本月无记录）
 * @param monthlyPlayingTime 月度出场时间（null 表示本月未上场）
 * @param activeInjury 当前活跃伤病（T08 集成，null 表示健康）
 * @param injuryHistory 近 6 月伤病历史
 * @param moraleValue 球员士气值 0-100（默认 50）
 * @param mentorEffect 导师加成 0-0.1（默认 0）
 * @param clubTrainingQuality 俱乐部训练质量 0-1
 * @param clubCoachLevel 俱乐部教练等级 1-100
 * @param clubLeagueIntensity 俱乐部联赛强度 0-1
 * @param tacticalFitScore 战术适配分 0-1
 * @param nationTalentPoolBonus 国家人才池加成 1-100
 * @param seasonId 赛季 ID
 * @param executionDate 月结日期
 */
data class GrowthInput(
    val player: SavePlayerStateEntity,
    val playerBase: PlayerEntity,
    val attributes: PlayerAttributesEntity,
    val age: Int,
    val growthPhase: GrowthPhase,
    val rangeTier: GrowthRangeTier,
    val monthlyTraining: MonthlyTrainingRecordEntity?,
    val monthlyPlayingTime: MonthlyPlayingTimeEntity?,
    val activeInjury: SaveInjuryEntity?,
    val injuryHistory: List<SaveInjuryEntity>,
    val moraleValue: Int,
    val mentorEffect: Double,
    val club: SaveClubStateEntity,
    val clubTrainingQuality: Double,
    val clubCoachLevel: Int,
    val clubLeagueIntensity: Double,
    val tacticalFitScore: Double,
    val nationTalentPoolBonus: Int,
    val seasonId: Int,
    val executionDate: LocalDate
)

/**
 * 成长计算结果（GrowthCalculator 输出）
 *
 * @param playerId 球员 ID
 * @param caBefore 月结前 CA
 * @param caAfter 月结后 CA
 * @param caDelta CA 变化量（可为负）
 * @param attributeChanges 属性变化映射（属性名 → 变化量）
 * @param factors 10 因子分解（用于快照与调试）
 * @param realizationScore 潜力兑现率 0-1
 * @param notes 成长日志
 */
data class GrowthResult(
    val playerId: Int,
    val caBefore: Int,
    val caAfter: Int,
    val caDelta: Int,
    val attributeChanges: Map<String, Int>,
    val factors: GrowthFactorBreakdown,
    val realizationScore: Double,
    val notes: List<String>
)

/**
 * 10 因子分解（V0.2 §球员成长公式）
 */
data class GrowthFactorBreakdown(
    val trainingQuality: Double,
    val playingTime: Double,
    val mentor: Double,
    val clubFacility: Double,
    val talent: Double,
    val age: Double,
    val injury: Double,
    val morale: Double,
    val random: Double,
    val nationalPool: Double
) {
    /** 加权总分（-1 ~ 1） */
    fun weightedScore(config: GrowthConfig): Double =
        trainingQuality * config.weightTrainingQuality +
            playingTime * config.weightPlayingTime +
            mentor * config.weightMentor +
            clubFacility * config.weightClubFacility +
            talent * config.weightTalent +
            age * config.weightAge +
            injury * config.weightInjury +
            morale * config.weightMorale +
            random * config.weightRandom +
            nationalPool * config.weightNationalPool
}

/**
 * 潜力兑现月度更新结果
 */
data class PotentialUpdate(
    val realizationScore: Double,
    val paBefore: Int,
    val paAfter: Int,
    val paDelta: Int,
    val majorInjuryPaPenalty: Int
)

/**
 * 已应用的成长（用于快照写入）
 */
data class AppliedGrowth(
    val input: GrowthInput,
    val result: GrowthResult,
    val potentialUpdate: PotentialUpdate
)

/**
 * 球员成长摘要（月结报告 Top N 用）
 */
data class PlayerGrowthSummary(
    val playerId: Int,
    val playerName: String,
    val age: Int,
    val position: String,
    val caBefore: Int,
    val caAfter: Int,
    val caDelta: Int,
    val realizationScore: Double,
    val rangeTier: GrowthRangeTier,
    val primaryFactor: String
)

/**
 * 月结汇总结果
 */
data class MonthlyGrowthResult(
    val saveId: Int,
    val clubId: Int,
    val month: String,
    val executionDate: LocalDate,
    val totalPlayers: Int,
    val processedPlayers: Int,
    val skippedPlayers: Int,
    val rangeBreakdown: Map<GrowthRangeTier, Int>,
    val snapshotsWritten: Int,
    val eventsTriggered: Int,
    val topGrowers: List<PlayerGrowthSummary>,
    val topDecliners: List<PlayerGrowthSummary>,
    val eventList: List<GrowthEventEntity>,
    val durationMs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * 成长曲线数据点（T04 阵容页成长曲线数据源）
 */
data class GrowthCurvePoint(
    val date: String,
    val seasonLabel: String,
    val ca: Int,
    val pa: Int
)

/**
 * 7 档年龄阶段判定（严格对齐 V0.2 §7 档年龄表）
 */
fun classifyGrowthPhase(age: Int): GrowthPhase = when (age) {
    in 14..15 -> GrowthPhase.EXPLOSIVE
    in 16..17 -> GrowthPhase.RAPID
    in 18..20 -> GrowthPhase.STEADY
    in 21..23 -> GrowthPhase.PRE_PRIME
    in 24..27 -> GrowthPhase.PRIME
    in 28..31 -> GrowthPhase.PRE_DECLINE
    else -> GrowthPhase.DECLINE
}

/**
 * 由出生日期计算年龄
 */
fun calculateAge(birthDate: String?, currentDate: LocalDate): Int {
    if (birthDate.isNullOrBlank()) return 18
    return try {
        val birth = LocalDate.parse(birthDate.take(10))
        java.time.Period.between(birth, currentDate).years
    } catch (e: Exception) {
        18
    }
}
