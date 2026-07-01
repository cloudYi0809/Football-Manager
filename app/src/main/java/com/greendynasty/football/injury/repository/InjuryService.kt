package com.greendynasty.football.injury.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.injury.calculator.ComebackRiskCalculator
import com.greendynasty.football.injury.calculator.InjuryEffectApplier
import com.greendynasty.football.injury.calculator.InjuryProbabilityCalculator
import com.greendynasty.football.injury.calculator.InjuryRecoveryService
import com.greendynasty.football.injury.model.FacilityUpgradeResult
import com.greendynasty.football.injury.model.ForceReturnResult
import com.greendynasty.football.injury.model.InjuryConfig
import com.greendynasty.football.injury.model.InjuryEvent
import com.greendynasty.football.injury.model.InjuryEventType
import com.greendynasty.football.injury.model.InjuryRiskScore
import com.greendynasty.football.injury.model.InjurySeverity
import com.greendynasty.football.injury.model.InjurySource
import com.greendynasty.football.injury.model.InjuryTypeDefinition
import com.greendynasty.football.injury.model.MatchEventType
import com.greendynasty.football.injury.model.MatchInjuryContext
import com.greendynasty.football.injury.model.MedicalFacilityEntity
import com.greendynasty.football.injury.model.TreatmentSelectionResult
import com.greendynasty.football.injury.model.TreatmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * 伤病系统业务逻辑门面（Service 层）
 *
 * 整合 4 个计算器（[InjuryProbabilityCalculator] / [InjuryRecoveryService] /
 * [InjuryEffectApplier] / [ComebackRiskCalculator]）与 [InjuryRepository]，
 * 对外提供 T07 每日推进 / T02 比赛引擎 / UI 交互的统一入口。
 *
 * 核心职责：
 * 1. 比赛伤病判定（T02 集成点）：[evaluateMatchInjury]
 * 2. 训练伤病判定（T07 TrainingTask 集成点）：[evaluateTrainingInjury]
 * 3. 每日恢复推进（T07 InjuryRecoveryTask 集成点）：[advanceDailyRecovery]
 * 4. 强行复出（玩家操作）：[forceReturn]
 * 5. 治疗方案选择（玩家操作）：[selectTreatment]
 * 6. 医疗设施升级（玩家操作）：[upgradeMedicalFacility]
 * 7. 全队风险评分（医疗中心 UI）：[getSquadInjuryRisk]
 * 8. 活跃伤病 / 医疗设施查询（UI 数据源）
 *
 * 性能保障：每日恢复推进按俱乐部批量处理，单次调用覆盖所有活跃伤病。
 *
 * @param databaseManager 三库入口
 * @param config 伤病配置（默认使用 [InjuryConfig.getDefault]）
 */
