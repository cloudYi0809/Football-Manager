package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.squad.model.SquadTab

/**
 * 阵容页顶部梯队切换栏（5 个梯队）。
 *
 * 使用 Material3 [ScrollableTabRow] 实现，点击 Tab 触发 [onTabSelected]。
 *
 * @param currentTab 当前选中的梯队
 * @param onTabSelected 切换梯队回调
 */
@Composable
fun SquadTabBar(
    currentTab: SquadTab,
    onTabSelected: (SquadTab) -> Unit
) {
    val tabs = SquadTab.values()
    val selectedIndex = tabs.indexOf(currentTab)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == currentTab,
                onClick = { onTabSelected(tab) },
                text = { Text(text = tab.displayName) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.displayName,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}
