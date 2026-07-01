package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greendynasty.football.ui.squad.data.GrowthCurvePoint

/**
 * 成长曲线 Canvas 绘制。
 *
 * 基于 [GrowthCurvePoint] 多赛季数据绘制 CA / PA 双线曲线。
 * - X 轴：赛季（按数据点顺序）
 * - Y 轴：CA / PA 值（0-200）
 * - CA 实线（主色）
 * - PA 虚线（次要色）
 *
 * @param points 多赛季数据点（按赛季升序）
 */
@Composable
fun GrowthCurveChart(
    points: List<GrowthCurvePoint>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val caColor = Color(0xFF1B5E20)
    val paColor = Color(0xFFFFD700)
    val axisColor = Color(0xFF9E9E9E)
    val gridColor = Color(0xFFE0E0E0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        if (points.isEmpty()) {
            val layout = textMeasurer.measure(
                AnnotatedString("暂无成长数据"),
                TextStyle(color = axisColor, fontSize = 14.sp)
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    (size.width - layout.size.width) / 2f,
                    (size.height - layout.size.height) / 2f
                )
            )
            return@Canvas
        }

        val paddingLeft = 40f
        val paddingRight = 16f
        val paddingTop = 16f
        val paddingBottom = 28f
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        val maxY = 200f

        // 网格 + Y 轴刻度（每 50 一格）
        val ySteps = listOf(0, 50, 100, 150, 200)
        ySteps.forEach { y ->
            val yPos = paddingTop + chartHeight * (1 - y / maxY)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, yPos),
                end = Offset(size.width - paddingRight, yPos),
                strokeWidth = 1f
            )
            val label = textMeasurer.measure(
                AnnotatedString("$y"),
                TextStyle(color = axisColor, fontSize = 9.sp)
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    paddingLeft - label.size.width - 4f,
                    yPos - label.size.height / 2f
                )
            )
        }

        // X 轴标签
        val xStep = if (points.size > 1) chartWidth / (points.size - 1) else 0f
        points.forEachIndexed { index, point ->
            val xPos = paddingLeft + index * xStep
            val label = textMeasurer.measure(
                AnnotatedString(point.seasonLabel),
                TextStyle(color = axisColor, fontSize = 9.sp)
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    xPos - label.size.width / 2f,
                    size.height - paddingBottom + 6f
                )
            )
        }

        // CA 实线
        val caPath = Path()
        points.forEachIndexed { index, point ->
            val xPos = paddingLeft + index * xStep
            val yPos = paddingTop + chartHeight * (1 - point.ca / maxY)
            if (index == 0) caPath.moveTo(xPos, yPos) else caPath.lineTo(xPos, yPos)
        }
        drawPath(path = caPath, color = caColor, style = Stroke(width = 4f))

        // PA 虚线
        val paPath = Path()
        points.forEachIndexed { index, point ->
            val xPos = paddingLeft + index * xStep
            val yPos = paddingTop + chartHeight * (1 - point.pa / maxY)
            if (index == 0) paPath.moveTo(xPos, yPos) else paPath.lineTo(xPos, yPos)
        }
        drawPath(
            path = paPath,
            color = paColor,
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        )

        // 数据点圆点
        points.forEachIndexed { index, point ->
            val xPos = paddingLeft + index * xStep
            val caY = paddingTop + chartHeight * (1 - point.ca / maxY)
            val paY = paddingTop + chartHeight * (1 - point.pa / maxY)
            drawCircle(color = caColor, radius = 4f, center = Offset(xPos, caY))
            drawCircle(color = paColor, radius = 3f, center = Offset(xPos, paY))
        }
    }
}
