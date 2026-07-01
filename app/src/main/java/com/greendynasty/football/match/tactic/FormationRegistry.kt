package com.greendynasty.football.match.tactic

import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.Position

/**
 * 阵型注册表（V0.2 04 §二.1）
 *
 * 登记 6 种阵型，每种含 11 个位置及球场坐标。
 *
 * 坐标体系（V0.2 04 §二.1）：
 * - x：横向 0-100（0=左边界，50=中路，100=右边界）
 * - y：纵向 0-100（0=己方底线，50=中线，100=对方底线）
 *
 * 坐标用于 EventLayer 的事件生成（如 ST 在禁区附近 y≈82，
 * 传中事件需 LW/RW 在边路 y≈70）。
 *
 * 6 种阵型：F433 / F442 / F352 / F4231 / F4141 / F532
 */
object FormationRegistry {

    /** 阵型位置条目 */
    data class FormationSlot(
        /** 场上位置 */
        val position: Position,
        /** 横向坐标 0-100 */
        val x: Double,
        /** 纵向坐标 0-100 */
        val y: Double
    )

    /** 全部阵型定义：Formation -> 11 个位置 */
    private val formations: Map<Formation, List<FormationSlot>> = mapOf(
        // ===== 4-3-3：4 后卫 + 3 中场 + 3 前锋 =====
        Formation.F433 to listOf(
            FormationSlot(Position.GK, 50.0, 5.0),
            FormationSlot(Position.LB, 15.0, 22.0),
            FormationSlot(Position.CB, 35.0, 18.0),
            FormationSlot(Position.CB, 65.0, 18.0),
            FormationSlot(Position.RB, 85.0, 22.0),
            FormationSlot(Position.DM, 50.0, 38.0),
            FormationSlot(Position.CM, 30.0, 52.0),
            FormationSlot(Position.CM, 70.0, 52.0),
            FormationSlot(Position.LW, 18.0, 75.0),
            FormationSlot(Position.ST, 50.0, 85.0),
            FormationSlot(Position.RW, 82.0, 75.0)
        ),
        // ===== 4-4-2：4 后卫 + 4 中场 + 2 前锋 =====
        Formation.F442 to listOf(
            FormationSlot(Position.GK, 50.0, 5.0),
            FormationSlot(Position.LB, 15.0, 22.0),
            FormationSlot(Position.CB, 35.0, 18.0),
            FormationSlot(Position.CB, 65.0, 18.0),
            FormationSlot(Position.RB, 85.0, 22.0),
            FormationSlot(Position.LW, 18.0, 50.0),
            FormationSlot(Position.CM, 38.0, 48.0),
            FormationSlot(Position.CM, 62.0, 48.0),
            FormationSlot(Position.RW, 82.0, 50.0),
            FormationSlot(Position.ST, 40.0, 82.0),
            FormationSlot(Position.CF, 60.0, 82.0)
        ),
        // ===== 3-5-2：3 后卫 + 5 中场 + 2 前锋 =====
        Formation.F352 to listOf(
            FormationSlot(Position.GK, 50.0, 5.0),
            FormationSlot(Position.CB, 25.0, 18.0),
            FormationSlot(Position.CB, 50.0, 16.0),
            FormationSlot(Position.CB, 75.0, 18.0),
            FormationSlot(Position.LB, 12.0, 45.0),
            FormationSlot(Position.DM, 50.0, 40.0),
            FormationSlot(Position.CM, 35.0, 55.0),
            FormationSlot(Position.CM, 65.0, 55.0),
            FormationSlot(Position.RB, 88.0, 45.0),
            FormationSlot(Position.ST, 40.0, 82.0),
            FormationSlot(Position.CF, 60.0, 82.0)
        ),
        // ===== 4-2-3-1：4 后卫 + 2 后腰 + 3 前腰 + 1 前锋 =====
        Formation.F4231 to listOf(
            FormationSlot(Position.GK, 50.0, 5.0),
            FormationSlot(Position.LB, 15.0, 22.0),
            FormationSlot(Position.CB, 35.0, 18.0),
            FormationSlot(Position.CB, 65.0, 18.0),
            FormationSlot(Position.RB, 85.0, 22.0),
            FormationSlot(Position.DM, 38.0, 35.0),
            FormationSlot(Position.DM, 62.0, 35.0),
            FormationSlot(Position.LW, 20.0, 68.0),
            FormationSlot(Position.AM, 50.0, 65.0),
            FormationSlot(Position.RW, 80.0, 68.0),
            FormationSlot(Position.ST, 50.0, 85.0)
        ),
        // ===== 4-1-4-1：4 后卫 + 1 后腰 + 4 中场 + 1 前锋 =====
        Formation.F4141 to listOf(
            FormationSlot(Position.GK, 50.0, 5.0),
            FormationSlot(Position.LB, 15.0, 22.0),
            FormationSlot(Position.CB, 35.0, 18.0),
            FormationSlot(Position.CB, 65.0, 18.0),
            FormationSlot(Position.RB, 85.0, 22.0),
            FormationSlot(Position.DM, 50.0, 35.0),
            FormationSlot(Position.LW, 18.0, 58.0),
            FormationSlot(Position.CM, 38.0, 55.0),
            FormationSlot(Position.CM, 62.0, 55.0),
            FormationSlot(Position.RW, 82.0, 58.0),
            FormationSlot(Position.ST, 50.0, 85.0)
        ),
        // ===== 5-3-2：5 后卫 + 3 中场 + 2 前锋 =====
        Formation.F532 to listOf(
            FormationSlot(Position.GK, 50.0, 5.0),
            FormationSlot(Position.LB, 10.0, 20.0),
            FormationSlot(Position.CB, 28.0, 16.0),
            FormationSlot(Position.CB, 50.0, 14.0),
            FormationSlot(Position.CB, 72.0, 16.0),
            FormationSlot(Position.RB, 90.0, 20.0),
            FormationSlot(Position.CM, 30.0, 48.0),
            FormationSlot(Position.CM, 50.0, 45.0),
            FormationSlot(Position.CM, 70.0, 48.0),
            FormationSlot(Position.ST, 40.0, 82.0),
            FormationSlot(Position.CF, 60.0, 82.0)
        )
    )

    /** 查询阵型的 11 个位置及坐标，未注册返回空列表 */
    fun getFormation(formation: Formation): List<FormationSlot> =
        formations[formation] ?: emptyList()

    /** 查询阵型中某位置的球员坐标（取首个匹配） */
    fun getPositionSlot(formation: Formation, position: Position): FormationSlot? =
        formations[formation]?.firstOrNull { it.position == position }

    /** 全部已登记阵型 */
    fun allFormations(): Set<Formation> = formations.keys

    /** 某位置在阵型中的数量（如 F442 有 2 个 CB） */
    fun positionCount(formation: Formation, position: Position): Int =
        formations[formation]?.count { it.position == position } ?: 0
}
