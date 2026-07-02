package com.greendynasty.football.media.opinion

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.media.model.MediaConfig
import com.greendynasty.football.media.model.MediaImpact
import com.greendynasty.football.media.model.MediaOpinionEntity
import com.greendynasty.football.media.model.OpinionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.logging.Logger

/**
 * T24 舆论值管理器（V0.2 + T24 任务要求 §核心要点 4 + 实现方案 §四.4）。
 *
 * 严格依据 V0.2 算法文档 + T24 实现方案 §五.3 媒体关系更新：
 * 1. 初始化俱乐部媒体舆论值（基于俱乐部声望，默认 50）
 * 2. 受新闻（正面 +N / 负面 -N）影响
 * 3. 受采访回答影响（[MediaImpact.opinionDelta]）
 * 4. 受战绩影响（连胜 +1/场 / 连败 -1/场）
 * 5. 久未互动自然衰减（≥7 天 -1/天，下限 20）
 * 6. 丑闻曝光一次性 -7
 *
 * 舆论值等级（[OpinionLevel]）影响球迷支持度：
 * - EXCELLENT 极佳（≥80）：球迷支持度 +5%/月
 * - GOOD 良好（60-79）：球迷支持度 +2%/月
 * - NEUTRAL 中立（40-59）：无影响
 * - POOR 较差（20-39）：球迷支持度 -2%/月
 * - HOSTILE 敌对（<20）：球迷支持度 -5%/月
 *
 * @param databaseManager 三库管理入口
 * @param config 媒体配置
 */
