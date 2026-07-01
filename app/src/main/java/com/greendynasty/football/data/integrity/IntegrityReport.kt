package com.greendynasty.football.data.integrity

/**
 * 存档完整性检测报告
 *
 * 由 [SaveIntegrityChecker] 生成，包含检测出的所有问题列表。
 * 调用方可通过 [isSevere] / [hasMinorIssues] / [hasMediumIssues] 判断严重程度。
 *
 * 参考 V0.2 §八 存档完整性检测。
 */
data class IntegrityReport(
    /** 检测出的所有问题列表 */
    val issues: List<IntegrityIssue>
) {

    /** 是否存在 SEVERE 级别问题（存档损坏，需用户介入或从备份恢复） */
    fun isSevere(): Boolean = issues.any { it.severity == Severity.SEVERE }

    /** 是否存在 MEDIUM 级别问题（建议从最近 checkpoint 恢复） */
    fun hasMediumIssues(): Boolean = issues.any { it.severity == Severity.MEDIUM }

    /** 是否存在 MINOR 级别问题（可自动修复） */
    fun hasMinorIssues(): Boolean = issues.any { it.severity == Severity.MINOR }

    /** 是否完全无问题 */
    fun isOk(): Boolean = issues.isEmpty()

    /** 问题汇总文本（用于日志或用户提示） */
    val summary: String
        get() = if (issues.isEmpty()) {
            "OK"
        } else {
            issues.joinToString("; ") { "${it.severity}: ${it.description}" }
        }

    companion object {
        /** 空报告（无问题） */
        val OK = IntegrityReport(emptyList())
    }
}

/**
 * 单条完整性问题
 *
 * @param severity 严重等级
 * @param description 问题描述（中文）
 * @param table 相关表名（可选，用于定位）
 */
data class IntegrityIssue(
    val severity: Severity,
    val description: String,
    val table: String? = null
)

/**
 * 问题严重等级
 */
enum class Severity {
    /** 轻微：可自动修复，不影响游戏继续运行 */
    MINOR,

    /** 中等：建议从 checkpoint 恢复，否则可能产生数据偏差 */
    MEDIUM,

    /** 严重：存档损坏，必须从备份恢复或导出可救援数据 */
    SEVERE
}
