package com.greendynasty.football.ui.schedule.data

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveCupTieEntity
import com.greendynasty.football.data.save.entity.SaveLeagueTableEntity
import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.data.save.entity.SaveScheduleStateEntity
import com.greendynasty.football.ui.schedule.generator.CupBracketGenerator
import com.greendynasty.football.ui.schedule.generator.LeagueScheduleGenerator
import com.greendynasty.football.ui.schedule.generator.LeagueTableCalculator
import com.greendynasty.football.ui.schedule.model.CupStageConfig
import com.greendynasty.football.ui.schedule.model.CupStageUi
import com.greendynasty.football.ui.schedule.model.CupTieUi
import com.greendynasty.football.ui.schedule.model.CupStage
import com.greendynasty.football.ui.schedule.model.LeagueTableEntry
import com.greendynasty.football.ui.schedule.model.MatchStatus
import com.greendynasty.football.ui.schedule.model.MatchUi
import com.greendynasty.football.ui.schedule.model.ScheduleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * 赛程数据仓库
 *
 * 职责：
 * - 从 history.db 读取俱乐部 / 赛事 / 赛季 / 参赛关联（只读）
 * - 从 save.db 读取比赛、积分榜、杯赛对阵、生成状态（可写）
 * - 调用 [LeagueScheduleGenerator] / [CupBracketGenerator] / [LeagueTableCalculator] 生成与计算
 * - 聚合为 UI 模型 [MatchUi] / [LeagueTableEntry] / [CupTieUi]
 *
 * 三库分离读取铁律：history 只读、save 可写、cache 可重建（V1 暂未启用缓存）。
 *
 * @param databaseManager 三库管理入口
 * @param saveId 当前存档 ID（save_xxx.db 列值）
 * @param playerClubId 玩家俱乐部 ID（用于「我的赛程」与高亮）
 */
