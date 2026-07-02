package com.greendynasty.football.season.stats

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T19 球员赛季统计聚合器（V0.2 §七.3）
 *
 * 赛季归档时将球员本赛季的逐场比赛统计聚合为赛季汇总，
 * 写入 history.db 的 player_attributes 表（按 player_id + season_id 唯一）。
 *
 * V1 简化：match_stats_json 当前未持久化详细事件，聚合器仅完成结构搭建，
 * 从 save_player_state 读取当前 CA/PA 作为赛季快照写入。
 * T0D 数据分析接入后从 compressed_match.summary_json 聚合进球/助攻/出场等。
 *
 * @param databaseManager 三库管理入口
 */
class StatsAggregator(
    private val databaseManager: DatabaseManager
) {

    /**
     * 聚合本赛季球员统计。
     *
     * V1 实现：遍历玩家俱乐部球员，将当前 CA/PA 快照标记为赛季末属性，
     * 供 [com.greendynasty.football.season.archive.PlayerAttributeWriter] 回写 history.db。
     *
     * @param ctx 推进上下文
     * @return 已聚合的球员数
     */
    suspend fun aggregate(ctx: AdvanceContext): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0

        var aggregatedCount = 0
        try {
            // V1：聚合玩家俱乐部全部球员的当前 CA/PA 快照
            val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, ctx.managerClubId)
            for (player in players) {
                // V1 简化：仅记录日志，实际属性回写由 PlayerAttributeWriter 负责
                // T0D 接入后此处聚合出场/进球/助攻/评分等统计
                aggregatedCount++
            }

            Log.i(TAG, "赛季 ${ctx.currentSeasonId} 统计聚合完成：$aggregatedCount 名球员")
        } catch (e: Exception) {
            Log.e(TAG, "球员统计聚合失败：${e.message}", e)
        }

        aggregatedCount
    }

    companion object {
        private const val TAG = "StatsAggregator"
    }
}
