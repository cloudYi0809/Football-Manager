package com.greendynasty.football.season.init

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveLeagueTableEntity
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.ui.schedule.generator.LeagueScheduleGenerator
import com.greendynasty.football.ui.schedule.model.ScheduleConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T19 新赛季初始化器（V0.2 §七）
 *
 * 赛季归档完成后初始化新赛季：
 * 1. 清空 save.db 旧赛季数据（save_match / save_league_table / save_cup_tie）
 * 2. 推进赛季 ID（currentSeasonId + 1）
 * 3. 复用 T06 [LeagueScheduleGenerator] 生成新赛季双循环赛程
 * 4. 初始化空积分榜
 * 5. 更新 save_world_state.current_season_id
 *
 * V1 简化：
 * - 俱乐部列表从当前赛季 save_league_table 沿用（无升降级）
 * - 新赛季开始日期从 history.season 表读取，不存在则当前日期 + 1 年
 * - 杯赛赛程 V1 暂不生成（由 T06 杯赛生成器独立触发）
 *
 * @param databaseManager 三库管理入口
 * @param scheduleConfig 赛程配置（轮次间隔/连续主客限制等）
 */
class NewSeasonInitializer(
    private val databaseManager: DatabaseManager,
    private val scheduleConfig: ScheduleConfig = ScheduleConfig.DEFAULT
) {

    private val generator = LeagueScheduleGenerator(scheduleConfig)

    /**
     * 初始化新赛季。
     *
     * @param ctx 推进上下文（赛季归档完成后调用）
     * @return 新赛季已生成的比赛数；-1 表示初始化失败
     */
    suspend fun initializeNewSeason(ctx: AdvanceContext): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext -1

        try {
            val oldSeasonId = ctx.currentSeasonId
            val newSeasonId = oldSeasonId + 1

            // 1. 清空旧赛季数据
            clearOldSeasonData(ctx.saveId, oldSeasonId, saveDb)

            // 2. 确定参赛俱乐部列表（从旧赛季积分榜沿用）
            val clubIds = getClubListFromLeagueTable(ctx.saveId, oldSeasonId, saveDb)
            if (clubIds.isEmpty()) {
                Log.w(TAG, "无法获取俱乐部列表，新赛季赛程未生成")
                updateSeasonId(ctx.saveId, newSeasonId, saveDb)
                return@withContext 0
            }

            // 3. 确定新赛季开始日期
            val seasonStart = getNewSeasonStartDate(newSeasonId, ctx.currentDate)

            // 4. 生成新赛季联赛赛程（使用首个活跃联赛 ID 作为 competitionId）
            val competitionId = ctx.activeLeagueIds.firstOrNull() ?: 1
            val matches = generator.generateDoubleRoundRobin(
                clubIds = clubIds,
                seasonStart = seasonStart,
                competitionId = competitionId,
                seasonId = newSeasonId
            ).map { it.copy(saveId = ctx.saveId) }

            // 标记玩家俱乐部比赛
            val tagged = matches.map { m ->
                if (m.homeClubId == ctx.managerClubId || m.awayClubId == ctx.managerClubId) {
                    m.copy(isPlayerMatch = 1)
                } else m
            }
            saveDb.saveMatchDao().insertAll(tagged)

            // 5. 初始化空积分榜
            val emptyTable = clubIds.map { clubId ->
                SaveLeagueTableEntity(
                    saveId = ctx.saveId,
                    seasonId = newSeasonId,
                    competitionId = competitionId,
                    clubId = clubId,
                    position = 0,
                    updatedAt = LocalDate.now().toString()
                )
            }
            saveDb.saveLeagueTableDao().insertAll(emptyTable)

            // 6. 更新 save_world_state.current_season_id
            updateSeasonId(ctx.saveId, newSeasonId, saveDb)

            Log.i(TAG, "新赛季 $newSeasonId 初始化完成：${tagged.size} 场比赛，${clubIds.size} 支球队")
            tagged.size
        } catch (e: Exception) {
            Log.e(TAG, "新赛季初始化失败：${e.message}", e)
            -1
        }
    }

    /**
     * 清空旧赛季数据（save_match / save_league_table / save_cup_tie）。
     */
    private suspend fun clearOldSeasonData(
        saveId: Int,
        seasonId: Int,
        saveDb: com.greendynasty.football.data.save.SaveDatabase
    ) {
        runCatching {
            saveDb.saveMatchDao().deleteBySeason(saveId, seasonId)
        }.onFailure { Log.w(TAG, "清理旧赛季比赛失败：${it.message}") }

        runCatching {
            saveDb.saveLeagueTableDao().deleteBySeason(saveId, seasonId)
        }.onFailure { Log.w(TAG, "清理旧赛季积分榜失败：${it.message}") }

        runCatching {
            saveDb.saveCupTieDao().deleteBySeason(saveId, seasonId)
        }.onFailure { Log.w(TAG, "清理旧赛季杯赛对阵失败：${it.message}") }
    }

    /**
     * 从旧赛季积分榜获取俱乐部列表（沿用，V1 无升降级）。
     */
    private suspend fun getClubListFromLeagueTable(
        saveId: Int,
        seasonId: Int,
        saveDb: com.greendynasty.football.data.save.SaveDatabase
    ): List<Int> {
        return runCatching {
            saveDb.saveLeagueTableDao()
                .getBySeasonAndCompetition(saveId, seasonId, 1)
                .map { it.clubId }
        }.getOrDefault(emptyList())
    }

    /**
     * 获取新赛季开始日期。
     *
     * 优先从 history.season 表读取，不存在则默认为当前日期 + 1 年的 8 月 1 日。
     */
    private suspend fun getNewSeasonStartDate(newSeasonId: Int, currentDate: LocalDate): LocalDate {
        return runCatching {
            val season = databaseManager.historySeasonDao().getSeason(newSeasonId)
            season?.startDate?.let { LocalDate.parse(it) }
        }.getOrNull() ?: run {
            // 默认：次年 8 月 1 日开始新赛季
            LocalDate.of(currentDate.year + 1, 8, 1)
        }
    }

    /**
     * 更新 save_world_state 的当前赛季 ID。
     */
    private suspend fun updateSeasonId(
        saveId: Int,
        newSeasonId: Int,
        saveDb: com.greendynasty.football.data.save.SaveDatabase
    ) {
        runCatching {
            saveDb.saveWorldStateDao().updateSeason(
                saveId,
                newSeasonId,
                LocalDate.now().toString()
            )
        }.onFailure { Log.w(TAG, "更新赛季 ID 失败：${it.message}") }
    }

    companion object {
        private const val TAG = "NewSeasonInitializer"
    }
}
