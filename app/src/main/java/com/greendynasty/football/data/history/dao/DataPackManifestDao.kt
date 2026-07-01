package com.greendynasty.football.data.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.greendynasty.football.data.history.entity.DataPackManifestEntity
import kotlinx.coroutines.flow.Flow

/**
 * 数据包清单数据访问对象（history.db 只读，V0.2）
 * 提供数据包版本、类型、版权合规信息查询。
 * 用于支持虚构/真实数据包切换与版权合规检测。
 */
@Dao
interface DataPackManifestDao {

    // 查询所有数据包清单
    @Query("SELECT * FROM data_pack_manifest ORDER BY pack_id")
    fun getAllPacks(): Flow<List<DataPackManifestEntity>>

    // 按 pack_id 查询单个数据包
    @Query("SELECT * FROM data_pack_manifest WHERE pack_id = :packId")
    suspend fun getPack(packId: String): DataPackManifestEntity?

    // 查询当前数据包（取最新更新的一条）
    @Transaction
    @Query("SELECT * FROM data_pack_manifest ORDER BY updated_at DESC LIMIT 1")
    suspend fun getCurrentPack(): DataPackManifestEntity?

    // 按数据包类型筛选（fictional / real / local）
    @Query("SELECT * FROM data_pack_manifest WHERE pack_type = :packType ORDER BY pack_id")
    fun getPacksByType(packType: String): Flow<List<DataPackManifestEntity>>

    // 查询包含真实姓名的数据包
    @Query("SELECT * FROM data_pack_manifest WHERE contains_real_names = 1")
    fun getPacksWithRealNames(): Flow<List<DataPackManifestEntity>>

    // 查询包含真实队徽的数据包
    @Query("SELECT * FROM data_pack_manifest WHERE contains_real_logos = 1")
    fun getPacksWithRealLogos(): Flow<List<DataPackManifestEntity>>

    // 查询包含真实球员头像的数据包
    @Query("SELECT * FROM data_pack_manifest WHERE contains_real_faces = 1")
    fun getPacksWithRealFaces(): Flow<List<DataPackManifestEntity>>

    // 查询数据包总数
    @Query("SELECT COUNT(*) FROM data_pack_manifest")
    suspend fun count(): Int
}
