package com.greendynasty.football.data.importer.api

/**
 * 导入进度回调接口
 *
 * 由 UI 层实现，订阅 [DataImportManager] 的导入进度。
 * 所有回调在调用方线程（默认 IO 调度器）触发，UI 层自行切主线程。
 */
interface ImportProgressCallback {

    /**
     * 进度更新（每 batchSize 行触发一次，或阶段切换时触发）。
     * @param current 当前阶段已处理行数
     * @param total   当前阶段总行数（-1 表示未知）
     * @param stage   当前阶段描述（如 "导入球员 players.csv"）
     */
    fun onProgress(current: Int, total: Int, stage: String)

    /**
     * 阶段切换通知。
     * @param stage 新阶段描述（如 "导入俱乐部 clubs.csv"）
     */
    fun onStage(stage: String)

    /**
     * 单个 Importer 内部错误通知（不中止导入，仅记录与提示）。
     * @param error 错误描述
     */
    fun onError(error: String)
}
