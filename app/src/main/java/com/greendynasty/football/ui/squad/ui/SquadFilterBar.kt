package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.squad.model.SquadFilter
import com.greendynasty.football.ui.squad.model.SquadSortOption

/**
 * 阵容页筛选/排序条。
 *
 * 包含：搜索框（normalized_name 模糊）+ 排序下拉 + 8 项组合筛选入口。
 * 8 项筛选：位置 / 年龄 / 能力 / 潜力 / 合同 / 伤病 / 国籍 / 状态。
 *
 * @param query 当前搜索关键字
 * @param filter 当前筛选条件
 * @param sort 当前排序
 * @param onQueryChange 搜索关键字变更回调
 * @param onFilterChange 筛选条件变更回调
 * @param onSortChange 排序变更回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadFilterBar(
    query: String,
    filter: SquadFilter,
    sort: SquadSortOption,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SquadFilter) -> Unit,
    onSortChange: (SquadSortOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 搜索框 + 排序下拉
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索球员姓名") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索"
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            // 排序下拉
            var sortExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = sortExpanded,
                onExpandedChange = { sortExpanded = it },
                modifier = Modifier.width(140.dp)
            ) {
                OutlinedTextField(
                    value = sort.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("排序") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded)
                    },
                    modifier = Modifier.menuAnchor(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                ExposedDropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    SquadSortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onSortChange(option)
                                sortExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. 8 项筛选入口（横向滚动）
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FilterEntry.values().toList()) { entry ->
                FilterChipItem(
                    entry = entry,
                    filter = filter,
                    onFilterChange = onFilterChange
                )
            }
        }
    }
}

/** 单个筛选 Chip + 下拉菜单 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipItem(
    entry: FilterEntry,
    filter: SquadFilter,
    onFilterChange: (SquadFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = entry.isSelected(filter)
    val label = entry.displayLabel(filter)

    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
            colors = FilterChipDefaults.filterChipColors()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            entry.options().forEach { optionLabel ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onFilterChange(entry.applyOption(filter, optionLabel))
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 筛选维度定义（8 项） */
private enum class FilterEntry {
    POSITION {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.position == null) "位置" else "位置·${filter.position}"

        override fun isSelected(filter: SquadFilter) = filter.position != null

        override fun options() = listOf("不限") + POSITIONS

        override fun applyOption(filter: SquadFilter, label: String) =
            filter.copy(position = label.takeIf { it != "不限" })
    },
    AGE {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.ageMin == 16 && filter.ageMax == 45) "年龄" else "年龄·${filter.ageMin}-${filter.ageMax}"

        override fun isSelected(filter: SquadFilter) = !(filter.ageMin == 16 && filter.ageMax == 45)

        override fun options() = listOf("不限", "U18(≤18)", "U21(≤21)", "当打(22-30)", "老将(31+)")

        override fun applyOption(filter: SquadFilter, label: String): SquadFilter = when (label) {
            "不限" -> filter.copy(ageMin = 16, ageMax = 45)
            "U18(≤18)" -> filter.copy(ageMin = 16, ageMax = 18)
            "U21(≤21)" -> filter.copy(ageMin = 16, ageMax = 21)
            "当打(22-30)" -> filter.copy(ageMin = 22, ageMax = 30)
            "老将(31+)" -> filter.copy(ageMin = 31, ageMax = 45)
            else -> filter
        }
    },
    CA {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.caMin == 0 && filter.caMax == 200) "能力" else "能力·≥${filter.caMin}"

        override fun isSelected(filter: SquadFilter) = !(filter.caMin == 0 && filter.caMax == 200)

        override fun options() = listOf("不限", "≥80", "≥100", "≥130", "≥150")

        override fun applyOption(filter: SquadFilter, label: String): SquadFilter {
            val min = when (label) {
                "≥80" -> 80
                "≥100" -> 100
                "≥130" -> 130
                "≥150" -> 150
                else -> 0
            }
            return if (min == 0) filter.copy(caMin = 0, caMax = 200)
            else filter.copy(caMin = min, caMax = 200)
        }
    },
    PA {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.paMin == 0 && filter.paMax == 200) "潜力" else "潜力·≥${filter.paMin}"

        override fun isSelected(filter: SquadFilter) = !(filter.paMin == 0 && filter.paMax == 200)

        override fun options() = listOf("不限", "≥100", "≥130", "≥150", "≥180")

        override fun applyOption(filter: SquadFilter, label: String): SquadFilter {
            val min = when (label) {
                "≥100" -> 100
                "≥130" -> 130
                "≥150" -> 150
                "≥180" -> 180
                else -> 0
            }
            return if (min == 0) filter.copy(paMin = 0, paMax = 200)
            else filter.copy(paMin = min, paMax = 200)
        }
    },
    CONTRACT {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.contractUntilYear == null) "合同" else "合同·${filter.contractUntilYear}"

        override fun isSelected(filter: SquadFilter) = filter.contractUntilYear != null

        override fun options() = listOf("不限", "2003", "2004", "2005", "2006")

        override fun applyOption(filter: SquadFilter, label: String) =
            filter.copy(contractUntilYear = label.toIntOrNull())
    },
    INJURY {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.injuryStatus == null) "伤病" else "伤病·${if (filter.injuryStatus == "healthy") "健康" else "伤停"}"

        override fun isSelected(filter: SquadFilter) = filter.injuryStatus != null

        override fun options() = listOf("不限", "健康", "伤停")

        override fun applyOption(filter: SquadFilter, label: String): SquadFilter = when (label) {
            "健康" -> filter.copy(injuryStatus = "healthy")
            "伤停" -> filter.copy(injuryStatus = "injured")
            else -> filter.copy(injuryStatus = null)
        }
    },
    NATIONALITY {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.nationality == null) "国籍" else "国籍·${filter.nationality}"

        override fun isSelected(filter: SquadFilter) = filter.nationality != null

        override fun options() = listOf("不限") + NATIONS

        override fun applyOption(filter: SquadFilter, label: String) =
            filter.copy(nationality = label.takeIf { it != "不限" })
    },
    STATUS {
        override fun displayLabel(filter: SquadFilter) =
            if (filter.playerStatus == null) "状态" else "状态·${filter.playerStatus}"

        override fun isSelected(filter: SquadFilter) = filter.playerStatus != null

        override fun options() = listOf("不限", "挂牌", "队长", "外租")

        override fun applyOption(filter: SquadFilter, label: String): SquadFilter {
            val status = when (label) {
                "挂牌" -> "listed"
                "队长" -> "captain"
                "外租" -> "loaned"
                else -> null
            }
            return filter.copy(playerStatus = status)
        }
    };

    abstract fun displayLabel(filter: SquadFilter): String
    abstract fun isSelected(filter: SquadFilter): Boolean
    abstract fun options(): List<String>
    abstract fun applyOption(filter: SquadFilter, label: String): SquadFilter

    companion object {
        private val POSITIONS = listOf("GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST")
        private val NATIONS = listOf("中国", "巴西", "阿根廷", "英格兰", "西班牙", "法国", "德国", "意大利")
    }
}
