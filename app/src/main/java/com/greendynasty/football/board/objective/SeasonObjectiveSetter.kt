package com.greendynasty.football.board.objective

import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.board.expectation.BoardExpectationManager
import com.greendynasty.football.board.model.BoardConfig
import com.greendynasty.football.board.model.LongTermGoalEntity
import com.greendynasty.football.board.model.SeasonTargetEntity
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.economy.repository.EconomyRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T22 赛季目标设定器（V0.2 11 §四 + T22 方案 §四.2 GoalManager + 任务要求 §二.2）。
 *
 * 职责：
 * 1. 赛季初为指定俱乐部设定 3-5 项目标（联赛排名 / 杯赛 / 欧战 / 财政 / 青训）
 * 2. 基于 [BoardExpectationManager] 计算的期望 + 俱乐部画像 + 上赛季成绩
 * 3. 同步生成 3 年长期目标（声望提升 / 球场扩建 / 青训设施 / 商业收入 / 奖杯）
 *
 * 与 V0.2 §四 GoalManager.generateSeasonTargets 算法对齐：
 * - 联赛排名目标 = 基于 ambition + 上赛季排名
 * - 杯赛目标 = 基于 ambition（野心 >75 → 夺冠，>60 → 决赛，>45 → 半决赛，否则 8 强）
 * - 欧战目标 = 是否有欧战资格 + ambition
 * - 财政目标 = wageToIncomeRatio ≤ 阈值（豪门 0.70 / 中游 0.80 / 保级 0.90）
 * - 青训目标 = youthPreference 决定（青训型俱乐部 ≥3，其他 ≥1）
 *
 * @param databaseManager 三库管理入口
 * @param expectationManager 董事会期望管理器
 * @param clubProfileRepository T18 俱乐部画像仓库
 * @param economyRepository T17 经济仓库
 * @param config 董事会配置
 */
