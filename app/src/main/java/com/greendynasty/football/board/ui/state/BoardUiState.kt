package com.greendynasty.football.board.ui.state

import com.greendynasty.football.board.model.BoardConfidenceEntity
import com.greendynasty.football.board.model.BoardEventEntity
import com.greendynasty.football.board.model.BoardExpectationSummary
import com.greendynasty.football.board.model.BoardFeedback
import com.greendynasty.football.board.model.BoardSatisfactionEntity
import com.greendynasty.football.board.model.BudgetRequestEntity
import com.greendynasty.football.board.model.DismissalDecision
import com.greendynasty.football.board.model.LongTermGoalEntity
import com.greendynasty.football.board.model.ObjectiveProgress
import com.greendynasty.football.board.model.SeasonTargetEntity
import java.time.LocalDate

/**
 * T22 董事会页 UI 状态（V0.2 11 §四 + T22 方案 §六）。
 *
 * 5 种完备状态（对齐 EconomyUiState 模式）：
 * - [Loading]：首次加载中
 * - [Locked]：未打开存档
 * - [Empty]：暂无数据（如赛季尚未开始）
 * - [Error]：加载失败
 * - [Normal]：正常展示，含 5 个 Tab 数据 + 解雇警告
 *
 * 5 个 Tab（[BoardTab]）：
 * 1. 赛季目标：当前赛季 5 类目标 + 实时进度
 * 2. 长期目标：3 年/5 年规划列表
 * 3. 满意度：8 因子分项 + 历史曲线
 * 4. 预算申请：申请记录 + 提交表单
 * 5. 董事会评价：反馈文案 + 最近事件
 */
sealed interface BoardUiState {

    /** 加载中 */
    object Loading : BoardUiState

    /** 未打开存档 */
    data class Locked(val reason: String = "请先打开存档") : BoardUiState

    /** 暂无数据 */
    data class Empty(val reason: String = "暂无董事会数据") : BoardUiState

    /** 加载失败 */
    data class Error(val message: String) : BoardUiState

    /**
     * 正常状态：含全部 5 个 Tab 数据。
     *
     * @property saveId 存档 ID
     * @property clubId 俱乐部 ID
     * @property seasonId 赛季 ID
     * @property currentDate 当前游戏日期
     * @property expectation 董事会期望摘要
     * @property seasonTarget 当前赛季目标
     * @property objectiveProgress 赛季中目标实时进度
     * @property longTermGoals 长期目标列表
     * @property latestSatisfaction 最近满意度快照
     * @property satisfactionHistory 满意度历史曲线（最近 12 个月）
     * @property confidence 董事会信心值
     * @property budgetRequests 预算申请记录
     * @property recentEvents 最近董事会事件
     * @property feedback 董事会反馈（满意/一般/不满/解雇警告）
     * @property dismissalWarning 解雇警告（非 null 时弹窗提示）
     * @property message 一次性消息（Snackbar）
     */
    data class Normal(
        val saveId: Int,
        val clubId: Int,
        val seasonId: Int,
        val currentDate: LocalDate,
        val expectation: BoardExpectationSummary? = null,
        val seasonTarget: SeasonTargetEntity? = null,
        val objectiveProgress: List<ObjectiveProgress> = emptyList(),
        val longTermGoals: List<LongTermGoalEntity> = emptyList(),
        val latestSatisfaction: BoardSatisfactionEntity? = null,
        val satisfactionHistory: List<BoardSatisfactionEntity> = emptyList(),
        val confidence: BoardConfidenceEntity? = null,
        val budgetRequests: List<BudgetRequestEntity> = emptyList(),
        val recentEvents: List<BoardEventEntity> = emptyList(),
        val feedback: BoardFeedback? = null,
        val dismissalWarning: DismissalDecision? = null,
        val message: String? = null
    ) : BoardUiState
}

/**
 * 董事会页 Tab 类型（V0.2 + T22 方案 §六）。
 */
enum class BoardTab(val title: String) {
    /** 赛季目标：5 类目标 + 实时进度 */
    SEASON_TARGET("赛季目标"),

    /** 长期目标：3 年/5 年规划 */
    LONG_TERM("长期目标"),

    /** 满意度：8 因子分项 + 历史曲线 */
    SATISFACTION("满意度"),

    /** 预算申请：6 类申请 + 审批记录 */
    BUDGET("预算申请"),

    /** 董事会评价：反馈 + 最近事件 */
    FEEDBACK("董事会评价")
}
