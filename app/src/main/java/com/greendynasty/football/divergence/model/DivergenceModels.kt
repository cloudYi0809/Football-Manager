package com.greendynasty.football.divergence.model

import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyEventCategory
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.model.ButterflyImpactType
import com.greendynasty.football.butterfly.model.ButterflyTriggerType
import com.greendynasty.football.butterfly.model.ImpactNodeStatus
import java.time.LocalDate

/**
 * T21 历史分歧日志（任务 T21.1：分歧日志数据结构）。
 *
 * 复用 T20 [ButterflyEvent] + [ButterflyImpactNode]，扩展为分歧日志：
 * - [originalPath]：原历史路径（球员原本应该去的俱乐部 / 原本应该发生的事件）
 * - [currentPath]：当前路径（玩家干预后的实际走向）
 * - [impactSummary]：影响摘要（基于影响节点聚合生成的简明描述）
 * - [hasMajorReplacement]：是否产生了重大替代（V0.2 §七：允许"未产生重大替代"）
 *
 * 与 [ButterflyEvent] 的关系：组合而非继承（Kotlin data class 不支持继承）。
 * [event] 字段保留完整的蝴蝶事件信息，本类仅追加分歧视角的展示字段。
 *
 * @param event 源蝴蝶事件（T20 持久化数据）
 * @param impactNodes 该事件的影响节点列表
 * @param originalPath 原历史路径描述（如"梅西 → 巴塞罗那"）
 * @param currentPath 当前路径描述（如"梅西 → 玩家俱乐部"）
 * @param impactSummary 影响摘要（聚合影响节点生成）
 * @param hasMajorReplacement 是否产生重大替代（影响节点中存在 status=SUCCESS 的替代转会节点）
 * @param divergenceText 分歧提示文案（由 [com.greendynasty.football.divergence.generator.DivergenceTextGenerator] 生成）
 */
data class DivergenceLog(
    val event: ButterflyEvent,
    val impactNodes: List<ButterflyImpactNode>,
    val originalPath: String,
    val currentPath: String,
    val impactSummary: String,
    val hasMajorReplacement: Boolean,
    val divergenceText: String
) {
    /** 事件唯一标识（透传 event.eventId）。 */
    val eventId: String get() = event.eventId

    /** 存档 UUID（透传 event.saveId）。 */
    val saveId: String get() = event.saveId

    /** 触发日期（透传 event.triggerDate）。 */
    val triggerDate: LocalDate get() = event.triggerDate

    /** 事件分类（透传 event.category）。 */
    val category: ButterflyEventCategory get() = event.category

    /** 触发类型（透传 event.triggerType）。 */
    val triggerType: ButterflyTriggerType get() = event.triggerType

    /** 重要度（透传 event.importance）。 */
    val importance: Int get() = event.importance

    /** 事件摘要（透传 event.summary）。 */
    val summary: String get() = event.summary

    companion object {
        /**
         * 从蝴蝶事件 + 影响节点构建分歧日志（不包含文案生成，需外部调用 [DivergenceTextGenerator] 补充）。
         *
         * @param event 蝴蝶事件
         * @param impactNodes 影响节点列表
         * @param originalPath 原路径描述（可空，默认从 expectedClubId 推导）
         * @param currentPath 当前路径描述（可空，默认从 sourceClubId 推导）
         * @param divergenceText 分歧提示文案
         * @return 分歧日志
         */
        fun fromButterfly(
            event: ButterflyEvent,
            impactNodes: List<ButterflyImpactNode>,
            originalPath: String,
            currentPath: String,
            divergenceText: String
        ): DivergenceLog {
            // 判断是否有重大替代：影响节点中存在 status=SUCCESS 的替代转会节点
            val hasMajorReplacement = impactNodes.any { node ->
                node.status == ImpactNodeStatus.SUCCESS &&
                    node.impactType == ButterflyImpactType.TRANSFER_REPLACEMENT
            }

            // 聚合影响摘要
            val impactSummary = buildImpactSummary(impactNodes)

            return DivergenceLog(
                event = event,
                impactNodes = impactNodes,
                originalPath = originalPath,
                currentPath = currentPath,
                impactSummary = impactSummary,
                hasMajorReplacement = hasMajorReplacement,
                divergenceText = divergenceText
            )
        }

        /**
         * 聚合影响节点生成影响摘要。
         *
         * 策略：
         * - 若无影响节点 → "未产生连锁影响"
         * - 若有节点但全部 NO_REPLACEMENT → "历史分歧未产生重大替代"
         * - 否则 → 拼接各节点 resultSummary
         */
        private fun buildImpactSummary(nodes: List<ButterflyImpactNode>): String {
            if (nodes.isEmpty()) return "未产生连锁影响"
            val noReplacement = nodes.all { it.status == ImpactNodeStatus.NO_REPLACEMENT }
            if (noReplacement) return "历史分歧未产生重大替代"
            return nodes
                .filter { it.resultSummary != null }
                .joinToString("；") { it.resultSummary!! }
                .ifEmpty { "产生了 ${nodes.size} 个影响节点" }
        }
    }
}

