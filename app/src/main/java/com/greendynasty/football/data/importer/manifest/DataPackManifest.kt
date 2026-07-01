package com.greendynasty.football.data.importer.manifest

import kotlinx.serialization.Serializable

/**
 * 数据包清单（manifest.json）
 *
 * 描述一个数据包的身份、版本、版权属性与文件清单。
 * 由 [ManifestLoader] 加载并校验后供 DataImportManager 使用。
 *
 * 字段命名沿用 JSON snake_case，与 manifest.json 一一对应。
 */
@Serializable
data class DataPackManifest(
    /** 数据包唯一标识（如 "fictional_default_v1"） */
    val pack_id: String = "",
    /** 数据包展示名称 */
    val pack_name: String = "",
    /** 数据包类型（fictional / historical / user_local） */
    val pack_type: String = "",
    /** manifest schema 版本，需与 ManifestLoader.EXPECTED_SCHEMA_VERSION 匹配 */
    val schema_version: Int = 0,
    /** 数据版本号（语义化版本，如 "0.2.0"） */
    val data_version: String = "",
    /** 起始赛季列表（如 ["2002_03"]） */
    val start_seasons: List<String> = emptyList(),
    /** 创建日期（YYYY-MM-DD） */
    val created_at: String = "",
    /** 作者 */
    val author: String = "",
    /** 分发范围（public_safe / local_only 等） */
    val distribution: String = "",
    /** 是否含真实姓名 */
    val contains_real_names: Boolean = false,
    /** 是否含真实队徽 */
    val contains_real_logos: Boolean = false,
    /** 是否含真实球员头像 */
    val contains_real_faces: Boolean = false,
    /** 版权声明说明 */
    val license_note: String = "",
    /** 整包 SHA-256 校验值（可带 "sha256:" 前缀） */
    val checksum: String = "",
    /** 包含的文件清单 */
    val file_list: List<ManifestFileEntry> = emptyList()
)
