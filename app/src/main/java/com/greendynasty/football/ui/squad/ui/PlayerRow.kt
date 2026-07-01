package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.squad.model.PlayerWithState

/**
 * 球员行卡片。
 *
 * 展示字段：姓名 / 年龄 / 国籍 / 位置 / CA / PA / 状态 / 体能 / 士气 / 合同到期 / 身价。
 *
 * 交互：
 * - 单击：[onClick] 进入球员详情
 * - 长按：[onLongClick] 触发 [PlayerActionSheet] 操作弹层
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerRow(
    player: PlayerWithState,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { onClick(player.playerId) },
                onLongClick = { onLongClick(player.playerId) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 球衣号码 / 位置色块
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.position,
                    style = MaterialTheme.typography.labelMedium,
                    color = positionColor(player.position)
                )
            }

            // 姓名 / 国籍 / 年龄
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (player.shirtNumber != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#${player.shirtNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Text(
                    text = "${player.nationality} · ${player.age}岁",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // CA / PA
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${player.ca}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "CA/${player.pa}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 状态 / 体能 / 士气
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = player.statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(player)
                )
                Text(
                    text = "体能${player.condition} 士气${player.morale}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "合同 ${player.contractUntil ?: "-"} · ${formatValue(player.marketValue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 位置色 */
private fun positionColor(position: String): Color = when (position) {
    "GK" -> Color(0xFFE65100)
    "CB", "LB", "RB" -> Color(0xFF1565C0)
    "DM", "CM", "AM" -> Color(0xFF2E7D32)
    "LW", "RW", "ST" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

/** 状态色 */
private fun statusColor(player: PlayerWithState): Color = when {
    player.injuryStatus != "healthy" -> Color(0xFFC62828)
    player.isListed -> Color(0xFFEF6C00)
    player.isCaptain -> Color(0xFFFFD700)
    player.isLoaned -> Color(0xFF6A1B9A)
    else -> Color(0xFF2E7D32)
}

/** 身价格式化：万 / 亿 */
private fun formatValue(value: Int): String = when {
    value >= 100_000_000 -> "%.2f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.0f万".format(value / 10_000.0)
    else -> "${value}"
}
