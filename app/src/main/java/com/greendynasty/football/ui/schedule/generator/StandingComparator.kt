package com.greendynasty.football.ui.schedule.generator

import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.ui.schedule.model.ScheduleConfig

/**
 * 单队战绩累计容器（内部使用，可变字段供 [LeagueTableCalculator] 累加）
 *
 * 含主场/客场拆分以支持 home/away 视图切换。
 */
data class TeamStats(
    val clubId: Int,
    var played: Int = 0,
    var won: Int = 0,
    var drawn: Int = 0,
    var lost: Int = 0,
    var goalsFor: Int = 0,
    var goalsAgainst: Int = 0,
    var goalDifference: Int = 0,
    var points: Int = 0,
    // 主场战绩
    var homePlayed: Int = 0,
    var homeWon: Int = 0,
    var homeDrawn: Int = 0,
    var homeLost: Int = 0,
    var homeGoalsFor: Int = 0,
    var homeGoalsAgainst: Int = 0,
    // 客场战绩
    var awayPlayed: Int = 0,
    var awayWon: Int = 0,
    var awayDrawn: Int = 0,
    var awayLost: Int = 0,
    var awayGoalsFor: Int = 0,
    var awayGoalsAgainst: Int = 0
)

/**
 * 积分榜排名比较器
 *
 * 严格依据 T06 实现方案 §四 排名规则：
 * 1. 积分（高 → 前）
 * 2. 净胜球（高 → 前）
 * 3. 进球数（高 → 前）
 * 4. 相互交锋战绩（积分 → 净胜球）
 * 5. 客场进球数（可配置开关 [ScheduleConfig.useAwayGoalsTiebreaker]）
 * 6. 抽签（稳定排序，保持原顺序）
 *
 * 注意：相互交锋仅比较已完赛的两队对战，未踢按 0 处理。
 */
class StandingComparator(
    private val finishedMatches: List<SaveMatchEntity> = emptyList(),
    private val config: ScheduleConfig = ScheduleConfig.DEFAULT
) : Comparator<TeamStats> {

    override fun compare(a: TeamStats, b: TeamStats): Int {
        // 1. 积分（降序）
        if (a.points != b.points) return b.points.compareTo(a.points)
        // 2. 净胜球（降序）
        if (a.goalDifference != b.goalDifference) {
            return b.goalDifference.compareTo(a.goalDifference)
        }
        // 3. 进球数（降序）
        if (a.goalsFor != b.goalsFor) return b.goalsFor.compareTo(a.goalsFor)
        // 4. 相互交锋战绩
        val h2h = compareHeadToHead(a.clubId, b.clubId, finishedMatches)
        if (h2h != 0) return h2h
        // 5. 客场进球数（降序，可配置）
        if (config.useAwayGoalsTiebreaker) {
            val aAwayGoals = a.awayGoalsFor
            val bAwayGoals = b.awayGoalsFor
            if (aAwayGoals != bAwayGoals) return bAwayGoals.compareTo(aAwayGoals)
        }
        // 6. 抽签（稳定排序，保持原顺序）
        return 0
    }

    /**
     * 比较两队的相互交锋战绩。
     *
     * @return 负数表示 a 在前，正数表示 b 在前，0 表示完全相同
     */
    private fun compareHeadToHead(
        clubA: Int,
        clubB: Int,
        matches: List<SaveMatchEntity>
    ): Int {
        var aPoints = 0
        var bPoints = 0
        var aGd = 0
        var bGd = 0
        for (m in matches) {
            if (m.status != "finished") continue
            val isAHome = m.homeClubId == clubA && m.awayClubId == clubB
            val isAAway = m.awayClubId == clubA && m.homeClubId == clubB
            if (!isAHome && !isAAway) continue
            val aScore = (if (isAHome) m.homeScore else m.awayScore) ?: continue
            val bScore = (if (isAHome) m.awayScore else m.homeScore) ?: continue
            when {
                aScore > bScore -> aPoints += 3
                aScore < bScore -> bPoints += 3
                else -> { aPoints++; bPoints++ }
            }
            aGd += aScore - bScore
            bGd += bScore - aScore
        }
        if (aPoints != bPoints) return bPoints.compareTo(aPoints)
        return bGd.compareTo(aGd)
    }
}
