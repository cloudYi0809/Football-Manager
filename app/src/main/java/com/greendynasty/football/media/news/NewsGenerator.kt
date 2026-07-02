package com.greendynasty.football.media.news

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.media.model.MediaConfig
import com.greendynasty.football.media.model.MediaNewsEntity
import com.greendynasty.football.media.model.NewsCategory
import com.greendynasty.football.media.model.NewsEventInput
import com.greendynasty.football.media.model.NewsImportance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger
import kotlin.random.Random

/**
 * T24 新闻生成器（V0.2 + T24 任务要求 §核心要点 1 + 实现方案 §四.2）。
 *
 * 严格依据 V0.2 算法文档 + T24 实现方案 §五.1 新闻生成主流程：
 * 1. 收集当日游戏事件（[NewsEventInput]）
 * 2. 按事件类型匹配模板（[NewsTemplate]）
 * 3. 渲染标题 + 正文（占位符 `{player_name}` / `{club_name}` / `{opponent_name}` / `{score}` 等替换）
 * 4. 重要性分级（1-5 星，玩家俱乐部事件优先 5 星）
 * 5. 时效性标记（1-3 天 TTL）
 * 6. 批量写入 media_news（≤30 条/日）
 *
 * 6 类新闻（T24 任务要求）：
 * - MATCH 比赛类：赛前前瞻 / 赛后报道
 * - TRANSFER 转会类：转会传闻 / 转会完成
 * - INJURY 伤病类：球员伤病新闻
 * - HONOR 荣誉类：夺冠 / 个人奖项 / 里程碑
 * - BUTTERFLY 蝴蝶类：联动 T20 蝴蝶效应历史回顾
 * - GOSSIP 八卦类：球员场外事件 / 经理传闻
 *
 * 性能预算：≤200ms / 日（实现方案 §十）。
 *
 * 设计原则：
 * - 模板化生成（避免硬编码文案）
 * - 单事件最多 2 条新闻（避免刷屏）
 * - 玩家俱乐部事件 100% 上新闻，同联赛 60%，跨联赛 30%，其他 10%
 * - 单条新闻影响 ≤5（钳制，避免媒体盖过比赛）
 *
 * @param databaseManager 三库管理入口
 * @param config 媒体配置
 */
