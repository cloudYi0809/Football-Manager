package com.greendynasty.football.ui.tactics.model

/**
 * 位置适配度计算结果（V0.1 03 §3 战术页）。
 *
 * 描述球员在特定位置的适配度，供首发选择与拖拽换位时展示。
 * 适配度 0-100：100=完全适配，70-90=可踢，20-40=勉强，<20=不适应。
 *
 * @property playerId 球员 ID
 * @property position 目标位置（GK/CB/LB/RB/DM/CM/AM/LW/RW/ST/CF）
 * @property fitScore 适配度 0-100
 * @property reason 适配度判定原因（如"主位置匹配"/"副位置"/"同类位置"/"不适应"）
 */
data class PositionFit(
    val playerId: Int,
    val position: String,
    val fitScore: Int,
    val reason: String
) {

    /** 适配等级（用于 UI 颜色区分） */
    val level: FitLevel
        get() = when {
            fitScore >= 90 -> FitLevel.PERFECT
            fitScore >= 70 -> FitLevel.GOOD
            fitScore >= 40 -> FitLevel.FAIR
            else -> FitLevel.POOR
        }

    /** 是否适配（可用于首发校验） */
    val isFit: Boolean
        get() = fitScore >= 40
}

/**
 * 适配度等级
 */
enum class FitLevel(val label: String) {
    PERFECT("完美"),
    GOOD("良好"),
    FAIR("一般"),
    POOR("不适应")
}
