package com.greendynasty.football.simulation.matchday

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveLeagueTableEntity
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * 积分榜增量更新器（T06 LeagueTableCalculator 的简化实现）
 *
 * 每场比赛结束后增量更新双方积分榜记录，避免每次查询都重新计算。
 *
 * 积分规则：胜 3 分 / 平 1 分 / 负 0 分
 * 排序规则：积分 → 净胜球 → 进球数
 *
 * @param databaseManager 三库管理入口
 */
class LeagueTableUpdater(
    private val databaseManager: DatabaseManager
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 更新一场比赛后的积分榜
     *
     * @param ctx 推进上下文
     * @param competitionId 赛事 ID
     * @param homeClubId 主场俱乐部 ID
     * @param awayClubId 客场俱乐部 ID
     * @param homeScore 主场比分
     * @param awayScore 客场比分
     */
    suspend fun updateAfterMatch(
        ctx: AdvanceContext,
        competitionId: Int,
        homeClubId: Int,
        awayClubId: Int,
        homeScore: Int,
        awayScore: Int
    ) = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext
        val updatedAt = dateFormatter.format(ctx.nextDate)

        val homeStanding = saveDb.saveLeagueTableDao()
            .getByClub(ctx.saveId, ctx.currentSeasonId, competitionId, homeClubId)
            ?: createInitialStanding(ctx, competitionId, homeClubId)

        val awayStanding = saveDb.saveLeagueTableDao()
            .getByClub(ctx.saveId, ctx.currentSeasonId, competitionId, awayClubId)
            ?: createInitialStanding(ctx, competitionId, awayClubId)

        // 更新主队
        val updatedHome = applyResult(homeStanding, homeScore, awayScore, isHome = true, updatedAt)
        saveDb.saveLeagueTableDao().updateStandings(
            saveId = updatedHome.saveId,
            seasonId = updatedHome.seasonId,
            competitionId = updatedHome.competitionId,
            clubId = updatedHome.clubId,
            position = updatedHome.position,
            played = updatedHome.played,
            won = updatedHome.won,
            drawn = updatedHome.drawn,
            lost = updatedHome.lost,
            goalsFor = updatedHome.goalsFor,
            goalsAgainst = updatedHome.goalsAgainst,
            goalDifference = updatedHome.goalDifference,
            points = updatedHome.points,
            form = updatedHome.form,
            updatedAt = updatedAt
        )

        // 更新客队
        val updatedAway = applyResult(awayStanding, awayScore, homeScore, isHome = false, updatedAt)
        saveDb.saveLeagueTableDao().updateStandings(
            saveId = updatedAway.saveId,
            seasonId = updatedAway.seasonId,
            competitionId = updatedAway.competitionId,
            clubId = updatedAway.clubId,
            position = updatedAway.position,
            played = updatedAway.played,
            won = updatedAway.won,
            drawn = updatedAway.drawn,
            lost = updatedAway.lost,
            goalsFor = updatedAway.goalsFor,
            goalsAgainst = updatedAway.goalsAgainst,
            goalDifference = updatedAway.goalDifference,
            points = updatedAway.points,
            form = updatedAway.form,
            updatedAt = updatedAt
        )

        // 重新计算排名
        recalculatePositions(ctx, competitionId)
    }

    /**
     * 将比赛结果应用到积分榜记录
     */
    private fun applyResult(
        standing: SaveLeagueTableEntity,
        goalsFor: Int,
        goalsAgainst: Int,
        isHome: Boolean,
        updatedAt: String
    ): SaveLeagueTableEntity {
        val result = when {
            goalsFor > goalsAgainst -> "W"
            goalsFor < goalsAgainst -> "L"
            else -> "D"
        }

        val newForm = updateForm(standing.form, result)

        return standing.copy(
            played = standing.played + 1,
            won = standing.won + if (result == "W") 1 else 0,
            drawn = standing.drawn + if (result == "D") 1 else 0,
            lost = standing.lost + if (result == "L") 1 else 0,
            goalsFor = standing.goalsFor + goalsFor,
            goalsAgainst = standing.goalsAgainst + goalsAgainst,
            goalDifference = standing.goalDifference + (goalsFor - goalsAgainst),
            points = standing.points + when (result) {
                "W" -> 3
                "D" -> 1
                else -> 0
            },
            form = newForm,
            updatedAt = updatedAt
        )
    }

    /**
     * 更新近 N 场战绩（保留最近 5 场）
     */
    private fun updateForm(currentForm: String?, result: String): String {
        val form = currentForm ?: ""
        return (form + result).takeLast(5)
    }

    /**
     * 创建初始积分榜记录（俱乐部首场比赛时）
     */
    private suspend fun createInitialStanding(
        ctx: AdvanceContext,
        competitionId: Int,
        clubId: Int
    ): SaveLeagueTableEntity {
        val standing = SaveLeagueTableEntity(
            saveId = ctx.saveId,
            seasonId = ctx.currentSeasonId,
            competitionId = competitionId,
            clubId = clubId,
            position = 0,
            played = 0,
            won = 0,
            drawn = 0,
            lost = 0,
            goalsFor = 0,
            goalsAgainst = 0,
            goalDifference = 0,
            points = 0,
            form = null,
            updatedAt = null
        )
        databaseManager.getSaveDatabase().saveLeagueTableDao().insert(standing)
        return standing
    }

    /**
     * 重新计算指定赛事的积分榜排名
     * 排序：积分 DESC → 净胜球 DESC → 进球数 DESC
     */
    private suspend fun recalculatePositions(ctx: AdvanceContext, competitionId: Int) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return
        val standings = saveDb.saveLeagueTableDao()
            .getBySeasonAndCompetition(ctx.saveId, ctx.currentSeasonId, competitionId)
            .sortedWith(
                compareByDescending<SaveLeagueTableEntity> { it.points }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
            )

        val updatedAt = dateFormatter.format(ctx.nextDate)
        standings.forEachIndexed { index, entry ->
            val newPosition = index + 1
            if (entry.position != newPosition) {
                saveDb.saveLeagueTableDao().updatePosition(
                    saveId = ctx.saveId,
                    seasonId = ctx.currentSeasonId,
                    competitionId = competitionId,
                    clubId = entry.clubId,
                    position = newPosition,
                    updatedAt = updatedAt
                )
            }
        }
    }
}
