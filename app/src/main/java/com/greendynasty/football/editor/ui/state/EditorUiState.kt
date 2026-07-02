package com.greendynasty.football.editor.ui.state

import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.editor.model.EditOperation
import com.greendynasty.football.editor.model.EditableClub
import com.greendynasty.football.editor.model.EditableMatch
import com.greendynasty.football.editor.model.EditablePlayer

/**
 * T25 数据编辑器 UI 状态（V0.2 + T25 任务要求 + 实现方案 §六 UI 结构）。
 *
 * 5 种完备状态（对齐 MediaUiState / DressingRoomUiState 模式）：
 * - [Loading]：首次加载中
 * - [Locked]：history.db 未初始化
 * - [Empty]：暂无数据（如球员/俱乐部/比赛列表为空）
 * - [Error]：加载失败
 * - [Normal]：正常展示，含 4 个 Tab 数据
 *
 * 4 个 Tab（[EditorTab]）：
 * 1. 球员编辑：球员列表 + 选中球员草稿 + 属性编辑
 * 2. 俱乐部编辑：俱乐部列表 + 选中俱乐部草稿
 * 3. 赛程编辑：比赛列表 + 选中比赛草稿
 * 4. 变更历史：操作历史 + 撤销/重做按钮
 */
sealed interface EditorUiState {

    /** 加载中 */
    object Loading : EditorUiState

    /** history.db 未初始化 */
    data class Locked(val reason: String = "history.db 未初始化，请先启动应用") : EditorUiState

    /** 暂无数据 */
    data class Empty(val reason: String = "暂无数据") : EditorUiState

    /** 加载失败 */
    data class Error(val message: String) : EditorUiState

    /**
     * 正常状态：含全部 4 个 Tab 数据。
     *
     * @property players 球员列表（按 ID 排序）
     * @property clubs 俱乐部列表（按名称排序）
     * @property matches 比赛列表（按日期排序）
     * @property currentEditablePlayer 当前编辑的球员草稿（可空）
     * @property currentEditableClub 当前编辑的俱乐部草稿（可空）
     * @property currentEditableMatch 当前编辑的比赛草稿（可空）
     * @property history 变更历史（最新在前）
     * @property undoableCount 可撤销步数
     * @property redoableCount 可重做步数
     * @property lastReport 最近一次保存结果报告（用于 Snackbar）
     * @property message 一次性消息（Snackbar）
     */
    data class Normal(
        val players: List<PlayerEntity> = emptyList(),
        val clubs: List<ClubEntity> = emptyList(),
        val matches: List<MatchEntity> = emptyList(),
        val currentEditablePlayer: EditablePlayer? = null,
        val currentEditableClub: EditableClub? = null,
        val currentEditableMatch: EditableMatch? = null,
        val history: List<EditOperation> = emptyList(),
        val undoableCount: Int = 0,
        val redoableCount: Int = 0,
        val lastReport: String? = null,
        val message: String? = null
    ) : EditorUiState
}

/**
 * 编辑器 Tab 类型（V0.2 + T25 方案 §六）。
 */
enum class EditorTab(val title: String) {
    /** 球员编辑 */
    PLAYER("球员"),

    /** 俱乐部编辑 */
    CLUB("俱乐部"),

    /** 赛程编辑 */
    MATCH("赛程"),

    /** 变更历史 */
    HISTORY("历史")
}
