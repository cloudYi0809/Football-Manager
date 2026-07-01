package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.ui.tactics.model.TacticalStyleDef

/**
 * 战术风格选择器（8 风格，V0.1 03 §3 战术风格区）。
 *
 * 使用 FlowRow + FilterChip 展示 8 种战术风格：
 * 控球组织 / 快速反击 / 高位压迫 / 防守反击 / 边路传中 / 中路渗透 / 长传冲吊 / 巨星自由发挥。
 *
 * @param current 当前选中风格
 * @param currentFormation 当前阵型（用于标记兼容性）
 * @param onSelect 风格选择回调
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TacticalStyleSelector(
    current: TacticStyle,
    currentFormation: com.greendynasty.football.match.api.Formation,
    onSelect: (TacticStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val styles = TacticalStyleDef.all()

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        styles.forEach { def ->
            val isCompatible = def.isCompatibleWith(currentFormation)
            FilterChip(
                selected = def.style == current,
                onClick = { onSelect(def.style) },
                label = {
                    Column {
                        Text(
                            text = def.name,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (!isCompatible) {
                            Text(
                                text = "阵型不匹配",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                )
            )
        }
    }
}
