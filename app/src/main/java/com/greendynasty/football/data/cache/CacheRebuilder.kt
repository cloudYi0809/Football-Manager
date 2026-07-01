package com.greendynasty.football.data.cache

import com.greendynasty.football.data.api.DatabaseConfig
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.cache.entity.ImagePathCacheEntity
import com.greendynasty.football.data.cache.entity.PlayerSearchIndexEntity
import com.greendynasty.football.data.cache.entity.RankingCacheEntity
import com.greendynasty.football.data.cache.entity.StatsCacheEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 缓存重建服务（cache.db，可重建）
 *
 * 从 history.db（只读）和 save.db（可写）中提取数据，重建 cache.db 中的各项缓存。
 * cache.db 可随时删除重建，不影响存档数据。
 *
 * 支持两种模式：
 * - 全量重建：清空指定缓存表后重新构建全部数据
 * - 增量更新：仅更新单个球员/单条记录的缓存
 *
 * 使用方式：
 * ```
 * val rebuilder = CacheRebuilder(DatabaseManager.getInstance(context))
 * rebuilder.rebuildAll()                  // 全量重建所有缓存
 * rebuilder.updatePlayerIndex(playerId)   // 增量更新单个球员索引
 * ```
 *
 * 注意：rebuildAll 需在 history.db 初始化后调用；若需包含存档数据，
 * 需先通过 DatabaseManager.openSave() 加载存档。
 */
class CacheRebuilder(private val databaseManager: DatabaseManager) {

    /** 日期格式化器（ISO 格式） */
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** 开档参考日期（用于计算球员年龄） */
    private val referenceDate: LocalDate = LocalDate.parse(DatabaseConfig.DEFAULT_START_DATE)

    // ==================== 全量重建 ====================

    /**
     * 全量重建所有缓存（球员搜索索引 + 排名缓存 + 统计缓存 + 图片路径缓存）。
     *
     * 调用前提：history.db 已通过 [DatabaseManager.initHistoryDatabase] 初始化。
     * 若 save.db 已打开，会同时读取存档数据以丰富索引内容。
     */
    suspend fun rebuildAll() = withContext(Dispatchers.IO) {
        rebuildPlayerSearchIndex()
        rebuildRankingCache()
        rebuildStatsCache()
        rebuildImagePathCache()
    }

    /**
     * 重建球员搜索索引（player_search_index 表）。
     *
     * 数据来源：
     * - history.db.player：球员姓名、位置、出生日期、国籍
     * - save.db.save_player_state（若存档已加载）：当前 CA、当前俱乐部、身价
     * - 若存档未加载，current_ca 使用默认值 50，current_club_id 为 null
     */
    suspend fun rebuildPlayerSearchIndex() = withContext(Dispatchers.IO) {
        val cacheDb = databaseManager.getCacheDatabase()

        // 1. 清空旧索引
        cacheDb.playerSearchIndexDao().clear()

        // 2. 从 history.db 读取所有球员
        val historyDb = databaseManager.getHistoryDatabase()
        val players = historyDb.playerDao().getAllPlayers().first()

        // 3. 尝试读取存档中的球员状态（可选）
        val saveStates: Map<Int, SavePlayerStateEntity> = loadSavePlayerStates()

        // 4. 逐球员构建搜索索引
        val indices = players.map { player ->
            val saveState = saveStates[player.playerId]
            buildSearchIndex(player, saveState)
        }

        // 5. 批量写入
        if (indices.isNotEmpty()) {
            cacheDb.playerSearchIndexDao().insertAll(indices)
        }
    }

    /**
     * 重建排名缓存（ranking_cache 表）。
     *
     * 排名缓存依赖联赛积分榜数据，当前为预留框架。
     * T03/T05 实现赛季推进后，将从此处读取 save_league_table 并生成 JSON 缓存。
     */
    suspend fun rebuildRankingCache() = withContext(Dispatchers.IO) {
        val cacheDb = databaseManager.getCacheDatabase()
        // 清空旧缓存
        cacheDb.rankingCacheDao().clear()

        // TODO: T03/T05 赛季推进实现后，从 save.db 读取积分榜数据并生成 ranking_json
        // 当前为预留框架，无实际排名数据可缓存
    }

