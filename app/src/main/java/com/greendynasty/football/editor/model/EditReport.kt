package com.greendynasty.football.editor.model

/**
 * 编辑结果报告。
 *
 * 由 [com.greendynasty.football.editor.repository.EditorRepository.save] / [com.greendynasty.football.editor.repository.EditorRepository.undo]
 * 等方法返回，承载成功/失败标志、计数、错误与警告列表。
 */
data class EditReport(
    var success: Boolean = false,
    var insertedCount: Int = 0,
    var updatedCount: Int = 0,
    var deletedCount: Int = 0,
    val errors: MutableList<String> = mutableListOf(),
    val warnings: MutableList<String> = mutableListOf()
) {
    /** 标记失败并附加错误信息。 */
    fun fail(message: String): EditReport {
        success = false
        errors.add(message)
        return this
    }

    /** 标记成功。 */
    fun ok(): EditReport {
        success = true
        return this
    }

    /** 摘要文本。 */
    fun summary(): String = "成功=$success, 新增=$insertedCount, 更新=$updatedCount, 删除=$deletedCount" +
        (if (errors.isNotEmpty()) ", 错误=${errors.size}" else "") +
        (if (warnings.isNotEmpty()) ", 警告=${warnings.size}" else "")
}
