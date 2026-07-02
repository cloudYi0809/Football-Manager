package com.greendynasty.football.scouting.core

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.data.SaveScoutEventEntity
import com.greendynasty.football.scouting.data.SaveScoutHiredEntity
import com.greendynasty.football.scouting.data.SaveScoutTaskEntity
import com.greendynasty.football.scouting.model.ScoutEventType
import com.greendynasty.football.scouting.model.YouthTournament
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * T14 青年赛事扫描器（V0.2 08 §七 青年赛事发现）。
 *
 * 5 项青年赛事：
 * - U17 世界杯（10-11 月举办）
 * - U20 世界杯（5-6 月）
 * - 欧洲青年锦标赛（3-4 月）
 * - 南美青年锦标赛（1-2 月）
 * - 青年欧冠（3-5 月）
 *
 * 4 类事件（按概率触发）：
 * - YOUTH_HAT_TRICK（5%）：小妖帽子戏法
 * - BIG_CLUB_RUSH（4%）：豪门争夺
 * - VALUE_SURGE（4%）：身价暴涨
 * - SCOUT_STRONG_RECOMMEND（6%）：球探强烈推荐（概率随球探潜力判断提升）
 *
 * 限制：单任务单日最多触发 1 类事件（避免刷屏）。
 *
 * @param databaseManager 三库管理入口
 * @param config 球探配置
 */
class YouthTournamentScanner(
    private val databaseManager: DatabaseManager,
    private val config: ScoutConfig = ScoutConfig.DEFAULT
) {

    /**
     * 扫描青年赛事事件（V0.2 08 §七）。
     *
     * 由 [com.greendynasty.football.scouting.ScoutingService.advanceDaily] 在每日推进时调用，
     * 仅对 taskType = YOUTH_TOURNAMENT 的任务执行。
     *
     * @param task 青年赛事观察任务
     * @param currentDate 当前日期
     * @return 触发的事件列表（单任务单日最多 1 类事件）
     */
    suspend fun scan(
        task: SaveScoutTaskEntity,
        currentDate: LocalDate
    ): List<SaveScoutEventEntity> = withContext(Dispatchers.IO) {
        val events = mutableListOf<SaveScoutEventEntity>()

        // 1. 校验任务类型与赛事 ID
        val tournamentId = task.youthTournamentId ?: return@withContext events
        val tournament = YouthTournament.fromId(tournamentId) ?: return@withContext events

        // 2. 仅在赛事举办月份触发
        if (!isTournamentActive(tournament, currentDate)) return@withContext events

        // 3. 获取球探信息
        val hired = databaseManager.saveScoutHiredDao().get(task.hiredId)
            ?: return@withContext events
        val scout = databaseManager.historyScoutDao().getScout(hired.scoutId)
            ?: return@withContext events

        val eventProbabilities = config.youthEventProbabilities

        // 4. 4 类事件按概率触发（单任务单日最多 1 类，避免刷屏）
        //    按 SCOUT_STRONG_RECOMMEND → YOUTH_HAT_TRICK → BIG_CLUB_RUSH → VALUE_SURGE 优先级
        val recommendProb = (eventProbabilities["SCOUT_STRONG_RECOMMEND"] ?: 0.0) *
            (scout.judgingPotential / 20.0)
        if (Random.nextDouble() < recommendProb) {
            val player = pickRandomYouthPlayer(task.saveId, tournament.regionCodes)
            events.add(
                SaveScoutEventEntity(
                    saveId = task.saveId, hiredId = task.hiredId, taskId = task.taskId,
                    eventType = ScoutEventType.SCOUT_STRONG_RECOMMEND.code,
                    playerId = player,
                    tournamentId = tournamentId,
                    eventDate = currentDate.toString(),
                    summary = if (player != null) "球探强烈推荐关注球员 #$player（${tournament.displayName}）"
                    else "球探强烈推荐关注某球员（${tournament.displayName}）"
                )
            )
            return@withContext events
        }

        if (Random.nextDouble() < (eventProbabilities["YOUTH_HAT_TRICK"] ?: 0.0)) {
            val player = pickRandomYouthPlayer(task.saveId, tournament.regionCodes)
            events.add(
                SaveScoutEventEntity(
                    saveId = task.saveId, hiredId = task.hiredId, taskId = task.taskId,
                    eventType = ScoutEventType.YOUTH_HAT_TRICK.code,
                    playerId = player,
                    tournamentId = tournamentId,
                    eventDate = currentDate.toString(),
                    summary = if (player != null) "球员 #$player 在 ${tournament.displayName} 上演帽子戏法！"
                    else "${tournament.displayName} 上出现帽子戏法！"
                )
            )
            return@withContext events
        }

        if (Random.nextDouble() < (eventProbabilities["BIG_CLUB_RUSH"] ?: 0.0)) {
            events.add(
                SaveScoutEventEntity(
                    saveId = task.saveId, hiredId = task.hiredId, taskId = task.taskId,
                    eventType = ScoutEventType.BIG_CLUB_RUSH.code,
                    tournamentId = tournamentId,
                    eventDate = currentDate.toString(),
                    summary = "${tournament.displayName} 出现豪门争夺的目标"
                )
            )
            return@withContext events
        }

        if (Random.nextDouble() < (eventProbabilities["VALUE_SURGE"] ?: 0.0)) {
            events.add(
                SaveScoutEventEntity(
                    saveId = task.saveId, hiredId = task.hiredId, taskId = task.taskId,
                    eventType = ScoutEventType.VALUE_SURGE.code,
                    tournamentId = tournamentId,
                    eventDate = currentDate.toString(),
                    summary = "${tournament.displayName} 上某球员身价暴涨"
                )
            )
            return@withContext events
        }

        events
    }

    /**
     * 判断赛事是否在举办月份（V0.2 08 §七）。
     *
     * 举办月份见 [ScoutConfig.tournamentSchedule]。
     */
    private fun isTournamentActive(tournament: YouthTournament, date: LocalDate): Boolean {
        val schedule = config.tournamentSchedule[tournament.id] ?: return false
        return date.monthValue in schedule
    }

    /**
     * 随机抽取一名青年球员（V0.2 08 §七，简化版）。
     *
     * V1 简化：从该赛事地区内的青年球员（年龄 ≤20）随机抽取。
     * V2 可接入 T15 历史新星池。
     *
     * @return 球员 ID，无候选返回 null
     */
    private suspend fun pickRandomYouthPlayer(saveId: Int, regionCodes: List<String>): Int? =
        withContext(Dispatchers.IO) {
            // V1 简化：从 save_player_state 随机抽取一名活跃球员
            // V2 可改为从历史新星池抽取
            val playerStateDao = databaseManager.savePlayerStateDao()
            // 通过 clubId 范围查询太宽泛，这里返回 null 占位
            // 真实实现需 T15 提供历史新星候选池
            null
        }
}
