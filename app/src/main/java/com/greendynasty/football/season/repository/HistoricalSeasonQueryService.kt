package com.greendynasty.football.season.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.CompressedMatchEntity
import com.greendynasty.football.data.save.entity.SeasonArchiveEntity
import com.greendynasty.football.season.compress.CompressedMatchEvent
import com.greendynasty.football.season.summary.LeagueStandingSummary
import com.greendynasty.football.season.summary.ScorerListSummary
import com.greendynasty.football.season.summary.SeasonSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * T19 历史赛季查询服务（V0.2 §七.5）
 *
 * 旧赛季只读访问入口，提供以下查询能力：
 * 1. 赛季归档记录查询（season_archive 表）
 * 2. 赛季摘要查询（反序列化 summary_json）
 * 3. 历史积分榜查询（优先从 cache.db ranking_cache 读取，回退到 summary_json）
 * 4. 历史射手榜 / 助攻榜查询
 * 5. 压缩比赛结果查询（compressed_match 表）
 *
 * 所有方法均为只读，不修改任何数据。
 *
 * @param databaseManager 三库管理入口
 * @param seasonRepository 赛季归档仓库
 */
class HistoricalSeasonQueryService(
    private val databaseManager: DatabaseManager,
    private val seasonRepository: SeasonRepository
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * 获取赛季归档记录。
     */
    suspend fun getSeasonArchive(saveUuid: String, seasonId: Int): SeasonArchiveEntity? {
        return seasonRepository.getSeasonArchive(saveUuid, seasonId)
    }

    /**
     * 获取赛季摘要。
     */
    suspend fun getSeasonSummary(saveUuid: String, seasonId: Int): SeasonSummary? {
        return seasonRepository.getSeasonSummary(saveUuid, seasonId)
    }

    /**
     * 获取全部归档赛季列表。
     */
    suspend fun getAllArchivedSeasons(saveUuid: String): List<SeasonArchiveEntity> {
        return seasonRepository.getAllArchivedSeasons(saveUuid)
    }

    /**
     * 获取历史积分榜（优先从 cache.db 读取冻结快照）。
     *
     * @param saveId 存档 ID（Int，用于构建缓存键）
     * @param seasonId 赛季 ID
     * @param leagueId 联赛 ID
     * @return 积分榜条目列表，不存在返回 null
     */
    suspend fun getHistoricalStandings(
        saveId: Int,
        seasonId: Int,
        leagueId: String
    ): List<com.greendynasty.football.season.summary.StandingEntry>? = withContext(Dispatchers.IO) {
        // 1. 优先从 cache.db ranking_cache 读取冻结快照
        val cacheKey = "season_archive_${saveId}_${seasonId}_league_${leagueId}"
        val cached = runCatching {
            databaseManager.rankingCacheDao().get(cacheKey)
        }.getOrNull()

        if (cached != null) {
            val standing = runCatching {
                json.decodeFromString(LeagueStandingSummary.serializer(), cached.rankingJson)
            }.getOrNull()
            if (standing != null) return@withContext standing.standings
        }

        // 2. 回退到 season_archive.summary_json
        val summary = seasonRepository.getSeasonSummary(saveId.toString(), seasonId)
        summary?.leagueStandings?.find { it.leagueId == leagueId }?.standings
    }

    /**
     * 获取历史射手榜。
     */
    suspend fun getHistoricalTopScorers(
        saveUuid: String,
        seasonId: Int
    ): List<ScorerListSummary>? {
        val summary = seasonRepository.getSeasonSummary(saveUuid, seasonId) ?: return null
        return summary.topScorers
    }

    /**
     * 获取历史助攻榜。
     */
    suspend fun getHistoricalTopAssists(
        saveUuid: String,
        seasonId: Int
    ): List<ScorerListSummary>? {
        val summary = seasonRepository.getSeasonSummary(saveUuid, seasonId) ?: return null
        return summary.topAssists
    }

    /**
     * 查询压缩后的比赛结果（只读）。
     *
     * @param saveId 存档 ID
     * @param seasonId 赛季 ID
     * @return 压缩比赛实体列表
     */
    suspend fun getCompressedMatchesBySeason(
        saveId: Int,
        seasonId: Int
    ): List<CompressedMatchEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
            saveDb.compressedMatchDao().getBySeason(saveId, seasonId)
        }.getOrDefault(emptyList())
    }

    /**
     * 查询单场压缩比赛并反序列化事件。
     *
     * @param matchId 比赛 ID
     * @return 压缩比赛事件，不存在返回 null
     */
    suspend fun getCompressedMatch(matchId: Int): CompressedMatchEvent? = withContext(Dispatchers.IO) {
        val entity = runCatching {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null
            saveDb.compressedMatchDao().getByMatch(matchId)
        }.getOrNull() ?: return@withContext null

        runCatching {
            json.decodeFromString(CompressedMatchEvent.serializer(), entity.summaryJson)
        }.getOrNull()
    }
}
