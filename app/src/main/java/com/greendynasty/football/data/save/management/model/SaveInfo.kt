package com.greendynasty.football.data.save.management.model

import com.greendynasty.football.data.save.management.SaveState

/**
 * 当前加载存档的运行时信息
 *
 * 用于 UI 展示当前游戏状态（存档名、游戏日期、赛季、执教俱乐部、存档状态）。
 * 与 [SaveMeta] 不同，[SaveInfo] 反映的是"当前正在玩的存档"的实时状态。
 *
 * @param saveId 存档唯一标识（UUID）
 * @param saveName 存档名称（玩家可见）
 * @param gameDate 游戏内当前日期（如 2003-05-30）
 * @param currentSeason 当前赛季 ID
 * @param managerClubId 玩家执教的俱乐部 ID
 * @param currentState 当前存档状态（LOADED/SAVING/SWITCHING 等）
 */
data class SaveInfo(
    val saveId: String,
    val saveName: String,
    val gameDate: String,
    val currentSeason: Int,
    val managerClubId: Int,
    val currentState: SaveState
)
