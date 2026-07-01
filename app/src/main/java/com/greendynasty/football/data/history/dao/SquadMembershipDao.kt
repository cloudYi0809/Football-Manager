package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.SquadMembershipEntity
import kotlinx.coroutines.flow.Flow

/**
 * 球队成员关系数据访问对象（history.db 只读）
 * 提供球员在某赛季效力于某俱乐部的查询，包括租借查询。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface SquadMembershipDao {

    // 查询某赛季某俱乐部的全部阵容（按号码排序）
    @Query("SELECT * FROM squad_membership WHERE season_id = :seasonId AND club_id = :clubId ORDER BY squad_number")
    fun getSquadByClub(seasonId: Int, clubId: Int): Flow<List<SquadMembershipEntity>>

    // 查询某球员在某赛季效力的俱乐部
    @Query("SELECT * FROM squad_membership WHERE player_id = :playerId AND season_id = :seasonId LIMIT 1")
    suspend fun getPlayerClubInSeason(playerId: Int, seasonId: Int): SquadMembershipEntity?

    // 查询某球员的全部赛季效力记录（按赛季排序）
    @Transaction
    @Query("SELECT * FROM squad_membership WHERE player_id = :playerId ORDER BY season_id")
    fun getPlayerHistory(playerId: Int): Flow<List<SquadMembershipEntity>>

    // 查询某赛季的全部成员关系
    @Query("SELECT * FROM squad_membership WHERE season_id = :seasonId")
    fun getAllMembershipsInSeason(seasonId: Int): Flow<List<SquadMembershipEntity>>

    // 查询某赛季的全部租借记录
    @Query("SELECT * FROM squad_membership WHERE season_id = :seasonId AND is_loan = 1")
    fun getLoansInSeason(seasonId: Int): Flow<List<SquadMembershipEntity>>

    // 成员关系总数
    @Query("SELECT COUNT(*) FROM squad_membership")
    suspend fun count(): Int
}
