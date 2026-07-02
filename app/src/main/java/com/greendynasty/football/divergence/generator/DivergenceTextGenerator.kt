package com.greendynasty.football.divergence.generator

import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyEventCategory
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.model.ButterflyImpactType
import com.greendynasty.football.butterfly.model.ButterflyTriggerType
import com.greendynasty.football.butterfly.model.ImpactNodeStatus

/**
 * T21 分歧提示文案生成器（任务 T21.2：基于影响类型模板化生成人类可读文案）。
 *
 * 按 [ButterflyEventCategory] 5 类事件分别生成文案模板：
 * - 转会类："你签走了{球员}，{预期俱乐部}失去了未来的核心"
 * - 比赛类："你改变了{比赛}的结果，{失败方}错失了关键胜利"
 * - 伤病类："{球员}的伤病历史被改写，避免了原本的{伤病}"
 * - 荣誉类："{赛事}的冠军归属发生改变，{新冠军}取代了{原冠军}"
 * - 退役类："{球员}的退役时间被改写，{提前/推迟}了退役"
 *
 * V1 简化范围：
 * - 仅 TRANSFER 类有实际触发（T20 已实现），其余分类为 V2 预留模板
 * - 球员/俱乐部名称 V1 使用 ID 占位（V2 联表 history.player/club）
 * - 文案参数化，所有可调文本集中在 [DivergenceTextConfig]
 *
 * @param config 文案配置（模板与占位符）
 */
