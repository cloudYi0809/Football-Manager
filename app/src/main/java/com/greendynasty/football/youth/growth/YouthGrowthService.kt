package com.greendynasty.football.youth.growth

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.growth.calculator.GrowthCalculator
import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthInput
import com.greendynasty.football.growth.model.GrowthPhase
import com.greendynasty.football.growth.model.GrowthRangeTier
import com.greendynasty.football.growth.model.GrowthResult
import com.greendynasty.football.growth.model.classifyGrowthPhase
import com.greendynasty.football.youth.model.YouthAcademyConfig
import com.greendynasty.football.youth.model.YouthAcademyStateEntity
import com.greendynasty.football.youth.model.YouthPlayerEntity
import com.greendynasty.football.youth.model.YouthPlayerStatus
import com.greendynasty.football.youth.model.YouthTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * T16 青训球员月度成长服务（V0.2 08 §三 + T16 方案 §四.5 + §五.3）
 *
 * 严格复用 T09 [GrowthCalculator] 的 10 因子月度成长公式，不重写算法。
 * 青训特化点仅在于输入参数：
 * - training_quality: 青训训练质量 = 0.70 + (training_facility / 100) × 0.40，范围 0.70-1.10
 * - coach_level: 青训教练质量（U18 / U21 分开）
 * - league_intensity: 0.5（青训不踢职业联赛）
 * - tactical_fit: 0.7（青训战术适配默认）
 *
 * 性能要求：全队青训球员 ≤500ms。
 *
 * 异常保护（V0.2 §十五）由 [YouthAnomalyGuard] 处理，叠加在 T0Y 结果之上。
 *
 * @param databaseManager 三库管理入口
 * @param growthCalculator T09 成长计算器
 * @param anomalyGuard 青训异常保护
 * @param config 青训学院配置
 * @param growthConfig T09 成长配置
 */
