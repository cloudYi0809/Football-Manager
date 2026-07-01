package com.greendynasty.football.ui.tactics.ui.state

import com.greendynasty.football.ui.tactics.algorithm.RiskLevel
import com.greendynasty.football.ui.tactics.data.PlayerWithPosition
import com.greendynasty.football.ui.tactics.model.TacticalSetup

/**
 * 战术页 UI 状态（6 种完备状态，V0.1 03 §五）。
 *
 * - [Loading]：数据加载中
 * - [Error]：数据加载错误
 * - [Normal]：正常展示战术设置 + 球员列表 + 熟练度
 * - [Empty]：无球员数据（未加载存档）
 * - [Warning]：有风险提示但仍展示（如战术过于激进）
 * - [Locked]：功能未解锁（未加载存档）
 *
 * ViewModel 通过 StateFlow 暴露此状态，UI 层据此渲染对应视图。
 */
sealed class TacticsUiState {

    /** 加载中 */
    data object Loading : TacticsUiState()

    /** 错误 */
    data class Error(val message: String) : TacticsUiState()

    /** 空数据：无可选球员 */
    data class Empty(val reason: String = "暂无可选球员，请先加载存档") : TacticsUiState()

    /** 功能未解锁 */
    data class Locked(val reason: String = "请先加载存档") : TacticsUiState()

    /**
     * 正常状态。
     *
     * @property setup 当前战术设置
     * @property proficiency 战术熟练度 0-100
     * @property riskLevel 体能风险等级
     * @property availablePlayers 全部可选球员
     * @property proficiencyHint 熟练度提示文案
     */
    data class Normal(
        val setup: TacticalSetup,
        val proficiency: Double,
        val riskLevel: RiskLevel,
        val availablePlayers: List<PlayerWithPosition>,
        val proficiencyHint: String = ""
    ) : TacticsUiState()

    /**
     * 警告状态：有风险提示但仍展示战术设置。
     *
     * @property message 警告信息（如"战术过于激进，体能风险高"）
     * @property setup 当前战术设置
     * @property proficiency 战术熟练度 0-100
     * @property riskLevel 体能风险等级
     * @property availablePlayers 全部可选球员
     */
    data class Warning(
        val message: String,
        val setup: TacticalSetup,
        val proficiency: Double,
        val riskLevel: RiskLevel,
        val availablePlayers: List<PlayerWithPosition>
    ) : TacticsUiState()
}
