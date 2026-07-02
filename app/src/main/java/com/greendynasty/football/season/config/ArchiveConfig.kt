package com.greendynasty.football.season.config

import kotlinx.serialization.Serializable

/**
 * T19 赛季归档配置参数（V0.2 §七 archive_config.json）
 *
 * 严格依据 V0.2 `09_长程性能_存档_版本迁移.md` §七：
 * - 压缩策略：仅保留比分 + 进球 + 红黄牌 + Top N 评分
 * - 清理策略：6 类数据按各自保留期清理
 * - 体积红线：20 年存档 ≤80MB，达到 85% 预警
 * - VACUUM：归档后自动执行
 *
 * 仅调参不改架构，所有参数配置化。
 */
@Serializable
data class ArchiveConfig(

    /** 压缩策略 */
    val compression: CompressionConfig = CompressionConfig(),

    /** 清理策略 */
    val cleanup: CleanupConfig = CleanupConfig(),

    /** 体积监控 */
    val size: SizeConfig = SizeConfig(),

    /** VACUUM 配置 */
    val vacuum: VacuumConfig = VacuumConfig()
) {

    companion object {
        /** 默认配置（V0.2 推荐值） */
        val DEFAULT = ArchiveConfig()
    }
}

/** 压缩策略：仅保留比分 + 进球 + 红黄牌 + Top N 评分 */
@Serializable
data class CompressionConfig(
    val keepGoals: Boolean = true,
    val keepCards: Boolean = true,
    val keepTopRatingsCount: Int = 5,
    val deleteDetailedEvents: Boolean = true
)

/** 清理策略：6 类数据按各自保留期清理 */
@Serializable
data class CleanupConfig(
    /** 新闻保留最近 N 赛季 */
    val newsKeepSeasons: Int = 1,
    /** 待办保留最近 N 月 */
    val todoKeepMonths: Int = 3,
    /** 球探报告保留最近 N 月 */
    val scoutReportKeepMonths: Int = 6,
    /** 转会报价保留最近 N 月 */
    val transferOfferKeepMonths: Int = 6,
    /** AI 决策日志保留最近 N 赛季 */
    val aiLogKeepSeasons: Int = 2,
    /** 性能日志保留最近 N 赛季 */
    val perfLogKeepSeasons: Int = 1
)

/** 体积监控配置 */
@Serializable
data class SizeConfig(
    /** 存档体积上限（MB），20 年铁律 ≤80MB */
    val maxSaveSizeMb: Double = 80.0,
    /** 体积预警阈值比例（达到即预警） */
    val warningThresholdRatio: Double = 0.85
)

/** VACUUM 配置 */
@Serializable
data class VacuumConfig(
    val enabled: Boolean = true,
    val runAfterArchive: Boolean = true
)
