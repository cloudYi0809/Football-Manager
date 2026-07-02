package com.greendynasty.football.prospect.path

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.prospect.data.ProspectPathEventEntity
import com.greendynasty.football.prospect.data.ProspectStateEntity
import com.greendynasty.football.prospect.model.DefaultTransferRecord
import com.greendynasty.football.prospect.model.ProspectAction
import com.greendynasty.football.prospect.model.ProspectConfig
import com.greendynasty.football.prospect.model.ProspectPathEventType
import com.greendynasty.football.prospect.model.ProspectResult
import com.greendynasty.football.prospect.model.ProspectStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T15 历史新星成长路径模拟器（V0.2 08 §三 + 06 §二.1）。
 *
 * 职责：
 * 1. 月度 CA/PA 推进（CA 向 PA 靠拢）
 * 2. 历史转会路径执行（按 default_transfer_path JSON 到期触发转会）
 * 3. 路径打断判定（玩家签走 → 触发蝴蝶效应）
 *
 * 集成入口：
 * - T07 每月推进 → [simulateMonthly]（每月执行一次，推进所有已激活新星）
 * - T11 转会完成 → [onPlayerSigned]（玩家签约时检查是否历史新星 + 是否提前）
 *
 * @param databaseManager 三库管理入口
 * @param config 历史新星池配置
 * @param butterflyMarker 蝴蝶效应标记器
 */
