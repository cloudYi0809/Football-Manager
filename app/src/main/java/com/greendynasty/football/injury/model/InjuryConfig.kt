package com.greendynasty.football.injury.model

/**
 * 伤病系统配置（T08 方案 §八 injury_config.json）
 *
 * 所有算法参数均集中于此，便于调参与平衡性回归（Gate 2 "赛季伤病 8-15 人次" 硬指标）。
 * 严格依据 V0.1 07 §六.2 的 7 因子公式权重与 V0.2 08 §十 永久影响参数。
 *
 * 调参红线：只调参不改架构，不通过修改随机数种子造假。
 */
data class InjuryConfig(

    /** 基础概率：每场比赛每球员的"被判定机会"基础值 */
    val baseMatchInjuryProbability: Double = 0.025,

    /** 训练日基础伤病概率（远低于比赛） */
    val baseTrainingInjuryProbability: Double = 0.005,

    /** 默认伤病类型 code（兜底） */
    val defaultInjuryType: String = "STRAIN_MUSCLE",

    /** 7 因子权重（V0.1 07 §六.2） */
    val riskWeights: RiskWeights = RiskWeights(),

    /** 年龄风险系数表 */
    val ageRiskTable: AgeRiskTable = AgeRiskTable(),

    /** 年龄恢复速度系数表 */
    val ageRecoveryTable: AgeRecoveryTable = AgeRecoveryTable(),

    /** 位置风险修正（门将风险低，前锋被铲风险高） */
    val positionRiskModifier: Map<String, Double> = mapOf(
        "GK" to 0.40, "CB" to 0.85, "LB" to 0.95, "RB" to 0.95,
        "DM" to 1.00, "CM" to 1.00, "LM" to 1.00, "RM" to 1.00,
        "AM" to 1.05, "LW" to 1.10, "RW" to 1.10, "ST" to 1.25, "CF" to 1.25
    ),

    /** 比赛事件风险倍率 */
    val matchEventRiskMultiplier: MatchEventRiskMultiplier = MatchEventRiskMultiplier(),

    /** 训练强度风险倍率 */
    val trainingRiskMultiplier: TrainingRiskMultiplier = TrainingRiskMultiplier(),

    /** 治疗方案参数 */
    val treatment: TreatmentParams = TreatmentParams(),

    /** 强行复出风险参数 */
    val forceReturn: ForceReturnParams = ForceReturnParams(),

    /** 永久影响参数 */
    val permanentImpact: PermanentImpactParams = PermanentImpactParams(),

    /** 职业威胁伤退役参数 */
    val careerThreateningRetireBaseProbability: Double = 0.20,
    val careerThreateningRetireAgeIncrement: Double = 0.05,
    val careerThreateningRetireAgeThreshold: Int = 30,

    /** 医疗设施参数 */
    val facility: FacilityParams = FacilityParams(),

    /** 伤病类型预置池（13 种，覆盖 4 档严重度 + 4 类重伤） */
    val injuryTypes: List<InjuryTypeDefinition> = defaultInjuryTypes
) {
    companion object {
        /** 默认配置单例 */
        @Volatile
        private var instance: InjuryConfig? = null

        fun getDefault(): InjuryConfig = instance ?: synchronized(this) {
            instance ?: InjuryConfig().also { instance = it }
        }
    }

    /** 按 code 查找伤病类型 */
    fun getInjuryType(typeCode: String): InjuryTypeDefinition? =
        injuryTypes.firstOrNull { it.typeCode == typeCode }

    // ==================== 子参数组 ====================

    /** 7 因子权重 */
    data class RiskWeights(
        val injuryProneness: Double = 0.25,
        val conditionInverse: Double = 0.20,
        val matchIntensity: Double = 0.15,
        val trainingIntensity: Double = 0.15,
        val fixtureDensity: Double = 0.10,
        val ageFactor: Double = 0.10,
        val randomFactor: Double = 0.05
    ) {
        val sum: Double get() = injuryProneness + conditionInverse + matchIntensity +
            trainingIntensity + fixtureDensity + ageFactor + randomFactor
    }

    /** 年龄风险系数（伤病发生） */
    data class AgeRiskTable(
        val range14to16: Double = 0.70,
        val range17to19: Double = 0.80,
        val range20to23: Double = 0.85,
        val range24to27: Double = 0.90,
        val range28to30: Double = 1.00,
        val range31to33: Double = 1.20,
        val range34plus: Double = 1.50
    ) {
        fun getRiskFactor(age: Int): Double = when {
            age <= 16 -> range14to16
            age <= 19 -> range17to19
            age <= 23 -> range20to23
            age <= 27 -> range24to27
            age <= 30 -> range28to30
            age <= 33 -> range31to33
            else -> range34plus
        }
    }

    /** 年龄恢复速度系数 */
    data class AgeRecoveryTable(
        val range14to16: Double = 1.10,
        val range17to19: Double = 1.10,
        val range20to23: Double = 1.05,
        val range24to27: Double = 1.00,
        val range28to30: Double = 0.95,
        val range31to33: Double = 0.85,
        val range34plus: Double = 0.75
    ) {
        fun getMultiplier(age: Int): Double = when {
            age <= 16 -> range14to16
            age <= 19 -> range17to19
            age <= 23 -> range20to23
            age <= 27 -> range24to27
            age <= 30 -> range28to30
            age <= 33 -> range31to33
            else -> range34plus
        }
    }

    /** 比赛事件风险倍率 */
    data class MatchEventRiskMultiplier(
        val tackleHard: Double = 2.0,
        val headerDuel: Double = 1.5,
        val collision: Double = 1.8,
        val fatigueLate: Double = 1.3,
        val forcedReturnActive: Double = 2.0
    )

    /** 训练强度风险倍率 */
    data class TrainingRiskMultiplier(
        val highIntensity: Double = 2.5,
        val mediumIntensity: Double = 1.5,
        val lowIntensity: Double = 1.0
    )

    /** 治疗方案参数（speedMul 速度系数 / recurMod 复发修正 / permMod 永久影响修正 / cost 费用） */
    data class TreatmentParams(
        val conservativeSpeedMul: Double = 0.90,
        val conservativeRecurMod: Double = 0.90,
        val conservativePermMod: Double = 0.95,
        val standardSpeedMultiplier: Double = 1.00,
        val standardRecurrenceModifier: Double = 1.00,
        val standardPermanentModifier: Double = 1.00,
        val surgerySpeedMul: Double = 0.70,
        val surgeryRecurMod: Double = 0.70,
        val surgeryPermMod: Double = 0.80,
        val surgeryCost: Int = 200_000,
        val externalExpertSpeedMul: Double = 1.30,
        val externalExpertRecurMod: Double = 0.85,
        val externalExpertPermMod: Double = 1.05,
        val externalExpertCost: Int = 500_000
    ) {
        fun get(type: TreatmentType): Triple<Double, Double, Int> = when (type) {
            TreatmentType.CONSERVATIVE -> Triple(conservativeSpeedMul, conservativeRecurMod, 0)
            TreatmentType.STANDARD -> Triple(standardSpeedMultiplier, standardRecurrenceModifier, 0)
            TreatmentType.SURGERY -> Triple(surgerySpeedMul, surgeryRecurMod, surgeryCost)
            TreatmentType.EXTERNAL_EXPERT -> Triple(externalExpertSpeedMul, externalExpertRecurMod, externalExpertCost)
        }
    }

    /** 强行复出风险参数 */
    data class ForceReturnParams(
        val recurrenceBaseBySeverity: Map<String, Double> = mapOf(
            "MINOR" to 0.10, "MODERATE" to 0.20, "MAJOR" to 0.35, "CAREER_THREATENING" to 0.50
        ),
        val aggravationBaseBySeverity: Map<String, Double> = mapOf(
            "MINOR" to 0.05, "MODERATE" to 0.10, "MAJOR" to 0.20, "CAREER_THREATENING" to 0.30
        ),
        val permanentLossBase: Map<String, Double> = mapOf(
            "MAJOR" to 0.15, "CAREER_THREATENING" to 0.30
        ),
        val maxCaPenalty: Map<String, Int> = mapOf(
            "MINOR" to 5, "MODERATE" to 10, "MAJOR" to 15, "CAREER_THREATENING" to 20
        ),
        val matchReinjuryMultiplier: Double = 2.5,
        val moraleDrop: Int = -10,
        val dailyRecurrenceRisk: Double = 0.02
    )

    /** 永久影响参数（V0.2 08 §十） */
    data class PermanentImpactParams(
        val baseProbability: Map<String, Double> = mapOf(
            "MAJOR" to 0.30, "CAREER_THREATENING" to 0.60
        ),
        val aclTear: MajorImpactRange = MajorImpactRange(
            paceDeltaMin = 1, paceDeltaMax = 4,
            accelDeltaMin = 1, accelDeltaMax = 4,
            pronenessDeltaMin = 3, pronenessDeltaMax = 8,
            paDeltaMin = 0, paDeltaMax = 5, paAgeThreshold = 24
        ),
        val achillesRupture: MajorImpactRange = MajorImpactRange(
            paceDeltaMin = 2, paceDeltaMax = 6,
            accelDeltaMin = 2, accelDeltaMax = 5,
            pronenessDeltaMin = 5, pronenessDeltaMax = 10,
            paDeltaMin = 0, paDeltaMax = 5, paAgeThreshold = 26
        ),
        val meniscusTear: MajorImpactRange = MajorImpactRange(
            paceDeltaMin = 1, paceDeltaMax = 3,
            accelDeltaMin = 0, accelDeltaMax = 0,
            pronenessDeltaMin = 2, pronenessDeltaMax = 4,
            paDeltaMin = 0, paDeltaMax = 2, paAgeThreshold = 24
        ),
        val fracture: MajorImpactRange = MajorImpactRange(
            paceDeltaMin = 0, paceDeltaMax = 0,
            accelDeltaMin = 0, accelDeltaMax = 0,
            pronenessDeltaMin = 1, pronenessDeltaMax = 3,
            paDeltaMin = 0, paDeltaMax = 0, paAgeThreshold = 99
        )
    )

    /** 重大伤病永久影响区间 */
    data class MajorImpactRange(
        val paceDeltaMin: Int,
        val paceDeltaMax: Int,
        val accelDeltaMin: Int,
        val accelDeltaMax: Int,
        val pronenessDeltaMin: Int,
        val pronenessDeltaMax: Int,
        val paDeltaMin: Int,
        val paDeltaMax: Int,
        val paAgeThreshold: Int
    )

    /** 医疗设施参数 */
    data class FacilityParams(
        val initialLevel: Int = 50,
        val upgradeCooldownDays: Int = 90,
        val minLevel: Int = 1,
        val maxLevel: Int = 100,
        val upgradeBaseCost: Int = 500_000,
        val upgradeCostCoefficient: Int = 10_000
    )
}