class DivergenceTextGenerator(
    private val config: DivergenceTextConfig = DivergenceTextConfig.DEFAULT
) {

    /**
     * 生成分歧提示文案。
     *
     * @param event 蝴蝶事件
     * @param impactNodes 影响节点列表
     * @param sourcePlayerName 源球员名称（V1 可空，使用占位符）
     * @param sourceClubName 源俱乐部名称
     * @param expectedClubName 预期俱乐部名称
     * @return 人类可读的分歧提示文案
     */
    fun generate(
        event: ButterflyEvent,
        impactNodes: List<ButterflyImpactNode>,
        sourcePlayerName: String?,
        sourceClubName: String?,
        expectedClubName: String?
    ): String {
        val playerName = sourcePlayerName ?: config.unknownPlayerPlaceholder
        val fromClub = expectedClubName ?: config.unknownClubPlaceholder
        val toClub = sourceClubName ?: config.unknownClubPlaceholder

        return when (event.category) {
            ButterflyEventCategory.TRANSFER ->
                generateTransferText(event, impactNodes, playerName, fromClub, toClub)
            ButterflyEventCategory.MATCH ->
                generateMatchText(event, impactNodes, playerName)
            ButterflyEventCategory.INJURY ->
                generateInjuryText(event, impactNodes, playerName)
            ButterflyEventCategory.HONOR ->
                generateHonorText(event, impactNodes, playerName)
            ButterflyEventCategory.RETIREMENT ->
                generateRetirementText(event, impactNodes, playerName)
        }
    }

    // ==================== 转会类文案 ====================

    /**
     * 转会类分歧文案（V1 主用）。
     *
     * 模板分支：
     * - HISTORICAL_TRANSFER_BROKEN："你签走了{球员}，{预期俱乐部}失去了未来的核心"
     * - PROSPECT_SIGNED_EARLY："你提前签下了新星{球员}，原本他将在{预期俱乐部}崭露头角"
     * - GENERIC / MANAGER_LEAVE_EARLY：使用通用转会模板
     */
    private fun generateTransferText(
        event: ButterflyEvent,
        nodes: List<ButterflyImpactNode>,
        playerName: String,
        fromClub: String,
        toClub: String
    ): String {
        // 主文案：按触发类型选择模板
        val mainText = when (event.triggerType) {
            ButterflyTriggerType.HISTORICAL_TRANSFER_BROKEN ->
                config.transferBrokenTemplate
                    .replace(config.playerPlaceholder, playerName)
                    .replace(config.expectedClubPlaceholder, fromClub)
                    .replace(config.currentClubPlaceholder, toClub)
            ButterflyTriggerType.PROSPECT_SIGNED_EARLY ->
                config.prospectEarlySignTemplate
                    .replace(config.playerPlaceholder, playerName)
                    .replace(config.expectedClubPlaceholder, fromClub)
                    .replace(config.currentClubPlaceholder, toClub)
            else ->
                config.transferGenericTemplate
                    .replace(config.playerPlaceholder, playerName)
                    .replace(config.expectedClubPlaceholder, fromClub)
                    .replace(config.currentClubPlaceholder, toClub)
        }

        // 追加影响摘要：若有替代转会则补充
        val replacementText = generateReplacementSuffix(nodes, fromClub)
        return if (replacementText.isNotEmpty()) "$mainText，$replacementText" else mainText
    }

    // ==================== 比赛类文案（V2 预留） ====================

    private fun generateMatchText(
        event: ButterflyEvent,
        nodes: List<ButterflyImpactNode>,
        playerName: String
    ): String {
        val opponent = nodes.firstOrNull { it.resultSummary != null }?.resultSummary ?: "对手"
        return config.matchTemplate
            .replace(config.playerPlaceholder, playerName)
            .replace("{对手}", opponent)
    }

    // ==================== 伤病类文案（V2 预留） ====================

    private fun generateInjuryText(
        event: ButterflyEvent,
        nodes: List<ButterflyImpactNode>,
        playerName: String
    ): String {
        val injuryDesc = nodes.firstOrNull { it.resultSummary != null }?.resultSummary ?: "严重伤病"
        return config.injuryTemplate
            .replace(config.playerPlaceholder, playerName)
            .replace("{伤病}", injuryDesc)
    }

    // ==================== 荣誉类文案（V2 预留） ====================

    private fun generateHonorText(
        event: ButterflyEvent,
        nodes: List<ButterflyImpactNode>,
        playerName: String
    ): String {
        val honor = nodes.firstOrNull { it.resultSummary != null }?.resultSummary ?: "冠军"
        return config.honorTemplate
            .replace(config.playerPlaceholder, playerName)
            .replace("{荣誉}", honor)
    }

    // ==================== 退役类文案（V2 预留） ====================

    private fun generateRetirementText(
        event: ButterflyEvent,
        nodes: List<ButterflyImpactNode>,
        playerName: String
    ): String {
        val timing = nodes.firstOrNull { it.resultSummary != null }?.resultSummary ?: "提前"
        return config.retirementTemplate
            .replace(config.playerPlaceholder, playerName)
            .replace("{时机}", timing)
    }

    // ==================== 影响后缀生成 ====================

    /**
     * 生成替代转会影响的后缀文案。
     *
     * - 有 SUCCESS 替代转会节点 → "{预期俱乐部}转而签下了{替代球员}"
     * - 全部 NO_REPLACEMENT → "但{预期俱乐部}并未找到合适的替代"
     * - 无影响节点 → 空字符串
     */
    private fun generateReplacementSuffix(
        nodes: List<ButterflyImpactNode>,
        expectedClub: String
    ): String {
        if (nodes.isEmpty()) return ""

        val replacementNodes = nodes.filter {
            it.impactType == ButterflyImpactType.TRANSFER_REPLACEMENT
        }

        if (replacementNodes.isEmpty()) return ""

        // 有成功的替代转会
        val successReplacement = replacementNodes.firstOrNull {
            it.status == ImpactNodeStatus.SUCCESS
        }
        if (successReplacement != null) {
            val replacementDesc = successReplacement.resultSummary ?: "替代球员"
            return config.replacementFoundSuffix
                .replace(config.expectedClubPlaceholder, expectedClub)
                .replace("{替代球员}", replacementDesc)
        }

        // 全部未找到替代
        val allNoReplacement = replacementNodes.all {
            it.status == ImpactNodeStatus.NO_REPLACEMENT
        }
        if (allNoReplacement) {
            return config.noReplacementSuffix
                .replace(config.expectedClubPlaceholder, expectedClub)
        }

        return ""
    }

    /**
     * 生成原路径描述（用于 DivergenceLog.originalPath）。
     *
     * @param event 蝴蝶事件
     * @param expectedClubName 预期俱乐部名称
     * @param sourcePlayerName 源球员名称
     * @return 原路径描述（如"梅西 → 巴塞罗那"）
     */
    fun generateOriginalPath(
        event: ButterflyEvent,
        expectedClubName: String?,
        sourcePlayerName: String?
    ): String {
        val playerName = sourcePlayerName ?: config.unknownPlayerPlaceholder
        val clubName = expectedClubName ?: config.unknownClubPlaceholder
        return "$playerName → $clubName"
    }

    /**
     * 生成当前路径描述（用于 DivergenceLog.currentPath）。
     *
     * @param event 蝴蝶事件
     * @param sourceClubName 当前俱乐部名称
     * @param sourcePlayerName 源球员名称
     * @return 当前路径描述（如"梅西 → 玩家俱乐部"）
     */
    fun generateCurrentPath(
        event: ButterflyEvent,
        sourceClubName: String?,
        sourcePlayerName: String?
    ): String {
        val playerName = sourcePlayerName ?: config.unknownPlayerPlaceholder
        val clubName = sourceClubName ?: config.unknownClubPlaceholder
        return "$playerName → $clubName"
    }
}

