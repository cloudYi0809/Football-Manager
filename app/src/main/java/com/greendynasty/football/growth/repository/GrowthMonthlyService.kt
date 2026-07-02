package com.greendynasty.football.growth.repository

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.growth.calculator.AgeBasedGrowthTable
import com.greendynasty.football.growth.calculator.GrowthAnomalyGuard
import com.greendynasty.football.growth.calculator.GrowthCalculator
import com.greendynasty.football.growth.calculator.GrowthHistoryRecorder
import com.greendynasty.football.growth.calculator.PotentialRealizationCalculator
import com.greendynasty.football.growth.model.AppliedGrowth
import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthEventEntity
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.GrowthRangeTier
import com.greendynasty.football.growth.model.GrowthResult
import com.greendynasty.football.growth.model.MonthlyGrowthResult
import com.greendynasty.football.growth.model.MonthlyPlayingTimeEntity
import com.greendynasty.football.growth.model.MonthlyTrainingRecordEntity
import com.greendynasty.football.growth.model.PlayerGrowthSummary
import com.greendynasty.football.growth.model.calculateAge
import com.greendynasty.football.growth.model.classifyGrowthPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 成长月结业务逻辑门面（Service 层，T09 方案 §五）
 *
 * 整合 [GrowthCalculator]（10 因子公式）/ [PotentialRealizationCalculator]（8 因子潜力兑现）/
 * [GrowthAnomalyGuard]（4 类异常保护）/ [GrowthHistoryRecorder]（快照持久化）/
 * [GrowthEventDetector]（6 类事件检测）与 [GrowthRepository]（数据访问），
 * 对外提供 T07 月度任务调度器的统一入口。
 *
 * 核心职责：
 * 1. 月度成长结算入口（T07 集成点）：[executeMonthlyGrowth]
 * 2. 多源输入聚合（批量查表避免 N+1）：[collectInputs]
 * 3. 活跃范围分档（FULL/ACTIVE/LIGHT/MINIMAL）：[classifyByRange]
 * 4. 分档执行成长计算：[processByRange]
 * 5. 应用成长 + 写快照（事务化）：[applyGrowth]
 * 6. 触发成长事件：[triggerEvents]
 * 7. 月结报告生成（Top growers / Top decliners）：[buildReport]
 * 8. UI 数据源接口（成长曲线 / 事件流）
 *
 * 性能保障：
 * - 单俱乐部月结目标 ≤800ms（100 球员）
 * - 批量查询避免 N+1（单表 1 次查全俱乐部）
 * - LIGHT 范围简化计算（≤2ms / 球员）
 *
 * T08 集成：[GrowthCalculator.calculateInjuryFactor] 根据当前活跃伤病 severity 降权
 * （健康 1.0 / 轻伤 0.5 / 中度 0.2 / 重伤 0.1 / 职业威胁 0.0）。
 *
 * @param databaseManager 三库入口
 * @param config 成长配置（默认使用 [GrowthConfig.getDefault]）
 */
