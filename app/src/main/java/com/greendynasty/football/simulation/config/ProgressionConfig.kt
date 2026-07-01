package com.greendynasty.football.simulation.config

import java.time.LocalDate

/**
 * 推进模块配置参数（T07 方案 §十 progression_config.json）
 *
 * 所有推进相关参数集中配置，便于调参与 V0.2 性能优化。
 * 参数值依据 V0.2 算法文档，只调参不改架构。
 */
data class ProgressionConfig(

    // ===== 性能 =====
    /** 性能告警阈值（毫秒），超过则记录告警 */
    val perfWarningMs: Long = 2500L,
    /** 性能临界阈值（毫秒），P95 不超过此值 */
    val perfCriticalMs: Long = 3000L,
    /** 自动保存间隔（天），每 N 天自动 checkpoint */
    val autoSaveIntervalDays: Int = 7,
    /** checkpoint 间隔（天） */
    val checkpointIntervalDays: Int = 30,
    /** 是否启用每日推进后自动保存 */
    val autoSaveEnabled: Boolean = true,

    // ===== 活跃范围 =====
    /** 默认活跃联赛 ID 列表（玩家所在联赛 + 主要关联联赛） */
    val activeLeagues: List<Int> = listOf(1),
    /** 浅度模拟的顶级联赛 ID 列表 */
    val topLeaguesLight: List<Int> = listOf(2, 3, 4, 5),
    /** 欧冠参赛时是否动态扩展活跃范围 */
    val dynamicExpansionOnChampionsLeague: Boolean = true,

    // ===== 每日任务参数 =====
    val trainingEnabled: Boolean = true,
    /** 每日体能恢复量（非比赛日） */
    val conditionRecoveryPerDay: Int = 5,
    /** 比赛日体能消耗 */
    val conditionLossOnMatch: Int = 25,
    /** 士气自然衰减率（每日） */
    val moraleDecayRate: Double = 0.5,
    /** 新闻生成概率（每日） */
    val newsGenerationRate: Double = 0.3,

    // ===== 每月任务参数 =====
    val wagePaymentDay: Int = 1,
    val financialSettlement: Boolean = true,
    val growthSettlement: Boolean = true,
    val boardReview: Boolean = true,

    // ===== 赛季结束参数 =====
    val forcedCheckpoint: Boolean = true,
    val archiveSeason: Boolean = true,
    /** 退役年龄阈值 */
    val retirementAgeThreshold: Int = 33,
    /** 新赛季新星刷新数量 */
    val youthRefreshCount: Int = 50,

    // ===== 回滚 =====
    val rollbackEnabled: Boolean = true,
    /** 每次推进前创建轻量 checkpoint（V1 默认关闭以提升性能） */
    val lightCheckpointOnAdvance: Boolean = false,
    /** 赛季结束创建完整 checkpoint */
    val fullCheckpointOnSeasonEnd: Boolean = true
) {
    companion object {
        /** 默认配置（V0.2 推荐值） */
        val DEFAULT = ProgressionConfig()
    }

    /**
     * 判断指定日期是否在转会窗内（V0.1 09 §转会窗）
     * - 夏窗：07-01 ~ 08-31
     * - 冬窗：01-01 ~ 01-31
     */
    fun isTransferWindowOpen(date: LocalDate): Boolean {
        val month = date.monthValue
        val day = date.dayOfMonth
        return when {
            // 夏窗 7月1日 - 8月31日
            month == 7 || month == 8 -> true
            // 冬窗 1月1日 - 1月31日
            month == 1 -> true
            else -> false
        }
    }

    /**
     * 判断是否为赛季结束日（V1 简化：5月最后一日）
     * TODO: T19 赛季归档细化，从 season 表读取实际结束日
     */
    fun isSeasonEndDate(date: LocalDate): Boolean {
        return date.monthValue == 5 && date.dayOfMonth == 31
    }
}
