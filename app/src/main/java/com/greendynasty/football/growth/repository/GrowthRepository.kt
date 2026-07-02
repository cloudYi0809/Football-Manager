package com.greendynasty.football.growth.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.data.save.entity.SaveWorldStateEntity
import com.greendynasty.football.growth.model.GrowthEventEntity
import com.greendynasty.football.growth.model.GrowthSnapshotEntity
import com.greendynasty.football.growth.model.MonthlyPlayingTimeEntity
import com.greendynasty.football.growth.model.MonthlyTrainingRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 成长月结数据访问门面（Repository 层，T09 方案 §五.4）
 *
 * 统一封装 [DatabaseManager] 三库 DAO 的成长相关数据操作，对上层 [GrowthMonthlyService]
 * 屏蔽 save.db / history.db 的细节。仅负责纯数据 CRUD，不包含业务逻辑。
 *
 * 数据来源：
 * - save.db：growth_snapshot / growth_event / monthly_training_record / monthly_playing_time /
 *   save_player_state / save_club_state / save_injury / save_world_state
 * - history.db：player / player_attributes（只读，批量查询避免 N+1）
 *
 * 性能铁律：俱乐部月结涉及的批量查询全部走 [getPlayersByIds] / [getLatestAttributesBatch] 等
 * 批量接口，避免对 100+ 球员逐条查询导致 N+1。
 *
 * @param databaseManager 三库入口
 */
