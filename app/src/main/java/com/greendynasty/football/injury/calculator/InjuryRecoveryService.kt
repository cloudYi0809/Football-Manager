package com.greendynasty.football.injury.calculator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.injury.model.InjuryConfig
import com.greendynasty.football.injury.model.MedicalFacilityEntity
import com.greendynasty.football.injury.model.RecoveryProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * 伤病恢复服务（T08.2 三档恢复 + 医疗水平修正）
 *
 * 依据 T08 方案 §四.4 RecoveryCalculator 实现，核心三方法：
 * 1. [calculateRecoverySpeed]：6 因子相乘的恢复速度系数（医疗设施 / 年龄 / 体质 / 治疗方案 / 职业态度 / 复发惩罚）。
 * 2. [calculateDailyProgress]：每日恢复进度推进，进度 = 已过天数 / 调整后总天数 × 100。
 * 3. [calculateFullRecoveryChance]：恢复完成时的"完全恢复"概率（医疗水平 1→0.60 / 50→0.85 / 100→0.97）。
 *
 * 三档恢复周期（由 [com.greendynasty.football.injury.model.InjuryTypeDefinition] 的 baseRecoveryDaysMin/Max 决定）：
 * - 轻伤 MINOR：1-28 天（如挫伤 1-5 / 肌肉拉伤 3-7）
 * - 中伤 MODERATE：7-28 天（如腘绳肌拉伤 14-28 / 肌肉撕裂 21-28）
 * - 重伤 MAJOR / 职业威胁 CAREER_THREATENING：60-365 天（如 ACL 180-270 / 跟腱断裂 240-365）
 *
 * 医疗水平修正：
 * - medicalLevel 50 → 恢复速度系数 1.0（基准）
 * - medicalLevel 100 → 1.3（顶级医疗，恢复快 30%）
 * - medicalLevel 1 → 0.7（简陋医疗，恢复慢 30%）
 *
 * V1 简化：治疗方案直接存储于 [SaveInjuryEntity.treatmentType] 字段，未引入独立 treatment_plan 表。
 *
 * @param databaseManager 三库入口
 * @param config 伤病配置
 */
