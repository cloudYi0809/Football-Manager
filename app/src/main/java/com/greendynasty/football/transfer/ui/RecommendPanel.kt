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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greendynasty.football.transfer.model.PlayerRecommendation
import com.greendynasty.football.transfer.ui.state.RecommendUiState

/**
 * 球员推荐列表（V0.2 §四）。
 *
 * 处理 5 种 UI 状态，Normal 状态展示按 matchScore 排序的推荐列表，
 * 每张卡片包含：综合匹配度进度条 / 推荐理由 / 薄弱位置加成 / 战术匹配度。
 *
 * @param state 推荐页 UI 状态
 */
@Composable
fun RecommendPanel(
    state: RecommendUiState
) {
    when (state) {
        is RecommendUiState.Loading -> LoadingView()
        is RecommendUiState.Empty -> EmptyView(state.reason)
        is RecommendUiState.Error -> ErrorView(state.message)
        is RecommendUiState.Locked -> LockedView(state.reason)
        is RecommendUiState.Normal -> RecommendList(
            recommendations = state.recommendations,
            weakPositions = state.weakPositions
        )
    }
}

/** 正常推荐列表 */
@Composable
private fun RecommendList(
    recommendations: List<PlayerRecommendation>,
    weakPositions: Set<String>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 4.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (weakPositions.isNotEmpty()) {
            item {
                WeakPositionBanner(weakPositions = weakPositions)
            }
        }
        items(recommendations, key = { it.result.playerId }) { rec ->
            RecommendCard(recommendation = rec)
        }
    }
}

/** 薄弱位置横幅 */
@Composable
private fun WeakPositionBanner(weakPositions: Set<String>) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = "球队薄弱位置：${weakPositions.joinToString(" / ")}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * 推荐卡片：综合匹配度进度条 + 推荐理由 + 球员核心信息。
 */
@Composable
private fun RecommendCard(
    recommendation: PlayerRecommendation
) {
    val result = recommendation.result
    val score = recommendation.matchScore
    val scoreColor = matchScoreColor(score)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：位置色块 + 姓名 + 年龄 + CA/PA + 综合匹配度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = result.playerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${result.age}岁 · CA ${result.currentCa}/${result.potentialPa}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "${result.nationality} · ${formatValue(result.marketValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 综合匹配度
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${score.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                    Text(
                        text = "匹配度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 第二行：匹配度进度条
            @Suppress("DEPRECATION")
            LinearProgressIndicator(
                progress = (score / 100.0).coerceIn(0.0, 1.0).toFloat(),
                color = scoreColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            // 第三行：推荐理由（bullet 列表）
            if (recommendation.reasons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    recommendation.reasons.take(3).forEach { reason ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = scoreColor,
                                modifier = Modifier.width(8.dp)
                            )
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 第四行：加成标签
            if (recommendation.weakPositionBonus > 0 || recommendation.styleMatchScore >= 80) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (recommendation.weakPositionBonus > 0) {
                        BonusChip(text = "+薄弱位置", color = Color(0xFFEF6C00))
                    }
                    if (recommendation.styleMatchScore >= 80) {
                        BonusChip(text = "战术契合", color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

/** 加成小标签 */
@Composable
private fun BonusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 综合匹配度配色 */
private fun matchScoreColor(score: Double): Color = when {
    score >= 80 -> Color(0xFF2E7D32)
    score >= 60 -> Color(0xFF1565C0)
    score >= 40 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

/** 位置色 */
private fun positionColor(position: String): Color = when (position) {
    "GK" -> Color(0xFFE65100)
    "CB", "LB", "RB" -> Color(0xFF1565C0)
    "DM", "CM", "AM" -> Color(0xFF2E7D32)
    "LW", "RW", "ST", "CF" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

/** 身价格式化 */
private fun formatValue(value: Int): String = when {
    value >= 100_000_000 -> "%.2f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.0f万".format(value / 10_000.0)
    else -> "$value"
}
