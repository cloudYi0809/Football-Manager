package com.greendynasty.football.dressingroom.event

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.dressingroom.model.DressingRoomConfig
import com.greendynasty.football.dressingroom.model.EventSeverity
import com.greendynasty.football.dressingroom.model.PlayerEmotionEventEntity
import com.greendynasty.football.dressingroom.model.PlayerEmotionEventType
import com.greendynasty.football.dressingroom.model.PlayerMoraleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T23 球员情绪事件服务（V0.2 + T23 任务要求 §二.5 + 实现方案 §四.6）。
 *
 * 触发 6 类球员情绪事件：
 * - UNHAPPY 不满：士气 <30 触发
 * - TRANSFER_RUMOR 转会传闻：士气 <20 + 连败触发
 * - RENEWAL_REQUEST 续约要求：合同 <1 年触发
 * - CONFLICT 球员冲突：2 名球员均士气 <30 触发
 * - NEW_SIGNING_STRUGGLE 新援融入困难：加入 30 天内士气 <40
 * - VETERAN_FAREWELL 老将告别：≥33 岁宣布赛季末退役
 *
 * 严重等级（[EventSeverity]）：
 * - MINOR 轻微：士气影响 -2
 * - MODERATE 中等：士气影响 -5
 * - MAJOR 严重：士气影响 -10
 * - CRITICAL 危急：士气影响 -15
 *
 * 事件每赛季上限配置化（[DressingRoomConfig.eventLimits]）。
 *
 * 触发时机：T07 每周推进调用（每周一检查所有球员）。
 *
 * @param databaseManager 三库管理入口
 * @param config 更衣室配置
 */
