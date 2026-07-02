package com.greendynasty.football.season.ranking

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.cache.entity.RankingCacheEntity
import com.greendynasty.football.season.summary.LeagueStandingSummary
import com.greendynasty.football.season.summary.SeasonSummary
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

/**
 * T19 榜单冻结器（V0.2 §七.4）
 *
 * 赛季归档时将最终积分榜 / 射手榜 / 助攻榜快照冻结到 cache.db 的 `ranking_cache` 表，
 * 供历史赛季查询快速读取，避免每次从 season_archive.summary_json 反序列化。
 *
 * 冻结的榜单永不过期（expiresAt = null），表示历史快照常驻缓存。
 *
 * @param databaseManager 三库管理入口（cache.db 可写）
 */
class RankingFreezer(
    private val databaseManager: DatabaseManager
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 冻结赛季榜单到缓存。
     *
     * @param ctx 推进上下文
     * @param summary 已生成的赛季摘要（含积分榜/射手榜/助攻榜）
     * @return 已冻结的榜单条数
     */
    suspend fun freeze(ctx: AdvanceContext, summary: SeasonSummary): Int =
        withContext(Dispatchers.IO) {
            var frozenCount = 0
            val updatedAt = dateFormatter.format(ctx.currentDate)

            try {
                val cacheDao = databaseManager.rankingCacheDao()
                val caches = mutableListOf<RankingCacheEntity>()

                // 1. 冻结联赛积分榜（按赛事分组）
                for (league in summary.leagueStandings) {
                    val cacheKey = buildStandingKey(ctx.saveId, ctx.currentSeasonId, league.leagueId)
                    val rankingJson = json.encodeToString(
                        LeagueStandingSummary.serializer(),
                        league
                    )
                    caches.add(
                        RankingCacheEntity(
                            cacheKey = cacheKey,
                            rankingJson = rankingJson,
                            updatedAt = updatedAt,
                            expiresAt = null // 历史快照永不过期
                        )
                    )
                }

                // 2. 冻结射手榜
                if (summary.topScorers.isNotEmpty()) {
                    val cacheKey = buildScorerKey(ctx.saveId, ctx.currentSeasonId, "scorers")
                    val rankingJson = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.greendynasty.football.season.summary.ScorerListSummary.serializer()
                        ),
                        summary.topScorers
                    )
                    caches.add(
                        RankingCacheEntity(
                            cacheKey = cacheKey,
                            rankingJson = rankingJson,
                            updatedAt = updatedAt,
                            expiresAt = null
                        )
                    )
                }

                // 3. 冻结助攻榜
                if (summary.topAssists.isNotEmpty()) {
                    val cacheKey = buildScorerKey(ctx.saveId, ctx.currentSeasonId, "assists")
                    val rankingJson = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.greendynasty.football.season.summary.ScorerListSummary.serializer()
                        ),
                        summary.topAssists
                    )
                    caches.add(
                        RankingCacheEntity(
                            cacheKey = cacheKey,
                            rankingJson = rankingJson,
                            updatedAt = updatedAt,
                            expiresAt = null
                        )
                    )
                }

                // 批量写入缓存
                if (caches.isNotEmpty()) {
                    cacheDao.insertAll(caches)
                    frozenCount = caches.size
                }

                Log.i(TAG, "赛季 ${ctx.currentSeasonId} 榜单冻结完成：$frozenCount 条")
            } catch (e: Exception) {
                Log.e(TAG, "榜单冻结失败：${e.message}", e)
            }

            frozenCount
        }

    /** 构建积分榜缓存键：season_archive_{saveId}_{seasonId}_league_{leagueId} */
    private fun buildStandingKey(saveId: Int, seasonId: Int, leagueId: String): String {
        return "season_archive_${saveId}_${seasonId}_league_${leagueId}"
    }

    /** 构建射手/助攻榜缓存键：season_archive_{saveId}_{seasonId}_{type} */
    private fun buildScorerKey(saveId: Int, seasonId: Int, type: String): String {
        return "season_archive_${saveId}_${seasonId}_${type}"
    }

    companion object {
        private const val TAG = "RankingFreezer"
    }
}
