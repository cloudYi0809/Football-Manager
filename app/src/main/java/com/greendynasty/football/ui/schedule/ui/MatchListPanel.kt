package com.greendynasty.football.ui.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.schedule.model.MatchStatus
import com.greendynasty.football.ui.schedule.model.MatchUi

/**
 * 比赛列表面板
 *
 * 支持两种展示模式：
 * - 平铺：[matches] 直接列出
 * - 按轮次分组：[matchesByRound] 提供时优先使用，每组带「第 N 轮」标题
 */
@Composable
fun MatchListPanel(
    matches: List<MatchUi> = emptyList(),
    matchesByRound: Map<Int, List<MatchUi>> = emptyMap(),
    playerClubId: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (matchesByRound.isNotEmpty()) {
            matchesByRound.forEach { (round, roundMatches) ->
                item {
                    RoundHeader(round = round, matchCount = roundMatches.size)
                }
                items(roundMatches) { match ->
                    MatchRow(match = match, playerClubId = playerClubId)
                }
            }
        } else {
            items(matches) { match ->
                MatchRow(match = match, playerClubId = playerClubId)
            }
        }
    }
}

@Composable
private fun RoundHeader(round: Int, matchCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "第 $round 轮 · $matchCount 场",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 比赛行
 *
 * 布局：日期 ｜ 赛事 ｜ 主队 比分 客队 ｜ 状态
 *
 * 玩家俱乐部行高亮（队名加粗，背景淡色标记）。
 */
@Composable
fun MatchRow(
    match: MatchUi,
    playerClubId: Int,
    modifier: Modifier = Modifier
) {
    val isPlayerMatch = match.isPlayerMatch || match.homeClubId == playerClubId ||
        match.awayClubId == playerClubId
    val rowBackground = if (isPlayerMatch) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日期（取 MM-DD）
        Text(
            text = match.matchDate.takeLast(5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(52.dp),
            color = MaterialTheme.colorScheme.outline
        )
        // 赛事短名
        Text(
            text = match.competitionShortName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp),
            color = MaterialTheme.colorScheme.outline
        )
        // 主队 + 比分 + 客队
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = match.homeClubName,
                fontWeight = if (match.homeClubId == playerClubId) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium
            )
            ScoreBlock(
                homeScore = match.homeScore,
                awayScore = match.awayScore,
                status = match.status,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Text(
                text = match.awayClubName,
                fontWeight = if (match.awayClubId == playerClubId) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        // 状态指示
        StatusIndicator(status = match.status, modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ScoreBlock(
    homeScore: Int?,
    awayScore: Int?,
    status: MatchStatus,
    modifier: Modifier = Modifier
) {
    val text = if (homeScore != null && awayScore != null) {
        "$homeScore - $awayScore"
    } else {
        "VS"
    }
    // 进行中比赛用强调色高亮比分
    val color = if (status == MatchStatus.IN_PROGRESS) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatusIndicator(status: MatchStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        MatchStatus.SCHEDULED -> "未开始" to MaterialTheme.colorScheme.outline
        MatchStatus.IN_PROGRESS -> "进行中" to MaterialTheme.colorScheme.tertiary
        MatchStatus.FINISHED -> "已结束" to MaterialTheme.colorScheme.secondary
        MatchStatus.POSTPONED -> "已推迟" to MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

/** 空数据视图 */
@Composable
fun EmptyScheduleView(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
