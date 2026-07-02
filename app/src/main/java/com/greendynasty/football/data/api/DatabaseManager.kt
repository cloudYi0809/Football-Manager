package com.greendynasty.football.data.api

import android.content.Context
import com.greendynasty.football.data.cache.CacheDatabase
import com.greendynasty.football.data.cache.dao.ImagePathCacheDao
import com.greendynasty.football.data.cache.dao.PlayerSearchIndexDao
import com.greendynasty.football.data.cache.dao.RankingCacheDao
import com.greendynasty.football.data.cache.dao.StatsCacheDao
import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.dao.AgentDao
import com.greendynasty.football.data.history.dao.ClubDao
import com.greendynasty.football.data.history.dao.CompetitionDao
import com.greendynasty.football.data.history.dao.HistoricalProspectDao
import com.greendynasty.football.data.history.dao.MatchDao
import com.greendynasty.football.data.history.dao.PlayerDao
import com.greendynasty.football.data.history.dao.ScoutDao
import com.greendynasty.football.data.history.dao.SeasonDao
import com.greendynasty.football.data.history.dao.SquadMembershipDao
import com.greendynasty.football.data.history.dao.StaffDao
import com.greendynasty.football.data.history.dao.TransferHistoryDao
import com.greendynasty.football.data.save.SaveDatabase
import com.greendynasty.football.data.save.dao.ButterflyEventDao
import com.greendynasty.football.data.save.dao.CheckpointDao
import com.greendynasty.football.data.save.dao.ClubAiProfileDao
import com.greendynasty.football.data.save.dao.EconomyIndexDao
import com.greendynasty.football.data.save.dao.PerfLogDao
import com.greendynasty.football.data.save.dao.SaveClubStateDao
import com.greendynasty.football.data.save.dao.SaveCupTieDao
import com.greendynasty.football.data.save.dao.SaveInjuryDao
import com.greendynasty.football.data.save.dao.SaveLeagueTableDao
import com.greendynasty.football.data.save.dao.SaveManifestDao
import com.greendynasty.football.data.save.dao.SaveMatchDao
import com.greendynasty.football.data.save.dao.SaveNewsDao
import com.greendynasty.football.data.save.dao.SavePlayerStateDao
import com.greendynasty.football.data.save.dao.SaveScheduleStateDao
import com.greendynasty.football.data.save.dao.SaveTransferOfferDao
import com.greendynasty.football.data.save.dao.SaveWorldStateDao
import com.greendynasty.football.data.save.dao.ScoutAssignmentDao
import com.greendynasty.football.data.save.dao.ScoutReportDao
import com.greendynasty.football.data.save.dao.SeasonArchiveDao
import com.greendynasty.football.growth.model.GrowthEventDao
import com.greendynasty.football.growth.model.GrowthSnapshotDao
import com.greendynasty.football.growth.model.MonthlyPlayingTimeDao
import com.greendynasty.football.growth.model.MonthlyTrainingRecordDao
import com.greendynasty.football.injury.model.MedicalFacilityDao
import com.greendynasty.football.prospect.data.ProspectPathEventDao
import com.greendynasty.football.prospect.data.ProspectStateDao
import com.greendynasty.football.scouting.data.SaveScoutEventDao
import com.greendynasty.football.scouting.data.SaveScoutHiredDao
import com.greendynasty.football.scouting.data.SaveScoutRegionKnowledgeDao
import com.greendynasty.football.scouting.data.SaveScoutReportDao
import com.greendynasty.football.scouting.data.SaveScoutTaskDao
import com.greendynasty.football.transfer.contract.model.ContractReminderDao
import com.greendynasty.football.transfer.contract.model.ContractRenewalDao
import com.greendynasty.football.transfer.negotiation.model.ContractTermsDao
import com.greendynasty.football.transfer.negotiation.model.NegotiationSessionDao
import com.greendynasty.football.transfer.negotiation.model.OfferRoundDao
import java.io.File

