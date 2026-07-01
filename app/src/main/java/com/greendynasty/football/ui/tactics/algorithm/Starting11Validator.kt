package com.greendynasty.football.ui.tactics.algorithm

import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.tactic.FormationRegistry
import com.greendynasty.football.ui.tactics.data.PlayerWithPosition
import com.greendynasty.football.ui.tactics.model.TacticalSetup

/**
 * 首发 11 人校验器（V0.1 03 §3 战术页 + V0.2 04 §二）。
 *
 * 校验项：
 * 1. 必须 11 人
 * 2. 位置覆盖（按阵型 11 个位置全部填满）
 * 3. GK 必须有且仅有 1 名
 * 4. 球员不重复
 * 5. 无红牌停赛
 * 6. 无伤病
 *
 * 提供两个重载：
 * - [validate] 仅校验人数 / 重复（不依赖球员详情）
 * - [validateWithPlayers] 完整校验（含伤病 / 停赛 / 位置适配）
 */
class Starting11Validator {

    /**
     * 基础校验：人数与重复（不依赖球员详情）。
     *
     * @param starting11 首发 11 人球员 ID 列表（按阵型位置顺序，-1 / null 元素请过滤后传入）
     * @param formation 阵型
     * @return 校验结果
     */
    fun validate(starting11: List<Int>, formation: Formation): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. 必须 11 人
        if (starting11.size != TacticalSetup.STARTING_SIZE) {
            errors.add("首发必须 ${TacticalSetup.STARTING_SIZE} 人，当前 ${starting11.size} 人")
        }

        // 2. 球员不重复
        val distinctIds = starting11.toSet()
        if (distinctIds.size != starting11.size) {
            errors.add("首发存在重复球员（共 ${starting11.size - distinctIds.size} 人重复）")
        }

        // 3. 阵型位置覆盖（阵型必须注册 11 个位置）
        val formationSlots = FormationRegistry.getFormation(formation)
        if (formationSlots.size != TacticalSetup.STARTING_SIZE) {
            errors.add("阵型 ${formation.name} 位置数异常（${formationSlots.size}），应为 11")
        }

        // 4. GK 必须有（阵型首位应为 GK）
        val hasGk = formationSlots.firstOrNull()?.position ==
            com.greendynasty.football.match.api.Position.GK
        if (!hasGk && formationSlots.isNotEmpty()) {
            errors.add("阵型 ${formation.name} 缺少门将位置")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    /**
     * 完整校验：含伤病 / 停赛 / GK 唯一性 / 位置适配。
     *
     * @param starting11 首发 11 人球员 ID 列表（按阵型位置顺序）
     * @param formation 阵型
     * @param players 全部可选球员（含状态），用于查询伤病 / 停赛
     * @return 校验结果
     */
    fun validateWithPlayers(
        starting11: List<Int>,
        formation: Formation,
        players: List<PlayerWithPosition>
    ): ValidationResult {
        // 先做基础校验
        val base = validate(starting11, formation)
        val errors = base.errors.toMutableList()

        val playerMap = players.associateBy { it.playerId }
        val formationSlots = FormationRegistry.getFormation(formation)

        // GK 唯一性：检查实际分配到 GK 位置的球员
        val gkSlotIndices = formationSlots.mapIndexed { index, slot ->
            index to slot
        }.filter { it.second.position == com.greendynasty.football.match.api.Position.GK }

        var gkAssigned = 0
        starting11.forEachIndexed { index, playerId ->
            val player = playerMap[playerId] ?: return@forEachIndexed
            val slot = formationSlots.getOrNull(index) ?: return@forEachIndexed

            // 5. 无伤病
            if (player.isInjured) {
                errors.add("球员 ${player.name}（${player.position}）处于伤病状态，无法首发")
            }
            // 6. 无停赛
            if (player.isSuspended) {
                errors.add("球员 ${player.name}（${player.position}）红牌停赛，无法首发")
            }

            // GK 计数
            if (slot.position == com.greendynasty.football.match.api.Position.GK &&
                playerId > 0
            ) {
                gkAssigned++
            }
        }

        // GK 数量校验
        if (starting11.size == TacticalSetup.STARTING_SIZE && gkSlotIndices.isNotEmpty()) {
            if (gkAssigned == 0) {
                errors.add("首发必须包含 1 名门将（GK）")
            } else if (gkAssigned > 1) {
                errors.add("首发只能有 1 名门将（GK），当前 $gkAssigned 名")
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}

/**
 * 校验结果。
 *
 * @property isValid 是否通过校验
 * @property errors 错误信息列表（通过时为空）
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
) {

    /** 错误信息合并文案 */
    val errorSummary: String
        get() = if (errors.isEmpty()) "校验通过" else errors.joinToString("；")
}
