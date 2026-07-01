package com.greendynasty.football.ui.squad.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 球员长按操作枚举（7 种）。
 *
 * 列表行长按或详情页操作栏均触发这 7 种操作。
 * [needConfirm] 标记是否需要二次确认（铁律：关键操作必须二次确认）。
 *
 * 7 种：设置首发 / 放入替补 / 续约 / 挂牌 / 外租 / 设置训练 / 设置导师。
 */
enum class PlayerAction(
    val displayName: String,
    val icon: ImageVector,
    val needConfirm: Boolean
) {

    SET_STARTING("设置首发", Icons.Default.Star, true),

    SET_SUBSTITUTE("放入替补", Icons.Default.Person, true),

    RENEW_CONTRACT("续约", Icons.Default.Edit, true),

    LIST_FOR_TRANSFER("挂牌", Icons.Default.Share, true),

    LOAN_OUT("外租", Icons.Default.Send, true),

    SET_TRAINING("设置训练", Icons.Default.Settings, false),

    SET_MENTOR("设置导师", Icons.Default.Person, false);

    companion object {
        /** 列表长按弹层展示的全部操作顺序 */
        val LIST_ACTIONS: List<PlayerAction> = values().toList()
    }
}