    /**
     * 重建统计缓存（stats_cache 表）。
     *
     * 统计缓存包括射手榜、助攻榜等，当前为预留框架。
     * T03/T05 实现比赛模拟后，将从此处生成统计 JSON 缓存。
     */
    suspend fun rebuildStatsCache() = withContext(Dispatchers.IO) {
        val cacheDb = databaseManager.getCacheDatabase()
        // 清空旧缓存
        cacheDb.statsCacheDao().clear()

        // TODO: T03/T05 比赛模拟实现后，从 save.db 读取比赛数据并生成 stats_json
        // 当前为预留框架，无实际统计数据可缓存
    }

    /**
     * 重建图片路径缓存（image_path_cache 表）。
     *
     * 从 history.db 读取球员头像路径（portrait_path）和俱乐部 logo 路径（logo_path），
     * 写入缓存表以加速图片查找。
     */
    suspend fun rebuildImagePathCache() = withContext(Dispatchers.IO) {
        val cacheDb = databaseManager.getCacheDatabase()
        // 清空旧缓存
        cacheDb.imagePathCacheDao().clear()

        val historyDb = databaseManager.getHistoryDatabase()
        val now = dateFormatter.format(LocalDateTime.now())

        // 球员头像路径缓存
        val playerCaches = historyDb.playerDao().getAllPlayers().first()
            .filter { !it.portraitPath.isNullOrBlank() }
            .map { player ->
                ImagePathCacheEntity(
                    entityType = "player",
                    entityId = player.playerId,
                    imageType = "portrait",
                    filePath = player.portraitPath!!,
                    updatedAt = now
                )
            }

        // 俱乐部 logo 路径缓存
        val clubCaches = historyDb.clubDao().getAllClubs().first()
            .filter { !it.logoPath.isNullOrBlank() }
            .map { club ->
                ImagePathCacheEntity(
                    entityType = "club",
                    entityId = club.clubId,
                    imageType = "logo",
                    filePath = club.logoPath!!,
                    updatedAt = now
                )
            }

        // 俱乐部球衣路径缓存
        val kitCaches = historyDb.clubDao().getAllClubs().first()
            .filter { !it.kitPath.isNullOrBlank() }
            .map { club ->
                ImagePathCacheEntity(
                    entityType = "club",
                    entityId = club.clubId,
                    imageType = "kit",
                    filePath = club.kitPath!!,
                    updatedAt = now
                )
            }

        val allCaches = playerCaches + clubCaches + kitCaches
        if (allCaches.isNotEmpty()) {
            cacheDb.imagePathCacheDao().insertAll(allCaches)
        }
    }

    // ==================== 增量更新 ====================

    /**
     * 增量更新单个球员的搜索索引。
     * 若该球员已存在于索引中，则替换；否则新增。
     *
     * @param playerId 球员 ID
     */
    suspend fun updatePlayerIndex(playerId: Int) = withContext(Dispatchers.IO) {
        val historyDb = databaseManager.getHistoryDatabase()
        val player = historyDb.playerDao().getPlayer(playerId) ?: return@withContext

        val saveState = loadSavePlayerState(playerId)
        val index = buildSearchIndex(player, saveState)

        databaseManager.getCacheDatabase().playerSearchIndexDao().insert(index)
    }

    /**
     * 增量更新单个排名缓存项。
     *
     * @param cacheKey 缓存键（如 "league_table_EPL_2003"）
     * @param rankingJson 排名 JSON 数据
     * @param expiresAt 过期时间（可选）
     */
    suspend fun updateRankingCache(
        cacheKey: String,
        rankingJson: String,
        expiresAt: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = dateFormatter.format(LocalDateTime.now())
        val entity = RankingCacheEntity(
            cacheKey = cacheKey,
            rankingJson = rankingJson,
            updatedAt = now,
            expiresAt = expiresAt
        )
        databaseManager.getCacheDatabase().rankingCacheDao().insert(entity)
    }

