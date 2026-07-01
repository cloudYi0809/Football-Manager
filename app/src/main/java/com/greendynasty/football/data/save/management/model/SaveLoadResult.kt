package com.greendynasty.football.data.save.management.model

/**
 * 加载存档操作结果
 *
 * 四种结果：
 * - [Success]：加载成功，返回当前存档运行时信息
 * - [NeedsMigration]：存档需要版本迁移（schema 版本低于当前 app 版本）
 * - [Corrupted]：存档损坏（完整性校验失败，无法继续）
 * - [Failure]：加载失败（文件不存在、IO 异常等）
 */
sealed class SaveLoadResult {

    /**
     * 加载成功
     *
     * @param saveInfo 当前存档运行时信息
     */
    data class Success(val saveInfo: SaveInfo) : SaveLoadResult()

    /**
     * 需要版本迁移
     *
     * 存档 schema 版本低于当前 app 支持版本，需要执行迁移后才能加载。
     * UI 应提示用户并触发迁移流程。
     *
     * @param fromVersion 存档当前 schema 版本
     * @param toVersion 目标 schema 版本
     */
    data class NeedsMigration(val fromVersion: Int, val toVersion: Int) : SaveLoadResult()

    /**
     * 存档损坏
     *
     * 完整性校验发现严重问题（manifest 缺失、世界状态缺失、执教俱乐部不存在等），
     * 无法直接加载，需从 checkpoint 恢复或提示用户。
     *
     * @param error 损坏原因描述
     */
    data class Corrupted(val error: String) : SaveLoadResult()

    /**
     * 加载失败
     *
     * @param error 错误描述
     * @param cause 原始异常（可选，用于日志）
     */
    data class Failure(val error: String, val cause: Throwable? = null) : SaveLoadResult()
}
