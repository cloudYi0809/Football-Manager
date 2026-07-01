package com.greendynasty.football.injury.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 医疗设施表（save.db）
 *
 * V0.1 schema 中 club 表无 medical_level，T08 引入独立的 save_medical_facility 表，
 * 避免改动 history.db 的 club 静态表。每存档每俱乐部一条记录，玩家可升级。
 *
 * @param medicalLevel 医疗设施等级 1-100
 * @param recoverySpeedMultiplier 由等级推导的恢复速度系数（0.7-1.3）
 * @param recurrenceReduction 由等级推导的复发概率降低（-0.10 ~ 0.30）
 */
@Entity(
    tableName = "save_medical_facility",
    indices = [Index(value = ["save_id", "club_id"], unique = true)]
)
data class MedicalFacilityEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "medical_level")
    val medicalLevel: Int = 50,

    @ColumnInfo(name = "recovery_speed_multiplier")
    val recoverySpeedMultiplier: Double = 1.0,

    @ColumnInfo(name = "recurrence_reduction")
    val recurrenceReduction: Double = 0.0,

    @ColumnInfo(name = "external_expert_slots")
    val externalExpertSlots: Int = 0,

    @ColumnInfo(name = "last_upgrade_date")
    val lastUpgradeDate: String? = null,

    @ColumnInfo(name = "upgrade_cooldown_days")
    val upgradeCooldownDays: Int = 0
)