/**
 * T21 分歧文案配置（所有模板与占位符集中管理，便于调参）。
 *
 * 占位符约定：
 * - {player}：源球员名称
 * - {expectedClub}：原路径预期俱乐部
 * - {currentClub}：当前路径实际俱乐部
 * - {对手} / {伤病} / {荣誉} / {时机} / {替代球员}：分类专用占位符
 *
 * @param transferBrokenTemplate 转会被打断模板
 * @param prospectEarlySignTemplate 新星提前签约模板
 * @param transferGenericTemplate 通用转会模板
 * @param matchTemplate 比赛类模板（V2）
 * @param injuryTemplate 伤病类模板（V2）
 * @param honorTemplate 荣誉类模板（V2）
 * @param retirementTemplate 退役类模板（V2）
 * @param replacementFoundSuffix 找到替代的后缀
 * @param noReplacementSuffix 未找到替代的后缀
 * @param playerPlaceholder 球员占位符
 * @param expectedClubPlaceholder 预期俱乐部占位符
 * @param currentClubPlaceholder 当前俱乐部占位符
 * @param unknownPlayerPlaceholder 未知球员占位文本
 * @param unknownClubPlaceholder 未知俱乐部占位文本
 */
data class DivergenceTextConfig(
    val transferBrokenTemplate: String = "你签走了{player}，{expectedClub}失去了未来的核心",
    val prospectEarlySignTemplate: String = "你提前签下了新星{player}，原本他将在{expectedClub}崭露头角",
    val transferGenericTemplate: String = "{player}的转会路径发生分歧，从{expectedClub}转向{currentClub}",
    val matchTemplate: String = "你改变了{player}所在比赛的结果，{对手}错失了关键胜利",
    val injuryTemplate: String = "{player}的伤病历史被改写，避免了原本的{伤病}",
    val honorTemplate: String = "{player}所在的赛事冠军归属发生改变，{荣誉}易主",
    val retirementTemplate: String = "{player}的退役时间被改写，{时机}了退役",
    val replacementFoundSuffix: String = "{expectedClub}转而签下了{替代球员}",
    val noReplacementSuffix: String = "但{expectedClub}并未找到合适的替代",
    val playerPlaceholder: String = "{player}",
    val expectedClubPlaceholder: String = "{expectedClub}",
    val currentClubPlaceholder: String = "{currentClub}",
    val unknownPlayerPlaceholder: String = "该球员",
    val unknownClubPlaceholder: String = "该俱乐部"
) {
    companion object {
        /** 默认文案配置。 */
        val DEFAULT = DivergenceTextConfig()
    }
}
