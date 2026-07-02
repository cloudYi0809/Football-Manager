package com.greendynasty.football.butterfly.model

import com.greendynasty.football.data.save.entity.ButterflyEventEntity
import com.greendynasty.football.data.save.entity.ButterflyImpactNodeEntity
import java.time.LocalDate
import java.util.UUID

/**
 * T20 蝴蝶事件触发类型（V0.2 06 §二.1）。
 *
 * V1 范围（T20 方案 §十四）：
 * - HISTORICAL_TRANSFER_BROKEN：关键历史转会被打断（玩家签走历史目标球员）
 * - PROSPECT_SIGNED_EARLY：历史新星被提前签约（T15 集成）
 * - MANAGER_LEAVE_EARLY：豪门主帅提前离任（V1 检测骨架，实际触发由 V2 实现）
 */
enum class ButterflyTriggerType(val code: String) {
    /** 关键历史转会被打断（V0.2 §二.1）。 */
    HISTORICAL_TRANSFER_BROKEN("transfer_broken"),

    /** 历史新星提前签约（V0.2 §二.1，T15 集成）。 */
    PROSPECT_SIGNED_EARLY("prospect_signed_early"),

    /** 豪门主帅提前离任（V0.2 §二.1）。 */
    MANAGER_LEAVE_EARLY("manager_leave_early"),

    /** 通用触发（T15 ButterflyEffectMarker 写入的 EARLY_SIGN / AI_SIGN / PATH_INTERRUPTED）。 */
    GENERIC("generic");

    companion object {
        /** 从 DAO 字符串解析触发类型，未知值归类为 GENERIC。 */
        fun fromCode(code: String): ButterflyTriggerType =
            values().firstOrNull { it.code == code } ?: GENERIC
    }
}

/**
 * T20 蝴蝶事件分类（任务要求 5 类：转会 / 比赛 / 伤病 / 荣誉 / 退役）。
 *
 * 与 [ButterflyTriggerType] 区别：triggerType 描述"如何触发"，category 描述"影响哪个维度"。
 * V1 仅 TRANSFER 类有实际触发，其余分类为 V2 预留。
 */
enum class ButterflyEventCategory(val code: String, val display: String) {
    /** 转会类：转会被打断 / 提前签约 / 替代转会。 */
    TRANSFER("transfer", "转会类"),

    /** 比赛类：历史比赛结果被改变（V2）。 */
    MATCH("match", "比赛类"),

    /** 伤病类：历史伤病被避免或新增（V2）。 */
    INJURY("injury", "伤病类"),

    /** 荣誉类：冠军 / 个人奖项归属改变（V2）。 */
    HONOR("honor", "荣誉类"),

    /** 退役类：球员提前 / 推迟退役（V2）。 */
    RETIREMENT("retirement", "退役类");

    companion object {
        /** 由触发类型推导事件分类。 */
        fun fromTriggerType(type: ButterflyTriggerType): ButterflyEventCategory = when (type) {
            ButterflyTriggerType.HISTORICAL_TRANSFER_BROKEN,
            ButterflyTriggerType.PROSPECT_SIGNED_EARLY,
            ButterflyTriggerType.GENERIC -> TRANSFER
            ButterflyTriggerType.MANAGER_LEAVE_EARLY -> TRANSFER
        }
    }
}

/**
 * T20 蝴蝶事件状态（V0.2 06 §三）。
 */
enum class ButterflyEventStatus(val code: String, val display: String) {
    /** 待处理（T15 写入后初始状态）。 */
    PENDING("pending", "待处理"),

    /** 处理中（T20 正在生成影响节点）。 */
    PROCESSING("processing", "处理中"),

    /** 已完成（影响节点已生成，偏差已更新）。 */
    COMPLETED("completed", "已完成"),

    /** 已归档（赛季归档后转入）。 */
    ARCHIVED("archived", "已归档");

    companion object {
        fun fromCode(code: String): ButterflyEventStatus =
            values().firstOrNull { it.code == code } ?: PENDING
    }
}

/**
 * T20 蝴蝶影响类型（V0.2 06 §四，5 种影响类型）。
 */
enum class ButterflyImpactType(val code: String, val display: String) {
    /** 1. 替代转会（V0.2 §四.1）。 */
    TRANSFER_REPLACEMENT("transfer_replacement", "替代转会"),

    /** 2. 财政影响（V0.2 §四.2）。 */
    FINANCIAL_IMPACT("financial_impact", "财政影响"),

    /** 3. 成长路径改变（V0.2 §四.3）。 */
    CAREER_PATH_SHIFT("career_path_shift", "成长路径改变"),

    /** 4. 俱乐部策略变化（V0.2 §四.4）。 */
    CLUB_STRATEGY_SHIFT("club_strategy_shift", "俱乐部策略变化"),

    /** 5. 国家队路线变化（V0.2 §四.5，V1 仅记录趋势）。 */
    NATIONAL_TEAM_SHIFT("national_team_shift", "国家队趋势");

