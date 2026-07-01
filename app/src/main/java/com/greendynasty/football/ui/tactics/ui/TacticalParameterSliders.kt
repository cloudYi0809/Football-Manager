package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greendynasty.football.match.api.Mentality
import com.greendynasty.football.match.api.PassStyle
import com.greendynasty.football.ui.tactics.model.AttackingFocus
import com.greendynasty.football.ui.tactics.model.DefensiveFocus
import com.greendynasty.football.ui.tactics.model.TacticalParameters

/**
 * 战术参数滑块区（V0.1 03 §3 战术页参数区）。
 *
 * 包含：
 * - 节奏 / 压迫 / 防线 滑块（1-10）
 * - 传球风格 / 心态 / 进攻方向 / 防守方式 选择
 * - 实时战术熟练度提示
 *
 * @param parameters 当前战术参数
 * @param proficiency 战术熟练度 0-100
 * @param onUpdate 参数更新回调
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TacticalParameterSliders(
    parameters: TacticalParameters,
    proficiency: Double,
    onUpdate: (TacticalParameters) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "战术参数",
                style = MaterialTheme.typography.titleSmall
            )

            // 数值滑块
            ParameterSlider(
                label = "压迫强度",
                value = parameters.pressingIntensity,
                valueLabel = "${parameters.pressingIntensity}/10",
                onValueChange = { onUpdate(parameters.copy(pressingIntensity = it.toInt())) }
            )
            ParameterSlider(
                label = "防线高度",
                value = parameters.defensiveLine,
                valueLabel = "${parameters.defensiveLine}/10（${if (parameters.defensiveLine >= 7) "高位" else if (parameters.defensiveLine <= 3) "低位" else "中位"}）",
                onValueChange = { onUpdate(parameters.copy(defensiveLine = it.toInt())) }
            )
            ParameterSlider(
                label = "比赛节奏",
                value = parameters.tempo,
                valueLabel = "${parameters.tempo}/10",
                onValueChange = { onUpdate(parameters.copy(tempo = it.toInt())) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 传球风格
            Text("传球风格", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PassStyle.entries.forEach { style ->
                    FilterChip(
                        selected = parameters.passStyle == style,
                        onClick = { onUpdate(parameters.copy(passStyle = style)) },
                        label = { Text(passStyleLabel(style), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // 心态
            Text("心态", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mentality.entries.forEach { mentality ->
                    FilterChip(
                        selected = parameters.mentality == mentality,
                        onClick = { onUpdate(parameters.copy(mentality = mentality)) },
                        label = { Text(mentalityLabel(mentality), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // 进攻方向
            Text("进攻方向", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttackingFocus.entries.forEach { focus ->
                    FilterChip(
                        selected = parameters.attackingFocus == focus,
                        onClick = { onUpdate(parameters.copy(attackingFocus = focus)) },
                        label = { Text(focus.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // 防守方式
            Text("防守方式", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DefensiveFocus.entries.forEach { focus ->
                    FilterChip(
                        selected = parameters.defensiveFocus == focus,
                        onClick = { onUpdate(parameters.copy(defensiveFocus = focus)) },
                        label = { Text(focus.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 实时战术熟练度提示
            ProficiencyHintRow(parameters, proficiency)
        }
    }
}

/** 单个数值参数滑块 */
@Composable
private fun ParameterSlider(
    label: String,
    value: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = 1f..10f,
            steps = 8
        )
    }
}

/** 实时战术熟练度提示行 */
@Composable
private fun ProficiencyHintRow(parameters: TacticalParameters, proficiency: Double) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (parameters.isAggressive)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("战术熟练度", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${proficiency.toInt()}/100",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = parameters.proficiencyHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 传球风格展示名 */
private fun passStyleLabel(style: PassStyle): String = when (style) {
    PassStyle.SHORT -> "短传"
    PassStyle.DIRECT -> "直传"
    PassStyle.LONG -> "长传"
}

/** 心态展示名 */
private fun mentalityLabel(mentality: Mentality): String = when (mentality) {
    Mentality.ALL_ATTACK -> "全攻"
    Mentality.BALANCED -> "平衡"
    Mentality.ALL_DEFENSE -> "全守"
}
