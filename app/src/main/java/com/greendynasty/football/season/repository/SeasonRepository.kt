package com.greendynasty.football.season.repository

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SeasonArchiveEntity
import com.greendynasty.football.season.summary.SeasonSummary
import com.greendynasty.football.season.summary.SeasonSummaryGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T19 赛季归档仓库（V0.2 §七）
 *
 * 统一对外提供赛季归档记录的读写访问，封装 save.db `season_archive` 表操作。
 * 供 UI 层（ViewModel）和历史查询服务调用。
 *
 * @param databaseManager 三库管理入口
 * @param summaryGenerator 摘要生成器（用于序列化/反序列化 JSON）
 */
class SeasonRepository(
    private val databaseManager: DatabaseManager,
    private val summaryGenerator: SeasonSummaryGenerator
) {

    /**
     * 获取指定赛季的归档记录。
     *
     * @param saveUuid 存档 UUID
     * @param seasonId 赛季 ID
     * @return 归档记录，不存在返回 null
     */
    suspend fun getSeasonArchive(saveUuid: String, seasonId: Int): SeasonArchiveEntity? =
        withContext(Dispatchers.IO) {
            runCatching {
                val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null
                saveDb.seasonArchiveDao().getBySeason(saveUuid, seasonId).firstOrNull()
            }.getOrNull()
        }

    /**
     * 获取指定赛季的摘要（反序列化）。
     *
     * @param saveUuid 存档 UUID
     * @param seasonId 赛季 ID
     * @return 赛季摘要，不存在或解析失败返回 null
     */
    suspend fun getSeasonSummary(saveUuid: String, seasonId: Int): SeasonSummary? =
        withContext(Dispatchers.IO) {
            val archive = getSeasonArchive(saveUuid, seasonId) ?: return@withContext null
            summaryGenerator.deserialize(archive.summaryJson)
        }

    /**
     * 获取全部归档赛季列表（按赛季 ID 降序）。
     *
     * @param saveUuid 存档 UUID
     * @return 归档记录列表
     */
    suspend fun getAllArchivedSeasons(saveUuid: String): List<SeasonArchiveEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()
                saveDb.seasonArchiveDao().getAll(saveUuid)
            }.getOrDefault(emptyList())
        }

    /**
     * 获取已归档赛季数。
     *
     * @param saveUuid 存档 UUID
     * @return 归档赛季数
     */
    suspend fun getArchivedSeasonCount(saveUuid: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0
            saveDb.seasonArchiveDao().count(saveUuid)
        }.getOrDefault(0)
    }

    companion object {
        private const val TAG = "SeasonRepository"
    }
}
