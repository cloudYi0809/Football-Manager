package com.greendynasty.football.injury.calculator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.injury.model.AbilityPenalty
import com.greendynasty.football.injury.model.AppearanceDecision
import com.greendynasty.football.injury.model.InjuryConfig
import com.greendynasty.football.injury.model.InjurySeverity
import com.greendynasty.football.injury.model.PermanentImpactResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * 伤病对能力的影响应用器（T08.3）
 *
 * 职责：
 * 1. 出场限制：判定球员能否出场（健康 / 强行复出可带伤上场 / 受伤不可上）。
 * 2. 能力惩罚：带伤上场 / 强行复出时属性降权（CA 临时下降 + 属性有效权重折算）。
 * 3. 永久影响：重伤及以上恢复后按 V0.2 08 §十 + T0Y §五 结算 4 类重伤永久影响
 *    （ACL / 跟腱 / 半月板 / 骨折），医疗水平可降低损失。
 * 4. 职业威胁伤 + 30 岁以上球员触发退役评估。
 *
 * 持久化说明：
 * - PA 永久下降写入 save_player_state.current_pa（save.db 可写）。
 * - pace / acceleration / injury_proneness 属于 history.db player_attributes（只读资产），
 *   V1 将损失值记录于 [PermanentImpactResult] 与伤病 notes（可展示），不写只读库。
 *   后续若引入可写属性覆盖层（save_player_attribute_override），可在此处接入。
 */