class SeasonObjectiveSetter(
    private val databaseManager: DatabaseManager,
    private val expectationManager: BoardExpectationManager,
    private val clubProfileRepository: ClubProfileRepository,
    private val economyRepository: EconomyRepository,
    private val config: BoardConfig = BoardConfig.DEFAULT
) {

    /**
     * 赛季初生成赛季目标（3-5 项目标）。
     *
     * 由 T07 SeasonStartExecutor 调用。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @return 已生成的赛季目标实体，俱乐部画像不存在时返回 null
     */
    suspend fun generateSeasonTargets(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate
    ): SeasonTargetEntity? {
        val profile = clubProfileRepository.getProfile(clubId) ?: return null
        val expectation = expectationManager.computeExpectation(saveId, clubId, seasonId, currentDate)
            ?: return null

        // 1. 联赛排名目标（CORE）
        val leagueTarget = expectation.expectedLeaguePosition
        val leagueImportance = "CORE"

        // 2. 杯赛目标
        val cupTarget = expectation.expectedCupRound
        val cupImportance = if (profile.ambition > 60) "CORE" else "SECONDARY"

        // 3. 欧战目标（基于上赛季是否获得欧战资格）
        val hasEuropeanQualification = expectationManager.hasEuropeanQualification(
            saveId, clubId, seasonId - 1
        )
        val europeanTarget = if (!hasEuropeanQualification) "NONE"
        else when {
            profile.ambition > 80 -> "QUARTER_FINAL"
            profile.ambition > 65 -> "ROUND_OF_16"
            else -> "GROUP_STAGE"
        }
        val europeanImportance = if (hasEuropeanQualification && profile.ambition > 65) "CORE" else "SECONDARY"

        // 4. 财政目标（来自期望摘要的 wageRatioTarget）
        val wageRatioTarget = expectation.wageRatioTarget
        val financialImportance = if (profile.wageStrictness > 60) "CORE" else "SECONDARY"

        // 5. 青训目标
        val youthPromotionTarget = when {
            profile.youthPreference > 70 -> 3
            profile.youthPreference > 50 -> 2
            else -> 1
        }

        val target = SeasonTargetEntity(
            saveId = saveId,
            clubId = clubId,
            seasonId = seasonId,
            setAt = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            leaguePositionTarget = leagueTarget,
            leaguePositionImportance = leagueImportance,
            cupTarget = cupTarget,
            cupImportance = cupImportance,
            europeanTarget = europeanTarget,
            europeanImportance = europeanImportance,
            financialWageRatioTarget = wageRatioTarget,
            financialImportance = financialImportance,
            youthPromotionTarget = youthPromotionTarget,
            evaluationStatus = "PENDING"
        )
        databaseManager.getSaveDatabase().seasonTargetDao().upsert(target)

        // 同步生成 3 年长期目标（每 3 年生成一次）
        ensureLongTermGoals(saveId, clubId, currentDate, profile.ambition, profile.youthPreference)

        return target
    }

    /**
     * 确保长期目标存在（3 年规划）。
     *
     * V1 简化：仅当无 ACTIVE 状态长期目标时生成 3 年规划。
     *
     * 5 类长期目标：
     * - REPUTATION_RISE：声望提升 +15（3 年）
     * - STADIUM_EXPANSION：球场容量 +30%（5 年）
     * - YOUTH_FACILITY_UPGRADE：青训设施 +2 级（3 年）
     * - COMMERCIAL_GROWTH：商业收入 +50%（5 年）
     * - TROPHY_WIN：5 年内夺冠 1 次
     */
    suspend fun ensureLongTermGoals(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate,
        ambition: Int,
        youthPreference: Int
    ) {
        val longTermGoalDao = databaseManager.getSaveDatabase().longTermGoalDao()
        val activeGoals = longTermGoalDao.getActiveGoals(saveId, clubId)
        if (activeGoals.isNotEmpty()) return

        val currentYear = currentDate.year
        val clubState = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        val currentReputation = clubState?.reputation?.toDouble() ?: 50.0

        // V1 简化：仅生成 3 年声望提升 + 5 年奖杯规划（避免一次生成太多目标）
        val reputationGoal = LongTermGoalEntity(
            saveId = saveId,
            clubId = clubId,
            goalType = "REPUTATION_RISE",
            targetYear = currentYear + 3,
            startYear = currentYear,
            startMetric = currentReputation,
            targetMetric = currentReputation + config.longTermGoal.reputationRise3yrDelta,
            currentMetric = currentReputation,
            progressPercent = 0.0,
            status = "ACTIVE"
        )
        longTermGoalDao.upsert(reputationGoal)

        // 野心高的俱乐部增加 5 年奖杯目标
        if (ambition > 60) {
            val trophyGoal = LongTermGoalEntity(
                saveId = saveId,
                clubId = clubId,
                goalType = "TROPHY_WIN",
                targetYear = currentYear + 5,
                startYear = currentYear,
                startMetric = 0.0,
                targetMetric = config.longTermGoal.trophyWin5yrCount.toDouble(),
                currentMetric = 0.0,
                progressPercent = 0.0,
                status = "ACTIVE"
            )
            longTermGoalDao.upsert(trophyGoal)
        }

        // 青训偏好高的俱乐部增加青训设施升级目标
        if (youthPreference > 60) {
            val youthFacilityGoal = LongTermGoalEntity(
                saveId = saveId,
                clubId = clubId,
                goalType = "YOUTH_FACILITY_UPGRADE",
                targetYear = currentYear + 3,
                startYear = currentYear,
                startMetric = 0.0,
                targetMetric = config.longTermGoal.youthFacilityUpgrade3yrLevels.toDouble(),
                currentMetric = 0.0,
                progressPercent = 0.0,
                status = "ACTIVE"
            )
            longTermGoalDao.upsert(youthFacilityGoal)
        }
    }

    /**
     * 玩家请求降低目标。
     *
     * 规则（V0.2 + T22 方案 §八 target_lowering）：
     * - 仅可在赛季前 1/3 阶段申请
     * - 每次降低 1 档（如前 4 → 前 6）
     * - 满意度惩罚 -5
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param seasonId 赛季 ID
     * @param currentDate 当前游戏日期
     * @param seasonProgressRatio 赛季进度 0-1（用于判断是否在赛季前 1/3 阶段）
     * @return 降低结果
     */
    suspend fun requestTargetLowering(
        saveId: Int,
        clubId: Int,
        seasonId: Int,
        currentDate: LocalDate,
        seasonProgressRatio: Double
    ): com.greendynasty.football.board.model.TargetLoweringResult {
        // 1. 校验赛季进度
        if (seasonProgressRatio > config.targetLowering.seasonProgressMaxRatio) {
            return com.greendynasty.football.board.model.TargetLoweringResult(
                allowed = false,
                reason = "仅可在赛季前 1/3 阶段申请降低目标（当前进度 ${(seasonProgressRatio * 100).toInt()}%）。"
            )
        }

        // 2. 查询当前目标
        val target = databaseManager.getSaveDatabase().seasonTargetDao()
            .getBySeason(saveId, clubId, seasonId)
            ?: return com.greendynasty.football.board.model.TargetLoweringResult(
                allowed = false,
                reason = "未找到当前赛季目标。"
            )

        // 3. 降低 1 档（联赛排名目标 +1，如前 4 → 前 5，前 6 → 前 7）
        val newPosition = (target.leaguePositionTarget + config.targetLowering.tierDown)
            .coerceAtMost(17) // 不超过保级线
        val updatedTarget = target.copy(leaguePositionTarget = newPosition)
        databaseManager.getSaveDatabase().seasonTargetDao().upsert(updatedTarget)

        // 4. 满意度惩罚 -5
        val clubState = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        if (clubState != null) {
            val newSatisfaction = (clubState.boardSatisfaction + config.targetLowering.satisfactionPenalty)
                .coerceIn(0, 100)
            databaseManager.saveClubStateDao()
                .updateBoardSatisfaction(saveId, clubId, newSatisfaction)
        }

        return com.greendynasty.football.board.model.TargetLoweringResult(
            allowed = true,
            reason = "目标已降低 1 档，联赛排名目标从 ${target.leaguePositionTarget} 调整为 $newPosition。",
            newLeaguePositionTarget = newPosition,
            satisfactionImpact = config.targetLowering.satisfactionPenalty
        )
    }
}
