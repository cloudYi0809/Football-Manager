package com.greendynasty.football.prospect.model

/**
 * T15 历史新星池配置（V0.2 08 §三 + 06 §二.1 + T15 方案 §九 prospect_config.json）。
 *
 * 所有可调参数集中在此对象，便于调参与热重载。
 * 默认值严格遵循 V0.2 算法文档；调参只需修改本文件，不需改架构。
 *
 * 参数分组：
 * 1. 池配置（100+ 新星 / 传奇等级阈值）
 * 2. 发现概率（基础概率 + 上限 + 权重）
 * 3. AI 竞争签约（豪门限额 + 可签约时间窗 + 兴趣阈值）
 * 4. 蝴蝶效应触发开关
 * 5. 路径模拟（CA/PA 月度推进参数）
 */
data class ProspectConfig(

    // ==================== 1. 池配置 ====================

    /** 池最少新星数（V0.2 08 §三，默认 100）。 */
    val minProspects: Int = 100,

    /** 池最多新星数（V0.2 08 §三，默认 500）。 */
    val maxProspects: Int = 500,

    /** 传奇等级阈值（V0.2 08 §三，默认 80）。 */
    val legendLevelThreshold: Int = 80,

    // ==================== 2. 发现概率 ====================

    /** 基础发现概率（V0.2 08 §三.5，默认 0.3）。 */
    val baseDiscoveryProbability: Double = 0.3,

    /** 单次发现概率上限（V0.2 08 §三.5，默认 0.8）。 */
    val maxProbabilityPerAttempt: Double = 0.8,

    /** 地区知识权重（V0.2 08 §三.5，默认 0.25）。 */
    val regionKnowledgeWeight: Double = 0.25,

    /** 潜力判断权重（V0.2 08 §三.5，默认 0.20）。 */
    val potentialJudgmentWeight: Double = 0.20,

    // ==================== 3. AI 竞争签约 ====================

    /** 每豪门每窗 ≤21 岁高潜球员签约上限（V0.2 06 §十一.5，默认 2）。 */
    val maxYoungTalentPerClubPerWindow: Int = 2,

    /** 新星激活后多久 AI 才可签约（月，V0.2 08 §三，默认 6）。 */
    val minMonthsAfterDiscoverable: Int = 6,

    /** AI 兴趣阈值 target_score（V0.2 06 §十一.5，默认 70）。 */
    val interestThresholdTargetScore: Int = 70,

    // ==================== 4. 蝴蝶效应触发 ====================

    /** 玩家提前签约是否触发蝴蝶效应（V0.2 06 §二.1，默认 true）。 */
    val triggerButterflyOnEarlySign: Boolean = true,

    /** AI 签约是否触发蝴蝶效应（V0.2 06 §二.1，默认 true）。 */
    val triggerButterflyOnAiSign: Boolean = true,

    /** 蝴蝶事件默认重要度（1-5，V0.2 06 §二.1，默认 4）。 */
    val butterflyDefaultImportance: Int = 4,

    /** 蝴蝶事件默认影响预算（V0.2 06 §二.1，默认 10）。 */
    val butterflyDefaultImpactBudget: Int = 10,

    /** 蝴蝶事件默认最大传播深度（V0.2 06 §二.1，默认 3，V1 不传播只标记）。 */
    val butterflyDefaultMaxDepth: Int = 3,

    // ==================== 5. 路径模拟 ====================

    /** 是否启用月度 CA/PA 推进（V0.2 08 §三，默认 true）。 */
    val monthlyProgressEnabled: Boolean = true,

    /** 默认每月 CA 推进量（向 PA 靠拢，V0.2 08 §三，默认 1）。 */
    val caProgressPerMonth: Int = 1,

    /** 默认每月 PA 推进量（V1 简化：默认 0，PA 不变）。 */
    val paProgressPerMonth: Int = 0,

    /** CA 推进到 PA 的剩余阈值（CA 距 PA ≤ 此值时不再推进，默认 2）。 */
    val caProgressHaltThreshold: Int = 2,

    /** 路径模拟单次最大处理新星数（性能保护，默认 200）。 */
    val maxProspectsPerSimulation: Int = 200
) {
    companion object {
        /** 默认配置（V0.2 文档参数）。 */
        val DEFAULT = ProspectConfig()
    }
}
