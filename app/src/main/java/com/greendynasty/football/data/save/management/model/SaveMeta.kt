package com.greendynasty.football.data.save.management.model

import kotlinx.serialization.Serializable

/**
 * 存档元信息数据类（用于存档列表展示）
 *
 * 双存储策略：
 * - 权威存储：save_manifest 表（save.db 内）
 * - 快速索引：save_{saveId}.meta.json（saves/ 目录，列表展示无需打开数据库）
 *
 * 每次保存时双写，保证一致性。JSON 损坏时从 DB 恢复。
 *
 * @param saveId 存档唯一标识（UUID）
 * @param saveName 存档名称（玩家可见）
 * @param gameVersion 创建时的游戏版本
 * @param createdAt 创建时间（现实时间，ISO 格式）
 * @param lastSavedAt 最后保存时间（现实时间，ISO 格式）
 * @param gameDate 游戏内当前日期（如 2003-05-30）
 * @param managerClubId 玩家执教的俱乐部 ID
 * @param scenarioId 剧本 ID
 * @param schemaVersion 存档 schema 版本号（用于迁移）
 * @param fileSizeBytes 存档文件大小（字节）
 * @param isLoaded 当前是否已加载到内存
 */
@Serializable
data class SaveMeta(
    val saveId: String,
    val saveName: String,
    val gameVersion: String,
    val createdAt: String,
    val lastSavedAt: String,
    val gameDate: String,
    val managerClubId: Int,
    val scenarioId: String,
    val schemaVersion: Int,
    val fileSizeBytes: Long,
    val isLoaded: Boolean = false
)
