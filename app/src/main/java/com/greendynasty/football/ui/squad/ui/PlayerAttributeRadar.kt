package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greendynasty.football.ui.squad.data.RadarValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 属性雷达图 Canvas 绘制（静态）。
 *
 * 6 维度：进攻 / 中场 / 防守 / 身体 / 心理 / 门将。
 * 绘制 4 层背景网格 + 数据多边形 + 维度标签。
 *
 * @param values 6 维度值（0-100）
 */
@Composable
fun PlayerAttributeRadar(
    values: List<RadarValue>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = Color(0xFFE0E0E0)
    val axisColor = Color(0xFF9E9E9E)
    val fillColor = Color(0xFF1B5E20).copy(alpha = 0.25f)
    val strokeColor = Color(0xFF1B5E20)
    val labelColor = Color(0xFF424242)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) * 0.7f
        val dimensions = values.ifEmpty { emptyRadarValues() }
        val n = dimensions.size
        if (n < 3) return@Canvas

        val angleStep = (2 * PI / n).toFloat()
        val startAngle = (-PI / 2).toFloat()

        // 4 层背景网格
        val layers = 4
        for (layer in 1..layers) {
            val r = radius * layer / layers
            val path = Path()
            for (i in 0 until n) {
                val a = startAngle + i * angleStep
                val x = cx + r * cos(a)
                val y = cy + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path = path, color = gridColor, style = Stroke(width = 1f))
        }

        // 轴线
        for (i in 0 until n) {
            val a = startAngle + i * angleStep
            drawLine(
                color = gridColor,
                start = Offset(cx, cy),
                end = Offset(cx + radius * cos(a), cy + radius * sin(a)),
                strokeWidth = 1f
            )
        }

        // 数据多边形
        val dataPath = Path()
        dimensions.forEachIndexed { i, v ->
            val a = startAngle + i * angleStep
            val r = radius * (v.value.coerceIn(0, 100) / 100f)
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        drawPath(path = dataPath, color = fillColor)
        drawPath(path = dataPath, color = strokeColor, style = Stroke(width = 2f))

        // 数据点
        dimensions.forEachIndexed { i, v ->
            val a = startAngle + i * angleStep
            val r = radius * (v.value.coerceIn(0, 100) / 100f)
            drawCircle(
                color = strokeColor,
                radius = 3f,
                center = Offset(cx + r * cos(a), cy + r * sin(a))
            )
        }

        // 维度标签
        dimensions.forEachIndexed { i, v ->
            val a = startAngle + i * angleStep
            val labelR = radius + 18f
            val lx = cx + labelR * cos(a)
            val ly = cy + labelR * sin(a)
            val label = textMeasurer.measure(
                AnnotatedString("${v.label} ${v.value}"),
                TextStyle(color = labelColor, fontSize = 10.sp)
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(lx - label.size.width / 2f, ly - label.size.height / 2f)
            )
        }
    }
}

/** 空维度兜底 */
private fun emptyRadarValues(): List<RadarValue> = listOf(
    RadarValue("进攻", 0),
    RadarValue("中场", 0),
    RadarValue("防守", 0),
    RadarValue("身体", 0),
    RadarValue("心理", 0),
    RadarValue("门将", 0)
)
