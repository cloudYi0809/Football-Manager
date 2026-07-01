package com.greendynasty.football.data.save

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode
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
import com.greendynasty.football.data.save.entity.AiDecisionLogEntity
import com.greendynasty.football.data.save.entity.ButterflyEventEntity
import com.greendynasty.football.data.save.entity.ButterflyImpactNodeEntity
import com.greendynasty.football.data.save.entity.CheckpointEntity
import com.greendynasty.football.data.save.entity.ClubAiProfileEntity
import com.greendynasty.football.data.save.entity.EconomyIndexEntity
import com.greendynasty.football.data.save.entity.LeagueEconomyProfileEntity
import com.greendynasty.football.data.save.entity.PerfLogEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveCupTieEntity
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SaveLeagueTableEntity
import com.greendynasty.football.data.save.entity.SaveManifestEntity
import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.data.save.entity.SaveScheduleStateEntity
import com.greendynasty.football.data.save.entity.SaveTransferOfferEntity
import com.greendynasty.football.data.save.entity.SaveWorldStateEntity
import com.greendynasty.football.data.save.entity.ScoutAssignmentEntity
import com.greendynasty.football.data.save.entity.ScoutReportEntity
import com.greendynasty.football.data.save.entity.SeasonArchiveEntity
import com.greendynasty.football.injury.model.MedicalFacilityDao
import com.greendynasty.football.injury.model.MedicalFacilityEntity
import java.io.File

/**
 * 存档数据库（save_xxx.db，可写）
 *
 * 每个存档对应一个独立的 save_<saveId>.db 文件，玩家所有修改都在这里。
 * 包含存档元数据、世界状态、球员/俱乐部状态、转会报价、球探任务、
 * AI 画像、蝴蝶效应、经济指数、赛季归档、检查点、性能日志等。
 *
 * 使用 WAL 日志模式提升并发写入性能。按 saveId 打开不同的存档文件。
 */
@Database(
    entities = [
        SaveManifestEntity::class,
        SaveWorldStateEntity::class,
        SavePlayerStateEntity::class,
        SaveClubStateEntity::class,
        SaveMatchEntity::class,
        SaveLeagueTableEntity::class,
        SaveNewsEntity::class,
        SaveInjuryEntity::class,
        SaveTransferOfferEntity::class,
        ScoutAssignmentEntity::class,
        ScoutReportEntity::class,
        ClubAiProfileEntity::class,
        AiDecisionLogEntity::class,
        ButterflyEventEntity::class,
        ButterflyImpactNodeEntity::class,
        EconomyIndexEntity::class,
        LeagueEconomyProfileEntity::class,
        SeasonArchiveEntity::class,
        CheckpointEntity::class,
        PerfLogEntity::class,
        SaveCupTieEntity::class,
        SaveScheduleStateEntity::class,
        MedicalFacilityEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SaveDatabase : RoomDatabase() {

    abstract fun saveManifestDao(): SaveManifestDao
    abstract fun saveWorldStateDao(): SaveWorldStateDao
    abstract fun savePlayerStateDao(): SavePlayerStateDao
    abstract fun saveClubStateDao(): SaveClubStateDao
    abstract fun saveMatchDao(): SaveMatchDao
    abstract fun saveLeagueTableDao(): SaveLeagueTableDao
    abstract fun saveInjuryDao(): SaveInjuryDao
    abstract fun saveTransferOfferDao(): SaveTransferOfferDao
    abstract fun saveNewsDao(): SaveNewsDao
    abstract fun scoutAssignmentDao(): ScoutAssignmentDao
    abstract fun scoutReportDao(): ScoutReportDao
    abstract fun clubAiProfileDao(): ClubAiProfileDao
    abstract fun butterflyEventDao(): ButterflyEventDao
    abstract fun economyIndexDao(): EconomyIndexDao
    abstract fun seasonArchiveDao(): SeasonArchiveDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun perfLogDao(): PerfLogDao
    abstract fun saveCupTieDao(): SaveCupTieDao
    abstract fun saveScheduleStateDao(): SaveScheduleStateDao
    abstract fun medicalFacilityDao(): MedicalFacilityDao

    companion object {
        /**
         * 创建指定存档的 SaveDatabase 实例。
         *
         * @param context 上下文
         * @param saveId 存档 UUID，用于拼出 save_<saveId>.db 文件名
         * @return 可写的 SaveDatabase 实例
         */
        fun create(context: Context, saveId: String): SaveDatabase {
            val dbFile = File(getSaveDir(context), "save_$saveId.db")
            return Room.databaseBuilder(context, SaveDatabase::class.java, dbFile.absolutePath)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // WAL 提升并发写入性能
                .fallbackToDestructiveMigrationOnDowngrade()
                // T08 起 schema 演进（新增 save_medical_facility 表 + save_injury V0.2 字段）。
                // 开发期允许升级时重建表；正式上线后需替换为显式 Migration。
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * 获取存档文件目录（filesDir/saves/）
         */
        fun getSaveDir(context: Context): File {
            val dir = File(context.filesDir, "saves")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }
}