    companion object {
        fun fromCode(code: String): ButterflyImpactType =
            values().firstOrNull { it.code == code } ?: TRANSFER_REPLACEMENT
    }
}

/**
 * T20 影响节点状态（V0.2 06 §三）。
 */
enum class ImpactNodeStatus(val code: String) {
    /** 待处理。 */
    PENDING("pending"),

    /** 成功生成影响（替代转会成立 / 财政调整生效等）。 */
    SUCCESS("success"),

    /** 未找到合适替代（V0.2 §七：允许"未产生重大替代"）。 */
    NO_REPLACEMENT("no_replacement"),

    /** 已归档。 */
    ARCHIVED("archived");

    companion object {
        fun fromCode(code: String): ImpactNodeStatus =
            values().firstOrNull { it.code == code } ?: PENDING
    }
}

/**
 * T20 蝴蝶事件领域模型（V0.2 06 §三）。
 *
 * 与 [ButterflyEventEntity] 区别：Entity 是 Room 持久化对象（无类型安全枚举），
 * 本模型是领域层使用的类型安全版本，封装触发原因 / 影响范围 / 偏差值等业务字段。
 *
 * @param eventId 事件唯一标识（UUID）
 * @param saveId 存档 UUID
 * @param triggerType 触发类型
 * @param category 事件分类（5 类之一）
 * @param sourcePlayerId 触发球员 ID（可空，主帅离任类无）
 * @param sourceClubId 触发俱乐部 ID
 * @param expectedClubId 原本应该去的俱乐部 ID
 * @param triggerDate 触发日期
 * @param importance 重要度 0-100
 * @param impactBudget 该事件的影响预算
 * @param maxDepth 最大传播深度（V1 固定 1，单层）
 * @param status 事件状态
 * @param summary 事件摘要
 * @param triggerReason 触发原因描述（任务要求字段）
 * @param impactScope 影响范围描述（任务要求字段）
 * @param deviationValue 该事件贡献的偏差值 0-100
 */
data class ButterflyEvent(
    val eventId: String,
    val saveId: String,
    val triggerType: ButterflyTriggerType,
    val category: ButterflyEventCategory,
    val sourcePlayerId: Int?,
    val sourceClubId: Int?,
    val expectedClubId: Int?,
    val triggerDate: LocalDate,
    val importance: Int,
    val impactBudget: Int,
    val maxDepth: Int = 1,
    val status: ButterflyEventStatus = ButterflyEventStatus.PENDING,
    val summary: String,
    val triggerReason: String = summary,
    val impactScope: String = "",
    val deviationValue: Int = 0
) {

    /** 转换为 Room Entity 持久化。 */
    fun toEntity(): ButterflyEventEntity = ButterflyEventEntity(
        eventId = eventId,
        saveId = saveId,
        triggerType = triggerType.code,
        sourcePlayerId = sourcePlayerId,
        sourceClubId = sourceClubId,
        expectedClubId = expectedClubId,
        triggerDate = triggerDate.toString(),
        importance = importance,
        impactBudget = impactBudget,
        maxDepth = maxDepth,
        status = status.code,
        summary = summary
    )

    companion object {
        /** 从 Room Entity 转换为领域模型。 */
        fun fromEntity(e: ButterflyEventEntity): ButterflyEvent {
            val triggerType = ButterflyTriggerType.fromCode(e.triggerType)
            return ButterflyEvent(
                eventId = e.eventId,
                saveId = e.saveId,
                triggerType = triggerType,
                category = ButterflyEventCategory.fromTriggerType(triggerType),
                sourcePlayerId = e.sourcePlayerId,
                sourceClubId = e.sourceClubId,
                expectedClubId = e.expectedClubId,
                triggerDate = runCatching { LocalDate.parse(e.triggerDate) }.getOrDefault(LocalDate.now()),
                importance = e.importance,
                impactBudget = e.impactBudget,
                maxDepth = e.maxDepth,
                status = ButterflyEventStatus.fromCode(e.status),
                summary = e.summary ?: ""
            )
        }

        /**
         * 创建新事件（自动生成 UUID + 默认状态）。
         *
         * @param saveId 存档 UUID
         * @param triggerType 触发类型
         * @param sourcePlayerId 触发球员 ID
         * @param sourceClubId 触发俱乐部 ID
         * @param expectedClubId 原本应该去的俱乐部
         * @param triggerDate 触发日期
         * @param importance 重要度 0-100
         * @param impactBudget 影响预算
         * @param summary 事件摘要
         * @param triggerReason 触发原因
         */
        fun create(
            saveId: String,
            triggerType: ButterflyTriggerType,
            sourcePlayerId: Int?,
            sourceClubId: Int?,
            expectedClubId: Int?,
            triggerDate: LocalDate,
            importance: Int,
            impactBudget: Int,
            summary: String,
            triggerReason: String = summary
        ): ButterflyEvent = ButterflyEvent(
            eventId = UUID.randomUUID().toString(),
            saveId = saveId,
            triggerType = triggerType,
            category = ButterflyEventCategory.fromTriggerType(triggerType),
            sourcePlayerId = sourcePlayerId,
            sourceClubId = sourceClubId,
            expectedClubId = expectedClubId,
            triggerDate = triggerDate,
            importance = importance.coerceIn(0, 100),
            impactBudget = impactBudget,
            maxDepth = 1, // V1 单层
            status = ButterflyEventStatus.PENDING,
            summary = summary,
            triggerReason = triggerReason
        )
    }
}

