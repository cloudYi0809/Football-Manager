package com.greendynasty.football.data.importer.writer

import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greendynasty.football.data.history.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批量写入器（事务支持 + 分批提交）
 *
 * 提供两种写入模式：
 * 1. [writeBatch]：List 分批写入，由调用方提供批量 writer（如 DAO @Insert List<T>）
 * 2. [writeWithSavepoint]：Sequence 流式写入 + Savepoint 错误恢复，适用于大文件（10000+ 球员）
 *
 * 所有写入操作包裹在 `database.withTransaction { }` 单大事务中，保证原子性。
 * 内部按 [batchSize] 分批，每批失败时回滚至 savepoint 并退化为逐条插入，定位错误行。
 *
 * @param database   目标 Room 数据库
 * @param batchSize  每批行数（默认 500）
 */
class BatchWriter(
    val database: HistoryDatabase,
    val batchSize: Int = 500
) {

    /**
     * 批量写入：将 [items] 按 [batchSize] 分批，在单事务中调用 [writer] 处理每批。
     *
     * @param items  待写入列表
     * @param writer 批量写入函数（接收一个 batch，执行如 DAO.insertAll）
     */
    suspend fun <T> writeBatch(items: List<T>, writer: suspend (List<T>) -> Unit) {
        if (items.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                items.chunked(batchSize).forEach { batch ->
                    writer(batch)
                }
            }
        }
    }

    /**
     * 流式写入 + Savepoint 错误恢复：避免全量加载到内存。
     *
     * 用于球员等大数据量表（10000+ 行）。内部按 [batchSize] 缓冲，
     * 每批用 SQL SAVEPOINT 包裹：
     * - 批量成功 → RELEASE SAVEPOINT
     * - 批量失败 → ROLLBACK TO SAVEPOINT，退化为逐条插入定位错误行
     *
     * @param items  待写入序列（Sequence 惰性求值）
     * @param writer 单条写入函数（如 { entity -> db.insert(...) }）
     * @return 成功写入的行数
     */
    suspend fun <T> writeWithSavepoint(items: Sequence<T>, writer: suspend (T) -> Unit): Int {
        var successCount = 0
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                var batchIndex = 0
                val buffer = mutableListOf<T>()

                for (item in items) {
                    buffer.add(item)
                    if (buffer.size >= batchSize) {
                        successCount += writeBatchWithSavepoint(db, batchIndex++, buffer, writer)
                        buffer.clear()
                    }
                }
                if (buffer.isNotEmpty()) {
                    successCount += writeBatchWithSavepoint(db, batchIndex++, buffer, writer)
                }
            }
        }
        return successCount
    }

    /**
     * 单批写入 + Savepoint 保护：
     * 1. 先尝试整批写入（性能最优）
     * 2. 整批失败 → 回滚到 savepoint → 退化为逐条插入，定位错误行
     */
    private suspend fun <T> writeBatchWithSavepoint(
        db: SupportSQLiteDatabase,
        batchIndex: Int,
        batch: List<T>,
        writer: suspend (T) -> Unit
    ): Int {
        val spName = "imp_batch_$batchIndex"
        var count = 0
        db.execSQL("SAVEPOINT $spName")
        try {
            for (entity in batch) {
                writer(entity)
                count++
            }
            db.execSQL("RELEASE SAVEPOINT $spName")
            return count
        } catch (e: Exception) {
            // 整批失败：回滚到 savepoint
            db.execSQL("ROLLBACK TO SAVEPOINT $spName")
            db.execSQL("RELEASE SAVEPOINT $spName")
        }
        // 退化为逐条插入，定位错误行
        count = 0
        for (entity in batch) {
            val itemSp = "imp_item_${batchIndex}_$count"
            db.execSQL("SAVEPOINT $itemSp")
            try {
                writer(entity)
                db.execSQL("RELEASE SAVEPOINT $itemSp")
                count++
            } catch (e2: Exception) {
                db.execSQL("ROLLBACK TO SAVEPOINT $itemSp")
                db.execSQL("RELEASE SAVEPOINT $itemSp")
                // 单条失败，跳过继续后续
            }
        }
        return count
    }
}
