package com.greendynasty.football.butterfly.deviation

import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.DeviationLevel
import com.greendynasty.football.butterfly.model.DeviationReport

/**
 * T20 历史偏差度量计算器（任务要求 4：量化历史偏差 0-100）。
 *
 * V1 简化算法（严格遵循 V0.2 §三 importance 字段 + T20 方案 §九 配置化）：
 *
 * 公式：
 * ```
 * deviation = min(deviationMax,
 *     (eventCount / maxEvents) * eventCountWeight +
 *     (totalImportance / (maxEvents * 100)) * importanceWeight +
 *     (nodeCount / maxNodes) * nodeCountWeight
 * )
 * ```
 *
 * 三维度加权（默认 40/40/20）：
 * - 事件数量维度（40%）：事件越多，偏差越大
 * - 总重要度维度（40%）：重要度越高的事件对历史冲击越大
 * - 节点数量维度（20%）：影响节点数反映传播广度
 *
 * 例：
 * - 0 事件 → 偏差 0
 * - 5 事件、平均重要度 70、10 节点 → (5/20)*40 + (350/2000)*40 + (10/200)*20 = 10 + 7 + 1 = 18
 * - 20 事件、平均重要度 70、200 节点 → 40 + 28 + 20 = 88
 *
 * @param config 蝴蝶效应配置
 */
class DeviationCalculator(private val config: ButterflyConfig = ButterflyConfig.DEFAULT) {

    /**
     * 计算单个事件贡献的偏差增量（V1 简化：基于重要度比例）。
     *
     * @param importance 事件重要度 0-100
     * @return 偏差增量 0-100
     */
    fun calculateEventDeviationDelta(importance: Int): Int {
        // 单事件偏差增量 = importance / 100 * eventCountWeight
        // 这样 20 个 importance=100 的事件正好填满 eventCountWeight + importanceWeight
        val delta = (importance.toDouble() / 100.0) * config.deviationEventCountWeight
        return delta.toInt().coerceIn(0, config.deviationMax)
    }

    /**
     * 计算单个事件本身的偏差值（用于事件展示）。
     *
     * @param event 蝴蝶事件
     * @return 偏差值 0-100
     */
    fun calculateEventDeviation(event: ButterflyEvent): Int {
        return calculateEventDeviationDelta(event.importance)
    }

    /**
     * 计算存档累计历史偏差（任务核心：0-100 度量）。
     *
     * @param eventCount 事件总数
     * @param totalImportance 总重要度
     * @param nodeCount 节点总数
     * @return 偏差报告
     */
    fun calculateCumulativeDeviation(
        eventCount: Int,
        totalImportance: Int,
        nodeCount: Int
    ): DeviationReport {
        val maxEvents = config.maxEventsPerSeason.coerceAtLeast(1)
        val maxNodes = config.maxNodesPerSeason.coerceAtLeast(1)
        val maxImportance = maxEvents * 100 // 理论最大重要度

        val eventRatio = (eventCount.toDouble() / maxEvents).coerceIn(0.0, 1.0)
        val importanceRatio = (totalImportance.toDouble() / maxImportance).coerceIn(0.0, 1.0)
        val nodeRatio = (nodeCount.toDouble() / maxNodes).coerceIn(0.0, 1.0)

        val rawDeviation = eventRatio * config.deviationEventCountWeight +
            importanceRatio * config.deviationImportanceWeight +
            nodeRatio * config.deviationNodeCountWeight

        val totalDeviation = rawDeviation.toInt().coerceIn(0, config.deviationMax)

        return DeviationReport(
            totalDeviation = totalDeviation,
            eventCount = eventCount,
            nodeCount = nodeCount,
            totalImportance = totalImportance,
            maxEvents = maxEvents,
            maxNodes = maxNodes
        )
    }

    /**
     * 由事件列表 + 节点数计算偏差（便捷重载）。
     *
     * @param events 蝴蝶事件列表
     * @param nodeCount 节点总数
     * @return 偏差报告
     */
    fun calculateFromEvents(events: List<ButterflyEvent>, nodeCount: Int): DeviationReport {
        val totalImportance = events.sumOf { it.importance }
        return calculateCumulativeDeviation(
            eventCount = events.size,
            totalImportance = totalImportance,
            nodeCount = nodeCount
        )
    }

    /**
     * 获取偏差等级（UI 配色用）。
     *
     * @param score 偏差值 0-100
     * @return 偏差等级
     */
    fun getLevel(score: Int): DeviationLevel = DeviationLevel.fromScore(score)
}
