package com.greendynasty.football.youth.ui.state

import com.greendynasty.football.youth.model.YouthEventEntity
import com.greendynasty.football.youth.repository.YouthAcademyStatistics
import com.greendynasty.football.youth.repository.YouthAcademyViewItem
import com.greendynasty.football.youth.repository.YouthPlayerViewItem

/**
 * T16 青训学院页 UI 状态（V0.1 08 §二 + T16 方案 §六）。
 *
 * 4 个模块：
 * - 概览卡：青训等级 / 设施 / 招募 / 声望 / 风格 / 预算
 * - U18 / U21 球员列表
 * - 青训报告：最近事件
 * - 投资 / 风格切换
 */
data class YouthUiState(
    val isLoading: Boolean = false,
    val academy: YouthAcademyViewItem? = null,
    val u18Players: List<YouthPlayerViewItem> = emptyList(),
    val u21Players: List<YouthPlayerViewItem> = emptyList(),
    val recentEvents: List<YouthEventEntity> = emptyList(),
    val statistics: YouthAcademyStatistics = YouthAcademyStatistics(
        u18Count = 0,
        u21Count = 0,
        geniusCount = 0,
        highPotentialCount = 0,
        firstTeamPromotedCount = 0,
        totalInvestment = 0
    ),
    val clubBalance: Int = 0,
    val selectedPlayer: YouthPlayerViewItem? = null,
    val message: String? = null
)

/**
 * 青训学院页 Tab 类型。
 */
enum class YouthTab(val title: String) {
    OVERVIEW("学院概览"),
    U18("U18 球员"),
    U21("U21 球员"),
    EVENTS("青训报告"),
    INVEST("投资风格")
}
