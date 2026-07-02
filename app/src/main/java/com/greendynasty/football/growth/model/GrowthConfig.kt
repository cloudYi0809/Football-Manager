package com.greendynasty.football.growth.model

/**
 * 成长月结配置参数（T09 方案 §九 monthly_growth_config.json）
 *
 * 所有算法参数集中配置，便于调参与平衡性回归。
 * 严格依据 V0.2 算法文档，只调参不改架构。
 *
 * 调参红线：
 * - 单月 CA 增长 ≤ maxMonthlyGrowthYoung（3）
 * - 28+ 球员 CA 不再增长
 * - 已达 PA 球员 CA 不再增长
 */
data class GrowthConfig(
    // ===== 性能 =====
    /** 单俱乐部月结告警阈值（毫秒） */
    val perfWarningClubMs: Long = 800L,
    /** 单俱乐部月结临界阈值（毫秒） */
    val perfCriticalClubMs: Long = 1500L,
    /** 月结总耗时告警阈值（毫秒） */
    val perfWarningTotalMs: Long = 2000L,
    /** 单球员 FULL 计算目标耗时（毫秒） */
    val targetSinglePlayerMs: Long = 10L,

    // ===== 活跃范围 =====
    /** FULL 范围的 squad_role 集合 */
    val fullSquadRoles: List<String> = listOf("starter", "backup", "prospect", "RESERVE"),
    /** ACTIVE 范围的 squad_role 集合 */
    val activeSquadRoles: List<String> = listOf("u21", "u18", "youth"),
    /** ACTIVE 简化计算的默认战术适配分 */
    val activeDefaultTacticalFit: Double = 0.7,
    /** LIGHT 范围成长微调系数 */
    val lightGrowthMultiplier: Double = 0.6,

    // ===== 异常保护 =====
    /** 19 岁及以下单月 CA 增长上限 */
    val maxMonthlyGrowthYoung: Int = 3,
    /** 20-23 岁单月 CA 增长上限 */
    val maxMonthlyGrowthMid: Int = 4,
    /** 24-27 岁单月 CA 增长上限 */
    val maxMonthlyGrowthPrime: Int = 2,
    /** 衰退期起始年龄（28+ CA 不再增长） */
    val declineAgeStart: Int = 28,
    /** 强制身体属性下降起始年龄（32+） */
    val forceDeclineAgeStart: Int = 32,
    /** 32+ 强制 pace 下降点数 */
    val forceDeclinePaceDelta: Int = 1,
    /** 32+ 强制 acceleration 下降点数 */
    val forceDeclineAccDelta: Int = 1,
    /** 职业态度低阈值（低于此值触发态度恶化事件） */
    val attitudeLowThreshold: Int = 40,
    /** 随机停滞概率（每月，用于防小妖全部满潜） */
    val randomStagnationProbability: Double = 0.15,

    // ===== 潜力兑现 =====
    /** 潜力兑现阈值（CA/PA 达到此比例触发兑现事件） */
    val potentialFulfilledRatio: Double = 0.9,
    /** 重伤 PA 惩罚：第 1 月内 */
    val majorInjuryPaPenaltyMonth1: Int = 1,
    /** 重伤 PA 惩罚：第 3 月内 */
    val majorInjuryPaPenaltyMonth3: Int = 1,
    /** 重伤 PA 惩罚：第 6 月内 */
    val majorInjuryPaPenaltyMonth6: Int = 0,
    /** 重伤类型集合（触发 PA 惩罚） */
    val majorInjuryTypes: List<String> = listOf("ACL_TEAR", "ACHILLES_RUPTURE", "MENISCUS_TEAR", "FRACTURE"),

    // ===== 成长事件 =====
    /** 突破性成长阈值（月 CA 增长 > 此值触发） */
    val breakthroughCaThreshold: Int = 3,
    /** 成长停滞判定月数 */
    val stagnationMonths: Int = 3,
    /** 成长停滞 CA 阈值（月增长 < 此值视为停滞） */
    val stagnationCaThreshold: Int = 1,
    /** 早衰年龄阈值（小于此年龄衰退触发早衰事件） */
    val earlyDeclineAgeThreshold: Int = 28,
    /** 导师正向影响阈值（加成 > 此值触发事件） */
    val mentorPositiveThreshold: Double = 0.08,

    // ===== 快照保留 =====
    /** 月度快照保留月数（超过后删除明细） */
    val snapshotMonthlyRetainMonths: Int = 24,

    // ===== 10 因子权重（V0.2 §球员成长公式）=====
    val weightTrainingQuality: Double = 0.20,
    val weightPlayingTime: Double = 0.20,
    val weightMentor: Double = 0.05,
    val weightClubFacility: Double = 0.10,
    val weightTalent: Double = 0.10,
    val weightAge: Double = 0.15,
    val weightInjury: Double = 0.10,
    val weightMorale: Double = 0.05,
    val weightRandom: Double = 0.03,
    val weightNationalPool: Double = 0.02,

    // ===== 8 因子潜力兑现率权重（V0.2 §潜力兑现率）=====
    val realizationWeightProfessionalism: Double = 0.25,
    val realizationWeightAmbition: Double = 0.15,
    val realizationWeightMorale: Double = 0.10,
    val realizationWeightPlayingTime: Double = 0.20,
    val realizationWeightTrainingQuality: Double = 0.15,
    val realizationWeightClubFacility: Double = 0.05,
    val realizationWeightInjury: Double = 0.05,
    val realizationWeightAge: Double = 0.05,

    // ===== 默认出场时间 =====
    val defaultMaxPossibleMinutes: Int = 360,

    /** CA 增长换算系数（导师加成 × 此值 → CA 增量） */
    val caGrowthMultiplier: Double = 10.0
) {
    companion object {
        /** 默认配置单例 */
        @Volatile
        private var instance: GrowthConfig? = null

        fun getDefault(): GrowthConfig = instance ?: synchronized(this) {
            instance ?: GrowthConfig().also { instance = it }
        }
    }
}
