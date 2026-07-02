package com.greendynasty.football.transfer.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greendynasty.football.transfer.model.CompareResult
import com.greendynasty.football.transfer.model.PlayerCompareData
import com.greendynasty.football.transfer.model.RadarDimension
import com.greendynasty.football.transfer.ui.state.CompareUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 球员对比色板（最多 3 人，颜色区分） */
private val COMPARE_COLORS = listOf(
    Color(0xFF1565C0),  // 蓝
    Color(0xFFC62828),  // 红
    Color(0xFF2E7D32)   // 绿
)

/** 属性分组（用于属性表格展示） */
private data class AttributeGroup(val title: String, val keys: List<Pair<String, String>>)

private val ATTRIBUTE_GROUPS = listOf(
    AttributeGroup("进攻", listOf(
        "shooting" to "射门",
        "finishing" to "终结",
        "long_shots" to "远射",
        "crossing" to "传中"
    )),
    AttributeGroup("中场", listOf(
        "passing" to "传球",
        "technique" to "技术",
        "first_touch" to "停球",
        "dribbling" to "盘带"
    )),
    AttributeGroup("防守", listOf(
        "defending" to "防守",
        "tackling" to "抢断",
        "marking" to "盯人",
        "positioning" to "跑位"
    )),
    AttributeGroup("身体", listOf(
        "pace" to "速度",
        "acceleration" to "加速",
        "strength" to "力量",
        "stamina" to "体能"
    )),
    AttributeGroup("心理", listOf(
        "vision" to "视野",
        "decision" to "决策",
        "composure" to "冷静",
        "leadership" to "领导力"
    )),
    AttributeGroup("门将", listOf(
        "gk_diving" to "扑救",
        "gk_reflexes" to "反应",
        "gk_handling" to "持球",
        "gk_positioning" to "站位"
    ))
)

/**
 * 球员对比视图（V0.1 03 阵容页：球员对比）。
 *
 * 包含：
 * - 多球员雷达图（2-3 人叠加显示）
 * - 基础信息对比表（年龄/位置/CA/PA/身价/工资/合同）
 * - 属性分组对比表（按 6 大类展示，bestInCategory 高亮）
 *
 * @param state 对比页 UI 状态
 * @param compareSelection 当前选中的球员 ID 集合
 * @param onPerformCompare 触发对比回调（选择 ≥2 人时可用）
 * @param onClearSelection 清空选择回调
 */
@Composable
fun PlayerCompareView(
    state: CompareUiState,
    compareSelection: Set<Int>,
    onPerformCompare: () -> Unit,
    onClearSelection: () -> Unit
) {
    when (state) {
        is CompareUiState.Idle -> CompareIdleView(
            compareSelection = compareSelection,
            onPerformCompare = onPerformCompare,
            onClearSelection = onClearSelection
        )
        is CompareUiState.Loading -> LoadingView()
        is CompareUiState.Error -> ErrorView(state.message)
        is CompareUiState.Normal -> CompareContent(
            result = state.result,
            onClearSelection = onClearSelection
        )
    }
}

/** Idle 状态：提示用户选择球员，并提供开始对比 / 清空按钮 */
@Composable
private fun CompareIdleView(
    compareSelection: Set<Int>,
    onPerformCompare: () -> Unit,
    onClearSelection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "在「搜索」Tab 中点击对比图标，选择 2-3 名球员",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "已选 ${compareSelection.size}/3 人",
            style = MaterialTheme.typography.labelMedium,
            color = if (compareSelection.size >= 2) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        )
        if (compareSelection.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPerformCompare) { Text("开始对比") }
                androidx.compose.material3.OutlinedButton(onClick = onClearSelection) {
                    Text("清空")
                }
            }
        } else if (compareSelection.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = onClearSelection) {
                Text("清空已选")
            }
        }
    }
}

/** 对比内容（雷达图 + 基础信息表 + 属性表） */
@Composable
private fun CompareContent(
    result: CompareResult,
    onClearSelection: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 4.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 雷达图
        item {
            CompareRadarCard(result = result)
        }

        // 2. 基础信息对比
        item {
            BasicInfoCard(result = result)
        }

        // 3. 属性分组对比
        items(ATTRIBUTE_GROUPS) { group ->
            AttributeGroupCard(
                group = group,
                result = result
            )
        }

        // 4. 清空按钮
        item {
            Button(
                onClick = onClearSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清空对比")
            }
        }
    }
}

