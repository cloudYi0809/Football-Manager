package com.greendynasty.football.season.compress

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.CompressedMatchEntity
import com.greendynasty.football.season.config.ArchiveConfig
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * T19 比赛事件压缩器（V0.2 §七.2）
 *
 * 将本赛季已完赛比赛的详细事件压缩为 [CompressedMatchEvent]，仅保留：
 * - 比分 + xG
 * - 进球（minute + scorer + assist）
 * - 红黄牌（minute + player + type）
 * - 关键评分 Top N
 *
 * 压缩后写入 `compressed_match` 表，并清空 `save_match.match_stats_json` 以回收空间。
 *
 * V1 简化：match_stats_json 当前未持久化详细事件，本压缩器主要完成结构搭建与空事件写入，
 * T0D 数据分析接入后从原始事件流提取压缩字段。
 *
 * @param databaseManager 三库管理入口
 * @param config 归档配置（控制 Top N 评分数量等）
 */
class EventCompressor(
    private val databaseManager: DatabaseManager,
    private val config: ArchiveConfig
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * 压缩本赛季所有已完赛比赛事件
     *
     * @param ctx 推进上下文
     * @return 已压缩的比赛数
     */
    suspend fun compress(ctx: AdvanceContext): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0
        var compressedCount = 0

        try {
            val seasonMatches = saveDb.saveMatchDao()
                .getBySeason(ctx.saveId, ctx.currentSeasonId)
                .filter { it.status == "finished" }

            if (seasonMatches.isEmpty()) return@withContext 0

            val archiveDate = LocalDate.now().toString()
            val topN = config.compression.keepTopRatingsCount.coerceAtLeast(1)

            val compressedEntities = seasonMatches.mapNotNull { match ->
                try {
                    // V1：match_stats_json 为 null 时生成空事件结构
                    val statsJson = match.matchStatsJson
                    val compressed = if (statsJson.isNullOrEmpty()) {
                        CompressedMatchEvent(
                            matchId = match.matchId,
                            homeScore = match.homeScore ?: 0,
                            awayScore = match.awayScore ?: 0,
                            homeXg = 0.0,
                            awayXg = 0.0,
                            goals = emptyList(),
                            cards = emptyList(),
                            topRatedPlayers = emptyList()
                        )
                    } else {
                        parseMatchStats(match.matchId, match.homeScore, match.awayScore, statsJson, topN)
                    }

                    CompressedMatchEntity(
                        saveId = ctx.saveId,
                        seasonId = ctx.currentSeasonId,
                        matchId = match.matchId,
                        homeClubId = match.homeClubId,
                        awayClubId = match.awayClubId,
                        homeScore = compressed.homeScore,
                        awayScore = compressed.awayScore,
                        homeXg = compressed.homeXg,
                        awayXg = compressed.awayXg,
                        summaryJson = json.encodeToString(CompressedMatchEvent.serializer(), compressed),
                        createdAt = archiveDate
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "压缩比赛失败：matchId=${match.matchId}, ${e.message}")
                    null
                }
            }

            // 批量写入压缩事件
            if (compressedEntities.isNotEmpty()) {
                saveDb.compressedMatchDao().insertAll(compressedEntities)
                compressedCount = compressedEntities.size
            }

            // 清空原始详细统计字段（V0.2 §七.2 要求）
            if (config.compression.deleteDetailedEvents) {
                saveDb.saveMatchDao().clearMatchStatsBySeason(ctx.saveId, ctx.currentSeasonId)
            }

            Log.i(TAG, "赛季 ${ctx.currentSeasonId} 压缩完成：$compressedCount 场比赛")
        } catch (e: Exception) {
            Log.e(TAG, "比赛事件压缩失败：${e.message}", e)
        }

        compressedCount
    }

    /**
     * 解析 match_stats_json 提取压缩事件（V1 占位实现）。
     *
     * V1：match_stats_json 当前为 null，本方法仅在结构上预留解析逻辑。
     * T0D 数据分析接入后实现完整的事件流解析。
     */
    private fun parseMatchStats(
        matchId: Int,
        homeScore: Int?,
        awayScore: Int?,
        statsJson: String,
        topN: Int
    ): CompressedMatchEvent {
        // V1 占位：直接构造空事件结构（比分 / xG 已知，事件流暂为空）
        // TODO: T0D 解析 JSON 中的 goals / cards / ratings 字段
        return CompressedMatchEvent(
            matchId = matchId,
            homeScore = homeScore ?: 0,
            awayScore = awayScore ?: 0,
            homeXg = 0.0,
            awayXg = 0.0,
            goals = emptyList(),
            cards = emptyList(),
            topRatedPlayers = emptyList()
        )
    }

    companion object {
        private const val TAG = "EventCompressor"
    }
}