/** 13 种伤病类型预置池（T08 方案 §九） */
private val defaultInjuryTypes = listOf(
    InjuryTypeDefinition("STRAIN_MUSCLE", "肌肉拉伤", "Muscle Strain", InjurySeverity.MINOR, "MUSCLE",
        3, 7, 0.15, 0.0, "leg", false, 30),
    InjuryTypeDefinition("BRUISE", "挫伤", "Bruise", InjurySeverity.MINOR, "MUSCLE",
        1, 5, 0.05, 0.0, "leg", true, 25),
    InjuryTypeDefinition("SPRAIN_MINOR", "轻微扭伤", "Minor Sprain", InjurySeverity.MINOR, "LIGAMENT",
        3, 7, 0.10, 0.0, "ankle", true, 25),
    InjuryTypeDefinition("STRAIN_HAMSTRING", "腘绳肌拉伤", "Hamstring Strain", InjurySeverity.MODERATE, "MUSCLE",
        14, 28, 0.25, 0.0, "leg", false, 20),
    InjuryTypeDefinition("SPRAIN_ANKLE_MODERATE", "中度脚踝扭伤", "Moderate Ankle Sprain", InjurySeverity.MODERATE, "LIGAMENT",
        14, 28, 0.20, 0.0, "ankle", true, 18),
    InjuryTypeDefinition("CONCUSSION", "脑震荡", "Concussion", InjurySeverity.MODERATE, "CONCUSSION",
        7, 21, 0.05, 0.0, "head", true, 8),
    InjuryTypeDefinition("MUSCLE_TEAR", "肌肉撕裂", "Muscle Tear", InjurySeverity.MODERATE, "MUSCLE",
        21, 28, 0.30, 0.0, "leg", false, 12),
    InjuryTypeDefinition("SPRAIN_ANKLE_SEVERE", "严重脚踝扭伤", "Severe Ankle Sprain", InjurySeverity.MAJOR, "LIGAMENT",
        60, 120, 0.35, 0.20, "ankle", true, 10),
    InjuryTypeDefinition("ACL_TEAR", "十字韧带撕裂", "ACL Tear", InjurySeverity.MAJOR, "LIGAMENT",
        180, 270, 0.15, 0.40, "knee", false, 6),
    InjuryTypeDefinition("FRACTURE", "骨折", "Fracture", InjurySeverity.MAJOR, "FRACTURE",
        90, 150, 0.05, 0.25, "leg", true, 8),
    InjuryTypeDefinition("MENISCUS_TEAR", "半月板撕裂", "Meniscus Tear", InjurySeverity.MAJOR, "LIGAMENT",
        90, 150, 0.20, 0.30, "knee", false, 6),
    InjuryTypeDefinition("ACHILLES_RUPTURE", "跟腱断裂", "Achilles Rupture", InjurySeverity.CAREER_THREATENING, "TENDON",
        240, 365, 0.25, 0.65, "leg", false, 4),
    InjuryTypeDefinition("MULTIPLE_RECURRENCE", "多次重伤复发", "Multiple Recurrence", InjurySeverity.CAREER_THREATENING, "MUSCLE",
        180, 365, 0.50, 0.55, "leg", false, 2)
)
