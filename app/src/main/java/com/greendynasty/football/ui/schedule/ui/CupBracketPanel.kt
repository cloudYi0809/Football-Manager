package com.greendynasty.football.ui.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.schedule.model.CupStageUi
import com.greendynasty.football.ui.schedule.model.CupTieUi

/**
 * 杯赛对阵表面板
 *
 * V1 简化渲染：按阶段竖向排列（每阶段一组 ties），每个 tie 一张卡片。
 *
 * V2 可扩展为水平树形 bracket，使用 Canvas + 连线绘制。
 *
 * @param stages 各阶段对阵组（按 stageOrder 升序）
 * @param playerClubId 玩家俱乐部 ID（高亮其所在对阵）
 */
@Composable
fun CupBracketPanel(
    stages: List<CupStageUi>,
    playerClubId: Int,
    modifier: Modifier = Modifier
) {
    if (stages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "杯赛对阵尚未生成",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stages, key = { it.stage.raw }) { stage ->
            CupStageSection(stage = stage, playerClubId = playerClubId)
        }
    }
}

@Composable
private fun CupStageSection(stage: CupStageUi, playerClubId: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 阶段标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${stage.stage.displayName}（${stage.ties.size} 场）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 对阵卡片
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            stage.ties.forEach { tie ->
                CupTieCard(tie = tie, playerClubId = playerClubId)
            }
        }
    }
}

@Composable
private fun CupTieCard(tie: CupTieUi, playerClubId: Int) {
    val involvesPlayer = tie.homeClubId == playerClubId || tie.awayClubId == playerClubId
    val borderColor = if (involvesPlayer) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 主队
        TeamSlot(
            name = tie.homeClubName ?: "待定",
            isWinner = tie.winnerClubId == tie.homeClubId,
            isPlayerClub = tie.homeClubId == playerClubId,
            modifier = Modifier.weight(1f),
            align = TextAlign.End
        )
        // 比分
        Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
            val scoreText = if (tie.aggregateHomeScore != null && tie.aggregateAwayScore != null) {
                "${tie.aggregateHomeScore} - ${tie.aggregateAwayScore}"
            } else if (tie.isTwoLegged) {
                "两回合"
            } else {
                "VS"
            }
            Text(
                text = scoreText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        // 客队
        TeamSlot(
            name = tie.awayClubName ?: "待定",
            isWinner = tie.winnerClubId == tie.awayClubId,
            isPlayerClub = tie.awayClubId == playerClubId,
            modifier = Modifier.weight(1f),
            align = TextAlign.Start
        )
    }
}

@Composable
private fun TeamSlot(
    name: String,
    isWinner: Boolean,
    isPlayerClub: Boolean,
    modifier: Modifier = Modifier,
    align: TextAlign
) {
    val color = when {
        isWinner -> MaterialTheme.colorScheme.primary
        isPlayerClub -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = if (isWinner || isPlayerClub) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        modifier = modifier,
        maxLines = 1
    )
}

/** 空视图颜色占位（防止未启用主题时崩溃） */
private val FallbackColor = Color(0xFF888888)
