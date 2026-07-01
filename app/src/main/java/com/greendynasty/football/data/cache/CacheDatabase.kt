package com.greendynasty.football.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode
import com.greendynasty.football.data.cache.dao.ImagePathCacheDao
import com.greendynasty.football.data.cache.dao.PlayerSearchIndexDao
import com.greendynasty.football.data.cache.dao.RankingCacheDao
import com.greendynasty.football.data.cache.dao.StatsCacheDao
import com.greendynasty.football.data.cache.entity.ImagePathCacheEntity
import com.greendynasty.football.data.cache.entity.PlayerSearchIndexEntity
import com.greendynasty.football.data.cache.entity.RankingCacheEntity
import com.greendynasty.football.data.cache.entity.StatsCacheEntity
import java.io.File

/**
 * 缓存数据库（cache.db，可重建）
 *
 * 可随时删除重建，不影响存档数据。
 * 包含球员搜索索引、积分榜缓存、统计缓存、图片路径缓存等。
 *
 * 使用 TRUNCATE 日志模式节省空间。可通过 [rebuild] 删除重建。
 */
@Database(
    entities = [
        PlayerSearchIndexEntity::class,
        RankingCacheEntity::class,
        StatsCacheEntity::class,
        ImagePathCacheEntity::class
    ],
    version = 1,
    exportSchema = false  // cache.db 可重建，无需导出 schema
)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun playerSearchIndexDao(): PlayerSearchIndexDao
    abstract fun rankingCacheDao(): RankingCacheDao
    abstract fun statsCacheDao(): StatsCacheDao
    abstract fun imagePathCacheDao(): ImagePathCacheDao

    companion object {
        const val DATABASE_NAME = "cache.db"

        /**
         * 创建 CacheDatabase 实例。
         *
         * @param context 上下文
         * @return 可读写的 CacheDatabase 实例
         */
        fun create(context: Context): CacheDatabase {
            return Room.databaseBuilder(context, CacheDatabase::class.java, DATABASE_NAME)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }

        /**
         * 删除并重建 cache.db。
         * 关闭现有连接后删除文件，下次调用 [create] 时会自动重建。
         *
         * @param context 上下文
         * @return true 表示删除成功
         */
        fun rebuild(context: Context): Boolean {
            // 删除 cache.db 及其 WAL/SHM 临时文件
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")
            walFile.delete()
            shmFile.delete()
            return dbFile.delete()
        }
    }
}
