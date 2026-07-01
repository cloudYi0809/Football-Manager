package com.greendynasty.football.ui.advance.state

import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.MatchResultSummary
import com.greendynasty.football.simulation.api.NewsItem
import com.greendynasty.football.simulation.api.TodoItem
import java.time.LocalDate

/**
 * 推进页 UI 状态（T07 方案 §三 AdvanceResult 的 UI 层映射）
 *
 * 三种状态：
 * - [Loading]：推进中（禁用按钮）
 * - [Ready]：空闲或推进完成（展示事件列表 + 推进按钮）
 * - [Error]：推进失败（展示回滚原因）
 */
sealed interface AdvanceUiState {

    /** 推进中 */
    data object Loading : AdvanceUiState

    /** 推进完成 / 空闲 */
    data class Ready(
        /** 当前游戏内日期 */
        val currentDate: LocalDate,
        /** 最近一次推进产生的事件列表 */
        val events: List<AdvanceEvent>,
        /** 最近一次推进产生的比赛结果 */
        val matches: List<MatchResultSummary>,
        /** 最近一次推进产生的新闻 */
        val news: List<NewsItem>,
        /** 当前待办列表 */
        val todos: List<TodoItem>,
        /** 最近一次推进耗时（毫秒） */
        val lastDurationMs: Long,
        /** 是否正在推进（用于禁用按钮） */
        val isAdvancing: Boolean
    ) : AdvanceUiState

    /** 推进失败（已回滚） */
    data class Error(
        val currentDate: LocalDate,
        val message: String
    ) : AdvanceUiState
}
