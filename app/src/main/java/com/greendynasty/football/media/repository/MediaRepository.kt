package com.greendynasty.football.media.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.media.interview.InterviewService
import com.greendynasty.football.media.model.InterviewScenario
import com.greendynasty.football.media.model.MediaConfig
import com.greendynasty.football.media.model.MediaImpact
import com.greendynasty.football.media.model.MediaNewsEntity
import com.greendynasty.football.media.model.MediaOpinionEntity
import com.greendynasty.football.media.model.MediaSnapshot
import com.greendynasty.football.media.model.NewsCategory
import com.greendynasty.football.media.model.NewsEventInput
import com.greendynasty.football.media.news.NewsGenerator
import com.greendynasty.football.media.opinion.PublicOpinionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.logging.Logger

/**
 * T24 媒体仓库（V0.2 + T24 任务要求 + 实现方案 §四.1 MediaManager）。
 *
 * 数据访问层 + 业务协调入口，负责：
 * 1. 协调 [NewsGenerator] / [InterviewService] / [PublicOpinionManager] 三大组件
 * 2. 持久化所有媒体数据到 save.db
 * 3. 提供 Flow / suspend 查询接口供 ViewModel 使用
 * 4. 聚合媒体快照（[MediaSnapshot]）
 * 5. 衔接 T07 每日推进（[onDailyAdvance]）
 *
 * 三库分离：运行时数据落 save.db，球员/俱乐部基础信息从 history.db 只读。
 *
 * @param databaseManager 三库管理入口
 * @param config 媒体配置
 */
