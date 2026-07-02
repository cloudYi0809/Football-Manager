package com.greendynasty.football.divergence.repository

import com.greendynasty.football.butterfly.model.ButterflyEvent
import com.greendynasty.football.butterfly.repository.ButterflyEventViewItem
import com.greendynasty.football.butterfly.repository.ButterflyRepository
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.divergence.DivergenceTimelineComposer
import com.greendynasty.football.divergence.archive.DivergenceArchiveEntity
import com.greendynasty.football.divergence.generator.DivergenceTextGenerator
import com.greendynasty.football.divergence.model.DivergenceTimelineItem
import com.greendynasty.football.divergence.model.NoReplacementRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T21 分歧日志仓库（任务 T21.4：归档查询 + 复用 T20 ButterflyRepository）。
 *
 * 职责：
 * 1. **复用 T20 查询能力**：通过 [butterflyRepository] 查询当前赛季的蝴蝶事件
 * 2. **归档查询**：通过 divergence_archive DAO 查询历史归档记录
 * 3. **时间线组合**：将当前事件 + 归档记录合并为完整时间线
 *
 * 避免重复造轮子：不直接访问 butterfly_event 表，全部委托 [ButterflyRepository]。
 *
 * @param databaseManager 三库管理入口
 * @param butterflyRepository T20 蝴蝶事件仓库（复用查询能力）
 * @param composer 时间线组合器
 */
class DivergenceRepository(
    private val databaseManager: DatabaseManager,
    private val butterflyRepository: ButterflyRepository,
    private val composer: DivergenceTimelineComposer = DivergenceTimelineComposer()
) {

    // ==================== 1. 当前赛季分歧（复用 ButterflyRepository） ====================

    /**
     * 观察当前存档的蝴蝶事件（Flow 驱动 UI 刷新）。
     *
     * 复用 [ButterflyRepository.observeAllEvents]，返回原始事件列表。
     */
    fun observeCurrentEvents(saveUuid: String): Flow<List<ButterflyEvent>> {
        return butterflyRepository.observeAllEvents(saveUuid)
    }

    /**
     * 加载当前存档的蝴蝶事件视图项列表（含影响节点）。
     *
     * 复用 [ButterflyRepository.getEventViewItems]。
     */
    suspend fun getCurrentViewItems(saveUuid: String): List<ButterflyEventViewItem> =
        withContext(Dispatchers.IO) {
            butterflyRepository.getEventViewItems(saveUuid)
        }

    /**
     * 加载单个事件详情（含影响节点）。
     *
     * 复用 [ButterflyRepository.getEventDetail]。
     */
    suspend fun getEventDetail(eventId: String): ButterflyEventViewItem? =
        withContext(Dispatchers.IO) {
            butterflyRepository.getEventDetail(eventId)
        }

    // ==================== 2. 时间线组合 ====================

    /**
     * 加载当前赛季的分歧时间线（仅未归档事件）。
     *
     * @param saveUuid 存档 UUID
     * @return 时间线条目列表（按触发日期倒序）
     */
    suspend fun getCurrentTimeline(saveUuid: String): List<DivergenceTimelineItem> =
        withContext(Dispatchers.IO) {
            val viewItems = butterflyRepository.getEventViewItems(saveUuid)
            composer.compose(viewItems)
        }

    // ==================== 3. 归档查询 ====================

    /**
     * 观察当前存档的全部归档记录（Flow 驱动 UI 刷新）。
     */
    fun observeArchivedDivergences(saveUuid: String): Flow<List<DivergenceArchiveEntity>> {
        return databaseManager.divergenceArchiveDao().observeAll(saveUuid)
    }

    /**
     * 一次性加载当前存档全部归档记录。
     */
    suspend fun getArchivedDivergences(saveUuid: String): List<DivergenceArchiveEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.divergenceArchiveDao().getAll(saveUuid)
        }

    /**
     * 按赛季加载归档记录。
     */
    suspend fun getArchivedDivergencesBySeason(
        saveUuid: String,
        seasonId: Int
    ): List<DivergenceArchiveEntity> = withContext(Dispatchers.IO) {
        databaseManager.divergenceArchiveDao().getBySeason(saveUuid, seasonId)
    }

    /**
     * 加载已归档的赛季列表。
     */
    suspend fun getArchivedSeasons(saveUuid: String): List<Int> =
        withContext(Dispatchers.IO) {
            databaseManager.divergenceArchiveDao().getArchivedSeasons(saveUuid)
        }

    /**
     * 观察某赛季的归档记录。
     */
    fun observeArchivedBySeason(
        saveUuid: String,
        seasonId: Int
    ): Flow<List<DivergenceArchiveEntity>> {
        return databaseManager.divergenceArchiveDao().observeBySeason(saveUuid, seasonId)
    }

    // ==================== 4. 无重大替代记录查询 ====================

    /**
     * 加载"历史分歧未产生重大替代"记录（任务 T21.5）。
     *
     * @param saveUuid 存档 UUID
     * @return 无重大替代记录列表
     */
    suspend fun getNoReplacementRecords(saveUuid: String): List<NoReplacementRecord> =
        withContext(Dispatchers.IO) {
            val entities = databaseManager.divergenceArchiveDao().getByReplacement(saveUuid, 0)
            entities.map { entity ->
                NoReplacementRecord(
                    eventId = entity.eventId,
                    triggerDate = runCatching { LocalDate.parse(entity.triggerDate) }
                        .getOrDefault(LocalDate.now()),
                    summary = entity.divergenceText,
                    reason = entity.impactSummary,
                    checkedNodeCount = 0
                )
            }
        }

    /**
     * 统计无重大替代记录数。
     */
    suspend fun countNoReplacement(saveUuid: String): Int = withContext(Dispatchers.IO) {
        databaseManager.divergenceArchiveDao().countNoReplacement(saveUuid)
    }

    /**
     * 统计有重大替代记录数。
     */
    suspend fun countWithReplacement(saveUuid: String): Int = withContext(Dispatchers.IO) {
        databaseManager.divergenceArchiveDao().countWithReplacement(saveUuid)
    }

    // ==================== 5. 统计查询 ====================

    /**
     * 当前存档归档记录总数。
     */
    suspend fun getArchiveCount(saveUuid: String): Int = withContext(Dispatchers.IO) {
        databaseManager.divergenceArchiveDao().countBySaveId(saveUuid)
    }

    /**
     * 按分类查询归档记录。
     */
    suspend fun getArchivedByCategory(
        saveUuid: String,
        categoryCode: String
    ): List<DivergenceArchiveEntity> = withContext(Dispatchers.IO) {
        databaseManager.divergenceArchiveDao().getByCategory(saveUuid, categoryCode)
    }
}

/**
 * T21 归档记录视图项（聚合归档实体为 UI 展示单元）。
 *
 * @param entity 归档实体
 * @param sourcePlayerName 源球员名称（V1 简化：null，V2 联表 history.player）
 * @param sourceClubName 源俱乐部名称
 * @param expectedClubName 预期俱乐部名称
 */
data class DivergenceArchiveViewItem(
    val entity: DivergenceArchiveEntity,
    val sourcePlayerName: String?,
    val sourceClubName: String?,
    val expectedClubName: String?
) {
    /** 是否有重大替代。 */
    val hasMajorReplacement: Boolean get() = entity.hasMajorReplacement == 1

    /** 格式化的触发日期。 */
    val formattedDate: String get() = entity.triggerDate
}
