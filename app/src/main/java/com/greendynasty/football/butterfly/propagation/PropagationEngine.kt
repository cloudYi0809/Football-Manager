package com.greendynasty.football.butterfly.propagation

import com.greendynasty.football.butterfly.config.ButterflyConfig
import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.model.ButterflyImpactType
import com.greendynasty.football.butterfly.model.ButterflyTriggerType
import com.greendynasty.football.butterfly.model.ImpactNodeStatus
import kotlin.math.pow

/**
 * T20 蝴蝶影响衰减计算器（V0.2 06 §五 影响衰减公式）。
 *
 * 公式：strength(depth) = base_importance * decay_rate ^ depth
 * 默认 decay_rate = 0.55（V0.2 §五）
 *
 * 例（base=100）：
 * - depth 0：100.0
 * - depth 1：55.0
 * - depth 2：30.25
 * - depth 3：16.64
 *
 * 低于 [ButterflyConfig.minImpactThreshold]（默认 15）的影响不生成具体事件，只记录为趋势修正。
 *
 * @param config 蝴蝶效应配置
 */
class ImpactDecayCalculator(private val config: ButterflyConfig = ButterflyConfig.DEFAULT) {

    /**
     * 计算指定深度的衰减后影响强度。
     *
     * @param baseImportance 基础重要度 0-100
     * @param depth 传播深度（0 = 直接影响）
     * @return 衰减后强度
     */
    fun calculateStrength(baseImportance: Int, depth: Int): Double {
        return baseImportance.toDouble() * config.decayRate.pow(depth.toDouble())
    }

    /**
     * 判断该强度是否值得生成影响节点。
     *
     * @param strength 衰减后强度
     * @return true 表示应生成影响节点
     */
    fun shouldGenerateImpact(strength: Double): Boolean {
        return strength >= config.minImpactThreshold
    }

    /**
     * 判断是否超过最大深度。
     *
     * @param depth 当前深度
     * @return true 表示超过最大深度，应停止传播
     */
    fun isDepthExceeded(depth: Int): Boolean {
        return depth > config.maxDepth
    }
}

/**
 * T20 蝴蝶因果链传播引擎（V0.2 06 §四 影响生成）。
 *
 * V1 简化范围（严格遵循 T20 方案 §十四 V1 范围明确）：
 * - **仅生成单层影响（depth=0）**，不递归到 depth 1+
 * - 按 [ButterflyTriggerType] 决定 depth=0 的影响类型组合
 * - 不实际修改俱乐部财政 / 球员能力 / 俱乐部策略（V2 实现）
 * - 仅生成影响节点记录 + resultSummary 文案，用于 UI 展示
 *
 * 完整因果链（depth 1-3 递归 + 替代转会 + 回退机制）由 V2 实现。
 *
 * @param config 蝴蝶效应配置
 * @param decayCalculator 衰减计算器
 */
