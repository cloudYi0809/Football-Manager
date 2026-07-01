package com.greendynasty.football.ui.tactics.model

import com.greendynasty.football.match.api.Mentality
import com.greendynasty.football.match.api.PassStyle

/**
 * 战术参数（V0.1 03 §3 战术页参数区 + V0.2 04 §四）
 *
 * 实时影响比赛评分与战术熟练度。所有数值参数范围为 1-10。
 * 改变参数时由 ViewModel 实时重新计算战术熟练度提示。
 *
 * 复用 T02 的 [PassStyle] / [Mentality] 枚举（com.greendynasty.football.match.api）。
 */
data class TacticalParameters(

    /** 压迫强度 1-10（10=全场高位逼抢） */
    val pressingIntensity: Int = 5,

    /** 防线高度 1-10（10=高位防线，身后风险大） */
    val defensiveLine: Int = 5,

    /** 节奏 1-10（10=快节奏，体能消耗大） */
    val tempo: Int = 5,

    /** 传球风格：短传 / 直传 / 长传 */
    val passStyle: PassStyle = PassStyle.SHORT,

    /** 心态：全攻 / 平衡 / 全守 */
    val mentality: Mentality = Mentality.BALANCED,

    /** 进攻方向 */
    val attackingFocus: AttackingFocus = AttackingFocus.BOTH_FLANKS,

    /** 防守方式 */
    val defensiveFocus: DefensiveFocus = DefensiveFocus.ZONAL
) {
    init {
        require(pressingIntensity in 1..10) { "压迫强度必须在 1-10 之间，当前 $pressingIntensity" }
        require(defensiveLine in 1..10) { "防线高度必须在 1-10 之间，当前 $defensiveLine" }
        require(tempo in 1..10) { "节奏必须在 1-10 之间，当前 $tempo" }
    }

    /**
     * 激进度评分（用于体能风险判定，V0.1 03 §3"战术过于激进时提示体能风险"）。
     * 三项之和，范围 3-30。
     */
    val aggressionScore: Int
        get() = tempo + pressingIntensity + defensiveLine

    /** 是否属于激进战术（高风险） */
    val isAggressive: Boolean
        get() = aggressionScore >= HIGH_RISK_THRESHOLD

    /** 实时战术熟练度提示文案 */
    val proficiencyHint: String
        get() = when {
            aggressionScore >= 25 -> "战术过于激进，下半场体能风险高"
            aggressionScore >= 18 -> "战术偏激进，注意体能分配"
            aggressionScore <= 9 -> "战术过于保守，进攻威胁不足"
            else -> "战术参数平衡，推荐维持"
        }

    companion object {
        /** 默认参数 */
        val DEFAULT = TacticalParameters()

        /** 高风险激进度阈值（V0.2 04 §四） */
        const val HIGH_RISK_THRESHOLD = 25

        /** 中风险激进度阈值 */
        const val MEDIUM_RISK_THRESHOLD = 18
    }
}

/**
 * 进攻方向（V0.1 03 §3 参数区"进攻方向"）
 */
enum class AttackingFocus(val label: String, val description: String) {
    LEFT("左路", "主攻左路通道"),
    CENTER("中路", "主攻中路渗透"),
    RIGHT("右路", "主攻右路通道"),
    BOTH_FLANKS("两翼齐飞", "左右边路同时压上")
}

/**
 * 防守方式（V0.1 03 §3 参数区"防守方式"）
 */
enum class DefensiveFocus(val label: String, val description: String) {
    ZONAL("区域防守", "保持阵型，按区域盯防"),
    MAN_TO_MAN("人盯人", "贴身盯防对手核心"),
    HIGH_PRESS("前场逼抢", "在前场就地反抢"),
    DEEP_SIT("低位防守", "收缩防线密集防守")
}