class InjuryService(
    databaseManager: DatabaseManager,
    private val config: InjuryConfig = InjuryConfig.getDefault()
) {
    private val repository = InjuryRepository(databaseManager)
    private val probabilityCalculator = InjuryProbabilityCalculator(databaseManager, config)
    private val recoveryService = InjuryRecoveryService(databaseManager, config)
    private val effectApplier = InjuryEffectApplier(databaseManager, config)
    private val comebackRiskCalculator = ComebackRiskCalculator(databaseManager, config)

    // ==================== 1. 比赛伤病判定（T02 集成） ====================

    /**
     * 比赛中伤病判定（V0.1 07 §六.2 + V0.2 04 §十）
     *
     * 由 T02 比赛引擎 [com.greendynasty.football.simulation.matchday.MatchDayExecutor]
     * 在每个对抗事件后回调。已受伤球员不再判定新伤。
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param ctx 比赛伤病上下文（含比赛强度 / 事件类型 / 分钟）
     * @return 新发生的伤病事件，未受伤返回 null
     */
    suspend fun evaluateMatchInjury(
        saveId: Int, playerId: Int, ctx: MatchInjuryContext
    ): InjuryEvent? = withContext(Dispatchers.IO) {
        val player = getPlayerState(saveId, playerId) ?: return@withContext null
        if (player.injuryStatus != "healthy") return@withContext null

        // 1. 基础风险分数（7 因子公式）
        val baseRisk = probabilityCalculator.calculateMatchRisk(saveId, playerId, ctx)

        // 2. 比赛事件强度修正（铲断 2.0 / 头球 1.5 / 冲撞 1.8 / 疲劳 1.3）
        val eventModifier = when (ctx.eventType) {
            MatchEventType.TACKLE_HARD -> config.matchEventRiskMultiplier.tackleHard
            MatchEventType.HEADER_DUEL -> config.matchEventRiskMultiplier.headerDuel
            MatchEventType.COLLISION -> config.matchEventRiskMultiplier.collision
            MatchEventType.FATIGUE_LATE -> config.matchEventRiskMultiplier.fatigueLate
            else -> 1.0
        }

        // 3. 强行复出球员比赛中受伤风险翻倍
        val forcedReturnModifier = if (player.injuryStatus == "healthy_forced") {
            config.matchEventRiskMultiplier.forcedReturnActive
        } else 1.0

        // 4. 最终概率
        val finalProbability = (baseRisk * eventModifier * forcedReturnModifier).coerceIn(0.0, 0.95)

        // 5. 抽样
        if (Random.nextDouble() > finalProbability) return@withContext null

        // 6. 决定伤病类型
        val isContact = ctx.eventType != MatchEventType.FATIGUE_LATE
        val injuryType = selectInjuryType(isContact, ctx.matchIntensity)

        // 7. 创建伤病记录
        val source = if (isContact) InjurySource.MATCH_CONTACT else InjurySource.MATCH_NON_CONTACT
        val injury = createInjuryRecord(
            saveId, playerId, player.currentClubId, injuryType,
            source = source, currentDate = ctx.matchDate,
            matchId = ctx.matchId, matchMinute = ctx.minute
        )

        // 8. 应用到球员状态
        applyInjuryToPlayer(player, injury, saveId)

        InjuryEvent(
            injuryId = injury.injuryId, playerId = playerId,
            typeCode = injury.injuryType,
            severity = InjurySeverity.fromCode(injury.severity),
            source = source,
            expectedReturnDate = injury.expectedReturnDate,
            matchId = ctx.matchId, matchMinute = ctx.minute,
            eventType = InjuryEventType.OCCURRED
        )
    }

    // ==================== 2. 训练伤病判定（T07 TrainingTask 集成） ====================

    /**
     * 训练中伤病判定（T07 TrainingTask 调用）
     *
     * 训练伤病概率低于比赛，但高强度训练显著提升（≥8 翻 2.5 倍）。
     * 体能 <30 时归类为疲劳伤。
     *
     * @param trainingIntensity 训练强度 1-10
     * @return 新发生的伤病事件，未受伤返回 null
     */
    suspend fun evaluateTrainingInjury(
        saveId: Int, playerId: Int, trainingIntensity: Int, currentDate: LocalDate
    ): InjuryEvent? = withContext(Dispatchers.IO) {
        val player = getPlayerState(saveId, playerId) ?: return@withContext null
        if (player.injuryStatus != "healthy") return@withContext null

        val baseRisk = probabilityCalculator.calculateTrainingRisk(
            saveId, playerId, trainingIntensity, currentDate
        )
        val intensityModifier = when {
            trainingIntensity >= 8 -> config.trainingRiskMultiplier.highIntensity
            trainingIntensity >= 6 -> config.trainingRiskMultiplier.mediumIntensity
            else -> 1.0
        }
        val finalProbability = (baseRisk * intensityModifier).coerceIn(0.0, 0.30)

        if (Random.nextDouble() > finalProbability) return@withContext null

        val injuryType = selectInjuryType(isContact = false, intensity = trainingIntensity)
        val source = if (player.condition < 30) InjurySource.FATIGUE else InjurySource.TRAINING
        val injury = createInjuryRecord(
            saveId, playerId, player.currentClubId, injuryType,
            source = source, currentDate = currentDate,
            matchId = null, matchMinute = null
        )
        applyInjuryToPlayer(player, injury, saveId)

        InjuryEvent(
            injuryId = injury.injuryId, playerId = playerId,
            typeCode = injury.injuryType,
            severity = InjurySeverity.fromCode(injury.severity),
            source = source, expectedReturnDate = injury.expectedReturnDate,
            eventType = InjuryEventType.OCCURRED
        )
    }

    // ==================== 3. 每日恢复推进（T07 InjuryRecoveryTask 集成） ====================

    /**
     * 每日恢复推进（T07 InjuryRecoveryTask 调用）
     *
     * 流程：
     * 1. 更新所有进行中伤病的恢复进度
     * 2. 进度达 100% 的触发恢复完成（评估永久影响 / 完全恢复 vs 部分恢复 / 退役）
     * 3. 强行复出球员每日有复发概率（默认 2%）
     *
     * @return 当日产生的伤病事件列表（恢复 / 永久影响 / 复发 / 退役）
     */
    suspend fun advanceDailyRecovery(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): List<InjuryEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<InjuryEvent>()
        val activeInjuries = repository.getActiveInjuriesByClub(saveId, clubId)

        for (injury in activeInjuries) {
            val progress = recoveryService.calculateDailyProgress(saveId, injury, currentDate)

            if (progress.isReady) {
                handleRecoveryComplete(saveId, injury, currentDate, clubId, events)
            } else {
                // 进行中：更新进度，active → recovering
                val newStatus = if (injury.status == "active") "recovering" else injury.status
                repository.updateRecoveryProgress(
                    injury.injuryId, progress.progress, progress.elapsedDays, newStatus
                )

                // 强行复出球员每日复发判定
                if (injury.isForcedReturn && injury.status == "returned_early") {
                    evaluateForcedReturnRecurrence(saveId, injury, currentDate, events)
                }
            }
        }
        events
    }

    // ==================== 4. 强行复出（玩家操作） ====================

    /**
     * 强行复出（玩家操作）
     *
     * 风险代价：CA 临时下降 + 士气下降 + 每日复发风险 + 比赛中再伤概率翻倍。
     *
     * @param injuryId 伤病记录 ID
     * @return 强行复出结果（含风险详情）
     */
    suspend fun forceReturn(
        saveId: Int, injuryId: Int, currentDate: LocalDate
    ): ForceReturnResult = withContext(Dispatchers.IO) {
        val injury = repository.getInjury(injuryId)
            ?: return@withContext ForceReturnResult.failed("伤病记录不存在")
        if (injury.status !in listOf("active", "recovering")) {
            return@withContext ForceReturnResult.failed("伤病状态不允许强行复出")
        }

        val risk = comebackRiskCalculator.evaluateFor(injury)
        if (!risk.valid) return@withContext ForceReturnResult.failed("无法评估风险")

        val player = getPlayerState(saveId, injury.playerId)
            ?: return@withContext ForceReturnResult.failed("球员状态未知")

        // 应用 CA 临时下降
        val newCa = (player.currentCa - risk.caPenalty).coerceAtLeast(1)
        repository.updatePlayerCa(saveId, injury.playerId, newCa)

        // 标记伤病为"强行复出"
        repository.updateInjury(
            injury.copy(
                status = "returned_early",
                isForcedReturn = true,
                actualReturnDate = currentDate.toString()
            )
        )

        // 球员状态回到"健康但带强行复出标记"
        repository.updatePlayerInjuryStatus(saveId, injury.playerId, "healthy_forced", null)

        // 士气下降
        repository.updatePlayerMorale(saveId, injury.playerId, player.morale + risk.moraleDrop)

        ForceReturnResult(
            success = true, injuryId = injuryId,
            caPenalty = risk.caPenalty, moraleDrop = risk.moraleDrop,
            recurrenceProbability = risk.recurrenceProbability,
            aggravationProbability = risk.aggravationProbability,
            permanentLossProbability = risk.permanentLossProbability,
            warnings = risk.warnings,
            message = "已强行复出（CA -${risk.caPenalty}，士气 ${risk.moraleDrop}）"
        )
    }

    // ==================== 5. 治疗方案选择（玩家操作） ====================

    /**
     * 选择治疗方案（玩家操作）
     *
     * 不同方案对恢复速度 / 复发风险 / 永久影响有不同修正：
     * - 保守：速度 0.9x，复发 -10%，免费
     * - 标准：速度 1.0x，无修正，免费
     * - 手术：速度 0.7x，复发 -30% / 永久 -20%，200k
     * - 外部专家：速度 1.3x，复发 -15% / 永久 +5%，500k（仅重伤可用）
     *
     * 选择后重算预计复出日期。
     */
    suspend fun selectTreatment(
        saveId: Int, injuryId: Int, treatmentType: TreatmentType, currentDate: LocalDate
    ): TreatmentSelectionResult = withContext(Dispatchers.IO) {
        val injury = repository.getInjury(injuryId)
            ?: return@withContext TreatmentSelectionResult.failed("伤病记录不存在")
        if (injury.status !in listOf("active", "recovering")) {
            return@withContext TreatmentSelectionResult.failed("伤病状态不允许更改治疗方案")
        }

        // 外部专家仅重伤及以上可用
        val severity = InjurySeverity.fromCode(injury.severity)
        if (treatmentType == TreatmentType.EXTERNAL_EXPERT && !severity.isMajorOrWorseByCode) {
            return@withContext TreatmentSelectionResult.failed("外部专家仅限重伤及以上")
        }

        // 扣费
        val (_, _, cost) = config.treatment.get(treatmentType)
        if (cost > 0) {
            val clubId = injury.clubId
                ?: return@withContext TreatmentSelectionResult.failed("伤病记录缺少俱乐部信息")
            repository.deductClubBalance(saveId, clubId, cost)
        }

        // 更新治疗方案
        val updatedInjury = injury.copy(treatmentType = treatmentType.name)

        // 重算预计复出日期
        val speedMultiplier = recoveryService.calculateRecoverySpeed(saveId, updatedInjury, currentDate)
        val originalTotal = updatedInjury.recoveryTotalDays.takeIf { it > 0 }
            ?: ((config.getInjuryType(updatedInjury.injuryType)?.let {
                (it.baseRecoveryDaysMin + it.baseRecoveryDaysMax) / 2
            } ?: 14))
        val newTotalDays = (originalTotal / speedMultiplier).toInt().coerceAtLeast(1)
        val newReturnDate = LocalDate.parse(updatedInjury.startDate).plusDays(newTotalDays.toLong())

        repository.updateInjury(
            updatedInjury.copy(
                recoveryTotalDays = newTotalDays,
                expectedReturnDate = newReturnDate.toString()
            )
        )

        TreatmentSelectionResult(
            success = true, planType = treatmentType,
            newExpectedReturnDate = newReturnDate.toString(), cost = cost,
            message = "已选择${treatmentType.displayName}" +
                if (cost > 0) "（花费 $cost）" else ""
        )
    }

    // ==================== 6. 医疗设施升级（玩家操作） ====================

    /**
     * 升级医疗设施（玩家操作，需预算批准）
     *
     * 升级费用 = (targetLevel - currentLevel) × upgradeCostCoefficient + upgradeBaseCost
     * 升级后进入 90 天冷却期。
     */
    suspend fun upgradeMedicalFacility(
        saveId: Int, clubId: Int, targetLevel: Int, currentDate: LocalDate
    ): FacilityUpgradeResult = withContext(Dispatchers.IO) {
        val current = repository.getMedicalFacility(saveId, clubId)
            ?: return@withContext FacilityUpgradeResult.failed("医疗设施记录不存在")

        if (targetLevel <= current.medicalLevel) {
            return@withContext FacilityUpgradeResult.failed("目标等级必须高于当前等级")
        }
        if (current.upgradeCooldownDays > 0) {
            return@withContext FacilityUpgradeResult.failed("升级冷却中（剩余 ${current.upgradeCooldownDays} 天）")
        }

        val cost = calculateUpgradeCost(current.medicalLevel, targetLevel)
        repository.deductClubBalance(saveId, clubId, cost)

        val upgraded = recoveryService.buildUpgradedFacility(current, targetLevel, currentDate)
        repository.upsertMedicalFacility(upgraded)

        FacilityUpgradeResult(
            success = true, newLevel = targetLevel, cost = cost,
            newSpeedMultiplier = upgraded.recoverySpeedMultiplier,
            newRecurrenceReduction = upgraded.recurrenceReduction,
            message = "医疗设施已升级至 ${targetLevel} 级（花费 $cost）"
        )
    }

    /** 初始化存档时为玩家俱乐部创建默认医疗设施（存档创建时调用） */
    suspend fun initMedicalFacilityForNewSave(saveId: Int, clubId: Int) = withContext(Dispatchers.IO) {
        val existing = repository.getMedicalFacility(saveId, clubId)
        if (existing != null) return@withContext
        val initialLevel = config.facility.initialLevel
        val facility = MedicalFacilityEntity(
            saveId = saveId, clubId = clubId,
            medicalLevel = initialLevel,
            recoverySpeedMultiplier = recoveryService.deriveFacilitySpeedMultiplier(initialLevel),
            recurrenceReduction = recoveryService.deriveFacilityRecurrenceReduction(initialLevel),
            externalExpertSlots = 0,
            lastUpgradeDate = null,
            upgradeCooldownDays = 0
        )
        repository.upsertMedicalFacility(facility)
    }

    // ==================== 7. 全队风险评分（医疗中心 UI） ====================

    /** 全队伤病风险评分列表（按风险降序） */
    suspend fun getSquadInjuryRisk(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): List<InjuryRiskScore> = probabilityCalculator.calculateForSquad(saveId, clubId, currentDate)

    // ==================== 8. 查询接口（UI 数据源） ====================

    /** 俱乐部活跃伤病列表 */
    suspend fun getActiveInjuries(saveId: Int, clubId: Int): List<SaveInjuryEntity> =
        repository.getActiveInjuriesByClub(saveId, clubId)

    /** 观察俱乐部活跃伤病（Flow 驱动 UI） */
    fun observeActiveInjuries(saveId: Int, clubId: Int): Flow<List<SaveInjuryEntity>> =
        repository.observeActiveInjuries(saveId, clubId)

    /** 球员伤病历史 */
    suspend fun getPlayerInjuryHistory(saveId: Int, playerId: Int): List<SaveInjuryEntity> =
        repository.getPlayerInjuryHistory(saveId, playerId)

    /** 医疗设施记录 */
    suspend fun getMedicalFacility(saveId: Int, clubId: Int): MedicalFacilityEntity? =
        repository.getMedicalFacility(saveId, clubId)

    /** 观察医疗设施记录（Flow 驱动 UI） */
    fun observeMedicalFacility(saveId: Int, clubId: Int): Flow<MedicalFacilityEntity?> =
        repository.observeMedicalFacility(saveId, clubId)

    /** 出场限制判定（T05 首发选择 / T04 阵容页调用） */
    suspend fun evaluateAppearance(saveId: Int, playerId: Int) =
        effectApplier.evaluateAppearance(saveId, playerId)

    // ==================== 内部实现 ====================

    /** 恢复完成处理 */
    private suspend fun handleRecoveryComplete(
        saveId: Int, injury: SaveInjuryEntity, currentDate: LocalDate,
        clubId: Int, events: MutableList<InjuryEvent>
    ) {
        val severity = InjurySeverity.fromCode(injury.severity)

        // 1. 评估永久影响（仅重伤及以上）
        if (severity.isMajorOrWorseByCode && !injury.permanentImpactApplied) {
            val facility = repository.getMedicalFacility(saveId, clubId)
            val medicalLevel = facility?.medicalLevel ?: config.facility.initialLevel
            val permanentImpact = effectApplier.evaluateAndApply(injury, currentDate, medicalLevel)
            if (permanentImpact != null) {
                events.add(
                    InjuryEvent(
                        injuryId = injury.injuryId, playerId = injury.playerId,
                        typeCode = injury.injuryType, severity = severity,
                        source = InjurySource.fromName(injury.source),
                        expectedReturnDate = injury.expectedReturnDate,
                        description = permanentImpact.description,
                        eventType = InjuryEventType.PERMANENT_IMPACT
                    )
                )
            }
        }

        // 2. 评估完全恢复 vs 部分恢复
        val fullRecoveryChance = recoveryService.calculateFullRecoveryChance(saveId, clubId)
        val isFullRecovery = Random.nextDouble() < fullRecoveryChance

        // 3. 更新伤病记录
        repository.updateStatusAndReturn(injury.injuryId, "recovered", currentDate.toString())

        // 4. 更新球员状态：体能恢复至完全恢复 85 / 部分恢复 70
        repository.updatePlayerInjuryStatus(saveId, injury.playerId, "healthy", null)
        val recoveredCondition = if (isFullRecovery) 85 else 70
        repository.updatePlayerCondition(saveId, injury.playerId, recoveredCondition)

        // 5. 部分恢复标记后续复发风险
        if (!isFullRecovery) {
            val newRecurrenceRisk = (injury.recurrenceRisk + 20).coerceAtMost(100)
            repository.updateInjury(injury.copy(recurrenceRisk = newRecurrenceRisk))
        }

        // 6. 职业威胁伤触发退役评估
        if (severity == InjurySeverity.CAREER_THREATENING) {
            val shouldRetire = effectApplier.evaluateCareerThreatenedRetirement(injury, currentDate)
            if (shouldRetire) {
                repository.updatePlayerCareerStatus(saveId, injury.playerId, "retired")
                events.add(
                    InjuryEvent(
                        injuryId = injury.injuryId, playerId = injury.playerId,
                        typeCode = injury.injuryType, severity = severity,
                        source = InjurySource.fromName(injury.source),
                        expectedReturnDate = injury.expectedReturnDate,
                        description = "职业威胁伤触发退役",
                        eventType = InjuryEventType.CAREER_RETIREMENT
                    )
                )
            }
        }

        events.add(
            InjuryEvent(
                injuryId = injury.injuryId, playerId = injury.playerId,
                typeCode = injury.injuryType, severity = severity,
                source = InjurySource.fromName(injury.source),
                expectedReturnDate = injury.expectedReturnDate,
                description = if (isFullRecovery) "完全恢复" else "部分恢复（体能受限）",
                eventType = InjuryEventType.RECOVERED
            )
        )
    }

    /** 强行复出每日复发判定 */
    private suspend fun evaluateForcedReturnRecurrence(
        saveId: Int, injury: SaveInjuryEntity, currentDate: LocalDate, events: MutableList<InjuryEvent>
    ) {
        val dailyRecurrenceRisk = config.forceReturn.dailyRecurrenceRisk
        if (Random.nextDouble() >= dailyRecurrenceRisk) return

        // 复发：原伤病重新激活
        repository.updateStatus(injury.injuryId, "recurred")

        // 复发伤恢复时间延长 50%
        val newTotalDays = (injury.recoveryTotalDays * 1.5).toInt().coerceAtLeast(1)
        val newReturnDate = currentDate.plusDays(newTotalDays.toLong())

        val newInjury = injury.copy(
            injuryId = 0,
            startDate = currentDate.toString(),
            expectedReturnDate = newReturnDate.toString(),
            status = "active",
            isRecurrence = true,
            sourceInjuryId = injury.injuryId,
            recoveryProgress = 0,
            recoveryElapsedDays = 0,
            recoveryTotalDays = newTotalDays,
            isForcedReturn = false,
            actualReturnDate = null,
            permanentImpactApplied = false,
            notes = "强行复出导致复发"
        )
        val newId = repository.insertInjury(newInjury).toInt()

        // 球员状态回到受伤
        repository.updatePlayerInjuryStatus(saveId, injury.playerId, "injured", newReturnDate.toString())

        events.add(
            InjuryEvent(
                injuryId = newId, playerId = injury.playerId,
                typeCode = injury.injuryType,
                severity = InjurySeverity.fromCode(injury.severity),
                source = InjurySource.RECURRENCE,
                expectedReturnDate = newReturnDate.toString(),
                description = "强行复出导致复发",
                eventType = InjuryEventType.RECURRED
            )
        )
    }

    /** 按 contact/intensity 抽样伤病类型（高强度比赛重伤概率更高） */
    private fun selectInjuryType(isContact: Boolean, intensity: Int): InjuryTypeDefinition {
        val candidates = config.injuryTypes.filter { it.isContactType == isContact }
        if (candidates.isEmpty()) {
            return config.getInjuryType(config.defaultInjuryType) ?: config.injuryTypes.first()
        }

        // 重伤权重在高强度下提升
        val adjustedWeights = candidates.map { type ->
            val intensityBonus = when (type.severity) {
                InjurySeverity.CAREER_THREATENING -> if (intensity >= 8) 2.0 else 0.3
                InjurySeverity.MAJOR -> if (intensity >= 7) 1.5 else 0.7
                InjurySeverity.MODERATE -> 1.0
                InjurySeverity.MINOR -> if (intensity <= 5) 1.5 else 0.8
            }
            type to (type.weightInPool * intensityBonus)
        }

        return weightedSample(adjustedWeights)
    }

    /** 加权抽样 */
    private fun <T> weightedSample(items: List<Pair<T, Double>>): T {
        val total = items.sumOf { it.second }
        if (total <= 0) return items.first().first
        var r = Random.nextDouble() * total
        for ((item, weight) in items) {
            r -= weight
            if (r <= 0) return item
        }
        return items.last().first
    }

    /** 创建伤病记录并持久化 */
    private suspend fun createInjuryRecord(
        saveId: Int, playerId: Int, clubId: Int?, injuryType: InjuryTypeDefinition,
        source: InjurySource, currentDate: LocalDate,
        matchId: Long?, matchMinute: Int?
    ): SaveInjuryEntity {
        // 基础恢复天数在 min-max 间抽样
        val baseDays = Random.nextInt(
            injuryType.baseRecoveryDaysMin,
            (injuryType.baseRecoveryDaysMax + 1).coerceAtLeast(injuryType.baseRecoveryDaysMin + 1)
        )

        // 检查是否为复发（同部位旧伤）
        val isRecurrence = evaluateRecurrence(saveId, playerId, injuryType)
        val sourceInjuryId = if (isRecurrence) {
            // 查找同部位最近一次已恢复伤病
            val history = repository.getMajorInjuryHistory(saveId, playerId)
            history.firstOrNull { config.getInjuryType(it.injuryType)?.bodyPart == injuryType.bodyPart }?.injuryId
        } else null

        // 复发伤恢复时间延长 30%
        val adjustedDays = if (isRecurrence) (baseDays * 1.3).toInt() else baseDays
        val expectedReturn = currentDate.plusDays(adjustedDays.toLong())

        val injury = SaveInjuryEntity(
            saveId = saveId, playerId = playerId,
            clubId = clubId,
            injuryType = injuryType.typeCode,
            startDate = currentDate.toString(),
            expectedReturnDate = expectedReturn.toString(),
            severity = injuryType.severity.code,
            recurrenceRisk = (injuryType.recurrenceBaseRisk * 100).toInt(),
            status = "active",
            source = source.name,
            treatmentType = TreatmentType.STANDARD.name,
            recoveryProgress = 0,
            recoveryTotalDays = adjustedDays,
            recoveryElapsedDays = 0,
            isForcedReturn = false,
            isRecurrence = isRecurrence,
            sourceInjuryId = sourceInjuryId,
            permanentImpactApplied = false,
            matchId = matchId,
            matchMinute = matchMinute,
            notes = if (isRecurrence) "复发伤（源伤病 ID: $sourceInjuryId）" else null
        )
        val id = repository.insertInjury(injury).toInt()
        return injury.copy(injuryId = id)
    }

    /** 旧伤复发判定：同部位旧伤复发概率 = baseRecurrenceRisk × (1 + recurrenceCount × 0.15) */
    private suspend fun evaluateRecurrence(
        saveId: Int, playerId: Int, injuryType: InjuryTypeDefinition
    ): Boolean {
        return runCatching {
            val history = repository.getPlayerInjuryHistory(saveId, playerId)
            val sameBodyPart = history.filter {
                config.getInjuryType(it.injuryType)?.bodyPart == injuryType.bodyPart
            }
            if (sameBodyPart.isEmpty()) return false
            val recurrenceCount = sameBodyPart.count { it.isRecurrence }
            val recurrenceProb = injuryType.recurrenceBaseRisk * (1.0 + recurrenceCount * 0.15)
            Random.nextDouble() < recurrenceProb
        }.getOrDefault(false)
    }

    /** 应用伤病到球员状态（更新 injury_status + 体能下降） */
    private suspend fun applyInjuryToPlayer(
        player: SavePlayerStateEntity, injury: SaveInjuryEntity, saveId: Int
    ) {
        repository.updatePlayerInjuryStatus(
            saveId, player.playerId, "injured", injury.expectedReturnDate
        )
        // 伤病期间体能立即下降
        val conditionPenalty = when (InjurySeverity.fromCode(injury.severity)) {
            InjurySeverity.MINOR -> -10
            InjurySeverity.MODERATE -> -20
            InjurySeverity.MAJOR -> -35
            InjurySeverity.CAREER_THREATENING -> -50
        }
        repository.updatePlayerCondition(
            saveId, player.playerId,
            (player.condition + conditionPenalty).coerceAtLeast(0)
        )
    }

    /** 计算医疗设施升级费用 */
    private fun calculateUpgradeCost(currentLevel: Int, targetLevel: Int): Int {
        val levelDiff = targetLevel - currentLevel
        return config.facility.upgradeBaseCost + levelDiff * config.facility.upgradeCostCoefficient
    }

    /** 内部便捷方法：获取球员状态（委托给 Repository） */
    private suspend fun getPlayerState(
        saveId: Int, playerId: Int
    ): SavePlayerStateEntity? = repository.getPlayerState(saveId, playerId)

    /** InjurySeverity 扩展：按 code 判断是否重伤及以上 */
    private val InjurySeverity.isMajorOrWorseByCode: Boolean
        get() = this == InjurySeverity.MAJOR || this == InjurySeverity.CAREER_THREATENING
}
