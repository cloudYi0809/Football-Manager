package com.greendynasty.football.season.summary

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * T19 赛季摘要生成器（V0.2 §七.1）
 *
 * 赛季结束时生成 [SeasonSummary]，包含：
 * 1. 联赛积分榜（按赛事分组）
 * 2. 射手榜 / 助攻榜（V1 数据来源不足时返回空列表）
 * 3. 转会汇总（按 fee 排序 Top 10）
 * 4. 玩家俱乐部财政摘要
 * 5. 赛季奖项（金靴 / 冠军 等）
 * 6. 升降级俱乐部 ID 列表
 *
 * 输出 JSON 存入 `season_archive.summary_json`，供历史查询使用。
 *
 * @param databaseManager 三库管理入口（save.db 可写、history.db 只读）
 */
class SeasonSummaryGenerator(
    private val databaseManager: DatabaseManager
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** 序列化器（供 [com.greendynasty.football.season.archive.SeasonArchiver] 使用） */
    val serializer get() = json

    /**
     * 生成赛季摘要
     *
     * @param ctx 推进上下文（isSeasonEnd=true 时调用）
     * @return 赛季完整摘要
     */
    suspend fun generate(ctx: AdvanceContext): SeasonSummary = withContext(Dispatchers.IO) {
        val seasonId = ctx.currentSeasonId

        // 1. 联赛积分榜（按赛事分组）
        val leagueStandings = generateLeagueStandings(ctx)

        // 2. 射手榜（V1 数据源不足，返回空列表）
        val topScorers = generateTopScorers(ctx)

        // 3. 助攻榜（V1 数据源不足，返回空列表）
        val topAssists = emptyList<ScorerListSummary>()

        // 4. 转会汇总
        val transfers = generateTransferSummary(ctx)

        // 5. 玩家俱乐部财政
        val managerFinancial = generateManagerFinancial(ctx)

        // 6. 奖项
        val awards = generateAwards(ctx, leagueStandings, topScorers)

        // 7. 升降级（V1 简化：取顶级联赛末 3 名降级，次级联赛前 3 名升级）
        val (promotions, relegations) = generatePromotionRelegation(ctx, leagueStandings)

        val seasonLabel = getSeasonLabel(seasonId)

        SeasonSummary(
            seasonId = seasonId,
            seasonLabel = seasonLabel,
            leagueStandings = leagueStandings,
            topScorers = topScorers,
            topAssists = topAssists,
            transfers = transfers,
            managerClubFinancial = managerFinancial,
            awards = awards,
            promotions = promotions,
            relegations = relegations
        )
    }

    /** 序列化 [SeasonSummary] 为 JSON 字符串 */
    fun serialize(summary: SeasonSummary): String {
        return json.encodeToString(SeasonSummary.serializer(), summary)
    }

    /** 反序列化 [SeasonSummary] */
    fun deserialize(jsonStr: String): SeasonSummary? {
        return runCatching {
            json.decodeFromString(SeasonSummary.serializer(), jsonStr)
        }.getOrElse {
            Log.w(TAG, "反序列化 SeasonSummary 失败：${it.message}")
            null
        }
    }

    // ==================== 内部生成方法 ====================

    /**
     * 生成联赛积分榜摘要。
     *
     * 遍历 [AdvanceContext.activeLeagueIds] 关联的赛事，从 save_league_table 读取。
     * V1 简化：activeLeagueIds 为联赛赛事 ID 列表，直接用作 competitionId。
     */
    private suspend fun generateLeagueStandings(ctx: AdvanceContext): List<LeagueStandingSummary> {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return emptyList()
        val result = mutableListOf<LeagueStandingSummary>()

        for (competitionId in ctx.activeLeagueIds) {
            try {
                val entries = saveDb.saveLeagueTableDao()
                    .getBySeasonAndCompetition(ctx.saveId, ctx.currentSeasonId, competitionId)
                    .sortedBy { it.position }

                if (entries.isEmpty()) continue

                val clubIds = entries.map { it.clubId }
                val names = getClubNames(clubIds)
                val competitionName = getCompetitionName(competitionId)

                result.add(
                    LeagueStandingSummary(
                        leagueId = competitionId.toString(),
                        leagueName = competitionName,
                        standings = entries.map { e ->
                            StandingEntry(
                                position = e.position,
                                clubId = e.clubId,
                                clubName = names[e.clubId] ?: "俱乐部${e.clubId}",
                                played = e.played,
                                won = e.won,
                                drawn = e.drawn,
                                lost = e.lost,
                                goalsFor = e.goalsFor,
                                goalsAgainst = e.goalsAgainst,
                                points = e.points
                            )
                        }
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "生成联赛积分榜失败：competitionId=$competitionId, ${e.message}")
            }
        }
        return result
    }

    /**
     * 生成射手榜。
     *
     * V1 简化：match_stats_json 当前未持久化详细事件，返回空列表。
     * T0D 数据分析接入后从 compressed_match.summary_json 聚合。
     */
    private suspend fun generateTopScorers(ctx: AdvanceContext): List<ScorerListSummary> {
        // V1: 数据源不足，返回空列表
        // TODO: T0D 数据分析接入后从 compressed_match 解析进球数据
        return emptyList()
    }

    /**
     * 生成转会汇总（仅 status=completed 的报价）。
     */
    private suspend fun generateTransferSummary(ctx: AdvanceContext): TransferSummary =
        withContext(Dispatchers.IO) {
            val saveDb = databaseManager.getSaveDatabaseOrNull()
                ?: return@withContext TransferSummary(0, 0L, 0, emptyList())

            try {
                val completed = saveDb.saveTransferOfferDao()
                    .getByStatus(ctx.saveId, "completed")

                val playerIds = completed.map { it.playerId }.distinct()
                val playerNames = getPlayerNames(playerIds)
                val clubIds = completed.flatMap { listOfNotNull(it.fromClubId, it.toClubId) }.distinct()
                val clubNames = getClubNames(clubIds)

                val records = completed.map { offer ->
                    TransferRecord(
                        playerId = offer.playerId,
                        playerName = playerNames[offer.playerId] ?: "球员${offer.playerId}",
                        fromClubId = offer.fromClubId,
                        fromClubName = offer.fromClubId?.let { clubNames[it] } ?: "自由转会",
                        toClubId = offer.toClubId,
                        toClubName = offer.toClubId?.let { clubNames[it] } ?: "退役",
                        fee = offer.fee,
                        date = offer.createdDate
                    )
                }.sortedByDescending { it.fee }

                TransferSummary(
                    totalTransfers = completed.size,
                    totalFee = completed.sumOf { it.fee.toLong() },
                    maxFee = completed.maxOfOrNull { it.fee } ?: 0,
                    topTransfers = records.take(10)
                )
            } catch (e: Exception) {
                Log.w(TAG, "生成转会汇总失败：${e.message}")
                TransferSummary(0, 0L, 0, emptyList())
            }
        }

    /**
     * 生成玩家俱乐部财政摘要。
     */
    private suspend fun generateManagerFinancial(ctx: AdvanceContext): ClubFinancialSummary {
        val defaultName = "俱乐部${ctx.managerClubId}"
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: return ClubFinancialSummary(
                clubId = ctx.managerClubId, clubName = defaultName,
                balance = 0, transferBudget = 0, wageBudget = 0,
                boardSatisfaction = 0, fanSatisfaction = 0
            )

        val clubState = try {
            saveDb.saveClubStateDao().getByClub(ctx.saveId, ctx.managerClubId)
        } catch (e: Exception) {
            Log.w(TAG, "读取俱乐部状态失败：${e.message}")
            null
        }
        val clubName = getClubNames(listOf(ctx.managerClubId))[ctx.managerClubId] ?: defaultName

        return ClubFinancialSummary(
            clubId = ctx.managerClubId,
            clubName = clubName,
            balance = clubState?.balance ?: 0,
            transferBudget = clubState?.transferBudget ?: 0,
            wageBudget = clubState?.wageBudget ?: 0,
            boardSatisfaction = clubState?.boardSatisfaction ?: 0,
            fanSatisfaction = clubState?.fanSatisfaction ?: 0
        )
    }

    /**
     * 生成赛季奖项。
     *
     * V1 简化：
     * - 金靴奖：取射手榜第 1 名（数据不足时跳过）
     * - 联赛冠军：取每个联赛积分榜第 1 名
     */
    private suspend fun generateAwards(
        ctx: AdvanceContext,
        leagueStandings: List<LeagueStandingSummary>,
        topScorers: List<ScorerListSummary>
    ): List<AwardSummary> {
        val awards = mutableListOf<AwardSummary>()

        // 金靴奖
        if (topScorers.isNotEmpty()) {
            val topScorer = topScorers.first()
            awards.add(
                AwardSummary(
                    awardType = "golden_boot",
                    awardName = "金靴奖",
                    playerId = topScorer.playerId,
                    playerName = topScorer.playerName,
                    clubId = topScorer.clubId,
                    statValue = topScorer.goals
                )
            )
        }

        // 联赛冠军
        for (league in leagueStandings) {
            val champion = league.standings.firstOrNull() ?: continue
            awards.add(
                AwardSummary(
                    awardType = "league_champion_${league.leagueId}",
                    awardName = "${league.leagueName}冠军",
                    playerId = 0,
                    playerName = "",
                    clubId = champion.clubId,
                    statValue = champion.points
                )
            )
        }

        return awards
    }

    /**
     * 生成升降级俱乐部 ID 列表。
     *
     * V1 简化：取首个联赛（玩家所在联赛）末 3 名降级。
     * 升级列表 V1 暂为空（次级联赛未模拟）。
     */
    private suspend fun generatePromotionRelegation(
        ctx: AdvanceContext,
        leagueStandings: List<LeagueStandingSummary>
    ): Pair<List<Int>, List<Int>> {
        val primaryLeague = leagueStandings.firstOrNull() ?: return emptyList<Int>() to emptyList()
        val relegationZone = 3
        val relegations = primaryLeague.standings
            .sortedByDescending { it.position }
            .take(relegationZone)
            .map { it.clubId }
        return emptyList<Int>() to relegations
    }

    // ==================== 工具方法 ====================

    /** 获取赛季标签（"2002/03" 格式） */
    private suspend fun getSeasonLabel(seasonId: Int): String {
        return try {
            val season = databaseManager.historySeasonDao().getSeason(seasonId)
            season?.label ?: "${2002 + seasonId - 1}/${((2002 + seasonId) % 100).toString().padStart(2, '0')}"
        } catch (e: Exception) {
            "赛季$seasonId"
        }
    }

    /** 批量获取俱乐部名称 */
    private suspend fun getClubNames(clubIds: List<Int>): Map<Int, String> {
        if (clubIds.isEmpty()) return emptyMap()
        return try {
            val clubDao = databaseManager.historyClubDao()
            clubIds.associateWith { id ->
                clubDao.getClub(id)?.clubName ?: "俱乐部$id"
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取俱乐部名称失败：${e.message}")
            clubIds.associateWith { "俱乐部$it" }
        }
    }

    /** 批量获取球员名称 */
    private suspend fun getPlayerNames(playerIds: List<Int>): Map<Int, String> {
        if (playerIds.isEmpty()) return emptyMap()
        return try {
            val playerDao = databaseManager.historyPlayerDao()
            playerIds.associateWith { id ->
                playerDao.getPlayer(id)?.displayName
                    ?: playerDao.getPlayer(id)?.realName
                    ?: "球员$id"
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取球员名称失败：${e.message}")
            playerIds.associateWith { "球员$it" }
        }
    }

    /** 获取赛事名称 */
    private suspend fun getCompetitionName(competitionId: Int): String {
        return try {
            databaseManager.historyCompetitionDao().getCompetition(competitionId)?.name
                ?: "赛事$competitionId"
        } catch (e: Exception) {
            "赛事$competitionId"
        }
    }

    companion object {
        private const val TAG = "SeasonSummaryGenerator"
    }
}
