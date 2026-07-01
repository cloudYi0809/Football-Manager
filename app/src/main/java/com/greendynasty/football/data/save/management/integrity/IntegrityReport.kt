package com.greendynasty.football.data.save.management.integrity

/**
 * 存档完整性检测报告（T03）
 *
 * 包含存档 ID、每项检测结果与总体状态。
 *
 * @param saveId 存档唯一标识
 * @param checks 各项检测的结果列表
 * @param overallStatus 总体状态
 */
data class IntegrityReport(
    val saveId: String,
    val checks: List<IntegrityCheck>,
    val overallStatus: IntegrityStatus
) {
    /** 是否全部检测通过 */
    val allPassed: Boolean get() = checks.all { it.passed }

    /** 是否存在严重损坏（CORRUPTED / UNREADABLE） */
    val isSevere: Boolean
        get() = overallStatus == IntegrityStatus.CORRUPTED ||
            overallStatus == IntegrityStatus.UNREADABLE

    /** 摘要描述 */
    val summary: String
        get() = checks.joinToString("; ") { "${it.checkName}=${if (it.passed) "OK" else "FAIL"}" }
}

/**
 * 单项检测结果
 *
 * @param checkName 检测项名称
 * @param passed 是否通过
 * @param message 检测说明
 */
data class IntegrityCheck(
    val checkName: String,
    val passed: Boolean,
    val message: String
)

/**
 * 完整性总体状态
 */
enum class IntegrityStatus {
    /** 全部通过 */
    OK,
    /** 存在告警但存档可用 */
    WARNING,
    /** 存档损坏（关键检测失败） */
    CORRUPTED,
    /** 存档不可读（无法打开） */
    UNREADABLE
}
