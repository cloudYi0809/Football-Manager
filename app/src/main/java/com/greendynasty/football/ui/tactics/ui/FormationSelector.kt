package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.ui.tactics.model.FormationDefinition

/**
 * 阵型选择器（6 阵型，V0.1 03 §3 战术页阵型区）。
 *
 * 使用 FlowRow + FilterChip 展示 6 阵型：F433 / F442 / F352 / F4231 / F4141 / F532。
 *
 * @param current 当前选中阵型
 * @param onSelect 阵型选择回调
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FormationSelector(
    current: Formation,
    onSelect: (Formation) -> Unit,
    modifier: Modifier = Modifier
) {
    val formations = FormationDefinition.all()

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        formations.forEach { def ->
            FilterChip(
                selected = def.formation == current,
                onClick = { onSelect(def.formation) },
                label = {
                    Text(
                        text = "${def.name}\n${def.defenseLine}-${def.midfieldLine}-${def.attackLine}",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