    /**
     * 增量更新单个统计缓存项。
     *
     * @param cacheKey 缓存键（如 "scorer_list_EPL_2003"）
     * @param statsJson 统计 JSON 数据
     */
    suspend fun updateStatsCache(
        cacheKey: String,
        statsJson: String
    ) = withContext(Dispatchers.IO) {
        val now = dateFormatter.format(LocalDateTime.now())
        val entity = StatsCacheEntity(
            cacheKey = cacheKey,
            statsJson = statsJson,
            updatedAt = now
        )
        databaseManager.getCacheDatabase().statsCacheDao().insert(entity)
    }

    /**
     * 清理过期的排名缓存。
     *
     * @param now 当前时间字符串（ISO 格式）
     */
    suspend fun cleanExpiredRankingCache(now: String) = withContext(Dispatchers.IO) {
        databaseManager.getCacheDatabase().rankingCacheDao().deleteExpired(now)
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 尝试加载存档中所有球员的状态。
     * 若存档未加载，返回空 Map。
     *
     * @return playerId -> SavePlayerStateEntity 的映射
     */
    private suspend fun loadSavePlayerStates(): Map<Int, SavePlayerStateEntity> {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return emptyMap()
        return try {
            // save_world_state 的主键 save_id（Int），每个存档库只有一条，默认为 1
            val worldState = saveDb.saveWorldStateDao().get()
                ?: return emptyMap()
            saveDb.savePlayerStateDao().getAll(worldState.saveId)
                .associateBy { it.playerId }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 尝试加载存档中单个球员的状态。
     * 若存档未加载，返回 null。
     *
     * @param playerId 球员 ID
     * @return 球员存档状态，或 null
     */
    private suspend fun loadSavePlayerState(playerId: Int): SavePlayerStateEntity? {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return null
        return try {
            val worldState = saveDb.saveWorldStateDao().get() ?: return null
            saveDb.savePlayerStateDao().getByPlayer(worldState.saveId, playerId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根据球员基础信息和存档状态构建搜索索引实体。
     *
     * @param player history.db 中的球员基础信息
     * @param saveState save.db 中的球员存档状态（可为 null）
     * @return 搜索索引实体
     */
    private fun buildSearchIndex(
        player: PlayerEntity,
        saveState: SavePlayerStateEntity?
    ): PlayerSearchIndexEntity {
        val normalizedName = player.realName.lowercase()
        val searchTokens = buildSearchTokens(player.realName, player.nationality)
        val currentClubId = saveState?.currentClubId
        val currentCa = saveState?.currentCa ?: DEFAULT_CA
        val age = calculateAge(player.birthDate)
        val position = player.primaryPosition ?: DEFAULT_POSITION
        val marketValue = saveState?.marketValue ?: 0

        return PlayerSearchIndexEntity(
            playerId = player.playerId,
            normalizedName = normalizedName,
            searchTokens = searchTokens,
            currentClubId = currentClubId,
            currentCa = currentCa,
            age = age,
            position = position,
            marketValue = marketValue
        )
    }

    /**
     * 构建搜索分词。
     * 将姓名按空格、连字符、点号分割后小写，并附加国籍 token。
     *
     * @param name 球员姓名
     * @param nationality 国籍（可选，作为附加搜索 token）
     * @return 空格分隔的搜索 token 字符串
     */
    private fun buildSearchTokens(name: String, nationality: String? = null): String {
        val tokens = name.lowercase()
            .split(" ", "-", ".")
            .filter { it.isNotBlank() }
            .toMutableList()

        if (!nationality.isNullOrBlank()) {
            tokens.add(nationality.lowercase())
        }

        return tokens.joinToString(" ")
    }

    /**
     * 根据出生日期计算球员在开档日期时的年龄。
     *
     * @param birthDate 出生日期字符串（格式 yyyy-MM-dd）
     * @return 年龄，若无法解析则返回默认值 25
     */
    private fun calculateAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return DEFAULT_AGE
        return try {
            val birth = LocalDate.parse(birthDate)
            ChronoUnit.YEARS.between(birth, referenceDate).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            DEFAULT_AGE
        }
    }

    companion object {
        /** 默认 CA（无存档数据时） */
        private const val DEFAULT_CA = 50

        /** 默认年龄（出生日期无法解析时） */
        private const val DEFAULT_AGE = 25

        /** 默认位置（球员位置为空时） */
        private const val DEFAULT_POSITION = "Unknown"
    }
}
