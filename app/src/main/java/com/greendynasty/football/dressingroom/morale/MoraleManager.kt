package com.greendynasty.football.dressingroom.morale

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.dressingroom.model.DressingRoomConfig
import com.greendynasty.football.dressingroom.model.MoraleFactorScores
import com.greendynasty.football.dressingroom.model.MoraleFactorWeights
import com.greendynasty.football.dressingroom.model.MoraleLevel
import com.greendynasty.football.dressingroom.model.PlayerMoraleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.logging.Logger

/**
 * T23 士气管理器（V0.2 + T23 任务要求 §二.1 + 实现方案 §四.2）。
 *
 * 负责球员综合士气 0-100 的计算与持久化，4 因子加权（裁剪自原方案 12 因子）：
 * - playing_time 上场时间（权重 0.40）：连续首发 +3 / 连续替补 -5 / 长期未上场低
 * - match_result 战绩（权重 0.30）：胜 +4 / 平 +1 / 负 -3 / 夺冠 +15
 * - contract 合同状况（权重 0.20）：合同年限 + 工资水平
 * - personal_event 个人事件（权重 0.10）：家庭 / 媒体 / 个人荣誉
 *
 * 5 档士气等级（[MoraleLevel]）：
 * - EXTREME_HIGH 极高 90+：超常发挥 +10%
 * - HIGH 高 75+：正常发挥
 * - MID 中 50+：略低发挥 -5%
 * - LOW 低 30+：发挥下降 -15%
 * - EXTREME_LOW 极低 <30：严重影响 -30%
 *
 * 阈值（T23 任务要求 §二.7）：
 * - 连续首发 +3（consecutiveStartsBonus=3，连续 3 场 +3）
 * - 连续替补 -5（consecutiveBenchedPenalty=-5，连续 3 场 -5）
 * - 夺冠 +15（winTitleBonus=15）
 * - 单场士气变化钳制 ±maxDeltaPerMatch=8，避免剧烈波动
 *
 * @param databaseManager 三库管理入口
 * @param config 更衣室配置
 */
