package com.greendynasty.football.scouting.ui.state

import com.greendynasty.football.scouting.data.SaveScoutReportEntity
import com.greendynasty.football.scouting.model.ScoutEventItem
import com.greendynasty.football.scouting.model.ScoutReportDetail
import com.greendynasty.football.scouting.model.ScoutTaskItem
import com.greendynasty.football.scouting.model.ScoutWithKnowledge

/**
 * T14 球探中心页 UI 状态（V0.2 08 §六 UI 结构）。
 *
 * 5 个 Tab：
 * - 球探列表
 * - 正在执行任务
 * - 最新报告
 * - 青年赛事事件
 * - 观察名单（V1 复用 T10 watchlist，本 Tab 暂未实现）
 */
data class ScoutingUiState(
    val isLoading: Boolean = false,
    val scouts: List<ScoutWithKnowledge> = emptyList(),
    val activeTasks: List<ScoutTaskItem> = emptyList(),
    val allTasks: List<ScoutTaskItem> = emptyList(),
    val recentReports: List<SaveScoutReportEntity> = emptyList(),
    val recentEvents: List<ScoutEventItem> = emptyList(),
    val selectedReportDetail: ScoutReportDetail? = null,
    val message: String? = null
)

/**
 * 5 个 Tab 类型。
 */
enum class ScoutingTab(val title: String) {
    SCOUTS("球探列表"),
    TASKS("进行中任务"),
    REPORTS("最新报告"),
    EVENTS("青年赛事事件"),
    WATCHLIST("观察名单")
}

/**
 * 派遣任务弹窗表单状态。
 */
data class DispatchFormState(
    val scoutId: Int = 0,
    val scoutName: String = "",
    val taskTypeIndex: Int = 0, // 0-7 对应 ScoutTaskType 枚举
    val regionCode: String = "BRA",
    val targetPosition: String = "",
    val ageMin: Int = 15,
    val ageMax: Int = 21,
    val durationDays: Int = 30,
    val budgetLevelIndex: Int = 1, // 0/1/2 对应 LOW/MEDIUM/HIGH
    val youthTournamentIndex: Int = 0,
    val targetClubId: Int = 0
) {
    val isValid: Boolean
        get() = scoutId > 0 && targetPosition.isNotBlank() || taskTypeIndex != 1
}
