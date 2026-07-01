package com.greendynasty.football.simulation.matchday

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.match.api.CompetitionContext
import com.greendynasty.football.match.api.Importance
import com.greendynasty.football.match.api.MatchInput
import com.greendynasty.football.match.api.MatchSimulator
import com.greendynasty.football.match.model.MatchResult
import com.greendynasty.football.simulation.active.ActiveScopeManager
import com.greendynasty.football.simulation.active.ClubSimulationDepth
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.api.MatchResultSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 比赛日执行器（T07 方案 §四.1 MatchCheckTask 的核心实现）
 *
 * 职责：
 * 1. 查询当日全部比赛
 * 2. 按活跃范围分层模拟：FULL/ACTIVE 调用 MatchSimulator，LIGHT/MINIMAL 快速比分
 * 3. 回填 SaveMatchEntity 比分
 * 4. 增量更新积分榜（调用 LeagueTableUpdater）
 *
 * 集成点：
 * - T02 MatchSimulator：完整比赛模拟
 * - T06 LeagueTableUpdater：积分榜增量更新
 * - T04/T05 TeamSheetBuilder：从存档构建出场名单
 *
 * @param databaseManager 三库管理入口
 * @param matchSimulator T02 比赛模拟器
 * @param teamSheetBuilder 出场名单构建器
 * @param leagueTableUpdater 积分榜更新器
 * @param activeScopeManager 活跃范围管理器
 */
