package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.greendynasty.football.ui.tactics.model.FitLevel
import com.greendynasty.football.ui.tactics.model.FormationDefinition
import com.greendynasty.football.ui.tactics.model.PlayerSlot
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 2D 球场 Canvas（V0.1 03 §3 战术页阵型区核心组件）。
 *
 * 绘制：
 * - 球场边界 / 中线 / 中圈 / 禁区 / 小禁区 / 点球点 / 球门
 * - 11 个球员位置圆圈（颜色随适配度变化）
 * - 球员姓名 + 适配度评分
 *
 * 交互：
 * - 长按拖拽球员换位（detectDragGestures）
 * - 拖拽至目标位置释放，自动与最近槽位交换
 *
 * 坐标体系：FormationPosition.x/y 为 0-1 归一化坐标，
 * y=0 为己方底线（渲染在屏幕底部），y=1 为对方底线（屏幕顶部）。
 *
 * @param formation 阵型定义（含 11 个位置坐标）
 * @param starting11 首发 11 人槽位
 * @param playerNames 球员 ID → 姓名映射
 * @param onPlayerDragged 拖拽换位回调（fromSlotId, toSlotId）
 * @param onSlotClicked 槽位点击回调
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun PitchCanvas(
    formation: FormationDefinition,
    starting11: List<PlayerSlot>,
    playerNames: Map<Int, String>,
    onPlayerDragged: (fromSlotId: Int, toSlotId: Int) -> Unit,
    onSlotClicked: (slotId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var draggedSlotId by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PITCH_RATIO)
            .pointerInput(formation) {
                detectDragGestures(
                    onDragStart = { touchOffset ->
                        // 找到触摸点对应的槽位
                        val slotId = findSlotAt(touchOffset, formation, size.toSize())
                        if (slotId != null) {
                            draggedSlotId = slotId
                            // 初始化拖拽位置为该槽位的基准位置
                            val slot = formation.positions.first { it.slotId == slotId }
                            dragPosition = slotToPixel(slot, size.toSize())
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        if (draggedSlotId != null) {
                            dragPosition = Offset(
                                dragPosition.x + delta.x,
                                dragPosition.y + delta.y
                            )
                        }
                    },
                    onDragEnd = {
                        draggedSlotId?.let { from ->
                            val to = findNearestSlot(dragPosition, formation, size.toSize())
                            if (to != null && to != from) {
                                onPlayerDragged(from, to)
                            }
                        }
                        draggedSlotId = null
                        dragPosition = Offset.Zero
                    },
                    onDragCancel = {
                        draggedSlotId = null
                        dragPosition = Offset.Zero
                    }
                )
            }
    ) {
        // 1. 球场背景（绿色草地条纹）
        drawPitchBackground()

        // 2. 球场线条
        drawPitchLines()

        // 3. 球员位置圆圈
        formation.positions.forEach { pos ->
            val slot = starting11.find { it.slotId == pos.slotId } ?: return@forEach
            val base = slotToPixel(pos, size)
            val center = if (draggedSlotId == pos.slotId) dragPosition else base
            val playerName = slot.playerId?.let { playerNames[it] }
            drawPlayerNode(center, slot, playerName, textMeasurer, draggedSlotId == pos.slotId)
        }
    }
}

// ==================== 球场绘制 ====================

/** 绘制球场背景：绿色 + 横向条纹 */
private fun DrawScope.drawPitchBackground() {
    val stripeCount = 7
    val stripeHeight = size.height / stripeCount
    repeat(stripeCount) { i ->
        val color = if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF256628)
        drawRect(
            color = color,
            topLeft = Offset(0f, i * stripeHeight),
            size = Size(size.width, stripeHeight)
        )
    }
}

