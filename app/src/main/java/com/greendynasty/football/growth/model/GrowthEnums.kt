package com.greendynasty.football.growth.model

/**
 * 成长月结模块枚举定义（T09 方案 §三.6）
 *
 * 严格对齐 V0.2 算法文档：4 档活跃范围 / 6 类成长事件 / 7 档年龄阶段。
 */

/**
 * 活跃范围分档（决定月结计算精度与性能开销）
 *
 * - FULL：一线队 + 预备队：完整 10 因子计算
 * - ACTIVE：U21 + U18：简化 10 因子（省略战术适配细节）
 * - LIGHT：外租：月度汇总（仅 CA 微调 + 年龄更新）
 * - MINIMAL：未加载联赛：仅年龄 + 退役检查
 */
enum class GrowthRangeTier {
    FULL,
    ACTIVE,
    LIGHT,
    MINIMAL
}

/**
 * 成长事件类型（6 类，玩家可见的命运转折事件）
 *
 * - BREAKTHROUGH_GROWTH：突破性成长（月成长 > 阈值）
 * - GROWTH_STAGNATION：成长停滞（连续 N 月 < 阈值）
 * - POTENTIAL_FULFILLED：潜力兑现（达 PA 90%）
 * - EARLY_DECLINE：早衰（28 岁前开始衰退）
 * - ATTITUDE_DETERIORATION：训练态度恶化（职业态度月降 > 5）
 * - MENTOR_POSITIVE：导师正向影响（加成 > 0.08）
 */
enum class GrowthEventType {
    BREAKTHROUGH_GROWTH,
    GROWTH_STAGNATION,
    POTENTIAL_FULFILLED,
    EARLY_DECLINE,
    ATTITUDE_DETERIORATION,
    MENTOR_POSITIVE
}

/**
 * 7 档年龄阶段（严格对齐 V0.2 §球员成长 7 档年龄表）
 *
 * - EXPLOSIVE：14-15 爆发成长期
 * - RAPID：16-17 高速成长期
 * - STEADY：18-20 稳定成长期
 * - PRE_PRIME：21-23 成熟前期
 * - PRIME：24-27 巅峰期
 * - PRE_DECLINE：28-31 衰退前期
 * - DECLINE：32+ 衰退期
 */
enum class GrowthPhase {
    EXPLOSIVE,
    RAPID,
    STEADY,
    PRE_PRIME,
    PRIME,
    PRE_DECLINE,
    DECLINE
}

/**
 * 事件严重度
 */
enum class GrowthEventSeverity {
    INFO,
    WARN,
    CRITICAL
}

/**
 * 训练侧重类型（月度训练记录）
 */
enum class TrainingFocus {
    SHOOTING,
    PASSING,
    FITNESS,
    DEFENDING,
    BALANCED
}