class MatchDayExecutor(
    private val databaseManager: DatabaseManager,
    private val matchSimulator: MatchSimulator,
    private val teamSheetBuilder: TeamSheetBuilder,
    private val leagueTableUpdater: LeagueTableUpdater,
    private val activeScopeManager: ActiveScopeManager
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 执行当日全部比赛
     *
     * @param ctx 推进上下文
     * @return 当日比赛事件列表 + 比赛结果摘要列表
     */
    suspend fun execute(ctx: AdvanceContext): MatchDayOutput = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val results = mutableListOf<MatchResultSummary>()

        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext MatchDayOutput(events, results)

        // 查询当日全部未完赛比赛
        val dateStr = dateFormatter.format(ctx.currentDate)
        val todayMatches = saveDb.saveMatchDao()
            .getByDateRange(ctx.saveId, dateStr, dateStr)
            .filter { it.status == "scheduled" }

        if (todayMatches.isEmpty()) {
            return@withContext MatchDayOutput(events, results)
        }

        // 俱乐部名称缓存（避免重复查询）
        val clubNameCache = mutableMapOf<Int, String>()

        for (match in todayMatches) {
            try {
                val result = simulateMatch(match, ctx)
                if (result != null) {
                    // 回填比分到 save_match
                    val updatedAt = dateFormatter.format(ctx.nextDate)
                    saveDb.saveMatchDao().updateResult(
                        matchId = match.matchId,
                        homeScore = result.homeScore,
                        awayScore = result.awayScore,
                        status = "finished",
                        statsJson = null, // V1 简化，不存详细统计
                        updatedAt = updatedAt
                    )

                    // 更新积分榜（仅联赛比赛，杯赛不更新积分榜）
                    leagueTableUpdater.updateAfterMatch(
                        ctx = ctx,
                        competitionId = match.competitionId,
                        homeClubId = match.homeClubId,
                        awayClubId = match.awayClubId,
                        homeScore = result.homeScore,
                        awayScore = result.awayScore
                    )

                    // 生成比赛事件
                    val homeName = clubNameCache.getOrPut(match.homeClubId) {
                        getClubName(match.homeClubId) ?: "俱乐部${match.homeClubId}"
                    }
                    val awayName = clubNameCache.getOrPut(match.awayClubId) {
                        getClubName(match.awayClubId) ?: "俱乐部${match.awayClubId}"
                    }

                    val isPlayerMatch = match.homeClubId == ctx.managerClubId ||
                            match.awayClubId == ctx.managerClubId

                    results.add(
                        MatchResultSummary(
                            matchId = match.matchId,
                            homeClubId = match.homeClubId,
                            awayClubId = match.awayClubId,
                            homeClubName = homeName,
                            awayClubName = awayName,
                            homeScore = result.homeScore,
                            awayScore = result.awayScore,
                            isPlayerMatch = isPlayerMatch,
                            competitionId = match.competitionId
                        )
                    )

                    events.add(
                        AdvanceEvent(
                            type = AdvanceEventType.MATCH_PLAYED,
                            description = "$homeName ${result.homeScore}-${result.awayScore} $awayName",
                            clubId = null,
                            playerId = null,
                            priority = if (isPlayerMatch) EventPriority.URGENT else EventPriority.LOW
                        )
                    )

                    // 处理比赛后果（伤病/红黄牌，仅活跃范围）
                    processMatchConsequences(match, result, ctx)
                }
            } catch (e: Exception) {
                Log.e(TAG, "比赛模拟失败: matchId=${match.matchId}", e)
            }
        }

        MatchDayOutput(events, results)
    }

    /**
     * 模拟单场比赛（按活跃范围分层）
     */
    private suspend fun simulateMatch(match: SaveMatchEntity, ctx: AdvanceContext): MatchResult? {
        val homeDepth = activeScopeManager.getClubDepth(match.homeClubId, ctx)
        val awayDepth = activeScopeManager.getClubDepth(match.awayClubId, ctx)

        return when {
            // 玩家比赛或活跃范围比赛：完整模拟
            match.homeClubId == ctx.managerClubId || match.awayClubId == ctx.managerClubId -> {
                simulateFullMatch(match, ctx)
            }
            homeDepth == ClubSimulationDepth.ACTIVE || awayDepth == ClubSimulationDepth.ACTIVE -> {
                simulateFullMatch(match, ctx)
            }
            // 非活跃范围：快速比分生成
            else -> {
                simulateQuickScore(match, ctx)
            }
        }
    }

    /**
     * 完整模拟（调用 T02 MatchSimulator）
     */
    private suspend fun simulateFullMatch(match: SaveMatchEntity, ctx: AdvanceContext): MatchResult? {
        val homeSheet = teamSheetBuilder.build(ctx, match.homeClubId, isHome = true)
            ?: return null
        val awaySheet = teamSheetBuilder.build(ctx, match.awayClubId, isHome = false)
            ?: return null

        val input = MatchInput(
            matchId = match.matchId.toString(),
            homeTeam = homeSheet,
            awayTeam = awaySheet,
            competition = CompetitionContext(
                competitionId = match.competitionId.toString(),
                importance = Importance.NORMAL,
                isDerby = false,
                isKnockout = false,
                isFinal = false,
                isRelegationBattle = false
            ),
            randomSeed = ctx.randomSeed + match.matchId
        )

        return matchSimulator.simulate(input)
    }

    /**
     * 快速比分生成（非活跃范围，不调用完整引擎）
     * 基于俱乐部声望的简化泊松采样
     */
    private suspend fun simulateQuickScore(match: SaveMatchEntity, ctx: AdvanceContext): MatchResult {
        val homeRating = getClubRating(match.homeClubId).toDouble()
        val awayRating = getClubRating(match.awayClubId).toDouble()

        // 简化泊松：基于评分差直接采样
        val ratio = if (awayRating > 0) (homeRating / awayRating) else 1.0
        val homeXg = 1.45 * ratio.coerceIn(0.55, 1.75)
        val awayXg = 1.15 * (1.0 / ratio.coerceIn(0.55, 1.75))

        val random = Random(ctx.randomSeed + match.matchId)
        val homeGoals = poissonSample(homeXg, random)
        val awayGoals = poissonSample(awayXg, random)

        return MatchResult(
            matchId = match.matchId.toString(),
            homeScore = homeGoals,
            awayScore = awayGoals,
            homeXg = homeXg,
            awayXg = awayXg,
            events = emptyList(),
            playerRatings = emptyMap(),
            homeStats = com.greendynasty.football.match.model.MatchStatistics(
                0.5, 0, 0, 0, 0, 0, 0, 0.75
            ),
            awayStats = com.greendynasty.football.match.model.MatchStatistics(
                0.5, 0, 0, 0, 0, 0, 0, 0.75
            ),
            injuries = emptyList(),
            cards = emptyList(),
            calibrated = true
        )
    }

    /**
     * 泊松采样（Knuth 算法）
     */
    private fun poissonSample(lambda: Double, random: Random): Int {
        val l = Math.exp(-lambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > l)
        return k - 1
    }

    /**
     * 获取俱乐部声望作为评分（0-100）
     */
    private suspend fun getClubRating(clubId: Int): Int {
        return try {
            val club = databaseManager.historyClubDao().getClub(clubId)
            club?.reputation ?: 50
        } catch (e: Exception) {
            50
        }
    }

    /**
     * 获取俱乐部名称
     */
    private suspend fun getClubName(clubId: Int): String? {
        return try {
            databaseManager.historyClubDao().getClub(clubId)?.clubName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 处理比赛后果（伤病/体能消耗，仅活跃范围）
     * V1 简化：仅更新比赛日体能消耗
     */
    private suspend fun processMatchConsequences(
        match: SaveMatchEntity,
        result: MatchResult,
        ctx: AdvanceContext
    ) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val homeDepth = activeScopeManager.getClubDepth(match.homeClubId, ctx)
        val awayDepth = activeScopeManager.getClubDepth(match.awayClubId, ctx)

        // 活跃范围俱乐部：比赛后体能消耗
        if (homeDepth != ClubSimulationDepth.MINIMAL) {
            applyPostMatchConditionLoss(ctx, match.homeClubId)
        }
        if (awayDepth != ClubSimulationDepth.MINIMAL) {
            applyPostMatchConditionLoss(ctx, match.awayClubId)
        }

        // 处理比赛中的伤病（仅活跃范围）
        if (homeDepth != ClubSimulationDepth.LIGHT && homeDepth != ClubSimulationDepth.MINIMAL) {
            processMatchInjuries(ctx, match.homeClubId, result)
        }
        if (awayDepth != ClubSimulationDepth.LIGHT && awayDepth != ClubSimulationDepth.MINIMAL) {
            processMatchInjuries(ctx, match.awayClubId, result)
        }
    }

    /**
     * 比赛后体能消耗（活跃范围俱乐部全部球员）
     */
    private suspend fun applyPostMatchConditionLoss(ctx: AdvanceContext, clubId: Int) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, clubId)
        for (player in players) {
            if (player.injuryStatus != "healthy") continue
            val newCondition = (player.condition - 15).coerceAtLeast(0)
            saveDb.savePlayerStateDao().updateCondition(ctx.saveId, player.playerId, newCondition)
        }
    }

    /**
     * 处理比赛中的伤病记录（V1 简化：直接从 MatchResult.injuries 写入 save_injury）
     */
    private suspend fun processMatchInjuries(ctx: AdvanceContext, clubId: Int, result: MatchResult) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val dateStr = dateFormatter.format(ctx.currentDate)

        for (injury in result.injuries) {
            val playerId = injury.playerId.toIntOrNull() ?: continue
            val severityDays = injury.severityDays
            val returnDate = ctx.currentDate.plusDays(severityDays.toLong())

            saveDb.saveInjuryDao().insert(
                com.greendynasty.football.data.save.entity.SaveInjuryEntity(
                    saveId = ctx.saveId,
                    playerId = playerId,
                    injuryType = "match_injury",
                    startDate = dateStr,
                    expectedReturnDate = dateFormatter.format(returnDate),
                    severity = severityDays.coerceIn(1, 3),
                    recurrenceRisk = 0,
                    status = "active"
                )
            )
            saveDb.savePlayerStateDao().updateInjuryStatus(
                ctx.saveId, playerId, "injured", dateFormatter.format(returnDate)
            )
        }
    }

    companion object {
        private const val TAG = "MatchDayExecutor"
    }
}

/**
 * 比赛日执行结果
 */
data class MatchDayOutput(
    val events: List<AdvanceEvent>,
    val matches: List<MatchResultSummary>
)
