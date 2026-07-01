package com.greendynasty.football.simulation.api

import java.time.LocalDate

/**
 * 推进上下文（V0.1 11 §一 + T07 方案 §二）
 *
 * 每次推进前构建，封装推进所需的全部上下文信息。
 * 所有每日/每周/每月/赛季任务均接收此上下文。
 *
 * @param saveId 数据表存档 ID（save_player_state.save_id 等，Int 类型）
 * @param saveUuid 存档 UUID（save_manifest.save_id，用于 checkpoint/perf_log）
 * @param currentDate 当前游戏内日期（推进前）
 * @param nextDate 下一日日期（推进目标）
 * @param currentSeasonId 当前赛季 ID
 * @param managerClubId 玩家执教的俱乐部 ID
 * @param activeLeagueIds 活跃联赛 ID 列表（玩家所在联赛 + 关联联赛）
 * @param isTransferWindowOpen 转会窗是否开启
 * @param isMatchDay 玩家俱乐部今日是否有比赛
 * @param isWeekStart 是否周一（每周任务触发）
 * @param isMonthStart 是否月初（每月任务触发）
 * @param isSeasonEnd 是否赛季结束日
 * @param randomSeed 可复现随机种子（由当前日期派生）
 */
data class AdvanceContext(
    val saveId: Int,
    val saveUuid: String,
    val currentDate: LocalDate,
    val nextDate: LocalDate,
    val currentSeasonId: Int,
    val managerClubId: Int,
    val activeLeagueIds: List<Int>,
    val isTransferWindowOpen: Boolean,
    val isMatchDay: Boolean,
    val isWeekStart: Boolean,
    val isMonthStart: Boolean,
    val isSeasonEnd: Boolean,
    val randomSeed: Long
)

/**
 * 推进结果（V0.1 11 + T07 方案 §二）
 *
 * 封装一次推进产生的全部结果：事件、比赛、新闻、待办、耗时。
 * 若推进失败回滚，则 [rollbackReason] 非空。
 */
data class AdvanceResult(
    val newDate: LocalDate,
    val events: List<AdvanceEvent>,
    val matches: List<MatchResultSummary>,
    val news: List<NewsItem>,
    val todos: List<TodoItem>,
    val durationMs: Long,
    val rollbackReason: String? = null
) {
    /** 推进是否成功（未回滚） */
    val isSuccess: Boolean get() = rollbackReason == null
}

/**
 * 推进期间产生的单条事件
 */
data class AdvanceEvent(
    val type: AdvanceEventType,
    val description: String,
    val clubId: Int?,
    val playerId: Int?,
    val priority: EventPriority
)

/**
 * 比赛结果摘要（用于 UI 展示，与 match 引擎的 MatchResult 区分）
 */
data class MatchResultSummary(
    val matchId: Int,
    val homeClubId: Int,
    val awayClubId: Int,
    val homeClubName: String,
    val awayClubName: String,
    val homeScore: Int,
    val awayScore: Int,
    val isPlayerMatch: Boolean,
    val competitionId: Int
)

/**
 * 新闻摘要（用于 UI 展示）
 */
data class NewsItem(
    val title: String,
    val body: String,
    val newsType: String,
    val relatedClubId: Int?,
    val relatedPlayerId: Int?,
    val date: String
)

/**
 * 待办事项（用于 UI 展示）
 */
data class TodoItem(
    val id: String,
    val title: String,
    val priority: EventPriority,
    val dueDate: String?
)

/**
 * 推进事件类型（V0.1 11 §二.1 对应 13 项每日任务 + 每周/每月/赛季事件）
 */
enum class AdvanceEventType {
    TRAINING_COMPLETE,
    CONDITION_CHANGE,
    INJURY_RECOVERED,
    INJURY_OCCURRED,
    MORALE_CHANGE,
    SCOUT_REPORT_READY,
    YOUTH_PROMOTED,
    TRANSFER_COMPLETED,
    TRANSFER_OFFER_RECEIVED,
    AI_ACTION,
    MATCH_PLAYED,
    HISTORICAL_EVENT,
    CONTRACT_EXPIRED,
    RETIREMENT_ANNOUNCED,
    NEWS_PUBLISHED,
    BOARD_REVIEW
}

/** 事件优先级 */
enum class EventPriority { LOW, MEDIUM, HIGH, URGENT }
