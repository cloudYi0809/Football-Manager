package com.greendynasty.football.transfer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greendynasty.football.transfer.model.SigningDifficulty
import com.greendynasty.football.transfer.model.WatchlistEntry
import com.greendynasty.football.transfer.ui.state.WatchlistUiState

/**
 * 观察名单列表（V0.1 09 §二.2）。
 *
 * 处理 3 种 UI 状态：Loading / Empty / Normal。
 * Normal 状态展示观察名单卡片列表，每张卡片包含：
 * - 球员 ID / 加入日期
 * - 球探报告等级
 * - 预计身价
 * - 签约难度（彩色标签）
 * - 竞争球队数量
 * - 球探建议
 *
 * @param state 观察名单 UI 状态
 * @param onRemove 移出观察名单回调
 */
@Composable
fun WatchlistView(
    state: WatchlistUiState,
    onRemove: (Int) -> Unit
) {
    when (state) {
        is WatchlistUiState.Loading -> LoadingView()
        is WatchlistUiState.Empty -> EmptyView(state.reason)
        is WatchlistUiState.Normal -> WatchlistList(
            entries = state.entries,
            onRemove = onRemove
        )
    }
}

/** 正常观察名单列表 */
@Composable
private fun WatchlistList(
    entries: List<WatchlistEntry>,
    onRemove: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 4.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "观察名单 ${entries.size} 人",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        items(entries, key = { it.playerId }) { entry ->
            WatchlistCard(entry = entry, onRemove = { onRemove(entry.playerId) })
        }
    }
}

/**
 * 观察名单卡片。
 */
@Composable
private fun WatchlistCard(
    entry: WatchlistEntry,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：球员 ID + 加入日期 + 移除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "球员 #${entry.playerId}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "加入于 ${entry.addedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "移出观察名单",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 第二行：报告等级 + 身价 + 签约难度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoChip(label = "报告", value = "L${entry.reportLevel}")
                InfoChip(label = "身价", value = formatValue(entry.estimatedValue))
                SigningDifficultyChip(difficulty = entry.signingDifficulty)
                if (entry.competitorClubs.isNotEmpty()) {
                    InfoChip(label = "竞争", value = "${entry.competitorClubs.size} 家")
                }
            }

            // 第三行：球探建议
            entry.scoutRecommendation?.let { recommendation ->
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "球探建议：$recommendation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

/** 信息小标签 */
@Composable
private fun InfoChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 签约难度彩色标签 */
@Composable
private fun SigningDifficultyChip(difficulty: SigningDifficulty) {
    val color = Color(difficulty.color)
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "签约 ${difficulty.label}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 身价格式化 */
private fun formatValue(value: Int): String = when {
    value >= 100_000_000 -> "%.2f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.0f万".format(value / 10_000.0)
    else -> "$value"
}
