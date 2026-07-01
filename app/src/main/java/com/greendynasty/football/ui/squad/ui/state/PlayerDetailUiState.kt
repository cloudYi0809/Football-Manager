package com.greendynasty.football.ui.squad.ui.state

import com.greendynasty.football.ui.squad.data.PlayerDetail

/**
 * 球员详情页 UI 状态。
 *
 * - [Loading]：详情聚合中
 * - [Error]：聚合失败
 * - [Normal]：展示完整 [PlayerDetail]（10 个模块）
 */
sealed class PlayerDetailUiState {

    /** 加载中 */
    data object Loading : PlayerDetailUiState()

    /** 错误 */
    data class Error(val message: String) : PlayerDetailUiState()

    /** 正常：展示完整球员详情 */
    data class Normal(val detail: PlayerDetail) : PlayerDetailUiState()
}
