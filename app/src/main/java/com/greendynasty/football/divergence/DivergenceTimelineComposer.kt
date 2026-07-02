package com.greendynasty.football.divergence

import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.model.ButterflyImpactNode
import com.greendynasty.football.butterfly.repository.ButterflyEventViewItem
import com.greendynasty.football.divergence.generator.DivergenceTextGenerator
import com.greendynasty.football.divergence.model.DivergenceFilter
import com.greendynasty.football.divergence.model.DivergenceLog
import com.greendynasty.football.divergence.model.DivergenceTimelineItem
import com.greendynasty.football.divergence.model.ImportanceLevel

/**
 * T21 时间线组合器（任务 T21.3：按时间顺序展示所有分歧事件，支持筛选）。
 *
 * 职责：
 * 1. 将 T20 [ButterflyEventViewItem] 列表转换为 [DivergenceTimelineItem] 列表
 *    （调用 [DivergenceTextGenerator] 生成文案 + 路径描述）
 * 2. 按时间排序（默认倒序：最新在前）
 * 3. 按 [DivergenceFilter] 筛选（分类 / 重要度 / 是否有重大替代）
 *
 * 不涉及数据库访问，纯内存组合，便于 UI 层调用。
 *
 * @param textGenerator 文案生成器
 */
class DivergenceTimelineComposer(
    private val textGenerator: DivergenceTextGenerator = DivergenceTextGenerator()
) {

    /**
     * 将蝴蝶事件视图项列表组合为分歧时间线。
     *
     * @param viewItems 蝴蝶事件视图项列表（来自 ButterflyRepository）
     * @param filter 筛选条件（默认无筛选）
     * @param ascending 是否按时间升序（true=旧→新，false=新→旧，默认 false）
     * @return 筛选并排序后的时间线条目列表
     */
    fun compose(
        viewItems: List<ButterflyEventViewItem>,
        filter: DivergenceFilter = DivergenceFilter.NONE,
        ascending: Boolean = false
    ): List<DivergenceTimelineItem> {
        // 1. 转换为 DivergenceLog + TimelineItem
        val items = viewItems.map { convertToTimelineItem(it) }

        // 2. 筛选
        val filtered = applyFilter(items, filter)

        // 3. 排序
        return sortByDate(filtered, ascending)
    }

    /**
     * 将单个蝴蝶事件视图项转换为分歧时间线条目。
     *
     * @param viewItem 蝴蝶事件视图项
     * @return 分歧时间线条目（含文案生成）
     */
    private fun convertToTimelineItem(viewItem: ButterflyEventViewItem): DivergenceTimelineItem {
        val event = viewItem.event
        val nodes = viewItem.impactNodes

        // 生成路径描述
        val originalPath = textGenerator.generateOriginalPath(
            event = event,
            expectedClubName = viewItem.expectedClubName,
            sourcePlayerName = viewItem.sourcePlayerName
        )
        val currentPath = textGenerator.generateCurrentPath(
            event = event,
            sourceClubName = viewItem.sourceClubName,
            sourcePlayerName = viewItem.sourcePlayerName
        )

        // 生成分歧文案
        val divergenceText = textGenerator.generate(
            event = event,
            impactNodes = nodes,
            sourcePlayerName = viewItem.sourcePlayerName,
            sourceClubName = viewItem.sourceClubName,
            expectedClubName = viewItem.expectedClubName
        )

        // 构建分歧日志
        val log = DivergenceLog.fromButterfly(
            event = event,
            impactNodes = nodes,
            originalPath = originalPath,
            currentPath = currentPath,
            divergenceText = divergenceText
        )

        return DivergenceTimelineItem(
            log = log,
            sourcePlayerName = viewItem.sourcePlayerName,
            sourceClubName = viewItem.sourceClubName,
            expectedClubName = viewItem.expectedClubName
        )
    }

    /**
     * 应用筛选条件。
     *
     * @param items 待筛选列表
     * @param filter 筛选条件
     * @return 筛选后的列表
     */
    private fun applyFilter(
        items: List<DivergenceTimelineItem>,
        filter: DivergenceFilter
    ): List<DivergenceTimelineItem> {
        return items.filter { item ->
            // 分类筛选
            val categoryMatch = filter.category == null || item.log.category == filter.category
            // 重要度筛选
            val importanceMatch = filter.importanceLevel == null ||
                item.importanceLevel == filter.importanceLevel
            // 重大替代筛选
            val replacementMatch = filter.onlyWithReplacement == null ||
                item.log.hasMajorReplacement == filter.onlyWithReplacement

            categoryMatch && importanceMatch && replacementMatch
        }
    }

    /**
     * 按触发日期排序。
     *
     * @param items 待排序列表
     * @param ascending 是否升序
     * @return 排序后的列表
     */
    private fun sortByDate(
        items: List<DivergenceTimelineItem>,
        ascending: Boolean
    ): List<DivergenceTimelineItem> {
        return if (ascending) {
            items.sortedBy { it.log.triggerDate }
        } else {
            items.sortedByDescending { it.log.triggerDate }
        }
    }

    /**
     * 统计时间线中的"无重大替代"记录数（任务 T21.5 辅助）。
     *
     * @param items 时间线条目列表
     * @return 无重大替代的条目数
     */
    fun countNoReplacement(items: List<DivergenceTimelineItem>): Int {
        return items.count { !it.log.hasMajorReplacement }
    }

    /**
     * 统计时间线中各分类的条目数。
     *
     * @param items 时间线条目列表
     * @return 分类 → 数量 的映射
     */
    fun countByCategory(items: List<DivergenceTimelineItem>): Map<String, Int> {
        return items.groupingBy { it.categoryDisplay }.eachCount()
    }

    /**
     * 统计时间线中各重要度等级的条目数。
     *
     * @param items 时间线条目列表
     * @return 重要度等级 → 数量 的映射
     */
    fun countByImportance(items: List<DivergenceTimelineItem>): Map<ImportanceLevel, Int> {
        return items.groupingBy { it.importanceLevel }.eachCount()
    }
}
