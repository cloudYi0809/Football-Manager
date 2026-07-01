package com.greendynasty.football.injury.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.injury.model.MedicalFacilityEntity
import kotlinx.coroutines.flow.Flow

/**
 * 伤病系统数据访问门面（Repository 层）
 *
 * 统一封装 [DatabaseManager] 三库 DAO 的伤病相关数据操作，对上层 [InjuryService] 屏蔽
 * save.db / history.db 的细节。仅负责纯数据 CRUD，不包含业务逻辑。
 *
 * 数据来源：
 * - save.db：save_injury（伤病记录）/ save_medical_facility（医疗设施）/ save_player_state（球员状态）
 * - history.db：player / player_attributes（球员档案与属性，只读）
 *
 * @param databaseManager 三库入口
 */
class InjuryRepository(
    private val databaseManager: DatabaseManager
) {

    // ==================== 伤病记录 CRUD ====================

    /** 新增伤病记录，返回自增 ID */
    suspend fun insertInjury(injury: SaveInjuryEntity): Long =
        databaseManager.saveInjuryDao().insert(injury)

    /** 更新伤病记录（全字段替换） */
    suspend fun updateInjury(injury: SaveInjuryEntity) =
        databaseManager.saveInjuryDao().update(injury)

    /** 按 ID 查询伤病记录 */
    suspend fun getInjury(injuryId: Int): SaveInjuryEntity? =
        databaseManager.saveInjuryDao().get(injuryId)

    /** 球员当前活跃伤病（最多 1 条，含 active/recovering/returned_early/recurred） */
    suspend fun getActiveInjury(saveId: Int, playerId: Int): SaveInjuryEntity? =
        databaseManager.saveInjuryDao().getActiveInjury(saveId, playerId)

    /** 俱乐部全部活跃伤病（按预计复出日期升序） */
    suspend fun getActiveInjuriesByClub(saveId: Int, clubId: Int): List<SaveInjuryEntity> =
        databaseManager.saveInjuryDao().getActiveInjuriesByClub(saveId, clubId)

    /** 观察俱乐部活跃伤病（Flow 驱动 UI） */
    fun observeActiveInjuries(saveId: Int, clubId: Int): Flow<List<SaveInjuryEntity>> =
        databaseManager.saveInjuryDao().observeActiveInjuries(saveId, clubId)

    /** 球员伤病历史（含已恢复，按开始日期倒序） */
    suspend fun getPlayerInjuryHistory(saveId: Int, playerId: Int): List<SaveInjuryEntity> =
        databaseManager.saveInjuryDao().getPlayerInjuryHistory(saveId, playerId)

    /** 球员重伤及以上历史（用于永久影响 / 复发评估） */
    suspend fun getMajorInjuryHistory(saveId: Int, playerId: Int): List<SaveInjuryEntity> =
        databaseManager.saveInjuryDao().getMajorInjuryHistory(saveId, playerId)

    /** 区间内俱乐部伤病发生次数（赛季统计 / Gate 2 验收） */
    suspend fun countInjuriesInPeriod(
        saveId: Int, clubId: Int, startDate: String, endDate: String
    ): Int = databaseManager.saveInjuryDao().countInjuriesInPeriod(saveId, clubId, startDate, endDate)

    /** 按严重度统计已恢复伤病数（赛季统计） */
    suspend fun countRecoveredBySeverity(saveId: Int, clubId: Int, severity: Int): Int =
        databaseManager.saveInjuryDao().countRecoveredBySeverity(saveId, clubId, severity)

    // ==================== 恢复进度更新 ====================

    /** 更新恢复进度（T07 每日推进调用） */
    suspend fun updateRecoveryProgress(
        injuryId: Int, progress: Int, elapsed: Int, status: String
    ) = databaseManager.saveInjuryDao().updateRecoveryProgress(injuryId, progress, elapsed, status)

    /** 更新状态并回填实际复出日 */
    suspend fun updateStatusAndReturn(injuryId: Int, status: String, returnDate: String?) =
        databaseManager.saveInjuryDao().updateStatusAndReturn(injuryId, status, returnDate)

    /** 更新伤病状态 */
    suspend fun updateStatus(injuryId: Int, status: String) =
        databaseManager.saveInjuryDao().updateStatus(injuryId, status)

    /** 标记永久影响已结算 */
    suspend fun markPermanentImpactEvaluated(injuryId: Int) =
        databaseManager.saveInjuryDao().markPermanentImpactEvaluated(injuryId)

    // ==================== 球员状态 ====================

    /** 获取球员存档状态（含体能 / 士气 / 伤病状态 / CA / 俱乐部等） */
    suspend fun getPlayerState(saveId: Int, playerId: Int): SavePlayerStateEntity? =
        databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)

    /** 更新球员伤病状态 */
    suspend fun updatePlayerInjuryStatus(
        saveId: Int, playerId: Int, status: String, until: String?
    ) = databaseManager.savePlayerStateDao().updateInjuryStatus(saveId, playerId, status, until)

    /** 更新球员体能 */
    suspend fun updatePlayerCondition(saveId: Int, playerId: Int, condition: Int) =
        databaseManager.savePlayerStateDao().updateCondition(saveId, playerId, condition)

    /** 更新球员士气 */
    suspend fun updatePlayerMorale(saveId: Int, playerId: Int, morale: Int) =
        databaseManager.savePlayerStateDao().updateMorale(saveId, playerId, morale)

    /** 更新球员 CA（强行复出临时下降） */
    suspend fun updatePlayerCa(saveId: Int, playerId: Int, ca: Int) =
        databaseManager.savePlayerStateDao().updateCa(saveId, playerId, ca)

    /** 更新球员职业状态（退役） */
    suspend fun updatePlayerCareerStatus(saveId: Int, playerId: Int, status: String) =
        databaseManager.savePlayerStateDao().updateCareerStatus(saveId, playerId, status)

    // ==================== 医疗设施 ====================

    /** 获取医疗设施记录 */
    suspend fun getMedicalFacility(saveId: Int, clubId: Int): MedicalFacilityEntity? =
        databaseManager.medicalFacilityDao().get(saveId, clubId)

    /** 观察医疗设施记录（Flow 驱动 UI） */
    fun observeMedicalFacility(saveId: Int, clubId: Int): Flow<MedicalFacilityEntity?> =
        databaseManager.medicalFacilityDao().observe(saveId, clubId)

    /** 新增/替换医疗设施记录 */
    suspend fun upsertMedicalFacility(facility: MedicalFacilityEntity): Long =
        databaseManager.medicalFacilityDao().upsert(facility)

    /** 升级医疗设施（直接更新字段） */
    suspend fun upgradeMedicalFacility(
        saveId: Int, clubId: Int, level: Int, speedMul: Double,
        recurRed: Double, date: String, cooldown: Int
    ) = databaseManager.medicalFacilityDao().upgrade(
        saveId, clubId, level, speedMul, recurRed, date, cooldown
    )

    // ==================== 俱乐部预算 ====================

    /** 扣减俱乐部余额（医疗设施升级 / 外部专家费用） */
    suspend fun deductClubBalance(saveId: Int, clubId: Int, amount: Int) {
        val club = databaseManager.saveClubStateDao().getByClub(saveId, clubId) ?: return
        val newBalance = (club.balance - amount).coerceAtLeast(0)
        databaseManager.saveClubStateDao().updateBalance(saveId, clubId, newBalance)
    }
}
