package com.greendynasty.football.data.save.management

import kotlinx.serialization.Serializable

/**
 * 创建新存档请求
 *
 * 由 UI 层（新游戏页面）构造，传递给 [SaveManager.createSave]。
 * 包含存档名称、剧本、执教俱乐部、模式开关、加载范围等开档参数。
 *
 * @param saveName 存档名称（玩家自定义，展示在存档列表）
 * @param scenarioId 剧本 ID（如 "2002_03"）
 * @param managerClubId 玩家执教的俱乐部 ID
 * @param modeConfig 模式开关配置（真实转会、蝴蝶效应、经济模型等）
 * @param loadRange 加载范围（联赛范围、活跃俱乐部列表）
 */
data class CreateSaveRequest(
    val saveName: String,
    val scenarioId: String,
    val managerClubId: Int,
    val modeConfig: ModeConfig = ModeConfig(),
    val loadRange: LoadRange = LoadRange()
)

/**
 * 模式开关配置
 *
 * 控制游戏的各种模拟开关，影响存档的初始状态和后续模拟行为。
 * 序列化为 JSON 存储在 save_world_state.config_json 字段。
 *
 * @param realTransfersEnabled 是否启用真实转会（按历史数据还原转会）
 * @param butterflyEffectEnabled 是否启用蝴蝶效应（玩家行为影响世界）
 * @param economicModelEnabled 是否启用经济模型（通胀、身价、工资动态变化）
 * @param youthDevelopmentEnabled 是否启用青训系统
 * @param injurySystemEnabled 是否启用伤病系统
 * @param mediaSystemEnabled 是否启用媒体系统
 */
@Serializable
data class ModeConfig(
    val realTransfersEnabled: Boolean = true,
    val butterflyEffectEnabled: Boolean = true,
    val economicModelEnabled: Boolean = true,
    val youthDevelopmentEnabled: Boolean = true,
    val injurySystemEnabled: Boolean = true,
    val mediaSystemEnabled: Boolean = false
)

/**
 * 加载范围配置
 *
 * 控制存档加载时从 history.db 拷贝哪些数据到 save.db。
 * 玩家关注范围内的联赛/俱乐部完整模拟，范围外简化模拟。
 *
 * @param leagueScope 加载的联赛范围（联赛 ID 列表，如 ["EPL", "LaLiga"]）
 * @param activeClubs 活跃俱乐部 ID 列表（完整模拟阵容与状态）
 */
@Serializable
data class LoadRange(
    val leagueScope: List<String> = emptyList(),
    val activeClubs: List<Int> = emptyList()
)
