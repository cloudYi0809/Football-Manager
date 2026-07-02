package com.greendynasty.football.media.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T24 媒体模块实体集合（save.db）。
 *
 * 严格依据 `/Users/yi/Desktop/足球经理/开发文档/T24_媒体_实现方案.md` §三 数据模型裁剪，
 * 按 T24 任务要求聚焦 6 类新闻 + 舆论值 + 赛前/赛后采访。
 *
 * 共 4 张表：
 * 1. [MediaNewsEntity] - 媒体新闻（独立于通用 save_news，承载完整字段：分类 / 重要性 / 时效 / 关联实体）
 * 2. [MediaOpinionEntity] - 俱乐部媒体舆论值（每存档每俱乐部一条）
 * 3. [MediaInterviewEntity] - 采访会话（赛前 / 赛后，含问题列表与进度）
 * 4. [MediaInterviewAnswerEntity] - 采访回答历史（每问题一条，含选项与影响快照）
 *
 * 三库归属：全部 save.db，每存档独立，与 T22 董事会 / T23 更衣室表保持一致风格。
 * saveId 类型与 [com.greendynasty.football.data.save.entity.SaveClubStateEntity] 一致使用 Int。
 *
 * 设计决策：不复用通用 save_news 表（字段过于简单），新建媒体专属表以承载完整字段，
 * 与 T22/T23 一致的"模块自治"原则，避免破坏已有 schema。
 */

// ==================== 1. 媒体新闻 ====================

/**
 * 媒体新闻表（save.db）。
 *
 * 由 [com.greendynasty.football.media.news.NewsGenerator] 生成并写入。
 * 支持按日期 / 分类 / 重要性查询，自动过期清理。
 *
 * @property newsId 自增主键
 * @property saveId 存档 ID（多存档隔离）
 * @property newsDate 新闻日期（游戏内日期，YYYY-MM-DD）
 * @property expireDate 过期日期（newsDate + ttlDays）
 * @property title 渲染后的标题
 * @property body 渲染后的正文
 * @property category 新闻分类（MATCH / TRANSFER / INJURY / HONOR / BUTTERFLY / GOSSIP）
 * @property importance 重要性 1-5 星
 * @property outletName 发布媒体名称
 * @property relatedPlayerId 关联球员 ID（可空）
 * @property relatedClubId 关联俱乐部 ID（可空）
 * @property relatedMatchId 关联比赛 ID（可空，仅比赛类）
 * @property isRead 是否已读：0 = 未读，1 = 已读
 * @property impactApplied 影响是否已结算：0 = 未结算，1 = 已结算
 * @property metaJson 扩展字段（JSON 字符串，如传闻真伪标记）
 */
@Entity(
    tableName = "media_news",
    indices = [
        Index(value = ["save_id", "news_date"]),
        Index(value = ["save_id", "category"]),
        Index(value = ["save_id", "is_read"]),
        Index(value = ["save_id", "related_club_id"])
    ]
)
data class MediaNewsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "news_id")
    val newsId: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "news_date")
    val newsDate: String,

    @ColumnInfo(name = "expire_date")
    val expireDate: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    /** 新闻分类：MATCH / TRANSFER / INJURY / HONOR / BUTTERFLY / GOSSIP。 */
    @ColumnInfo(name = "category")
    val category: String,

    /** 重要性 1-5 星。 */
    @ColumnInfo(name = "importance")
    val importance: Int,

    /** 发布媒体名称。 */
    @ColumnInfo(name = "outlet_name")
    val outletName: String,

    @ColumnInfo(name = "related_player_id")
    val relatedPlayerId: Int? = null,

    @ColumnInfo(name = "related_club_id")
    val relatedClubId: Int? = null,

    @ColumnInfo(name = "related_match_id")
    val relatedMatchId: Int? = null,

    @ColumnInfo(name = "is_read")
    val isRead: Int = 0,

    @ColumnInfo(name = "impact_applied")
    val impactApplied: Int = 0,

    @ColumnInfo(name = "meta_json")
    val metaJson: String = ""
)

// ==================== 2. 媒体舆论值 ====================

