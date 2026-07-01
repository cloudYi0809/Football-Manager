package com.greendynasty.football.data.save.management.backup

import kotlinx.serialization.Serializable

/**
 * 备份元信息（T03）
 *
 * 每个备份文件伴随一个同名的 .meta.json，序列化本对象，用于不打开 DB 即可读取备份信息。
 *
 * @param backupId 备份唯一标识（如 backup_{timestamp}）
 * @param saveId 关联存档 ID
 * @param createdAt 创建时间戳（epoch millis）
 * @param fileSizeBytes 备份文件大小（字节）
 * @param reason 备份原因（PERIODIC / MANUAL / PRE_MIGRATION / SEASON_END）
 */
@Serializable
data class BackupInfo(
    val backupId: String,
    val saveId: String,
    val createdAt: Long,
    val fileSizeBytes: Long,
    val reason: String
)
