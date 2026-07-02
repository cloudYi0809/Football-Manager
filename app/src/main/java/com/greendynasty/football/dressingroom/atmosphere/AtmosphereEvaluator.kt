package com.greendynasty.football.dressingroom.atmosphere

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.dressingroom.model.AtmosphereEvaluation
import com.greendynasty.football.dressingroom.model.AtmosphereLevel
import com.greendynasty.football.dressingroom.model.DressingRoomAtmosphereEntity
import com.greendynasty.football.dressingroom.model.DressingRoomConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T23 更衣室氛围评估器（V0.2 + T23 任务要求 §二.3 + 实现方案 §四.4）。
 *
 * 综合 4 个指标判定更衣室氛围等级：
 * - 球队平均士气（team_morale 0-100）
 * - 化学反应指数（chemistry_index 0-1，来自 [com.greendynasty.football.dressingroom.chemistry.ChemistryCalculator]）
 * - 领袖影响力（leader_influence 0-100，来自 [com.greendynasty.football.dressingroom.leader.DressingRoomLeaderDetector]）
 * - 不满球员数（unrest_count，来自 player_morale.unrest_accumulator ≥ 阈值）
 *
 * 4 档氛围等级（[AtmosphereLevel]）：
 * - HARMONIOUS 和谐：士气 ≥75 + 化学反应 ≥0.7 + 无不满（performanceModifier=1.05）
 * - NORMAL 一般：士气 ≥50 + 不满 ≤2（performanceModifier=1.00）
 * - TENSE 紧张：士气 ≥30 或 不满 ≥3（performanceModifier=0.92）
 * - SPLIT 分裂：士气 <30 或 不满 ≥5（performanceModifier=0.80）
 *
 * 稳定指数（stability_index 0-100）：综合 4 指标加权，提供给 T22 董事会满意度评估。
 *
 * 触发时机：月度评估 + 重要事件后（如夺冠 / 解雇 / 重大冲突）。
 *
 * @param databaseManager 三库管理入口
 * @param config 更衣室配置
 */
