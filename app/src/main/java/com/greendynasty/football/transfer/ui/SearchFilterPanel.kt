@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greendynasty.football.transfer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greendynasty.football.transfer.model.SortOrder
import com.greendynasty.football.transfer.model.TransferSearchFilter
import com.greendynasty.football.transfer.model.TransferSortBy

/** 全部可选位置 */
private val ALL_POSITIONS = listOf("GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST", "CF")

/**
 * 搜索筛选面板（V0.1 09 §二.1，14 项筛选条件）。
 *
 * 提供：
 * - 姓名模糊输入
 * - 位置多选（FilterChip）
 * - 年龄范围滑块
 * - CA 范围滑块
 * - PA 范围滑块
 * - 最大身价滑块
 * - 排序字段 + 方向
 * - 重置 / 应用按钮
 *
 * @param filter 当前筛选条件
 * @param onApply 应用筛选回调
 * @param onReset 重置筛选回调
 */
@Composable
fun SearchFilterPanel(
    filter: TransferSearchFilter,
    onApply: (TransferSearchFilter) -> Unit,
    onReset: () -> Unit
) {
    // 局部编辑状态，应用时才回调
    var name by remember(filter) { mutableStateOf(filter.name ?: "") }
    var positions by remember(filter) { mutableStateOf(filter.positions.toSet()) }
    var ageMin by remember(filter) { mutableStateOf(filter.ageRange?.first?.toFloat() ?: 16f) }
    var ageMax by remember(filter) { mutableStateOf(filter.ageRange?.last?.toFloat() ?: 40f) }
    var caMin by remember(filter) { mutableStateOf(filter.caRange?.first?.toFloat() ?: 0f) }
    var caMax by remember(filter) { mutableStateOf(filter.caRange?.last?.toFloat() ?: 200f) }
    var paMin by remember(filter) { mutableStateOf(filter.paRange?.first?.toFloat() ?: 0f) }
    var paMax by remember(filter) { mutableStateOf(filter.paRange?.last?.toFloat() ?: 200f) }
    var maxValue by remember(filter) {
        mutableStateOf(filter.maxMarketValue?.toFloat() ?: 100_000_000f)
    }
    var sortBy by remember(filter) { mutableStateOf(filter.sortBy) }
    var sortOrder by remember(filter) { mutableStateOf(filter.sortOrder) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("筛选条件", style = MaterialTheme.typography.titleSmall)

            // 1. 姓名模糊
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("球员姓名（模糊）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 2. 位置多选
            Text("位置", style = MaterialTheme.typography.labelMedium)
            PositionChips(
                selected = positions,
                onToggle = { pos ->
                    positions = if (pos in positions) positions - pos else positions + pos
                }
            )

            // 3. 年龄范围
            RangeSliderRow(
                label = "年龄",
                minValue = ageMin,
                maxValue = ageMax,
                valueRange = 16f..45f,
                steps = 28,
                valueFormatter = { it.toInt().toString() },
                onChange = { min, max -> ageMin = min; ageMax = max }
            )

            // 4. CA 范围
            RangeSliderRow(
                label = "当前能力 CA",
                minValue = caMin,
                maxValue = caMax,
                valueRange = 0f..200f,
                steps = 39,
                valueFormatter = { it.toInt().toString() },
                onChange = { min, max -> caMin = min; caMax = max }
            )

            // 5. PA 范围
            RangeSliderRow(
                label = "潜力 PA",
                minValue = paMin,
                maxValue = paMax,
                valueRange = 0f..200f,
                steps = 39,
                valueFormatter = { it.toInt().toString() },
                onChange = { min, max -> paMin = min; paMax = max }
            )

            // 6. 最大身价
            Text("最大身价：${formatValue(maxValue.toInt())}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = maxValue,
                onValueChange = { maxValue = it },
                valueRange = 1_000_000f..200_000_000f,
                steps = 199
            )

            // 7. 排序
            Text("排序", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransferSortBy.values().forEach { sb ->
                    FilterChip(
                        selected = sortBy == sb,
                        onClick = { sortBy = sb },
                        label = { Text(sb.label) }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = sortOrder == SortOrder.DESC,
                    onClick = { sortOrder = SortOrder.DESC },
                    label = { Text("降序") }
                )
                FilterChip(
                    selected = sortOrder == SortOrder.ASC,
                    onClick = { sortOrder = SortOrder.ASC },
                    label = { Text("升序") }
                )
            }

            // 8. 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReset) { Text("重置") }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = {
                    val newFilter = TransferSearchFilter(
                        name = name.ifBlank { null },
                        positions = positions.toList(),
                        ageRange = ageMin.toInt()..ageMax.toInt(),
                        caRange = caMin.toInt()..caMax.toInt(),
                        paRange = paMin.toInt()..paMax.toInt(),
                        maxMarketValue = maxValue.toInt(),
                        sortBy = sortBy,
                        sortOrder = sortOrder
                    )
                    onApply(newFilter)
                }) { Text("搜索") }
            }
        }
    }
}

/** 位置多选 Chip 行 */
@Composable
private fun PositionChips(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ALL_POSITIONS.take(6).forEach { pos ->
            FilterChip(
                selected = pos in selected,
                onClick = { onToggle(pos) },
                label = { Text(pos) }
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ALL_POSITIONS.drop(6).forEach { pos ->
            FilterChip(
                selected = pos in selected,
                onClick = { onToggle(pos) },
                label = { Text(pos) }
            )
        }
    }
}

/** 范围滑块行（双滑块） */
@Composable
private fun RangeSliderRow(
    label: String,
    minValue: Float,
    maxValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueFormatter: (Float) -> String,
    onChange: (Float, Float) -> Unit
) {
    Column {
        Text("$label：${valueFormatter(minValue)} - ${valueFormatter(maxValue)}", style = MaterialTheme.typography.labelMedium)
        androidx.compose.material3.RangeSlider(
            value = minValue..maxValue,
            onValueChange = { range -> onChange(range.start, range.endInclusive) },
            valueRange = valueRange,
            steps = steps
        )
    }
}

/** 身价格式化 */
private fun formatValue(value: Int): String = when {
    value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000 -> "%.0f万".format(value / 10_000.0)
    else -> "$value"
}