/**
 * 三库管理入口（history.db / save.db / cache.db）
 *
 * 职责：
 * - 管理 history.db（只读，从 assets 复制到内部存储后以只读模式打开）
 * - 管理 save_xxx.db（可写，每个存档一个独立文件，支持多存档切换）
 * - 管理 cache.db（可重建，随时删除重建不影响存档）
 *
 * 使用方式：
 * ```
 * val dbManager = DatabaseManager.getInstance(context)
 * dbManager.initHistoryDatabase()       // 应用启动时初始化历史库
 * dbManager.initCacheDatabase()         // 初始化缓存库
 * dbManager.openSave("uuid-xxx")       // 打开存档
 * // ... 使用 DAO 读写数据 ...
 * dbManager.closeSave()                 // 关闭当前存档
 * ```
 *
 * 注意：同时只能打开一个存档，切换存档前必须先 closeSave()。
 */
class DatabaseManager private constructor(private val context: Context) {

    private val appContext = context.applicationContext

    /** 历史数据库（只读），应用启动后常驻 */
    @Volatile
    private var historyDb: HistoryDatabase? = null

    /** 当前存档数据库（可写），每次 openSave 创建，closeSave 关闭 */
    @Volatile
    private var saveDb: SaveDatabase? = null

    /** 当前打开的存档 ID */
    @Volatile
    private var currentSaveId: String? = null

    /** 缓存数据库（可重建），应用启动后常驻 */
    @Volatile
    private var cacheDb: CacheDatabase? = null

