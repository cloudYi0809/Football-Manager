package com.greendynasty.football.season.archive

import com.greendynasty.football.season.summary.SeasonSummary

/**
 * T19 赛季归档结果（V0.2 §七）
 *
 * 封装一次归档的全部产出与体积指标，由 [SeasonArchiver.archiveSeason] 返回。
 *
 * @param seasonId 归档的赛季 ID
 * @param archiveId season_archive 表自增主键
 * @param summary 赛季摘要（积分榜/射手榜/转会/财政/奖项/升降级）
 * @param compressedMatches 压缩的比赛场数
 * @param cleanedRecords 清理的记录总数
 * @param attributesWritten 回写到 history.db 的球员属性条数
 * @param sizeBeforeMb 归档前 save.db 体积（MB）
 * @param sizeAfterMb 归档后 save.db 体积（MB）
 * @param durationMs 归档总耗时（毫秒）
 */
data class ArchiveResult(
    val seasonId: Int,
    val archiveId: Int,
    val summary: SeasonSummary,
    val compressedMatches: Int,
    val cleanedRecords: Int,
    val attributesWritten: Int,
    val sizeBeforeMb: Double,
    val sizeAfterMb: Double,
    val durationMs: Long
)

/**
 * V0.2 §七 体积监控报告
 *
 * 每次归档后生成，用于体积预警与 Gate 3 验收。
 *
 * @param currentSizeMb 当前 save.db 体积（MB）
 * @param seasonsArchived 已归档赛季数
 * @param maxSizeMb 体积上限（默认 80MB，20 年铁律）
 * @param isApproachingLimit 是否接近上限（≥85%）
 * @param isOverLimit 是否超过上限
 */
data class SizeReport(
    val currentSizeMb: Double,
    val seasonsArchived: Int,
    val maxSizeMb: Double,
    val isApproachingLimit: Boolean,
    val isOverLimit: Boolean
)

/**
 * VACUUM 执行结果
 *
 * @param sizeBeforeMb VACUUM 前体积（MB）
 * @param sizeAfterMb VACUUM 后体积（MB）
 * @param savedMb 回收体积（MB）
 * @param durationMs VACUUM 耗时（毫秒）
 */
data class VacuumResult(
    val sizeBeforeMb: Double,
    val sizeAfterMb: Double,
    val savedMb: Double,
    val durationMs: Long
)