/**
 * T21 时间线视图项（任务 T21.3：时间线 UI 展示单元）。
 *
 * 在 [DivergenceLog] 基础上追加 UI 展示所需的名称解析与格式化字段。
 *
 * @param log 源分歧日志
 * @param sourcePlayerName 源球员名称（V1 简化：ID 占位，V2 联表 history.player）
 * @param sourceClubName 源俱乐部名称
 * @param expectedClubName 预期俱乐部名称
 */
data class DivergenceTimelineItem(
    val log: DivergenceLog,
    val sourcePlayerName: String?,
    val sourceClubName: String?,
    val expectedClubName: String?
) {
    /** 分类显示文本。 */
    val categoryDisplay: String get() = log.category.display

    /** 重要度等级（用于筛选与配色）。 */
    val importanceLevel: ImportanceLevel get() = ImportanceLevel.fromScore(log.importance)

    /** 是否已归档。 */
    val isArchived: Boolean get() = log.event.status.code == "archived"

    /** 格式化的触发日期文本。 */
    val formattedDate: String get() = log.triggerDate.toString()
}

/**
 * T21 重要度等级（用于时间线筛选与 UI 配色）。
 */
enum class ImportanceLevel(val display: String, val minScore: Int) {
    /** 0-30：低重要度。 */
    LOW("低", 0),
    /** 31-70：中重要度。 */
    MEDIUM("中", 31),
    /** 71-100：高重要度。 */
    HIGH("高", 71);

    companion object {
        fun fromScore(score: Int): ImportanceLevel = when {
            score >= HIGH.minScore -> HIGH
            score >= MEDIUM.minScore -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * T21 时间线筛选条件（任务 T21.3：按类型 / 按重要度筛选）。
 *
 * @param category 分类筛选（null = 全部）
 * @param importanceLevel 重要度筛选（null = 全部）
 * @param onlyWithReplacement 是否仅显示有重大替代的分歧（null = 全部）
 */
data class DivergenceFilter(
    val category: ButterflyEventCategory? = null,
    val importanceLevel: ImportanceLevel? = null,
    val onlyWithReplacement: Boolean? = null
) {
    companion object {
        /** 默认筛选（无限制）。 */
        val NONE = DivergenceFilter()
    }
}

/**
 * T21 "历史分歧未产生重大替代"记录（任务 T21.5）。
 *
 * 记录蝴蝶事件触发后未产生后续影响（无替代转会 / 无财政调整等）的情况。
 * V0.2 §七：允许"未产生重大替代"，玩家仍需理解为何没有连锁反应。
 *
 * @param eventId 关联的蝴蝶事件 ID
 * @param triggerDate 触发日期
 * @param summary 事件摘要
 * @param reason 未产生重大替代的原因描述
 * @param checkedNodeCount 检查的影响节点数
 */
data class NoReplacementRecord(
    val eventId: String,
    val triggerDate: LocalDate,
    val summary: String,
    val reason: String,
    val checkedNodeCount: Int
)

/**
 * T21 分歧归档统计（归档结果汇总）。
 *
 * @param seasonId 归档的赛季 ID
 * @param totalEvents 归档的事件总数
 * @param archivedEvents 实际归档的事件数
 * @param noReplacementCount "未产生重大替代"记录数
 * @param withReplacementCount "有重大替代"记录数
 * @param archivedAt 归档时间
 */
data class DivergenceArchiveSummary(
    val seasonId: Int,
    val totalEvents: Int,
    val archivedEvents: Int,
    val noReplacementCount: Int,
    val withReplacementCount: Int,
    val archivedAt: String
)
