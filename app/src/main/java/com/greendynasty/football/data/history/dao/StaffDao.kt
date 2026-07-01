package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import com.greendynasty.football.data.history.entity.StaffEntity
import kotlinx.coroutines.flow.Flow

/**
 * 教练/员工数据访问对象（history.db 只读）
 * 提供俱乐部教练和工作人员查询，支持按角色筛选。
 * 所有方法均为查询方法（@Query），history.db 只读不写。
 */
@Dao
interface StaffDao {

    // 按 staff_id 查询单个员工
    @Query("SELECT * FROM staff WHERE staff_id = :staffId")
    suspend fun getStaff(staffId: Int): StaffEntity?

    // 查询某俱乐部的全部员工（按角色排序）
    @Query("SELECT * FROM staff WHERE current_club_id = :clubId ORDER BY role")
    fun getStaffByClub(clubId: Int): Flow<List<StaffEntity>>

    // 查询某俱乐部某角色的员工
    @Query("SELECT * FROM staff WHERE current_club_id = :clubId AND role = :role")
    fun getStaffByClubAndRole(clubId: Int, role: String): Flow<List<StaffEntity>>

    // 查询全部员工（按姓名排序）
    @Query("SELECT * FROM staff ORDER BY name")
    fun getAllStaff(): Flow<List<StaffEntity>>

    // 员工总数
    @Query("SELECT COUNT(*) FROM staff")
    suspend fun count(): Int
}
