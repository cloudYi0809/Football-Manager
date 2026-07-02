package com.greendynasty.football.divergence.ui.state

import com.greendynasty.football.butterfly.model.ButterflyEventCategory
import com.greendynasty.football.divergence.archive.DivergenceArchiveEntity
import com.greendynasty.football.divergence.model.DivergenceFilter
import com.greendynasty.football.divergence.model.DivergenceTimelineItem
import com.greendynasty.football.divergence.model.ImportanceLevel
import com.greendynasty.football.divergence.model.NoReplacementRecord

/**
 * T21 历史分歧时间线页 UI 状态（任务 T21.3：时间线 UI）。
 *
 * 3 个视图区域：
 * - **时间线列表**：当前赛季分歧事件（按时间倒序，支持筛选）
 * - **归档记录**：历史赛季归档的分歧记录
 * - **详情视图**：选中某事件后的影响节点详情
 *
 * @param isLoading 加载中
 * @param timelineItems 当前赛季时间线条目列表（已筛选）
 * @param archivedDivergences 历史归档记录
 * @param noReplacementRecords "无重大替代"记录列表
 * @param selectedTimelineItem 选中的时间线条目（详情视图）
 * @param selectedArchive 选中的归档记录（详情视图）
 * @param filter 当前筛选条件
 * @param totalCount 总事件数（未筛选）
 * @param noReplacementCount 无重大替代记录数
 * @param withReplacementCount 有重大替代记录数
 * @param message 操作反馈消息
 */
data class DivergenceUiState(
    val isLoading: Boolean = false,
    val timelineItems: List<DivergenceTimelineItem> = emptyList(),
    val archivedDivergences: List<DivergenceArchiveEntity> = emptyList(),
    val noReplacementRecords: List<NoReplacementRecord> = emptyList(),
    val selectedTimelineItem: DivergenceTimelineItem? = null,
    val selectedArchive: DivergenceArchiveEntity? = null,
    val filter: DivergenceFilter = DivergenceFilter.NONE,
    val totalCount: Int = 0,
    val noReplacementCount: Int = 0,
    val withReplacementCount: Int = 0,
    val message: String? = null
)

/**
 * 时间线页 Tab 类型。
 */
enum class DivergenceTab(val title: String) {
    /** 当前赛季分歧时间线。 */
    TIMELINE("时间线"),
    /** 历史归档记录。 */
    ARCHIVE("归档"),
    /** "无重大替代"记录。 */
    NO_REPLACEMENT("无替代"),
    /** 事件详情。 */
    DETAIL("详情")
}

/**
 * 筛选选项（用于 UI 筛选条展示）。
 */
data class FilterOption(
    val label: String,
    val category: ButterflyEventCategory? = null,
    val importanceLevel: ImportanceLevel? = null
)

/**
 * 预定义筛选选项列表（用于 UI 筛选条）。
 */
val FILTER_OPTIONS = listOf(
    FilterOption("全部"),
    FilterOption("转会类", ButterflyEventCategory.TRANSFER),
    FilterOption("比赛类", ButterflyEventCategory.MATCH),
    FilterOption("伤病类", ButterflyEventCategory.INJURY),
    FilterOption("荣誉类", ButterflyEventCategory.HONOR),
    FilterOption("退役类", ButterflyEventCategory.RETIREMENT),
    FilterOption("高重要度", importanceLevel = ImportanceLevel.HIGH),
    FilterOption("中重要度", importanceLevel = ImportanceLevel.MEDIUM),
    FilterOption("低重要度", importanceLevel = ImportanceLevel.LOW)
)
