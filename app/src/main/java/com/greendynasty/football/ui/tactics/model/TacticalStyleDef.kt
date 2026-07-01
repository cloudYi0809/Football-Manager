package com.greendynasty.football.ui.tactics.model

import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.Mentality
import com.greendynasty.football.match.api.PassStyle
import com.greendynasty.football.match.api.TacticStyle

/**
 * 战术风格定义（V0.1 03 §3 战术风格区，8 种风格）。
 *
 * 复用 T02 [TacticStyle] 枚举（com.greendynasty.football.match.api），
 * 补充展示名、描述、兼容阵型与参数修正。
 *
 * 8 种风格：
 * 1. 控球组织 POSSESSION
 * 2. 快速反击 COUNTER_ATTACK
 * 3. 高位压迫 HIGH_PRESS
 * 4. 防守反击 DEFENSIVE_COUNTER
 * 5. 边路传中 WING_CROSS
 * 6. 中路渗透 CENTRAL_PENETRATION
 * 7. 长传冲吊 LONG_BALL
 * 8. 巨星自由发挥 STAR_FREE
 *
 * @property style 风格枚举（复用 T02 [TacticStyle]）
 * @property name 中文展示名
 * @property description 风格说明
 * @property compatibleFormations 兼容阵型
 * @property modifiers 参数修正（推荐节奏/压迫/防线偏移与偏好传球/心态）
 */
data class TacticalStyleDef(
    val style: TacticStyle,
    val name: String,
    val description: String,
    val compatibleFormations: List<Formation>,
    val modifiers: TacticalParameterModifiers
) {

    /** 是否与指定阵型兼容 */
    fun isCompatibleWith(formation: Formation): Boolean = formation in compatibleFormations

    companion object {

        /** 全部 8 种战术风格定义 */
        private val ALL: List<TacticalStyleDef> = listOf(
            TacticalStyleDef(
                style = TacticStyle.POSSESSION,
                name = "控球组织",
                description = "通过控球掌控比赛节奏，适合技术型球队",
                compatibleFormations = listOf(Formation.F433, Formation.F4231, Formation.F352),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = 0,
                    pressingDelta = 0,
                    defensiveLineDelta = +1,
                    preferredPassStyle = PassStyle.SHORT,
                    preferredMentality = Mentality.BALANCED
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.COUNTER_ATTACK,
                name = "快速反击",
                description = "防守后快速转换进攻，适合速度型球队",
                compatibleFormations = listOf(Formation.F442, Formation.F433, Formation.F4141),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = +2,
                    pressingDelta = -1,
                    defensiveLineDelta = -1,
                    preferredPassStyle = PassStyle.DIRECT,
                    preferredMentality = Mentality.BALANCED
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.HIGH_PRESS,
                name = "高位压迫",
                description = "在前场高位逼抢，体能消耗大",
                compatibleFormations = listOf(Formation.F433, Formation.F4231),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = +1,
                    pressingDelta = +3,
                    defensiveLineDelta = +2,
                    preferredPassStyle = PassStyle.SHORT,
                    preferredMentality = Mentality.ALL_ATTACK
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.DEFENSIVE_COUNTER,
                name = "防守反击",
                description = "低位防守+快速反击，适合弱队",
                compatibleFormations = listOf(Formation.F442, Formation.F4141, Formation.F532),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = -1,
                    pressingDelta = -2,
                    defensiveLineDelta = -3,
                    preferredPassStyle = PassStyle.DIRECT,
                    preferredMentality = Mentality.ALL_DEFENSE
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.WING_CROSS,
                name = "边路传中",
                description = "通过边路突破传中制造机会",
                compatibleFormations = listOf(Formation.F442, Formation.F433),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = 0,
                    pressingDelta = 0,
                    defensiveLineDelta = 0,
                    preferredPassStyle = PassStyle.LONG,
                    preferredMentality = Mentality.BALANCED
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.CENTRAL_PENETRATION,
                name = "中路渗透",
                description = "通过中路短传配合渗透",
                compatibleFormations = listOf(Formation.F4231, Formation.F352),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = 0,
                    pressingDelta = +1,
                    defensiveLineDelta = +1,
                    preferredPassStyle = PassStyle.SHORT,
                    preferredMentality = Mentality.BALANCED
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.LONG_BALL,
                name = "长传冲吊",
                description = "直接长传找前锋，简单直接",
                compatibleFormations = listOf(Formation.F442, Formation.F4141),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = +1,
                    pressingDelta = -1,
                    defensiveLineDelta = -1,
                    preferredPassStyle = PassStyle.LONG,
                    preferredMentality = Mentality.BALANCED
                )
            ),
            TacticalStyleDef(
                style = TacticStyle.STAR_FREE,
                name = "巨星自由发挥",
                description = "围绕巨星自由进攻，依赖个人能力",
                compatibleFormations = listOf(Formation.F433, Formation.F4231),
                modifiers = TacticalParameterModifiers(
                    tempoDelta = +1,
                    pressingDelta = 0,
                    defensiveLineDelta = +1,
                    preferredPassStyle = PassStyle.SHORT,
                    preferredMentality = Mentality.ALL_ATTACK
                )
            )
        )

        private val BY_STYLE: Map<TacticStyle, TacticalStyleDef> =
            ALL.associateBy { it.style }

        /** 由 T02 [TacticStyle] 查询风格定义 */
        fun from(style: TacticStyle): TacticalStyleDef =
            BY_STYLE[style] ?: BY_STYLE.getValue(TacticStyle.POSSESSION)

        /** 全部 8 种风格 */
        fun all(): List<TacticalStyleDef> = ALL

        /** 默认风格（控球组织） */
        val DEFAULT: TacticalStyleDef = from(TacticStyle.POSSESSION)
    }
}

/**
 * 战术风格对参数的修正。
 *
 * delta 表示该风格相对平衡参数的推荐偏移（可为负），
 * 用于战术熟练度计算与 UI 提示。
 *
 * @property tempoDelta 节奏偏移
 * @property pressingDelta 压迫强度偏移
 * @property defensiveLineDelta 防线高度偏移
 * @property preferredPassStyle 偏好传球风格
 * @property preferredMentality 偏好心态
 */
data class TacticalParameterModifiers(
    val tempoDelta: Int,
    val pressingDelta: Int,
    val defensiveLineDelta: Int,
    val preferredPassStyle: PassStyle,
    val preferredMentality: Mentality
) {
    companion object {
        /** 中性修正（无偏移） */
        val NEUTRAL = TacticalParameterModifiers(
            tempoDelta = 0,
            pressingDelta = 0,
            defensiveLineDelta = 0,
            preferredPassStyle = PassStyle.SHORT,
            preferredMentality = Mentality.BALANCED
        )
    }
}