class PropagationEngine(
    private val config: ButterflyConfig = ButterflyConfig.DEFAULT,
    private val decayCalculator: ImpactDecayCalculator = ImpactDecayCalculator(config)
) {

    /**
     * V1 单层影响生成（任务要求 3：因果链传播 V1 简化为单层影响）。
     *
     * 行为：
     * 1. 计算 depth=0 衰减强度（= base importance）
     * 2. 按 [ButterflyTriggerType] 决定影响类型组合
     * 3. 每事件最多生成 [ButterflyConfig.maxDirectImpactsPerEvent] 个节点
     * 4. 节点状态 SUCCESS（V1 简化：不实际执行影响，仅记录）
     *
     * @param event 蝴蝶事件
     * @return 生成的影响节点列表（depth=0）
     */
    fun propagate(event: ButterflyEvent): List<ButterflyImpactNode> {
        // 1. 深度检查（V1 固定 depth=0，不会超限）
        val depth = 0
        if (decayCalculator.isDepthExceeded(depth)) return emptyList()

        // 2. 计算衰减强度
        val strength = decayCalculator.calculateStrength(event.importance, depth)
        if (!decayCalculator.shouldGenerateImpact(strength)) return emptyList()

        // 3. 确定影响类型组合（V0.2 §四 影响类型分层）
        val impactTypes = determineImpactTypes(event, depth)

        // 4. 限制单事件直接影响数量
        val limitedTypes = impactTypes.take(config.maxDirectImpactsPerEvent)

        // 5. 生成影响节点
        return limitedTypes.map { impactType ->
            val node = ButterflyImpactNode.create(
                eventId = event.eventId,
                depth = depth,
                impactType = impactType,
                targetClubId = determineTargetClub(event, impactType),
                targetPlayerId = determineTargetPlayer(event, impactType),
                impactStrength = strength
            )
            // V1 简化：不实际执行影响，直接标记 SUCCESS + 生成文案
            node.copy(
                status = ImpactNodeStatus.SUCCESS,
                resultSummary = buildImpactSummary(event, impactType, strength)
            )
        }
    }

    /**
     * V0.2 §四 影响类型分层（depth=0 层）。
     *
     * 第一层影响类型由触发类型决定：
     * - HISTORICAL_TRANSFER_BROKEN → 替代转会 + 财政影响
     * - PROSPECT_SIGNED_EARLY → 成长路径改变 + 财政影响
     * - MANAGER_LEAVE_EARLY → 俱乐部策略变化
     * - GENERIC → 成长路径改变（T15 集成默认）
     */
    private fun determineImpactTypes(
        event: ButterflyEvent,
        depth: Int
    ): List<ButterflyImpactType> {
        if (depth != 0) return emptyList() // V1 仅 depth=0
        return when (event.triggerType) {
            ButterflyTriggerType.HISTORICAL_TRANSFER_BROKEN -> listOf(
                ButterflyImpactType.TRANSFER_REPLACEMENT,
                ButterflyImpactType.FINANCIAL_IMPACT
            )
            ButterflyTriggerType.PROSPECT_SIGNED_EARLY -> listOf(
                ButterflyImpactType.CAREER_PATH_SHIFT,
                ButterflyImpactType.FINANCIAL_IMPACT
            )
            ButterflyTriggerType.MANAGER_LEAVE_EARLY -> listOf(
                ButterflyImpactType.CLUB_STRATEGY_SHIFT
            )
            ButterflyTriggerType.GENERIC -> listOf(
                ButterflyImpactType.CAREER_PATH_SHIFT
            )
        }
    }

    /**
     * 确定影响节点的目标俱乐部（V1 简化：取事件的 expectedClubId）。
     */
    private fun determineTargetClub(
        event: ButterflyEvent,
        impactType: ButterflyImpactType
    ): Int? {
        return when (impactType) {
            ButterflyImpactType.TRANSFER_REPLACEMENT,
            ButterflyImpactType.FINANCIAL_IMPACT,
            ButterflyImpactType.CLUB_STRATEGY_SHIFT -> event.expectedClubId ?: event.sourceClubId
            ButterflyImpactType.CAREER_PATH_SHIFT,
            ButterflyImpactType.NATIONAL_TEAM_SHIFT -> event.sourceClubId
        }
    }

    /**
     * 确定影响节点的目标球员（V1 简化：取事件的 sourcePlayerId）。
     */
    private fun determineTargetPlayer(
        event: ButterflyEvent,
        impactType: ButterflyImpactType
    ): Int? {
        return when (impactType) {
            ButterflyImpactType.CAREER_PATH_SHIFT,
            ButterflyImpactType.NATIONAL_TEAM_SHIFT -> event.sourcePlayerId
            ButterflyImpactType.TRANSFER_REPLACEMENT,
            ButterflyImpactType.FINANCIAL_IMPACT,
            ButterflyImpactType.CLUB_STRATEGY_SHIFT -> null
        }
    }

    /**
     * 生成影响结果文案（V0.2 §八 玩家可见反馈文案模板）。
     */
    private fun buildImpactSummary(
        event: ButterflyEvent,
        impactType: ButterflyImpactType,
        strength: Double
    ): String {
        val clubName = "俱乐部#${event.expectedClubId ?: event.sourceClubId ?: -1}"
        val playerName = event.sourcePlayerId?.let { "球员#$it" } ?: "相关球员"
        val amount = (strength * 1_000_000).toInt()
        return when (impactType) {
            ButterflyImpactType.TRANSFER_REPLACEMENT ->
                "$clubName 转向寻找替代球员（强度 ${strength.toInt()}）"
            ButterflyImpactType.FINANCIAL_IMPACT ->
                "$clubName 缺少原本的转会收入，预算下调约 $amount 欧元"
            ButterflyImpactType.CAREER_PATH_SHIFT ->
                "$playerName 的成长路径已偏离真实历史（强度 ${strength.toInt()}）"
            ButterflyImpactType.CLUB_STRATEGY_SHIFT ->
                "$clubName 调整了俱乐部策略（青训偏好 +10，巨星偏好 -5）"
            ButterflyImpactType.NATIONAL_TEAM_SHIFT ->
                "国家队路线出现微小修正（强度 ${strength.toInt()}，V1 仅记录趋势）"
        }
    }
}
