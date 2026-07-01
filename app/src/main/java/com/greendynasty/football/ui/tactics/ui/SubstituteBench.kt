package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.sp
import com.greendynasty.football.ui.tactics.data.PlayerWithPosition

/**
 * 替补席（V0.1 03 §3 战术页）。
 *
 * 显示替补球员列表，支持点击将替补拖入首发。
 * V1 简化交互：点击替补球员卡片即触发"放入首发"回调，
 * 由调用方决定放入哪个槽位（默认放入空槽或与当前选中槽位交换）。
 *
 * @param substitutes 替补球员列表
 * @param onSelectSubstitute 替补选择回调（球员 ID）
 * @param modifier 修饰符
 */
@Composable
fun SubstituteBench(
    substitutes: List<PlayerWithPosition>,
    onSelectSubstitute: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "替补席",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${substitutes.size}人",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (substitutes.isEmpty()) {
                Text(
                    text = "暂无替补球员",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(substitutes, key = { it.playerId }) { player ->
                        SubstituteCard(player = player, onClick = { onSelectSubstitute(player.playerId) })
                    }
                }
            }
        }
    }
}

/** 单个替补球员卡片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubstituteCard(
    player: PlayerWithPosition,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(width = 80.dp, height = 100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (player.isAvailable)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.errorContainer
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 球员位置圆圈
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.position,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            // 姓名
            Text(
                text = player.name.take(5),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            // CA
            Text(
                text = "CA ${player.ca}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
            // 状态
            if (!player.isAvailable) {
                Text(
                    text = if (player.isInjured) "伤" else "停",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
