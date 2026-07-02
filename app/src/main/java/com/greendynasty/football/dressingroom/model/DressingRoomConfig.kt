package com.greendynasty.football.dressingroom.model

/**
 * T23 更衣室配置（V0.2 + T23 任务要求 + 实现方案 §八 dressingroom_config.json）。
 *
 * 严格依据 V0.2 算法文档，所有可调参数集中配置化，便于调参不改架构（铁律）。
 *
 * 涵盖：
 * - 4 因子士气权重（默认 0.40 / 0.30 / 0.20 / 0.10）
 * - 5 档士气等级阈值
 * - 4 因子化学反应权重
 * - 4 档氛围阈值
 * - 队长 / 影响力阈值
 * - 情绪事件触发阈值（连续首发 +3 / 连续替补 -5 / 夺冠 +15）
 * - 事件每赛季上限
 *
 * @property moraleWeights 4 因子士气权重
 * @property chemistryWeights 4 因子化学反应权重
 * @property dailyDrift 日度衰减参数
 * @property matchImpact 比赛日士气影响参数
 * @property captainChange 队长变更参数
 * @property captainInfluence 队长影响力参数
 * @property unrest 不满累积参数
 * @property eventTrigger 事件触发概率参数
 * @property eventLimits 6 类事件每赛季上限
 * @property maxLeadersPerClub 单俱乐部最大领袖数（含队长 + 副队长 + 影响力）
 */
data class DressingRoomConfig(
    val moraleWeights: MoraleFactorWeights = MoraleFactorWeights(),
    val chemistryWeights: ChemistryWeights = ChemistryWeights(),
    val dailyDrift: DailyDriftParams = DailyDriftParams(),
    val matchImpact: MatchImpactParams = MatchImpactParams(),
    val captainChange: CaptainChangeParams = CaptainChangeParams(),
    val captainInfluence: CaptainInfluenceParams = CaptainInfluenceParams(),
    val unrest: UnrestParams = UnrestParams(),
    val eventTrigger: EventTriggerParams = EventTriggerParams(),
    val eventLimits: Map<String, Int> = defaultEventLimits,
    val maxLeadersPerClub: Int = 5,
    val captainMinLeadership: Int = 60,
    val viceCaptainMinLeadership: Int = 50,
    val influentialMinLeadership: Int = 45,
    val veteranAgeThreshold: Int = 33,
    val newSigningStruggleDays: Int = 30,
    val renewalRequestContractMonthsThreshold: Int = 12
) {
    companion object {
        /** 默认配置（V0.2 推荐参数）。 */
        val DEFAULT = DressingRoomConfig()

        /** 6 类事件每赛季上限（V0.2 + T23 实现方案 §八 event_limits）。 */
        val defaultEventLimits: Map<String, Int> = mapOf(
            "UNHAPPY" to 10,
            "TRANSFER_RUMOR" to 5,
            "RENEWAL_REQUEST" to 8,
            "CONFLICT" to 5,
            "NEW_SIGNING_STRUGGLE" to 5,
            "VETERAN_FAREWELL" to 5
        )
    }
}

/**
 * 日度衰减参数（V0.2 + T23 实现方案 §八 daily_drift）。
 *
 * 士气向均值 50 缓慢回归，避免长期卡在极值。
 */
data class DailyDriftParams(
    val regressionRate: Double = 0.02,
    val noiseRange: Int = 1,
    val regressionTarget: Int = 50
)

/**
 * 比赛日士气影响参数（V0.2 + T23 任务要求：连续首发 +3 / 连续替补 -5 / 夺冠 +15）。
 *
 * 单场士气变化钳制在 ±maxDeltaPerMatch 内，避免剧烈波动。
 */
data class MatchImpactParams(
    val maxDeltaPerMatch: Int = 8,
    val starterMinutesThreshold: Int = 75,
    val subMinutesThreshold: Int = 30,
    val winBonus: Int = 4,
    val drawBonus: Int = 1,
    val lossPenalty: Int = -3,
    val ratingHighThreshold: Double = 8.0,
    val ratingHighBonus: Int = 3,
    val ratingLowThreshold: Double = 5.5,
    val ratingLowPenalty: Int = -2,
    // 连续首发 / 替补阈值
    val consecutiveStartsBonusThreshold: Int = 3,  // 连续 3 场首发 +3
    val consecutiveStartsBonus: Int = 3,
    val consecutiveBenchedPenaltyThreshold: Int = 3, // 连续 3 场替补 -5
    val consecutiveBenchedPenalty: Int = -5,
    // 夺冠奖励
    val winTitleBonus: Int = 15
)

/**
 * 队长变更参数（V0.2 + T23 实现方案 §八 captain_change）。
 */
data class CaptainChangeParams(
    val previousCaptainPenalty: Int = 15,
    val newCaptainBonus: Int = 10
)

/**
 * 队长影响力参数（V0.2 + T23 实现方案 §八 captain_influence）。
 *
 * 队长影响力 → 全队士气影响：
 * - influence >60 → 全队 +3
 * - influence 40-60 → 全队 +1
 * - influence <40 → 无影响
 * - 队长士气 <30 → 全队 -5（队长带头崩盘）
 */
data class CaptainInfluenceParams(
    val highInfluenceThreshold: Int = 60,
    val midInfluenceThreshold: Int = 40,
    val highInfluenceBonus: Int = 3,
    val midInfluenceBonus: Int = 1,
    val captainLowMoraleThreshold: Int = 30,
    val captainCollapsePenalty: Int = 5
)

/**
 * 不满累积参数（V0.2 + T23 实现方案 §八 unrest）。
 *
 * 长期未上场 / 战绩差 → unrestAccumulator 累积，≥阈值触发情绪事件。
 */
data class UnrestParams(
    val unrestThreshold: Int = 3,
    val severeThreshold: Int = 5,
    val unrestPerMissedImportantMatch: Int = 1,
    val unrestPerConsecutiveBenched: Int = 1
)

/**
 * 事件触发概率参数（V0.2 + T23 实现方案 §八 event_trigger）。
 */
data class EventTriggerParams(
    val unhappyMoraleThreshold: Int = 30,
    val transferRumorMoraleThreshold: Int = 20,
    val transferRumorMinConsecutiveLosses: Int = 3,
    val conflictMoraleThreshold: Int = 30,
    val newSigningStruggleMoraleThreshold: Int = 40,
    val conflictProbability: Double = 0.15,
    val transferRumorProbability: Double = 0.30
)
