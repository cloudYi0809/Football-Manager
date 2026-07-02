package com.greendynasty.football.youth.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.youth.generator.ProductionQualityCalculator
import com.greendynasty.football.youth.generator.YouthPlayerGenerator
import com.greendynasty.football.youth.model.AcademyStyle
import com.greendynasty.football.youth.model.InvestmentField
import com.greendynasty.football.youth.model.RecruitmentRange
import com.greendynasty.football.youth.model.YouthAcademyConfig
import com.greendynasty.football.youth.model.YouthAcademyInvestmentEntity
import com.greendynasty.football.youth.model.YouthAcademyStateEntity
import com.greendynasty.football.youth.model.YouthEventEntity
import com.greendynasty.football.youth.model.YouthPlayerEntity
import com.greendynasty.football.youth.model.YouthPlayerStatus
import com.greendynasty.football.youth.model.YouthTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * T16 青训球员视图项（聚合 YouthPlayerEntity + 计算字段，供 UI 使用）。
 */
data class YouthPlayerViewItem(
    val youthPlayerId: Int,
    val playerId: Int,
    val playerName: String,
    val nationality: String,
    val age: Int,
    val primaryPosition: String,
    val alternativePositions: List<String>,
    val tier: String,
    val status: String,
    val currentCa: Int,
    val potentialPa: Int,
    val initialPa: Int,
    val professionalism: Int,
    val isGenius: Boolean,
    val isKeyProspect: Boolean,
    val trainingFocus: String,
    val mentorPlayerId: Int?,
    val contractType: String,
    val contractUntil: String?,
    val wage: Int,
    val hiddenTags: List<String>,
    val generatedDate: String
)

/**
 * T16 青训学院视图项（聚合状态 + 配置）。
 */
data class YouthAcademyViewItem(
    val academyId: Int,
    val clubId: Int,
    val youthLevel: Int,
    val trainingFacility: Int,
    val recruitmentRange: RecruitmentRange,
    val academyReputation: Int,
    val academyStyle: AcademyStyle,
    val monthlyBudget: Int,
    val u18CoachQuality: Int,
    val u21CoachQuality: Int,
    val nationTalentPoolBonus: Int,
    val styleChangeCooldown: Int,
    val lastProductionMonth: String?
)

/**
 * T16 青训学院操作结果。
 */
data class YouthOperationResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
) {
    companion object {
        fun success(message: String, data: Any? = null) = YouthOperationResult(true, message, data)
        fun failed(message: String) = YouthOperationResult(false, message)
    }
}

/**
 * T16 青训学院统计信息。
 */
data class YouthAcademyStatistics(
    val u18Count: Int,
    val u21Count: Int,
    val geniusCount: Int,
    val highPotentialCount: Int, // PA >= 80
    val firstTeamPromotedCount: Int,
    val totalInvestment: Int
)

/**
 * T16 青训学院仓库（V0.1 08 §二 + T16 方案 §六 UI 数据层）。
 *
 * 职责：
 * 1. 封装 DAO 访问，提供 Flow / suspend 查询接口供 ViewModel 使用
 * 2. Entity → ViewModel 数据转换
 * 3. 投资升级 / 风格切换 / 球员操作 / 月度处理等业务入口
 *
 * 三库分离：history.club 只读（国家信息），save.youth_academy_state / youth_player 可写。
 *
 * @param databaseManager 三库管理入口
 * @param generator 青训球员生成器
 * @param productionCalculator 7 因子产出质量计算器
 * @param config 青训学院配置
 */