class AtmosphereEvaluator(
    private val databaseManager: DatabaseManager,
    private val config: DressingRoomConfig = DressingRoomConfig.DEFAULT
) {

    // ==================== 1. 氛围评估 ====================

    /**
     * 评估俱乐部更衣室氛围（V0.2 + T23 任务要求 §二.3）。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param chemistryIndex 化学反应指数 0-1（来自 ChemistryCalculator）
     * @param leaderInfluence 领袖影响力 0-100（来自 DressingRoomLeaderDetector）
     * @param currentDate 当前游戏日期
     * @return 氛围评估结果（含稳定指数）
     */
    suspend fun evaluate(
        saveId: Int,
        clubId: Int,
        chemistryIndex: Double,
        leaderInfluence: Int,
        currentDate: LocalDate
    ): AtmosphereEvaluation = withContext(Dispatchers.IO) {
        val moraleDao = databaseManager.playerMoraleDao()

        // 1. 球队平均士气
        val teamMorale = moraleDao.getAverageMorale(saveId, clubId)?.toInt() ?: 50

        // 2. 不满球员数（unrestAccumulator ≥ 阈值）
        val unrestCount = moraleDao.getPlayersNeedingTalk(
            saveId, clubId, config.unrest.unrestThreshold
        ).size

        // 3. 判定氛围等级
        val level = AtmosphereLevel.fromMetrics(teamMorale, chemistryIndex, unrestCount)

        // 4. 计算稳定指数（0-100）
        val stabilityIndex = computeStabilityIndex(teamMorale, chemistryIndex, leaderInfluence, unrestCount)

        AtmosphereEvaluation(
            level = level,
            teamMorale = teamMorale,
            chemistryIndex = chemistryIndex,
            leaderInfluence = leaderInfluence,
            unrestCount = unrestCount,
            stabilityIndex = stabilityIndex
        )
    }

    /**
     * 评估并持久化氛围快照（V0.2 + 实现方案 §四.4）。
     *
     * 触发时机：月度评估（T07 每月推进调用）。
     *
     * @param seasonId 赛季 ID
     * @return 持久化后的氛围快照实体
     */
    suspend fun evaluateAndPersist(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        chemistryIndex: Double,
        leaderInfluence: Int,
        currentDate: LocalDate
    ): DressingRoomAtmosphereEntity = withContext(Dispatchers.IO) {
        val evaluation = evaluate(saveId, clubId, chemistryIndex, leaderInfluence, currentDate)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val entity = DressingRoomAtmosphereEntity(
            saveId = saveId,
            clubId = clubId,
            snapshotDate = dateStr,
            atmosphereLevel = evaluation.level.name,
            teamMorale = evaluation.teamMorale,
            chemistryIndex = evaluation.chemistryIndex,
            leaderInfluence = evaluation.leaderInfluence,
            unrestCount = evaluation.unrestCount,
            stabilityIndex = evaluation.stabilityIndex,
            snapshotSeason = seasonId
        )
        databaseManager.dressingRoomAtmosphereDao().insert(entity)

        // 同步更新 save_club_state.dressing_room_morale 冗余字段
        try {
            val clubStateDao = databaseManager.saveClubStateDao()
            val clubState = clubStateDao.getByClub(saveId, clubId)
            if (clubState != null) {
                // dressingRoomMorale 字段表示更衣室士气（用 teamMorale 简化表示）
                // 使用更新查询以避免覆盖其他字段（V1 简化：直接调用 updateBalance 等更新方法不存在时
                // 用整体 update 替代；此处采用重写法）
                // 由于 SaveClubStateDao 没有 updateDressingRoomMorale 专用方法，使用 update(entity)
                clubStateDao.update(clubState.copy(dressingRoomMorale = evaluation.teamMorale))
            }
        } catch (_: Exception) {
            // 静默失败：氛围快照已写入，club_state 同步失败不影响主流程
        }

        entity
    }

    // ==================== 2. 稳定指数计算 ====================

    /**
     * 计算稳定指数 0-100（综合 4 指标加权，提供给 T22 董事会满意度评估）。
     *
     * V1 简化权重：
     * - 球队士气 0.40
     * - 化学反应 0.20
     * - 领袖影响力 0.20
     * - 不满数（反向）0.20
     *
     * @param teamMorale 球队平均士气 0-100
     * @param chemistryIndex 化学反应指数 0-1
     * @param leaderInfluence 领袖影响力 0-100
     * @param unrestCount 不满球员数
     * @return 稳定指数 0-100
     */
    fun computeStabilityIndex(
        teamMorale: Int,
        chemistryIndex: Double,
        leaderInfluence: Int,
        unrestCount: Int
    ): Int {
        // 士气分项 0-100
        val moraleScore = teamMorale.coerceIn(0, 100).toDouble()

        // 化学反应分项 0-100
        val chemistryScore = (chemistryIndex * 100).coerceIn(0.0, 100.0)

        // 领袖影响力分项 0-100
        val leaderScore = leaderInfluence.coerceIn(0, 100).toDouble()

        // 不满数分项（反向：0 个不满=100，每增 1 个扣 10，钳制 0-100）
        val unrestScore = (100 - unrestCount * 10).coerceIn(0, 100).toDouble()

        val stability = (
            moraleScore * 0.40 +
                chemistryScore * 0.20 +
                leaderScore * 0.20 +
                unrestScore * 0.20
            ).coerceIn(0.0, 100.0)

        return stability.toInt()
    }

    // ==================== 3. 氛围历史查询 ====================

    /**
     * 查询俱乐部最近氛围快照（用于 UI 历史曲线）。
     */
    suspend fun getLatestSnapshots(
        saveId: Int,
        clubId: Int,
        limit: Int = 12
    ): List<DressingRoomAtmosphereEntity> = withContext(Dispatchers.IO) {
        databaseManager.dressingRoomAtmosphereDao().getLatest(saveId, clubId, limit)
    }

    /**
     * 查询俱乐部最近一次氛围快照。
     */
    suspend fun getLatestSnapshot(
        saveId: Int,
        clubId: Int
    ): DressingRoomAtmosphereEntity? = withContext(Dispatchers.IO) {
        databaseManager.dressingRoomAtmosphereDao().getLatest(saveId, clubId, 1).firstOrNull()
    }
}
