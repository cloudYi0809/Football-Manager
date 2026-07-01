package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.greendynasty.football.ui.tactics.model.FitLevel
import com.greendynasty.football.ui.tactics.model.PlayerSlot

/**
 * 球员位置节点（球场上的圆圈，V0.1 03 §3 战术页）。
 *
 * 显示球员姓名 + 适配度评分，颜色随适配度等级变化。
 * 可拖拽换位（拖拽交互由 [PitchCanvas] 的 pointerInput 统一处理）。
 *
 * @param slot 球员槽位
 * @param playerName 球员姓名（可空，空槽位显示位置缩写）
 * @param isHighlighted 是否高亮（如拖拽中）
 * @param modifier 修饰符
 */
@Composable
fun PlayerPositionNode(
    slot: PlayerSlot,
    playerName: String?,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val fitLevel = slot.fitLevel
    val (bgColor, borderColor) = when (fitLevel) {
        FitLevel.PERFECT -> Color(0xFF4CAF50) to Color(0xFFFFD700)      // 绿底金边
        FitLevel.GOOD -> Color(0xFF66BB6A) to Color.White                // 浅绿
        FitLevel.FAIR -> Color(0xFFFFA726) to Color.White                // 橙色
        FitLevel.POOR -> Color(0xFFEF5350) to Color.White                // 红色
    }

    val displayBg = if (slot.playerId == null) {
        Color(0x66000000) // 空槽位半透明黑
    } else {
        bgColor
    }

    val borderModifier = if (isHighlighted || fitLevel == FitLevel.PERFECT) {
        Modifier.border(2.dp, borderColor, CircleShape)
    } else {
        Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(displayBg)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        if (slot.playerId != null && playerName != null) {
            Text(
                text = playerName.take(MAX_NAME_LEN),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        } else {
            // 空槽位显示位置缩写
            Text(
                text = slot.position.name,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }

    // 适配度评分显示在节点下方（仅已分配球员时）
    if (slot.playerId != null && slot.positionFitScore > 0) {
        Text(
            text = "${slot.positionFitScore}",
            color = scoreColor(fitLevel),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 44.dp)
        )
    }
}

/** 适配度评分颜色 */
private fun scoreColor(level: FitLevel): Color = when (level) {
    FitLevel.PERFECT -> Color(0xFFFFD700)
    FitLevel.GOOD -> Color(0xFF66BB6A)
    FitLevel.FAIR -> Color(0xFFFFA726)
    FitLevel.POOR -> Color(0xFFEF5350)
}

private const val MAX_NAME_LEN = 4
