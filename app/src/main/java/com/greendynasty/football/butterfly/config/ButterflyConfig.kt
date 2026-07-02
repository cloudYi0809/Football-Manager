package com.greendynasty.football.butterfly.config

/**
 * T20 蝴蝶效应配置（V0.2 06_蝴蝶效应约束算法.md §三 + §五 + §九）。
 *
 * 所有可调参数集中在此对象，便于调参与热重载。
 * 默认值严格遵循 V0.2 算法文档；调参只需修改本文件，不需改架构。
 *
 * 参数分组：
 * 1. 触发阈值（最低重要度 / 检测间隔）
 * 2. 衰减（衰减率 / 最大深度 / 最小影响阈值）
 * 3. 预算（每赛季事件上限 / 节点上限 / 单事件直接影晌上限）
 * 4. 偏差度量（权重 / 上限）
 * 5. UI 展示（可见深度 / 自动已读天数）
 *
 * V1 简化范围（严格遵循 T20 方案 §十四 V1 范围明确）：
 * - 单层影响（maxDepth=1，仅生成 depth=0 节点）
 * - 偏差度量 0-100
 * - 通知存入事件表 + UI 展示
 * - 不做完整因果链（depth>0 不递归）
 */
data class ButterflyConfig(

    // ==================== 1. 触发阈值 ====================

    /** 触发蝴蝶事件的最低重要度（V0.2 §九，默认 60）。 */
    val minTriggerImportance: Int = 60,

    /** 触发检测间隔天数（V0.2 §九，默认 1，每日推进检测）。 */
    val detectIntervalDays: Int = 1,

    // ==================== 2. 衰减 ====================

    /** 影响衰减率（V0.2 §五，默认 0.55，公式 strength = base * 0.55^depth）。 */
    val decayRate: Double = 0.55,

    /** 最大传播深度（V0.2 §五，默认 3；V1 简化仅生成 depth=0）。 */
    val maxDepth: Int = 3,

    /** 最小影响阈值，低于此值不生成影响节点（V0.2 §五，默认 15）。 */
    val minImpactThreshold: Double = 15.0,

    // ==================== 3. 预算 ====================

    /** 每赛季重大蝴蝶事件上限（V0.2 §三.3，默认 20）。 */
    val maxEventsPerSeason: Int = 20,

    /** 每赛季总影响节点上限（V0.2 §三.3，默认 200）。 */
    val maxNodesPerSeason: Int = 200,

    /** 每事件最多直接影响节点数（V0.2 §三.3，默认 5）。 */
    val maxDirectImpactsPerEvent: Int = 5,

    /** 单事件默认影响预算（V0.2 §三.3，默认 10）。 */
    val defaultImpactBudget: Int = 10,

    // ==================== 4. 偏差度量 ====================

    /** 偏差度量中事件数量权重（默认 40，0-100 制）。 */
    val deviationEventCountWeight: Int = 40,

    /** 偏差度量中重要度权重（默认 40，0-100 制）。 */
    val deviationImportanceWeight: Int = 40,

    /** 偏差度量中节点数权重（默认 20，0-100 制）。 */
    val deviationNodeCountWeight: Int = 20,

    /** 偏差度量上限（默认 100）。 */
    val deviationMax: Int = 100,

    // ==================== 5. UI 展示 ====================

    /** 玩家可见反馈：是否展示深度 0 节点（V0.2 §八，默认 true）。 */
    val showDepth0: Boolean = true,

    /** 玩家可见反馈：是否展示深度 1 节点（V0.2 §八，默认 true）。 */
    val showDepth1: Boolean = true,

    /** 玩家可见反馈：是否展示深度 2 节点（V0.2 §八，默认 false）。 */
    val showDepth2: Boolean = false,

    /** 玩家可见反馈：是否展示深度 3 节点（V0.2 §八，默认 false）。 */
    val showDepth3: Boolean = false,

    /** 分歧日志自动标记已读天数（默认 30 天）。 */
    val autoMarkReadAfterDays: Int = 30
) {
    companion object {
        /** 默认配置（V0.2 文档参数）。 */
        val DEFAULT = ButterflyConfig()
    }
}
