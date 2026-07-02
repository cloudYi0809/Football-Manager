package com.greendynasty.football.transfer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greendynasty.football.transfer.model.TransferSearchResult
import com.greendynasty.football.transfer.model.TransferStatus
import com.greendynasty.football.transfer.ui.state.TransferSearchUiState

/**
 * 搜索结果列表（V0.1 09 §二.1）。
 *
 * 处理 5 种 UI 状态：Loading / Empty / Error / Normal / Locked。
 * Normal 状态展示球员卡片列表，支持加入观察名单 / 加入对比。
 *
 * @param state UI 状态
 * @param onToggleWatchlist 切换观察名单回调
 * @param onToggleCompare 切换对比选择回调
 * @param compareSelection 当前已加入对比的球员 ID 集合
 */
@Composable
fun SearchResultList(
    state: TransferSearchUiState,
    compareSelection: Set<Int>,
    onToggleWatchlist: (Int) -> Unit,
    onToggleCompare: (Int) -> Unit
) {
    when (state) {
        is TransferSearchUiState.Loading -> LoadingView()
        is TransferSearchUiState.Empty -> EmptyView(state.reason)
        is TransferSearchUiState.Error -> ErrorView(state.message)
        is TransferSearchUiState.Locked -> LockedView(state.reason)
        is TransferSearchUiState.Normal -> ResultList(
            results = state.results,
            totalCount = state.totalCount,
            compareSelection = compareSelection,
            onToggleWatchlist = onToggleWatchlist,
            onToggleCompare = onToggleCompare
        )
    }
}

/** 正常结果列表 */
@Composable
private fun ResultList(
    results: List<TransferSearchResult>,
    totalCount: Int,
    compareSelection: Set<Int>,
    onToggleWatchlist: (Int) -> Unit,
    onToggleCompare: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "共 $totalCount 名球员",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(results, key = { it.playerId }) { result ->
                PlayerSearchRow(
                    result = result,
                    isOnWatchlist = result.isOnWatchlist,
                    isInCompare = result.playerId in compareSelection,
                    onToggleWatchlist = { onToggleWatchlist(result.playerId) },
                    onToggleCompare = { onToggleCompare(result.playerId) }
                )
            }
        }
    }
}

/**
 * 搜索结果球员卡片。
 *
 * 展示：位置色块 / 姓名 / 国籍 / 年龄 / CA/PA / 身价 / 合同 / 状态 / 球探报告等级。
 * 操作：加入观察名单（星标）/ 加入对比。
 */
@Composable
fun PlayerSearchRow(
    result: TransferSearchResult,
    isOnWatchlist: Boolean,
    isInCompare: Boolean,
    onToggleWatchlist: () -> Unit,
    onToggleCompare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 位置色块
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result.position,
                    style = MaterialTheme.typography.labelMedium,
                    color = positionColor(result.position),
                    fontWeight = FontWeight.Bold
                )
            }

            // 姓名 / 国籍 / 俱乐部
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.playerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${result.age}岁",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = buildString {
                        append(result.nationality)
                        result.clubName?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = result.transferStatus)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "报告 L${result.scoutingReportLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // CA / PA
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${result.currentCa}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "CA/${result.potentialPa}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 身价 + 操作
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatValue(result.marketValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "合同 ${result.contractUntil ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row {
                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = if (isOnWatchlist) "★" else "☆",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOnWatchlist) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = onToggleCompare,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isInCompare) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/** 转会状态小标签 */
@Composable
private fun StatusChip(status: TransferStatus) {
    val color = when (status) {
        TransferStatus.TRANSFERABLE -> Color(0xFFEF6C00)
        TransferStatus.LOANABLE -> Color(0xFF6A1B9A)
        TransferStatus.FREE_AGENT -> Color(0xFF2E7D32)
    }
    Text(
        text = status.label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

/** 位置色 */
private fun positionColor(position: String): Color = when (position) {
    "GK" -> Color(0xFFE65100)
    "CB", "LB", "RB" -> Color(0xFF1565C0)
    "DM", "CM", "AM" -> Color(0xFF2E7D32)
    "LW", "RW", "ST", "CF" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

/** 身价格式化：万 / 亿 */
private fun formatValue(value: Int): String = when {
    value >= 100_000_000 -> "%.2f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.0f万".format(value / 10_000.0)
    else -> "$value"
}

// ==================== 通用状态视图 ====================

@Composable
internal fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text("加载中…", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
internal fun EmptyView(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
internal fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
internal fun LockedView(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "🔒", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = reason,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
