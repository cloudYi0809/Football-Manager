package com.greendynasty.football.media.ui.state

import com.greendynasty.football.media.model.AnswerOption
import com.greendynasty.football.media.model.InterviewQuestion
import com.greendynasty.football.media.model.MediaImpact
import com.greendynasty.football.media.model.MediaInterviewEntity
import com.greendynasty.football.media.model.MediaNewsEntity
import com.greendynasty.football.media.model.MediaOpinionEntity
import com.greendynasty.football.media.model.OpinionLevel
import java.time.LocalDate

/**
 * T24 媒体页 UI 状态（V0.2 + T24 任务要求 + 实现方案 §六 UI 结构）。
 *
 * 5 种完备状态（对齐 DressingRoomUiState / BoardUiState 模式）：
 * - [Loading]：首次加载中
 * - [Locked]：未打开存档
 * - [Empty]：暂无数据
 * - [Error]：加载失败
 * - [Normal]：正常展示，含 4 个 Tab 数据
 *
 * 4 个 Tab（[MediaTab]）：
 * 1. 新闻列表：最近新闻 + 未读数 + 分类过滤
 * 2. 新闻详情：单条新闻完整内容
 * 3. 采访：当前采访 + 问题 + 选项
 * 4. 舆论仪表盘：舆论值 + 等级 + 历史峰值/谷值
 */
sealed interface MediaUiState {

    /** 加载中 */
    object Loading : MediaUiState

    /** 未打开存档 */
    data class Locked(val reason: String = "请先打开存档") : MediaUiState

    /** 暂无数据 */
    data class Empty(val reason: String = "暂无媒体数据") : MediaUiState

    /** 加载失败 */
    data class Error(val message: String) : MediaUiState

    /**
     * 正常状态：含全部 4 个 Tab 数据。
     *
     * @property saveId 存档 ID
     * @property clubId 俱乐部 ID
     * @property currentDate 当前游戏日期
     * @property opinion 当前舆论值
     * @property opinionLevel 舆论值等级
     * @property fanSupportModifier 球迷支持度修正
     * @property recentNews 最近新闻列表
     * @property unreadCount 未读新闻数
     * @property activeInterviews 活跃采访列表
     * @property currentInterview 当前进行中的采访（可空）
     * @property currentQuestion 当前采访的当前问题（可空）
     * @property currentOptions 当前问题的选项列表
     * @property lastImpact 最近一次回答的影响（用于弹窗展示）
     * @property selectedNewsId 选中的新闻 ID（详情视图）
     * @property selectedNews 选中的新闻详情
     * @property filterCategory 新闻分类过滤器（null 表示全部）
     * @property message 一次性消息（Snackbar）
     */
    data class Normal(
        val saveId: Int,
        val clubId: Int,
        val currentDate: LocalDate,
        val opinion: MediaOpinionEntity? = null,
        val opinionLevel: OpinionLevel = OpinionLevel.NEUTRAL,
        val fanSupportModifier: Int = 0,
        val recentNews: List<MediaNewsEntity> = emptyList(),
        val unreadCount: Int = 0,
        val activeInterviews: List<MediaInterviewEntity> = emptyList(),
        val currentInterview: MediaInterviewEntity? = null,
        val currentQuestion: InterviewQuestion? = null,
        val currentOptions: List<AnswerOption> = emptyList(),
        val lastImpact: MediaImpact? = null,
        val selectedNewsId: Long? = null,
        val selectedNews: MediaNewsEntity? = null,
        val filterCategory: String? = null,
        val message: String? = null
    ) : MediaUiState
}

/**
 * 媒体页 Tab 类型（V0.2 + T24 方案 §六）。
 */
enum class MediaTab(val title: String) {
    /** 新闻列表 */
    NEWS("新闻"),

    /** 新闻详情 */
    NEWS_DETAIL("详情"),

    /** 采访页 */
    INTERVIEW("采访"),

    /** 舆论仪表盘 */
    OPINION("舆论")
}