/**
 * T20 蝴蝶影响节点领域模型（V0.2 06 §三）。
 *
 * @param nodeId 节点唯一标识（UUID）
 * @param eventId 所属事件 ID
 * @param depth 传播深度（V1 仅 0）
 * @param impactType 影响类型（5 种之一）
 * @param targetClubId 受影响俱乐部 ID
 * @param targetPlayerId 受影响球员 ID
 * @param impactStrength 衰减后强度（0.0-100.0）
 * @param status 节点状态
 * @param resultSummary 影响结果摘要
 */
data class ButterflyImpactNode(
    val nodeId: String,
    val eventId: String,
    val depth: Int,
    val impactType: ButterflyImpactType,
    val targetClubId: Int?,
    val targetPlayerId: Int?,
    val impactStrength: Double,
    val status: ImpactNodeStatus = ImpactNodeStatus.PENDING,
    val resultSummary: String? = null
) {

    /** 转换为 Room Entity 持久化。 */
    fun toEntity(): ButterflyImpactNodeEntity = ButterflyImpactNodeEntity(
        nodeId = nodeId,
        eventId = eventId,
        depth = depth,
        impactType = impactType.code,
        targetClubId = targetClubId,
        targetPlayerId = targetPlayerId,
        impactStrength = impactStrength,
        status = status.code,
        resultSummary = resultSummary
    )

    companion object {
        /** 从 Room Entity 转换为领域模型。 */
        fun fromEntity(e: ButterflyImpactNodeEntity): ButterflyImpactNode = ButterflyImpactNode(
            nodeId = e.nodeId,
            eventId = e.eventId,
            depth = e.depth,
            impactType = ButterflyImpactType.fromCode(e.impactType),
            targetClubId = e.targetClubId,
            targetPlayerId = e.targetPlayerId,
            impactStrength = e.impactStrength,
            status = ImpactNodeStatus.fromCode(e.status),
            resultSummary = e.resultSummary
        )

        /** 创建新节点（自动生成 UUID）。 */
        fun create(
            eventId: String,
            depth: Int,
            impactType: ButterflyImpactType,
            targetClubId: Int?,
            targetPlayerId: Int?,
            impactStrength: Double
        ): ButterflyImpactNode = ButterflyImpactNode(
            nodeId = UUID.randomUUID().toString(),
            eventId = eventId,
            depth = depth,
            impactType = impactType,
            targetClubId = targetClubId,
            targetPlayerId = targetPlayerId,
            impactStrength = impactStrength,
            status = ImpactNodeStatus.PENDING
        )
    }
}

/**
 * T20 蝴蝶事件处理结果（V0.2 06 §三 ButterflyResult）。
 *
 * @param event 触发的蝴蝶事件
 * @param impactNodes 生成的影响节点列表
 * @param budgetUsed 消耗的预算（节点数）
 * @param budgetRemaining 剩余预算
 * @param deviationDelta 该事件贡献的偏差增量
 */
data class ButterflyResult(
    val event: ButterflyEvent,
    val impactNodes: List<ButterflyImpactNode>,
    val budgetUsed: Int,
    val budgetRemaining: Int,
    val deviationDelta: Int
)

/**
 * T20 历史偏差度量报告（任务要求 4：量化历史偏差 0-100）。
 *
 * @param totalDeviation 总偏差值 0-100
 * @param eventCount 事件总数
 * @param nodeCount 节点总数
 * @param totalImportance 总重要度
 * @param maxEvents 上限事件数
 * @param maxNodes 上限节点数
 * @param level 偏差等级（用于 UI 仪表盘配色）
 */
data class DeviationReport(
    val totalDeviation: Int,
    val eventCount: Int,
    val nodeCount: Int,
    val totalImportance: Int,
    val maxEvents: Int,
    val maxNodes: Int,
    val level: DeviationLevel = DeviationLevel.fromScore(totalDeviation)
)

/**
 * T20 偏差等级（UI 仪表盘配色用）。
 */
enum class DeviationLevel(val display: String, val colorHint: String) {
    /** 0-20：历史基本未偏离。 */
    MINIMAL("微弱", "green"),
    /** 21-40：少量偏离。 */
    LOW("轻度", "lime"),
    /** 41-60：明显偏离。 */
    MODERATE("中度", "amber"),
    /** 61-80：严重偏离。 */
    HIGH("严重", "orange"),
    /** 81-100：历史已大幅改写。 */
    CRITICAL("极端", "red");

    companion object {
        fun fromScore(score: Int): DeviationLevel = when {
            score <= 20 -> MINIMAL
            score <= 40 -> LOW
            score <= 60 -> MODERATE
            score <= 80 -> HIGH
            else -> CRITICAL
        }
    }
}
