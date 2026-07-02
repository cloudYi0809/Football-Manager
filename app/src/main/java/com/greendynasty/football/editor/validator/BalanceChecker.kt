package com.greendynasty.football.editor.validator

import com.greendynasty.football.editor.model.EditableClub
import com.greendynasty.football.editor.model.EditableEntity
import com.greendynasty.football.editor.model.EditablePlayer
import com.greendynasty.football.editor.model.EditTargetTable

/**
 * 平衡性警告严重程度。
 */
enum class BalanceSeverity { LOW, MEDIUM, HIGH }

/**
 * 平衡性警告（仅警告，不阻塞保存）。
 *
 * @property severity 严重程度
 * @property field 字段名
 * @property message 警告描述
 * @property suggestion 建议值
 */
data class BalanceWarning(
    val severity: BalanceSeverity,
    val field: String,
    val message: String,
    val suggestion: String
)

/**
 * 平衡性检查器（对应实现方案 §五.4 平衡性检查）。
 *
 * 所有阈值参数配置化（构造参数注入），符合「只调参不改架构」约束。
 * 检查 CA/PA 过高、PA<CA、PA-CA 差距过大、单属性过高、声望过高、设施全满级 6 类。
 *
 * @param caHighThreshold CA 高水平阈值（默认 180）
 * @param paHighThreshold PA 高潜力阈值（默认 190）
 * @param maxCaPaGap PA-CA 最大允许差距（默认 60）
 * @param singleAttrHighThreshold 单属性高水平阈值（默认 90）
 * @param reputationHighThreshold 声望过高阈值（默认 95）
 */
class BalanceChecker(
    private val caHighThreshold: Int = 180,
    private val paHighThreshold: Int = 190,
    private val maxCaPaGap: Int = 60,
    private val singleAttrHighThreshold: Int = 90,
    private val reputationHighThreshold: Int = 95
) {

    /**
     * 检查草稿态平衡性。
     * @return 警告列表（空列表表示无警告）
     */
    fun check(editable: EditableEntity, targetTable: EditTargetTable): List<BalanceWarning> {
        val warnings = mutableListOf<BalanceWarning>()
        when (targetTable) {
            EditTargetTable.PLAYER -> checkPlayer(editable as EditablePlayer, warnings)
            EditTargetTable.CLUB -> checkClub(editable as EditableClub, warnings)
            else -> { /* 属性 / 比赛无平衡性检查 */ }
        }
        return warnings
    }

    private fun checkPlayer(p: EditablePlayer, warnings: MutableList<BalanceWarning>) {
        for ((_, attr) in p.attributesDraft) {
            // 1. CA 过高
            if (attr.ca > caHighThreshold) warnings += BalanceWarning(
                BalanceSeverity.HIGH, "ca",
                "CA=${attr.ca} 超过高水平阈值 $caHighThreshold",
                "建议 CA ≤ $caHighThreshold"
            )
            // 2. PA 过高
            if (attr.pa > paHighThreshold) warnings += BalanceWarning(
                BalanceSeverity.HIGH, "pa",
                "PA=${attr.pa} 超过高潜力阈值 $paHighThreshold",
                "建议 PA ≤ $paHighThreshold"
            )
            // 3. PA-CA 差距过大
            if (attr.pa - attr.ca > maxCaPaGap) warnings += BalanceWarning(
                BalanceSeverity.MEDIUM, "pa",
                "PA-CA=${attr.pa - attr.ca} 差距过大",
                "建议 PA-CA ≤ $maxCaPaGap"
            )
            // 4. 单属性过高
            val singleAttrs = listOf(
                "shooting" to attr.shooting, "finishing" to attr.finishing, "passing" to attr.passing,
                "dribbling" to attr.dribbling, "pace" to attr.pace, "strength" to attr.strength,
                "defending" to attr.defending, "vision" to attr.vision
            )
            for ((name, value) in singleAttrs) {
                if (value > singleAttrHighThreshold) warnings += BalanceWarning(
                    BalanceSeverity.LOW, name,
                    "单属性 $name=$value 较高",
                    "建议单属性 ≤ $singleAttrHighThreshold"
                )
            }
        }
    }

    private fun checkClub(c: EditableClub, warnings: MutableList<BalanceWarning>) {
        // 5. 声望过高
        if (c.draft.reputation > reputationHighThreshold) warnings += BalanceWarning(
            BalanceSeverity.MEDIUM, "reputation",
            "声望=${c.draft.reputation} 过高",
            "建议声望 ≤ $reputationHighThreshold"
        )
        // 6. 设施全满级
        if (c.draft.trainingLevel >= 100 && c.draft.youthLevel >= 100 && c.draft.financeLevel >= 100) {
            warnings += BalanceWarning(
                BalanceSeverity.MEDIUM, "facilities",
                "全部设施满级",
                "建议保留差异化，不要全部 100"
            )
        }
    }
}
