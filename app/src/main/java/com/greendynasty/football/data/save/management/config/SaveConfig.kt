package com.greendynasty.football.data.save.management.config

/**
 * 存档系统配置参数（T03）
 *
 * 集中管理存档数量上限、单存档大小上限、自动保存触发条件、
 * checkpoint/备份保留份数与清理周期、读写超时等可调参数。
 *
 * 默认值参考 V0.2 §九 存档修复策略与 T03 实现方案。
 *
 * @property maxSaveCount 最大存档数量
 * @property maxSaveSizeMb 单存档大小上限（MB），硬限制
 * @property autoSaveOnDailyAdvance 每日推进后自动保存
 * @property autoSaveOnMatch 比赛后自动保存
 * @property checkpointKeepCount checkpoint 保留份数
 * @property backupKeepCount 备份保留份数
 * @property backupIntervalDays 备份清理周期（天），超过此天数的备份自动清理
 * @property vacuumIntervalDays VACUUM 执行周期（天）
 * @property saveTimeoutMs 存档写入超时（毫秒）
 * @property loadTimeoutMs 存档读取超时（毫秒）
 */
data class SaveConfig(
    val maxSaveCount: Int = 20,
    val maxSaveSizeMb: Int = 80,
    val autoSaveOnDailyAdvance: Boolean = true,
    val autoSaveOnMatch: Boolean = true,
    val checkpointKeepCount: Int = 3,
    val backupKeepCount: Int = 5,
    val backupIntervalDays: Int = 7,
    val vacuumIntervalDays: Int = 30,
    val saveTimeoutMs: Long = 2000,
    val loadTimeoutMs: Long = 3000
)