class InjuryEffectApplier(
    private val databaseManager: DatabaseManager,
    private val config: InjuryConfig = InjuryConfig.getDefault()
) {

    // ==================== 1. 出场限制 ====================

    /**
     * 判定球员能否出场（T05 首发选择 / T04 阵容页调用）
     *
     * - healthy → 可出场，无惩罚
     * - healthy_forced → 可出场，带 CA 惩罚 + 属性降权（强行复出标记）
     * - injured → 不可出场
     */
    suspend fun evaluateAppearance(saveId: Int, playerId: Int): AppearanceDecision =
        withContext(Dispatchers.IO) {
            val player = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
                ?: return@withContext AppearanceDecision(false, "球员状态未知")
            evaluateAppearanceFor(player)
        }

    /** 直接基于球员状态判定（避免重复查询） */
    fun evaluateAppearanceFor(player: SavePlayerStateEntity): AppearanceDecision {
        return when (player.injuryStatus) {
            "healthy" -> AppearanceDecision(true, "健康可出场")
            "healthy_forced" -> {
                val injury = null // 强行复出惩罚由 ComebackRiskCalculator 在复出时已应用，此处仅提示
                AppearanceDecision(
                    canPlay = true,
                    reason = "强行复出中（比赛中再伤概率提升，属性已降权）",
                    abilityPenalty = AbilityPenalty(
                        caPenalty = 0, // CA 在复出时已扣除，避免重复
                        attributeWeight = 0.85,
                        affectedAttributes = listOf("pace", "acceleration", "agility")
                    )
                )
            }
            else -> AppearanceDecision(false, "伤病中无法出场")
        }
    }

    // ==================== 2. 能力惩罚 ====================

    /**
     * 计算带伤上场的属性降权（用于比赛引擎读取球员属性时折算）
     *
     * @param severity 伤病严重度
     * @return 属性有效权重 0-1（重伤带伤仅剩 70% 效力）
     */
    fun calculateAttributeWeight(severity: InjurySeverity): Double = when (severity) {
        InjurySeverity.MINOR -> 0.95
        InjurySeverity.MODERATE -> 0.85
        InjurySeverity.MAJOR -> 0.70
        InjurySeverity.CAREER_THREATENING -> 0.50
    }

    /**
     * 计算强行复出时的 CA 临时下降（V0.1 07 §六.3）
     *
     * @param progress 当前恢复进度 0-100
     * @param severity 伤病严重度
     * @return CA 临时下降点数
     */
    fun calculateCaPenalty(progress: Int, severity: InjurySeverity): Int {
        val maxPenalty = config.forceReturn.maxCaPenalty[severity.name] ?: 10
        return (maxPenalty * (1.0 - progress / 100.0)).toInt().coerceAtLeast(0)
    }

    // ==================== 3. 永久影响（V0.2 08 §十） ====================

    /**
     * 评估并应用永久影响（重伤及以上恢复后调用）
     *
     * @param injury 已恢复的伤病记录
     * @param medicalLevel 俱乐部医疗设施等级 1-100
     * @return 永久影响结果（null 表示未触发或已结算）
     */
    suspend fun evaluateAndApply(
        injury: SaveInjuryEntity, currentDate: LocalDate, medicalLevel: Int
    ): PermanentImpactResult? = withContext(Dispatchers.IO) {
        if (injury.permanentImpactApplied) return@withContext null
        val severity = InjurySeverity.fromCode(injury.severity)
        if (severity != InjurySeverity.MAJOR && severity != InjurySeverity.CAREER_THREATENING) {
            return@withContext null
        }

        // 基础概率（受治疗方案修正）
        val baseRisk = config.permanentImpact.baseProbability[severity.name] ?: 0.0
        val treatmentPermMod = getTreatmentPermModifier(injury.treatmentType)
        val modifiedRisk = baseRisk * treatmentPermMod

        // 医疗水平降低永久影响概率（100 级降低 30%）
        val medicalReduction = (medicalLevel / 100.0) * 0.30
        val finalRisk = (modifiedRisk - medicalReduction).coerceAtLeast(0.0)

        if (Random.nextDouble() > finalRisk) {
            databaseManager.saveInjuryDao().markPermanentImpactEvaluated(injury.injuryId)
            return@withContext null
        }

        val impact = when (injury.injuryType) {
            "ACL_TEAR" -> applyAclImpact(injury, medicalLevel)
            "ACHILLES_RUPTURE" -> applyAchillesImpact(injury, medicalLevel)
            "MENISCUS_TEAR" -> applyMeniscusImpact(injury, medicalLevel)
            "FRACTURE" -> applyFractureImpact(injury, medicalLevel)
            else -> applyGenericMajorImpact(injury, medicalLevel)
        }

        databaseManager.saveInjuryDao().markPermanentImpactEvaluated(injury.injuryId)
        // 记录永久影响摘要到伤病备注（持久化展示）
        databaseManager.saveInjuryDao().update(
            injury.copy(notes = (injury.notes ?: "") + " | 永久影响：${impact.description}")
        )
        impact
    }

    /** ACL 撕裂：pace/accel -1~4，proneness +3~8，PA -0~5 if age<24 */
    private suspend fun applyAclImpact(
        injury: SaveInjuryEntity, medicalLevel: Int
    ): PermanentImpactResult {
        val r = config.permanentImpact.aclTear
        val reduction = medicalLevel / 25 // 医疗每 25 级降低 1 点损失
        val paceDelta = -(maxOf(0, Random.nextInt(r.paceDeltaMin, r.paceDeltaMax + 1) - reduction))
        val accelDelta = -(maxOf(0, Random.nextInt(r.accelDeltaMin, r.accelDeltaMax + 1) - reduction))
        val pronenessDelta = maxOf(1, Random.nextInt(r.pronenessDeltaMin, r.pronenessDeltaMax + 1) - reduction)
        val age = getAge(injury.playerId)
        val paDelta = if (age < r.paAgeThreshold) {
            -maxOf(0, Random.nextInt(r.paDeltaMin, r.paDeltaMax + 1) - reduction)
        } else 0

        applyPaDelta(injury, paDelta)
        return PermanentImpactResult(
            playerId = injury.playerId, injuryId = injury.injuryId, typeCode = "ACL_TEAR",
            paceDelta = paceDelta, accelerationDelta = accelDelta,
            paDelta = paDelta, injuryPronenessDelta = pronenessDelta,
            description = "ACL 撕裂永久影响：速度 ${paceDelta}、爆发 ${accelDelta}、" +
                "伤病倾向 +${pronenessDelta}" + if (paDelta < 0) "、潜力 ${paDelta}" else ""
        )
    }

    /** 跟腱断裂：比 ACL 更严重 */
    private suspend fun applyAchillesImpact(
        injury: SaveInjuryEntity, medicalLevel: Int
    ): PermanentImpactResult {
        val r = config.permanentImpact.achillesRupture
        val reduction = medicalLevel / 25
        val paceDelta = -(maxOf(0, Random.nextInt(r.paceDeltaMin, r.paceDeltaMax + 1) - reduction))
        val accelDelta = -(maxOf(0, Random.nextInt(r.accelDeltaMin, r.accelDeltaMax + 1) - reduction))
        val pronenessDelta = maxOf(2, Random.nextInt(r.pronenessDeltaMin, r.pronenessDeltaMax + 1) - reduction)
        val age = getAge(injury.playerId)
        val paDelta = if (age < r.paAgeThreshold) {
            -maxOf(0, Random.nextInt(r.paDeltaMin, r.paDeltaMax + 1) - reduction)
        } else 0

        applyPaDelta(injury, paDelta)
        return PermanentImpactResult(
            playerId = injury.playerId, injuryId = injury.injuryId, typeCode = "ACHILLES_RUPTURE",
            paceDelta = paceDelta, accelerationDelta = accelDelta,
            paDelta = paDelta, injuryPronenessDelta = pronenessDelta,
            description = "跟腱断裂永久影响：速度 ${paceDelta}、爆发 ${accelDelta}、" +
                "伤病倾向 +${pronenessDelta}" + if (paDelta < 0) "、潜力 ${paDelta}" else ""
        )
    }

    /** 半月板撕裂：仅 pace 下降，影响较小 */
    private suspend fun applyMeniscusImpact(
        injury: SaveInjuryEntity, medicalLevel: Int
    ): PermanentImpactResult {
        val r = config.permanentImpact.meniscusTear
        val reduction = medicalLevel / 25
        val paceDelta = -(maxOf(0, Random.nextInt(r.paceDeltaMin, r.paceDeltaMax + 1) - reduction))
        val pronenessDelta = maxOf(1, Random.nextInt(r.pronenessDeltaMin, r.pronenessDeltaMax + 1) - reduction)
        val age = getAge(injury.playerId)
        val paDelta = if (age < r.paAgeThreshold) {
            -maxOf(0, Random.nextInt(r.paDeltaMin, r.paDeltaMax + 1) - reduction)
        } else 0

        applyPaDelta(injury, paDelta)
        return PermanentImpactResult(
            playerId = injury.playerId, injuryId = injury.injuryId, typeCode = "MENISCUS_TEAR",
            paceDelta = paceDelta, paDelta = paDelta, injuryPronenessDelta = pronenessDelta,
            description = "半月板撕裂永久影响：速度 ${paceDelta}、伤病倾向 +${pronenessDelta}"
        )
    }

    /** 骨折：仅伤病倾向上升 */
    private suspend fun applyFractureImpact(
        injury: SaveInjuryEntity, medicalLevel: Int
    ): PermanentImpactResult {
        val r = config.permanentImpact.fracture
        val reduction = medicalLevel / 25
        val pronenessDelta = maxOf(1, Random.nextInt(r.pronenessDeltaMin, r.pronenessDeltaMax + 1) - reduction)
        return PermanentImpactResult(
            playerId = injury.playerId, injuryId = injury.injuryId, typeCode = "FRACTURE",
            injuryPronenessDelta = pronenessDelta,
            description = "骨折后伤病倾向 +${pronenessDelta}"
        )
    }

    /** 通用重大伤病影响 */
    private suspend fun applyGenericMajorImpact(
        injury: SaveInjuryEntity, medicalLevel: Int
    ): PermanentImpactResult {
        val reduction = medicalLevel / 25
        val pronenessDelta = maxOf(1, Random.nextInt(2, 6) - reduction)
        return PermanentImpactResult(
            playerId = injury.playerId, injuryId = injury.injuryId, typeCode = injury.injuryType,
            injuryPronenessDelta = pronenessDelta,
            description = "重大伤病后伤病倾向 +${pronenessDelta}"
        )
    }

    /**
     * 职业威胁伤触发退役评估（V0.2 08 §十）
     *
     * @return true 表示应触发提前退役
     */
    suspend fun evaluateCareerThreatenedRetirement(
        injury: SaveInjuryEntity, currentDate: LocalDate
    ): Boolean {
        val severity = InjurySeverity.fromCode(injury.severity)
        if (severity != InjurySeverity.CAREER_THREATENING) return false
        val age = getAge(injury.playerId)
        if (age < config.careerThreateningRetireAgeThreshold) return false
        val retireProbability = config.careerThreateningRetireBaseProbability +
            (age - config.careerThreateningRetireAgeThreshold) * config.careerThreateningRetireAgeIncrement
        return Random.nextDouble() < retireProbability
    }

    // ==================== 内部工具 ====================

    private fun getTreatmentPermModifier(treatmentType: String): Double = when (treatmentType) {
        "CONSERVATIVE" -> config.treatment.conservativePermMod
        "STANDARD" -> config.treatment.standardPermanentModifier
        "SURGERY" -> config.treatment.surgeryPermMod
        "EXTERNAL_EXPERT" -> config.treatment.externalExpertPermMod
        else -> 1.0
    }

    /** 将 PA 永久下降写入 save_player_state（save.db 可写） */
    private suspend fun applyPaDelta(injury: SaveInjuryEntity, paDelta: Int) {
        if (paDelta >= 0) return
        val player = databaseManager.savePlayerStateDao()
            .getByPlayer(injury.saveId, injury.playerId) ?: return
        val newPa = (player.currentPa + paDelta).coerceAtLeast(1)
        databaseManager.savePlayerStateDao().updatePa(injury.saveId, injury.playerId, newPa)
    }

    private suspend fun getAge(playerId: Int): Int {
        return runCatching {
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)
            val birthStr = player?.birthDate ?: return@runCatching 0
            java.time.Period.between(java.time.LocalDate.parse(birthStr), java.time.LocalDate.now()).years
        }.getOrDefault(0)
    }
}
