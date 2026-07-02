package com.greendynasty.football.scouting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T14 球探地区知识表（save.db，V0.2 08 §三.2）。
 *
 * 每个雇佣球探对 15 个地区独立维护知识值（0-100）：
 * - 影响发现概率（权重 0.25，7 因子之一）
 * - V1 不随任务执行自动提升（仅按年资/事件缓慢提升）
 *
 * 唯一约束：(save_id, hired_id, region_code)
 */
@Entity(
    tableName = "scout_region_knowledge",
    indices = [
        Index(value = ["save_id", "hired_id", "region_code"], unique = true),
        Index(value = ["save_id", "hired_id"])
    ]
)
data class SaveScoutRegionKnowledgeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 关联 save.scout_hired.hired_id。 */
    @ColumnInfo(name = "hired_id")
    val hiredId: Int,

    /** 球探 ID（冗余便于查询，与 hired.scoutId 一致）。 */
    @ColumnInfo(name = "scout_id")
    val scoutId: Int,

    /** 地区代码（见 ScoutRegionCode 枚举）。 */
    @ColumnInfo(name = "region_code")
    val regionCode: String,

    /** 知识值 0-100。 */
    @ColumnInfo(name = "knowledge_value")
    val knowledgeValue: Int
)
