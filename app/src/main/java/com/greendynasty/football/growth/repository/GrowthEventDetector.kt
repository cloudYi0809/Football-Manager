package com.greendynasty.football.growth.repository

import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthEventEntity
import com.greendynasty.football.growth.model.GrowthEventType
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.GrowthResult
import java.time.LocalDate

/**
 * 成长事件检测器（T09 方案 §五.6，6 类成长事件）
 *
 * 严格对齐 V0.2 §十五 异常保护与事件触发规则。每个事件类型都有独立的判定条件与去重策略，
 * 玩家可见的命运转折事件由本类负责识别。
 *
 * 6 类事件：
 * 1. [GrowthEventType.BREAKTHROUGH_GROWTH]：突破性成长（月 CA 增长 > 阈值，默认 3）
 * 2. [GrowthEventType.GROWTH_STAGNATION]：成长停滞（连续 N 月 CA 增长 < 阈值，默认 3 月 < 1）
 * 3. [GrowthEventType.POTENTIAL_FULFILLED]：潜力兑现（CA ≥ PA × 90%）
 * 4. [GrowthEventType.EARLY_DECLINE]：早衰（28 岁前出现 CA 下降）
 * 5. [GrowthEventType.ATTITUDE_DETERIORATION]：训练态度恶化（职业态度 < 40 且导师负向）
 * 6. [GrowthEventType.MENTOR_POSITIVE]：导师正向影响（导师加成 > 0.08）
 *
 * 去重策略：每球员每月每类型最多 1 条（由 [GrowthEventDao.getByPlayerDateType] 校验）。
 *
 * @param repository 数据访问门面（用于停滞月数统计与去重判定）
 * @param config 成长配置
 */
