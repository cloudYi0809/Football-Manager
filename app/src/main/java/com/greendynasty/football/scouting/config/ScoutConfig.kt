package com.greendynasty.football.scouting.config

import com.greendynasty.football.scouting.model.ScoutRegionCode

/**
 * T14 球探系统配置（V0.2 08 §三 + §四 + §七 + T14 方案 §八 scout_config.json）。
 *
 * 所有可调参数集中在此对象，便于调参与热重载。
 * 默认值严格遵循 V0.2 算法文档；调参只需修改本文件，不需改架构。
 *
 * 参数分组：
 * 1. 球探池上限 / 雇佣
 * 2. 任务派遣（周期 / 预算 / 并行限制）
 * 3. 发现概率（7 因子权重 + 上限）
 * 4. 报告升级阈值
 * 5. 地区人才密度（15 地区）
 * 6. 青年赛事（日程 + 事件概率）
 * 7. 任务类型参数（8 种）
 * 8. 性能预算
 */
data class ScoutConfig(

    // ==================== 1. 球探池 / 雇佣 ====================

    /** 每俱乐部最多雇佣球探数（V0.2 08 §三.1，默认 6）。 */
    val maxScoutsPerClub: Int = 6,

    /** 球探对未记录地区的默认知识值（V0.2 08 §三.2，默认 20）。 */
    val baseRegionKnowledge: Int = 20,

    /** 新雇佣球探默认合同年限（V0.2 08 §三.1，默认 2 年）。 */
    val defaultContractYears: Int = 2,

    /** 球探默认初始士气（0-100）。 */
    val defaultScoutMorale: Int = 70,

    // ==================== 2. 任务派遣 ====================

    /** 允许的任务周期天数（V0.2 08 §三.4，30/60/90）。 */
    val allowedDurations: List<Int> = listOf(30, 60, 90),

    /** 任务每日预算成本（财务扣款基数，V0.2 08 §三.4，默认 100）。 */
    val dailyBudgetCost: Int = 100,

    /** 单球探每日最多发现球员数（V0.2 08 §三.5，默认 3）。 */
    val maxDiscoveriesPerDay: Int = 3,

    /** 每俱乐部同时进行的任务上限（V0.2 08 §三.4，默认 6）。 */
    val maxActiveTasksPerClub: Int = 6,

    // ==================== 3. 发现概率（7 因子） ====================

    /** 因子 1：地区知识权重（V0.2 08 §三.5，默认 0.25）。 */
    val regionKnowledgeWeight: Double = 0.25,

    /** 因子 2：潜力判断权重（V0.2 08 §三.5，默认 0.20）。 */
    val potentialJudgmentWeight: Double = 0.20,

    /** 因子 3：人脉权重（V0.2 08 §三.5，默认 0.15）。 */
    val networkWeight: Double = 0.15,

    /** 因子 4：任务匹配度权重（V0.2 08 §三.5，默认 0.15）。 */
    val taskMatchWeight: Double = 0.15,

    /** 因子 5：预算权重（V0.2 08 §三.5，默认 0.10）。 */
    val budgetWeight: Double = 0.10,

    /** 因子 6：地区人才密度权重（V0.2 08 §三.5，默认 0.10）。 */
    val regionDensityWeight: Double = 0.10,

    /** 因子 7：随机扰动权重（V0.2 08 §三.5，默认 0.05）。 */
    val randomWeight: Double = 0.05,

    /** 单次发现概率上限（V0.2 08 §三.5，默认 0.8）。 */
    val maxProbabilityPerAttempt: Double = 0.8,

    // ==================== 4. 报告升级阈值（观察天数） ====================

    /** 升级到等级 2（粗略报告）所需累计观察天数（V0.2 08 §四，默认 14）。 */
    val upgradeThresholdL2: Int = 14,

    /** 升级到等级 3（标准报告）所需累计观察天数（默认 35）。 */
    val upgradeThresholdL3: Int = 35,

    /** 升级到等级 4（深度报告）所需累计观察天数（默认 60）。 */
    val upgradeThresholdL4: Int = 60,

    /** 升级到等级 5（完全掌握）所需累计观察天数（默认 90）。 */
    val upgradeThresholdL5: Int = 90,

    /** 升级到等级 5 所需球探潜力判断最低值（0-20，默认 15）。 */
    val upgradeL5PotentialJudgmentMin: Int = 15,

    /** 等级 2 的 CA 估值区间半径（V0.2 08 §四，默认 ±12）。 */
    val caSpreadL2: Int = 12,

    /** 等级 2 的 PA 估值区间半径（默认 ±15）。 */
    val paSpreadL2: Int = 15,

    /** 等级 3 的 CA 估值区间半径（默认 ±7）。 */
    val caSpreadL3: Int = 7,

    /** 等级 3 的 PA 估值区间半径（默认 ±9）。 */
    val paSpreadL3: Int = 9,

    // ==================== 5. 地区人才密度（15 地区，V0.2 08 §三.5） ====================

    /** 各地区人才密度（0-1）。巴西/阿根廷/法国 等高产地区密度高。 */
    val regionTalentDensity: Map<String, Double> = mapOf(
        ScoutRegionCode.ENGLAND.code to 0.70,
        ScoutRegionCode.SPAIN.code to 0.80,
        ScoutRegionCode.ITALY.code to 0.65,
        ScoutRegionCode.GERMANY.code to 0.70,
        ScoutRegionCode.FRANCE.code to 0.85,
        ScoutRegionCode.NETHERLANDS.code to 0.75,
        ScoutRegionCode.PORTUGAL.code to 0.80,
        ScoutRegionCode.BRAZIL.code to 0.95,
        ScoutRegionCode.ARGENTINA.code to 0.90,
        ScoutRegionCode.URUGUAY.code to 0.70,
        ScoutRegionCode.EASTERN_EUROPE.code to 0.65,
        ScoutRegionCode.NORDIC.code to 0.60,
        ScoutRegionCode.WEST_AFRICA.code to 0.75,
        ScoutRegionCode.ASIA.code to 0.50,
        ScoutRegionCode.NORTH_AMERICA.code to 0.45
    ),

    /** 默认地区人才密度（未配置地区用，默认 0.5）。 */
    val defaultDensity: Double = 0.5,

    // ==================== 6. 青年赛事 ====================

    /** 青年赛事举办月份（V0.2 08 §七）。 */
    val tournamentSchedule: Map<String, List<Int>> = mapOf(
        "U17WC" to listOf(10, 11),
        "U20WC" to listOf(5, 6),
        "UEFA_Y" to listOf(3, 4),
        "CONMEBOL_Y" to listOf(1, 2),
        "YOUTH_UCL" to listOf(3, 4, 5)
    ),

    /** 青年赛事 4 类事件触发概率（V0.2 08 §七）。 */
    val youthEventProbabilities: Map<String, Double> = mapOf(
        "YOUTH_HAT_TRICK" to 0.05,
        "BIG_CLUB_RUSH" to 0.04,
        "VALUE_SURGE" to 0.04,
        "SCOUT_STRONG_RECOMMEND" to 0.06
    ),

    // ==================== 7. 任务类型参数（8 种，V0.2 08 §三.4） ====================

    /** 8 种任务类型的发现加成 / 报告升级加成。 */
    val taskTypeParams: Map<String, TaskTypeParam> = mapOf(
        "REGION_SEARCH" to TaskTypeParam(discoveryBonus = 0.10, reportUpgradeBonus = 0.0),
        "POSITION_SEARCH" to TaskTypeParam(discoveryBonus = 0.05, reportUpgradeBonus = 0.0),
        "AGE_GROUP_SEARCH" to TaskTypeParam(discoveryBonus = 0.05, reportUpgradeBonus = 0.0),
        "CLUB_OBSERVATION" to TaskTypeParam(discoveryBonus = 0.0, reportUpgradeBonus = 0.10),
        "YOUTH_TOURNAMENT" to TaskTypeParam(discoveryBonus = 0.15, reportUpgradeBonus = 0.05),
        "CONTRACT_OPPORTUNITY" to TaskTypeParam(discoveryBonus = 0.08, reportUpgradeBonus = 0.0),
        "LOWER_LEAGUE" to TaskTypeParam(discoveryBonus = 0.07, reportUpgradeBonus = 0.0),
        "STAR_TRACKING" to TaskTypeParam(discoveryBonus = 0.0, reportUpgradeBonus = 0.20)
    ),

    // ==================== 8. 性能预算（V0.2 08 §九） ====================

    /** 单球探任务每日推进耗时上限（毫秒，默认 50）。 */
    val scoutAdvanceBudgetMs: Long = 50L,

    /** 报告生成耗时上限（毫秒，默认 100）。 */
    val reportBuildBudgetMs: Long = 100L,

    /** 候选池上限（V0.2 08 §九，默认 200），超出按随机采样。 */
    val candidatePoolLimit: Int = 200
) {
    companion object {
        /** 默认配置（V0.2 08 文档参数）。 */
        val DEFAULT = ScoutConfig()
    }
}

/**
 * 任务类型参数（V0.2 08 §三.4）。
 *
 * @param discoveryBonus 发现概率加成（叠加到 7 因子公式结果上）
 * @param reportUpgradeBonus 报告升级天数加成（实际观察天数 × (1 + bonus)）
 */
data class TaskTypeParam(
    val discoveryBonus: Double,
    val reportUpgradeBonus: Double
)