class ScheduleRepository(
    private val databaseManager: DatabaseManager,
    private val saveId: Int = DEFAULT_SAVE_ID,
    private val playerClubId: Int = DEFAULT_CLUB_ID,
    private val config: ScheduleConfig = ScheduleConfig.DEFAULT
) {

    private val leagueGenerator = LeagueScheduleGenerator(config)
    private val cupGenerator = CupBracketGenerator(config)
    private val tableCalculator = LeagueTableCalculator(config)

    // ==================== 赛程生成 ====================

    /**
     * 生成联赛双循环赛程并写入 save.db。
     *
     * @param seasonId 赛季 ID
     * @param competitionId 赛事 ID
     * @param clubIds 参赛俱乐部 ID 列表
     * @param seasonStart 赛季开始日期
     * @return 生成的比赛数；-1 表示存档未就绪
     */
    suspend fun generateLeagueSchedule(
        seasonId: Int,
        competitionId: Int,
        clubIds: List<Int>,
        seasonStart: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext -1
        try {
            val matches = leagueGenerator.generateDoubleRoundRobin(
                clubIds = clubIds,
                seasonStart = seasonStart,
                competitionId = competitionId,
                seasonId = seasonId
            ).map { it.copy(saveId = saveId) }

            // 标记玩家俱乐部比赛
            val tagged = matches.map { m ->
                if (m.homeClubId == playerClubId || m.awayClubId == playerClubId) {
                    m.copy(isPlayerMatch = 1)
                } else m
            }
            saveDb.saveMatchDao().insertAll(tagged)

            // 初始化空积分榜（保证表显示 0 数据）
            val emptyTable = clubIds.map { clubId ->
                SaveLeagueTableEntity(
                    saveId = saveId,
                    seasonId = seasonId,
                    competitionId = competitionId,
                    clubId = clubId,
                    position = 0,
                    updatedAt = LocalDate.now().toString()
                )
            }
            saveDb.saveLeagueTableDao().insertAll(emptyTable)
            tagged.size
        } catch (e: Exception) {
            Log.e(TAG, "generateLeagueSchedule 失败: comp=$competitionId", e)
            -1
        }
    }

    /**
     * 生成杯赛对阵表并写入 save.db。
     *
     * @param seasonId 赛季 ID
     * @param competitionId 赛事 ID
     * @param participants 参赛俱乐部 ID 列表
     * @param seedRanking 种子排名（clubId -> 种子分）
     * @param startDate 首轮首回合日期
     * @param stageConfig 各轮次赛制（默认使用 [ScheduleConfig.defaultCupStages]）
     * @return 生成的 tie 数与比赛数；Pair(0, 0) 表示存档未就绪
     */
    suspend fun generateCupBracket(
        seasonId: Int,
        competitionId: Int,
        participants: List<Int>,
        seedRanking: Map<Int, Int>,
        startDate: LocalDate,
        stageConfig: List<CupStageConfig> = config.defaultCupStages
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0 to 0
        try {
            val (ties, matches) = cupGenerator.generateCupBracket(
                participants = participants,
                seedRanking = seedRanking,
                stageConfig = stageConfig,
                competitionId = competitionId,
                seasonId = seasonId,
                startDate = startDate
            )
            val taggedTies = ties.map { it.copy(saveId = saveId) }
            val taggedMatches = matches.map { m ->
                if (m.homeClubId == playerClubId || m.awayClubId == playerClubId) {
                    m.copy(saveId = saveId, isPlayerMatch = 1)
                } else m.copy(saveId = saveId)
            }
            saveDb.saveCupTieDao().insertAll(taggedTies)
            saveDb.saveMatchDao().insertAll(taggedMatches)

            // 回填每个 tie 的 firstLegMatchId / secondLegMatchId
            // 通过 (homeClubId, awayClubId, matchDate) 反查
            backfillTieMatchIds(taggedTies, saveDb)
            taggedTies.size to taggedMatches.size
        } catch (e: Exception) {
            Log.e(TAG, "generateCupBracket 失败: comp=$competitionId", e)
            0 to 0
        }
    }

    /**
     * 回填 tie 的 firstLegMatchId / secondLegMatchId。
     *
     * 由于 SaveMatchEntity 不持有 tieId，通过 (home, away, round) 反查匹配。
     */
    private suspend fun backfillTieMatchIds(
        ties: List<SaveCupTieEntity>,
        saveDb: com.greendynasty.football.data.save.SaveDatabase
    ) {
        val now = LocalDate.now().toString()
        // 重新查询以拿到带 matchId 的实体
        val savedMatches = saveDb.saveMatchDao()
            .getBySeasonAndCompetition(
                saveId,
                ties.firstOrNull()?.seasonId ?: return,
                ties.first().competitionId
            )
        for (tie in ties) {
            val home = tie.homeClubId ?: continue
            val away = tie.awayClubId ?: continue
            val legMatches = savedMatches.filter {
                (it.homeClubId == home && it.awayClubId == away) &&
                    it.matchRound == tie.stageOrder
            }.sortedBy { it.matchDate }
            if (legMatches.isEmpty()) continue
            saveDb.saveCupTieDao().setFirstLegMatchId(tie.tieId, legMatches[0].matchId, now)
            if (legMatches.size >= 2) {
                saveDb.saveCupTieDao().setSecondLegMatchId(tie.tieId, legMatches[1].matchId, now)
            }
        }
    }

    /**
     * 写入赛程生成状态记录（防止重复生成 & 标记生成完成时间）。
     */
    suspend fun markScheduleGenerated(
        seasonId: Int,
        matchCount: Int,
        leagueRounds: Int,
        cupGenerated: Boolean,
        euroGenerated: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext
        try {
            saveDb.saveScheduleStateDao().upsert(
                SaveScheduleStateEntity(
                    saveId = saveId,
                    seasonId = seasonId,
                    generatedAt = LocalDate.now().toString(),
                    matchCount = matchCount,
                    leagueRounds = leagueRounds,
                    cupGenerated = if (cupGenerated) 1 else 0,
                    euroGenerated = if (euroGenerated) 1 else 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "markScheduleGenerated 失败", e)
        }
    }

    /** 查询某赛季是否已生成赛程 */
    suspend fun isScheduleGenerated(seasonId: Int): Boolean = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext false
        saveDb.saveScheduleStateDao().get(saveId, seasonId) != null
    }

    // ==================== 比赛查询 ====================

    /** 玩家俱乐部未来比赛（「我的赛程」Tab） */
    fun observePlayerMatches(seasonId: Int): Flow<List<MatchUi>> {
        if (!isSaveReady()) return flow { emit(emptyList()) }
        val matchDao = databaseManager.getSaveDatabase().saveMatchDao()
        return matchDao.observeByClub(saveId, playerClubId, seasonId)
            .mapToUi()
            .flowOn(Dispatchers.IO)
    }

    /** 指定赛事的所有比赛（「联赛」Tab） */
    fun observeLeagueMatches(seasonId: Int, competitionId: Int): Flow<List<MatchUi>> {
        if (!isSaveReady()) return flow { emit(emptyList()) }
        val matchDao = databaseManager.getSaveDatabase().saveMatchDao()
        return matchDao.observeBySeasonAndCompetition(saveId, seasonId, competitionId)
            .mapToUi()
            .flowOn(Dispatchers.IO)
    }

    /** 按轮次分组的联赛赛程 */
    fun observeLeagueMatchesGroupedByRound(
        seasonId: Int,
        competitionId: Int
    ): Flow<Map<Int, List<MatchUi>>> =
        observeLeagueMatches(seasonId, competitionId).map { matches ->
            matches.sortedBy { it.round }.groupBy { it.round }
        }

    // ==================== 积分榜 ====================

    /**
     * 观察联赛积分榜（含 home/away 拆分）。
     *
     * 实现：观察 save_match，过滤已完赛 → 调用 [LeagueTableCalculator] 计算。
     * 性能：单赛事重算 ≤100ms，UI 仅订阅一次。
     */
    fun observeLeagueTable(
        seasonId: Int,
        competitionId: Int,
        clubIds: List<Int>
    ): Flow<List<LeagueTableEntry>> {
        if (!isSaveReady()) return flow { emit(emptyList()) }
        val matchDao = databaseManager.getSaveDatabase().saveMatchDao()
        return matchDao.observeBySeasonAndCompetition(saveId, seasonId, competitionId)
            .map { matches ->
                val names = getClubNames(clubIds)
                tableCalculator.recalculate(matches, names)
            }
            .catch { e ->
                Log.e(TAG, "observeLeagueTable 失败: comp=$competitionId", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * 同步重算并写入 save_league_table（供 T07 比赛日入口调用）。
     *
     * 性能要求：≤100ms。
     */
    suspend fun refreshLeagueTable(
        seasonId: Int,
        competitionId: Int,
        clubIds: List<Int>
    ): List<LeagueTableEntry> = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
        try {
            val matches = saveDb.saveMatchDao()
                .getBySeasonAndCompetition(saveId, seasonId, competitionId)
            val names = getClubNames(clubIds)
            val entries = tableCalculator.recalculate(matches, names)
            // 持久化（仅 overall 字段，home/away 由查询时实时计算）
            val entities = entries.map { e ->
                SaveLeagueTableEntity(
                    saveId = saveId,
                    seasonId = seasonId,
                    competitionId = competitionId,
                    clubId = e.clubId,
                    position = e.rank,
                    played = e.played,
                    won = e.won, drawn = e.drawn, lost = e.lost,
                    goalsFor = e.goalsFor, goalsAgainst = e.goalsAgainst,
                    goalDifference = e.goalDifference,
                    points = e.points,
                    form = e.form,
                    updatedAt = LocalDate.now().toString()
                )
            }
            saveDb.saveLeagueTableDao().deleteBySeasonAndCompetition(saveId, seasonId, competitionId)
            saveDb.saveLeagueTableDao().insertAll(entities)
            entries
        } catch (e: Exception) {
            Log.e(TAG, "refreshLeagueTable 失败: comp=$competitionId", e)
            emptyList()
        }
    }

    // ==================== 杯赛对阵 ====================

    /** 观察杯赛对阵表 */
    fun observeCupBracket(competitionId: Int): Flow<List<CupStageUi>> {
        if (!isSaveReady()) return flow { emit(emptyList()) }
        val cupDao = databaseManager.getSaveDatabase().saveCupTieDao()
        return cupDao.observeByCompetition(saveId, competitionId)
            .map { ties -> groupTiesByStage(ties) }
            .catch { e ->
                Log.e(TAG, "observeCupBracket 失败: comp=$competitionId", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /** 按 CupStage 分组并转换为 UI 模型 */
    private suspend fun groupTiesByStage(ties: List<SaveCupTieEntity>): List<CupStageUi> {
        if (ties.isEmpty()) return emptyList()
        val allClubIds = ties.flatMap { listOfNotNull(it.homeClubId, it.awayClubId) }.distinct()
        val names = getClubNames(allClubIds)
        return ties
            .groupBy { CupStage.fromRaw(it.stage) ?: CupStage.FINAL }
            .map { (stage, stageTies) ->
                CupStageUi(
                    stage = stage,
                    ties = stageTies.sortedBy { it.slotIndex }.map { it.toUi(names) }
                )
            }
            .sortedBy { it.stage.order }
    }

    /**
     * 解析杯赛对阵并回填晋级方到下一轮。
     *
     * 由比赛日入口在杯赛比赛结束后调用。
     */
    suspend fun resolveCupTie(tieId: String): Int? = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null
        try {
            val tie = saveDb.saveCupTieDao().get(tieId) ?: return@withContext null
            val matches = listOfNotNull(
                tie.firstLegMatchId?.let { saveDb.saveMatchDao().get(it) },
                tie.secondLegMatchId?.let { saveDb.saveMatchDao().get(it) }
            ).filter { it.status == "finished" }
            if (matches.isEmpty()) return@withContext null
            val winner = computeTieWinner(tie, matches)
            val aggHome = matches.filter { it.homeClubId == tie.homeClubId }
                .sumOf { it.homeScore ?: 0 } +
                matches.filter { it.awayClubId == tie.homeClubId }
                    .sumOf { it.awayScore ?: 0 }
            val aggAway = matches.filter { it.homeClubId == tie.awayClubId }
                .sumOf { it.homeScore ?: 0 } +
                matches.filter { it.awayClubId == tie.awayClubId }
                    .sumOf { it.awayScore ?: 0 }
            saveDb.saveCupTieDao().updateWinner(
                tieId, winner, aggHome, aggAway, LocalDate.now().toString()
            )

            // 回填到下一轮对阵的空位
            if (winner != null) {
                tie.nextTieId?.let { nextId ->
                    val updated = saveDb.saveCupTieDao().fillHomeSlot(nextId, winner, LocalDate.now().toString())
                    if (updated == 0) {
                        saveDb.saveCupTieDao().fillAwaySlot(nextId, winner, LocalDate.now().toString())
                    }
                    // 重新读取确认双方是否都已就位；就位后由 T07 比赛日入口生成实际比赛
                    val refreshed = saveDb.saveCupTieDao().get(nextId)
                    if (refreshed != null && refreshed.homeClubId != null &&
                        refreshed.awayClubId != null && refreshed.firstLegMatchId == null
                    ) {
                        Log.d(TAG, "下一轮对阵双方就位: tie=$nextId, 待生成比赛")
                    }
                }
            }
            winner
        } catch (e: Exception) {
            Log.e(TAG, "resolveCupTie 失败: tie=$tieId", e)
            null
        }
    }

    /** 计算对阵晋级方 */
    private fun computeTieWinner(
        tie: SaveCupTieEntity,
        matches: List<SaveMatchEntity>
    ): Int? {
        if (matches.isEmpty()) return null
        if (tie.isTwoLegged != 1) {
            // 单回合
            val m = matches.first()
            val home = m.homeScore ?: return null
            val away = m.awayScore ?: return null
            return when {
                home > away -> m.homeClubId
                away > home -> m.awayClubId
                else -> if (config.cupUsePenaltyShootout) {
                    if (Random.nextBoolean()) m.homeClubId else m.awayClubId
                } else null
            }
        }
        // 双回合：总比分
        val leg1 = matches.sortedBy { it.matchDate }.getOrNull(0) ?: return null
        val leg2 = matches.sortedBy { it.matchDate }.getOrNull(1)
        val homeAgg = (leg1.homeScore ?: 0) + (leg2?.awayScore ?: 0)
        val awayAgg = (leg1.awayScore ?: 0) + (leg2?.homeScore ?: 0)
        val homeClub = tie.homeClubId ?: return null
        val awayClub = tie.awayClubId ?: return null
        return when {
            homeAgg > awayAgg -> homeClub
            awayAgg > homeAgg -> awayClub
            else -> {
                // 客场进球规则
                val awayGoalsHome = leg1.awayScore ?: 0  // home club 客场进球
                val awayGoalsAway = leg2?.awayScore ?: 0 // away club 客场进球
                when {
                    awayGoalsHome > awayGoalsAway -> homeClub
                    awayGoalsAway > awayGoalsHome -> awayClub
                    else -> if (config.cupUsePenaltyShootout) {
                        if (Random.nextBoolean()) homeClub else awayClub
                    } else null
                }
            }
        }
    }

    // ==================== history.db 查询 ====================

    /** 查询某赛季某赛事的全部参赛俱乐部 ID */
    suspend fun getCompetitionParticipants(seasonId: Int, competitionId: Int): List<Int> =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.historyCompetitionDao()
                    .getClubsInCompetition(seasonId, competitionId)
                    .first()
                    .map { it.clubId }
            } catch (e: Exception) {
                Log.e(TAG, "getCompetitionParticipants 失败", e)
                emptyList()
            }
        }

    /** 获取玩家俱乐部 ID（注入时确定） */
    fun getPlayerClubId(): Int = playerClubId

    /** 查询俱乐部名称映射 */
    suspend fun getClubNames(clubIds: List<Int>): Map<Int, String> = withContext(Dispatchers.IO) {
        if (clubIds.isEmpty()) return@withContext emptyMap()
        try {
            val clubDao = databaseManager.historyClubDao()
            clubIds.associateWith { id ->
                clubDao.getClub(id)?.clubName ?: "俱乐部$id"
            }
        } catch (e: Exception) {
            Log.e(TAG, "getClubNames 失败", e)
            clubIds.associateWith { "俱乐部$it" }
        }
    }

    /** 查询赛季信息 */
    suspend fun getSeason(seasonId: Int): com.greendynasty.football.data.history.entity.SeasonEntity? =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.historySeasonDao().getSeason(seasonId)
            } catch (e: Exception) {
                Log.e(TAG, "getSeason 失败", e)
                null
            }
        }

    /** 查询赛事信息 */
    suspend fun getCompetition(competitionId: Int): com.greendynasty.football.data.history.entity.CompetitionEntity? =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.historyCompetitionDao().getCompetition(competitionId)
            } catch (e: Exception) {
                Log.e(TAG, "getCompetition 失败", e)
                null
            }
        }

    // ==================== 内部工具 ====================

    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    /** 将 SaveMatchEntity 列表转换为 MatchUi，聚合俱乐部名称 */
    private fun kotlinx.coroutines.flow.Flow<List<SaveMatchEntity>>.mapToUi(): Flow<List<MatchUi>> =
        map { matches ->
            if (matches.isEmpty()) return@map emptyList()
            val clubIds = matches.flatMap { listOf(it.homeClubId, it.awayClubId) }.distinct()
            val names = getClubNames(clubIds)
            val compNames = mutableMapOf<Int, String>()
            matches.map { m ->
                val compName = compNames.getOrPut(m.competitionId) {
                    runCatching {
                        kotlinx.coroutines.runBlocking { getCompetition(m.competitionId)?.name }
                    }.getOrNull() ?: "赛事${m.competitionId}"
                }
                MatchUi(
                    matchId = m.matchId,
                    matchDate = m.matchDate,
                    round = m.matchRound ?: 0,
                    competitionId = m.competitionId,
                    competitionShortName = compName.take(6),
                    homeClubId = m.homeClubId,
                    homeClubName = names[m.homeClubId] ?: "?",
                    awayClubId = m.awayClubId,
                    awayClubName = names[m.awayClubId] ?: "?",
                    homeScore = m.homeScore,
                    awayScore = m.awayScore,
                    status = MatchStatus.fromRaw(m.status),
                    isPlayerMatch = m.isPlayerMatch == 1
                )
            }
        }

    /** SaveCupTieEntity → CupTieUi */
    private fun SaveCupTieEntity.toUi(names: Map<Int, String>): CupTieUi = CupTieUi(
        tieId = tieId,
        stage = CupStage.fromRaw(stage) ?: CupStage.FINAL,
        stageOrder = stageOrder,
        slotIndex = slotIndex,
        homeClubId = homeClubId,
        homeClubName = homeClubId?.let { names[it] },
        awayClubId = awayClubId,
        awayClubName = awayClubId?.let { names[it] },
        isTwoLegged = isTwoLegged == 1,
        aggregateHomeScore = aggregateHomeScore,
        aggregateAwayScore = aggregateAwayScore,
        winnerClubId = winnerClubId,
        nextTieId = nextTieId
    )

    companion object {
        private const val TAG = "ScheduleRepository"
        private const val DEFAULT_SAVE_ID = 1
        private const val DEFAULT_CLUB_ID = 1
    }
}