class MoraleManager(
    private val databaseManager: DatabaseManager,
    private val config: DressingRoomConfig = DressingRoomConfig.DEFAULT
) {
    private val logger = Logger.getLogger("MoraleManager")

    // ==================== 1. 士气计算 ====================

    /**
     * 计算 4 因子士气评分（每个 0-1）。
     *
     * V1 简化：从 [SavePlayerStateEntity] 推导，不直接读 history.player_attributes。
     *
     * - playingTime：squad_role=starter=0.9 / backup=0.6 / prospect=0.5，连续首发 +0.05×n（钳制 1.0）
     * - matchResult：默认 0.5，胜 +0.1 / 平 +0.0 / 负 -0.1
     * - contract：合同剩余年限 >2=0.9 / 1-2=0.7 / <1=0.4，未签约=0.2
     * - personalEvent：默认 0.5（V1 暂无个人事件流）
     */
    fun computeFactorScores(
        playerState: SavePlayerStateEntity,
        consecutiveStarts: Int,
        consecutiveBenched: Int,
        lastMatchResult: MatchResult?
    ): MoraleFactorScores {
        // 1. 上场时间因子
        val basePlayingTime = when (playerState.squadRole) {
            "starter" -> 0.9
            "backup" -> 0.6
            "prospect" -> 0.5
            else -> 0.5
        }
        val startBonus = (consecutiveStarts * 0.05).coerceAtMost(0.1)
        val benchedPenalty = (consecutiveBenched * 0.05).coerceAtMost(0.3)
        val playingTime = (basePlayingTime + startBonus - benchedPenalty).coerceIn(0.0, 1.0)

        // 2. 战绩因子
        val matchResult = when (lastMatchResult?.outcome) {
            MatchOutcome.WIN -> 0.6
            MatchOutcome.DRAW -> 0.5
            MatchOutcome.LOSS -> 0.3
            null -> 0.5
        }

        // 3. 合同因子
        val contract = computeContractFactor(playerState)

        // 4. 个人事件因子（V1 简化默认 0.5）
        val personalEvent = 0.5

        return MoraleFactorScores(
            playingTime = playingTime,
            matchResult = matchResult,
            contract = contract,
            personalEvent = personalEvent
        )
    }

    /**
     * 计算合同因子 0-1。
     *
     * 基于合同剩余年限 + 工资相对水平（V1 简化：仅合同年限）。
     */
    private fun computeContractFactor(playerState: SavePlayerStateEntity): Double {
        val contractUntil = playerState.contractUntil ?: return 0.2 // 未签约
        return try {
            val until = LocalDate.parse(contractUntil)
            val today = LocalDate.now() // V1：简化用当前日期，正式版应传入游戏日期
            val monthsLeft = ChronoUnit.MONTHS.between(today, until).coerceAtLeast(0)
            when {
                monthsLeft > 24 -> 0.9
                monthsLeft > 12 -> 0.7
                monthsLeft > 0 -> 0.4
                else -> 0.2
            }
        } catch (_: Exception) {
            0.5
        }
    }

    /**
     * 按 [MoraleFactorWeights] 加权求和得到综合士气 0-100。
     */
    fun computeMorale(scores: MoraleFactorScores): Int {
        return scores.applyWeights(config.moraleWeights)
    }

    // ==================== 2. 比赛日士气影响 ====================

    /**
     * 比赛日士气影响（V0.2 + T23 任务要求 §二.7 + 实现方案 §四.2）。
     *
     * 触发时机：T02 比赛引擎每场结算后调用。
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param clubId 俱乐部 ID
     * @param isStarter 是否首发
     * @param minutesPlayed 上场分钟数
     * @param matchResult 比赛结果
     * @param rating 球员评分（可选）
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 更新后的士气分值 0-100
     */
    suspend fun onMatchPlayed(
        saveId: Int,
        playerId: Int,
        clubId: Int,
        isStarter: Boolean,
        minutesPlayed: Int,
        matchResult: MatchResult,
        rating: Double? = null,
        seasonId: Int,
        currentDate: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        val dao = databaseManager.playerMoraleDao()
        val existing = dao.getByPlayer(saveId, playerId)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // 1. 更新连续首发 / 替补计数
        val newStarts = if (isStarter) (existing?.consecutiveStarts ?: 0) + 1 else 0
        val newBenched = if (!isStarter && minutesPlayed < config.matchImpact.subMinutesThreshold) {
            (existing?.consecutiveBenched ?: 0) + 1
        } else 0

        // 2. 计算比赛日士气增量
        var delta = when (matchResult.outcome) {
            MatchOutcome.WIN -> config.matchImpact.winBonus
            MatchOutcome.DRAW -> config.matchImpact.drawBonus
            MatchOutcome.LOSS -> config.matchImpact.lossPenalty
        }

        // 连续首发 +3 阈值
        if (newStarts >= config.matchImpact.consecutiveStartsBonusThreshold) {
            delta += config.matchImpact.consecutiveStartsBonus
        }
        // 连续替补 -5 阈值
        if (newBenched >= config.matchImpact.consecutiveBenchedPenaltyThreshold) {
            delta += config.matchImpact.consecutiveBenchedPenalty // 负值
        }

        // 球员评分加成
        if (rating != null) {
            delta += when {
                rating >= config.matchImpact.ratingHighThreshold -> config.matchImpact.ratingHighBonus
                rating <= config.matchImpact.ratingLowThreshold -> config.matchImpact.ratingLowPenalty
                else -> 0
            }
        }

        // 3. 钳制单场士气变化 ±maxDeltaPerMatch
        delta = delta.coerceIn(-config.matchImpact.maxDeltaPerMatch, config.matchImpact.maxDeltaPerMatch)

        // 4. 计算新士气
        val oldMorale = existing?.morale ?: 50
        val newMorale = (oldMorale + delta).coerceIn(0, 100)
        val newLevel = MoraleLevel.fromScore(newMorale)

        // 5. 不满累积：连续替补 +1
        val unrestDelta = if (newBenched > 0) config.unrest.unrestPerConsecutiveBenched else 0

        // 6. 持久化
        val entity = existing?.copy(
            morale = newMorale,
            moraleLevel = newLevel.name,
            consecutiveStarts = newStarts,
            consecutiveBenched = newBenched,
            lastUpdatedDate = dateStr,
            lastUpdatedSeason = seasonId,
            unrestAccumulator = (existing.unrestAccumulator + unrestDelta).coerceAtLeast(0),
            pendingConversation = (existing.unrestAccumulator + unrestDelta) >= config.unrest.unrestThreshold
        ) ?: PlayerMoraleEntity(
            saveId = saveId,
            playerId = playerId,
            clubId = clubId,
            morale = newMorale,
            moraleLevel = newLevel.name,
            playingTimeFactor = if (isStarter) 0.9 else 0.5,
            matchResultFactor = when (matchResult.outcome) {
                MatchOutcome.WIN -> 0.6
                MatchOutcome.DRAW -> 0.5
                MatchOutcome.LOSS -> 0.3
            },
            contractFactor = 0.5,
            personalEventFactor = 0.5,
            chemistryScore = 0.5,
            unrestAccumulator = unrestDelta,
            pendingConversation = false,
            consecutiveStarts = newStarts,
            consecutiveBenched = newBenched,
            lastUpdatedDate = dateStr,
            lastUpdatedSeason = seasonId
        )
        dao.upsert(entity)

        // 7. 同步更新 save_player_state.morale 冗余字段
        try {
            databaseManager.savePlayerStateDao().updateMorale(saveId, playerId, newMorale)
        } catch (e: Exception) {
            logger.warning("同步更新 save_player_state.morale 失败：playerId=$playerId, ${e.message}")
        }

        newMorale
    }

    /**
     * 夺冠士气奖励（T23 任务要求：夺冠 +15）。
     *
     * 触发时机：T19 赛季归档时调用（如俱乐部夺得联赛冠军）。
     */
    suspend fun onWinTitle(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        val dao = databaseManager.playerMoraleDao()
        val all = dao.getByClub(saveId, clubId)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        all.forEach { entity ->
            val newMorale = (entity.morale + config.matchImpact.winTitleBonus).coerceIn(0, 100)
            val newLevel = MoraleLevel.fromScore(newMorale)
            dao.upsert(
                entity.copy(
                    morale = newMorale,
                    moraleLevel = newLevel.name,
                    lastUpdatedDate = dateStr,
                    lastUpdatedSeason = seasonId
                )
            )
        }
    }

    // ==================== 3. 日度衰减 ====================

    /**
     * 日度士气衰减（V0.2 + 实现方案 §四.2 daily_drift）。
     *
     * 士气向均值 50 缓慢回归，避免长期卡在极值。
     *
     * 由 T07 每日推进调用。
     */
    suspend fun applyDailyDrift(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        val dao = databaseManager.playerMoraleDao()
        val all = dao.getByClub(saveId, clubId)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val target = config.dailyDrift.regressionTarget
        val rate = config.dailyDrift.regressionRate
        val noise = config.dailyDrift.noiseRange

        all.forEach { entity ->
            if (entity.morale == target) return@forEach
            // 向均值回归
            val diff = target - entity.morale
            val drift = (diff * rate).toInt()
            // 加微小噪声（避免完全确定）
            val noiseDelta = if (noise > 0) (Math.random() * 2 * noise - noise).toInt() else 0
            val newMorale = (entity.morale + drift + noiseDelta).coerceIn(0, 100)
            if (newMorale != entity.morale) {
                val newLevel = MoraleLevel.fromScore(newMorale)
                dao.upsert(
                    entity.copy(
                        morale = newMorale,
                        moraleLevel = newLevel.name,
                        lastUpdatedDate = dateStr
                    )
                )
            }
        }
    }

    // ==================== 4. 队长影响力 ====================

    /**
     * 应用队长影响力到全队士气（V0.2 + 实现方案 §四.5 captain_influence）。
     *
     * - influence >60 → 全队 +3
     * - influence 40-60 → 全队 +1
     * - influence <40 → 无影响
     * - 队长士气 <30 → 全队 -5（队长带头崩盘）
     *
     * @param captainInfluence 队长影响力 0-100
     * @param captainMorale 队长士气 0-100
     */
    suspend fun applyCaptainInfluence(
        saveId: Int,
        clubId: Int,
        captainInfluence: Int,
        captainMorale: Int,
        seasonId: Int,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        val params = config.captainInfluence
        val teamDelta = when {
            captainMorale < params.captainLowMoraleThreshold -> -params.captainCollapsePenalty
            captainInfluence >= params.highInfluenceThreshold -> params.highInfluenceBonus
            captainInfluence >= params.midInfluenceThreshold -> params.midInfluenceBonus
            else -> 0
        }
        if (teamDelta == 0) return@withContext

        val dao = databaseManager.playerMoraleDao()
        val all = dao.getByClub(saveId, clubId)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        all.forEach { entity ->
            val newMorale = (entity.morale + teamDelta).coerceIn(0, 100)
            val newLevel = MoraleLevel.fromScore(newMorale)
            dao.upsert(
                entity.copy(
                    morale = newMorale,
                    moraleLevel = newLevel.name,
                    lastUpdatedDate = dateStr,
                    lastUpdatedSeason = seasonId
                )
            )
        }
    }

    // ==================== 5. 不满处理 ====================

    /**
     * 清除球员不满（玩家发起谈话后调用）。
     *
     * @return true 表示成功清除
     */
    suspend fun clearUnrest(
        saveId: Int,
        playerId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val dao = databaseManager.playerMoraleDao()
        val entity = dao.getByPlayer(saveId, playerId) ?: return@withContext false
        dao.adjustUnrest(saveId, playerId, -entity.unrestAccumulator, false)
        true
    }
}

// ==================== 比赛结果辅助类型 ====================

/**
 * 比赛结果（[MoraleManager.onMatchPlayed] 入参）。
 */
data class MatchResult(
    val outcome: MatchOutcome,
    val teamGoals: Int,
    val opponentGoals: Int
)

/** 比赛胜负枚举。 */
enum class MatchOutcome {
    WIN,
    DRAW,
    LOSS
}
