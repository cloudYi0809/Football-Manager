package com.greendynasty.football.ui.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.schedule.model.ScheduleTab

/**
 * 赛程页顶部 Tab 切换栏
 *
 * 3 个 Tab：我的赛程 / 联赛 / 杯赛（V1 范围，欧战与国家队推迟到后期）。
 */
@Composable
fun ScheduleTabBar(
    selectedTab: ScheduleTab,
    onTabSelected: (ScheduleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = ScheduleTab.values()
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        edgePadding = 0.dp
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.displayName,
                        fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

/**
 * 积分榜视图切换条（overall / home / away）
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StandingViewToggle(
    selected: com.greendynasty.football.ui.schedule.model.StandingViewType,
    onChanged: (com.greendynasty.football.ui.schedule.model.StandingViewType) -> Unit,
    modifier: Modifier = Modifier
) {
    val views = com.greendynasty.football.ui.schedule.model.StandingViewType.values()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        views.forEach { v ->
            androidx.compose.material3.FilterChip(
                selected = v == selected,
                onClick = { onChanged(v) },
                label = {
                    Text(
                        text = when (v) {
                            com.greendynasty.football.ui.schedule.model.StandingViewType.OVERALL -> "总"
                            com.greendynasty.football.ui.schedule.model.StandingViewType.HOME -> "主"
                            com.greendynasty.football.ui.schedule.model.StandingViewType.AWAY -> "客"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