class InjuryRecoveryService(
    private val databaseManager: DatabaseManager,
    private val config: InjuryConfig = InjuryConfig.getDefault()
) {

    /**
     * 计算指定伤病的实际恢复速度系数（6 因子相乘）
     *
     * 因子：
     * 1. 医疗设施系数（0.7-1.3）
     * 2. 年龄系数（老将恢复慢，14-19 岁 1.10 / 28-30 岁 0.95 / 34+ 0.75）
     * 3. 体质属性系数（stamina 高恢复快，0.85-1.15）
     * 4. 治疗方案系数（保守 0.90 / 标准 1.00 / 手术 0.70 / 外部专家 1.30）
     * 5. 职业态度系数（高职业态度恢复好，0.90-1.10）
     * 6. 复发伤恢复慢 20%（0.80）
     *
     * @return 恢复速度系数，典型范围 0.4-2.0
     */
    suspend fun calculateRecoverySpeed(
        saveId: Int, injury: SaveInjuryEntity, currentDate: LocalDate
    ): Double = withContext(Dispatchers.IO) {
        val player = databaseManager.savePlayerStateDao().getByPlayer(saveId, injury.playerId)
            ?: return@withContext 1.0
        val attrs = runCatching {
            databaseManager.historyPlayerDao().getLatestAttributes(injury.playerId)
        }.getOrNull()
        val facility = getFacility(saveId, injury.clubId)

        // 1. 医疗设施系数（0.7-1.3）
        val facilityMultiplier = facility?.recoverySpeedMultiplier ?: 1.0

        // 2. 年龄系数（老将恢复慢）
        val age = calculateAge(injury.playerId, currentDate)
        val ageMultiplier = if (age > 0) config.ageRecoveryTable.getMultiplier(age) else 1.0

        // 3. 体质属性系数（stamina 高恢复快，0.85-1.15）
        val staminaMultiplier = if (attrs != null) {
            0.85 + (attrs.stamina / 100.0) * 0.30
        } else 1.0

        // 4. 治疗方案系数（保守 0.90 / 标准 1.00 / 手术 0.70 / 外部专家 1.30）
        val treatmentMultiplier = getTreatmentSpeedMultiplier(injury.treatmentType)

        // 5. 职业态度系数（高职业态度恢复好，0.90-1.10）
        val professionalismMultiplier = if (attrs != null) {
            0.90 + (attrs.professionalism / 100.0) * 0.20
        } else 1.0

        // 6. 复发伤恢复慢 20%
        val recurrencePenalty = if (injury.isRecurrence) 0.80 else 1.0

        (facilityMultiplier * ageMultiplier * staminaMultiplier *
            treatmentMultiplier * professionalismMultiplier * recurrencePenalty)
            .coerceIn(0.3, 2.5)
    }

    /**
     * 每日恢复进度推进
     *
     * progress = (elapsedDays / adjustedTotalDays) × 100
     * adjustedTotalDays = recoveryTotalDays / speedMultiplier（恢复快则总天数缩短）
     *
     * @return 恢复进度结果（含进度百分比 / 已过天数 / 调整后总天数 / 剩余天数 / 是否就绪）
     */
    suspend fun calculateDailyProgress(
        saveId: Int, injury: SaveInjuryEntity, currentDate: LocalDate
    ): RecoveryProgress = withContext(Dispatchers.IO) {
        val speedMultiplier = calculateRecoverySpeed(saveId, injury, currentDate)

        // 调整后总恢复天数（恢复快则总天数缩短，至少 1 天）
        val originalTotal = injury.recoveryTotalDays.takeIf { it > 0 }
            ?: estimateTotalDays(injury)
        val adjustedTotalDays = (originalTotal / speedMultiplier).toInt().coerceAtLeast(1)

        // 已经过的天数（从伤病开始日到当前日）
        val elapsed = runCatching {
            ChronoUnit.DAYS.between(LocalDate.parse(injury.startDate), currentDate).toInt()
                .coerceAtLeast(0)
        }.getOrDefault(0)

        val progress = ((elapsed.toDouble() / adjustedTotalDays) * 100).toInt().coerceIn(0, 100)
        val isReady = progress >= 100
        val remainingDays = (adjustedTotalDays - elapsed).coerceAtLeast(0)

        RecoveryProgress(
            injuryId = injury.injuryId,
            progress = progress,
            elapsedDays = elapsed,
            totalDays = adjustedTotalDays,
            remainingDays = remainingDays,
            isReady = isReady,
            speedMultiplier = speedMultiplier
        )
    }

    /**
     * 评估恢复完成时的"完全恢复"概率
     *
     * 医疗水平高 → 完全恢复概率高（无复发风险，体能恢复至 85）
     * 医疗水平低 → 部分恢复（体能仅恢复至 70，复发风险提升）
     *
     * 映射：medicalLevel 1 → 0.60 / 50 → 0.85 / 100 → 0.97
     *
     * @return 完全恢复概率 0.60-0.97
     */
    suspend fun calculateFullRecoveryChance(saveId: Int, clubId: Int): Double =
        withContext(Dispatchers.IO) {
            val facility = getFacility(saveId, clubId)
            val level = facility?.medicalLevel ?: config.facility.initialLevel
            deriveFullRecoveryChance(level)
        }

    // ==================== 医疗设施等级→系数推导（供 Facility 升级时使用） ====================

    /**
     * 由医疗设施等级推导恢复速度系数
     *
     * medicalLevel 50 → 1.0（基准）
     * medicalLevel 100 → 1.3（顶级医疗，恢复快 30%）
     * medicalLevel 1 → 0.7（简陋医疗，恢复慢 30%）
     *
     * 公式：0.7 + (level / 100) × 0.6，结果限制在 0.7-1.3
     */
    fun deriveFacilitySpeedMultiplier(medicalLevel: Int): Double {
        return (0.7 + (medicalLevel.coerceIn(1, 100) / 100.0) * 0.6).coerceIn(0.7, 1.3)
    }

    /**
     * 由医疗设施等级推导复发概率降低值
     *
     * medicalLevel 50 → 0.0（基准，无降低）
     * medicalLevel 100 → 0.30（顶级医疗，复发概率降 30%）
     * medicalLevel 1 → -0.10（简陋医疗，复发概率反升 10%）
     *
     * 公式：-0.10 + (level / 100) × 0.40，结果限制在 -0.10 ~ 0.30
     */
    fun deriveFacilityRecurrenceReduction(medicalLevel: Int): Double {
        return (-0.10 + (medicalLevel.coerceIn(1, 100) / 100.0) * 0.40).coerceIn(-0.10, 0.30)
    }

    /**
     * 由医疗设施等级推导完全恢复概率（与 [calculateFullRecoveryChance] 一致，纯函数版）
     *
     * 映射：medicalLevel 1 → 0.60 / 50 → 0.85 / 100 → 0.97
     */
    fun deriveFullRecoveryChance(medicalLevel: Int): Double {
        return (0.60 + (medicalLevel.coerceIn(1, 100) / 100.0) * 0.37).coerceIn(0.60, 0.97)
    }

    /**
     * 构造医疗设施升级后的实体（供 Repository 调用 upsert）
     *
     * @param current 当前医疗设施记录
     * @param newLevel 升级后等级
     * @param upgradeDate 升级日期
     * @return 升级后的新实体
     */
    fun buildUpgradedFacility(
        current: MedicalFacilityEntity, newLevel: Int, upgradeDate: LocalDate
    ): MedicalFacilityEntity {
        val level = newLevel.coerceIn(config.facility.minLevel, config.facility.maxLevel)
        return current.copy(
            medicalLevel = level,
            recoverySpeedMultiplier = deriveFacilitySpeedMultiplier(level),
            recurrenceReduction = deriveFacilityRecurrenceReduction(level),
            lastUpgradeDate = upgradeDate.toString(),
            upgradeCooldownDays = config.facility.upgradeCooldownDays
        )
    }

    // ==================== 内部工具 ====================

    /** 获取医疗设施记录（不存在时返回 null，调用方按默认等级 50 处理） */
    private suspend fun getFacility(saveId: Int, clubId: Int?): MedicalFacilityEntity? {
        if (clubId == null) return null
        return runCatching {
            databaseManager.medicalFacilityDao().get(saveId, clubId)
        }.getOrNull()
    }

    /** 治疗方案 → 恢复速度系数 */
    private fun getTreatmentSpeedMultiplier(treatmentType: String): Double = when (treatmentType) {
        "CONSERVATIVE" -> config.treatment.conservativeSpeedMul
        "STANDARD" -> config.treatment.standardSpeedMultiplier
        "SURGERY" -> config.treatment.surgerySpeedMul
        "EXTERNAL_EXPERT" -> config.treatment.externalExpertSpeedMul
        else -> config.treatment.standardSpeedMultiplier
    }

    /**
     * 当 recoveryTotalDays 为 0 时，按伤病类型基础区间中值估算总恢复天数
     * （兼容 V0.1 旧伤病记录，或未初始化恢复天数的场景）
     */
    private fun estimateTotalDays(injury: SaveInjuryEntity): Int {
        val typeDef = config.getInjuryType(injury.injuryType) ?: return 14
        return ((typeDef.baseRecoveryDaysMin + typeDef.baseRecoveryDaysMax) / 2).coerceAtLeast(1)
    }

    /** 计算球员年龄（基于 history.player.birth_date） */
    private suspend fun calculateAge(playerId: Int, currentDate: LocalDate): Int {
        return runCatching {
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)
            val birthStr = player?.birthDate ?: return@runCatching 0
            Period.between(LocalDate.parse(birthStr), currentDate).years
        }.getOrDefault(0)
    }
}
