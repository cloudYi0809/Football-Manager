package com.greendynasty.football.ui.tactics.model

/**
 * 球员角色分配（V0.1 03 §3 战术页角色区）。
 *
 * 6 个固定角色：队长 / 点球手 / 任意球手 / 角球手 / 进攻核心 / 防守核心。
 * 球员 ID 映射，null 表示未分配。
 *
 * @property captainId 队长（影响士气与更衣室）
 * @property penaltyTakerId 点球手（依赖 finishing 与 composure）
 * @property freeKickTakerId 任意球手（依赖 technique 与 long_shots）
 * @property cornerTakerId 角球手（依赖 crossing 与 technique）
 * @property attackCoreId 进攻核心（进攻支点）
 * @property defenseCoreId 防守核心（防守组织者）
 */
data class PlayerRoleAssignment(
    val captainId: Int? = null,
    val penaltyTakerId: Int? = null,
    val freeKickTakerId: Int? = null,
    val cornerTakerId: Int? = null,
    val attackCoreId: Int? = null,
    val defenseCoreId: Int? = null
) {

    /**
     * 分配角色。
     *
     * @param role 角色类型
     * @param playerId 球员 ID，传 null 表示取消该角色分配
     * @return 新的角色分配对象（不可变）
     */
    fun assign(role: PlayerRole, playerId: Int?): PlayerRoleAssignment = when (role) {
        PlayerRole.CAPTAIN -> copy(captainId = playerId)
        PlayerRole.PENALTY_TAKER -> copy(penaltyTakerId = playerId)
        PlayerRole.FREE_KICK_TAKER -> copy(freeKickTakerId = playerId)
        PlayerRole.CORNER_TAKER -> copy(cornerTakerId = playerId)
        PlayerRole.ATTACK_CORE -> copy(attackCoreId = playerId)
        PlayerRole.DEFENSE_CORE -> copy(defenseCoreId = playerId)
    }

    /** 查询某角色当前分配的球员 ID */
    fun playerIdOf(role: PlayerRole): Int? = when (role) {
        PlayerRole.CAPTAIN -> captainId
        PlayerRole.PENALTY_TAKER -> penaltyTakerId
        PlayerRole.FREE_KICK_TAKER -> freeKickTakerId
        PlayerRole.CORNER_TAKER -> cornerTakerId
        PlayerRole.ATTACK_CORE -> attackCoreId
        PlayerRole.DEFENSE_CORE -> defenseCoreId
    }

    /** 是否已分配指定角色 */
    fun isAssigned(role: PlayerRole): Boolean = playerIdOf(role) != null

    /** 全部已分配角色及其球员 ID */
    fun assignedEntries(): List<Pair<PlayerRole, Int>> =
        PlayerRole.entries.mapNotNull { role ->
            playerIdOf(role)?.let { role to it }
        }

    companion object {
        /** 默认空分配 */
        val DEFAULT = PlayerRoleAssignment()
    }
}

/**
 * 球员角色枚举（6 种）。
 *
 * @property label 展示名
 * @property description 角色说明
 * @property keyAttribute 关键属性提示（用于推荐分配）
 */
enum class PlayerRole(
    val label: String,
    val description: String,
    val keyAttribute: String
) {
    CAPTAIN("队长", "球队精神领袖，影响士气与更衣室稳定", "leadership"),
    PENALTY_TAKER("点球手", "主罚点球，依赖终结与冷静", "finishing"),
    FREE_KICK_TAKER("任意球手", "主罚任意球，依赖技术与远射", "technique"),
    CORNER_TAKER("角球手", "主罚角球，依赖传中与技术", "crossing"),
    ATTACK_CORE("进攻核心", "进攻支点，主导进攻方向", "vision"),
    DEFENSE_CORE("防守核心", "防守组织者，统领防线", "marking")
}
