package com.greendynasty.football.growth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 成长快照表（save.db，T09 方案 §三.2）
 *
 * 每球员每月 1 条快照，记录 CA/PA/属性变化 + 10 因子分解 + 兑现率。
 * 供 T04 阵容页成长曲线绘制与 T19 赛季归档使用。
 *
 * 唯一索引 (save_id, player_id, snapshot_date) 防止同月重复写入。
 */
@Entity(
    tableName = "growth_snapshot",
    indices = [
        Index(value = ["save_id", "player_id", "snapshot_date"], unique = true),
        Index(value = ["save_id", "club_id", "snapshot_date"]),
        Index(value = ["save_id", "player_id", "season_id"])
    ]
)
data class GrowthSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "snapshot_date")
    val snapshotDate: String, // ISO yyyy-MM-dd（月结日）

    @ColumnInfo(name = "age")
    val age: Int,

    @ColumnInfo(name = "ca_before")
    val caBefore: Int,

    @ColumnInfo(name = "ca_after")
    val caAfter: Int,

    @ColumnInfo(name = "ca_delta")
    val caDelta: Int, // 可为负（衰退期）

    @ColumnInfo(name = "pa_before")
    val paBefore: Int,

    @ColumnInfo(name = "pa_after")
    val paAfter: Int,

    @ColumnInfo(name = "pa_delta")
    val paDelta: Int,

    @ColumnInfo(name = "realization_score")
    val realizationScore: Double, // 0-1

    @ColumnInfo(name = "range_tier")
    val rangeTier: String, // FULL/ACTIVE/LIGHT/MINIMAL

    @ColumnInfo(name = "growth_phase")
    val growthPhase: String, // 7 档年龄阶段

    // 10 因子分解（用于调试与 T04 详细展示）
    @ColumnInfo(name = "factor_training_quality")
    val factorTrainingQuality: Double,

    @ColumnInfo(name = "factor_playing_time")
    val factorPlayingTime: Double,

    @ColumnInfo(name = "factor_mentor")
    val factorMentor: Double,

    @ColumnInfo(name = "factor_club_facility")
    val factorClubFacility: Double,

    @ColumnInfo(name = "factor_talent")
    val factorTalent: Double,

    @ColumnInfo(name = "factor_age")
    val factorAge: Double,

    @ColumnInfo(name = "factor_injury")
    val factorInjury: Double,

    @ColumnInfo(name = "factor_morale")
    val factorMorale: Double,

    @ColumnInfo(name = "factor_random")
    val factorRandom: Double,

    @ColumnInfo(name = "factor_national_pool")
    val factorNationalPool: Double,

    // 属性快照（JSON 字符串，避免 30+ 列宽表）
    @ColumnInfo(name = "attributes_json")
    val attributesJson: String,

    // 本月属性变化（JSON 字符串）
    @ColumnInfo(name = "attribute_changes_json")
    val attributeChangesJson: String,

    @ColumnInfo(name = "notes")
    val notes: String? // 成长日志
)
