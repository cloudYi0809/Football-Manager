package com.greendynasty.football.dressingroom.ui.state

import com.greendynasty.football.dressingroom.model.AtmosphereEvaluation
import com.greendynasty.football.dressingroom.model.DressingRoomLeaderEntity
import com.greendynasty.football.dressingroom.model.PlayerEmotionEventEntity
import com.greendynasty.football.dressingroom.model.PlayerMoraleEntity
import java.time.LocalDate

/**
 * T23 更衣室页 UI 状态（V0.2 + T23 任务要求 §二.6 + 实现方案 §六）。
 *
 * 5 种完备状态（对齐 BoardUiState / EconomyUiState 模式）：
 * - [Loading]：首次加载中
 * - [Locked]：未打开存档
 * - [Empty]：暂无数据（如未开赛）
 * - [Error]：加载失败
 * - [Normal]：正常展示，含 4 个 Tab 数据
 *
 * 4 个 Tab（[DressingRoomTab]）：
 * 1. 氛围：氛围等级 + 稳定指数 + 球队士气 + 化学反应
 * 2. 士气：全员士气列表 + 不满球员列表
 * 3. 领袖：队长 / 副队长 / 影响力球员列表
 * 4. 事件：最近情绪事件列表
 */
sealed interface DressingRoomUiState {

    /** 加载中 */
    object Loading : DressingRoomUiState

    /** 未打开存档 */
    data class Locked(val reason: String = "请先打开存档") : DressingRoomUiState

    /** 暂无数据 */
    data class Empty(val reason: String = "暂无更衣室数据") : DressingRoomUiState

    /** 加载失败 */
    data class Error(val message: String) : DressingRoomUiState

    /**
     * 正常状态：含全部 4 个 Tab 数据。
     *
     * @property saveId 存档 ID
     * @property clubId 俱乐部 ID
     * @property seasonId 赛季 ID
     * @property currentDate 当前游戏日期
     * @property captain 当前队长
     * @property leaders 活跃领袖列表（含队长 / 副队长 / 影响力球员）
     * @property playerMorales 全员士气列表
     * @property unhappyPlayers 不满球员列表（士气 LOW / EXTREME_LOW）
     * @property teamMorale 球队平均士气 0-100
     * @property chemistryIndex 化学反应指数 0-1
     * @property atmosphere 氛围评估结果
     * @property recentEvents 最近情绪事件
     * @property message 一次性消息（Snackbar）
     */
    data class Normal(
        val saveId: Int,
        val clubId: Int,
        val seasonId: Int,
        val currentDate: LocalDate,
        val captain: DressingRoomLeaderEntity? = null,
        val leaders: List<DressingRoomLeaderEntity> = emptyList(),
        val playerMorales: List<PlayerMoraleEntity> = emptyList(),
        val unhappyPlayers: List<PlayerMoraleEntity> = emptyList(),
        val teamMorale: Int = 50,
        val chemistryIndex: Double = 0.5,
        val atmosphere: AtmosphereEvaluation? = null,
        val recentEvents: List<PlayerEmotionEventEntity> = emptyList(),
        val message: String? = null
    ) : DressingRoomUiState
}

/**
 * 更衣室页 Tab 类型（V0.2 + T23 方案 §六）。
 */
enum class DressingRoomTab(val title: String) {
    /** 氛围仪表盘：4 档氛围 + 稳定指数 + 球队士气 + 化学反应 */
    ATMOSPHERE("氛围"),

    /** 士气列表：全员士气 + 不满球员 */
    MORALE("士气"),

    /** 领袖：队长 / 副队长 / 影响力球员 */
    LEADER("领袖"),

    /** 事件：最近情绪事件 */
    EVENT("事件")
}
