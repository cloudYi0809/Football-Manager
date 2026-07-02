package com.greendynasty.football.media.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.media.model.NewsCategory
import com.greendynasty.football.media.repository.MediaRepository
import com.greendynasty.football.media.ui.state.MediaTab
import com.greendynasty.football.media.ui.state.MediaUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T24 媒体页 ViewModel（V0.2 + T24 任务要求 + 实现方案 §六）。
 *
 * 持有 [MediaRepository] 实例，暴露 4 个 Tab 数据：
 * 1. 新闻列表：最近新闻 + 未读数 + 分类过滤（Flow 订阅）
 * 2. 新闻详情：单条新闻完整内容
 * 3. 采访：当前采访 + 问题 + 选项
 * 4. 舆论仪表盘：舆论值 + 等级 + 球迷支持度修正（Flow 订阅）
 *
 * UI 状态：[MediaUiState]，5 种完备状态（对齐 DressingRoomUiState 模式）。
 *
 * 用户交互：
 * - [switchTab]：切换 Tab
 * - [selectNews]：查看新闻详情
 * - [setNewsFilter]：设置分类过滤器
 * - [markAllNewsAsRead]：标记全部新闻已读
 * - [startInterview]：开始采访
 * - [answerQuestion]：回答当前采访问题
 * - [skipInterview]：跳过采访
 * - [dismissImpact]：关闭影响弹窗
 * - [consumeMessage]：消费 Snackbar 消息
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager / MediaRepository
 */
class MediaViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)

    private val repository = MediaRepository(databaseManager)

    /** UI 状态 */
    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Loading)
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(MediaTab.NEWS)
    val currentTab: StateFlow<MediaTab> = _currentTab.asStateFlow()

    init {
        loadMediaOverview()
    }

    // ==================== 数据加载 ====================

    /**
     * 加载媒体概览数据（首次进入页面时调用）。
     *
     * 流程：
     * 1. 从 SaveManager 获取当前存档上下文（saveId / clubId / currentDate）
     * 2. 若未打开存档 → Locked
     * 3. 计算媒体快照
     * 4. 订阅 Flow（最近新闻 / 未读数 / 活跃采访 / 舆论值）
     */
    fun loadMediaOverview() {
        viewModelScope.launch {
            _uiState.value = MediaUiState.Loading
            try {
                val saveInfo = saveManager.getCurrentSaveInfo()
                if (saveInfo == null) {
                    _uiState.value = MediaUiState.Locked()
                    return@launch
                }

                val currentDate = runCatching {
                    LocalDate.parse(saveInfo.gameDate)
                }.getOrDefault(LocalDate.now())
                val clubId = saveInfo.managerClubId
                val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1

                // 初始化 Normal 状态（先加载快照）
                val snapshot = try {
                    repository.getSnapshot(saveId, clubId)
                } catch (_: Exception) { null }

                val opinionLevel = repository.opinionManager.getOpinionLevel(snapshot?.opinion)
                val fanModifier = repository.opinionManager.getFanSupportModifier(snapshot?.opinion)

                _uiState.value = MediaUiState.Normal(
                    saveId = saveId,
                    clubId = clubId,
                    currentDate = currentDate,
                    opinion = snapshot?.opinion,
                    opinionLevel = opinionLevel,
                    fanSupportModifier = fanModifier,
                    recentNews = snapshot?.recentNews ?: emptyList(),
                    unreadCount = snapshot?.unreadCount ?: 0,
                    activeInterviews = snapshot?.pendingInterviews ?: emptyList(),
                    currentInterview = snapshot?.currentInterview
                )

                // 订阅 Flow
                observeRecentNews(saveId)
                observeUnreadCount(saveId)
                observeActiveInterviews(saveId, clubId)
                observeOpinion(saveId, clubId)
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error("加载媒体数据失败：${e.message}")
            }
        }
    }

    /** 订阅最近新闻 Flow。 */
    private fun observeRecentNews(saveId: Int) {
        viewModelScope.launch {
            try {
                repository.observeRecentNews(saveId, 50).collectLatest { news ->
                    updateNormal {
                        val filtered = if (it.filterCategory != null) {
                            news.filter { n -> n.category == it.filterCategory }
                        } else {
                            news
                        }
                        it.copy(recentNews = filtered)
                    }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅未读新闻数 Flow。 */
    private fun observeUnreadCount(saveId: Int) {
        viewModelScope.launch {
            try {
                repository.observeUnreadCount(saveId).collectLatest { count ->
                    updateNormal { it.copy(unreadCount = count) }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅活跃采访 Flow。 */
    private fun observeActiveInterviews(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeActiveInterviews(saveId, clubId).collectLatest { interviews ->
                    updateNormal {
                        it.copy(
                            activeInterviews = interviews,
                            currentInterview = interviews.firstOrNull { i -> i.status == "in_progress" }
                        )
                    }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    /** 订阅舆论值 Flow。 */
    private fun observeOpinion(saveId: Int, clubId: Int) {
        viewModelScope.launch {
            try {
                repository.observeOpinion(saveId, clubId).collectLatest { opinion ->
                    val level = repository.opinionManager.getOpinionLevel(opinion)
                    val modifier = repository.opinionManager.getFanSupportModifier(opinion)
                    updateNormal {
                        it.copy(
                            opinion = opinion,
                            opinionLevel = level,
                            fanSupportModifier = modifier
                        )
                    }
                }
            } catch (_: Exception) { /* 静默失败 */ }
        }
    }

    // ==================== 用户交互 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: MediaTab) {
        _currentTab.value = tab
    }

    /**
     * 查看新闻详情。
     */
    fun selectNews(newsId: Long) {
        viewModelScope.launch {
            val normal = _uiState.value as? MediaUiState.Normal ?: return@launch
            try {
                repository.markNewsAsRead(newsId)
                val news = repository.getNews(newsId)
                updateNormal {
                    it.copy(
                        selectedNewsId = newsId,
                        selectedNews = news,
                        message = null
                    )
                }
                switchTab(MediaTab.NEWS_DETAIL)
            } catch (e: Exception) {
                updateNormal { it.copy(message = "查看新闻失败：${e.message}") }
            }
        }
    }

    /** 设置新闻分类过滤器。 */
    fun setNewsFilter(category: NewsCategory?) {
        updateNormal {
            val filter = category?.name
            val filtered = if (filter != null) {
                it.recentNews.filter { n -> n.category == filter }
            } else {
                it.recentNews
            }
            it.copy(filterCategory = filter, recentNews = filtered)
        }
    }

    /** 标记全部新闻已读。 */
    fun markAllNewsAsRead() {
        viewModelScope.launch {
            val normal = _uiState.value as? MediaUiState.Normal ?: return@launch
            try {
                repository.markAllNewsAsRead(normal.saveId)
                updateNormal { it.copy(unreadCount = 0, message = "已标记全部新闻为已读") }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "标记已读失败：${e.message}") }
            }
        }
    }

    /**
     * 开始采访（pending → in_progress，加载第一个问题）。
     */
    fun startInterview(interviewId: Long) {
        viewModelScope.launch {
            val normal = _uiState.value as? MediaUiState.Normal ?: return@launch
            try {
                val result = repository.interviewService.startInterview(interviewId)
                if (result == null) {
                    updateNormal { it.copy(message = "采访无法开始") }
                    return@launch
                }
                val (interview, question) = result
                updateNormal {
                    it.copy(
                        currentInterview = interview,
                        currentQuestion = question,
                        currentOptions = question.options,
                        message = null
                    )
                }
                switchTab(MediaTab.INTERVIEW)
            } catch (e: Exception) {
                updateNormal { it.copy(message = "开始采访失败：${e.message}") }
            }
        }
    }

    /**
     * 回答当前采访问题。
     */
    fun answerQuestion(optionId: String) {
        viewModelScope.launch {
            val normal = _uiState.value as? MediaUiState.Normal ?: return@launch
            val interview = normal.currentInterview ?: return@launch
            try {
                val result = repository.interviewService.answerQuestion(
                    interview.interviewId, optionId, normal.currentDate
                )
                if (result == null) {
                    updateNormal { it.copy(message = "回答失败") }
                    return@launch
                }

                // 应用舆论值影响（V1 简化：仅应用舆论维度，士气/球迷/董事会由其他模块监听）
                repository.applyInterviewImpact(
                    normal.saveId, normal.clubId, result.impact, normal.currentDate
                )

                // 加载下一题或完成采访
                val nextQuestion = if (!result.isCompleted) {
                    repository.interviewService.getCurrentQuestion(result.conference)
                } else {
                    null
                }

                updateNormal {
                    it.copy(
                        currentInterview = result.conference,
                        currentQuestion = nextQuestion,
                        currentOptions = nextQuestion?.options ?: emptyList(),
                        lastImpact = result.impact,
                        message = if (result.isCompleted) "采访已完成" else null
                    )
                }
            } catch (e: Exception) {
                updateNormal { it.copy(message = "回答失败：${e.message}") }
            }
        }
    }

    /**
     * 跳过采访。
     */
    fun skipInterview() {
        viewModelScope.launch {
            val normal = _uiState.value as? MediaUiState.Normal ?: return@launch
            val interview = normal.currentInterview ?: return@launch
            try {
                val impact = repository.interviewService.skipInterview(
                    interview.interviewId, normal.currentDate
                )
                if (impact != null) {
                    repository.applyInterviewImpact(
                        normal.saveId, normal.clubId, impact, normal.currentDate
                    )
                }
                updateNormal {
                    it.copy(
                        currentInterview = null,
                        currentQuestion = null,
                        currentOptions = emptyList(),
                        lastImpact = impact,
                        message = "已跳过采访（舆论 ${impact?.opinionDelta ?: 0}）"
                    )
                }
                switchTab(MediaTab.NEWS)
            } catch (e: Exception) {
                updateNormal { it.copy(message = "跳过采访失败：${e.message}") }
            }
        }
    }

    /** 关闭影响弹窗。 */
    fun dismissImpact() {
        updateNormal { it.copy(lastImpact = null) }
    }

    /** 消费消息（避免 Snackbar 重复显示）。 */
    fun consumeMessage() {
        updateNormal { it.copy(message = null) }
    }

    // ==================== 内部工具 ====================

    /** 安全更新 Normal 状态（非 Normal 状态时忽略）。 */
    private fun updateNormal(transform: (MediaUiState.Normal) -> MediaUiState.Normal) {
        val current = _uiState.value
        if (current is MediaUiState.Normal) {
            _uiState.value = transform(current)
        }
    }

    companion object {
        /**
         * 创建 [MediaViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档上下文。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MediaViewModel(app) as T
                }
            }
    }
}
