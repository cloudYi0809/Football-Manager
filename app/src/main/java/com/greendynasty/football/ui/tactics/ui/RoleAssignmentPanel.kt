package com.greendynasty.football.ui.tactics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.tactics.data.PlayerWithPosition
import com.greendynasty.football.ui.tactics.model.PlayerRole
import com.greendynasty.football.ui.tactics.model.PlayerRoleAssignment

/**
 * 角色分配面板（V0.1 03 §3 战术页角色区）。
 *
 * 6 个角色：队长 / 点球手 / 任意球手 / 角球手 / 进攻核心 / 防守核心。
 * 每个角色可从可选球员中选择一人，或取消分配。
 *
 * @param roles 当前角色分配
 * @param availablePlayers 全部可选球员
 * @param onAssign 角色分配回调（角色 + 球员 ID，null 表示取消）
 */
@Composable
fun RoleAssignmentPanel(
    roles: PlayerRoleAssignment,
    availablePlayers: List<PlayerWithPosition>,
    onAssign: (PlayerRole, Int?) -> Unit,
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
                text = "角色分配",
                style = MaterialTheme.typography.titleSmall
            )

            PlayerRole.entries.forEach { role ->
                RoleRow(
                    role = role,
                    assignedPlayerId = roles.playerIdOf(role),
                    availablePlayers = availablePlayers,
                    onAssign = { onAssign(role, it) }
                )
            }
        }
    }
}

/** 单个角色分配行 */
@Composable
private fun RoleRow(
    role: PlayerRole,
    assignedPlayerId: Int?,
    availablePlayers: List<PlayerWithPosition>,
    onAssign: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val assignedPlayer = availablePlayers.find { it.playerId == assignedPlayerId }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 角色标签与说明
        Column(modifier = Modifier.weight(1f)) {
            Text(role.label, style = MaterialTheme.typography.labelLarge)
            Text(
                role.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 当前分配球员 + 下拉选择
        Box {
            OutlinedButton(
                onClick = { expanded = true }
            ) {
                Text(
                    text = assignedPlayer?.name ?: "未分配",
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "选择球员")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // 取消分配选项
                DropdownMenuItem(
                    text = { Text("取消分配") },
                    onClick = {
                        onAssign(null)
                        expanded = false
                    }
                )
                // 球员列表（按 CA 降序）
                availablePlayers.sortedByDescending { it.ca }.forEach { player ->
                    DropdownMenuItem(
                        text = {
                            Text("${player.name}（${player.position}·CA${player.ca}）")
                        },
                        onClick = {
                            onAssign(player.playerId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
