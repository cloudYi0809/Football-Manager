package com.greendynasty.football.ui.schedule.ui.state

import com.greendynasty.football.ui.schedule.model.CupStageUi
import com.greendynasty.football.ui.schedule.model.LeagueTableEntry
import com.greendynasty.football.ui.schedule.model.MatchUi

/**
 * 赛程页 UI 状态（4 种完备状态）。
 *
 * - [Loading]：数据加载中
 * - [Empty]：当前 Tab 无数据（如未生成赛程）
 * - [Error]：数据加载错误
 * - [Ready]：正常展示数据，包含 myMatches / leagueMatches / leagueTable / cupBracket
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class ScheduleUiState {

    /** 加载中 */
    data object Loading : ScheduleUiState()

    /** 空数据：未生成赛程或无比赛 */
    data class Empty(val reason: String = "暂无赛程数据，请先生成赛季赛程") : ScheduleUiState()

    /** 错误 */
    data class Error(val message: String) : ScheduleUiState()

    /** 正常：展示完整赛程数据 */
    data class Ready(
        val myMatches: List<MatchUi> = emptyList(),
        val leagueMatchesByRound: Map<Int, List<MatchUi>> = emptyMap(),
        val leagueTable: List<LeagueTableEntry> = emptyList(),
        val cupStages: List<CupStageUi> = emptyList(),
        val playerClubId: Int = 0,
        val totalRounds: Int = 0,
        val promotionZoneSize: Int = 3,
        val relegationZoneSize: Int = 3
    ) : ScheduleUiState()
}
