package com.greendynasty.football.data.save.management.model

/**
 * 保存存档操作结果
 *
 * 三种结果：
 * - [Success]：保存成功
 * - [Failure]：保存失败（保留原数据，未触发回滚）
 * - [RolledBack]：保存失败且已回滚到 checkpoint（数据恢复到保存前状态）
 */
sealed class SaveResult {

    /** 保存成功 */
    object Success : SaveResult()

    /**
     * 保存失败
     *
     * @param error 错误描述
     * @param cause 原始异常（可选，用于日志）
     */
    data class Failure(val error: String, val cause: Throwable? = null) : SaveResult()

    /**
     * 保存失败已回滚
     *
     * 保存过程中发生异常，已通过 checkpoint 回滚到保存前的状态。
     *
     * @param checkpointId 回滚使用的 checkpoint ID
     */
    data class RolledBack(val checkpointId: String) : SaveResult()
}
