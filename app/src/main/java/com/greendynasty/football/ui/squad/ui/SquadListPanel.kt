package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.squad.model.PlayerWithState
import com.greendynasty.football.ui.squad.model.SquadSortOption

/**
 * 球员列表面板。
 *
 * - 表头可点击切换排序
 * - [LazyColumn] 分页 50 条展示，底部“加载更多”按钮
 * - 列表项用 [playerId] 作为 key 避免重排
 *
 * @param players 当前展示的球员列表（已筛选/排序）
 * @param currentSort 当前排序（用于表头高亮）
 * @param onSortChange 表头点击切换排序
 * @param onPlayerClick 球员行单击
 * @param onPlayerLongClick 球员行长按
 */
@Composable
fun SquadListPanel(
    players: List<PlayerWithState>,
    currentSort: SquadSortOption,
    onSortChange: (SquadSortOption) -> Unit,
    onPlayerClick: (Int) -> Unit,
    onPlayerLongClick: (Int) -> Unit
) {
    val pageSize = 50
    var visibleCount by remember { mutableIntStateOf(pageSize) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 表头
        item(key = "header") {
            SquadListHeader(
                currentSort = currentSort,
                onSortChange = onSortChange
            )
        }

        // 球员行
        val visible = players.take(visibleCount)
        items(items = visible, key = { it.playerId }) { player ->
            PlayerRow(
                player = player,
                onClick = onPlayerClick,
                onLongClick = onPlayerLongClick
            )
        }

        // 加载更多
        if (visibleCount < players.size) {
            item(key = "load_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = { visibleCount += pageSize }) {
                        Text("加载更多（剩余 ${players.size - visibleCount}）")
                    }
                }
            }
        } else if (players.isNotEmpty()) {
            item(key = "footer") {
                Text(
                    text = "共 ${players.size} 名球员",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * 列表表头：可点击切换排序。
 */
@Composable
private fun SquadListHeader(
    currentSort: SquadSortOption,
    onSortChange: (SquadSortOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HeaderCell("姓名", weight = 1f, isSorted = currentSort == SquadSortOption.NAME_ASC) {
            onSortChange(SquadSortOption.NAME_ASC)
        }
        HeaderCell("CA", weight = 0.5f, isSorted = currentSort == SquadSortOption.CA_DESC) {
            onSortChange(SquadSortOption.CA_DESC)
        }
        HeaderCell("PA", weight = 0.5f, isSorted = currentSort == SquadSortOption.PA_DESC) {
            onSortChange(SquadSortOption.PA_DESC)
        }
        HeaderCell("年龄", weight = 0.5f, isSorted = currentSort == SquadSortOption.AGE_ASC) {
            onSortChange(SquadSortOption.AGE_ASC)
        }
        HeaderCell("体能", weight = 0.5f, isSorted = currentSort == SquadSortOption.CONDITION_DESC) {
            onSortChange(SquadSortOption.CONDITION_DESC)
        }
        HeaderCell("士气", weight = 0.5f, isSorted = currentSort == SquadSortOption.MORALE_DESC) {
            onSortChange(SquadSortOption.MORALE_DESC)
        }
        HeaderCell("身价", weight = 0.6f, isSorted = currentSort == SquadSortOption.VALUE_DESC) {
            onSortChange(SquadSortOption.VALUE_DESC)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(
    text: String,
    weight: Float,
    isSorted: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = if (isSorted) "$text ▼" else text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isSorted) FontWeight.Bold else FontWeight.Normal,
        color = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .weight(weight)
            .clickable { onClick() }
    )
}
