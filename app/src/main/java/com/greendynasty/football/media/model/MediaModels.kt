package com.greendynasty.football.media.model

/**
 * T24 媒体模块领域模型 + 枚举集合（V0.2 + T24 任务要求 + 实现方案 §四）。
 *
 * 与 Entity 区别：Entity 是 Room 持久化层，这里是业务计算的不可变 data class / enum。
 * 严格依据 V0.2 算法文档 + T24 任务要求（6 类新闻 + 舆论值 + 赛前/赛后采访），
 * 所有参数配置化（[MediaConfig]）。
 */

// ==================== 1. 新闻分类枚举 ====================

/**
 * 6 类新闻分类（T24 任务要求 §核心要点 2）。
 *
 * - MATCH 比赛类：赛前前瞻 / 赛后报道
 * - TRANSFER 转会类：转会传闻 / 转会完成
 * - INJURY 伤病类：球员伤病新闻
 * - HONOR 荣誉类：夺冠 / 个人奖项 / 里程碑
 * - BUTTERFLY 蝴蝶类：联动 T20 蝴蝶效应历史回顾
 * - GOSSIP 八卦类：球员场外事件 / 经理传闻
 */
enum class NewsCategory(val label: String, val defaultImportance: Int) {
    MATCH("比赛", 3),
    TRANSFER("转会", 4),
    INJURY("伤病", 2),
    HONOR("荣誉", 5),
    BUTTERFLY("蝴蝶", 3),
    GOSSIP("八卦", 2);

    companion object {
        /** 从字符串安全反序列化。 */
        fun fromValue(value: String?): NewsCategory =
            values().firstOrNull { it.name == value } ?: MATCH
    }
}

// ==================== 2. 新闻重要性 ====================

/**
 * 新闻重要性分级 1-5 星（V0.2 + T24 实现方案 §四.2）。
 *
 * | 等级 | 触发条件示例 |
 * |------|-------------|
 * | 5 星 | 玩家俱乐部关键比赛 / 顶级转会 / 重大荣誉 |
 * | 4 星 | 玩家俱乐部普通比赛 / 同联赛重要动态 |
 * | 3 星 | 玩家联赛其他俱乐部动态 |
 * | 2 星 | 其他联赛顶级动态 |
 * | 1 星 | 背景常规新闻 |
 */
enum class NewsImportance(val stars: Int, val label: String) {
    BACKGROUND(1, "背景"),
    LOW(2, "低"),
    NORMAL(3, "中"),
    HIGH(4, "高"),
    CRITICAL(5, "关键");

    /** TTL 天数（V0.2 §四 时效性 1-3 天）。 */
    val ttlDays: Int
        get() = when (this) {
            CRITICAL, HIGH -> 3
            NORMAL -> 2
            LOW, BACKGROUND -> 1
        }

    companion object {
        fun fromScore(score: Int): NewsImportance =
            values().firstOrNull { it.stars == score } ?: NORMAL
    }
}

// ==================== 3. 采访场景 ====================

/**
 * 采访场景（T24 任务要求 §核心要点 3：赛前 / 赛后采访）。
 *
 * - PRE_MATCH 赛前采访：赛前 1-3 天触发，4 个问题
 * - POST_MATCH 赛后采访：当日比赛结束后触发，4 个问题
 */
enum class InterviewScenario(val label: String, val questionsCount: Int) {
    PRE_MATCH("赛前采访", 4),
    POST_MATCH("赛后采访", 4);

    companion object {
        fun fromValue(value: String?): InterviewScenario =
            values().firstOrNull { it.name == value } ?: POST_MATCH
    }
}

// ==================== 4. 回答风格 ====================

/**
 * 5 种回答风格（V0.2 + T24 实现方案 §五.2 采访影响计算）。
 *
 * - NEUTRAL 中立：无影响
 * - CONFIDENT 自信：士气 +2 / 球迷 +3 / 董事会 +1 / 媒体 +1 / 声望 +1
 * - HUMBLE 谦虚：士气 +1 / 球迷 +1 / 董事会 +2 / 媒体 +2 / 声望 0
 * - AGGRESSIVE 激进：士气 -1 / 球迷 -2 / 董事会 -3 / 媒体 -5 / 声望 -1
 * - DEFLECT 转移：士气 0 / 球迷 0 / 董事会 0 / 媒体 -1 / 声望 0
 */
enum class AnswerStyle(val label: String) {
    NEUTRAL("中立"),
    CONFIDENT("自信"),
    HUMBLE("谦虚"),
    AGGRESSIVE("激进"),
    DEFLECT("转移");

    companion object {
        fun fromValue(value: String?): AnswerStyle =
            values().firstOrNull { it.name == value } ?: NEUTRAL
    }
}

// ==================== 5. 采访问题分类 ====================

/**
 * 采访问题分类（用于按场景抽取问题）。
 */
enum class InterviewQuestionCategory(val label: String) {
    MATCH_RESULT("比赛结果"),
    OPPONENT("对手评价"),
    PLAYER_PERFORMANCE("球员表现"),
    TACTICS("战术安排"),
    TRANSFER_PLAN("转会计划"),
    SEASON_GOAL("赛季目标"),
    CRISIS_RESPONSE("危机应对"),
    CONTROVERSY("争议话题");

    companion object {
        fun fromValue(value: String?): InterviewQuestionCategory =
            values().firstOrNull { it.name == value } ?: MATCH_RESULT
    }
}

// ==================== 6. 舆论值等级 ====================

/**
 * 媒体舆论值等级（V0.2 + T24 任务要求 §核心要点 4）。
 *
 * 舆论值 0-100，影响球迷支持度：
 * - EXCELLENT 极佳（≥80）：球迷支持度 +5%/月
 * - GOOD 良好（60-79）：球迷支持度 +2%/月
 * - NEUTRAL 中立（40-59）：无影响
 * - POOR 较差（20-39）：球迷支持度 -2%/月
 * - HOSTILE 敌对（<20）：球迷支持度 -5%/月
 */
