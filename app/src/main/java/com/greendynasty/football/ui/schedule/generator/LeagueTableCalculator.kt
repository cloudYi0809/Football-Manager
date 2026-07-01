package com.greendynasty.football.ui.schedule.generator

import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.ui.schedule.model.LeagueTableEntry
import com.greendynasty.football.ui.schedule.model.ScheduleConfig
import java.time.LocalDate

/**
 * 积分榜计算器
 *
 * 依据 T06 实现方案 §四，V0.1 11 §九 积分规则。
 *
 * 性能要求：单赛事重算 ≤100ms（铁律）。
 *
 * 算法：
 * 1. 从 finishedMatches 全量累计每队战绩（含主场/客场拆分）
 * 2. 按相互交锋 → 净胜球 → 进球数 → 客场进球 排序
 * 3. 生成 LeagueTableEntry 列表，含 home/away/overall 三视图数据
 *
 * 增量策略说明：
 * - 单场比赛结束后只涉及 2 队变化，但同分排名依赖相互交锋需重排整榜
 * - V1 简化：每场比赛后重算该赛事完整积分榜（数据量 ≤20 队，性能足够）
 * - 优化方向（V2）：仅重排受影响区段，避免全量排序
 */
class LeagueTableCalculator(
    private val config: ScheduleConfig = ScheduleConfig.DEFAULT,
    private val comparator: StandingComparator = StandingComparator(emptyList(), config)
) {

    /**
     * 重算指定赛事的完整积分榜。
     *
     * @param finishedMatches 该赛事所有已完赛比赛
     * @param clubNames 俱乐部 ID → 名称映射，用于排序兜底与 UI 展示
     * @param formWindowSize 近 N 场战绩窗口大小，默认 5
     * @return 排序后的积分榜条目（rank 从 1 开始）
     */
    fun recalculate(
        finishedMatches: List<SaveMatchEntity>,
        clubNames: Map<Int, String>,
        formWindowSize: Int = 5
    ): List<LeagueTableEntry> {
        // 1. 累计每队战绩（同时收集涉及的 clubId）
        val stats = mutableMapOf<Int, TeamStats>()
        val finished = finishedMatches.filter { it.status == "finished" }

        for (match in finished) {
            val homeScore = match.homeScore ?: continue
            val awayScore = match.awayScore ?: continue

            val home = stats.getOrPut(match.homeClubId) { TeamStats(match.homeClubId) }
            val away = stats.getOrPut(match.awayClubId) { TeamStats(match.awayClubId) }

            // 总战绩
            home.played++; away.played++
            home.goalsFor += homeScore; home.goalsAgainst += awayScore
            away.goalsFor += awayScore; away.goalsAgainst += homeScore
            // 主场战绩
            home.homePlayed++
            home.homeGoalsFor += homeScore; home.homeGoalsAgainst += awayScore
            // 客场战绩
            away.awayPlayed++
            away.awayGoalsFor += awayScore; away.awayGoalsAgainst += homeScore

            when {
                homeScore > awayScore -> {
                    home.won++; home.points += config.pointsForWin
                    away.lost++
                    home.homeWon++
                    away.awayLost++
                }
                homeScore < awayScore -> {
                    away.won++; away.points += config.pointsForWin
                    home.lost++
                    home.homeLost++
                    away.awayWon++
                }
                else -> {
                    home.drawn++; home.points += config.pointsForDraw
                    away.drawn++; away.points += config.pointsForDraw
                    home.homeDrawn++; away.awayDrawn++
                }
            }
        }

        // 2. 计算净胜球
        stats.values.forEach { s ->
            s.goalDifference = s.goalsFor - s.goalsAgainst
        }

        // 3. 近 N 场 form
        val formMap = computeRecentForm(finished, formWindowSize)

        // 4. 用配置好的 comparator 排序（相互交锋需 finishedMatches 注入）
        val sortedComparator = StandingComparator(finished, config)
        val sorted = stats.values.sortedWith(sortedComparator)

        // 5. 生成 UI 模型
        return sorted.mapIndexed { index, s ->
            LeagueTableEntry(
                clubId = s.clubId,
                clubName = clubNames[s.clubId] ?: "俱乐部${s.clubId}",
                rank = index + 1,
                played = s.played,
                won = s.won, drawn = s.drawn, lost = s.lost,
                goalsFor = s.goalsFor, goalsAgainst = s.goalsAgainst,
                goalDifference = s.goalDifference, points = s.points,
                homePlayed = s.homePlayed,
                homeWon = s.homeWon, homeDrawn = s.homeDrawn, homeLost = s.homeLost,
                homeGoalsFor = s.homeGoalsFor, homeGoalsAgainst = s.homeGoalsAgainst,
                awayPlayed = s.awayPlayed,
                awayWon = s.awayWon, awayDrawn = s.awayDrawn, awayLost = s.awayLost,
                awayGoalsFor = s.awayGoalsFor, awayGoalsAgainst = s.awayGoalsAgainst,
                form = formMap[s.clubId]?.joinToString("")
            )
        }
    }

    /**
     * 计算每队近 N 场战绩 form
     *
     * - 按比赛日期升序取最后 N 场
     * - 输出格式："WWDWL"（最新在最左）
     *
     * @return clubId -> List<Char>，char ∈ {'W','D','L'}
     */
    private fun computeRecentForm(
        finishedMatches: List<SaveMatchEntity>,
        windowSize: Int
    ): Map<Int, List<Char>> {
        val byClub = finishedMatches
            .filter { it.homeScore != null && it.awayScore != null }
            .sortedBy { it.matchDate }
            .fold(mutableMapOf<Int, MutableList<Char>>()) { acc, m ->
                val hs = m.homeScore!!
                val as_ = m.awayScore!!
                val homeChar = when {
                    hs > as_ -> 'W'; hs < as_ -> 'L'; else -> 'D'
                }
                val awayChar = when {
                    as_ > hs -> 'W'; as_ < hs -> 'L'; else -> 'D'
                }
                acc.getOrPut(m.homeClubId) { mutableListOf() }.add(homeChar)
                acc.getOrPut(m.awayClubId) { mutableListOf() }.add(awayChar)
                acc
            }
        return byClub.mapValues { (_, chars) ->
            chars.takeLast(windowSize).reversed()
        }
    }

    companion object {
        /** 当前日期 ISO 字符串（用于 updated_at 标记） */
        fun todayIso(): String = LocalDate.now().toString()
    }
}