/** 绘制球场线条：边界 / 中线 / 中圈 / 禁区 / 小禁区 / 点球点 / 球门 */
private fun DrawScope.drawPitchLines() {
    val w = size.width
    val h = size.height
    val lineColor = Color.White.copy(alpha = 0.85f)
    val lineStroke = Stroke(width = 2f)

    // 边界
    val margin = 6f
    drawRect(
        color = lineColor,
        topLeft = Offset(margin, margin),
        size = Size(w - 2 * margin, h - 2 * margin),
        style = lineStroke
    )

    // 中线（水平）
    val midY = h / 2f
    drawLine(lineColor, Offset(margin, midY), Offset(w - margin, midY), strokeWidth = 2f)

    // 中圈
    val centerCircleRadius = w * 0.12f
    drawCircle(lineColor, centerCircleRadius, Offset(w / 2f, midY), style = lineStroke)
    // 中点
    drawCircle(lineColor, 3f, Offset(w / 2f, midY))

    // 禁区尺寸（相对球场）
    val penBoxWidth = w * 0.5f
    val penBoxHeight = h * 0.18f
    val penBoxX = (w - penBoxWidth) / 2f

    // 上方禁区（对方）
    drawRect(
        color = lineColor,
        topLeft = Offset(penBoxX, margin),
        size = Size(penBoxWidth, penBoxHeight),
        style = lineStroke
    )
    // 下方禁区（己方）
    drawRect(
        color = lineColor,
        topLeft = Offset(penBoxX, h - margin - penBoxHeight),
        size = Size(penBoxWidth, penBoxHeight),
        style = lineStroke
    )

    // 小禁区
    val goalBoxWidth = w * 0.24f
    val goalBoxHeight = h * 0.08f
    val goalBoxX = (w - goalBoxWidth) / 2f
    drawRect(
        color = lineColor,
        topLeft = Offset(goalBoxX, margin),
        size = Size(goalBoxWidth, goalBoxHeight),
        style = lineStroke
    )
    drawRect(
        color = lineColor,
        topLeft = Offset(goalBoxX, h - margin - goalBoxHeight),
        size = Size(goalBoxWidth, goalBoxHeight),
        style = lineStroke
    )

    // 点球点
    val penSpotOffset = h * 0.11f
    drawCircle(lineColor, 2.5f, Offset(w / 2f, margin + penSpotOffset))
    drawCircle(lineColor, 2.5f, Offset(w / 2f, h - margin - penSpotOffset))

    // 禁区弧（半圆，简化为圆弧）
    val arcRadius = w * 0.08f
    drawArc(
        color = lineColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w / 2f - arcRadius, margin + penBoxHeight - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = lineStroke
    )
    drawArc(
        color = lineColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w / 2f - arcRadius, h - margin - penBoxHeight - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = lineStroke
    )

    // 球门
    val goalWidth = w * 0.16f
    val goalX = (w - goalWidth) / 2f
    drawLine(lineColor, Offset(goalX, margin), Offset(goalX + goalWidth, margin), strokeWidth = 3f)
    drawLine(
        lineColor,
        Offset(goalX, h - margin),
        Offset(goalX + goalWidth, h - margin),
        strokeWidth = 3f
    )
}

// ==================== 球员节点绘制 ====================

