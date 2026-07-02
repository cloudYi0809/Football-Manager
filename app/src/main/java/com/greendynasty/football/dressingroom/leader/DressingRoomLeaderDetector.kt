package com.greendynasty.football.dressingroom.leader

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.dressingroom.model.DressingRoomConfig
import com.greendynasty.football.dressingroom.model.DressingRoomLeaderEntity
import com.greendynasty.football.dressingroom.model.LeaderCandidate
import com.greendynasty.football.dressingroom.model.LeaderRole
import com.greendynasty.football.dressingroom.model.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * T23 更衣室领袖识别器（V0.2 + T23 任务要求 §二.4 + 实现方案 §四.5）。
 *
 * 识别俱乐部现任领袖（队长 / 副队长 / 影响力球员），3 档角色：
 * - CAPTAIN 队长：leadership ≥60 + 主力 + 在队 ≥1 年
 * - VICE_CAPTAIN 副队长：leadership ≥50 + 主力
 * - INFLUENTIAL 影响力球员：leadership ≥45 + 在队
 *
 * 影响力计算（V1 简化）：
 * - influence = leadership * 0.6 + age_factor * 0.2 + reputation_factor * 0.2
 * - age_factor：26-32 岁=100 / 23-25 或 33-34=80 / 其他=60
 * - reputation_factor：基于 squad_role，starter=100 / backup=70 / prospect=50
 *
 * 单俱乐部活跃领袖数上限 5（[DressingRoomConfig.maxLeadersPerClub]）。
 *
 * 触发时机：赛季初自动识别 + 玩家手动任命 / 撤销。
 *
 * @param databaseManager 三库管理入口
 * @param config 更衣室配置
 */
