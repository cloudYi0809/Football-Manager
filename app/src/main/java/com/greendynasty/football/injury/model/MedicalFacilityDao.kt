package com.greendynasty.football.injury.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 医疗设施数据访问对象（save.db）
 *
 * 每存档每俱乐部一条记录，提供查询 / 观察 / 升级接口。
 */
@Dao
interface MedicalFacilityDao {

    @Query("SELECT * FROM save_medical_facility WHERE save_id = :saveId AND club_id = :clubId")
    suspend fun get(saveId: Int, clubId: Int): MedicalFacilityEntity?

    @Query("SELECT * FROM save_medical_facility WHERE save_id = :saveId AND club_id = :clubId")
    fun observe(saveId: Int, clubId: Int): Flow<MedicalFacilityEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(facility: MedicalFacilityEntity): Long

    @Query(
        "UPDATE save_medical_facility SET medical_level = :level, " +
            "recovery_speed_multiplier = :speedMul, recurrence_reduction = :recurRed, " +
            "last_upgrade_date = :date, upgrade_cooldown_days = :cooldown " +
            "WHERE save_id = :saveId AND club_id = :clubId"
    )
    suspend fun upgrade(
        saveId: Int, clubId: Int, level: Int, speedMul: Double,
        recurRed: Double, date: String, cooldown: Int
    )
}
