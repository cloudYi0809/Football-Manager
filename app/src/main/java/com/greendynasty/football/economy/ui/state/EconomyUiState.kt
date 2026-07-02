package com.greendynasty.football.economy.ui.state

import com.greendynasty.football.economy.model.ClubFinancialState
import com.greendynasty.football.economy.model.EconomyIndexSnapshot
import com.greendynasty.football.economy.model.FinancialHealthReport
import com.greendynasty.football.economy.model.LeagueEconomySnapshot

/**
 * T17 经济概览页 UI 状态（5 种完备状态，对齐 GrowthUiState 模式）。
 *
 * - [Loading]：数据加载中
 * - [Locked]：功能未解锁（未加载存档）
 * - [Error]：数据加载错误
 * - [Empty]：无经济数据（新存档首次进入）
 * - [Normal]：展示经济概览（当前指数 + 趋势图 + 9 联赛 + 财政健康）
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class EconomyUiState {

    /** 加载中 */
    data object Loading : EconomyUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : EconomyUiState()

    /** 错误 */
    data class Error(val message: String) : EconomyUiState()

    /** 空数据 */
    data class Empty(val reason: String = "暂无经济数据") : EconomyUiState()

    /** 正常：展示经济概览 */
    data class Normal(
        /** 当前游戏日期（展示用） */
        val currentDate: String,
        /** 当前年份 */
        val currentYear: Int,
        /** 当前经济指数快照 */
        val currentIndex: EconomyIndexSnapshot,
        /** 通胀趋势（1992 至当前年份） */
        val trend: List<EconomyIndexSnapshot>,
        /** 9 大联赛商业快照（按系数降序） */
        val leagueSnapshots: List<LeagueEconomySnapshot>,
        /** 玩家俱乐部财政状态 */
        val clubFinancial: ClubFinancialState?,
        /** 玩家俱乐部财政健康报告 */
        val healthReport: FinancialHealthReport?
    ) : EconomyUiState()
}