/**
 * 在 Canvas 上绘制单个球员节点（圆圈 + 姓名 + 评分）。
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPlayerNode(
    center: Offset,
    slot: PlayerSlot,
    playerName: String?,
    textMeasurer: TextMeasurer,
    isHighlighted: Boolean
) {
    val radius = NODE_RADIUS_PX
    val (fillColor, borderColor) = nodeColors(slot, isHighlighted)

    // 圆圈填充
    drawCircle(fillColor, radius, center)
    // 边框
    drawCircle(borderColor, radius, center, style = Stroke(width = if (isHighlighted) 3f else 2f))

    // 文本：已分配球员显示姓名，空槽位显示位置缩写
    val displayText = if (slot.playerId != null && playerName != null) {
        playerName.take(MAX_NAME_CHARS)
    } else {
        slot.position.name
    }

    val textColor = if (slot.playerId != null) Color.White else Color.White.copy(alpha = 0.7f)
    val textLayout = textMeasurer.measure(
        text = displayText,
        style = TextStyle(color = textColor, fontSize = 9.sp)
    )
    drawText(
        textLayout,
        topLeft = Offset(
            center.x - textLayout.size.width / 2f,
            center.y - textLayout.size.height / 2f
        )
    )

    // 适配度评分（已分配球员且适配度 > 0 时显示在圆圈下方）
    if (slot.playerId != null && slot.positionFitScore > 0) {
        val scoreText = "${slot.positionFitScore}"
        val scoreColor = scoreColor(slot.fitLevel)
        val scoreLayout = textMeasurer.measure(
            text = scoreText,
            style = TextStyle(color = scoreColor, fontSize = 9.sp)
        )
        drawText(
            scoreLayout,
            topLeft = Offset(
                center.x - scoreLayout.size.width / 2f,
                center.y + radius + 2f
            )
        )
    }
}

/** 节点配色：按适配度等级 + 是否高亮 */
private fun nodeColors(slot: PlayerSlot, isHighlighted: Boolean): Pair<Color, Color> {
    if (slot.playerId == null) {
        return Color(0x66000000) to Color.White.copy(alpha = 0.5f)
    }
    val fill = when (slot.fitLevel) {
        FitLevel.PERFECT -> Color(0xFF4CAF50)
        FitLevel.GOOD -> Color(0xFF66BB6A)
        FitLevel.FAIR -> Color(0xFFFFA726)
        FitLevel.POOR -> Color(0xFFEF5350)
    }
    val border = when {
        isHighlighted -> Color(0xFFFFD700)
        slot.fitLevel == FitLevel.PERFECT -> Color(0xFFFFD700)
        else -> Color.White
    }
    return fill to border
}

/** 适配度评分颜色 */
private fun scoreColor(level: FitLevel): Color = when (level) {
    FitLevel.PERFECT -> Color(0xFFFFD700)
    FitLevel.GOOD -> Color(0xFF66BB6A)
    FitLevel.FAIR -> Color(0xFFFFA726)
    FitLevel.POOR -> Color(0xFFEF5350)
}

// ==================== 拖拽辅助 ====================

/**
 * 阵型位置 → 像素坐标。
 * y=0（己方底线）渲染在屏幕底部，y=1（对方底线）渲染在屏幕顶部。
 */
private fun slotToPixel(
    pos: com.greendynasty.football.ui.tactics.model.FormationPosition,
    size: Size
): Offset = Offset(
    x = pos.x * size.width,
    y = (1f - pos.y) * size.height
)

/** 查找触摸点所在的槽位（点击半径内） */
private fun findSlotAt(
    touch: Offset,
    formation: FormationDefinition,
    size: Size
): Int? {
    formation.positions.forEach { pos ->
        val center = slotToPixel(pos, size)
        if (hypot(touch.x - center.x, touch.y - center.y) <= TOUCH_RADIUS_PX) {
            return pos.slotId
        }
    }
    return null
}

/** 查找距离拖拽落点最近的槽位 */
private fun findNearestSlot(
    point: Offset,
    formation: FormationDefinition,
    size: Size
): Int? {
    var nearestSlot: Int? = null
    var minDist = Float.MAX_VALUE
    formation.positions.forEach { pos ->
        val center = slotToPixel(pos, size)
        val dist = hypot(point.x - center.x, point.y - center.y)
        if (dist < minDist) {
            minDist = dist
            nearestSlot = pos.slotId
        }
    }
    return nearestSlot
}

// ==================== 常量 ====================

private const val PITCH_RATIO = 0.62f        // 球场宽高比（height/width）
private const val NODE_RADIUS_PX = 22f       // 球员圆圈半径（像素）
private const val TOUCH_RADIUS_PX = 36f      // 触摸判定半径（像素）
private const val MAX_NAME_CHARS = 4         // 姓名最大显示字符数