class PublicOpinionManager(
    private val databaseManager: DatabaseManager,
    private val config: MediaConfig = MediaConfig.DEFAULT
) {
    private val logger = Logger.getLogger("PublicOpinionManager")
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ==================== 1. 初始化 ====================

    /**
     * 初始化俱乐部媒体舆论值（新存档创建时调用）。
     *
     * 关注度由俱乐部声望决定（V0.2 + T24 实现方案 §四.4 initRelations）。
     * V1 简化：默认初始值 50，可由 clubReputation 调整（声望 80+ 初始 60，声望 <50 初始 40）。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param clubReputation 俱乐部声望 0-100（可选，默认 50）
     * @param currentDate 当前日期
     * @return 创建的舆论值实体
     */
    suspend fun initOpinion(
        saveId: Int,
        clubId: Int,
        clubReputation: Int = 50,
        currentDate: LocalDate
    ): MediaOpinionEntity? = withContext(Dispatchers.IO) {
        try {
            // 防重：已存在则不覆盖
            val existing = databaseManager.mediaOpinionDao().get(saveId, clubId)
            if (existing != null) return@withContext existing

            // 基于俱乐部声望调整初始值
            val initialValue = when {
                clubReputation >= 80 -> (config.opinion.initialValue + 10).coerceIn(
                    config.opinion.minValue, config.opinion.maxValue
                )
                clubReputation < 50 -> (config.opinion.initialValue - 10).coerceIn(
                    config.opinion.minValue, config.opinion.maxValue
                )
                else -> config.opinion.initialValue
            }

            val opinion = MediaOpinionEntity(
                saveId = saveId,
                clubId = clubId,
                opinionValue = initialValue,
                peakValue = initialValue,
                troughValue = initialValue,
                lastInteractionDate = currentDate.format(dateFormatter),
                totalNewsCount = 0,
                positiveNewsCount = 0,
                negativeNewsCount = 0
            )
            val id = databaseManager.mediaOpinionDao().upsert(opinion)
            opinion.copy(id = id)
        } catch (e: Exception) {
            logger.warning("初始化舆论值失败：${e.message}")
            null
        }
    }

    // ==================== 2. 舆论值调整 ====================

    /**
     * 应用媒体影响到舆论值（实现方案 §五.3 updateMediaRelation）。
     *
     * 由 [com.greendynasty.football.media.repository.MediaRepository] 在新闻生成 / 采访回答后调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param impact 媒体影响（取 opinionDelta + reputationDelta 作为舆论调整）
     * @param currentDate 当前日期
     * @return 更新后的舆论值，失败返回 null
     */
    suspend fun applyImpact(
        saveId: Int,
        clubId: Int,
        impact: MediaImpact,
        currentDate: LocalDate
    ): MediaOpinionEntity? = withContext(Dispatchers.IO) {
        try {
            val current = databaseManager.mediaOpinionDao().get(saveId, clubId)
                ?: initOpinion(saveId, clubId, currentDate = currentDate) ?: return@withContext null

            // 舆论值变化 = opinionDelta + reputationDelta * 0.5（声望对舆论有间接影响）
            val delta = impact.opinionDelta + (impact.reputationDelta * 0.5).toInt()
            val newValue = (current.opinionValue + delta).coerceIn(
                config.opinion.minValue, config.opinion.maxValue
            )

            databaseManager.mediaOpinionDao().updateValue(
                saveId, clubId, newValue, currentDate.format(dateFormatter)
            )
            current.copy(
                opinionValue = newValue,
                peakValue = maxOf(current.peakValue, newValue),
                troughValue = minOf(current.troughValue, newValue),
                lastInteractionDate = currentDate.format(dateFormatter)
            )
        } catch (e: Exception) {
            logger.warning("应用媒体影响失败：${e.message}")
            null
        }
    }

    /**
     * 记录新闻统计（正面 / 负面新闻计数）。
     *
     * 由 [com.greendynasty.football.media.repository.MediaRepository] 在新闻生成后调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param isPositive 是否正面新闻
     * @param currentDate 当前日期
     */
    suspend fun recordNews(
        saveId: Int,
        clubId: Int,
        isPositive: Boolean,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        try {
            val current = databaseManager.mediaOpinionDao().get(saveId, clubId)
                ?: initOpinion(saveId, clubId, currentDate = currentDate) ?: return@withContext

            val positive = if (isPositive) 1 else 0
            val negative = if (!isPositive) 1 else 0
            databaseManager.mediaOpinionDao().incrementNewsCount(
                saveId, clubId, total = 1, positive = positive, negative = negative
            )

            // 正面新闻 +1 舆论，负面新闻 -1 舆论（轻度影响，避免媒体盖过比赛）
            val delta = if (isPositive) 1 else -1
            val newValue = (current.opinionValue + delta).coerceIn(
                config.opinion.minValue, config.opinion.maxValue
            )
            databaseManager.mediaOpinionDao().updateValue(
                saveId, clubId, newValue, currentDate.format(dateFormatter)
            )
        } catch (e: Exception) {
            logger.warning("记录新闻统计失败：${e.message}")
        }
    }

    /**
     * 战绩影响舆论值（实现方案 §四.4 关系更新伪代码）。
     *
     * - 连胜每场 +1
     * - 连败每场 -1
     * - 夺冠一次性 +10（额外奖励）
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param isWin 是否获胜
     * @param isLose 是否失败
     * @param isTitle 是否夺冠
     * @param currentDate 当前日期
     */
    suspend fun applyMatchResult(
        saveId: Int,
        clubId: Int,
        isWin: Boolean,
        isLose: Boolean,
        isTitle: Boolean = false,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        try {
            val current = databaseManager.mediaOpinionDao().get(saveId, clubId)
                ?: initOpinion(saveId, clubId, currentDate = currentDate) ?: return@withContext

            var delta = 0
            if (isWin) delta += config.opinion.winStreakDeltaPerMatch
            if (isLose) delta += config.opinion.loseStreakDeltaPerMatch
            if (isTitle) delta += 10 // 夺冠额外 +10
            if (delta == 0) return@withContext

            val newValue = (current.opinionValue + delta).coerceIn(
                config.opinion.minValue, config.opinion.maxValue
            )
            databaseManager.mediaOpinionDao().updateValue(
                saveId, clubId, newValue, currentDate.format(dateFormatter)
            )
        } catch (e: Exception) {
            logger.warning("应用战绩影响失败：${e.message}")
        }
    }

    /**
     * 丑闻曝光一次性扣分（实现方案 §四.4 关系更新伪代码）。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentDate 当前日期
     */
    suspend fun applyScandal(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        try {
            val current = databaseManager.mediaOpinionDao().get(saveId, clubId)
                ?: initOpinion(saveId, clubId, currentDate = currentDate) ?: return@withContext

            val newValue = (current.opinionValue + config.opinion.scandalDelta).coerceIn(
                config.opinion.minValue, config.opinion.maxValue
            )
            databaseManager.mediaOpinionDao().updateValue(
                saveId, clubId, newValue, currentDate.format(dateFormatter)
            )
        } catch (e: Exception) {
            logger.warning("应用丑闻影响失败：${e.message}")
        }
    }

    // ==================== 3. 每日衰减 ====================

    /**
     * 每日舆论值自然衰减（实现方案 §四.4 applyDailyDecay）。
     *
     * 仅对久未互动的俱乐部生效（lastInteractionDate 距今 ≥ decayThresholdDays 天）。
     * 衰减量：-dailyDecayAmount/天，下限 minDecayValue（避免无限下降到 0）。
     *
     * 由 T07 每日推进调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentDate 当前日期
     */
    suspend fun applyDailyDecay(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        try {
            val current = databaseManager.mediaOpinionDao().get(saveId, clubId) ?: return@withContext

            val lastInteraction = current.lastInteractionDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val daysSince = lastInteraction?.let { ChronoUnit.DAYS.between(it, currentDate).toInt() } ?: 30

            if (daysSince >= config.opinion.decayThresholdDays) {
                val newValue = (current.opinionValue - config.opinion.dailyDecayAmount).coerceIn(
                    config.opinion.minDecayValue, config.opinion.maxValue
                )
                if (newValue != current.opinionValue) {
                    databaseManager.mediaOpinionDao().updateValue(
                        saveId, clubId, newValue, currentDate.format(dateFormatter)
                    )
                }
            }
        } catch (e: Exception) {
            logger.warning("每日舆论衰减失败：${e.message}")
        }
    }

    // ==================== 4. 查询接口 ====================

    /** 查询俱乐部当前舆论值。 */
    suspend fun getOpinion(saveId: Int, clubId: Int): MediaOpinionEntity? =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaOpinionDao().get(saveId, clubId)
            } catch (e: Exception) {
                logger.warning("查询舆论值失败：${e.message}")
                null
            }
        }

    /** 观察俱乐部舆论值（Flow 驱动 UI 自动刷新）。 */
    fun observeOpinion(saveId: Int, clubId: Int) =
        databaseManager.mediaOpinionDao().observe(saveId, clubId)

    /** 查询舆论值等级。 */
    fun getOpinionLevel(opinion: MediaOpinionEntity?): OpinionLevel {
        val value = opinion?.opinionValue ?: config.opinion.initialValue
        return OpinionLevel.fromScore(value)
    }

    /**
     * 计算球迷支持度月度修正（实现方案 §四.4 OpinionLevel）。
     *
     * 由 T22 董事会 / 球迷系统每月推进时调用。
     *
     * @return 球迷支持度修正值（正数加分 / 负数减分）
     */
    fun getFanSupportModifier(opinion: MediaOpinionEntity?): Int {
        return getOpinionLevel(opinion).fanSupportModifier
    }
}