class GrowthRepository(
    private val databaseManager: DatabaseManager
) {

    // ==================== 球员状态（save.db） ====================

    /** 查询俱乐部全部球员状态（含一线队 / 预备队 / 外租），月结入口数据源 */
    suspend fun getClubPlayers(saveId: Int, clubId: Int): List<SavePlayerStateEntity> =
        databaseManager.savePlayerStateDao().getByClub(saveId, clubId)

    /** 查询球员状态 */
    suspend fun getPlayerState(saveId: Int, playerId: Int): SavePlayerStateEntity? =
        databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)

    /** 更新球员 CA（月结应用成长） */
    suspend fun updatePlayerCa(saveId: Int, playerId: Int, ca: Int) =
        databaseManager.savePlayerStateDao().updateCa(saveId, playerId, ca)

    /** 更新球员 PA（潜力兑现月度复查） */
    suspend fun updatePlayerPa(saveId: Int, playerId: Int, pa: Int) =
        databaseManager.savePlayerStateDao().updatePa(saveId, playerId, pa)

    // ==================== 俱乐部状态 / 世界状态（save.db） ====================

    /** 查询俱乐部状态 */
    suspend fun getClubState(saveId: Int, clubId: Int): SaveClubStateEntity? =
        databaseManager.saveClubStateDao().getByClub(saveId, clubId)

    /** 查询世界状态（取当前赛季 ID） */
    suspend fun getWorldState(saveId: Int): SaveWorldStateEntity? =
        databaseManager.saveWorldStateDao().getById(saveId)

    // ==================== 球员基础信息与属性（history.db 只读） ====================

    /** 批量查询球员基础信息（避免 N+1） */
    suspend fun getPlayersByIds(playerIds: List<Int>): List<PlayerEntity> =
        databaseManager.historyPlayerDao().getPlayersByIds(playerIds)

    /** 批量查询球员最新赛季属性（避免 N+1，已按 season_id DESC 排序） */
    suspend fun getLatestAttributesBatch(playerIds: List<Int>): List<PlayerAttributesEntity> =
        databaseManager.historyPlayerDao().getLatestAttributesBatch(playerIds)

    /** 单球员最新赛季属性（轻量场景） */
    suspend fun getLatestAttributes(playerId: Int): PlayerAttributesEntity? =
        databaseManager.historyPlayerDao().getLatestAttributes(playerId)

    // ==================== 伤病数据（T08 集成） ====================

    /** 球员当前活跃伤病（T08：用于成长降权） */
    suspend fun getActiveInjury(saveId: Int, playerId: Int): SaveInjuryEntity? =
        databaseManager.saveInjuryDao().getActiveInjury(saveId, playerId)

    /** 俱乐部全部活跃伤病（批量降权时使用） */
    suspend fun getActiveInjuriesByClub(saveId: Int, clubId: Int): List<SaveInjuryEntity> =
        databaseManager.saveInjuryDao().getActiveInjuriesByClub(saveId, clubId)

    /** 球员伤病历史（含已恢复，用于频繁伤病降权与重伤 PA 惩罚） */
    suspend fun getPlayerInjuryHistory(saveId: Int, playerId: Int): List<SaveInjuryEntity> =
        databaseManager.saveInjuryDao().getPlayerInjuryHistory(saveId, playerId)

    // ==================== 月度训练记录（save.db） ====================

    /** 俱乐部某月全部训练记录（月结时聚合输入） */
    suspend fun getTrainingByClubMonth(
        saveId: Int, clubId: Int, month: String
    ): List<MonthlyTrainingRecordEntity> =
        databaseManager.monthlyTrainingRecordDao().getByClubMonth(saveId, clubId, month)

    /** 写入单条月度训练记录（T07 每日训练任务聚合后写入） */
    suspend fun insertTrainingRecord(record: MonthlyTrainingRecordEntity) =
        databaseManager.monthlyTrainingRecordDao().insert(record)

    /** 批量写入月度训练记录 */
    suspend fun insertTrainingRecords(records: List<MonthlyTrainingRecordEntity>) =
        databaseManager.monthlyTrainingRecordDao().insertAll(records)

    // ==================== 月度出场时间（save.db，T09.2） ====================

    /** 俱乐部某月全部出场时间（月结时聚合输入） */
    suspend fun getPlayingTimeByClubMonth(
        saveId: Int, clubId: Int, month: String
    ): List<MonthlyPlayingTimeEntity> =
        databaseManager.monthlyPlayingTimeDao().getByClubMonth(saveId, clubId, month)

    /** 写入单条月度出场时间（T02 比赛引擎赛后写入） */
    suspend fun insertPlayingTime(record: MonthlyPlayingTimeEntity) =
        databaseManager.monthlyPlayingTimeDao().insert(record)

    /** 批量写入月度出场时间 */
    suspend fun insertPlayingTimes(records: List<MonthlyPlayingTimeEntity>) =
        databaseManager.monthlyPlayingTimeDao().insertAll(records)

    // ==================== 成长快照（save.db，T09.3） ====================

    /** 批量写入月度成长快照（事务化，REPLACE 策略保证幂等） */
    suspend fun insertSnapshots(snapshots: List<GrowthSnapshotEntity>) =
        databaseManager.growthSnapshotDao().insertAll(snapshots)

    /** 查询球员全部成长快照（按日期升序，供 T04 成长曲线） */
    suspend fun getSnapshotsByPlayer(saveId: Int, playerId: Int): List<GrowthSnapshotEntity> =
        databaseManager.growthSnapshotDao().getByPlayer(saveId, playerId)

    /** 观察球员成长快照（Flow 驱动 UI） */
    fun observeSnapshotsByPlayer(saveId: Int, playerId: Int): Flow<List<GrowthSnapshotEntity>> =
        databaseManager.growthSnapshotDao().observeByPlayer(saveId, playerId)

    /** 查询球员某赛季快照 */
    suspend fun getSnapshotsByPlayerSeason(
        saveId: Int, playerId: Int, seasonId: Int
    ): List<GrowthSnapshotEntity> =
        databaseManager.growthSnapshotDao().getByPlayerSeason(saveId, playerId, seasonId)

    /** 查询球员最新快照 */
    suspend fun getLatestSnapshot(saveId: Int, playerId: Int): GrowthSnapshotEntity? =
        databaseManager.growthSnapshotDao().getLatest(saveId, playerId)

    /** 查询俱乐部某月全部快照（月结报告） */
    suspend fun getSnapshotsByClubDate(
        saveId: Int, clubId: Int, date: String
    ): List<GrowthSnapshotEntity> =
        databaseManager.growthSnapshotDao().getByClubDate(saveId, clubId, date)

    /** 统计球员在指定日期后 CA 增长低于阈值的月数（停滞检测） */
    suspend fun countStagnationMonths(
        saveId: Int, playerId: Int, startDate: String, deltaThreshold: Int
    ): Int = databaseManager.growthSnapshotDao()
        .countStagnationMonths(saveId, playerId, startDate, deltaThreshold)

    /** 删除指定日期前的旧快照（保留策略） */
    suspend fun deleteSnapshotsBefore(saveId: Int, beforeDate: String): Int =
        databaseManager.growthSnapshotDao().deleteBefore(saveId, beforeDate)

    // ==================== 成长事件（save.db，T09.4） ====================

    /** 批量写入成长事件 */
    suspend fun insertEvents(events: List<GrowthEventEntity>): List<Long> =
        databaseManager.growthEventDao().insertAll(events)

    /** 写入单条成长事件 */
    suspend fun insertEvent(event: GrowthEventEntity): Long =
        databaseManager.growthEventDao().insert(event)

    /** 观察球员成长事件流（按日期倒序） */
    fun observeEventsByPlayer(saveId: Int, playerId: Int): Flow<List<GrowthEventEntity>> =
        databaseManager.growthEventDao().observeByPlayer(saveId, playerId)

    /** 查询球员成长事件 */
    suspend fun getEventsByPlayer(saveId: Int, playerId: Int): List<GrowthEventEntity> =
        databaseManager.growthEventDao().getByPlayer(saveId, playerId)

    /** 查询俱乐部近期事件 */
    suspend fun getRecentEventsByClub(
        saveId: Int, clubId: Int, limit: Int
    ): List<GrowthEventEntity> =
        databaseManager.growthEventDao().getByClubRecent(saveId, clubId, limit)

    /** 查询同月同类型事件（去重判定用） */
    suspend fun getEventByPlayerDateType(
        saveId: Int, playerId: Int, date: String, type: String
    ): GrowthEventEntity? =
        databaseManager.growthEventDao().getByPlayerDateType(saveId, playerId, date, type)

    /** 标记事件已读 */
    suspend fun markEventsRead(ids: List<Int>): Int =
        databaseManager.growthEventDao().markRead(ids)
}
