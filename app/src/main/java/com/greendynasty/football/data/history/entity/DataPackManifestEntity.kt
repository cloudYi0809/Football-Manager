package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 数据包清单表（history.db 只读，V0.2）
 * 记录当前数据包的版本、类型、版权合规信息等。
 * 用于支持虚构/真实数据包切换与版权合规。
 */
@Entity(tableName = "data_pack_manifest")
data class DataPackManifestEntity(
    @PrimaryKey
    @ColumnInfo(name = "pack_id")
    val packId: String,

    @ColumnInfo(name = "pack_name")
    val packName: String,

    @ColumnInfo(name = "pack_type")
    val packType: String, // fictional / real / local

    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,

    @ColumnInfo(name = "data_version")
    val dataVersion: String,

    @ColumnInfo(name = "distribution")
    val distribution: String,

    @ColumnInfo(name = "contains_real_names")
    val containsRealNames: Int = 0,

    @ColumnInfo(name = "contains_real_logos")
    val containsRealLogos: Int = 0,

    @ColumnInfo(name = "contains_real_faces")
    val containsRealFaces: Int = 0,

    @ColumnInfo(name = "license_note")
    val licenseNote: String?,

    @ColumnInfo(name = "checksum")
    val checksum: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)
