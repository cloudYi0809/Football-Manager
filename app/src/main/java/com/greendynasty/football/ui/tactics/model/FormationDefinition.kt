package com.greendynasty.football.ui.tactics.model

import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.tactic.FormationRegistry

/**
 * 阵型定义（复用 T02 [FormationRegistry]）。
 *
 * 包装 T02 的阵型坐标数据，提供展示名与 UI 友好的归一化坐标（0-1）。
 * 6 阵型：F433 / F442 / F352 / F4231 / F4141 / F532，每阵型 11 个位置。
 *
 * 坐标体系（沿用 T02 FormationRegistry）：
 * - x：横向 0-100（0=左边界，50=中路，100=右边界）→ 归一化为 0-1
 * - y：纵向 0-100（0=己方底线，50=中线，100=对方底线）→ 归一化为 0-1
 *
 * @property formation 阵型枚举（复用 T02 [Formation]）
 * @property name 展示名（如"4-3-3"）
 * @property shortName 简称（如"433"）
 * @property defenseLine 后卫线人数
 * @property midfieldLine 中场线人数
 * @property attackLine 前锋线人数
 * @property positions 11 个位置定义
 */
data class FormationDefinition(
    val formation: Formation,
    val name: String,
    val shortName: String,
    val defenseLine: Int,
    val midfieldLine: Int,
    val attackLine: Int,
    val positions: List<FormationPosition>
) {

    companion object {

        /** 战术页支持的 6 阵型 */
        private val SUPPORTED: List<Formation> = listOf(
            Formation.F433,
            Formation.F442,
            Formation.F352,
            Formation.F4231,
            Formation.F4141,
            Formation.F532
        )

        /** 阵型展示名映射 */
        private val NAMES: Map<Formation, String> = mapOf(
            Formation.F433 to "4-3-3",
            Formation.F442 to "4-4-2",
            Formation.F352 to "3-5-2",
            Formation.F4231 to "4-2-3-1",
            Formation.F4141 to "4-1-4-1",
            Formation.F532 to "5-3-2"
        )

        /** 阵型线型映射（后卫/中场/前锋人数） */
        private val LINES: Map<Formation, Triple<Int, Int, Int>> = mapOf(
            Formation.F433 to Triple(4, 3, 3),
            Formation.F442 to Triple(4, 4, 2),
            Formation.F352 to Triple(3, 5, 2),
            Formation.F4231 to Triple(4, 5, 1),
            Formation.F4141 to Triple(4, 5, 1),
            Formation.F532 to Triple(5, 3, 2)
        )

        /**
         * 由 T02 [Formation] 构造阵型定义。
         * 坐标从 [FormationRegistry.getFormation] 取（0-100），归一化为 0-1。
         */
        fun from(formation: Formation): FormationDefinition {
            val slots = FormationRegistry.getFormation(formation)
            val positions = slots.mapIndexed { index, slot ->
                FormationPosition(
                    slotId = index + 1,
                    position = slot.position,
                    x = (slot.x / 100.0).toFloat(),
                    y = (slot.y / 100.0).toFloat(),
                    roleLabel = roleLabelOf(slot.position)
                )
            }
            val name = NAMES[formation] ?: formation.name
            val (def, mid, att) = LINES[formation] ?: Triple(4, 4, 2)
            return FormationDefinition(
                formation = formation,
                name = name,
                shortName = name.replace("-", ""),
                defenseLine = def,
                midfieldLine = mid,
                attackLine = att,
                positions = positions
            )
        }

        /** 全部 6 阵型定义 */
        fun all(): List<FormationDefinition> = SUPPORTED.map { from(it) }

        /** 默认阵型（4-3-3） */
        val DEFAULT: FormationDefinition = from(Formation.F433)

        /**
         * 位置的角色展示名（中文）。
         * GK→门将，CB→中后卫，LB→左后卫 等。
         */
        fun roleLabelOf(position: Position): String = when (position) {
            Position.GK -> "门将"
            Position.CB -> "中后卫"
            Position.LB -> "左后卫"
            Position.RB -> "右后卫"
            Position.DM -> "后腰"
            Position.CM -> "中前卫"
            Position.AM -> "前腰"
            Position.LW -> "左边锋"
            Position.RW -> "右边锋"
            Position.ST -> "中锋"
            Position.CF -> "中锋"
        }
    }
}

/**
 * 阵型位置定义（UI 友好的归一化坐标）。
 *
 * @property slotId 槽位 ID（1-11）
 * @property position 场上位置（复用 T02 [Position]）
 * @property x 横向坐标 0-1（0=左边界，0.5=中路，1=右边界）
 * @property y 纵向坐标 0-1（0=己方底线，0.5=中线，1=对方底线）
 * @property roleLabel 角色展示名
 */
data class FormationPosition(
    val slotId: Int,
    val position: Position,
    val x: Float,
    val y: Float,
    val roleLabel: String
)
