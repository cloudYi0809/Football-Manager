package com.greendynasty.football.ai.profile.repository

import com.greendynasty.football.ai.profile.generator.ClubProfileGenerator
import com.greendynasty.football.ai.profile.model.ClubPersonality
import com.greendynasty.football.ai.profile.model.ClubProfile
import com.greendynasty.football.ai.profile.model.LongTermGoal
import com.greendynasty.football.ai.profile.model.TacticalIdentity
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.ClubEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * T18 俱乐部画像仓库（V0.2 05 §二 + T18 方案 §二）。
 *
 * 职责：
 * 1. 持久化画像到 save.db 的 `club_ai_profile` 表
 * 2. 提供 Flow / suspend 查询接口供 ViewModel / T13 AI 转会查询使用
 * 3. 调用 [ClubProfileGenerator] 在存档初始化时批量生成画像
 * 4. 实现 [ClubProfileQueryPort] 为 T13 提供画像查询接口（接口预留，不强制修改 T13）
 *
 * 三库分离：history.club 只读（基础属性），save.club_ai_profile 可写（画像）。
 *
 * @param databaseManager 三库管理入口
 * @param generator 画像生成器
 */
class ClubProfileRepository(
    private val databaseManager: DatabaseManager,
    private val generator: ClubProfileGenerator = ClubProfileGenerator()
) {

    // ==================== 1. 单画像查询 ====================

    /**
     * 查询单个俱乐部的画像（同步）。
     *
     * @param clubId 俱乐部 ID
     * @return 画像（不存在返回 null）
     */
    suspend fun getProfile(clubId: Int): ClubProfile? = withContext(Dispatchers.IO) {
        val entity = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao()?.get(clubId)
        entity?.let { ClubProfile.fromEntity(it) }
    }

    /**
     * 观察单个俱乐部画像（Flow 驱动 UI 自动刷新）。
     */
    fun observeProfile(clubId: Int): Flow<ClubProfile?> {
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: error("存档数据库未初始化，无法观察俱乐部画像")
        return saveDb.clubAiProfileDao().observe(clubId).map { entity ->
            entity?.let { ClubProfile.fromEntity(it) }
        }
    }

    // ==================== 2. 列表查询 ====================

    /**
     * 查询全部画像（按野心降序，UI 列表用）。
     */
    suspend fun listAllProfiles(): List<ClubProfile> = withContext(Dispatchers.IO) {
        val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao() ?: return@withContext emptyList()
        dao.getAll().map { ClubProfile.fromEntity(it) }
    }

    /**
     * 观察全部画像（按野心降序，Flow 驱动 UI 列表）。
     */
    fun observeAllProfiles(): Flow<List<ClubProfile>> {
        val saveDb = databaseManager.getSaveDatabaseOrNull()
            ?: error("存档数据库未初始化，无法观察俱乐部画像列表")
        return saveDb.clubAiProfileDao().observeAll().map { entities ->
            entities.map { ClubProfile.fromEntity(it) }
        }
    }

    /**
     * 按性格筛选画像（T18 6 种性格）。
     */
    suspend fun listByPersonality(personality: ClubPersonality): List<ClubProfile> =
        withContext(Dispatchers.IO) {
            val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao()
                ?: return@withContext emptyList()
            dao.getByPersonality(personality.name).map { ClubProfile.fromEntity(it) }
        }

    /**
     * 按战术风格筛选画像（T18 8 种战术）。
     */
    suspend fun listByTacticalIdentity(tactical: TacticalIdentity): List<ClubProfile> =
        withContext(Dispatchers.IO) {
            val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao()
                ?: return@withContext emptyList()
            dao.getByTacticalIdentity(tactical.name).map { ClubProfile.fromEntity(it) }
        }

    /**
     * 按长期目标筛选画像。
     */
    suspend fun listByLongTermGoal(goal: LongTermGoal): List<ClubProfile> =
        withContext(Dispatchers.IO) {
            val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao()
                ?: return@withContext emptyList()
            dao.getByLongTermGoal(goal.name).map { ClubProfile.fromEntity(it) }
        }

    // ==================== 3. 持久化操作 ====================

    /**
     * 保存单个画像（不存在则插入，存在则替换）。
     */
    suspend fun saveProfile(profile: ClubProfile) = withContext(Dispatchers.IO) {
        val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao() ?: return@withContext
        dao.insert(ClubProfile.toEntity(profile))
    }

    /**
     * 批量保存画像（存档初始化用）。
     */
    suspend fun saveAll(profiles: List<ClubProfile>) = withContext(Dispatchers.IO) {
        val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao() ?: return@withContext
        dao.upsertAll(profiles.map { ClubProfile.toEntity(it) })
    }

    /**
     * 清空所有画像（重置用）。
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val dao = databaseManager.getSaveDatabaseOrNull()?.clubAiProfileDao() ?: return@withContext
        dao.clearAll()
    }

    // ==================== 4. 存档初始化 ====================

    /**
     * 存档初始化时为所有俱乐部批量生成画像并持久化。
     *
     * 流程：
     * 1. 从 history.db 读取所有俱乐部基础信息
     * 2. （可选）从 T17 经济模型读取财力评分
     * 3. 调用 [ClubProfileGenerator.generateAll] 批量生成画像
     * 4. 持久化到 save.db
     *
     * @param financialPowerMap 俱乐部 ID → 财力评分（T17 提供，无则用 club.financeLevel）
     * @param randomSeed 随机种子（同一种子可复现画像）
     * @return 已生成的画像列表
     */
    suspend fun initializeForSave(
        financialPowerMap: Map<Int, Int> = emptyMap(),
        randomSeed: Long = System.currentTimeMillis()
    ): List<ClubProfile> = withContext(Dispatchers.IO) {
        // 1. 读取全部俱乐部（history.db 只暴露 Flow，取 first 一次）
        val clubs = databaseManager.historyClubDao().getAllClubs().first()
        if (clubs.isEmpty()) return@withContext emptyList()

        // 2. 生成画像
        val random = Random(randomSeed)
        val profiles = generator.generateAll(clubs, financialPowerMap, random)

        // 3. 持久化
        saveAll(profiles)

        profiles
    }

    /**
     * 为新增俱乐部生成画像（赛季中动态添加，如新升级球队）。
     *
     * @param club 俱乐部基础信息
     * @param financialPowerScore 财力评分
     * @param randomSeed 随机种子
     * @return 已生成的画像
     */
    suspend fun generateForClub(
        club: ClubEntity,
        financialPowerScore: Int = club.financeLevel,
        randomSeed: Long = System.currentTimeMillis()
    ): ClubProfile = withContext(Dispatchers.IO) {
        val profile = generator.generate(club, financialPowerScore, Random(randomSeed))
        saveProfile(profile)
        profile
    }

    // ==================== 5. 画像查询接口（T13 预留） ====================

    /**
     * 为 T13 AI 转会提供画像查询能力（接口预留，不强制修改 T13 代码）。
     *
     * T13 [com.greendynasty.football.transfer.ai.AiTransferService] 可通过此方法
     * 获取 [ClubProfile]，进而：
     * - 根据 [ClubProfile.clubType] 调整 target_score 权重（V0.2 05 §五）
     * - 根据 [ClubProfile.tacticalIdentity] 调整战术适配度
     * - 根据 [ClubProfile.playerArchetype] 筛选目标球员类型
     * - 根据 [ClubProfile.personality] 调整报价策略
     *
     * 调用方式：
     * ```kotlin
     * val profile = repository.queryProfileForAi(clubId)
     *     ?: return  // 无画像则用 T13 基础版逻辑
     * ```
     *
     * @param clubId 俱乐部 ID
     * @return 画像（不存在返回 null，T13 应回退到基础版逻辑）
     */
    suspend fun queryProfileForAi(clubId: Int): ClubProfile? = getProfile(clubId)

    /**
     * 查询画像统计信息（UI 仪表盘用）。
     *
     * @return 各性格 / 战术 / 长期目标的俱乐部数量统计
     */
    suspend fun getProfileStatistics(): ProfileStatistics = withContext(Dispatchers.IO) {
        val all = listAllProfiles()
        ProfileStatistics(
            totalClubs = all.size,
            byPersonality = all.groupingBy { it.personality }.eachCount(),
            byTacticalIdentity = all.groupingBy { it.tacticalIdentity }.eachCount(),
            byLongTermGoal = all.groupingBy { it.longTermGoal }.eachCount(),
            byPlayerArchetype = all.groupingBy { it.playerArchetype }.eachCount()
        )
    }
}

/**
 * 画像统计信息（UI 仪表盘）。
 */
data class ProfileStatistics(
    val totalClubs: Int,
    val byPersonality: Map<ClubPersonality, Int>,
    val byTacticalIdentity: Map<TacticalIdentity, Int>,
    val byLongTermGoal: Map<LongTermGoal, Int>,
    val byPlayerArchetype: Map<com.greendynasty.football.ai.profile.model.PlayerArchetype, Int>
)
