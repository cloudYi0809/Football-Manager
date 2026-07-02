package com.greendynasty.football.youth.model

/**
 * T16 青训学院配置（V0.1 08 §二 + V0.2 08 §十五 + T16 方案 §八 youth_academy_config.json）
 *
 * 所有可调参数集中在此对象，便于调参与热重载。
 * 默认值严格遵循 V0.2 算法文档；调参只需修改本文件，不需改架构。
 *
 * 参数分组：
 * 1. 7 因子产出公式权重
 * 2. 球员生成（天才概率 / 容量上限 / 双倍生成）
 * 3. 初始 PA / CA 范围
 * 4. 异常保护（单月成长上限 / 接近 PA 减速 / 低职业态度惩罚）
 * 5. 训练质量（青训加成系数）
 * 6. 风格切换冷却
 * 7. 投资成本基数
 * 8. 国家人才池加成
 * 9. 默认风格配置
 * 10. 青训事件参数
 */
data class YouthAcademyConfig(

    // ==================== 1. 7 因子产出公式权重（和为 1.0） ====================

    val weightYouthLevel: Double = 0.25,
    val weightTrainingFacility: Double = 0.20,
    val weightAcademyManager: Double = 0.15,
    val weightRecruitmentRange: Double = 0.15,
    val weightClubReputation: Double = 0.10,
    val weightNationTalentPool: Double = 0.10,
    val weightRandomGenius: Double = 0.05,

    // ==================== 2. 球员生成 ====================

    /** 最高月度生成概率（高分学院上限）。 */
    val maxGenerationProbability: Double = 0.80,
    /** 最低月度生成概率（低分学院保底）。 */
    val minGenerationProbability: Double = 0.05,
    /** 天才概率基数（5%）。 */
    val geniusProbability: Double = 0.05,
    /** 单俱乐部每月最多天才数（异常保护）。 */
    val maxGeniusPerMonth: Int = 1,
    /** U18 容量上限。 */
    val maxU18Capacity: Int = 25,
    /** U21 容量上限。 */
    val maxU21Capacity: Int = 15,
    /** 双倍生成触发的产出分数阈值。 */
    val doubleGenerationThresholdScore: Double = 85.0,
    /** 双倍生成概率。 */
    val doubleGenerationProbability: Double = 0.30,

    // ==================== 3. 初始 PA / CA 范围 ====================

    /** 天才球员 PA 下限。 */
    val geniusPaMin: Int = 90,
    /** 天才球员 PA 上限。 */
    val geniusPaMax: Int = 99,
    /** 普通球员 PA 下限（任务要求 60）。 */
    val normalPaMin: Int = 60,
    /** 普通球员 PA 上限（任务要求 90）。 */
    val normalPaMax: Int = 90,
    /** 普通球员 PA 正态分布标准差。 */
    val normalPaStdDev: Int = 8,
    /** 初始 CA 下限（任务要求 30）。 */
    val initialCaMin: Int = 30,
    /** 初始 CA 上限（任务要求 50）。 */
    val initialCaMax: Int = 50,

    // ==================== 4. 异常保护（V0.2 §十五） ====================

    /** 14-15 岁单月 CA 成长上限。 */
    val maxMonthlyGrowthAge14_15: Int = 2,
    /** 16+ 岁单月 CA 成长上限。 */
    val maxMonthlyGrowthAge16Plus: Int = 3,
    /** 接近 PA 时减速阈值 1（gap ≤ 此值时 caDelta / 3）。 */
    val potentialSlowdownThreshold3: Int = 3,
    /** 接近 PA 时减速阈值 2（gap ≤ 此值时 caDelta / 2）。 */
    val potentialSlowdownThreshold5: Int = 5,
    /** 低职业态度阈值（低于此值成长减半）。 */
    val lowProfessionalismThreshold: Int = 30,
    /** 低职业态度成长乘数。 */
    val lowProfessionalismGrowthMultiplier: Double = 0.5,
    /** 青训球员成长加成（相对一线队，年轻球员处于高成长区）。 */
    val youthGrowthBonus: Double = 0.15,

    // ==================== 5. 训练质量 ====================

    /** 青训训练质量基数下限。 */
    val trainingQualityBaseMin: Double = 0.70,
    /** 青训训练质量基数上限。 */
    val trainingQualityBaseMax: Double = 1.10,
    /** 青训训练质量设施系数。 */
    val trainingQualityMultiplier: Double = 0.40,

    // ==================== 6. 风格切换 ====================

    /** 风格切换冷却月数。 */
    val styleChangeCooldownMonths: Int = 3,

    // ==================== 7. 投资成本 ====================

    /** 投资成本基数（按字段）。 */
    val investmentBaseCost: Map<InvestmentField, Int> = mapOf(
        InvestmentField.YOUTH_LEVEL to 500_000,
        InvestmentField.TRAINING_FACILITY to 500_000,
        InvestmentField.RECRUITMENT_RANGE to 0, // 走阶梯定价
        InvestmentField.U18_COACH to 200_000,
        InvestmentField.U21_COACH to 200_000
    ),
    /** 招募范围阶梯定价（LOCAL→REGIONAL→NATIONAL→INTERNATIONAL）。 */
    val recruitmentRangeCosts: List<Int> = listOf(500_000, 2_000_000, 5_000_000, 10_000_000),
    /** 投资成本指数（base × (level / 10)^exponent）。 */
    val investmentCostExponent: Double = 1.5,

    // ==================== 8. 国家人才池加成（V0.2 附录 1） ====================

    val nationTalentPoolBonus: Map<String, Int> = mapOf(
        "BRA" to 90,
        "ARG" to 85,
        "FRA" to 85,
        "ESP" to 80,
        "GER" to 80,
        "ENG" to 75,
        "ITA" to 75,
        "POR" to 75,
        "NED" to 70,
        "URU" to 70
    ),
    val nationTalentPoolDefault: Int = 50,

    // ==================== 9. 默认风格配置（V0.2 附录 2） ====================

    val defaultStylesByCountry: Map<String, AcademyStyle> = mapOf(
        "ENG" to AcademyStyle.POWER,
        "ESP" to AcademyStyle.TECHNICAL,
        "ITA" to AcademyStyle.DEFENSIVE,
        "GER" to AcademyStyle.ALL_ROUND,
        "NED" to AcademyStyle.WING,
        "FRA" to AcademyStyle.ALL_ROUND,
        "BRA" to AcademyStyle.TECHNICAL,
        "ARG" to AcademyStyle.TECHNICAL,
        "POR" to AcademyStyle.WING
    ),

    // ==================== 10. 青训事件参数 ====================

    val goldenGenerationProbability: Double = 0.05,
    val goldenGenerationMinCount: Int = 3,
    val goldenGenerationMinPa: Int = 80,
    val goldenGenerationCooldownYears: Int = 5,
    val poachProbability: Double = 0.05,
    val poachMinPa: Int = 85,
    val coachRecommendationProbability: Double = 0.10,
    val coachRecommendationMinCoachQuality: Int = 70,
    val coachRecommendationMinPa: Int = 75,
    val attitudeDropProbability: Double = 0.15,
    val attitudeDropProfessionalismThreshold: Int = 50,
    val youthNationalTeamProbability: Double = 0.08,
    val youthNationalTeamMinCa: Int = 60,
    val youthNationalTeamMaxAge: Int = 19,
    val proContractRequestProbability: Double = 0.20,
    val proContractRequestMinAge: Int = 17,
    val proContractRequestMinCa: Int = 55,

    // ==================== 11. 合同 / 导师 ====================

    val defaultYouthWage: Int = 100,
    val defaultProWageBase: Int = 2000,
    val maxMenteesPerMentor: Int = 3,
    val mentorMinAge: Int = 28,
    val mentorMinProfessionalism: Int = 60,

    // ==================== 12. 性能目标 ====================

    val generationTargetMs: Long = 200L,
    val monthlyGrowthTargetMs: Long = 500L
) {
    companion object {
        /** 默认配置单例（V0.2 文档参数）。 */
        @Volatile
        private var instance: YouthAcademyConfig? = null

        fun getDefault(): YouthAcademyConfig = instance ?: synchronized(this) {
            instance ?: YouthAcademyConfig().also { instance = it }
        }
    }

    // ==================== 便捷查询方法 ====================

    /** 获取国家人才池加成。 */
    fun getNationTalentPoolBonus(countryCode: String?): Int =
        countryCode?.let { nationTalentPoolBonus[it.uppercase()] } ?: nationTalentPoolDefault

    /** 获取国家默认青训风格。 */
    fun getDefaultStyle(countryCode: String?): AcademyStyle =
        countryCode?.let { defaultStylesByCountry[it.uppercase()] } ?: AcademyStyle.TECHNICAL

    /** 7 因子权重列表（用于校验和为 1.0）。 */
    fun productionWeightSum(): Double =
        weightYouthLevel + weightTrainingFacility + weightAcademyManager +
            weightRecruitmentRange + weightClubReputation + weightNationTalentPool +
            weightRandomGenius
}