class MediaRepository(
    private val databaseManager: DatabaseManager,
    private val config: MediaConfig = MediaConfig.DEFAULT
) {
    private val logger = Logger.getLogger("MediaRepository")

    val newsGenerator = NewsGenerator(databaseManager, config)
    val interviewService = InterviewService(databaseManager, config = config)
    val opinionManager = PublicOpinionManager(databaseManager, config)

    // ==================== 1. 新闻查询 ====================

    /** 观察最近新闻列表（Flow 驱动 UI 自动刷新）。 */
    fun observeRecentNews(saveId: Int, limit: Int = 50): Flow<List<MediaNewsEntity>> =
        databaseManager.mediaNewsDao().observeRecent(saveId, limit)

    /** 观察未读新闻列表。 */
    fun observeUnreadNews(saveId: Int): Flow<List<MediaNewsEntity>> =
        databaseManager.mediaNewsDao().observeUnread(saveId)

    /** 观察未读新闻数。 */
    fun observeUnreadCount(saveId: Int): Flow<Int> =
        databaseManager.mediaNewsDao().observeUnreadCount(saveId)

    /** 查询最近新闻（一次性）。 */
    suspend fun getRecentNews(saveId: Int, limit: Int = 50): List<MediaNewsEntity> =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaNewsDao().getRecent(saveId, limit)
            } catch (e: Exception) {
                logger.warning("查询最近新闻失败：${e.message}")
                emptyList()
            }
        }

    /** 查询某日新闻。 */
    suspend fun getNewsByDate(saveId: Int, date: LocalDate): List<MediaNewsEntity> =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaNewsDao().getByDate(saveId, date.toString())
            } catch (e: Exception) {
                logger.warning("按日期查询新闻失败：${e.message}")
                emptyList()
            }
        }

    /** 查询日期范围内的新闻（归档查询，[com.greendynasty.football.media.model.MediaNewsEntity]）。 */
    suspend fun getNewsByDateRange(
        saveId: Int, from: LocalDate, to: LocalDate, limit: Int = 100
    ): List<MediaNewsEntity> = withContext(Dispatchers.IO) {
        try {
            databaseManager.mediaNewsDao().getByDateRange(saveId, from.toString(), to.toString(), limit)
        } catch (e: Exception) {
            logger.warning("按日期范围查询新闻失败：${e.message}")
            emptyList()
        }
    }

    /** 按分类查询新闻。 */
    suspend fun getNewsByCategory(
        saveId: Int, category: NewsCategory, limit: Int = 50
    ): List<MediaNewsEntity> = withContext(Dispatchers.IO) {
        try {
            databaseManager.mediaNewsDao().getByCategory(saveId, category.name, limit)
        } catch (e: Exception) {
            logger.warning("按分类查询新闻失败：${e.message}")
            emptyList()
        }
    }

    /** 查询某条新闻详情。 */
    suspend fun getNews(newsId: Long): MediaNewsEntity? = withContext(Dispatchers.IO) {
        try {
            databaseManager.mediaNewsDao().get(newsId)
        } catch (e: Exception) {
            logger.warning("查询新闻详情失败：${e.message}")
            null
        }
    }

    /** 查询某球员相关新闻。 */
    suspend fun getNewsByPlayer(saveId: Int, playerId: Int): List<MediaNewsEntity> =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaNewsDao().getByPlayer(saveId, playerId)
            } catch (e: Exception) {
                logger.warning("按球员查询新闻失败：${e.message}")
                emptyList()
            }
        }

    /** 标记新闻已读。 */
    suspend fun markNewsAsRead(newsId: Long) = withContext(Dispatchers.IO) {
        try {
            databaseManager.mediaNewsDao().markAsRead(newsId)
        } catch (e: Exception) {
            logger.warning("标记新闻已读失败：${e.message}")
        }
    }

    /** 标记全部新闻已读。 */
    suspend fun markAllNewsAsRead(saveId: Int) = withContext(Dispatchers.IO) {
        try {
            databaseManager.mediaNewsDao().markAllAsRead(saveId)
        } catch (e: Exception) {
            logger.warning("标记全部新闻已读失败：${e.message}")
        }
    }

    // ==================== 2. 采访查询 ====================

    /** 观察活跃采访列表（pending + in_progress）。 */
    fun observeActiveInterviews(saveId: Int, clubId: Int) =
        databaseManager.mediaInterviewDao().observeActive(saveId, clubId)

    /** 查询活跃采访（一次性）。 */
    suspend fun getActiveInterviews(saveId: Int, clubId: Int) =
        interviewService.getActiveInterviews(saveId, clubId)

    /** 查询下一条待处理采访。 */
    suspend fun getNextPendingInterview(saveId: Int, clubId: Int) =
        interviewService.getNextPendingInterview(saveId, clubId)

    // ==================== 3. 舆论值查询 ====================

    /** 查询俱乐部舆论值。 */
    suspend fun getOpinion(saveId: Int, clubId: Int): MediaOpinionEntity? =
        opinionManager.getOpinion(saveId, clubId)

    /** 观察俱乐部舆论值（Flow 驱动 UI 自动刷新）。 */
    fun observeOpinion(saveId: Int, clubId: Int) =
        opinionManager.observeOpinion(saveId, clubId)

    // ==================== 4. 每日推进 ====================

    /**
     * T07 每日推进入口（实现方案 §四.1 onDailyAdvance 简化版）。
     *
     * 由 T07 每日推进在"新闻生成"阶段调用：
     * 1. 清理过期新闻
     * 2. 初始化舆论值（若不存在）
     * 3. 批量生成新闻（基于事件）
     * 4. 每日舆论自然衰减
     *
     * @param saveId 存档 ID
     * @param currentDate 当前日期
     * @param managerClubId 玩家俱乐部 ID
     * @param events 当日游戏事件列表
     */
    suspend fun onDailyAdvance(
        saveId: Int,
        currentDate: LocalDate,
        managerClubId: Int,
        events: List<NewsEventInput> = emptyList()
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. 清理过期新闻
            newsGenerator.cleanupExpired(saveId, currentDate)

            // 2. 初始化舆论值（若不存在）
            opinionManager.initOpinion(saveId, managerClubId, currentDate = currentDate)

            // 3. 批量生成新闻
            val generatedNews = newsGenerator.generateBatch(saveId, currentDate, events, managerClubId)

            // 4. 更新舆论统计（正面/负面新闻计数）
            for (news in generatedNews) {
                if (news.relatedClubId == managerClubId) {
                    val isPositive = isPositiveNews(news)
                    opinionManager.recordNews(saveId, managerClubId, isPositive, currentDate)
                }
            }

            // 5. 每日舆论自然衰减
            opinionManager.applyDailyDecay(saveId, managerClubId, currentDate)
        } catch (e: Exception) {
            logger.warning("每日媒体推进失败：${e.message}")
        }
    }

    /**
     * 调度采访（实现方案 §四.3 scheduleForToday 简化版）。
     *
     * 由调用方判断触发条件后调用：
     * - 赛前 1-3 天 → PRE_MATCH
     * - 当日比赛结束 → POST_MATCH
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param scenario 采访场景
     * @param currentDate 当前日期
     * @param context 采访上下文（对手 / 比分等）
     */
    suspend fun scheduleInterview(
        saveId: Int,
        clubId: Int,
        scenario: InterviewScenario,
        currentDate: LocalDate,
        context: Map<String, String> = emptyMap()
    ) = interviewService.scheduleInterview(saveId, clubId, scenario, currentDate, context)

    /**
     * 应用采访回答影响（统一入口：影响舆论 + 士气 + 球迷 + 董事会）。
     *
     * 由 ViewModel 在 [InterviewService.answerQuestion] 后调用，将影响应用到舆论值。
     * 士气 / 球迷 / 董事会 维度的影响由 T23 更衣室 / T22 董事会模块自行监听并应用，
     * 本方法只负责舆论值维度（V1 简化：避免跨模块强耦合）。
     *
     * @return 更新后的舆论值，失败返回 null
     */
    suspend fun applyInterviewImpact(
        saveId: Int,
        clubId: Int,
        impact: MediaImpact,
        currentDate: LocalDate
    ): MediaOpinionEntity? = opinionManager.applyImpact(saveId, clubId, impact, currentDate)

    // ==================== 5. 快照聚合 ====================

    /**
     * 聚合媒体快照（UI 一次性消费）。
     *
     * 包含：
     * - 当前舆论值
     * - 最近 50 条新闻
     * - 未读新闻数
     * - 待处理采访列表
     * - 当前进行中的采访
     */
    suspend fun getSnapshot(
        saveId: Int, clubId: Int
    ): MediaSnapshot = withContext(Dispatchers.IO) {
        val opinion = opinionManager.getOpinion(saveId, clubId)
        val recentNews = try {
            databaseManager.mediaNewsDao().getRecent(saveId, 50)
        } catch (e: Exception) { emptyList() }
        val unreadCount = try {
            databaseManager.mediaNewsDao().countUnread(saveId)
        } catch (e: Exception) { 0 }
        val pendingInterviews = try {
            databaseManager.mediaInterviewDao().getActive(saveId, clubId)
        } catch (e: Exception) { emptyList() }
        val currentInterview = pendingInterviews.firstOrNull { it.status == "in_progress" }

        MediaSnapshot(
            saveId = saveId,
            clubId = clubId,
            opinion = opinion,
            recentNews = recentNews,
            unreadCount = unreadCount,
            pendingInterviews = pendingInterviews,
            currentInterview = currentInterview
        )
    }

    // ==================== 内部工具 ====================

    /**
     * 判断新闻是否为正面（用于舆论统计）。
     *
     * 正面新闻类别：HONOR（荣誉）+ MATCH 胜利标题 + TRANSFER 加入 + INJURY 复出
     * 负面新闻类别：GOSSIP 八卦 + MATCH 失败标题 + INJURY 受伤 + TRANSFER 离队
     */
    private fun isPositiveNews(news: MediaNewsEntity): Boolean {
        val title = news.title
        return when (NewsCategory.fromValue(news.category)) {
            NewsCategory.HONOR -> true
            NewsCategory.GOSSIP -> false
            NewsCategory.MATCH -> title.contains("击败") || title.contains("大胜") ||
                title.contains("完胜") || title.contains("夺冠") || title.contains("胜利")
            NewsCategory.TRANSFER -> title.contains("加盟") || title.contains("落定")
            NewsCategory.INJURY -> title.contains("复出") || title.contains("重返") || title.contains("利好")
            NewsCategory.BUTTERFLY -> true
        }
    }
}
