package com.greendynasty.football.match.core

import com.greendynasty.football.match.api.MatchInput
import com.greendynasty.football.match.api.PlayerState
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.match.api.TeamSheet
import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.model.EventType
import com.greendynasty.football.match.model.MatchEvent
import com.greendynasty.football.match.model.TeamSide
import com.greendynasty.football.match.model.XGResult
import com.greendynasty.football.match.template.StarTemplateRegistry
import kotlin.random.Random

/**
 * Layer 4 事件生成层（V0.2 04 §七）
 *
 * 将比赛分为 18 个常规 tick（每 5 分钟一个）+ 1-2 个补时 tick，
 * 每个 tick 根据控球/攻击概率生成事件，并预分配进球时刻。
 *
 * 球星模板在关键 tick 触发特殊事件（V0.2 04 §九），
 * 严格遵循"球星模板不直接加进球，只改事件权重"铁律。
 */
class EventLayer(
    private val config: MatchConfig = MatchConfig.DEFAULT,
    private val starTemplateRegistry: StarTemplateRegistry = StarTemplateRegistry()
) {

    /** 关键 tick 间隔（每 6 个 tick，即约 30 分钟，球星触发特殊事件） */
    private val starTickInterval = 6

    /**
     * 生成全场事件流。
     *
     * @param input 比赛输入
     * @param xg xG 结果（用于控球率推导与事件质量参考）
     * @param score 泊松层产出的比分 (homeGoals, awayGoals)
     * @param random 可复现随机源
     */
    fun generate(
        input: MatchInput,
        xg: XGResult,
        score: Pair<Int, Int>,
        random: Random
    ): List<MatchEvent> {
        val events = mutableListOf<MatchEvent>()

        // 控球强度参考：从 xg 的 controlRatio 反推双方控制力
        val homeControlStrength = deriveControlStrength(xg.homeModifiers.controlRatio)
        val awayControlStrength = deriveControlStrength(xg.awayModifiers.controlRatio)

        // 1. tick 总数：18 常规 + 1-2 补时
        val stoppageTicks = random.nextInt(
            config.stoppageTimeTicksMin,
            config.stoppageTimeTicksMax + 1
        )
        val totalTicks = config.totalRegularTicks + stoppageTicks

        // 2. 预分配进球时刻（V0.2 04 §十二.3 风险应对：进球时刻预分配机制）
        val homeGoalMinutes = distributeGoalMinutes(score.first, totalTicks, random)
        val awayGoalMinutes = distributeGoalMinutes(score.second, totalTicks, random)

        // 3. 逐 tick 生成事件
        for (tick in 0 until totalTicks) {
            val minute = tickToMinute(tick, random)
            val isStarTick = tick > 0 && tick % starTickInterval == 0

            // 控球方判定
            val possessionSide = determinePossession(
                homeControlStrength, awayControlStrength,
                currentHomeGoals = events.count { it.type == EventType.GOAL && it.teamSide == TeamSide.HOME },
                currentAwayGoals = events.count { it.type == EventType.GOAL && it.teamSide == TeamSide.AWAY },
                random
            )

            // 事件类型抽样
            val eventType = sampleEventType(
                possessionSide, input, isStarTick, random
            )

            // 选择参与球员
            val actingPlayer = selectPlayerForEvent(
                eventType, possessionSide, input, random
            )

            // 处理事件结果
            val event = resolveEvent(
                eventType, minute, possessionSide, actingPlayer, input,
                isStarTick, random
            )
            events.add(event)

            // 进球时刻触发（预分配的进球在这里落地为 GOAL 事件）
            if (minute in homeGoalMinutes) {
                val scorer = selectScorer(input.homeTeam, random)
                val assister = selectAssister(input.homeTeam, random)
                events.add(
                    MatchEvent(
                        minute = minute,
                        type = EventType.GOAL,
                        teamSide = TeamSide.HOME,
                        playerId = scorer?.playerId,
                        secondaryPlayerId = assister?.playerId,
                        quality = sampleGoalQuality(random),
                        description = "主队进球！${scorer?.playerId ?: "未知球员"}破门"
                    )
                )
                homeGoalMinutes.remove(minute)
            }
            if (minute in awayGoalMinutes) {
                val scorer = selectScorer(input.awayTeam, random)
                val assister = selectAssister(input.awayTeam, random)
                events.add(
                    MatchEvent(
                        minute = minute,
                        type = EventType.GOAL,
                        teamSide = TeamSide.AWAY,
                        playerId = scorer?.playerId,
                        secondaryPlayerId = assister?.playerId,
                        quality = sampleGoalQuality(random),
                        description = "客队进球！${scorer?.playerId ?: "未知球员"}破门"
                    )
                )
                awayGoalMinutes.remove(minute)
            }

            // 红黄牌/伤病检查
            events.addAll(
                checkCardsAndInjuries(minute, possessionSide, input, random)
            )
        }

        // 4. 处理补时阶段未落地的进球（兜底，确保比分与泊松层一致）
        homeGoalMinutes.forEach { minute ->
            val scorer = selectScorer(input.homeTeam, random)
            events.add(
                MatchEvent(
                    minute = minute.coerceAtMost(94),
                    type = EventType.GOAL,
                    teamSide = TeamSide.HOME,
                    playerId = scorer?.playerId,
                    secondaryPlayerId = null,
                    quality = sampleGoalQuality(random),
                    description = "主队进球！"
                )
            )
        }
        awayGoalMinutes.forEach { minute ->
            val scorer = selectScorer(input.awayTeam, random)
            events.add(
                MatchEvent(
                    minute = minute.coerceAtMost(94),
                    type = EventType.GOAL,
                    teamSide = TeamSide.AWAY,
                    playerId = scorer?.playerId,
                    secondaryPlayerId = null,
                    quality = sampleGoalQuality(random),
                    description = "客队进球！"
                )
            )
        }

        return events.sortedBy { it.minute }
    }

    // ==================== 进球时刻分配 ====================

    /**
     * 将 N 个进球分配到 tick 上，返回对应的分钟列表。
     *
     * 使用无放回采样，避免进球过度集中。
     */
    private fun distributeGoalMinutes(
        goals: Int,
        totalTicks: Int,
        random: Random
    ): MutableList<Int> {
        if (goals <= 0) return mutableListOf()
        val tickIndices = (0 until totalTicks).shuffled(random).take(goals)
        return tickIndices.map { tickToMinute(it, random) }.toMutableList()
    }

    /** tick 转分钟：tick 0 → 1-5 分钟，tick 17 → 86-90 分钟，补时 → 91-94 */
    private fun tickToMinute(tick: Int, random: Random): Int {
        val base = tick * config.matchEventIntervalMinutes + 1
        val offset = random.nextInt(config.matchEventIntervalMinutes)
        return (base + offset).coerceIn(1, 94)
    }

    // ==================== 控球方判定 ====================

    /**
     * 控球方判定（V0.2 04 §七）
     *
     * 基于双方控制力比例，叠加当前比分修正（落后方更激进）与主场优势。
     */
    private fun determinePossession(
        homeControl: Double,
        awayControl: Double,
        currentHomeGoals: Int,
        currentAwayGoals: Int,
        random: Random
    ): TeamSide {
        val controlSum = homeControl + awayControl
        if (controlSum <= 0.0) return if (random.nextBoolean()) TeamSide.HOME else TeamSide.AWAY

        var homeProb = homeControl / controlSum
        // 主场优势 +3%
        homeProb += 0.03
        // 落后方更积极抢控球
        val diff = currentHomeGoals - currentAwayGoals
        if (diff < 0) homeProb += 0.04
        if (diff > 0) homeProb -= 0.04

        homeProb = homeProb.coerceIn(0.20, 0.80)
        return if (random.nextDouble() < homeProb) TeamSide.HOME else TeamSide.AWAY
    }

    /**
     * 从 xg 的 controlRatio 乘子反推控制力强度。
     *
     * controlRatio ∈ [0.80, 1.20]，映射为 40-80 的控制力强度。
     */
    private fun deriveControlStrength(controlRatio: Double): Double {
        return (controlRatio * 50.0).coerceIn(20.0, 100.0)
    }

    // ==================== 事件类型抽样 ====================

    /**
     * 事件类型抽样（V0.2 04 §七 事件概率基础表）
     *
     * 基础概率从 config 读取，叠加战术修正、比分修正、球星模板修正。
     */
    private fun sampleEventType(
        possessionSide: TeamSide,
        input: MatchInput,
        isStarTick: Boolean,
        random: Random
    ): EventType {
        val baseProbs = config.eventBaseProbabilities.toMutableMap()

        // 战术修正：压迫强度提升抢断/前场事件
        val tacticMods = calculateTacticEventModifiers(input, possessionSide)
        // 比分修正：落后方更多危险进攻
        val scoreMods = calculateScoreEventModifiers(input, possessionSide)

        val adjustedProbs = baseProbs.mapValues { (type, prob) ->
            prob * (tacticMods[type] ?: 1.0) * (scoreMods[type] ?: 1.0)
        }.toMutableMap()

        // 球星模板修正（关键 tick 触发）
        if (isStarTick) {
            val sheet = sheetBySide(input, possessionSide)
            val starPlayer = sheet.starting11.firstOrNull { it.starTemplate != null }
            if (starPlayer?.starTemplate != null) {
                val template = starTemplateRegistry.get(starPlayer.starTemplate)
                template.modifiers.forEach { (eventType, mod) ->
                    adjustedProbs[eventType] = (adjustedProbs[eventType] ?: 0.0) * (1.0 + mod)
                }
            }
        }

        return weightedSample(adjustedProbs, random)
    }

    /**
     * 战术事件修正
     */
    private fun calculateTacticEventModifiers(
        input: MatchInput,
        side: TeamSide
    ): Map<EventType, Double> {
        val sheet = sheetBySide(input, side)
        val mods = mutableMapOf<EventType, Double>()

        // 高位压迫 → 前场抢断 + 危险进攻
        if (sheet.tactic.style.name.contains("HIGH_PRESS")) {
            mods[EventType.DANGEROUS_ATTACK] = 1.15
            mods[EventType.FOUL] = 1.10
        }
        // 反击类 → 反击事件 +
        if (sheet.tactic.style.name.contains("COUNTER")) {
            mods[EventType.COUNTER_ATTACK] = 1.30
            mods[EventType.POSSESSION] = 0.85
        }
        // 边路传中 → 角球 +
        if (sheet.tactic.style == TacticStyle.WING_CROSS) {
            mods[EventType.CORNER] = 1.20
        }
        // 控球 → 控球事件 +
        if (sheet.tactic.style == TacticStyle.POSSESSION) {
            mods[EventType.POSSESSION] = 1.15
        }
        return mods
    }

    /**
     * 比分修正：落后方更激进
     */
    private fun calculateScoreEventModifiers(
        input: MatchInput,
        side: TeamSide
    ): Map<EventType, Double> {
        // 简化：每个 tick 都允许基础修正
        return emptyMap()
    }

    // ==================== 球员选择 ====================

    /**
     * 为事件选择参与球员
     */
    private fun selectPlayerForEvent(
        eventType: EventType,
        side: TeamSide,
        input: MatchInput,
        random: Random
    ): PlayerState? {
        val sheet = sheetBySide(input, side)
        return when (eventType) {
            EventType.GOAL, EventType.SHOT, EventType.SHOT_ON_TARGET,
            EventType.DANGEROUS_ATTACK, EventType.NORMAL_ATTACK, EventType.COUNTER_ATTACK ->
                selectAttackingPlayer(sheet, random)
            EventType.SAVE -> selectGoalkeeper(sheet)
            EventType.CLEARANCE ->
                selectDefendingPlayer(sheet, random)
            EventType.CORNER, EventType.FREE_KICK, EventType.PENALTY ->
                selectSetPieceTaker(sheet, random)
            EventType.YELLOW_CARD, EventType.RED_CARD, EventType.FOUL ->
                selectDefendingPlayer(sheet, random)
            else -> sheet.starting11.randomOrNull(random)
        }
    }

    /** 选择进攻球员：前锋/边锋/前腰优先 */
    private fun selectAttackingPlayer(sheet: TeamSheet, random: Random): PlayerState? {
        val attackers = sheet.starting11.filter {
            it.position in setOf(Position.ST, Position.CF, Position.LW, Position.RW, Position.AM, Position.CM)
        }
        val pool = if (attackers.isNotEmpty()) attackers else sheet.starting11
        return pool.randomOrNull(random)
    }

    /** 选择防守球员 */
    private fun selectDefendingPlayer(sheet: TeamSheet, random: Random): PlayerState? {
        val defenders = sheet.starting11.filter {
            it.position in setOf(Position.CB, Position.LB, Position.RB, Position.DM)
        }
        val pool = if (defenders.isNotEmpty()) defenders else sheet.starting11
        return pool.randomOrNull(random)
    }

    /** 选择门将 */
    private fun selectGoalkeeper(sheet: TeamSheet): PlayerState? {
        return sheet.starting11.firstOrNull { it.position == Position.GK }
            ?: sheet.starting11.firstOrNull()
    }

    /** 选择定位球主罚者 */
    private fun selectSetPieceTaker(sheet: TeamSheet, random: Random): PlayerState? {
        val takers = sheet.starting11.filter {
            it.position in setOf(Position.AM, Position.CM, Position.DM)
        }
        val pool = if (takers.isNotEmpty()) takers else sheet.starting11
        return pool.randomOrNull(random)
    }

    /** 选择进球者：前锋优先，球星加权 */
    private fun selectScorer(sheet: TeamSheet, random: Random): PlayerState? {
        val candidates = sheet.starting11.filter {
            it.position in setOf(Position.ST, Position.CF, Position.LW, Position.RW, Position.AM, Position.CM)
        }
        val pool = if (candidates.isNotEmpty()) candidates else sheet.starting11
        // 球星加权：双倍权重
        val weighted = pool.flatMap { listOf(it, it).let { l -> if (it.starTemplate != null) l + it else l } }
        return weighted.randomOrNull(random)
    }

    /** 选择助攻者：中场优先 */
    private fun selectAssister(sheet: TeamSheet, random: Random): PlayerState? {
        val candidates = sheet.starting11.filter {
            it.position in setOf(Position.AM, Position.CM, Position.LW, Position.RW, Position.DM)
        }
        val pool = if (candidates.isNotEmpty()) candidates else sheet.starting11
        // 30% 概率无助攻
        return if (random.nextDouble() < 0.30) null else pool.randomOrNull(random)
    }

    // ==================== 事件解析 ====================

    /**
     * 解析事件，生成 MatchEvent
     */
    private fun resolveEvent(
        eventType: EventType,
        minute: Int,
        side: TeamSide,
        player: PlayerState?,
        input: MatchInput,
        isStarTick: Boolean,
        random: Random
    ): MatchEvent {
        val quality = sampleEventQuality(eventType, random)
        val description = describeEvent(eventType, side, player, isStarTick)

        // 球星在关键 tick 触发特殊事件描述
        val finalDescription = if (isStarTick && player?.starTemplate != null) {
            "$description（球星发挥）"
        } else {
            description
        }

        return MatchEvent(
            minute = minute,
            type = eventType,
            teamSide = side,
            playerId = player?.playerId,
            secondaryPlayerId = null,
            quality = quality,
            description = finalDescription
        )
    }

    /** 事件质量采样 0.0-1.0 */
    private fun sampleEventQuality(eventType: EventType, random: Random): Double {
        val base = when (eventType) {
            EventType.GOAL -> 0.5 + random.nextDouble() * 0.5
            EventType.DANGEROUS_ATTACK -> 0.4 + random.nextDouble() * 0.4
            EventType.SHOT, EventType.SHOT_ON_TARGET -> 0.3 + random.nextDouble() * 0.4
            EventType.SAVE -> 0.4 + random.nextDouble() * 0.4
            else -> random.nextDouble() * 0.5
        }
        return base.coerceIn(0.0, 1.0)
    }

    /** 进球质量 0.35-0.70 */
    private fun sampleGoalQuality(random: Random): Double {
        return (0.35 + random.nextDouble() * 0.35).coerceIn(0.0, 1.0)
    }

    /** 事件文字描述 */
    private fun describeEvent(
        eventType: EventType,
        side: TeamSide,
        player: PlayerState?,
        isStarTick: Boolean
    ): String {
        val sideText = when (side) {
            TeamSide.HOME -> "主队"
            TeamSide.AWAY -> "客队"
            TeamSide.NEUTRAL -> "中场"
        }
        val playerText = player?.playerId ?: ""
        return when (eventType) {
            EventType.POSSESSION -> "$sideText 控球推进"
            EventType.NORMAL_ATTACK -> "$sideText 普通进攻"
            EventType.DANGEROUS_ATTACK -> "$sideText 危险进攻 $playerText"
            EventType.SHOT -> "$sideText 射门 $playerText"
            EventType.SHOT_ON_TARGET -> "$sideText 射正 $playerText"
            EventType.GOAL -> "$sideText 进球！$playerText"
            EventType.CORNER -> "$sideText 角球"
            EventType.FREE_KICK -> "$sideText 任意球"
            EventType.PENALTY -> "$sideText 点球"
            EventType.YELLOW_CARD -> "$sideText 黄牌 $playerText"
            EventType.RED_CARD -> "$sideText 红牌 $playerText"
            EventType.INJURY -> "$sideText 伤病 $playerText"
            EventType.SAVE -> "$sideText 扑救 $playerText"
            EventType.CLEARANCE -> "$sideText 解围 $playerText"
            EventType.OFFSIDE -> "$sideText 越位"
            EventType.FOUL -> "$sideText 犯规 $playerText"
            EventType.COUNTER_ATTACK -> "$sideText 快速反击 $playerText"
            EventType.NOTHING -> "无明显事件"
        }
    }

    // ==================== 红黄牌与伤病 ====================

    /**
     * 红黄牌/伤病检查（V0.2 04 §十）
     */
    private fun checkCardsAndInjuries(
        minute: Int,
        side: TeamSide,
        input: MatchInput,
        random: Random
    ): List<MatchEvent> {
        val events = mutableListOf<MatchEvent>()

        // 黄牌概率：每 tick 约 3%
        if (random.nextDouble() < 0.03) {
            val foulSide = if (side == TeamSide.HOME) TeamSide.AWAY else TeamSide.HOME
            val sheet = sheetBySide(input, foulSide)
            val player = selectDefendingPlayer(sheet, random)
            events.add(
                MatchEvent(
                    minute = minute,
                    type = EventType.YELLOW_CARD,
                    teamSide = foulSide,
                    playerId = player?.playerId,
                    secondaryPlayerId = null,
                    quality = 0.0,
                    description = "${if (foulSide == TeamSide.HOME) "主队" else "客队"}黄牌 ${player?.playerId ?: ""}"
                )
            )
        }

        // 红牌概率：每 tick 约 0.3%
        if (random.nextDouble() < 0.003) {
            val foulSide = if (side == TeamSide.HOME) TeamSide.AWAY else TeamSide.HOME
            val sheet = sheetBySide(input, foulSide)
            val player = selectDefendingPlayer(sheet, random)
            events.add(
                MatchEvent(
                    minute = minute,
                    type = EventType.RED_CARD,
                    teamSide = foulSide,
                    playerId = player?.playerId,
                    secondaryPlayerId = null,
                    quality = 0.0,
                    description = "${if (foulSide == TeamSide.HOME) "主队" else "客队"}红牌 ${player?.playerId ?: ""}"
                )
            )
        }

        // 伤病概率：每 tick 约 0.5%
        if (random.nextDouble() < 0.005) {
            val sheet = sheetBySide(input, side)
            val player = sheet.starting11.randomOrNull(random)
            if (player != null) {
                events.add(
                    MatchEvent(
                        minute = minute,
                        type = EventType.INJURY,
                        teamSide = side,
                        playerId = player.playerId,
                        secondaryPlayerId = null,
                        quality = 0.0,
                        description = "${if (side == TeamSide.HOME) "主队" else "客队"}伤病 ${player.playerId}"
                    )
                )
            }
        }

        return events
    }

    // ==================== 工具方法 ====================

    private fun sheetBySide(input: MatchInput, side: TeamSide): TeamSheet =
        when (side) {
            TeamSide.HOME -> input.homeTeam
            TeamSide.AWAY -> input.awayTeam
            TeamSide.NEUTRAL -> input.homeTeam
        }

    /** 加权抽样 */
    private fun weightedSample(probs: Map<EventType, Double>, random: Random): EventType {
        val positive = probs.filterValues { it > 0.0 }
        if (positive.isEmpty()) return EventType.NOTHING
        val total = positive.values.sum()
        if (total <= 0.0) return EventType.NOTHING
        var r = random.nextDouble() * total
        for ((type, p) in positive) {
            r -= p
            if (r <= 0.0) return type
        }
        return positive.keys.last()
    }
}