class ProspectPathSimulator(
    private val databaseManager: DatabaseManager,
    private val config: ProspectConfig = ProspectConfig.DEFAULT,
    private val butterflyMarker: ButterflyEffectMarker = ButterflyEffectMarker(databaseManager, config)
) {

    /**
     * V0.1 08 §三 历史新星月度推进（V0.2 06 §二.1）。
     *
     * 由 T07 每月推进调用（月初触发）：
     * 1. 推进所有 DISCOVERED / DEFAULT_PATH 状态新星的 CA（向 PA 靠拢）
     * 2. 检查并执行到期的历史转会
     * 3. 检查路径是否被打断（球员被签走）
     *
     * @param saveId 存档 ID
     * @param saveUuid 存档 UUID（蝴蝶事件 save_id 字段用）
     * @param currentDate 当前游戏内日期
     * @return 产生的路径事件结果列表（供 T07 转换为新闻/待办）
     */
    suspend fun simulateMonthly(
        saveId: Int,
        saveUuid: String,
        currentDate: LocalDate
    ): List<ProspectResult> = withContext(Dispatchers.IO) {
        if (!config.monthlyProgressEnabled) return@withContext emptyList()

        val results = mutableListOf<ProspectResult>()
        val stateDao = databaseManager.prospectStateDao()

        // 1. 获取所有已发现 + 默认路径状态的新星
        val discoveredStates = stateDao.getByStatus(saveId, ProspectStatus.DISCOVERED.code)
        val defaultPathStates = stateDao.getByStatus(saveId, ProspectStatus.DEFAULT_PATH.code)
        val activeStates = (discoveredStates + defaultPathStates).distinctBy { it.prospectId }

        // 性能保护：单次模拟上限
        val states = if (activeStates.size > config.maxProspectsPerSimulation) {
            activeStates.take(config.maxProspectsPerSimulation)
        } else {
            activeStates
        }

        for (state in states) {
            // 2. 检查路径是否已被打断（球员被签走）
            val interrupted = checkPathInterrupted(saveId, saveUuid, state, currentDate)
            if (interrupted != null) {
                results.add(interrupted)
                continue
            }

            // 3. 推进 CA/PA（月度）
            val progressResult = progressCaPa(saveId, state, currentDate)
            if (progressResult != null) results.add(progressResult)

            // 4. 检查并执行到期的历史转会
            val transferResult = executeNextTransfer(saveId, state, currentDate)
            if (transferResult != null) results.add(transferResult)
        }

        results
    }

    /**
     * V0.2 06 §二.1 玩家签约历史新星时检查是否提前签约（蝴蝶触发点）。
     *
     * 由 T11 转会完成后调用：
     * - 若签约球员为历史新星：
     *   - 早于 defaultBreakthroughYear 或非默认一线队 → 触发蝴蝶效应
     *   - 否则按历史签约，无需触发蝴蝶
     * - 否则忽略（普通球员）
     *
     * @param saveId 存档 ID
     * @param saveUuid 存档 UUID
     * @param playerId 球员 ID
     * @param signedByClubId 签约俱乐部 ID
     * @param currentDate 当前游戏内日期
     * @return 处理结果（非历史新星返回 EARLY_SIGNED + butterflyEventTriggered=false）
     */
    suspend fun onPlayerSigned(
        saveId: Int,
        saveUuid: String,
        playerId: Int,
        signedByClubId: Int,
        currentDate: LocalDate
    ): ProspectResult = withContext(Dispatchers.IO) {
        // 1. 查找该球员对应的历史新星
        val historyProspect = databaseManager.historyProspectDao().getProspectByPlayer(playerId)
            ?: return@withContext ProspectResult(
                prospectId = -1,
                action = ProspectAction.EARLY_SIGNED,
                playerId = playerId,
                clubId = signedByClubId,
                butterflyEventTriggered = false,
                message = "非历史新星，无需打断"
            )

        // 2. 检查是否真的"提前"（早于 defaultBreakthroughYear 或非默认一线队）
        val isEarly = currentDate.year < historyProspect.defaultBreakthroughYear ||
            signedByClubId != historyProspect.defaultFirstTeamClubId

        if (!isEarly) {
            return@withContext ProspectResult(
                prospectId = historyProspect.prospectId,
                action = ProspectAction.EARLY_SIGNED,
                playerId = playerId,
                clubId = signedByClubId,
                butterflyEventTriggered = false,
                message = "按历史签约，无需触发蝴蝶"
            )
        }

        // 3. 更新星状态为 SIGNED_EARLY
        val stateDao = databaseManager.prospectStateDao()
        val state = stateDao.get(saveId, historyProspect.prospectId)
        if (state != null) {
            stateDao.updateStatus(saveId, historyProspect.prospectId, ProspectStatus.SIGNED_EARLY.code)
            stateDao.updateCurrentPath(saveId, historyProspect.prospectId, "interrupted")
            stateDao.updateSnapshot(
                saveId, historyProspect.prospectId,
                state.currentCa, state.currentPa, signedByClubId
            )

            // 4. 写入路径事件 EARLY_SIGNED
            databaseManager.prospectPathEventDao().insert(
                ProspectPathEventEntity(
                    saveId = saveId,
                    prospectId = historyProspect.prospectId,
                    playerId = playerId,
                    eventType = ProspectPathEventType.EARLY_SIGNED.code,
                    eventDate = currentDate.toString(),
                    fromClubId = state.currentClubId,
                    toClubId = signedByClubId,
                    isDefaultPath = 0,
                    summary = "玩家提前签约，默认路径打断"
                )
            )

            // 5. 触发蝴蝶效应（V1 简化：仅标记）
            val butterflyId = butterflyMarker.markButterflyTriggered(
                saveId = saveId,
                saveUuid = saveUuid,
                prospectId = historyProspect.prospectId,
                playerId = playerId,
                triggerType = "EARLY_SIGN",
                sourceClubId = signedByClubId,
                expectedClubId = historyProspect.defaultFirstTeamClubId,
                currentDate = currentDate,
                summary = "${historyProspect.prospectId} 号新星被玩家提前签约，历史路径偏离"
            )

            return@withContext ProspectResult(
                prospectId = historyProspect.prospectId,
                action = ProspectAction.EARLY_SIGNED,
                playerId = playerId,
                clubId = signedByClubId,
                butterflyEventTriggered = butterflyId != null,
                message = "历史新星被提前签约，默认路径已打断，触发蝴蝶效应"
            )
        }

        ProspectResult(
            prospectId = historyProspect.prospectId,
            action = ProspectAction.EARLY_SIGNED,
            playerId = playerId,
            clubId = signedByClubId,
            butterflyEventTriggered = false,
            message = "新星状态记录缺失，未触发蝴蝶"
        )
    }

    /**
     * V0.2 06 §二.1 检查默认路径是否被打断（球员被玩家签走）。
     *
     * 判定：球员当前俱乐部与上次路径事件记录的俱乐部不一致 → 路径被打断。
     *
     * @param saveUuid 存档 UUID（蝴蝶事件 save_id 字段用）
     * @return 若路径被打断返回 ProspectResult，否则返回 null
     */
    private suspend fun checkPathInterrupted(
        saveId: Int,
        saveUuid: String,
        state: ProspectStateEntity,
        currentDate: LocalDate
    ): ProspectResult? {
        val playerState = databaseManager.savePlayerStateDao().getByPlayer(saveId, state.playerId)
            ?: return null

        // 若球员当前俱乐部与历史路径期望的俱乐部不一致 → 被玩家签走
        val expectedClubId = state.currentClubId ?: return null
        if (playerState.currentClubId != expectedClubId &&
            playerState.currentClubId != null &&
            state.currentPath == "default"
        ) {
            // 写入路径打断事件
            databaseManager.prospectPathEventDao().insert(
                ProspectPathEventEntity(
                    saveId = saveId,
                    prospectId = state.prospectId,
                    playerId = state.playerId,
                    eventType = ProspectPathEventType.PATH_INTERRUPTED.code,
                    eventDate = currentDate.toString(),
                    fromClubId = expectedClubId,
                    toClubId = playerState.currentClubId,
                    isDefaultPath = 0,
                    summary = "球员被签走，默认路径打断"
                )
            )

            // 更新星状态
            databaseManager.prospectStateDao().updateStatus(
                saveId, state.prospectId, ProspectStatus.SIGNED_EARLY.code
            )
            databaseManager.prospectStateDao().updateCurrentPath(
                saveId, state.prospectId, "interrupted"
            )

            // V1 简化：路径自动检测到打断时也触发蝴蝶标记
            // （玩家可能通过非 T11 流程签走球员，这里兜底触发）
            val butterflyId = butterflyMarker.markButterflyTriggered(
                saveId = saveId,
                saveUuid = saveUuid,
                prospectId = state.prospectId,
                playerId = state.playerId,
                triggerType = "PATH_INTERRUPTED",
                sourceClubId = playerState.currentClubId,
                expectedClubId = expectedClubId,
                currentDate = currentDate,
                summary = "${state.prospectId} 号新星默认路径被打断（球员被签走），蝴蝶效应触发"
            )

            return ProspectResult(
                prospectId = state.prospectId,
                action = ProspectAction.PATH_INTERRUPTED,
                playerId = state.playerId,
                clubId = playerState.currentClubId,
                butterflyEventTriggered = butterflyId != null,
                message = "默认路径被打断（球员被签走）"
            )
        }
        return null
    }

    /**
     * V0.1 08 §三 月度 CA/PA 推进（V0.2 08 §三）。
     *
     * CA 每月向 PA 靠拢 config.caProgressPerMonth 点，CA 距 PA ≤ config.caProgressHaltThreshold 时不再推进。
     * PA 默认不变（V1 简化）。
     */
    private suspend fun progressCaPa(
        saveId: Int,
        state: ProspectStateEntity,
        currentDate: LocalDate
    ): ProspectResult? {
        val ca = state.currentCa
        val pa = state.currentPa
        if (pa - ca <= config.caProgressHaltThreshold) return null

        val newCa = (ca + config.caProgressPerMonth).coerceAtMost(pa)
        val newPa = pa + config.paProgressPerMonth

        // 更新星状态快照
        databaseManager.prospectStateDao().updateSnapshot(
            saveId, state.prospectId, newCa, newPa, state.currentClubId
        )

        // 同步更新 save_player_state（让球员列表也能看到 CA 推进）
        runCatching {
            databaseManager.savePlayerStateDao().updateCa(saveId, state.playerId, newCa)
            databaseManager.savePlayerStateDao().updatePa(saveId, state.playerId, newPa)
        }

        // 写入路径事件
        databaseManager.prospectPathEventDao().insert(
            ProspectPathEventEntity(
                saveId = saveId,
                prospectId = state.prospectId,
                playerId = state.playerId,
                eventType = ProspectPathEventType.CA_PA_PROGRESS.code,
                eventDate = currentDate.toString(),
                caBefore = ca,
                caAfter = newCa,
                paBefore = pa,
                paAfter = newPa,
                isDefaultPath = 1,
                summary = "CA $ca → $newCa, PA $pa → $newPa"
            )
        )

        // 更新 last_path_event_date
        databaseManager.prospectStateDao().updateLastPathEventDate(
            saveId, state.prospectId, currentDate.toString()
        )

        return ProspectResult(
            prospectId = state.prospectId,
            action = ProspectAction.CA_PA_PROGRESS,
            playerId = state.playerId,
            clubId = state.currentClubId,
            butterflyEventTriggered = false,
            message = "CA 推进 $ca → $newCa"
        )
    }

    /**
     * V0.1 08 §三 默认路径执行：检查并执行到期的历史转会。
     *
     * 由 [simulateMonthly] 在月度推进时调用：
     * 1. 解析 default_transfer_path JSON
     * 2. 查找下一条未执行且到期的转会
     * 3. 执行转会（更新星状态 + 写入路径事件 + 同步 save_player_state）
     *
     * @return 转会执行结果，无转会到期返回 null
     */
    private suspend fun executeNextTransfer(
        saveId: Int,
        state: ProspectStateEntity,
        currentDate: LocalDate
    ): ProspectResult? {
        // 1. 查询历史新星配置
        val historyProspect = databaseManager.historyProspectDao().getProspect(state.prospectId)
            ?: return null

        // 2. 解析默认转会路径
        val transferPath = DefaultTransferPathParser.parse(historyProspect.defaultTransferPath)
        if (transferPath.isEmpty()) return null

        // 3. 查找下一条未执行的转会（按日期升序）
        val eventDao = databaseManager.prospectPathEventDao()
        val nextTransfer = transferPath.find { transfer ->
            // 未到期的转会
            !currentDate.isBefore(LocalDate.parse(transfer.transferDate.take(10))) &&
                // 未执行过（按 toClubId 去重）
                eventDao.countTransferTo(saveId, state.prospectId, transfer.toClubId) == 0
        } ?: return null

        // 4. 执行默认转会
        val fromClubId = nextTransfer.fromClubId ?: state.currentClubId
        val toClubId = nextTransfer.toClubId

        // 更新星状态
        databaseManager.prospectStateDao().updateSnapshot(
            saveId, state.prospectId, state.currentCa, state.currentPa, toClubId
        )
        databaseManager.prospectStateDao().updateStatus(
            saveId, state.prospectId, ProspectStatus.DEFAULT_PATH.code
        )

        // 同步 save_player_state（让球员列表也能看到俱乐部变更）
        runCatching {
            databaseManager.savePlayerStateDao().updateClub(saveId, state.playerId, toClubId)
        }

        // 5. 写入路径事件 TRANSFER
        eventDao.insert(
            ProspectPathEventEntity(
                saveId = saveId,
                prospectId = state.prospectId,
                playerId = state.playerId,
                eventType = ProspectPathEventType.TRANSFER.code,
                eventDate = currentDate.toString(),
                fromClubId = fromClubId,
                toClubId = toClubId,
                transferFee = nextTransfer.transferFee,
                isDefaultPath = 1,
                summary = "按历史路径转会，转会费 ${nextTransfer.transferFee}"
            )
        )

        databaseManager.prospectStateDao().updateLastPathEventDate(
            saveId, state.prospectId, currentDate.toString()
        )

        return ProspectResult(
            prospectId = state.prospectId,
            action = ProspectAction.DEFAULT_PATH_EXECUTED,
            playerId = state.playerId,
            clubId = toClubId,
            butterflyEventTriggered = false,
            message = "按历史路径转会至俱乐部 $toClubId"
        )
    }

    /**
     * 检查球员是否被玩家签走（V0.2 06 §二.1）。
     *
     * 判定：save_player_state 中球员的 currentClubId 与历史路径期望的俱乐部不一致。
     */
    suspend fun isPlayerSignedByPlayer(
        saveId: Int,
        playerId: Int,
        expectedClubId: Int?
    ): Boolean = withContext(Dispatchers.IO) {
        if (expectedClubId == null) return@withContext false
        val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
            ?: return@withContext false
        state.currentClubId != null && state.currentClubId != expectedClubId
    }
}
