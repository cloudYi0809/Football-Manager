package com.greendynasty.football.data.save.management.model

/**
 * 创建存档操作结果
 *
 * 两种结果：
 * - [Success]：创建成功，返回新存档的 saveId
 * - [Failure]：创建失败（存档数量上限、存储空间不足、初始化异常等）
 */
sealed class SaveCreateResult {

    /**
     * 创建成功
     *
     * @param saveId 新创建的存档 UUID
     */
    data class Success(val saveId: String) : SaveCreateResult()

    /**
     * 创建失败
     *
     * @param error 错误描述
     * @param cause 原始异常（可选，用于日志）
     */
    data class Failure(val error: String, val cause: Throwable? = null) : SaveCreateResult()
}
