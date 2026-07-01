package com.greendynasty.football.match.core

import com.greendynasty.football.match.api.PlayerState
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.model.EventType
import com.greendynasty.football.match.model.MatchEvent
import com.greendynasty.football.match.model.MatchResult
import com.greendynasty.football.match.model.MatchStatistics
import com.greendynasty.football.match.model.PlayerRating
import com.greendynasty.football.match.model.PlayerRatingBreakdown
import com.greendynasty.football.match.model.TeamSide
import com.greendynasty.football.match.model.XGResult
import com.greendynasty.football.match.model.CardRecord
import com.greendynasty.football.match.model.InjuryRecord

/**
 * Layer 5 结果校准层（V0.2 04 §六 + §十一）
 *
 * 职责：
 * 1. 球员赛后评分（V0.2 04 §十一）
 * 2. 生成最终 MatchStatistics（控球率/射门/射正/角球/犯规/牌）
 * 3. 极端比分二次抑制（如 8-0 降级为 7-0，配合 calibrationMaxGoals）
 * 4. 提取伤病与牌记录
 *
 * 校准完成后标记 calibrated = true。
 */
class CalibrationLayer(
    private val config: MatchConfig = MatchConfig.DEFAULT,
    /** 主队首发球员（用于按位置计算评分） */
    private val homePlayers: List<PlayerState> = emptyList(),
    /** 客队首发球员 */
    private val awayPlayers: List<PlayerState> = emptyList()
) {

    /**
     * 校准比赛结果。
     *
     * @param result 初步 MatchResult（含 events / score / xg，stats 与 ratings 可为空）
     * @param xg XGLayer 产出（用于一致性参考）
     * @return 校准后的最终 MatchResult
     */
    fun calibrate(result: MatchResult, xg: XGResult): MatchResult {
        val allPlayers = homePlayers + awayPlayers

        // 1. 球员赛后评分（V0.2 04 §十一）
        val playerRatings = calculatePlayerRatings(
            events = result.events,
            homeScore = result.homeScore,
            awayScore = result.awayScore,
            startingPlayers = allPlayers
        )

        // 2. 生成统计
        val homeStats = generateStats(result.events, TeamSide.HOME)
        val awayStats = generateStats(result.events, TeamSide.AWAY)

        // 3. 极端比分二次抑制（V0.2 04 §六）
        var homeScore = result.homeScore
        var awayScore = result.awayScore
        var calibrated = false

        if (homeScore > config.calibrationMaxGoals) {
            homeScore = config.calibrationMaxGoals
            calibrated = true
        }
        if (awayScore > config.calibrationMaxGoals) {
            awayScore = config.calibrationMaxGoals
            calibrated = true
        }
        // 比分差过大也抑制（如 8-0 → 7-0 已由上面处理，这里再处理 diff > 6）
        val diff = kotlin.math.abs(homeScore - awayScore)
        if (diff > 6) {
            if (homeScore > awayScore) {
                homeScore = awayScore + 6
            } else {
                awayScore = homeScore + 6
            }
            calibrated = true
        }

        // 4. 提取伤病与牌记录
        val injuries = extractInjuries(result.events)
        val cards = extractCards(result.events)

        return result.copy(
            homeScore = homeScore,
            awayScore = awayScore,
            playerRatings = playerRatings,
            homeStats = homeStats,
            awayStats = awayStats,
            injuries = injuries,
            cards = cards,
            calibrated = calibrated
        )
    }

    // ==================== 球员评分（V0.2 04 §十一） ====================

    /**
     * 球员赛后评分
     *
     * match_rating = 6.5
     *              + goals * position_goal_weight
     *              + assists * 0.6
     *              + key_passes * 0.08
     *              + tackles_success * 0.05
     *              + interceptions * 0.05
     *              + saves * 0.10
     *              - errors_leading_to_goal * 0.8
     *              - red_card * 1.5
     *              - own_goal * 1.0
     *              + team_result_bonus
     *
     * 限制：4.0 - 10.0
     */
    fun calculatePlayerRatings(
        events: List<MatchEvent>,
        homeScore: Int,
        awayScore: Int,
        startingPlayers: List<PlayerState>
    ): Map<String, PlayerRating> {
        val ratings = mutableMapOf<String, PlayerRating>()
        if (startingPlayers.isEmpty()) return ratings

        for (player in startingPlayers) {
            val playerEvents = events.filter { it.playerId == player.playerId }
            val assistEvents = events.filter {
                it.secondaryPlayerId == player.playerId && it.type == EventType.GOAL
            }

            val goals = playerEvents.count { it.type == EventType.GOAL }
            val assists = assistEvents.size
            val keyPasses = playerEvents.count { it.type == EventType.DANGEROUS_ATTACK }
            val tacklesSuccess = playerEvents.count { it.type == EventType.CLEARANCE }
            val interceptions = playerEvents.count { it.type == EventType.CLEARANCE }
            val saves = if (player.position == Position.GK) {
                playerEvents.count { it.type == EventType.SAVE }
            } else 0
            val errors = 0  // V1 简化：未追踪失误导致进球
            val redCard = playerEvents.count { it.type == EventType.RED_CARD }
            val ownGoal = 0  // V1 简化

            val posWeight = getPositionGoalWeight(player.position)
            val isHome = homePlayers.any { it.playerId == player.playerId }
            val teamResultBonus = calculateTeamBonus(isHome, homeScore, awayScore)

            val pr = config.playerRating
            var rating = pr.baseRating +
                goals * posWeight +
                assists * pr.assistWeight +
                keyPasses * pr.keyPassWeight +
                tacklesSuccess * pr.tackleWeight +
                interceptions * pr.interceptionWeight +
                saves * pr.saveWeight -
                errors * pr.errorPenalty -
                redCard * pr.redCardPenalty -
                ownGoal * pr.ownGoalPenalty +
                teamResultBonus

            rating = rating.coerceIn(pr.minRating, pr.maxRating)

            ratings[player.playerId] = PlayerRating(
                playerId = player.playerId,
                rating = rating,
                breakdown = PlayerRatingBreakdown(
                    goals = goals,
                    assists = assists,
                    keyPasses = keyPasses,
                    tacklesSuccess = tacklesSuccess,
                    interceptions = interceptions,
                    saves = saves,
                    errorsLeadingToGoal = errors,
                    redCard = redCard,
                    ownGoal = ownGoal,
                    teamResultBonus = teamResultBonus,
                    positionGoalWeight = posWeight
                )
            )
        }

        return ratings
    }

    /**
     * 位置进球权重（V0.2 04 §十一）
     *
     * ST/CF/LW/RW: 1.0, AM/CM: 0.7, DM: 0.4, LB/RB/CB: 0.2, GK: 0.0
     */
    private fun getPositionGoalWeight(position: Position): Double {
        val key = position.name
        return config.playerRating.goalWeightByPosition.getOrDefault(key, 0.5)
    }

    /**
     * 球队结果加分
     *
     * 胜 +0.5，平 +0.2，负 -0.3
     */
    private fun calculateTeamBonus(isHome: Boolean, homeScore: Int, awayScore: Int): Double {
        val won = if (isHome) homeScore > awayScore else awayScore > homeScore
        val drew = homeScore == awayScore
        return when {
            won -> config.playerRating.teamWinBonus
            drew -> config.playerRating.teamDrawBonus
            else -> config.playerRating.teamLossBonus
        }
    }

    // ==================== 统计生成 ====================

    /**
     * 生成球队比赛统计
     *
     * 控球率：基于 POSSESSION/NORMAL_ATTACK/DANGEROUS_ATTACK 事件比例
     * 射门：SHOT + SHOT_ON_TARGET + GOAL
     * 射正：SHOT_ON_TARGET + GOAL
     * 角球/犯规/牌：对应事件计数
     * 传球成功率：基于控球事件与战术（V1 简化估算）
     */
    private fun generateStats(events: List<MatchEvent>, side: TeamSide): MatchStatistics {
        val sideEvents = events.filter { it.teamSide == side }
        val oppEvents = events.filter { it.teamSide != side && it.teamSide != TeamSide.NEUTRAL }

        // 控球率：基于控球类事件比例
        val homePossessionEvents = sideEvents.count {
            it.type in setOf(EventType.POSSESSION, EventType.NORMAL_ATTACK, EventType.DANGEROUS_ATTACK)
        }
        val awayPossessionEvents = oppEvents.count {
            it.type in setOf(EventType.POSSESSION, EventType.NORMAL_ATTACK, EventType.DANGEROUS_ATTACK)
        }
        val possession = if (homePossessionEvents + awayPossessionEvents > 0) {
            homePossessionEvents.toDouble() / (homePossessionEvents + awayPossessionEvents)
        } else {
            0.5
        }

        val shots = sideEvents.count {
            it.type in setOf(EventType.SHOT, EventType.SHOT_ON_TARGET, EventType.GOAL)
        }
        val shotsOnTarget = sideEvents.count {
            it.type in setOf(EventType.SHOT_ON_TARGET, EventType.GOAL)
        }
        val corners = sideEvents.count { it.type == EventType.CORNER }
        val fouls = sideEvents.count { it.type == EventType.FOUL }
        val yellowCards = sideEvents.count { it.type == EventType.YELLOW_CARD }
        val redCards = sideEvents.count { it.type == EventType.RED_CARD }

        // 传球成功率：V1 简化估算（基于控球事件占比 + 基线 0.75）
        val passAccuracy = (0.75 + (possession - 0.5) * 0.3).coerceIn(0.60, 0.90)

        return MatchStatistics(
            possession = possession,
            shots = shots,
            shotsOnTarget = shotsOnTarget,
            corners = corners,
            fouls = fouls,
            yellowCards = yellowCards,
            redCards = redCards,
            passAccuracy = passAccuracy
        )
    }

    // ==================== 伤病与牌提取 ====================

    private fun extractInjuries(events: List<MatchEvent>): List<InjuryRecord> {
        return events
            .filter { it.type == EventType.INJURY }
            .map { e ->
                InjuryRecord(
                    playerId = e.playerId ?: "",
                    teamSide = e.teamSide,
                    minute = e.minute,
                    severityDays = 7,  // V1 简化：固定 7 天
                    description = e.description
                )
            }
    }

    private fun extractCards(events: List<MatchEvent>): List<CardRecord> {
        return events
            .filter { it.type == EventType.YELLOW_CARD || it.type == EventType.RED_CARD }
            .map { e ->
                CardRecord(
                    playerId = e.playerId ?: "",
                    teamSide = e.teamSide,
                    minute = e.minute,
                    cardType = if (e.type == EventType.YELLOW_CARD) "YELLOW" else "RED"
                )
            }
    }
}