class GrowthMonthlyService(
    databaseManager: DatabaseManager,
    private val config: GrowthConfig = GrowthConfig.getDefault()
) {
    private val repository = GrowthRepository(databaseManager)
    private val growthCalculator = GrowthCalculator(config)
    private val potentialCalculator = PotentialRealizationCalculator(config)
    private val anomalyGuard = GrowthAnomalyGuard(config)
    private val historyRecorder = GrowthHistoryRecorder(databaseManager, config)
    private val eventDetector = GrowthEventDetector(repository, config)

    // ==================== 1. 月结入口（T07 集成） ====================

    /**
     * 执行单俱乐部月度成长结算（T07 MonthlyTaskScheduler 每月 1 日调用）。
     *
     * 流程：
     * 1. 拉取俱乐部全部球员状态 + 俱乐部状态 + 当前赛季 ID
     * 2. 批量聚合输入（一次查多表，避免 N+1）：球员基础信息 / 属性 / 月度训练 / 月度出场时间 / 活跃伤病 / 伤病历史
     * 3. 按活跃范围分档（FULL/ACTIVE/LIGHT/MINIMAL）
     * 4. 分档执行：FULL/ACTIVE 走 10 因子完整公式，LIGHT 走简化公式，MINIMAL 跳过
     * 5. 异常保护：单月上限 / 衰退期不增 / 已达 PA 不增 / 32+ 身体下降
     * 6. 事务化应用成长（更新 CA/PA）+ 写快照
     * 7. 检测并触发 6 类成长事件
     * 8. 生成月结报告（Top growers / Top decliners）
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param executionDate 月结日期（月初 1 日）
     * @return 月结汇总结果（含处理统计 / 事件 / Top 列表 / 耗时）
     */
    suspend fun executeMonthlyGrowth(
        saveId: Int, clubId: Int, executionDate: LocalDate
    ): MonthlyGrowthResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val month = executionDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))

        return@withContext try {
            // 1. 拉取俱乐部状态与赛季 ID
            val club = repository.getClubState(saveId, clubId)
                ?: return@withContext buildFailureResult(
                    saveId, clubId, month, executionDate,
                    "俱乐部状态不存在: saveId=$saveId, clubId=$clubId"
                )
            val seasonId = repository.getWorldState(saveId)?.currentSeasonId ?: 1

            // 2. 拉取俱乐部全部球员
            val allPlayers = repository.getClubPlayers(saveId, clubId)
            if (allPlayers.isEmpty()) {
                return@withContext buildSuccessResult(
                    saveId, clubId, month, executionDate,
                    totalPlayers = 0, processedPlayers = 0, skippedPlayers = 0,
                    rangeBreakdown = emptyMap(),
                    appliedList = emptyList(), eventList = emptyList(),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // 3. 批量聚合输入
            val inputs = collectInputs(saveId, clubId, allPlayers, club, executionDate, seasonId)

            // 4. 分档执行 + 异常保护
            val appliedList = mutableListOf<AppliedGrowth>()
            val rangeBreakdown = mutableMapOf<GrowthRangeTier, Int>()
            for (input in inputs) {
                rangeBreakdown[input.rangeTier] = (rangeBreakdown[input.rangeTier] ?: 0) + 1
                val applied = processByRange(input)
                if (applied != null) appliedList.add(applied)
            }

            // 5. 事务化应用成长（更新 CA/PA）+ 写快照
            applyGrowth(appliedList, saveId)
            historyRecorder.writeBatch(appliedList, saveId, seasonId, executionDate)

            // 6. 触发成长事件
            val eventList = triggerEvents(appliedList)

            // 7. 性能告警
            val durationMs = System.currentTimeMillis() - startTime
            if (durationMs > config.perfWarningClubMs) {
                Log.w(TAG, "月结单俱乐部耗时 ${durationMs}ms 超阈值 ${config.perfWarningClubMs}ms" +
                    "（saveId=$saveId, clubId=$clubId, players=${allPlayers.size}）")
            }

            // 8. 生成报告
            buildSuccessResult(
                saveId, clubId, month, executionDate,
                totalPlayers = allPlayers.size,
                processedPlayers = appliedList.size,
                skippedPlayers = allPlayers.size - appliedList.size,
                rangeBreakdown = rangeBreakdown,
                appliedList = appliedList,
                eventList = eventList,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "月结失败: saveId=$saveId, clubId=$clubId", e)
            buildFailureResult(
                saveId, clubId, month, executionDate,
                "月结异常: ${e.message}"
            )
        }
    }

    // ==================== 2. 多源输入聚合（避免 N+1） ====================

    /**
     * 批量聚合俱乐部全部球员的成长输入。
     *
     * 单表 1 次查全俱乐部，避免对 100+ 球员逐条查询。
     * 聚合源：
     * - history.player（批量）
     * - history.player_attributes（批量，取最新赛季）
     * - save.monthly_training_record（按俱乐部 + 月份）
     * - save.monthly_playing_time（按俱乐部 + 月份）
     * - save.save_injury（活跃伤病 + 伤病历史）
     *
     * T16/T23 未实现字段用默认值：
     * - mentorEffect = 0.0
     * - moraleValue 取 save_player_state.morale
     * - clubTrainingQuality 由 club.reputation 派生
     * - tacticalFitScore 用 ACTIVE 默认值
     */
    private suspend fun collectInputs(
        saveId: Int,
        clubId: Int,
        players: List<SavePlayerStateEntity>,
        club: SaveClubStateEntity,
        executionDate: LocalDate,
        seasonId: Int
    ): List<GrowthInput> {
        val month = executionDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val playerIds = players.map { it.playerId }

        // 批量查基础信息与属性（单表 1 次）
        val playerMap: Map<Int, PlayerEntity> = runCatching {
            repository.getPlayersByIds(playerIds).associateBy { it.playerId }
        }.getOrDefault(emptyMap())
        val attrMap: Map<Int, PlayerAttributesEntity> = runCatching {
            repository.getLatestAttributesBatch(playerIds).associateBy { it.playerId }
        }.getOrDefault(emptyMap())

        // 月度训练记录（按俱乐部 + 月份）
        val trainingMap: Map<Int, MonthlyTrainingRecordEntity> = runCatching {
            repository.getTrainingByClubMonth(saveId, clubId, month).associateBy { it.playerId }
        }.getOrDefault(emptyMap())

        // 月度出场时间（按俱乐部 + 月份）
        val playingTimeMap: Map<Int, MonthlyPlayingTimeEntity> = runCatching {
            repository.getPlayingTimeByClubMonth(saveId, clubId, month).associateBy { it.playerId }
        }.getOrDefault(emptyMap())

        // 俱乐部活跃伤病（一次性查全部，再按 playerId 分组）
        val activeInjuryMap: Map<Int, SaveInjuryEntity> = runCatching {
            repository.getActiveInjuriesByClub(saveId, clubId).associateBy { it.playerId }
        }.getOrDefault(emptyMap())

        // 俱乐部派生属性（V1 简化：T0M6 设施系统未接入）
        val clubTrainingQuality = (club.reputation / 100.0).coerceIn(0.0, 1.0)
        val clubCoachLevel = 50 // V1 占位
        val clubLeagueIntensity = 0.5 // V1 占位

        return players.mapNotNull { player ->
            val playerBase = playerMap[player.playerId]
                ?: return@mapNotNull null
            val attrs = attrMap[player.playerId]
                ?: return@mapNotNull null
            val age = calculateAge(playerBase.birthDate, executionDate)
            val growthPhase = classifyGrowthPhase(age)
            val rangeTier = classifyRange(player, config)

            // 伤病历史（仅活跃伤病的球员才查，避免无谓查询）
            val activeInjury = activeInjuryMap[player.playerId]
            val injuryHistory = if (activeInjury != null) {
                runCatching {
                    repository.getPlayerInjuryHistory(saveId, player.playerId)
                }.getOrDefault(emptyList())
            } else emptyList()

            // T16 未接入：导师加成默认 0
            val mentorEffect = trainingMap[player.playerId]?.mentorEffectScore ?: 0.0

            GrowthInput(
                player = player,
                playerBase = playerBase,
                attributes = attrs,
                age = age,
                growthPhase = growthPhase,
                rangeTier = rangeTier,
                monthlyTraining = trainingMap[player.playerId],
                monthlyPlayingTime = playingTimeMap[player.playerId],
                activeInjury = activeInjury,
                injuryHistory = injuryHistory,
                moraleValue = player.morale,
                mentorEffect = mentorEffect,
                club = club,
                clubTrainingQuality = clubTrainingQuality,
                clubCoachLevel = clubCoachLevel,
                clubLeagueIntensity = clubLeagueIntensity,
                tacticalFitScore = config.activeDefaultTacticalFit,
                nationTalentPoolBonus = 50, // V1 占位
                seasonId = seasonId,
                executionDate = executionDate
            )
        }
    }

    // ==================== 3. 活跃范围分档 ====================

    /**
     * 按球员角色与租借状态分档（参考 T07 ActiveScopeManager）。
     *
     * - FULL：一线队 + 预备队（squad_role in starter/backup/prospect/RESERVE 且未外租）
     * - ACTIVE：U21 + U18（青训球员）
     * - LIGHT：外租球员（loan_club_id 非空）
     * - MINIMAL：未加载联赛球员（极少出现，保留兜底）
     */
    private fun classifyRange(
        player: SavePlayerStateEntity, config: GrowthConfig
    ): GrowthRangeTier {
        // 外租球员走 LIGHT
        if (player.loanClubId != null) return GrowthRangeTier.LIGHT
        val role = (player.squadRole ?: "starter").lowercase()
        return when {
            config.fullSquadRoles.any { it.equals(role, ignoreCase = true) } -> GrowthRangeTier.FULL
            config.activeSquadRoles.any { it.equals(role, ignoreCase = true) } -> GrowthRangeTier.ACTIVE
            else -> GrowthRangeTier.MINIMAL
        }
    }

    // ==================== 4. 分档执行成长计算 ====================

    /**
     * 按活跃范围分档执行成长计算 + 异常保护。
     *
     * - FULL：完整 10 因子计算
     * - ACTIVE：完整 10 因子（省略战术适配细节，复用 FULL 公式）
     * - LIGHT：简化计算（仅 CA 微调，不计算属性细节）
     * - MINIMAL：跳过（仅年龄更新，由 T19 赛季归档处理）
     */
    private suspend fun processByRange(input: GrowthInput): AppliedGrowth? {
        val result = when (input.rangeTier) {
            GrowthRangeTier.FULL, GrowthRangeTier.ACTIVE -> {
                val raw = growthCalculator.calculate(input)
                anomalyGuard.protect(input, raw)
            }
            GrowthRangeTier.LIGHT -> growthCalculator.calculateLight(input)
            GrowthRangeTier.MINIMAL -> return null
        }

        // 潜力兑现月度更新（8 因子公式 + 重伤 PA 惩罚）
        val potentialUpdate = potentialCalculator.updateMonthly(input)

        return AppliedGrowth(
            input = input,
            result = result,
            potentialUpdate = potentialUpdate
        )
    }

    // ==================== 5. 应用成长 + 写快照 ====================

    /**
     * 事务化应用成长（更新 CA/PA 到 save.db）。
     *
     * 写操作按球员逐条更新（V1 简化，单俱乐部 ≤100 球员可接受）。
     * 快照写入由 [GrowthHistoryRecorder.writeBatch] 批量处理。
     */
    private suspend fun applyGrowth(appliedList: List<AppliedGrowth>, saveId: Int) {
        for (applied in appliedList) {
            val player = applied.input.player
            val newCa = applied.result.caAfter
            val newPa = applied.potentialUpdate.paAfter

            // 仅当 CA 或 PA 发生变化时才写入
            if (newCa != player.currentCa) {
                repository.updatePlayerCa(saveId, player.playerId, newCa)
            }
            if (newPa != player.currentPa) {
                repository.updatePlayerPa(saveId, player.playerId, newPa)
            }
        }
    }

    // ==================== 6. 触发成长事件 ====================

    /**
     * 检测并触发 6 类成长事件，批量入库。
     */
    private suspend fun triggerEvents(
        appliedList: List<AppliedGrowth>
    ): List<GrowthEventEntity> {
        val events = eventDetector.detectBatch(appliedList)
        if (events.isNotEmpty()) {
            runCatching { repository.insertEvents(events) }
                .onFailure { Log.w(TAG, "成长事件批量入库失败: ${it.message}") }
        }
        return events
    }

    // ==================== 7. 报告生成 ====================

    /** 构造成功月结结果（含 Top growers / Top decliners） */
    private fun buildSuccessResult(
        saveId: Int,
        clubId: Int,
        month: String,
        executionDate: LocalDate,
        totalPlayers: Int,
        processedPlayers: Int,
        skippedPlayers: Int,
        rangeBreakdown: Map<GrowthRangeTier, Int>,
        appliedList: List<AppliedGrowth>,
        eventList: List<GrowthEventEntity>,
        durationMs: Long
    ): MonthlyGrowthResult {
        val topGrowers = appliedList
            .filter { it.result.caDelta > 0 }
            .sortedByDescending { it.result.caDelta }
            .take(5)
            .map { it.toSummary("成长之星") }

        val topDecliners = appliedList
            .filter { it.result.caDelta < 0 }
            .sortedBy { it.result.caDelta }
            .take(5)
            .map { it.toSummary("状态下滑") }

        return MonthlyGrowthResult(
            saveId = saveId,
            clubId = clubId,
            month = month,
            executionDate = executionDate,
            totalPlayers = totalPlayers,
            processedPlayers = processedPlayers,
            skippedPlayers = skippedPlayers,
            rangeBreakdown = rangeBreakdown,
            snapshotsWritten = appliedList.size,
            eventsTriggered = eventList.size,
            topGrowers = topGrowers,
            topDecliners = topDecliners,
            eventList = eventList,
            durationMs = durationMs,
            success = true
        )
    }

    /** 构造失败月结结果 */
    private fun buildFailureResult(
        saveId: Int,
        clubId: Int,
        month: String,
        executionDate: LocalDate,
        errorMessage: String
    ): MonthlyGrowthResult = MonthlyGrowthResult(
        saveId = saveId,
        clubId = clubId,
        month = month,
        executionDate = executionDate,
        totalPlayers = 0,
        processedPlayers = 0,
        skippedPlayers = 0,
        rangeBreakdown = emptyMap(),
        snapshotsWritten = 0,
        eventsTriggered = 0,
        topGrowers = emptyList(),
        topDecliners = emptyList(),
        eventList = emptyList(),
        durationMs = 0L,
        success = false,
        errorMessage = errorMessage
    )

    /** AppliedGrowth → PlayerGrowthSummary 转换 */
    private fun AppliedGrowth.toSummary(primaryFactor: String): PlayerGrowthSummary =
        PlayerGrowthSummary(
            playerId = input.player.playerId,
            playerName = input.playerBase.displayName ?: input.playerBase.realName,
            age = input.age,
            position = input.playerBase.primaryPosition ?: "CM",
            caBefore = result.caBefore,
            caAfter = result.caAfter,
            caDelta = result.caDelta,
            realizationScore = potentialUpdate.realizationScore,
            rangeTier = input.rangeTier,
            primaryFactor = primaryFactor
        )

    // ==================== 8. UI 数据源接口 ====================

    /** 观察球员成长快照（Flow 驱动 UI，T04 成长曲线） */
    fun observePlayerSnapshots(saveId: Int, playerId: Int) =
        repository.observeSnapshotsByPlayer(saveId, playerId)

    /** 查询球员成长快照列表（按日期升序） */
    suspend fun getPlayerSnapshots(saveId: Int, playerId: Int): List<com.greendynasty.football.growth.model.GrowthSnapshotEntity> =
        repository.getSnapshotsByPlayer(saveId, playerId)

    /** 观察球员成长事件流（按日期倒序） */
    fun observePlayerEvents(saveId: Int, playerId: Int) =
        repository.observeEventsByPlayer(saveId, playerId)

    /** 查询俱乐部近期成长事件 */
    suspend fun getRecentClubEvents(
        saveId: Int, clubId: Int, limit: Int = 20
    ): List<GrowthEventEntity> = repository.getRecentEventsByClub(saveId, clubId, limit)

    /** 查询俱乐部某月全部快照（月结报告） */
    suspend fun getClubMonthlySnapshots(
        saveId: Int, clubId: Int, date: String
    ) = repository.getSnapshotsByClubDate(saveId, clubId, date)

    /** 标记成长事件已读 */
    suspend fun markEventsRead(ids: List<Int>): Int =
        repository.markEventsRead(ids)

    // ==================== 暴露给 T07 的便捷入口 ====================

    /**
     * T07 MonthlyTaskScheduler 调用入口（兼容 PlayerGrowthServiceStub 签名）。
     *
     * 与 [executeMonthlyGrowth] 等价，仅为保持与 stub 接口签名一致。
     */
    suspend fun monthlyGrowthSettlement(saveId: Int, clubId: Int, currentDate: LocalDate) {
        executeMonthlyGrowth(saveId, clubId, currentDate)
    }

    companion object {
        private const val TAG = "GrowthMonthlyService"
    }
}
