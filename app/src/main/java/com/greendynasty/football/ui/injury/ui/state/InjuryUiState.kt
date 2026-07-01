package com.greendynasty.football.ui.injury.ui.state

import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.injury.model.InjuryRiskScore
import com.greendynasty.football.injury.model.MedicalFacilityEntity

/**
 * 医疗中心页 UI 状态（6 种完备状态）。
 *
 * - [Loading]：数据加载中
 * - [Empty]：无活跃伤病
 * - [Error]：数据加载错误
 * - [Normal]：展示活跃伤病列表 + 医疗设施 + 风险评分
 * - [Locked]：功能未解锁（如未加载存档）
 * - [Warning]：有风险提示但仍展示列表
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class InjuryUiState {

    /** 加载中 */
    data object Loading : InjuryUiState()

    /** 空数据：无活跃伤病 */
    data class Empty(val reason: String = "当前无伤病球员") : InjuryUiState()

    /** 错误 */
    data class Error(val message: String) : InjuryUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : InjuryUiState()

    /** 正常：展示活跃伤病 + 医疗设施 + 风险评分 */
    data class Normal(
        val injuries: List<InjuryDisplay>,
        val facility: MedicalFacilityDisplay?,
        val riskScores: List<InjuryRiskScore>
    ) : InjuryUiState()

    /** 警告：有高风险球员提示 */
    data class Warning(
        val message: String,
        val injuries: List<InjuryDisplay>,
        val facility: MedicalFacilityDisplay?,
        val riskScores: List<InjuryRiskScore>
    ) : InjuryUiState()
}

/**
 * 伤病展示模型（UI 层使用，附球员姓名）
 */
data class InjuryDisplay(
    val injury: SaveInjuryEntity,
    val playerName: String,
    val severityName: String,
    val injuryTypeNameCn: String,
    val progressPercent: Int,
    val remainingDays: Int,
    val statusDisplayName: String
)

/**
 * 医疗设施展示模型
 */
data class MedicalFacilityDisplay(
    val facility: MedicalFacilityEntity,
    val speedMultiplierPercent: Int,
    val recurrenceReductionPercent: Int,
    val canUpgrade: Boolean,
    val upgradeCost: Int
)