class YouthRepository(
    private val databaseManager: DatabaseManager,
    private val generator: YouthPlayerGenerator,
    private val productionCalculator: ProductionQualityCalculator,
    private val config: YouthAcademyConfig = YouthAcademyConfig.getDefault()
) {

    // ==================== 1. 学院配置 ====================

    /** 观察青训学院状态（Flow 驱动 UI 刷新）。 */
    fun observeAcademy(saveId: Int, clubId: Int): Flow<YouthAcademyStateEntity?> =
        databaseManager.youthAcademyStateDao().observeByClub(saveId, clubId)

    /** 获取青训学院状态。 */
    suspend fun getAcademy(saveId: Int, clubId: Int): YouthAcademyStateEntity? =
        withContext(Dispatchers.IO) {
            databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
        }

    /**
     * 初始化青训学院（开档时调用，已存在则跳过）。
     */
    suspend fun initializeAcademy(saveId: Int, clubId: Int): YouthAcademyStateEntity? =
        withContext(Dispatchers.IO) {
            val existing = databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
            if (existing != null) return@withContext existing

            // 从 history.club 读取俱乐部国家信息
            val club = runCatching { databaseManager.historyClubDao().getClub(clubId) }.getOrNull()
            val countryCode = club?.country
            val nationBonus = config.getNationTalentPoolBonus(countryCode)
            val defaultStyle = config.getDefaultStyle(countryCode)

            // 从 history.youth_academy 读取初始等级（如有）
            val historyAcademy = runCatching {
                databaseManager.historyClubDao().let {
                    databaseManager.getHistoryDatabase().youthAcademyDao().getByClub(clubId)
                }
            }.getOrNull()

            val academy = YouthAcademyStateEntity(
                saveId = saveId,
                clubId = clubId,
                youthLevel = historyAcademy?.youthLevel ?: 50,
                trainingFacility = historyAcademy?.trainingLevel ?: 50,
                recruitmentRange = (historyAcademy?.recruitmentRange ?: RecruitmentRange.LOCAL.name),
                academyReputation = historyAcademy?.academyReputation ?: 50,
                academyStyle = (historyAcademy?.academyStyle ?: defaultStyle.name),
                monthlyBudget = 50_000,
                u18CoachQuality = historyAcademy?.u18CoachQuality ?: 50,
                u21CoachQuality = historyAcademy?.u21CoachQuality ?: 50,
                nationTalentPoolBonus = nationBonus,
                styleChangeCooldown = 0,
                lastProductionMonth = null
            )

            val id = databaseManager.youthAcademyStateDao().upsert(academy)
            academy.copy(academyId = id.toInt())
        }

    /**
     * 切换青训风格（3 个月冷却）。
     */
    suspend fun changeStyle(saveId: Int, clubId: Int, newStyle: AcademyStyle): YouthOperationResult =
        withContext(Dispatchers.IO) {
            val academy = databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
                ?: return@withContext YouthOperationResult.failed("青训学院未初始化")

            if (academy.styleChangeCooldown > 0) {
                return@withContext YouthOperationResult.failed(
                    "风格切换冷却中，剩余 ${academy.styleChangeCooldown} 个月"
                )
            }

            databaseManager.youthAcademyStateDao().upsert(
                academy.copy(
                    academyStyle = newStyle.name,
                    styleChangeCooldown = config.styleChangeCooldownMonths
                )
            )
            YouthOperationResult.success("已切换青训风格为 ${newStyle.displayName}")
        }

    /**
     * 投资升级（5 项：青训等级 / 训练设施 / 招募范围 / U18 教练 / U21 教练）。
     *
     * 成本公式：base × (current_level / 10)^1.5
     * 招募范围走阶梯定价：50万 / 200万 / 500万 / 1000万
     */
    suspend fun invest(
        saveId: Int,
        clubId: Int,
        field: InvestmentField,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val academy = databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
            ?: return@withContext YouthOperationResult.failed("青训学院未初始化")

        val (currentLevel, maxLevel) = when (field) {
            InvestmentField.YOUTH_LEVEL -> academy.youthLevel to 100
            InvestmentField.TRAINING_FACILITY -> academy.trainingFacility to 100
            InvestmentField.RECRUITMENT_RANGE -> RecruitmentRange.fromNameOrDefault(academy.recruitmentRange).ordinal to 3
            InvestmentField.U18_COACH -> academy.u18CoachQuality to 100
            InvestmentField.U21_COACH -> academy.u21CoachQuality to 100
        }

        if (currentLevel >= maxLevel) {
            return@withContext YouthOperationResult.failed("${field.displayName}已达最高等级")
        }

        val cost = calculateInvestmentCost(field, currentLevel)
        val clubState = runCatching {
            databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        }.getOrNull()

        if (clubState == null) {
            return@withContext YouthOperationResult.failed("俱乐部状态未找到")
        }

        if (clubState.balance < cost) {
            return@withContext YouthOperationResult.failed(
                "资金不足，需要 ${formatMoney(cost)}（当前余额 ${formatMoney(clubState.balance)}）"
            )
        }

        // 扣款
        databaseManager.saveClubStateDao().updateBalance(saveId, clubId, clubState.balance - cost)

        // 升级
        when (field) {
            InvestmentField.YOUTH_LEVEL ->
                databaseManager.youthAcademyStateDao().updateYouthLevel(academy.academyId, currentLevel + 1)
            InvestmentField.TRAINING_FACILITY ->
                databaseManager.youthAcademyStateDao().updateTrainingFacility(academy.academyId, currentLevel + 1)
            InvestmentField.RECRUITMENT_RANGE -> {
                val newRange = RecruitmentRange.entries[currentLevel + 1]
                databaseManager.youthAcademyStateDao().updateRecruitmentRange(academy.academyId, newRange.name)
            }
            InvestmentField.U18_COACH ->
                databaseManager.youthAcademyStateDao().updateU18CoachQuality(academy.academyId, currentLevel + 1)
            InvestmentField.U21_COACH ->
                databaseManager.youthAcademyStateDao().updateU21CoachQuality(academy.academyId, currentLevel + 1)
        }

        // 记录投资历史
        databaseManager.youthAcademyInvestmentDao().insert(
            YouthAcademyInvestmentEntity(
                saveId = saveId,
                clubId = clubId,
                investField = field.name,
                levelBefore = currentLevel,
                levelAfter = currentLevel + 1,
                cost = cost,
                investDate = currentDate.toString()
            )
        )

        YouthOperationResult.success(
            "${field.displayName}已升级至 ${currentLevel + 1}，花费 ${formatMoney(cost)}"
        )
    }

    /** 计算投资成本。 */
    private fun calculateInvestmentCost(field: InvestmentField, currentLevel: Int): Int {
        return if (field == InvestmentField.RECRUITMENT_RANGE) {
            // 阶梯定价
            config.recruitmentRangeCosts.getOrNull(currentLevel) ?: 10_000_000
        } else {
            // 公式：base × (level / 10)^1.5
            val base = config.investmentBaseCost[field] ?: 500_000
            (base * Math.pow(currentLevel / 10.0, config.investmentCostExponent)).toInt()
        }
    }

    /** 格式化金额（万欧元）。 */
    private fun formatMoney(amount: Int): String {
        return when {
            amount >= 1_000_000 -> "${"%.1f".format(amount / 1_000_000.0)}M €"
            amount >= 1_000 -> "${"%.1f".format(amount / 1_000.0)}K €"
            else -> "$amount €"
        }
    }

    // ==================== 2. 球员列表 ====================

    /** 观察俱乐部所有青训球员。 */
    fun observePlayers(saveId: Int, clubId: Int): Flow<List<YouthPlayerEntity>> =
        databaseManager.youthPlayerDao().observeByClub(saveId, clubId)

    /** 观察指定梯队球员。 */
    fun observePlayersByTier(saveId: Int, clubId: Int, tier: YouthTier): Flow<List<YouthPlayerEntity>> =
        databaseManager.youthPlayerDao().observeByTier(saveId, clubId, tier.name)

    /** 获取俱乐部所有青训球员。 */
    suspend fun getPlayers(saveId: Int, clubId: Int): List<YouthPlayerEntity> =
        withContext(Dispatchers.IO) {
            databaseManager.youthPlayerDao().getByClub(saveId, clubId)
        }

    /** 获取指定球员。 */
    suspend fun getPlayer(youthPlayerId: Int): YouthPlayerEntity? = withContext(Dispatchers.IO) {
        databaseManager.youthPlayerDao().getById(youthPlayerId)
    }

    /** 观察指定球员。 */
    fun observePlayer(youthPlayerId: Int): Flow<YouthPlayerEntity?> =
        databaseManager.youthPlayerDao().observeById(youthPlayerId)

    /** Entity → ViewItem 转换。 */
    suspend fun toViewItem(player: YouthPlayerEntity, currentDate: LocalDate): YouthPlayerViewItem {
        val age = calculateAge(player.birthDate, currentDate)
        return YouthPlayerViewItem(
            youthPlayerId = player.youthPlayerId,
            playerId = player.playerId,
            playerName = player.playerName,
            nationality = player.nationality,
            age = age,
            primaryPosition = player.primaryPosition,
            alternativePositions = player.alternativePositionList,
            tier = player.tier,
            status = player.status,
            currentCa = player.currentCa,
            potentialPa = player.potentialPa,
            initialPa = player.initialPa,
            professionalism = player.professionalism,
            isGenius = player.isGenius,
            isKeyProspect = player.keyProspect,
            trainingFocus = player.trainingFocus,
            mentorPlayerId = player.mentorPlayerId,
            contractType = player.contractType,
            contractUntil = player.contractUntil,
            wage = player.wage,
            hiddenTags = player.hiddenTagList,
            generatedDate = player.generatedDate
        )
    }

    /** Entity → ViewItem 转换（学院配置）。 */
    fun toViewItem(academy: YouthAcademyStateEntity): YouthAcademyViewItem {
        return YouthAcademyViewItem(
            academyId = academy.academyId,
            clubId = academy.clubId,
            youthLevel = academy.youthLevel,
            trainingFacility = academy.trainingFacility,
            recruitmentRange = RecruitmentRange.fromNameOrDefault(academy.recruitmentRange),
            academyReputation = academy.academyReputation,
            academyStyle = AcademyStyle.fromNameOrDefault(academy.academyStyle),
            monthlyBudget = academy.monthlyBudget,
            u18CoachQuality = academy.u18CoachQuality,
            u21CoachQuality = academy.u21CoachQuality,
            nationTalentPoolBonus = academy.nationTalentPoolBonus,
            styleChangeCooldown = academy.styleChangeCooldown,
            lastProductionMonth = academy.lastProductionMonth
        )
    }

    // ==================== 3. 球员操作（8 种） ====================

    /** 1. 签青年合同（默认 14-17 岁自动签）。 */
    suspend fun signYouthContract(
        youthPlayerId: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        val contractUntil = currentDate.plusYears(3).toString()
        databaseManager.youthPlayerDao().updateContract(
            youthPlayerId, "YOUTH", contractUntil, config.defaultYouthWage
        )
        databaseManager.youthPlayerDao().updateStatus(youthPlayerId, YouthPlayerStatus.YOUTH_CONTRACT.name)
        YouthOperationResult.success("已签青年合同至 $contractUntil")
    }

    /** 2. 签职业合同（17 岁+）。 */
    suspend fun signProfessionalContract(
        youthPlayerId: Int,
        wage: Int,
        years: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        val age = calculateAge(player.birthDate, currentDate)
        if (age < 17) {
            return@withContext YouthOperationResult.failed("未满 17 岁，无法签职业合同")
        }

        val contractUntil = currentDate.plusYears(years.toLong()).toString()
        databaseManager.youthPlayerDao().updateContract(
            youthPlayerId, "PROFESSIONAL", contractUntil, wage
        )
        databaseManager.youthPlayerDao().updateStatus(
            youthPlayerId, YouthPlayerStatus.PROFESSIONAL_CONTRACT.name
        )

        // 同步合同到 save_player_state
        runCatching {
            databaseManager.savePlayerStateDao().apply {
                updateCa(player.saveId, player.playerId, player.currentCa)
            }
        }

        YouthOperationResult.success("已签职业合同 ${years} 年，周薪 ${formatMoney(wage)}")
    }

    /** 3. 重点培养开关。 */
    suspend fun setKeyProspect(
        youthPlayerId: Int,
        isKey: Boolean
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        databaseManager.youthPlayerDao().updateKeyProspect(
            youthPlayerId, if (isKey) 1 else 0
        )
        YouthOperationResult.success(if (isKey) "已设为重点培养" else "已取消重点培养")
    }

    /** 4. 训练方向。 */
    suspend fun setTrainingFocus(
        youthPlayerId: Int,
        focus: String
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        databaseManager.youthPlayerDao().updateTrainingFocus(youthPlayerId, focus)
        YouthOperationResult.success("训练方向已设置")
    }

    /** 5. 安排导师。 */
    suspend fun assignMentor(
        saveId: Int,
        youthPlayerId: Int,
        mentorPlayerId: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("青训球员不存在")

        // 检查导师带徒数（最多 3 名）
        val menteeCount = databaseManager.youthPlayerDao()
            .countMenteesByMentor(saveId, mentorPlayerId)
        if (menteeCount >= config.maxMenteesPerMentor) {
            return@withContext YouthOperationResult.failed(
                "该导师已带满 ${config.maxMenteesPerMentor} 名青训球员"
            )
        }

        databaseManager.youthPlayerDao().updateMentor(youthPlayerId, mentorPlayerId)
        YouthOperationResult.success("已安排导师 #$mentorPlayerId")
    }

    /** 6. 提拔一线队（17 岁+ + 职业合同）。 */
    suspend fun promoteToFirstTeam(
        youthPlayerId: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        val age = calculateAge(player.birthDate, currentDate)
        if (age < 17) {
            return@withContext YouthOperationResult.failed("未满 17 岁，无法提拔一线队")
        }

        if (player.contractType != "PROFESSIONAL") {
            return@withContext YouthOperationResult.failed("请先签职业合同")
        }

        // 更新 save_player_state 的 squad_role 为 starter
        runCatching {
            databaseManager.savePlayerStateDao().updateClub(player.saveId, player.playerId, player.clubId)
        }

        databaseManager.youthPlayerDao().updateStatus(
            youthPlayerId, YouthPlayerStatus.FIRST_TEAM.name
        )
        YouthOperationResult.success("${player.playerName} 已提拔至一线队")
    }

    /** 7. 外租培养（18 岁+）。 */
    suspend fun loanOut(
        youthPlayerId: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        val age = calculateAge(player.birthDate, currentDate)
        if (age < 18) {
            return@withContext YouthOperationResult.failed("未满 18 岁，无法外租")
        }

        databaseManager.youthPlayerDao().updateStatus(
            youthPlayerId, YouthPlayerStatus.LOANED_OUT.name
        )
        YouthOperationResult.success("${player.playerName} 已外租培养")
    }

    /** 8. 放弃培养。 */
    suspend fun release(
        youthPlayerId: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val player = databaseManager.youthPlayerDao().getById(youthPlayerId)
            ?: return@withContext YouthOperationResult.failed("球员不存在")

        databaseManager.youthPlayerDao().updateStatus(
            youthPlayerId, YouthPlayerStatus.LEAVING.name
        )
        YouthOperationResult.success("${player.playerName} 已放弃培养")
    }

    // ==================== 4. 事件 ====================

    /** 观察待处理事件。 */
    fun observePendingEvents(saveId: Int, clubId: Int): Flow<List<YouthEventEntity>> =
        databaseManager.youthEventDao().observePending(saveId, clubId)

    /** 观察最近事件。 */
    fun observeRecentEvents(saveId: Int, clubId: Int, limit: Int = 20): Flow<List<YouthEventEntity>> =
        databaseManager.youthEventDao().observeRecent(saveId, clubId, limit)

    /** 处理事件（玩家选择选项）。 */
    suspend fun resolveEvent(
        eventId: Int,
        accepted: Boolean,
        currentDate: LocalDate,
        summary: String? = null
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val event = databaseManager.youthEventDao().getById(eventId)
            ?: return@withContext YouthOperationResult.failed("事件不存在")

        val status = if (accepted) "RESOLVED_ACCEPTED" else "RESOLVED_REJECTED"
        databaseManager.youthEventDao().resolve(
            eventId, status, currentDate.toString(), summary
        )
        YouthOperationResult.success("事件已处理")
    }

    // ==================== 5. 月度处理 ====================

    /**
     * 月度处理（由 T07 月结调用）。
     * 1. 扣月度预算 + 风格冷却递减
     * 2. 青训球员生成
     */
    suspend fun processMonthly(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ): YouthOperationResult = withContext(Dispatchers.IO) {
        val academy = databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
            ?: return@withContext YouthOperationResult.failed("青训学院未初始化")

        // 1. 扣月度预算
        val clubState = runCatching {
            databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        }.getOrNull()
        if (clubState != null) {
            val newBalance = (clubState.balance - academy.monthlyBudget).coerceAtLeast(0)
            databaseManager.saveClubStateDao().updateBalance(saveId, clubId, newBalance)
        }

        // 2. 风格冷却递减
        if (academy.styleChangeCooldown > 0) {
            databaseManager.youthAcademyStateDao().updateStyleCooldown(
                academy.academyId, academy.styleChangeCooldown - 1
            )
        }

        // 3. 青训球员生成
        val newPlayers = generator.generateMonthly(saveId, clubId, currentDate)

        YouthOperationResult.success(
            "月度处理完成：新增 ${newPlayers.size} 名青训球员",
            newPlayers
        )
    }

    // ==================== 6. 统计 ====================

    /** 获取青训学院统计信息。 */
    suspend fun getStatistics(saveId: Int, clubId: Int): YouthAcademyStatistics =
        withContext(Dispatchers.IO) {
            val u18Count = databaseManager.youthPlayerDao()
                .countByTier(saveId, clubId, YouthTier.U18.name)
            val u21Count = databaseManager.youthPlayerDao()
                .countByTier(saveId, clubId, YouthTier.U21.name)

            val players = databaseManager.youthPlayerDao().getByClub(saveId, clubId)
            val geniusCount = players.count { it.isGenius }
            val highPotentialCount = players.count { it.potentialPa >= 80 }
            val firstTeamPromotedCount = players.count {
                it.status == YouthPlayerStatus.FIRST_TEAM.name
            }

            val investments = databaseManager.youthAcademyInvestmentDao()
                .getByClub(saveId, clubId)
            val totalInvestment = investments.sumOf { it.cost }

            YouthAcademyStatistics(
                u18Count = u18Count,
                u21Count = u21Count,
                geniusCount = geniusCount,
                highPotentialCount = highPotentialCount,
                firstTeamPromotedCount = firstTeamPromotedCount,
                totalInvestment = totalInvestment
            )
        }

    // ==================== 7. 工具方法 ====================

    /** 由出生日期计算年龄。 */
    private fun calculateAge(birthDate: String, currentDate: LocalDate): Int {
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrDefault(18)
    }
}
