package com.greendynasty.football.injury.model

import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import java.time.LocalDate

/**
 * 比赛伤病判定上下文（T02 比赛引擎回调时构造）
 *
 * @param matchId 比赛 ID
 * @param matchDate 比赛日期
 * @param minute 受伤分钟
 * @param matchIntensity 比赛强度 1-10（赛事重要性 + 对抗强度）
 * @param eventType 触发事件类型
 * @param recentTrainingIntensity 最近一次训练强度 1-10（可为空，默认 5）
 */
data class MatchInjuryContext(
    val matchId: Long,
    val matchDate: LocalDate,
    val minute: Int = 0,
    val matchIntensity: Int = 5,
    val eventType: MatchEventType = MatchEventType.NORMAL,
    val recentTrainingIntensity: Int? = 5
)

/**
 * 伤病事件（对外事件，T07 AdvanceEvent 聚合 / T24 新闻生成消费）
 */
data class InjuryEvent(
    val injuryId: Int,
    val playerId: Int,
    val typeCode: String,
    val severity: InjurySeverity,
    val source: InjurySource,
    val expectedReturnDate: String,
    val matchId: Long? = null,
    val matchMinute: Int? = null,
    val description: String = "",
    val eventType: InjuryEventType = InjuryEventType.OCCURRED
)

/** 伤病事件类型 */
enum class InjuryEventType {
    OCCURRED,               // 新伤病发生
    RECOVERED,              // 恢复完成
    PERMANENT_IMPACT,       // 永久影响结算
    RECURRED,               // 复发
    CAREER_RETIREMENT       // 职业威胁伤触发退役
}

/** 球员伤病风险评分（医疗中心"疲劳风险"模块） */
data class InjuryRiskScore(
    val playerId: Int,
    val playerName: String,
    val riskScore: Int,        // 0-100
    val riskLevel: RiskLevel,
    val mainFactors: List<String> = emptyList()
)

/** 恢复进度计算结果 */
data class RecoveryProgress(
    val injuryId: Int,
    val progress: Int,         // 0-100
    val elapsedDays: Int,
    val totalDays: Int,
    val remainingDays: Int,
    val isReady: Boolean,
    val speedMultiplier: Double
)

/**
 * 强行复出风险评估结果（T08.4，供 UI 展示与玩家二次确认）
 */
data class ForceReturnRisk(
    val valid: Boolean,
    val currentProgress: Int = 0,
    val recurrenceProbability: Double = 0.0,
    val aggravationProbability: Double = 0.0,
    val permanentLossProbability: Double = 0.0,
    val matchReinjuryMultiplier: Double = 0.0,
    val caPenalty: Int = 0,
    val moraleDrop: Int = 0,
    val severity: String = "",
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun invalid() = ForceReturnRisk(valid = false)
    }
}

/** 强行复出执行结果 */
data class ForceReturnResult(
    val success: Boolean,
    val injuryId: Int = 0,
    val caPenalty: Int = 0,
    val moraleDrop: Int = 0,
    val recurrenceProbability: Double = 0.0,
    val aggravationProbability: Double = 0.0,
    val permanentLossProbability: Double = 0.0,
    val warnings: List<String> = emptyList(),
    val message: String = ""
) {
    companion object {
        fun failed(message: String) = ForceReturnResult(success = false, message = message)
    }
}

/** 治疗方案选择结果 */
data class TreatmentSelectionResult(
    val success: Boolean,
    val planType: TreatmentType = TreatmentType.STANDARD,
    val newExpectedReturnDate: String = "",
    val cost: Int = 0,
    val message: String = ""
) {
    companion object {
        fun failed(message: String) = TreatmentSelectionResult(success = false, message = message)
    }
}

/** 医疗设施升级结果 */
data class FacilityUpgradeResult(
    val success: Boolean,
    val newLevel: Int = 0,
    val cost: Int = 0,
    val newSpeedMultiplier: Double = 1.0,
    val newRecurrenceReduction: Double = 0.0,
    val message: String = ""
) {
    companion object {
        fun failed(message: String) = FacilityUpgradeResult(success = false, message = message)
    }
}

/** 永久影响结算结果（V0.2 08 §十） */
data class PermanentImpactResult(
    val playerId: Int,
    val injuryId: Int,
    val typeCode: String,
    val paceDelta: Int = 0,
    val accelerationDelta: Int = 0,
    val paDelta: Int = 0,
    val injuryPronenessDelta: Int = 0,
    val description: String
)

/**
 * 出场限制判定结果（T08.3 能力影响）
 */
data class AppearanceDecision(
    val canPlay: Boolean,
    val reason: String,
    val abilityPenalty: AbilityPenalty? = null
)

/**
 * 能力惩罚（带伤上场 / 强行复出时属性降权）
 *
 * @param caPenalty CA 临时下降点数
 * @param attributeWeight 属性有效权重 0-1（带伤时属性按此比例折算）
 * @param affectedAttributes 受影响的属性 key 列表
 */
data class AbilityPenalty(
    val caPenalty: Int,
    val attributeWeight: Double,
    val affectedAttributes: List<String> = emptyList()
)

/** 扩展：将 SaveInjuryEntity 转换为 InjuryEvent（恢复 / 复发事件构造时使用） */
fun SaveInjuryEntity.toInjuryEvent(
    severity: InjurySeverity = InjurySeverity.fromCode(this.severity),
    source: InjurySource = InjurySource.fromName(this.source),
    eventType: InjuryEventType = InjuryEventType.OCCURRED,
    description: String = ""
): InjuryEvent = InjuryEvent(
    injuryId = injuryId,
    playerId = playerId,
    typeCode = injuryType,
    severity = severity,
    source = source,
    expectedReturnDate = expectedReturnDate,
    matchId = matchId,
    matchMinute = matchMinute,
    description = description,
    eventType = eventType
)