class NewsGenerator(
    private val databaseManager: DatabaseManager,
    private val config: MediaConfig = MediaConfig.DEFAULT
) {
    private val logger = Logger.getLogger("NewsGenerator")

    /** ISO 日期格式化器（与 T22/T23 一致）。 */
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 内置媒体机构名称池（V1 简化：硬编码 4 类媒体 × 3 档覆盖）。 */
    private val outletNames = listOf(
        "全国体育电视台", "足球报", "球网", "体育电台",
        "地方晚报体育版", "都市体育频道", "足球周刊", "赛事评论"
    )

    /**
     * 批量生成当日新闻（实现方案 §五.1）。
     *
     * 流程：
     * 1. 按事件类型分组
     * 2. 每类事件匹配模板生成新闻（按概率过滤）
     * 3. 补充背景新闻（不足 minDailyNews 时）
     * 4. 重要性截断（≤maxDailyNews）
     * 5. 批量入库
     *
     * @param saveId 存档 ID
     * @param date 当日日期
     * @param events 当日游戏事件列表
     * @param managerClubId 玩家俱乐部 ID
     * @return 生成的新闻列表
     */
    suspend fun generateBatch(
        saveId: Int,
        date: LocalDate,
        events: List<NewsEventInput>,
        managerClubId: Int
    ): List<MediaNewsEntity> = withContext(Dispatchers.IO) {
        val allNews = mutableListOf<MediaNewsEntity>()

        // 1. 按事件类型分组
        val byType = events.groupBy { it.eventType }

        // 2. 每类事件匹配模板生成新闻
        for ((eventType, eventList) in byType) {
            val templates = NewsTemplateRegistry.getTemplatesForEvent(eventType)
            if (templates.isEmpty()) continue

            for (event in eventList) {
                // 决定是否生成（避免每条事件都生成新闻）
                if (!shouldGenerateNews(event)) continue

                val template = templates.randomOrNull() ?: continue
                val news = generateSingle(saveId, date, event, template)
                if (news != null) allNews.add(news)

                // 控制单事件新闻条数（≤2 条/事件，避免刷屏）
                val matchId = event.matchId
                if (matchId != null) {
                    val count = allNews.count { it.relatedMatchId == matchId }
                    if (count >= config.newsGeneration.maxNewsPerEvent) break
                }
            }
        }

        // 3. 补充背景新闻（即使无事件也生成 1-2 条背景新闻）
        if (allNews.size < config.newsGeneration.minDailyNews) {
            allNews.addAll(generateBackgroundNews(saveId, date, managerClubId, events))
        }

        // 4. 限制当日新闻总量（≤30 条，超过按重要性截断）
        val capped = allNews.sortedByDescending { it.importance }
            .take(config.newsGeneration.maxDailyNews)

        // 5. 批量入库
        try {
            databaseManager.mediaNewsDao().insertAll(capped)
        } catch (e: Exception) {
            logger.warning("批量写入新闻失败：${e.message}")
        }

        capped
    }

    /**
     * 判断事件是否应生成新闻（实现方案 §四.2 shouldGenerateNews）。
     *
     * - 玩家俱乐部事件 100% 上新闻
     * - 同联赛事件 60% 概率
     * - 跨联赛重大事件（高曝光列表）30% 概率
     * - 其他 10% 概率
     */
    private fun shouldGenerateNews(event: NewsEventInput): Boolean {
        if (event.isPlayerClub) return true
        if (event.sameLeague) return Random.nextDouble() < config.newsGeneration.generateProbSameLeague
        if (event.eventType in config.newsGeneration.highProfileEvents) {
            return Random.nextDouble() < config.newsGeneration.generateProbCrossLeague
        }
        return Random.nextDouble() < config.newsGeneration.generateProbOther
    }

    /**
     * 单条新闻生成（实现方案 §四.2 generateSingle）。
     *
     * 1. 选择发布媒体
     * 2. 准备占位符上下文
     * 3. 模板渲染（标题 + 正文）
     * 4. 重要性分级
     * 5. 时效性 TTL
     */
    private fun generateSingle(
        saveId: Int,
        date: LocalDate,
        event: NewsEventInput,
        template: NewsTemplate
    ): MediaNewsEntity? {
        // 1. 选择发布媒体
        val outletName = selectOutlet(event)

        // 2. 准备占位符上下文（合并事件自带 + 默认）
        val context = buildPlaceholderContext(event, outletName, date)

        // 3. 模板渲染
        val title = renderTemplate(template.titleTemplate, context)
        val body = renderTemplate(template.bodyTemplate, context)

        // 4. 重要性分级（玩家俱乐部事件优先 5 星）
        val importance = rateImportance(event, template.importanceBase)

        // 5. 时效性 TTL
        val ttl = getTtl(importance)
        val expireDate = date.plusDays(ttl.toLong())

        return MediaNewsEntity(
            saveId = saveId,
            newsDate = date.format(dateFormatter),
            expireDate = expireDate.format(dateFormatter),
            title = title,
            body = body,
            category = template.category.name,
            importance = importance.stars,
            outletName = outletName,
            relatedPlayerId = event.playerId,
            relatedClubId = event.clubId,
            relatedMatchId = event.matchId,
            metaJson = ""
        )
    }

    /**
     * 选择发布媒体（基于事件类型 + 随机选取）。
     * V1 简化：从内置媒体名池随机选取。
     */
    private fun selectOutlet(event: NewsEventInput): String {
        return outletNames.random()
    }

    /**
     * 构建占位符上下文（实现方案 §五.1）。
     *
     * 默认占位符：
     * - {player_name} → 球员名（默认"某球员"）
     * - {club_name} → 俱乐部名（默认"某俱乐部"）
     * - {opponent_name} → 对手名（默认"对手"）
     * - {score} → 比分（默认"2-1"）
     * - {date} → 日期
     * - {outlet_name} → 媒体名
     */
    private fun buildPlaceholderContext(
        event: NewsEventInput,
        outletName: String,
        date: LocalDate
    ): Map<String, String> {
        val defaults = mapOf(
            "player_name" to "某球员",
            "club_name" to "某俱乐部",
            "opponent_name" to "对手",
            "score" to "2-1",
            "date" to date.format(dateFormatter),
            "outlet_name" to outletName
        )
        return defaults + event.placeholders
    }

    /**
     * 模板渲染（占位符替换）。
     * 支持 `{key}` 格式占位符，未匹配的占位符保持原样。
     */
    private fun renderTemplate(template: String, context: Map<String, String>): String {
        var result = template
        for ((key, value) in context) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    /**
     * 重要性分级（实现方案 §四.2 ImportanceRater）。
     *
     * - 玩家俱乐部事件：基础 +2（最高 5 星）
     * - 同联赛事件：基础 +1
     * - 高曝光事件：基础 +1
     * - 其他：保持基础
     */
    private fun rateImportance(event: NewsEventInput, baseImportance: Int): NewsImportance {
        var score = baseImportance
        if (event.isPlayerClub) score += 2
        if (event.sameLeague) score += 1
        if (event.eventType in config.newsGeneration.highProfileEvents) score += 1
        return NewsImportance.fromScore(score.coerceIn(1, 5))
    }

    /**
     * 时效性 TTL（实现方案 §四.2 ExpirationPolicy）。
     * 不同重要性对应不同 TTL（1-3 天）。
     */
    private fun getTtl(importance: NewsImportance): Int = importance.ttlDays

    /**
     * 生成背景新闻（实现方案 §四.2 generateBackgroundNews）。
     *
     * 当无事件或新闻不足时，生成 1-2 条背景常规新闻（1 星），
     * 内容为联赛动态 / 球员观察 / 历史回顾等通用模板。
     */
    private fun generateBackgroundNews(
        saveId: Int,
        date: LocalDate,
        managerClubId: Int,
        events: List<NewsEventInput>
    ): List<MediaNewsEntity> {
        val templates = NewsTemplateRegistry.getBackgroundTemplates()
        val count = config.newsGeneration.minDailyNews - events.size.coerceAtLeast(0)
        val newsList = mutableListOf<MediaNewsEntity>()
        repeat(count.coerceAtLeast(1)) {
            val template = templates.randomOrNull() ?: return@repeat
            val outletName = outletNames.random()
            val context = mapOf(
                "date" to date.format(dateFormatter),
                "outlet_name" to outletName,
                "club_name" to "某俱乐部",
                "player_name" to "某球员"
            )
            val title = renderTemplate(template.titleTemplate, context)
            val body = renderTemplate(template.bodyTemplate, context)
            val ttl = getTtl(NewsImportance.BACKGROUND)
            newsList.add(
                MediaNewsEntity(
                    saveId = saveId,
                    newsDate = date.format(dateFormatter),
                    expireDate = date.plusDays(ttl.toLong()).format(dateFormatter),
                    title = title,
                    body = body,
                    category = template.category.name,
                    importance = NewsImportance.BACKGROUND.stars,
                    outletName = outletName,
                    relatedClubId = null,
                    metaJson = ""
                )
            )
        }
        return newsList
    }

    /**
     * 清理过期新闻（实现方案 §四.2 cleanupExpiredNews）。
     * 由 T07 每日推进在新闻生成前调用。
     */
    suspend fun cleanupExpired(saveId: Int, currentDate: LocalDate): Int =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaNewsDao()
                    .deleteExpired(saveId, currentDate.format(dateFormatter))
            } catch (e: Exception) {
                logger.warning("清理过期新闻失败：${e.message}")
                0
            }
        }

    /**
     * 生成单条新闻并入库（供外部模块如 InterviewService 调用）。
     * 例如：采访完成后生成"采访总结"新闻。
     */
    suspend fun generateAndInsert(
        saveId: Int,
        date: LocalDate,
        category: NewsCategory,
        title: String,
        body: String,
        importance: NewsImportance,
        relatedPlayerId: Int? = null,
        relatedClubId: Int? = null,
        relatedMatchId: Int? = null,
        outletName: String? = null
    ): MediaNewsEntity? = withContext(Dispatchers.IO) {
        val outlet = outletName ?: outletNames.random()
        val ttl = getTtl(importance)
        val news = MediaNewsEntity(
            saveId = saveId,
            newsDate = date.format(dateFormatter),
            expireDate = date.plusDays(ttl.toLong()).format(dateFormatter),
            title = title,
            body = body,
            category = category.name,
            importance = importance.stars,
            outletName = outlet,
            relatedPlayerId = relatedPlayerId,
            relatedClubId = relatedClubId,
            relatedMatchId = relatedMatchId,
            metaJson = ""
        )
        try {
            val id = databaseManager.mediaNewsDao().insert(news)
            news.copy(newsId = id)
        } catch (e: Exception) {
            logger.warning("单条新闻入库失败：${e.message}")
            null
        }
    }
}
