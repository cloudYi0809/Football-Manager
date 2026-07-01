package com.greendynasty.football.ui.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.schedule.model.LeagueTableEntry
import com.greendynasty.football.ui.schedule.model.LeagueTableEntryView
import com.greendynasty.football.ui.schedule.model.StandingViewType

/**
 * 积分榜面板
 *
 * 列：排名 ｜ 队名 ｜ 赛 ｜ 胜 ｜ 平 ｜ 负 ｜ 进 ｜ 失 ｜ 净 ｜ 分
 *
 * 升降级分区色块：
 * - 升级区（前 N 名）：绿色
 * - 降级区（后 N 名）：红色
 *
 * @param table 完整积分榜（含主客场拆分）
 * @param playerClubId 玩家俱乐部 ID（行高亮）
 * @param view 当前视图类型（overall / home / away）
 * @param promotionZoneSize 升级区名额
 * @param relegationZoneSize 降级区名额
 */
@Composable
fun LeagueTablePanel(
    table: List<LeagueTableEntry>,
    playerClubId: Int,
    view: StandingViewType = StandingViewType.OVERALL,
    promotionZoneSize: Int = 3,
    relegationZoneSize: Int = 3,
    modifier: Modifier = Modifier
) {
    if (table.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无积分数据",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val totalTeams = table.size
    val promotionZone = 1..promotionZoneSize.coerceAtMost(totalTeams)
    val relegationZone = (totalTeams - relegationZoneSize + 1).coerceAtLeast(1)..totalTeams

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { TableHeader() }
        items(table) { entry ->
            val viewEntry = entry.forView(view)
            val zoneColor = when (entry.rank) {
                in promotionZone -> PromotionColor
                in relegationZone -> RelegationColor
                else -> Color.Transparent
            }
            val rowBackground = when {
                entry.clubId == playerClubId -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else -> zoneColor
            }
            TableRow(
                view = viewEntry,
                rank = entry.rank,
                rowBackground = rowBackground,
                isPlayerClub = entry.clubId == playerClubId
            )
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("球队", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("赛", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("胜", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("平", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("负", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("进", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("失", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("净", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text("分", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TableRow(
    view: LeagueTableEntryView,
    rank: Int,
    rowBackground: Color,
    isPlayerClub: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isPlayerClub) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = view.clubName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isPlayerClub) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        Text("${view.played}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("${view.won}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("${view.drawn}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("${view.lost}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("${view.goalsFor}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("${view.goalsAgainst}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text("${view.goalDifference}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(
            "${view.points}",
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** 升级区淡绿色 */
private val PromotionColor = Color(0x3300C853)

/** 降级区淡红色 */
private val RelegationColor = Color(0x33FF1744)
