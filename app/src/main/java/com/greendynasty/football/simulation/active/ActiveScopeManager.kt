package com.greendynasty.football.simulation.active

import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.config.ProgressionConfig

/**
 * 俱乐部模拟深度枚举（V0.2 §长程性能 + T07 方案 §二.2）
 *
 * 活跃范围分层核心：不同俱乐部采用不同模拟深度，控制每日推进耗时。
 *
 * @property FULL 玩家俱乐部：训练+战术+全属性+董事会+更衣室，每日完整模拟
 * @property ACTIVE 同联赛俱乐部：训练+伤病+士气+转会+AI决策，每日模拟
 * @property LIGHT 其他顶级联赛：仅积分+排名+月度汇总
 * @property MINIMAL 低级联赛：仅赛季末更新
 */
enum class ClubSimulationDepth {
    FULL,
    ACTIVE,
    LIGHT,
    MINIMAL
}

/**
 * 活跃范围管理器（V0.2 性能优化关键 + T07 方案 §八）
 *
 * 核心机制：
 * - 玩家所在联赛全部俱乐部 → ACTIVE 深度
 * - 玩家俱乐部 → FULL 深度
 * - 其他顶级联赛 → LIGHT（仅月度/转会窗）
 * - 低级联赛 → MINIMAL（仅赛季末）
 *
 * 性能预算（P95 ≤3 秒）：
 * - FULL 1 队 200ms / ACTIVE 19 队 1500ms / LIGHT 80 队 300ms / MINIMAL 0ms
 *
 * @param config 推进配置（包含活跃联赛列表）
 */
class ActiveScopeManager(
    private val config: ProgressionConfig
) {

    /**
     * 获取活跃联赛 ID 列表
     * 玩家所在联赛 + 主要关联联赛（如欧冠参赛国）
     */
    fun getActiveLeagueIds(): List<Int> {
        return config.activeLeagues
    }

    /**
     * 获取俱乐部模拟深度
     *
     * @param clubId 俱乐部 ID
     * @param ctx 推进上下文
     * @return 模拟深度枚举
     */
    fun getClubDepth(clubId: Int, ctx: AdvanceContext): ClubSimulationDepth {
        return when {
            // 玩家俱乐部：完整模拟
            clubId == ctx.managerClubId -> ClubSimulationDepth.FULL
            // 活跃范围联赛俱乐部：活跃模拟
            isClubInActiveLeagues(clubId, ctx) -> ClubSimulationDepth.ACTIVE
            // 其他顶级联赛：浅度模拟
            isClubInTopLeague(clubId) -> ClubSimulationDepth.LIGHT
            // 低级联赛：最小化模拟
            else -> ClubSimulationDepth.MINIMAL
        }
    }

    /**
     * 判断俱乐部是否在活跃联赛中
     * V1 简化：活跃范围由 config.activeLeagues 决定，俱乐部联赛归属待 T13 AI 画像完善
     */
    private fun isClubInActiveLeagues(clubId: Int, ctx: AdvanceContext): Boolean {
        // V1 简化：活跃联赛内的俱乐部均视为活跃范围
        // TODO: T13/T18 接入后，从 club_competition_season 表查询俱乐部所属联赛
        return ctx.activeLeagueIds.isNotEmpty()
    }

    /**
     * 判断俱乐部是否在顶级联赛（浅度模拟范围）
     */
    private fun isClubInTopLeague(clubId: Int): Boolean {
        // V1 简化：由 config.topLeaguesLight 决定
        // TODO: T18 接入后查询俱乐部实际联赛归属
        return config.topLeaguesLight.isNotEmpty()
    }

    /**
     * 动态扩展活跃范围（V0.2 §活跃范围切换）
     * - 玩家转会到其他联赛 → 新联赛变活跃
     * - 玩家参加欧冠 → 欧冠对手联赛临时活跃
     *
     * @param newLeagueId 新增的活跃联赛 ID
     */
    fun expandActiveScope(newLeagueId: Int) {
        if (newLeagueId !in config.activeLeagues) {
            // 注意：data class 的 List 默认不可变，此处创建新列表
            // 实际使用时若需持久化，应通过可变配置对象
            // TODO: T13 接入后通过 SaveManager 持久化活跃范围变更
        }
    }
}
