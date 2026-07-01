package com.greendynasty.football.ui.squad.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 阵容梯队枚举（顶部 Tab 切换）。
 *
 * 5 个梯队：一线队 / 预备队 / U21 / U18 / 外租球员。
 * 每个枚举提供展示名与图标，供 [com.greendynasty.football.ui.squad.ui.SquadTabBar] 渲染。
 *
 * 与 SavePlayerStateEntity.squad_role 的映射：
 * - FIRST_TEAM / RESERVE / U21 / U18 直接对应 squad_role 字段。
 * - LOAN_OUT 通过 loan_club_id 非空判定（见 SquadRepository）。
 */
enum class SquadTab(val displayName: String, val icon: ImageVector) {

    FIRST_TEAM("一线队", Icons.Default.Star),
    RESERVE("预备队", Icons.Default.Person),
    U21("U21", Icons.Default.Home),
    U18("U18", Icons.Default.PlayArrow),
    LOAN_OUT("外租球员", Icons.Default.Send);

    companion object {
        /** 默认进入页面的梯队 */
        val DEFAULT: SquadTab = FIRST_TEAM
    }
}
