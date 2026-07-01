package com.greendynasty.football.data.importer.manifest

import kotlinx.serialization.Serializable

/**
 * 数据包文件条目（manifest.json 中 file_list 数组元素）
 *
 * @property path 文件相对路径（相对数据包根目录，如 "players.csv"）
 * @property rows 文件数据行数（不含表头）
 * @property checksum 文件 SHA-256 校验值（可带 "sha256:" 前缀）
 */
@Serializable
data class ManifestFileEntry(
    val path: String = "",
    val rows: Int = 0,
    val checksum: String = ""
)
