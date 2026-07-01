package com.greendynasty.football.data.save.management

import android.content.Context
import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.SaveDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 存档删除器
 *
 * 负责存档删除流程（V0.2 §一 + T03 方案）：
 * 1. 二次确认（防止误删）
 * 2. 关闭当前存档连接（若删除的是当前加载存档）
 * 3. 删除 save.db 文件及 WAL/SHM 临时文件
 * 4. 删除 meta.json 元信息文件
 * 5. 清理该存档的全部 checkpoint 文件
 *
 * 删除操作不可恢复，调用方必须确保 [confirmed] 为 true。
 *
 * @param context 应用上下文
 * @param databaseManager 三库管理入口
 */
class SaveDeleter(
    private val context: Context,
    private val databaseManager: DatabaseManager
) {

    /**
     * 删除存档
     *
     * @param saveId 存档 ID
     * @param confirmed 是否已二次确认（必须为 true 才执行删除）
     * @return true 表示删除成功，false 表示未确认或删除失败
     */
    suspend fun delete(saveId: String, confirmed: Boolean): Boolean = withContext(Dispatchers.IO) {
        // 1. 二次确认
        if (!confirmed) {
            Log.w("SaveDeleter", "删除取消：未通过二次确认（saveId=$saveId）")
            return@withContext false
        }

        try {
            // 2. 删除 save.db 文件（含关闭连接 + 清理 WAL/SHM）
            val deleted = databaseManager.deleteSave(saveId)
            if (!deleted) {
                Log.w("SaveDeleter", "save.db 删除失败或文件不存在：saveId=$saveId")
            }

            // 3. 删除 meta.json 元信息文件
            val metaFile = File(SaveDatabase.getSaveDir(context), "save_$saveId.meta.json")
            if (metaFile.exists()) {
                metaFile.delete()
            }

            // 4. 清理该存档的全部 checkpoint 文件
            val checkpointDir = File(SaveDatabase.getSaveDir(context), "checkpoints/$saveId")
            if (checkpointDir.exists()) {
                checkpointDir.deleteRecursively()
            }

            Log.d("SaveDeleter", "存档删除成功：saveId=$saveId")
            true
        } catch (e: Exception) {
            Log.e("SaveDeleter", "存档删除失败：${e.message}", e)
            false
        }
    }
}
