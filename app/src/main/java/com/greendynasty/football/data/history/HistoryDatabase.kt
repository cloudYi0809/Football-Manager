package com.greendynasty.football.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greendynasty.football.data.history.dao.AgentDao
import com.greendynasty.football.data.history.dao.ClubDao
import com.greendynasty.football.data.history.dao.CompetitionDao
import com.greendynasty.football.data.history.dao.DataPackManifestDao
import com.greendynasty.football.data.history.dao.HistoricalProspectDao
import com.greendynasty.football.data.history.dao.MatchDao
import com.greendynasty.football.data.history.dao.PlayerDao
import com.greendynasty.football.data.history.dao.ScoutDao
import com.greendynasty.football.data.history.dao.SeasonDao
import com.greendynasty.football.data.history.dao.SquadMembershipDao
import com.greendynasty.football.data.history.dao.StaffDao
import com.greendynasty.football.data.history.dao.TransferHistoryDao
import com.greendynasty.football.data.history.dao.YouthAcademyDao
import com.greendynasty.football.data.history.entity.AgentEntity
import com.greendynasty.football.data.history.entity.ClubCompetitionSeasonEntity
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.CompetitionEntity
import com.greendynasty.football.data.history.entity.DataPackManifestEntity
import com.greendynasty.football.data.history.entity.HistoricalEventEntity
import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.history.entity.ScoutEntity
import com.greendynasty.football.data.history.entity.SeasonEntity
import com.greendynasty.football.data.history.entity.SquadMembershipEntity
import com.greendynasty.football.data.history.entity.StaffEntity
import com.greendynasty.football.data.history.entity.TransferHistoryEntity
import com.greendynasty.football.data.history.entity.YouthAcademyEntity
import java.io.File

/**
 * 历史数据库（history.db，只读）
 *
 * 随安装包/数据包提供，玩家不可修改。
 * 包含球员、俱乐部、赛季、赛事、比赛、转会历史等只读数据。
 *
 * 通过 [create] 方法创建实例时，会在 onOpen 回调中执行 PRAGMA query_only = ON 强制只读。
 */
@Database(
    entities = [
        PlayerEntity::class,
        PlayerAttributesEntity::class,
        ClubEntity::class,
        SeasonEntity::class,
        CompetitionEntity::class,
        ClubCompetitionSeasonEntity::class,
        SquadMembershipEntity::class,
        TransferHistoryEntity::class,
        MatchEntity::class,
        HistoricalProspectPoolEntity::class,
        ScoutEntity::class,
        StaffEntity::class,
        AgentEntity::class,
        YouthAcademyEntity::class,
        HistoricalEventEntity::class,
        DataPackManifestEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao
    abstract fun clubDao(): ClubDao
    abstract fun seasonDao(): SeasonDao
    abstract fun competitionDao(): CompetitionDao
    abstract fun matchDao(): MatchDao
    abstract fun squadMembershipDao(): SquadMembershipDao
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun historicalProspectDao(): HistoricalProspectDao
    abstract fun scoutDao(): ScoutDao
    abstract fun staffDao(): StaffDao
    abstract fun agentDao(): AgentDao
    abstract fun youthAcademyDao(): YouthAcademyDao
    abstract fun dataPackManifestDao(): DataPackManifestDao

    companion object {
        const val DATABASE_NAME = "history.db"

        /**
         * 创建只读的 HistoryDatabase 实例。
         *
         * @param context 上下文
         * @param dbFile 数据库文件路径（通常从 assets 复制到内部存储后传入）
         * @return 只读 HistoryDatabase 实例
         */
        fun create(context: Context, dbFile: File): HistoryDatabase {
            return Room.databaseBuilder(context, HistoryDatabase::class.java, dbFile.absolutePath)
                .setJournalMode(JournalMode.TRUNCATE) // 只读库用 TRUNCATE 节省空间
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // 强制只读：禁止任何写操作
                        db.execSQL("PRAGMA query_only = ON")
                    }
                })
                .build()
        }
    }
}
