package com.greendynasty.football.data.save.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greendynasty.football.data.save.entity.EconomyIndexEntity
import com.greendynasty.football.data.save.entity.LeagueEconomyProfileEntity

/**
 * 经济指数数据访问对象（save.db，V0.2）
 * 提供全球经济指数和联赛经济画像的 CRUD。
 *
 * 协程规范：写操作与单次查询使用 suspend，指数列表使用 Flow 观察以驱动 UI 刷新。
 */
@Dao
interface EconomyIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(index: EconomyIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indices: List<EconomyIndexEntity>)

    @Update
    suspend fun update(index: EconomyIndexEntity)

    @Delete
    suspend fun delete(index: EconomyIndexEntity)

    @Query("SELECT * FROM economy_index WHERE year = :year")
    suspend fun get(year: Int): EconomyIndexEntity?

    @Query("SELECT * FROM economy_index ORDER BY year")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<EconomyIndexEntity>>

    @Query("SELECT * FROM economy_index ORDER BY year")
    suspend fun getAll(): List<EconomyIndexEntity>

    @Query("SELECT * FROM economy_index WHERE year <= :year ORDER BY year DESC LIMIT 1")
    suspend fun getLatestBefore(year: Int): EconomyIndexEntity?

    // ========== 联赛经济画像 ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeagueProfile(profile: LeagueEconomyProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLeagueProfiles(profiles: List<LeagueEconomyProfileEntity>)

    @Query("SELECT * FROM league_economy_profile WHERE league_id = :leagueId")
    suspend fun getLeagueProfile(leagueId: Int): LeagueEconomyProfileEntity?

    @Query("SELECT * FROM league_economy_profile ORDER BY base_multiplier DESC")
    suspend fun getAllLeagueProfiles(): List<LeagueEconomyProfileEntity>

    @Query("SELECT COUNT(*) FROM economy_index")
    suspend fun count(): Int
}