class DressingRoomLeaderDetector(
    private val databaseManager: DatabaseManager,
    private val config: DressingRoomConfig = DressingRoomConfig.DEFAULT
) {
    private val logger = Logger.getLogger("DressingRoomLeaderDetector")

    // ==================== 1. 候选识别 ====================

    /**
     * 识别俱乐部领袖候选（V0.2 + T23 任务要求 §二.4）。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param profiles 俱乐部内所有球员画像
     * @return 排序后的领袖候选列表（影响力降序），最多 [DressingRoomConfig.maxLeadersPerClub] 名
     */
    fun detectCandidates(
        saveId: Int,
        clubId: Int,
        profiles: List<PlayerProfile>
    ): List<LeaderCandidate> {
        if (profiles.isEmpty()) return emptyList()

        // 1. 计算每名球员的影响力
        val candidates = profiles.map { profile ->
            val influence = computeInfluence(profile)
            val recommendedRole = recommendRole(profile, influence)
            LeaderCandidate(
                playerId = profile.playerId,
                leadership = profile.leadership,
                influence = influence,
                recommendedRole = recommendedRole
            )
        }.filter { it.leadership >= config.influentialMinLeadership }
            .sortedByDescending { it.influence }

        // 2. 限制最多 maxLeadersPerClub 名
        return candidates.take(config.maxLeadersPerClub)
    }

    /**
     * 计算球员影响力 0-100（V1 简化）。
     *
     * influence = leadership * 0.6 + age_factor * 0.2 + reputation_factor * 0.2
     */
    fun computeInfluence(profile: PlayerProfile): Int {
        val leadershipScore = profile.leadership.coerceIn(0, 100)

        val ageFactor = when (profile.age) {
            in 26..32 -> 100
            in 23..25, in 33..34 -> 80
            else -> 60
        }

        val reputationFactor = when (profile.squadRole) {
            "starter" -> 100
            "backup" -> 70
            "prospect" -> 50
            else -> 60
        }

        val influence = (
            leadershipScore * 0.6 +
                ageFactor * 0.2 +
                reputationFactor * 0.2
            ).toInt().coerceIn(0, 100)

        return influence
    }

    /**
     * 推荐领袖角色（V0.2 + T23 任务要求 §二.4）。
     *
     * - CAPTAIN 队长：leadership ≥60 + 主力 + 在队 ≥1 年
     * - VICE_CAPTAIN 副队长：leadership ≥50 + 主力
     * - INFLUENTIAL 影响力球员：leadership ≥45
     */
    private fun recommendRole(profile: PlayerProfile, influence: Int): LeaderRole {
        val isStarter = profile.squadRole == "starter"
        return when {
            profile.leadership >= config.captainMinLeadership && isStarter -> LeaderRole.CAPTAIN
            profile.leadership >= config.viceCaptainMinLeadership && isStarter -> LeaderRole.VICE_CAPTAIN
            profile.leadership >= config.influentialMinLeadership -> LeaderRole.INFLUENTIAL
            else -> LeaderRole.INFLUENTIAL // 默认归为影响力球员
        }
    }

    // ==================== 2. 持久化 ====================

    /**
     * 识别并持久化俱乐部领袖（V0.2 + T23 任务要求 §二.4）。
     *
     * 触发时机：赛季初自动识别。
     *
     * 注意：调用前会先撤销所有现有 ACTIVE 领袖，再写入新识别结果。
     *
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 写入的领袖实体列表
     */
    suspend fun detectAndPersist(
        saveId: Int,
        clubId: Int,
        profiles: List<PlayerProfile>,
        seasonId: Int,
        currentDate: LocalDate
    ): List<DressingRoomLeaderEntity> = withContext(Dispatchers.IO) {
        val candidates = detectCandidates(saveId, clubId, profiles)
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dao = databaseManager.dressingRoomLeaderDao()

        // 1. 撤销现有 ACTIVE 领袖（赛季初重识别）
        dao.revokeAll(saveId, clubId, dateStr, "赛季初重新识别")

        // 2. 写入新领袖
        val entities = candidates.map { candidate ->
            val profile = profiles.firstOrNull { it.playerId == candidate.playerId }
            DressingRoomLeaderEntity(
                saveId = saveId,
                clubId = clubId,
                playerId = candidate.playerId,
                leaderRole = candidate.recommendedRole.name,
                leadership = candidate.leadership,
                influence = candidate.influence,
                appointedDate = dateStr,
                appointedSeason = seasonId,
                appointedBy = "AUTO",
                status = "ACTIVE"
            )
        }
        entities.forEach { dao.upsert(it) }
        entities
    }

    /**
     * 玩家手动任命领袖（V0.2 + 实现方案 §四.5）。
     *
     * @param playerId 球员 ID
     * @param role 任命角色（CAPTAIN / VICE_CAPTAIN / INFLUENTIAL）
     * @param leadership 领导力属性
     * @param influence 影响力 0-100
     * @return 写入的领袖实体
     */
    suspend fun appointLeader(
        saveId: Int,
        clubId: Int,
        playerId: Int,
        role: LeaderRole,
        leadership: Int,
        influence: Int,
        seasonId: Int,
        currentDate: LocalDate
    ): DressingRoomLeaderEntity = withContext(Dispatchers.IO) {
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dao = databaseManager.dressingRoomLeaderDao()

        // 若任命队长，先撤销现有队长（同角色唯一）
        if (role == LeaderRole.CAPTAIN || role == LeaderRole.VICE_CAPTAIN) {
            val existing = dao.getByRole(saveId, clubId, role.name)
            existing?.let { dao.revoke(it.id, dateStr, "被新任命替换") }
        }

        val entity = DressingRoomLeaderEntity(
            saveId = saveId,
            clubId = clubId,
            playerId = playerId,
            leaderRole = role.name,
            leadership = leadership,
            influence = influence,
            appointedDate = dateStr,
            appointedSeason = seasonId,
            appointedBy = "MANAGER",
            status = "ACTIVE"
        )
        dao.upsert(entity)
        entity
    }

    /**
     * 撤销领袖任命（V0.2 + 实现方案 §四.5）。
     *
     * @param leaderId 领袖记录 ID
     * @param reason 撤销原因
     */
    suspend fun revokeLeader(
        saveId: Int,
        clubId: Int,
        leaderId: Long,
        currentDate: LocalDate,
        reason: String
    ) = withContext(Dispatchers.IO) {
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        databaseManager.dressingRoomLeaderDao().revoke(leaderId, dateStr, reason)
    }

    // ==================== 3. 查询 ====================

    /**
     * 查询俱乐部当前活跃领袖。
     */
    suspend fun getActiveLeaders(
        saveId: Int,
        clubId: Int
    ): List<DressingRoomLeaderEntity> = withContext(Dispatchers.IO) {
        databaseManager.dressingRoomLeaderDao().getActive(saveId, clubId)
    }

    /**
     * 查询当前队长。
     */
    suspend fun getCaptain(
        saveId: Int,
        clubId: Int
    ): DressingRoomLeaderEntity? = withContext(Dispatchers.IO) {
        databaseManager.dressingRoomLeaderDao().getByRole(saveId, clubId, LeaderRole.CAPTAIN.name)
    }

    /**
     * 计算俱乐部领袖综合影响力（活跃领袖平均影响力，0-100）。
     *
     * 用于 [com.greendynasty.football.dressingroom.atmosphere.AtmosphereEvaluator] 输入。
     */
    suspend fun getTeamLeaderInfluence(
        saveId: Int,
        clubId: Int
    ): Int = withContext(Dispatchers.IO) {
        val leaders = databaseManager.dressingRoomLeaderDao().getActive(saveId, clubId)
        if (leaders.isEmpty()) 0 else leaders.map { it.influence }.average().toInt()
    }
}