    companion object {
        @Volatile
        private var instance: DatabaseManager? = null

        /**
         * 获取 DatabaseManager 单例实例。
         * 使用 Application context，避免内存泄漏。
         *
         * @param context 任意 Context，内部取 applicationContext
         * @return DatabaseManager 单例
         */
        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ==================== history.db ====================

    /**
     * 初始化 history.db（应用启动时调用）。
     * 从 assets 复制 history.db 到内部存储（filesDir），然后以只读模式打开。
     * 如果文件已存在则直接打开（不重复复制）。
     *
     * @param forceCopy 是否强制重新从 assets 复制（用于数据包更新）
     */
    fun initHistoryDatabase(forceCopy: Boolean = false) {
        val targetFile = File(appContext.filesDir, HistoryDatabase.DATABASE_NAME)

        if (forceCopy || !targetFile.exists()) {
            appContext.assets.open(HistoryDatabase.DATABASE_NAME).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        historyDb = HistoryDatabase.create(appContext, targetFile)
    }

    /** 获取只读历史数据库实例 */
    fun getHistoryDatabase(): HistoryDatabase {
        return historyDb ?: error("history.db 未初始化，请先调用 initHistoryDatabase()")
    }

    // ==================== save.db ====================

    /**
     * 打开指定存档的 save.db。
     * 如果当前已有存档打开，会先关闭旧的连接。
     *
     * @param saveId 存档 UUID
     */
    fun openSave(saveId: String) {
        // 先关闭当前存档
        if (saveDb != null) {
            closeSave()
        }

        currentSaveId = saveId
        saveDb = SaveDatabase.create(appContext, saveId)
    }

    /** 关闭当前存档数据库 */
    fun closeSave() {
        saveDb?.close()
        saveDb = null
        currentSaveId = null
    }

    /** 获取当前打开的存档数据库实例 */
    fun getSaveDatabase(): SaveDatabase {
        return saveDb ?: error("save.db 未加载，请先调用 openSave(saveId)")
    }

    /** 安全获取当前存档数据库实例，未加载时返回 null */
    fun getSaveDatabaseOrNull(): SaveDatabase? = saveDb

    /** 获取当前打开的存档 ID */
    fun getCurrentSaveId(): String? = currentSaveId

    /** 获取 Application context（供 SaveManager 等组件访问文件目录） */
    fun getAppContext(): Context = appContext

    /**
     * 列出所有本地存档文件。
     * @return 存档摘要列表（saveId、最后修改时间、文件大小）
     */
    fun listAllSaves(): List<SaveSummary> {
        val saveDir = SaveDatabase.getSaveDir(appContext)
        return saveDir.listFiles { f -> f.name.startsWith("save_") && f.name.endsWith(".db") }
            ?.map { f ->
                val saveId = f.name.removePrefix("save_").removeSuffix(".db")
                SaveSummary(saveId, f.lastModified(), f.length())
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    /**
     * 删除指定存档（关闭连接 + 删除文件）。
     * @param saveId 存档 UUID
     * @return true 表示删除成功
     */
    fun deleteSave(saveId: String): Boolean {
        // 如果要删除的是当前打开的存档，先关闭
        if (currentSaveId == saveId) {
            closeSave()
        }
        val dbFile = File(SaveDatabase.getSaveDir(appContext), "save_$saveId.db")
        // 同时删除 WAL/SHM 临时文件
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        return dbFile.delete()
    }

    // ==================== cache.db ====================

    /** 初始化缓存数据库 */
    fun initCacheDatabase() {
        cacheDb = CacheDatabase.create(appContext)
    }

    /**
     * 重建 cache.db（关闭连接 + 删除文件 + 重新创建）。
     * 可随时调用，不影响存档数据。
     */
    fun rebuildCache() {
        cacheDb?.close()
        cacheDb = null
        CacheDatabase.rebuild(appContext)
        cacheDb = CacheDatabase.create(appContext)
    }

    /** 获取缓存数据库实例 */
    fun getCacheDatabase(): CacheDatabase {
        if (cacheDb == null) {
            initCacheDatabase()
        }
        return cacheDb!!
    }

    // ==================== DAO 获取方法 ====================

    /**
     * 获取 history.db 的 DAO。
     * @param daoClass DAO 接口的 Class 对象
     * @return 对应的 DAO 实例
     */
    fun <T> getHistoryDao(daoClass: Class<T>): T {
        val db = getHistoryDatabase()
        @Suppress("UNCHECKED_CAST")
        return when (daoClass) {
            PlayerDao::class.java -> db.playerDao() as T
            ClubDao::class.java -> db.clubDao() as T
            SeasonDao::class.java -> db.seasonDao() as T
            CompetitionDao::class.java -> db.competitionDao() as T
            MatchDao::class.java -> db.matchDao() as T
            SquadMembershipDao::class.java -> db.squadMembershipDao() as T
            TransferHistoryDao::class.java -> db.transferHistoryDao() as T
            HistoricalProspectDao::class.java -> db.historicalProspectDao() as T
            ScoutDao::class.java -> db.scoutDao() as T
            StaffDao::class.java -> db.staffDao() as T
            AgentDao::class.java -> db.agentDao() as T
            else -> error("未知的 history DAO 类型: ${daoClass.name}")
        }
    }

    /**
     * 获取 save.db 的 DAO。
     * @param daoClass DAO 接口的 Class 对象
     * @return 对应的 DAO 实例
     */
    fun <T> getSaveDao(daoClass: Class<T>): T {
        val db = getSaveDatabase()
        @Suppress("UNCHECKED_CAST")
        return when (daoClass) {
            SaveManifestDao::class.java -> db.saveManifestDao() as T
            SaveWorldStateDao::class.java -> db.saveWorldStateDao() as T
            SavePlayerStateDao::class.java -> db.savePlayerStateDao() as T
            SaveClubStateDao::class.java -> db.saveClubStateDao() as T
            SaveInjuryDao::class.java -> db.saveInjuryDao() as T
            SaveTransferOfferDao::class.java -> db.saveTransferOfferDao() as T
            SaveNewsDao::class.java -> db.saveNewsDao() as T
            ScoutAssignmentDao::class.java -> db.scoutAssignmentDao() as T
            ScoutReportDao::class.java -> db.scoutReportDao() as T
            ClubAiProfileDao::class.java -> db.clubAiProfileDao() as T
            ButterflyEventDao::class.java -> db.butterflyEventDao() as T
            EconomyIndexDao::class.java -> db.economyIndexDao() as T
            SeasonArchiveDao::class.java -> db.seasonArchiveDao() as T
            CheckpointDao::class.java -> db.checkpointDao() as T
            PerfLogDao::class.java -> db.perfLogDao() as T
            SaveCupTieDao::class.java -> db.saveCupTieDao() as T
            SaveScheduleStateDao::class.java -> db.saveScheduleStateDao() as T
            SaveMatchDao::class.java -> db.saveMatchDao() as T
            SaveLeagueTableDao::class.java -> db.saveLeagueTableDao() as T
            MedicalFacilityDao::class.java -> db.medicalFacilityDao() as T
            GrowthSnapshotDao::class.java -> db.growthSnapshotDao() as T
            MonthlyTrainingRecordDao::class.java -> db.monthlyTrainingRecordDao() as T
            MonthlyPlayingTimeDao::class.java -> db.monthlyPlayingTimeDao() as T
            GrowthEventDao::class.java -> db.growthEventDao() as T
            NegotiationSessionDao::class.java -> db.negotiationSessionDao() as T
            OfferRoundDao::class.java -> db.offerRoundDao() as T
            ContractTermsDao::class.java -> db.contractTermsDao() as T
            ContractRenewalDao::class.java -> db.contractRenewalDao() as T
            ContractReminderDao::class.java -> db.contractReminderDao() as T
            SaveScoutHiredDao::class.java -> db.saveScoutHiredDao() as T
            SaveScoutRegionKnowledgeDao::class.java -> db.saveScoutRegionKnowledgeDao() as T
            SaveScoutTaskDao::class.java -> db.saveScoutTaskDao() as T
            SaveScoutReportDao::class.java -> db.saveScoutReportDao() as T
            SaveScoutEventDao::class.java -> db.saveScoutEventDao() as T
            ProspectStateDao::class.java -> db.prospectStateDao() as T
            ProspectPathEventDao::class.java -> db.prospectPathEventDao() as T
            else -> error("未知的 save DAO 类型: ${daoClass.name}")
        }
    }

    /**
     * 获取 cache.db 的 DAO。
     * @param daoClass DAO 接口的 Class 对象
     * @return 对应的 DAO 实例
     */
    fun <T> getCacheDao(daoClass: Class<T>): T {
        val db = getCacheDatabase()
        @Suppress("UNCHECKED_CAST")
        return when (daoClass) {
            PlayerSearchIndexDao::class.java -> db.playerSearchIndexDao() as T
            RankingCacheDao::class.java -> db.rankingCacheDao() as T
            StatsCacheDao::class.java -> db.statsCacheDao() as T
            ImagePathCacheDao::class.java -> db.imagePathCacheDao() as T
            else -> error("未知的 cache DAO 类型: ${daoClass.name}")
        }
    }

    // ==================== 便捷直接访问方法 ====================

    /** history DAO 便捷访问 */
    fun historyPlayerDao() = getHistoryDatabase().playerDao()
    fun historyClubDao() = getHistoryDatabase().clubDao()
    fun historySeasonDao() = getHistoryDatabase().seasonDao()
    fun historyCompetitionDao() = getHistoryDatabase().competitionDao()
    fun historyMatchDao() = getHistoryDatabase().matchDao()
    fun historySquadMembershipDao() = getHistoryDatabase().squadMembershipDao()
    fun historyTransferHistoryDao() = getHistoryDatabase().transferHistoryDao()
    fun historyProspectDao() = getHistoryDatabase().historicalProspectDao()
    fun historyScoutDao() = getHistoryDatabase().scoutDao()
    fun historyStaffDao() = getHistoryDatabase().staffDao()
    fun historyAgentDao() = getHistoryDatabase().agentDao()

    /** save DAO 便捷访问 */
    fun saveManifestDao() = getSaveDatabase().saveManifestDao()
    fun saveWorldStateDao() = getSaveDatabase().saveWorldStateDao()
    fun savePlayerStateDao() = getSaveDatabase().savePlayerStateDao()
    fun saveClubStateDao() = getSaveDatabase().saveClubStateDao()
    fun saveInjuryDao() = getSaveDatabase().saveInjuryDao()
    fun saveTransferOfferDao() = getSaveDatabase().saveTransferOfferDao()
    fun saveNewsDao() = getSaveDatabase().saveNewsDao()
    fun scoutAssignmentDao() = getSaveDatabase().scoutAssignmentDao()
    fun scoutReportDao() = getSaveDatabase().scoutReportDao()
    fun clubAiProfileDao() = getSaveDatabase().clubAiProfileDao()
    fun butterflyEventDao() = getSaveDatabase().butterflyEventDao()
    fun economyIndexDao() = getSaveDatabase().economyIndexDao()
    fun seasonArchiveDao() = getSaveDatabase().seasonArchiveDao()
    fun checkpointDao() = getSaveDatabase().checkpointDao()
    fun perfLogDao() = getSaveDatabase().perfLogDao()
    fun saveCupTieDao() = getSaveDatabase().saveCupTieDao()
    fun saveScheduleStateDao() = getSaveDatabase().saveScheduleStateDao()
    fun saveMatchDao() = getSaveDatabase().saveMatchDao()
    fun saveLeagueTableDao() = getSaveDatabase().saveLeagueTableDao()
    fun medicalFacilityDao() = getSaveDatabase().medicalFacilityDao()
    // T09 成长月结 DAO 便捷访问
    fun growthSnapshotDao() = getSaveDatabase().growthSnapshotDao()
    fun monthlyTrainingRecordDao() = getSaveDatabase().monthlyTrainingRecordDao()
    fun monthlyPlayingTimeDao() = getSaveDatabase().monthlyPlayingTimeDao()
    fun growthEventDao() = getSaveDatabase().growthEventDao()
    // T11 报价谈判 DAO 便捷访问
    fun negotiationSessionDao() = getSaveDatabase().negotiationSessionDao()
    fun offerRoundDao() = getSaveDatabase().offerRoundDao()
    fun contractTermsDao() = getSaveDatabase().contractTermsDao()
    // T12 合同续约 DAO 便捷访问
    fun contractRenewalDao() = getSaveDatabase().contractRenewalDao()
    fun contractReminderDao() = getSaveDatabase().contractReminderDao()
    // T14 球探任务 DAO 便捷访问
    fun saveScoutHiredDao() = getSaveDatabase().saveScoutHiredDao()
    fun saveScoutRegionKnowledgeDao() = getSaveDatabase().saveScoutRegionKnowledgeDao()
    fun saveScoutTaskDao() = getSaveDatabase().saveScoutTaskDao()
    fun saveScoutReportDao() = getSaveDatabase().saveScoutReportDao()
    fun saveScoutEventDao() = getSaveDatabase().saveScoutEventDao()
    // T15 历史新星池 DAO 便捷访问
    fun prospectStateDao() = getSaveDatabase().prospectStateDao()
    fun prospectPathEventDao() = getSaveDatabase().prospectPathEventDao()

    /** cache DAO 便捷访问 */
    fun playerSearchIndexDao() = getCacheDatabase().playerSearchIndexDao()
    fun rankingCacheDao() = getCacheDatabase().rankingCacheDao()
    fun statsCacheDao() = getCacheDatabase().statsCacheDao()
    fun imagePathCacheDao() = getCacheDatabase().imagePathCacheDao()

    // ==================== 资源清理 ====================

    /**
     * 关闭所有数据库连接（应用退出时调用）。
     */
    fun closeAll() {
        closeSave()
        historyDb?.close()
        historyDb = null
        cacheDb?.close()
        cacheDb = null
    }
}

/**
 * 存档摘要信息（用于存档列表展示）
 */
data class SaveSummary(
    val saveId: String,
    val lastModified: Long,
    val fileSize: Long
)
