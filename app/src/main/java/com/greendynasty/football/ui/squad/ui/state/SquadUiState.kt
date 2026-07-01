package com.greendynasty.football.ui.squad.ui.state

import com.greendynasty.football.ui.squad.model.PlayerWithState

/**
 * 阵容页 UI 状态（6 种完备状态）。
 *
 * - [Loading]：数据加载中
 * - [Empty]：当前梯队无球员
 * - [Error]：数据加载错误
 * - [Normal]：正常展示球员列表
 * - [Locked]：功能未解锁（如未加载存档）
 * - [Warning]：有风险提示但仍展示列表
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class SquadUiState {

    /** 加载中 */
    data object Loading : SquadUiState()

    /** 空数据：当前梯队无球员 */
    data class Empty(val reason: String = "当前梯队暂无球员") : SquadUiState()

    /** 错误 */
    data class Error(val message: String) : SquadUiState()

    /** 正常：展示球员列表 */
    data class Normal(val players: List<PlayerWithState>) : SquadUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : SquadUiState()

    /** 警告：有风险提示但附带球员列表 */
    data class Warning(val message: String, val players: List<PlayerWithState>) : SquadUiState()
}