enum class OpinionLevel(val range: IntRange, val label: String, val fanSupportModifier: Int) {
    EXCELLENT(80..100, "极佳", 5),
    GOOD(60..79, "良好", 2),
    NEUTRAL(40..59, "中立", 0),
    POOR(20..39, "较差", -2),
    HOSTILE(0..19, "敌对", -5);

    companion object {
        fun fromScore(score: Int): OpinionLevel =
            values().first { score in it.range }
    }
}

// ==================== 7. 媒体影响结果 ====================

/**
 * 媒体影响结果（V0.2 + T24 实现方案 §四.5 6 维度影响）。
 *
 * 单条新闻 / 单次采访回答产生的影响，所有维度钳制在 ±5 内（避免媒体盖过比赛）。
 *
 * @property moraleDelta 球员士气变化
 * @property fanSatisfactionDelta 球迷满意度变化
 * @property boardSatisfactionDelta 董事会满意度变化
 * @property opinionDelta 媒体舆论值变化
 * @property reputationDelta 俱乐部声望变化
 */
data class MediaImpact(
    val moraleDelta: Int = 0,
    val fanSatisfactionDelta: Int = 0,
    val boardSatisfactionDelta: Int = 0,
    val opinionDelta: Int = 0,
    val reputationDelta: Int = 0
) {
    /** 合并两个影响结果（用于一次采访多问题累积）。 */
    operator fun plus(other: MediaImpact): MediaImpact = MediaImpact(
        moraleDelta = moraleDelta + other.moraleDelta,
        fanSatisfactionDelta = fanSatisfactionDelta + other.fanSatisfactionDelta,
        boardSatisfactionDelta = boardSatisfactionDelta + other.boardSatisfactionDelta,
        opinionDelta = opinionDelta + other.opinionDelta,
        reputationDelta = reputationDelta + other.reputationDelta
    )

    /** 判断是否无任何影响。 */
    fun isZero(): Boolean = moraleDelta == 0 && fanSatisfactionDelta == 0 &&
        boardSatisfactionDelta == 0 && opinionDelta == 0 && reputationDelta == 0
}

// ==================== 8. 新闻生成事件输入 ====================

/**
 * 新闻生成事件输入（NewsGenerator 的输入参数）。
 *
 * 从 GameEventBus / 比赛结果 / 转会系统收集的简化事件，用于驱动新闻模板生成。
 *
 * @property eventType 事件类型（MATCH_FINISHED / TRANSFER_COMPLETED / INJURY_OCCURRED / TITLE_WON / BUTTERFLY_TRIGGERED / SCANDAL / 等）
 * @property clubId 关联俱乐部 ID（玩家俱乐部或 AI 俱乐部）
 * @property playerId 关联球员 ID（可空）
 * @property matchId 关联比赛 ID（可空，仅比赛类）
 * @property isPlayerClub 是否玩家俱乐部事件（影响生成概率与重要性）
 * @property sameLeague 是否同联赛事件
 * @property placeholders 占位符上下文（player_name / club_name / opponent_name / score 等）
 */
data class NewsEventInput(
    val eventType: String,
    val clubId: Int,
    val playerId: Int? = null,
    val matchId: Int? = null,
    val isPlayerClub: Boolean = false,
    val sameLeague: Boolean = false,
    val placeholders: Map<String, String> = emptyMap()
)

// ==================== 9. 采访问题与选项 ====================

/**
 * 采访问题（运行时构建，不持久化到 history.db，由 [com.greendynasty.football.media.interview.InterviewService] 内置）。
 */
data class InterviewQuestion(
    val questionId: String,
    val scenario: InterviewScenario,
    val category: InterviewQuestionCategory,
    val questionText: String,
    val options: List<AnswerOption>
)

/**
 * 采访回答选项。
 *
 * @property optionId 选项 ID（如 "confident_1"）
 * @property style 回答风格（决定基础影响）
 * @property text 选项文本
 * @property impact 基础影响（可能被上下文调整）
 */
data class AnswerOption(
    val optionId: String,
    val style: AnswerStyle,
    val text: String,
    val impact: MediaImpact
)

// ==================== 10. 采访回答结果 ====================

/**
 * 采访回答结果（[InterviewService.answerQuestion] 输出）。
 *
 * @property impact 该回答产生的最终影响（已应用上下文调整）
 * @property conference 更新后的发布会实体
 * @property isLastQuestion 是否最后一个问题
 * @property isCompleted 发布会是否已完成
 */
data class InterviewAnswerResult(
    val impact: MediaImpact,
    val conference: MediaInterviewEntity,
    val isLastQuestion: Boolean,
    val isCompleted: Boolean
)

// ==================== 11. 媒体快照 ====================

/**
 * 媒体模块快照（UI 一次性消费，由 [com.greendynasty.football.media.repository.MediaRepository] 聚合）。
 *
 * @property saveId 存档 ID
 * @property clubId 俱乐部 ID
 * @property opinion 当前舆论值
 * @property recentNews 最近新闻列表（按日期降序）
 * @property unreadCount 未读新闻数
 * @property pendingInterviews 待处理采访列表
 * @property currentInterview 当前进行中的采访（可空）
 */
data class MediaSnapshot(
    val saveId: Int,
    val clubId: Int,
    val opinion: MediaOpinionEntity?,
    val recentNews: List<MediaNewsEntity>,
    val unreadCount: Int,
    val pendingInterviews: List<MediaInterviewEntity>,
    val currentInterview: MediaInterviewEntity?
)