/**
 * 俱乐部媒体舆论值表（save.db）。
 *
 * 每存档每俱乐部一条记录，记录媒体舆论值（0-100）+ 历史峰值 + 最近互动日期。
 * 由 [com.greendynasty.football.media.opinion.PublicOpinionManager] 维护。
 *
 * 舆论值影响球迷支持度（[OpinionLevel] → fanSupportModifier）。
 *
 * @property opinionValue 当前舆论值 0-100
 * @property peakValue 历史峰值（用于回顾）
 * @property troughValue 历史谷值（用于回顾）
 * @property lastInteractionDate 最近互动日期（用于衰减计算）
 * @property totalNewsCount 累计新闻数
 * @property positiveNewsCount 累计正面新闻数
 * @property negativeNewsCount 累计负面新闻数
 */
@Entity(
    tableName = "media_opinion",
    indices = [
        Index(value = ["save_id", "club_id"], unique = true)
    ]
)
data class MediaOpinionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 当前舆论值 0-100。 */
    @ColumnInfo(name = "opinion_value")
    val opinionValue: Int,

    /** 历史峰值。 */
    @ColumnInfo(name = "peak_value")
    val peakValue: Int = 50,

    /** 历史谷值。 */
    @ColumnInfo(name = "trough_value")
    val troughValue: Int = 50,

    /** 最近互动日期（YYYY-MM-DD）。 */
    @ColumnInfo(name = "last_interaction_date")
    val lastInteractionDate: String? = null,

    @ColumnInfo(name = "total_news_count")
    val totalNewsCount: Int = 0,

    @ColumnInfo(name = "positive_news_count")
    val positiveNewsCount: Int = 0,

    @ColumnInfo(name = "negative_news_count")
    val negativeNewsCount: Int = 0
)

// ==================== 3. 采访会话 ====================

/**
 * 采访会话表（save.db）。
 *
 * 由 [com.greendynasty.football.media.interview.InterviewService] 调度并写入。
 *
 * 2 类场景（T24 任务要求：赛前 / 赛后）：
 * - PRE_MATCH 赛前采访：赛前 1-3 天触发
 * - POST_MATCH 赛后采访：当日比赛结束后触发
 *
 * @property scenario 采访场景：PRE_MATCH / POST_MATCH
 * @property scheduledDate 调度日期
 * @property status 状态：pending / in_progress / completed / skipped
 * @property questionIds 该场采访的问题 ID 列表（逗号分隔）
 * @property currentQuestionIndex 当前问题索引（0-based）
 * @property contextJson 上下文 JSON（对手 / 比分 / 连败次数等）
 * @property startedAt 开始时间（可空）
 * @property completedAt 完成时间（可空）
 */
@Entity(
    tableName = "media_interview",
    indices = [
        Index(value = ["save_id", "club_id", "status"]),
        Index(value = ["save_id", "club_id", "scheduled_date"])
    ]
)
data class MediaInterviewEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "interview_id")
    val interviewId: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 采访场景：PRE_MATCH / POST_MATCH。 */
    @ColumnInfo(name = "scenario")
    val scenario: String,

    @ColumnInfo(name = "scheduled_date")
    val scheduledDate: String,

    /** 状态：pending / in_progress / completed / skipped。 */
    @ColumnInfo(name = "status")
    val status: String = "pending",

    /** 问题 ID 列表（逗号分隔）。 */
    @ColumnInfo(name = "question_ids")
    val questionIds: String,

    /** 当前问题索引（0-based）。 */
    @ColumnInfo(name = "current_question_index")
    val currentQuestionIndex: Int = 0,

    /** 上下文 JSON（对手 / 比分 / 连败次数等）。 */
    @ColumnInfo(name = "context_json")
    val contextJson: String = "",

    @ColumnInfo(name = "started_at")
    val startedAt: String? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: String? = null
)

// ==================== 4. 采访回答历史 ====================

/**
 * 采访回答历史表（save.db）。
 *
 * 每个问题回答后写入一条记录，含选项 + 影响快照。
 * 用于历史回顾与采访影响审计。
 *
 * @property interviewId 所属采访 ID
 * @property questionId 问题 ID
 * @property selectedOptionId 玩家选择的选项 ID
 * @property answerDate 回答日期
 * @property impactJson 该回答产生的影响快照（JSON）
 */
@Entity(
    tableName = "media_interview_answer",
    indices = [
        Index(value = ["save_id", "interview_id"])
    ]
)
data class MediaInterviewAnswerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "answer_id")
    val answerId: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "interview_id")
    val interviewId: Long,

    @ColumnInfo(name = "question_id")
    val questionId: String,

    @ColumnInfo(name = "selected_option_id")
    val selectedOptionId: String,

    @ColumnInfo(name = "answer_date")
    val answerDate: String,

    @ColumnInfo(name = "impact_json")
    val impactJson: String
)