class PlayerEmotionEventService(
    private val databaseManager: DatabaseManager,
    private val config: DressingRoomConfig = DressingRoomConfig.DEFAULT
) {

    // ==================== 1. 周度扫描触发 ====================

    /**
     * 周度扫描触发情绪事件（V0.2 + 实现方案 §四.6）。
     *
     * 触发时机：T07 每周推进调用（每周一）。
     *
     * 流程：
     * 1. 查询俱乐部所有球员士气记录
     * 2. 对每名球员检查 6 类事件触发条件
     * 3. 检查每赛季上限，超限则跳过
     * 4. 触发事件并写入 player_emotion_event 表
     * 5. 应用 morale_impact 到全队 / 相关球员
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 触发的事件列表（可能为空）
     */
    suspend fun scanAndTrigger(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate
    ): List<PlayerEmotionEventEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PlayerEmotionEventEntity>()
        val moraleDao = databaseManager.playerMoraleDao()
        val eventDao = databaseManager.playerEmotionEventDao()
        val allMorales = moraleDao.getByClub(saveId, clubId)
        if (allMorales.isEmpty()) return@withContext emptyList()

        // 1. 对每名球员检查触发条件
        for (morale in allMorales) {
            // UNHAPPY 不满：士气 <30
            tryTriggerUnhappy(morale, saveId, clubId, seasonId, currentDate, eventDao)?.let {
                results.add(it)
                applyMoraleImpact(saveId, clubId, it.moraleImpact, currentDate)
            }

            // TRANSFER_RUMOR 转会传闻：士气 <20（V1 简化：连败判定由 T06 提供，此处仅看士气）
            tryTriggerTransferRumor(morale, saveId, clubId, seasonId, currentDate, eventDao)?.let {
                results.add(it)
                applyMoraleImpact(saveId, clubId, it.moraleImpact, currentDate)
            }

            // RENEWAL_REQUEST 续约要求：合同 <1 年
            tryTriggerRenewalRequest(morale, saveId, clubId, seasonId, currentDate, eventDao)?.let {
                results.add(it)
                applyMoraleImpact(saveId, clubId, it.moraleImpact, currentDate)
            }

            // NEW_SIGNING_STRUGGLE 新援融入困难：加入 30 天内士气 <40（V1 简化：用 last_updated_date 近似）
            tryTriggerNewSigningStruggle(morale, saveId, clubId, seasonId, currentDate, eventDao)?.let {
                results.add(it)
                applyMoraleImpact(saveId, clubId, it.moraleImpact, currentDate)
            }

            // VETERAN_FAREWELL 老将告别：≥33 岁（V1 简化：需传入年龄，此处跳过由调用方主动触发）
            // 由 [triggerVeteranFarewell] 显式调用，不在周度扫描中触发
        }

        // 2. CONFLICT 球员冲突：2 名球员均士气 <30
        tryTriggerConflict(allMorales, saveId, clubId, seasonId, currentDate, eventDao)?.let {
            results.add(it)
            applyMoraleImpact(saveId, clubId, it.moraleImpact, currentDate)
        }

        results
    }

    // ==================== 2. 单事件触发 ====================

    /**
     * 触发 UNHAPPY 不满事件（V0.2 + 实现方案 §四.6）。
     *
     * 条件：morale < 30（[EventTriggerParams.unhappyMoraleThreshold]）
     * 严重等级：MAJOR（士气影响 -10）
     */
    private suspend fun tryTriggerUnhappy(
        morale: PlayerMoraleEntity,
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): PlayerEmotionEventEntity? {
        if (morale.morale >= config.eventTrigger.unhappyMoraleThreshold) return null
        val type = PlayerEmotionEventType.UNHAPPY
        if (isOverSeasonLimit(saveId, clubId, seasonId, type, eventDao)) return null
        return writeEvent(
            saveId, clubId, morale.playerId, seasonId, currentDate,
            type, EventSeverity.MAJOR,
            description = "球员对当前处境严重不满，士气跌至 ${morale.morale}",
            moraleImpact = -10,
            eventDao = eventDao
        )
    }

    /**
     * 触发 TRANSFER_RUMOR 转会传闻事件（V0.2 + 实现方案 §四.6）。
     *
     * 条件：morale < 20（[EventTriggerParams.transferRumorMoraleThreshold]）+ 概率 30%
     * 严重等级：CRITICAL（士气影响 -15）
     */
    private suspend fun tryTriggerTransferRumor(
        morale: PlayerMoraleEntity,
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): PlayerEmotionEventEntity? {
        if (morale.morale >= config.eventTrigger.transferRumorMoraleThreshold) return null
        // 概率门控
        if (Math.random() > config.eventTrigger.transferRumorProbability) return null
        val type = PlayerEmotionEventType.TRANSFER_RUMOR
        if (isOverSeasonLimit(saveId, clubId, seasonId, type, eventDao)) return null
        return writeEvent(
            saveId, clubId, morale.playerId, seasonId, currentDate,
            type, EventSeverity.CRITICAL,
            description = "媒体爆出转会传闻，球员士气极度低迷（${morale.morale}）",
            moraleImpact = -15,
            eventDao = eventDao
        )
    }

    /**
     * 触发 RENEWAL_REQUEST 续约要求事件（V0.2 + 实现方案 §四.6）。
     *
     * 条件：合同剩余 < 12 个月（[DressingRoomConfig.renewalRequestContractMonthsThreshold]）
     * 严重等级：MODERATE（士气影响 -5）
     */
    private suspend fun tryTriggerRenewalRequest(
        morale: PlayerMoraleEntity,
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): PlayerEmotionEventEntity? {
        val playerState = try {
            databaseManager.savePlayerStateDao().getByPlayer(saveId, morale.playerId)
        } catch (_: Exception) { null } ?: return null
        val contractUntil = playerState.contractUntil ?: return null
        val monthsLeft = try {
            java.time.temporal.ChronoUnit.MONTHS.between(
                currentDate,
                LocalDate.parse(contractUntil)
            ).toInt()
        } catch (_: Exception) { return null }
        if (monthsLeft >= config.renewalRequestContractMonthsThreshold) return null
        // 仅在 6 个月内触发一次（避免重复）
        val type = PlayerEmotionEventType.RENEWAL_REQUEST
        if (isOverSeasonLimit(saveId, clubId, seasonId, type, eventDao)) return null
        return writeEvent(
            saveId, clubId, morale.playerId, seasonId, currentDate,
            type, EventSeverity.MODERATE,
            description = "合同仅剩 $monthsLeft 个月，球员要求启动续约谈判",
            moraleImpact = -5,
            eventDao = eventDao
        )
    }

    /**
     * 触发 NEW_SIGNING_STRUGGLE 新援融入困难事件（V0.2 + 实现方案 §四.6）。
     *
     * 条件：last_updated_date 距今 ≤ 30 天（[DressingRoomConfig.newSigningStruggleDays]）+ morale < 40
     * 严重等级：MINOR（士气影响 -2）
     */
    private suspend fun tryTriggerNewSigningStruggle(
        morale: PlayerMoraleEntity,
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): PlayerEmotionEventEntity? {
        if (morale.morale >= config.eventTrigger.newSigningStruggleMoraleThreshold) return null
        val daysSinceJoin = try {
            java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.parse(morale.lastUpdatedDate),
                currentDate
            ).toInt()
        } catch (_: Exception) { return null }
        if (daysSinceJoin > config.newSigningStruggleDays) return null
        val type = PlayerEmotionEventType.NEW_SIGNING_STRUGGLE
        if (isOverSeasonLimit(saveId, clubId, seasonId, type, eventDao)) return null
        return writeEvent(
            saveId, clubId, morale.playerId, seasonId, currentDate,
            type, EventSeverity.MINOR,
            description = "新援加入 $daysSinceJoin 天内士气低迷（${morale.morale}），融入困难",
            moraleImpact = -2,
            eventDao = eventDao
        )
    }

    /**
     * 触发 CONFLICT 球员冲突事件（V0.2 + 实现方案 §四.6）。
     *
     * 条件：2 名球员均 morale < 30 + 概率 15%
     * 严重等级：MAJOR（士气影响 -10，全队 -3）
     */
    private suspend fun tryTriggerConflict(
        allMorales: List<PlayerMoraleEntity>,
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): PlayerEmotionEventEntity? {
        val unhappy = allMorales.filter {
            it.morale < config.eventTrigger.conflictMoraleThreshold
        }
        if (unhappy.size < 2) return null
        // 概率门控
        if (Math.random() > config.eventTrigger.conflictProbability) return null
        val type = PlayerEmotionEventType.CONFLICT
        if (isOverSeasonLimit(saveId, clubId, seasonId, type, eventDao)) return null
        // 选 2 名最不满的球员
        val pair = unhappy.sortedBy { it.morale }.take(2)
        val involvedIds = pair.joinToString(",") { it.playerId.toString() }
        return writeEvent(
            saveId = saveId,
            clubId = clubId,
            playerId = pair.first().playerId,
            seasonId = seasonId,
            currentDate = currentDate,
            type = type,
            severity = EventSeverity.MAJOR,
            description = "更衣室内 ${pair.size} 名球员发生冲突（涉及：$involvedIds）",
            moraleImpact = -3, // 全队士气影响
            involvedPlayerIds = involvedIds,
            eventDao = eventDao
        )
    }

    /**
     * 显式触发 VETERAN_FAREWELL 老将告别事件（V0.2 + 实现方案 §四.6）。
     *
     * 条件：年龄 ≥33（[DressingRoomConfig.veteranAgeThreshold]）
     * 严重等级：MINOR（士气影响 +2，告别是正向激励）
     *
     * 由 T19 赛季归档 / T07 赛季末推进显式调用。
     *
     * @param playerId 老将球员 ID
     * @param age 球员年龄
     * @return 写入的事件实体，若不满足条件返回 null
     */
    suspend fun triggerVeteranFarewell(
        saveId: Int,
        clubId: Int,
        playerId: Int,
        age: Int,
        seasonId: Int,
        currentDate: LocalDate
    ): PlayerEmotionEventEntity? = withContext(Dispatchers.IO) {
        if (age < config.veteranAgeThreshold) return@withContext null
        val type = PlayerEmotionEventType.VETERAN_FAREWELL
        val eventDao = databaseManager.playerEmotionEventDao()
        if (isOverSeasonLimit(saveId, clubId, seasonId, type, eventDao)) return@withContext null
        val entity = writeEvent(
            saveId, clubId, playerId, seasonId, currentDate,
            type, EventSeverity.MINOR,
            description = "$age 岁老将宣布赛季末退役，全队为其送别",
            moraleImpact = 2, // 正向激励
            eventDao = eventDao
        )
        applyMoraleImpact(saveId, clubId, 2, currentDate)
        entity
    }

    // ==================== 3. 共用工具 ====================

    /**
     * 检查事件类型是否已达本赛季上限（V0.2 + 实现方案 §四.6）。
     */
    private suspend fun isOverSeasonLimit(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        type: PlayerEmotionEventType,
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): Boolean {
        val limit = config.eventLimits[type.name] ?: return false
        val current = eventDao.countByTypeThisSeason(saveId, clubId, type.name, seasonId)
        return current >= limit
    }

    /**
     * 写入事件实体到 player_emotion_event 表。
     */
    private suspend fun writeEvent(
        saveId: Int,
        clubId: Int,
        playerId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        type: PlayerEmotionEventType,
        severity: EventSeverity,
        description: String,
        moraleImpact: Int,
        involvedPlayerIds: String = "",
        eventDao: com.greendynasty.football.dressingroom.model.PlayerEmotionEventDao
    ): PlayerEmotionEventEntity {
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entity = PlayerEmotionEventEntity(
            saveId = saveId,
            clubId = clubId,
            playerId = playerId,
            eventDate = dateStr,
            eventSeason = seasonId,
            eventType = type.name,
            severity = severity.name,
            involvedPlayerIds = involvedPlayerIds,
            description = description,
            moraleImpact = moraleImpact
        )
        eventDao.insert(entity)
        return entity
    }

    /**
     * 应用事件士气影响到全队（V0.2 + 实现方案 §四.6）。
     *
     * V1 简化：对所有球员士气 ± moraleImpact（钳制 0-100）。
     */
    private suspend fun applyMoraleImpact(
        saveId: Int,
        clubId: Int,
        moraleImpact: Int,
        currentDate: LocalDate
    ) {
        if (moraleImpact == 0) return
        val dao = databaseManager.playerMoraleDao()
        val all = dao.getByClub(saveId, clubId)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        all.forEach { entity ->
            val newMorale = (entity.morale + moraleImpact).coerceIn(0, 100)
            val newLevel = com.greendynasty.football.dressingroom.model.MoraleLevel.fromScore(newMorale)
            dao.upsert(
                entity.copy(
                    morale = newMorale,
                    moraleLevel = newLevel.name,
                    lastUpdatedDate = dateStr
                )
            )
        }
    }

    // ==================== 4. 事件查询 ====================

    /**
     * 查询俱乐部最近事件（用于 UI 列表）。
     */
    suspend fun getRecentEvents(
        saveId: Int,
        clubId: Int,
        limit: Int = 50
    ): List<PlayerEmotionEventEntity> = withContext(Dispatchers.IO) {
        databaseManager.playerEmotionEventDao().getRecent(saveId, clubId, limit)
    }

    /**
     * 解决事件（玩家处理后标记为已解决）。
     */
    suspend fun resolveEvent(
        eventId: Long,
        resolution: String,
        currentDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        databaseManager.playerEmotionEventDao().resolve(eventId, resolution, dateStr)
    }
}