class YouthGrowthService(
    private val databaseManager: DatabaseManager,
    private val growthCalculator: GrowthCalculator,
    private val anomalyGuard: YouthAnomalyGuard,
    private val config: YouthAcademyConfig = YouthAcademyConfig.getDefault(),
    private val growthConfig: GrowthConfig = GrowthConfig.getDefault()
) {

    /**
     * 月度成长处理（由 T07 月结调用）。
     *
     * 性能目标：全队青训球员 ≤500ms。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentDate 当前游戏日期
     * @return 各青训球员的成长结果
     */
    suspend fun processMonthlyGrowth(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ): List<YouthGrowthResult> = withContext(Dispatchers.IO) {
        val academy = databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
            ?: return@withContext emptyList()

        val youthPlayers = databaseManager.youthPlayerDao().getByClub(saveId, clubId)
        val results = mutableListOf<YouthGrowthResult>()

        // 青训训练质量（用于 T0Y training_quality_factor）
        val trainingQuality = calculateYouthTrainingQuality(academy)

        for (player in youthPlayers) {
            // 跳过已提拔 / 外租 / 离队的球员
            if (player.status == YouthPlayerStatus.FIRST_TEAM.name ||
                player.status == YouthPlayerStatus.LOANED_OUT.name ||
                player.status == YouthPlayerStatus.LEAVING.name
            ) {
                continue
            }

            val age = calculateAge(player.birthDate, currentDate)
            if (age < 14) continue // 异常保护：未到青训年龄不成长

            val growthResult = processSinglePlayer(
                player, age, academy, trainingQuality, currentDate
            )
            if (growthResult != null) {
                results.add(growthResult)
            }
        }

        results
    }

    /** 处理单个青训球员的月度成长。 */
    private suspend fun processSinglePlayer(
        player: YouthPlayerEntity,
        age: Int,
        academy: YouthAcademyStateEntity,
        trainingQuality: Double,
        currentDate: LocalDate
    ): YouthGrowthResult? {
        // 1. 构造 T0Y GrowthInput（复用 T09 GrowthCalculator，避免重写公式）
        val growthInput = buildGrowthInput(player, age, academy, trainingQuality, currentDate)
            ?: return null

        // 2. 调用 T0Y 月度成长公式
        val rawGrowth = growthCalculator.calculate(growthInput)

        // 3. 计算导师加成（V1 简化：0.05 默认加成，有导师则 0.08）
        val mentorBonus = if (player.mentorPlayerId != null) 0.08 else 0.0

        // 4. V0.2 §十五 异常保护（青训版）
        val protectedGrowth = anomalyGuard.apply(rawGrowth, player, age, mentorBonus)

        // 5. 应用成长
        val newCa = (player.currentCa + protectedGrowth.caDelta)
            .coerceIn(1, player.potentialPa)

        databaseManager.youthPlayerDao().updateCa(player.youthPlayerId, newCa)

        // 6. 同步 CA 到 save_player_state
        runCatching {
            databaseManager.savePlayerStateDao().updateCa(player.saveId, player.youthPlayerId, newCa)
        }

        // 7. 自动升级梯队（17 岁 → 18 岁升至 U21）
        if (age >= 18 && player.tier == YouthTier.U18.name) {
            databaseManager.youthPlayerDao().updateTier(player.youthPlayerId, YouthTier.U21.name)
        }

        // 8. 记录成长日志（V1 简化：追加 JSON）
        val updatedLog = appendGrowthLog(player.monthlyGrowthLogJson, currentDate, protectedGrowth.caDelta)
        databaseManager.youthPlayerDao().updateGrowthLog(player.youthPlayerId, updatedLog)

        return YouthGrowthResult(
            youthPlayerId = player.youthPlayerId,
            playerName = player.playerName,
            caBefore = player.currentCa,
            caAfter = newCa,
            caDelta = protectedGrowth.caDelta,
            realizationScore = protectedGrowth.realizationScore,
            age = age
        )
    }

    /**
     * 构造 T0Y GrowthInput。
     *
     * V1 简化：青训球员不在 history.player / history.player_attributes 表中
     * （属于动态生成），构造 PlayerEntity / PlayerAttributesEntity 兜底对象。
     */
    private fun buildGrowthInput(
        player: YouthPlayerEntity,
        age: Int,
        academy: YouthAcademyStateEntity,
        trainingQuality: Double,
        currentDate: LocalDate
    ): GrowthInput? {
        val savePlayerState = SavePlayerStateEntity(
            id = 0,
            saveId = player.saveId,
            playerId = player.youthPlayerId,
            currentClubId = player.clubId,
            loanClubId = null,
            currentCa = player.currentCa,
            currentPa = player.potentialPa,
            condition = 100,
            morale = 60,
            injuryStatus = "healthy",
            injuryUntil = null,
            contractUntil = player.contractUntil,
            wage = player.wage,
            marketValue = 0,
            careerStatus = "active",
            squadRole = "prospect"
        )

        // V1 简化：青训球员不在 history.player 表，构造兜底 PlayerEntity
        val playerBase = PlayerEntity(
            playerId = player.youthPlayerId,
            sourceId = null,
            realName = player.playerName,
            displayName = player.playerName,
            birthDate = player.birthDate,
            nationality = player.nationality,
            secondNationality = null,
            height = null,
            weight = null,
            preferredFoot = null,
            primaryPosition = player.primaryPosition,
            secondaryPositions = player.alternativePositions,
            personality = null,
            retireAgeBase = 35,
            portraitPath = null,
            createdAt = null,
            updatedAt = null
        )

        // V1 简化：青训球员不在 history.player_attributes 表，构造兜底 PlayerAttributesEntity
        val attributes = PlayerAttributesEntity(
            id = 0,
            playerId = player.youthPlayerId,
            seasonId = 0,
            ca = player.currentCa,
            pa = player.potentialPa,
            professionalism = player.professionalism,
            ambition = player.ambition,
            injuryProneness = player.injuryProneness
        )

        val growthPhase = classifyGrowthPhase(age)
        val rangeTier = GrowthRangeTier.ACTIVE // 青训球员走 ACTIVE 简化计算
        val coachLevel = if (player.tier == YouthTier.U18.name) {
            academy.u18CoachQuality
        } else {
            academy.u21CoachQuality
        }

        val club = SaveClubStateEntity(
            id = 0,
            saveId = academy.saveId,
            clubId = academy.clubId,
            balance = 0,
            transferBudget = 0,
            wageBudget = 0,
            reputation = academy.academyReputation,
            boardSatisfaction = 50,
            fanSatisfaction = 50,
            dressingRoomMorale = 50
        )

        return GrowthInput(
            player = savePlayerState,
            playerBase = playerBase,
            attributes = attributes,
            age = age,
            growthPhase = growthPhase,
            rangeTier = rangeTier,
            monthlyTraining = null, // V1 简化：青训无月度训练记录
            monthlyPlayingTime = null, // V1 简化：青训不踢正式比赛
            activeInjury = null,
            injuryHistory = emptyList(),
            moraleValue = 60,
            mentorEffect = if (player.mentorPlayerId != null) 0.08 else 0.0,
            club = club,
            clubTrainingQuality = trainingQuality,
            clubCoachLevel = coachLevel,
            clubLeagueIntensity = 0.5, // 青训不踢职业联赛
            tacticalFitScore = 0.7, // 青训战术适配默认
            nationTalentPoolBonus = academy.nationTalentPoolBonus,
            seasonId = 0,
            executionDate = currentDate
        )
    }

    /**
     * 青训训练质量（用于 T0Y training_quality_factor）。
     *
     * 公式：training_quality = 0.70 + (training_facility / 100) × 0.40
     * 范围 0.70 - 1.10
     */
    private fun calculateYouthTrainingQuality(academy: YouthAcademyStateEntity): Double {
        val base = config.trainingQualityBaseMin +
            (academy.trainingFacility / 100.0) * config.trainingQualityMultiplier
        return base.coerceIn(config.trainingQualityBaseMin, config.trainingQualityBaseMax)
    }

    /** 由出生日期计算年龄。 */
    private fun calculateAge(birthDate: String, currentDate: LocalDate): Int {
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrDefault(18)
    }

    /** 追加月度成长日志（V1 简化：手动拼接 JSON 数组）。 */
    private fun appendGrowthLog(currentLogJson: String, date: LocalDate, caDelta: Int): String {
        // V1 简化：保留最近 12 个月记录
        val entry = "{\"date\":\"${date}\",\"ca_delta\":$caDelta}"
        val existing = if (currentLogJson.isBlank() || currentLogJson == "[]") {
            emptyList()
        } else {
            // 简化解析：用正则提取 entries（避免引入 JSON 库）
            Regex("""\{[^}]+\}""").findAll(currentLogJson)
                .map { it.value }
                .toList()
        }
        val updated = (existing + entry).takeLast(12)
        return "[${updated.joinToString(",")}]"
    }
}

/**
 * T16 青训球员月度成长结果。
 */
data class YouthGrowthResult(
    val youthPlayerId: Int,
    val playerName: String,
    val caBefore: Int,
    val caAfter: Int,
    val caDelta: Int,
    val realizationScore: Double,
    val age: Int
)
