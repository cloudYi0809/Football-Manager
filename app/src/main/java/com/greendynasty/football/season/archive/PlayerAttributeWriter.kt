package com.greendynasty.football.season.archive

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T19 球员属性回写器（V0.2 §七）
 *
 * 赛季归档时将 save.db 中球员当前属性（CA/PA）回写到 history.db 的 `player_attributes` 表，
 * 按 (player_id, season_id) 唯一约束存储，为历史赛季查询提供属性快照。
 *
 * 回写策略：
 * 1. 从 save_player_state 读取玩家俱乐部球员的当前 CA/PA
 * 2. 从 history.db 读取该球员最近赛季的完整属性作为基准
 * 3. 用当前 CA/PA 覆盖基准属性，生成新赛季快照
 * 4. 通过 [HistoryDbWriter] 临时解除只读约束后批量写入
 *
 * V1 简化：仅回写玩家俱乐部球员（活跃范围），T0D 接入后扩展到全部活跃范围球员。
 *
 * @param databaseManager 三库管理入口
 */
class PlayerAttributeWriter(
    private val databaseManager: DatabaseManager
) {

    /**
     * 将球员当前属性回写到 history.db。
     *
     * @param ctx 推进上下文
     * @return 已回写的球员属性条数
     */
    suspend fun writeAttributes(ctx: AdvanceContext): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0
        val historyDb = runCatching { databaseManager.getHistoryDatabase() }.getOrNull()
            ?: return@withContext 0

        var writtenCount = 0

        try {
            // 1. 读取玩家俱乐部全部球员的当前状态
            val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, ctx.managerClubId)
            if (players.isEmpty()) {
                Log.i(TAG, "玩家俱乐部无球员，跳过属性回写")
                return@withContext 0
            }

            // 2. 批量查询球员最近赛季属性作为基准
            val playerIds = players.map { it.playerId }
            val baseAttrs = historyDb.playerDao().getLatestAttributesBatch(playerIds)
            val baseMap = baseAttrs.associateBy { it.playerId }

            // 3. 构建新赛季属性快照
            val newAttrs = players.mapNotNull { state ->
                val base = baseMap[state.playerId]
                if (base != null) {
                    // 以最近赛季属性为基准，更新 CA/PA 和赛季 ID
                    base.copy(
                        id = 0, // 自增主键，由数据库分配
                        seasonId = ctx.currentSeasonId,
                        ca = state.currentCa,
                        pa = state.currentPa
                    )
                } else {
                    // 无基准属性：使用默认值构建（V1 简化）
                    PlayerAttributesEntity(
                        playerId = state.playerId,
                        seasonId = ctx.currentSeasonId,
                        ca = state.currentCa,
                        pa = state.currentPa
                    )
                }
            }

            if (newAttrs.isEmpty()) return@withContext 0

            // 4. 通过 HistoryDbWriter 临时解除只读后批量写入
            val writer = HistoryDbWriter(historyDb)
            writer.openForWrite()
            try {
                writer.insertPlayerAttributes(newAttrs)
                writtenCount = newAttrs.size
            } finally {
                writer.closeAndRestoreReadOnly()
            }

            Log.i(TAG, "赛季 ${ctx.currentSeasonId} 球员属性回写完成：$writtenCount 条")
        } catch (e: Exception) {
            Log.e(TAG, "球员属性回写失败：${e.message}", e)
        }

        writtenCount
    }

    companion object {
        private const val TAG = "PlayerAttributeWriter"
    }
}