class GrowthEventDetector(
    private val repository: GrowthRepository,
    private val config: GrowthConfig
) {

    /**
     * 检测单球员本月全部成长事件。
     *
     * @param input 成长输入
     * @param result 成长结果
     * @return 触发的事件列表（去重后，可能为空）
     */
    suspend fun detectForPlayer(
        input: GrowthInput,
        result: GrowthResult
    ): List<GrowthEventEntity> {
        val events = mutableListOf<GrowthEventEntity>()
        val dateStr = input.executionDate.toString()
        val saveId = input.player.saveId

        // 1. 突破性成长
        detectBreakthrough(input, result)?.let { events.add(it) }
        // 2. 成长停滞（需读历史快照统计）
        detectStagnation(input, result, saveId, dateStr)?.let { events.add(it) }
        // 3. 潜力兑现
        detectPotentialFulfilled(input, result)?.let { events.add(it) }
        // 4. 早衰
        detectEarlyDecline(input, result)?.let { events.add(it) }
        // 5. 训练态度恶化
        detectAttitudeDeterioration(input, result)?.let { events.add(it) }
        // 6. 导师正向影响
        detectMentorPositive(input, result)?.let { events.add(it) }

        // 去重：同月同类型只保留 1 条
        return deduplicate(events, saveId, dateStr)
    }

    /**
     * 批量检测并触发事件（月结主流程调用）。
     *
     * @param appliedList 已应用的成长列表
     * @return 触发的事件列表（去重后）
     */
    suspend fun detectBatch(
        appliedList: List<com.greendynasty.football.growth.model.AppliedGrowth>
    ): List<GrowthEventEntity> {
        val allEvents = mutableListOf<GrowthEventEntity>()
        for (applied in appliedList) {
            allEvents.addAll(detectForPlayer(applied.input, applied.result))
        }
        return allEvents
    }

    // ==================== 6 类事件检测 ====================

    /** 1. 突破性成长：月度 CA 增长 > 阈值（默认 +3） */
    private fun detectBreakthrough(
        input: GrowthInput, result: GrowthResult
    ): GrowthEventEntity? {
        if (result.caDelta <= config.breakthroughCaThreshold) return null
        return buildEvent(
            input, result, GrowthEventType.BREAKTHROUGH_GROWTH,
            severity = "INFO",
            title = "突破性成长",
            description = "${input.playerBase.realName} 本月 CA +${result.caDelta}，状态爆表！",
            metricValue = result.caDelta.toDouble(),
            threshold = config.breakthroughCaThreshold.toDouble()
        )
    }

    /** 2. 成长停滞：连续 N 月 CA 增长 < 阈值（默认 3 月 < 1） */
    private suspend fun detectStagnation(
        input: GrowthInput, result: GrowthResult, saveId: Int, dateStr: String
    ): GrowthEventEntity? {
        // 衰退期不算停滞
        if (input.age >= config.declineAgeStart) return null
        val startDate = LocalDate.parse(dateStr)
            .minusMonths(config.stagnationMonths.toLong()).toString()
        val stagnationCount = repository.countStagnationMonths(
            saveId, input.player.playerId, startDate, config.stagnationCaThreshold
        )
        if (stagnationCount < config.stagnationMonths) return null
        // 本月若成长达标也不触发
        if (result.caDelta >= config.stagnationCaThreshold) return null
        return buildEvent(
            input, result, GrowthEventType.GROWTH_STAGNATION,
            severity = "WARN",
            title = "成长停滞",
            description = "${input.playerBase.realName} 已连续 ${config.stagnationMonths} 个月成长缓慢，需要比赛时间！",
            metricValue = stagnationCount.toDouble(),
            threshold = config.stagnationMonths.toDouble()
        )
    }

    /** 3. 潜力兑现：CA 达到 PA 的 90% */
    private fun detectPotentialFulfilled(
        input: GrowthInput, result: GrowthResult
    ): GrowthEventEntity? {
        val paSafe = input.player.currentPa.coerceAtLeast(1)
        val ratio = input.player.currentCa.toDouble() / paSafe
        if (ratio < config.potentialFulfilledRatio) return null
        return buildEvent(
            input, result, GrowthEventType.POTENTIAL_FULFILLED,
            severity = "INFO",
            title = "潜力兑现",
            description = "${input.playerBase.realName} 已兑现 ${(ratio * 100).toInt()}% 潜力！",
            metricValue = ratio,
            threshold = config.potentialFulfilledRatio
        )
    }

    /** 4. 早衰：28 岁前出现 CA 下降 */
    private fun detectEarlyDecline(
        input: GrowthInput, result: GrowthResult
    ): GrowthEventEntity? {
        if (input.age >= config.earlyDeclineAgeThreshold) return null
        if (result.caDelta >= 0) return null
        return buildEvent(
            input, result, GrowthEventType.EARLY_DECLINE,
            severity = "CRITICAL",
            title = "早衰警告",
            description = "${input.playerBase.realName} 仅 ${input.age} 岁就出现能力下降，建议检查伤病历史！",
            metricValue = input.age.toDouble(),
            threshold = config.earlyDeclineAgeThreshold.toDouble()
        )
    }

    /** 5. 训练态度恶化：职业态度 < 40 且导师负向 */
    private fun detectAttitudeDeterioration(
        input: GrowthInput, result: GrowthResult
    ): GrowthEventEntity? {
        val attitude = input.attributes.professionalism
        if (attitude >= config.attitudeLowThreshold) return null
        // 仅导师负向影响时触发（mentorEffect < 0）
        if (input.mentorEffect >= 0) return null
        return buildEvent(
            input, result, GrowthEventType.ATTITUDE_DETERIORATION,
            severity = "WARN",
            title = "训练态度恶化",
            description = "${input.playerBase.realName} 职业态度仅 ${attitude}，训练表现下滑！",
            metricValue = attitude.toDouble(),
            threshold = config.attitudeLowThreshold.toDouble()
        )
    }

    /** 6. 导师正向影响：导师加成 > 0.08 */
    private fun detectMentorPositive(
        input: GrowthInput, result: GrowthResult
    ): GrowthEventEntity? {
        if (input.mentorEffect < config.mentorPositiveThreshold) return null
        val mentorId = input.monthlyTraining?.mentorId
        return buildEvent(
            input, result, GrowthEventType.MENTOR_POSITIVE,
            severity = "INFO",
            title = "导师正向影响",
            description = "受导师指导让 ${input.playerBase.realName} 收获成长！",
            metricValue = input.mentorEffect,
            threshold = config.mentorPositiveThreshold,
            relatedPlayerId = mentorId
        )
    }

    // ==================== 工具方法 ====================

    /** 构造成长事件实体（公共字段填充） */
    private fun buildEvent(
        input: GrowthInput,
        result: GrowthResult,
        type: GrowthEventType,
        severity: String,
        title: String,
        description: String,
        metricValue: Double,
        threshold: Double,
        relatedPlayerId: Int? = null
    ): GrowthEventEntity = GrowthEventEntity(
        eventId = 0,
        saveId = input.player.saveId,
        playerId = input.player.playerId,
        clubId = input.club.clubId,
        triggerDate = input.executionDate.toString(),
        eventType = type.name,
        severity = severity,
        title = title,
        description = description,
        caAtTrigger = result.caAfter,
        paAtTrigger = input.player.currentPa,
        metricValue = metricValue,
        threshold = threshold,
        relatedPlayerId = relatedPlayerId,
        isRead = false
    )

    /** 去重：同球员同月同类型只保留 1 条（数据库查询校验） */
    private suspend fun deduplicate(
        events: List<GrowthEventEntity>, saveId: Int, dateStr: String
    ): List<GrowthEventEntity> {
        if (events.isEmpty()) return events
        return events.filter { event ->
            repository.getEventByPlayerDateType(
                saveId, event.playerId, dateStr, event.eventType
            ) == null
        }
    }
}