/** 多球员雷达图卡片 */
@Composable
private fun CompareRadarCard(result: CompareResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("属性雷达图", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            MultiPlayerRadar(
                result = result,
                modifier = Modifier.fillMaxWidth().height(280.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 图例
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                result.players.forEachIndexed { index, player ->
                    val color = COMPARE_COLORS.getOrElse(index) { Color.Gray }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .padding(end = 2.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = color)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = player.playerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** 多球员雷达图 Canvas */
@Composable
private fun MultiPlayerRadar(
    result: CompareResult,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = Color(0xFFE0E0E0)
    val labelColor = Color(0xFF424242)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) * 0.7f

        val dimensions = result.radarValues.values.firstOrNull() ?: emptyList()
        if (dimensions.isEmpty()) return@Canvas
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

        // 各球员数据多边形
        result.players.forEachIndexed { index, player ->
            val color = COMPARE_COLORS.getOrElse(index) { Color.Gray }
            val radar = result.radarValues[player.playerId] ?: return@forEachIndexed
            val dataPath = Path()
            radar.forEachIndexed { i, v ->
                val a = startAngle + i * angleStep
                val r = radius * (v.value.coerceIn(0, 100) / 100f)
                val x = cx + r * cos(a)
                val y = cy + r * sin(a)
                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            }
            dataPath.close()
            drawPath(path = dataPath, color = color.copy(alpha = 0.15f))
            drawPath(path = dataPath, color = color, style = Stroke(width = 2f))

            // 数据点
            radar.forEachIndexed { i, v ->
                val a = startAngle + i * angleStep
                val r = radius * (v.value.coerceIn(0, 100) / 100f)
                drawCircle(
                    color = color,
                    radius = 3f,
                    center = Offset(cx + r * cos(a), cy + r * sin(a))
                )
            }
        }

        // 维度标签
        dimensions.forEachIndexed { i, v ->
            val a = startAngle + i * angleStep
            val labelR = radius + 18f
            val lx = cx + labelR * cos(a)
            val ly = cy + labelR * sin(a)
            val label = textMeasurer.measure(
                AnnotatedString(v.label),
                TextStyle(color = labelColor, fontSize = 10.sp)
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(lx - label.size.width / 2f, ly - label.size.height / 2f)
            )
        }
    }
}

/** 基础信息对比表 */
@Composable
private fun BasicInfoCard(result: CompareResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("基础信息", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            BasicInfoRow(label = "姓名", values = result.players.map { it.playerName })
            BasicInfoRow(label = "年龄", values = result.players.map { "${it.age}" })
            BasicInfoRow(label = "位置", values = result.players.map { it.position })
            BasicInfoRow(label = "CA", values = result.players.map { "${it.currentCa}" })
            BasicInfoRow(label = "PA", values = result.players.map { "${it.potentialPa}" })
            BasicInfoRow(label = "身价", values = result.players.map { formatValue(it.marketValue) })
            BasicInfoRow(label = "周薪", values = result.players.map { formatValue(it.wage) })
            BasicInfoRow(label = "合同", values = result.players.map { it.contractUntil ?: "-" })
        }
    }
}

/** 基础信息单行 */
@Composable
private fun BasicInfoRow(label: String, values: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(0.25f)
        )
        values.forEach { v ->
            Text(
                text = v,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.25f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** 属性分组对比卡片 */
@Composable
private fun AttributeGroupCard(
    group: AttributeGroup,
    result: CompareResult
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(group.title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // 表头
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "属性",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(0.25f)
                )
                result.players.forEach { player ->
                    Text(
                        text = player.playerName.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.25f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // 各属性行
            group.keys.forEach { (key, label) ->
                AttributeRow(
                    label = label,
                    key = key,
                    result = result
                )
            }
        }
    }
}

/** 单属性行（bestInCategory 高亮） */
@Composable
private fun AttributeRow(
    label: String,
    key: String,
    result: CompareResult
) {
    val bestPlayerId = result.bestInCategory[key]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.25f)
        )
        result.players.forEachIndexed { index, player ->
            val value = player.attributes[key] ?: 0
            val isBest = bestPlayerId == player.playerId
            val color = if (isBest) COMPARE_COLORS.getOrElse(index) { Color.Gray }
            else MaterialTheme.colorScheme.onSurface
            val weight = if (isBest) FontWeight.Bold else FontWeight.Normal
            Text(
                text = "$value",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = weight,
                modifier = Modifier.weight(0.25f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** 身价格式化 */
private fun formatValue(value: Int): String = when {
    value >= 100_000_000 -> "%.2f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.0f万".format(value / 10_000.0)
    else -> "$value"
}
