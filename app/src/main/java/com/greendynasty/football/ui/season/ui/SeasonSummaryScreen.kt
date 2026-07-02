package com.greendynasty.football.ui.season.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.season.summary.AwardSummary
import com.greendynasty.football.season.summary.ClubFinancialSummary
import com.greendynasty.football.season.summary.LeagueStandingSummary
import com.greendynasty.football.season.summary.ScorerListSummary
import com.greendynasty.football.season.summary.SeasonSummary
import com.greendynasty.football.season.summary.StandingEntry
import com.greendynasty.football.season.summary.TransferRecord
import com.greendynasty.football.season.summary.TransferSummary
import com.greendynasty.football.ui.season.ui.state.ArchivedSeasonDisplay
import com.greendynasty.football.ui.season.ui.state.SeasonSummaryUiState
import com.greendynasty.football.ui.season.viewmodel.SeasonSummaryViewModel

/**
 * 赛季总结页（T19 赛季归档 UI 入口）
 *
 * 轻量级记录页设计：
 * - 顶部：赛季切换条（横向滚动归档赛季列表）
 * - 中部：选中赛季摘要（联赛排名 / 杯赛成绩 / 射手榜 / 助攻榜 / 转会汇总 / 奖项 / 财政）
 */
@Composable
fun SeasonSummaryScreen(
    viewModel: SeasonSummaryViewModel = viewModel(factory = SeasonSummaryViewModel.factory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        when (val state = uiState) {
            is SeasonSummaryUiState.Loading -> LoadingView(padding)
            is SeasonSummaryUiState.Locked -> EmptyView(padding, state.reason)
            is SeasonSummaryUiState.Empty -> EmptyView(padding, state.reason)
            is SeasonSummaryUiState.Error -> ErrorView(padding, state.message)
            is SeasonSummaryUiState.Normal -> SeasonSummaryContent(
                padding = padding,
                archivedSeasons = state.archivedSeasons,
                summary = state.selectedSummary,
                onSelectSeason = viewModel::selectSeason
            )
        }
    }
}

// ==================== 内容视图 ====================

@Composable
private fun SeasonSummaryContent(
    padding: PaddingValues,
    archivedSeasons: List<ArchivedSeasonDisplay>,
    summary: SeasonSummary?,
    onSelectSeason: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "赛季总结",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // 赛季切换条
        item {
            SeasonSelectorRow(archivedSeasons, onSelectSeason)
        }

        if (summary == null) {
            item {
                Text(
                    text = "该赛季无摘要数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            // 赛季标签
            item {
                Text(
                    text = summary.seasonLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 联赛积分榜
            if (summary.leagueStandings.isNotEmpty()) {
                item {
                    Text(
                        text = "联赛排名",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(summary.leagueStandings, key = { it.leagueId }) { league ->
                    LeagueStandingCard(league)
                }
            }

            // 射手榜
            if (summary.topScorers.isNotEmpty()) {
                item {
                    Text(
                        text = "射手榜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(summary.topScorers, key = { it.playerId }) { scorer ->
                    ScorerRow(scorer, "球")
                }
            }

            // 助攻榜
            if (summary.topAssists.isNotEmpty()) {
                item {
                    Text(
                        text = "助攻榜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(summary.topAssists, key = { it.playerId }) { scorer ->
                    ScorerRow(scorer, "助攻")
                }
            }

            // 转会汇总
            if (summary.transfers.totalTransfers > 0) {
                item { TransferSummaryCard(summary.transfers) }
            }

            // 赛季奖项
            if (summary.awards.isNotEmpty()) {
                item {
                    Text(
                        text = "赛季奖项",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(summary.awards, key = { it.awardType }) { award ->
                    AwardRow(award)
                }
            }

            // 俱乐部财政
            item { FinancialCard(summary.managerClubFinancial) }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ==================== 子组件 ====================

@Composable
private fun SeasonSelectorRow(
    seasons: List<ArchivedSeasonDisplay>,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(seasons, key = { it.seasonId }) { season ->
            val bgColor = if (season.isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val textColor = if (season.isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = season.seasonLabel,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(16.dp))
                    .clickable { onSelect(season.seasonId) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LeagueStandingCard(league: LeagueStandingSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = league.leagueName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 表头
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("#", modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall)
                Text("俱乐部", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text("场", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
                Text("胜", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
                Text("平", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
                Text("负", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
                Text("积分", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            league.standings.sortedBy { it.position }.forEach { entry ->
                StandingRow(entry)
            }
        }
    }
}

@Composable
private fun StandingRow(entry: StandingEntry) {
    val positionColor = when (entry.position) {
        1 -> Color(0xFFFFD700) // 金
        2 -> Color(0xFFC0C0C0) // 银
        3 -> Color(0xFFCD7F32) // 铜
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(0.4f), contentAlignment = Alignment.CenterStart) {
            Text(
                text = "${entry.position}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (entry.position <= 3) FontWeight.Bold else FontWeight.Normal,
                color = if (entry.position <= 3) positionColor else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = entry.clubName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(2f),
            maxLines = 1
        )
        Text("${entry.played}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
        Text("${entry.won}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
        Text("${entry.drawn}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
        Text("${entry.lost}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
        Text(
            "${entry.points}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun ScorerRow(scorer: ScorerListSummary, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = scorer.playerName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = scorer.clubName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = "${scorer.goals} $unit",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TransferSummaryCard(transfers: TransferSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "转会汇总",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "总交易 ${transfers.totalTransfers} 笔  ·  总额 ¥${transfers.totalFee}  ·  标王 ¥${transfers.maxFee}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (transfers.topTransfers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Top 转会",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                transfers.topTransfers.take(5).forEach { record ->
                    TransferRecordRow(record)
                }
            }
        }
    }
}

@Composable
private fun TransferRecordRow(record: TransferRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.playerName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${record.fromClubName} → ${record.toClubName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "¥${record.fee}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AwardRow(award: AwardSummary) {
    val awardColor = when (award.awardType) {
        "golden_boot" -> Color(0xFFFFD700)
        "golden_ball" -> Color(0xFFFFD700)
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🏆",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = award.awardName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = awardColor
            )
            if (award.playerName.isNotEmpty()) {
                Text(
                    text = award.playerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = if (award.statValue > 0) "${award.statValue}" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FinancialCard(financial: ClubFinancialSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "俱乐部财政",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("余额", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥${financial.balance}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("转会预算", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥${financial.transferBudget}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("工资预算", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥${financial.wageBudget}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("董事会满意度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${financial.boardSatisfaction}%", style = MaterialTheme.typography.bodyMedium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("球迷满意度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${financial.fanSatisfaction}%", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ==================== 占位视图 ====================

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(padding: PaddingValues, reason: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorView(padding: PaddingValues, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}
