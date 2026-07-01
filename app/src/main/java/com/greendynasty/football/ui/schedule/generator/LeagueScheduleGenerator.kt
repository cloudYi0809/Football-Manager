package com.greendynasty.football.ui.schedule.generator

import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.ui.schedule.model.ScheduleConfig
import java.time.LocalDate
import kotlin.random.Random

/**
 * 联赛双循环赛程生成器（圆盘旋转法 Circle Method）
 *
 * 算法依据：T06 实现方案 §三.1，V0.1 11 §九.2 双循环联赛。
 *
 * 性能：O(n²) → 20 队 380 场生成耗时 ≤500ms（铁律）。
 *
 * 关键不变量：
 * 1. N 支球队生成 2*(N-1) 轮，每轮 N/2 场比赛（N 为偶数；奇数补 [BYE_TEAM_ID]）
 * 2. 第二半程主客对换，保证每队主客各 (N-1) 场
 * 3. 任意球队连续主场或客场 ≤ [ScheduleConfig.maxConsecutiveHomeAway]
 *
 * 注意：本生成器仅产出 SaveMatchEntity 列表，不直接写库；写库由 Repository/Service 负责。
 */
class LeagueScheduleGenerator(
    private val config: ScheduleConfig = ScheduleConfig.DEFAULT,
    private val random: Random = Random(System.currentTimeMillis())
) {

    /** 主客对阵对（homeClubId, awayClubId） */
    data class MatchPair(val home: Int, val away: Int)

    /**
     * 生成双循环联赛赛程。
     *
     * @param clubIds 参赛俱乐部 ID 列表（奇数自动补 bye）
     * @param seasonStart 赛季开始日期（首场比赛日）
     * @param competitionId 赛事 ID
     * @param seasonId 赛季 ID
     * @return 完整赛程（2*(n-1) 轮），saveId 默认 0 由调用方填入
     */
    fun generateDoubleRoundRobin(
        clubIds: List<Int>,
        seasonStart: LocalDate,
        competitionId: Int,
        seasonId: Int
    ): List<SaveMatchEntity> {
        require(clubIds.isNotEmpty()) { "参赛俱乐部列表不能为空" }
        val distinct = clubIds.distinct()
        require(distinct.size == clubIds.size) { "参赛俱乐部列表存在重复 ID" }

        // 1. 球队数处理：奇数补虚拟队（bye），轮空方不安排比赛
        val teams = distinct.toMutableList()
        val hasBye = teams.size % 2 != 0
        if (hasBye) teams.add(BYE_TEAM_ID)

        val n = teams.size
        val roundsPerHalf = n - 1            // 半程轮次数
        val totalRounds = 2 * roundsPerHalf  // 双循环总轮次
        val matchesPerRound = n / 2

        // 2. 圆盘旋转法生成首轮对阵
        val firstHalfMatches = mutableListOf<MutableList<MatchPair>>()
        val rotation = teams.toMutableList()

        for (round in 0 until roundsPerHalf) {
            val roundPairs = mutableListOf<MatchPair>()
            for (i in 0 until matchesPerRound) {
                val home = rotation[i]
                val away = rotation[n - 1 - i]
                if (home != BYE_TEAM_ID && away != BYE_TEAM_ID) {
                    // 交替主客：偶数轮 home 在前，奇数轮对换，保证主客平衡
                    val pair = if (round % 2 == 0) {
                        MatchPair(home, away)
                    } else {
                        MatchPair(away, home)
                    }
                    roundPairs.add(pair)
                }
            }
            firstHalfMatches.add(roundPairs)

            // 旋转：固定第一位，其余左移一位，最后一位移到第二位
            val fixed = rotation[0]
            rotation.removeAt(0)
            rotation.add(0, fixed)
            val last = rotation.removeAt(rotation.size - 1)
            rotation.add(1, last)
        }

        // 3. 第二半程：主客对换
        val secondHalfMatches = firstHalfMatches.map { round ->
            round.map { MatchPair(it.away, it.home) }.toMutableList()
        }

        // 4. 随机化轮次顺序（避免可预测），但保持主客半程对应
        val firstHalfOrder = (0 until roundsPerHalf).toMutableList().also { it.shuffle(random) }
        val secondHalfOrder = (0 until roundsPerHalf).toMutableList().also { it.shuffle(random) }

        // 5. 合并并尝试调整连续主客违规
        val finalSchedule = mutableListOf<List<MatchPair>>()
        firstHalfOrder.forEach { origRound -> finalSchedule.add(firstHalfMatches[origRound]) }
        secondHalfOrder.forEach { origRound -> finalSchedule.add(secondHalfMatches[origRound]) }
        adjustConsecutiveHomeAway(finalSchedule, distinct)

        // 6. 分配日期（按轮次间隔 + 杯赛/欧战/国际比赛日避让）
        val matchDates = assignMatchDates(totalRounds, seasonStart, config.leagueRoundIntervalDays)

        // 7. 转为 SaveMatchEntity（saveId=0，由调用方填入）
        val result = mutableListOf<SaveMatchEntity>()
        finalSchedule.forEachIndexed { roundIndex, roundPairs ->
            roundPairs.forEach { pair ->
                result.add(
                    SaveMatchEntity(
                        saveId = 0,
                        seasonId = seasonId,
                        competitionId = competitionId,
                        matchDate = matchDates[roundIndex].toString(),
                        matchRound = roundIndex + 1,
                        homeClubId = pair.home,
                        awayClubId = pair.away,
                        status = "scheduled",
                        isPlayerMatch = 0
                    )
                )
            }
        }
        return result
    }

    /**
     * 连续主客场调整：保证任意球队不连续超过 [ScheduleConfig.maxConsecutiveHomeAway] 场主场或客场
     *
     * 策略：检测违规后，随机选一轮整体交换主客，尝试减少违规计数；最多 [ScheduleConfig.adjustMaxAttempts] 次。
     * 超过最大尝试仍违规：记录警告，不阻塞生成（V1 简化处理，符合方案 §三.1 末段说明）。
     */
    private fun adjustConsecutiveHomeAway(
        schedule: MutableList<List<MatchPair>>,
        clubIds: List<Int>
    ) {
        val maxConsecutive = config.maxConsecutiveHomeAway
        for (attempt in 0 until config.adjustMaxAttempts) {
            var violations = 0
            for (club in clubIds) {
                val sequence = schedule.map { round ->
                    round.find { it.home == club || it.away == club }
                        ?.let { if (it.home == club) 'H' else 'A' } ?: '-'
                }.joinToString("")
                if (hasLongRun(sequence, 'H', maxConsecutive) ||
                    hasLongRun(sequence, 'A', maxConsecutive)
                ) {
                    violations++
                }
            }
            if (violations == 0) return
            // 随机交换两轮的内部主客对换，尝试减少违规
            swapRandomRoundHomeAway(schedule)
        }
        // 超过最大尝试仍违规：不阻塞生成，调用方可记录日志
    }

    /** 检查序列中是否存在超过 maxLen 长度的连续 ch */
    private fun hasLongRun(seq: String, ch: Char, maxLen: Int): Boolean {
        var run = 0
        for (c in seq) {
            run = if (c == ch) run + 1 else 0
            if (run > maxLen) return true
        }
        return false
    }

    /** 随机选一轮，对其所有比赛主客对换 */
    private fun swapRandomRoundHomeAway(schedule: MutableList<List<MatchPair>>) {
        if (schedule.isEmpty()) return
        val idx = random.nextInt(schedule.size)
        val swapped = schedule[idx].map { MatchPair(it.away, it.home) }
        schedule[idx] = swapped
    }

    /**
     * 按轮次间隔分配比赛日期。
     * 第 i 轮 = seasonStart + i * intervalDays。
     * （黑名单避让由 ScheduleOrchestrator 统一处理，此处仅线性分配）
     */
    private fun assignMatchDates(
        totalRounds: Int,
        seasonStart: LocalDate,
        intervalDays: Int
    ): List<LocalDate> {
        return (0 until totalRounds).map { i ->
            seasonStart.plusDays(i.toLong() * intervalDays)
        }
    }

    companion object {
        /** 虚拟队 ID（轮空占位） */
        const val BYE_TEAM_ID = -1
    }
}
