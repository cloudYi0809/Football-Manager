package com.greendynasty.football.youth.generator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.youth.model.AcademyStyle
import com.greendynasty.football.youth.model.RecruitmentRange
import com.greendynasty.football.youth.model.YouthAcademyConfig
import com.greendynasty.football.youth.model.YouthAcademyStateEntity
import com.greendynasty.football.youth.model.YouthPlayerEntity
import com.greendynasty.football.youth.model.YouthPlayerStatus
import com.greendynasty.football.youth.model.YouthTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * T16 青训球员生成器（V0.1 08 §二.3 + V0.2 §十五 + T16 方案 §四.2 + §五.2）
 *
 * 月度概率性生成 3-8 名青训球员（受梯队容量与产出质量影响）。
 *
 * 7 因子产出质量公式见 [ProductionQualityCalculator]。
 *
 * 球员生成流程：
 * 1. 检查本月是否已生成 + 梯队容量
 * 2. 计算 7 因子产出质量
 * 3. 概率决定是否本月生成（避免每月必生成）
 * 4. 决定生成数量（高分学院有几率双倍生成）
 * 5. 单球员生成：
 *    - 按年龄权重抽取年龄（14-18 岁，14-17 岁占 92%）
 *    - 按风格权重抽取位置
 *    - 按招募范围抽取国籍
 *    - 计算 PA（天才走 90-99，普通走任务要求 60-90，正态分布）
 *    - 计算 CA（任务要求 30-50，受年龄影响）
 *    - 滚动隐藏属性（职业态度 / 野心 / 伤病倾向 / 适应力）
 *    - 生成隐藏标签（天才 / 早慧 / 大器晚成 / 高职业态度 / 慵懒）
 *    - 按位置 + 风格生成属性 JSON
 *
 * 异常保护（V0.2 §十五）：
 * - 单俱乐部每月最多 [YouthAcademyConfig.maxGeniusPerMonth] 名天才
 * - 普通球员 PA 受 normalPaMin/normalPaMax 限制（任务要求 60-90）
 * - CA 严格限制在 30-50（任务要求）
 *
 * 同步至 save_player_state：
 * - 青训球员生成后在 save_player_state 表插入对应记录
 * - 用于比赛 / 统计 / 阵容页统一查询
 * - playerId 字段回填至 youth_player.player_id
 *
 * @param databaseManager 三库管理入口
 * @param productionCalculator 7 因子产出质量计算器
 * @param positionWeightTable 风格位置权重表
 * @param config 青训学院配置
 */
class YouthPlayerGenerator(
    private val databaseManager: DatabaseManager,
    private val productionCalculator: ProductionQualityCalculator,
    private val positionWeightTable: PositionWeightTable,
    private val config: YouthAcademyConfig = YouthAcademyConfig.getDefault()
) {

    /** 月度生成入口（由 T07 月结调用）。 */
    suspend fun generateMonthly(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ): List<YouthPlayerEntity> = withContext(Dispatchers.IO) {
        val academy = databaseManager.youthAcademyStateDao().getByClub(saveId, clubId)
            ?: return@withContext emptyList()

        // 1. 检查本月是否已生成
        val monthKey = currentDate.format(YEAR_MONTH_FORMATTER)
        if (academy.lastProductionMonth == monthKey) return@withContext emptyList()

        // 2. 检查梯队容量（U18 + U21 都满则不生成）
        val u18Count = databaseManager.youthPlayerDao().countByTier(saveId, clubId, YouthTier.U18.name)
        val u21Count = databaseManager.youthPlayerDao().countByTier(saveId, clubId, YouthTier.U21.name)
        if (u18Count >= config.maxU18Capacity && u21Count >= config.maxU21Capacity) {
            return@withContext emptyList()
        }

        // 3. 计算 7 因子产出质量分数
        val productionScore = productionCalculator.calculate(academy, saveId, clubId)

        // 4. 概率决定是否本月生成（避免每月必生成）
        val generationProbability = calculateGenerationProbability(productionScore)
        if (Random.nextDouble() > generationProbability) {
            databaseManager.youthAcademyStateDao().updateLastProductionMonth(academy.academyId, monthKey)
            return@withContext emptyList()
        }

        // 5. 决定生成数量（1-2 名，任务要求赛季初 3-8 名 = 多月累积）
        val generateCount = calculateGenerationCount(productionScore, u18Count + u21Count)

        // 6. 逐个生成
        val newPlayers = mutableListOf<YouthPlayerEntity>()
        repeat(generateCount) {
            val player = generateSinglePlayer(saveId, clubId, academy, productionScore, currentDate)
            if (player != null) {
                val id = databaseManager.youthPlayerDao().insert(player)
                val withId = player.copy(youthPlayerId = id.toInt())

                // 同步至 save_player_state 表（用于比赛/统计/阵容页统一查询）
                val playerId = syncToSavePlayerState(withId)
                if (playerId > 0) {
                    databaseManager.youthPlayerDao().updatePlayerId(id.toInt(), playerId)
                }
                newPlayers.add(withId.copy(playerId = playerId))
            }
        }

        databaseManager.youthAcademyStateDao().updateLastProductionMonth(academy.academyId, monthKey)
        newPlayers
    }

    /**
     * 月度生成概率：score / 100 × 0.7，限制在 [min, max] 区间。
     */
    private fun calculateGenerationProbability(score: Double): Double {
        val baseProbability = (score / 100.0) * 0.7
        return baseProbability.coerceIn(config.minGenerationProbability, config.maxGenerationProbability)
    }

    /**
     * 生成数量：高分学院有几率生成 2 名。
     */
    private fun calculateGenerationCount(score: Double, currentTotal: Int): Int {
        return when {
            score >= config.doubleGenerationThresholdScore &&
                currentTotal < config.maxU18Capacity + config.maxU21Capacity - 2 &&
                Random.nextDouble() < config.doubleGenerationProbability -> 2
            else -> 1
        }
    }

    /** 生成单个青训球员。 */
    private suspend fun generateSinglePlayer(
        saveId: Int,
        clubId: Int,
        academy: YouthAcademyStateEntity,
        productionScore: Double,
        currentDate: LocalDate
    ): YouthPlayerEntity? {
        // 1. 决定梯队（年龄）
        val age = pickAge()
        val tier = if (age <= 17) YouthTier.U18 else YouthTier.U21

        // 2. 决定位置（按风格权重）
        val style = AcademyStyle.fromNameOrDefault(academy.academyStyle)
        val position = positionWeightTable.pickPosition(style)
        val altPositions = positionWeightTable.pickAlternativePositions(position)

        // 3. 决定国籍（受招募范围影响）
        val nationality = pickNationality(academy)

        // 4. 生成姓名（V1 简化：用 "青训#ID + 国籍" 兜底，避免依赖外部姓名库）
        val playerName = generatePlayerName(nationality)

        // 5. 计算 PA（天才走 90-99，普通走任务要求 60-90）
        val (initialPa, isGenius) = generatePotentialPa(productionScore, saveId, clubId, academy, currentDate)

        // 6. 计算 CA（任务要求 30-50，受年龄影响）
        val currentCa = generateInitialCa(age, initialPa)

        // 7. 生成隐藏属性
        val professionalism = rollAttribute(isGenius, weightedBias = 60, reverse = false)
        val ambition = rollAttribute(isGenius, weightedBias = 55, reverse = false)
        val injuryProneness = rollAttribute(isGenius, weightedBias = 30, reverse = true)
        val adaptability = rollAttribute(isGenius, weightedBias = 50, reverse = false)

        // 8. 生成隐藏标签
        val hiddenTags = generateHiddenTags(isGenius, professionalism, age)

        // 9. 生成属性 JSON（按位置 + 风格分配）
        val attributesJson = generateAttributesByPosition(position, currentCa, style)

        // 10. 决定状态（14-17 岁默认 YOUTH_CONTRACT，18+ 默认 YOUTH_CONTRACT 等待签职业合同）
        val status = YouthPlayerStatus.YOUTH_CONTRACT

        return YouthPlayerEntity(
            saveId = saveId,
            academyId = academy.academyId,
            clubId = clubId,
            playerId = 0, // 待 sync 后回填
            playerName = playerName,
            nationality = nationality,
            birthDate = currentDate.minusYears(age.toLong()).toString(),
            primaryPosition = position,
            alternativePositions = altPositions.joinToString(","),
            tier = tier.name,
            status = status.name,
            currentCa = currentCa,
            potentialPa = initialPa,
            initialPa = initialPa,
            professionalism = professionalism,
            ambition = ambition,
            injuryProneness = injuryProneness,
            adaptability = adaptability,
            isKeyProspect = 0,
            trainingFocus = "BALANCED",
            mentorPlayerId = null,
            contractType = "YOUTH",
            contractUntil = currentDate.plusYears(3).toString(),
            wage = config.defaultYouthWage,
            generatedDate = currentDate.toString(),
            attributesJson = attributesJson,
            hiddenTags = hiddenTags.joinToString(","),
            monthlyGrowthLogJson = "[]"
        )
    }

    /**
     * V0.2 §十五 异常保护：PA 生成 + 天才限制。
     * - 天才概率 = config.geniusProbability × (1 + production_score / 200)
     * - 天才 PA: 90-99
     * - 普通 PA: 任务要求 60-90，正态分布
     * - 单俱乐部每月最多 1 名天才（防小妖全部满潜）
     */
    private suspend fun generatePotentialPa(
        productionScore: Double,
        saveId: Int,
        clubId: Int,
        academy: YouthAcademyStateEntity,
        currentDate: LocalDate
    ): Pair<Int, Boolean> {
        // 1. 天才概率（5% 基础，高分学院略高）
        val geniusProbability = config.geniusProbability * (1 + productionScore / 200.0)
        var isGenius = Random.nextDouble() < geniusProbability

        // 2. 异常保护：单俱乐部每月最多 1 名天才
        if (isGenius) {
            val monthPrefix = currentDate.format(YEAR_MONTH_FORMATTER) + "%"
            val recentGeniusCount = databaseManager.youthPlayerDao()
                .countGeniusInMonth(saveId, clubId, monthPrefix)
            if (recentGeniusCount >= config.maxGeniusPerMonth) {
                isGenius = false
            }
        }

        // 3. 计算 PA
        val pa = if (isGenius) {
            // 天才球员 PA: 90-99
            Random.nextInt(config.geniusPaMin, config.geniusPaMax + 1)
        } else {
            // 普通球员 PA: 任务要求 60-90，正态分布
            val mean = (config.normalPaMin + config.normalPaMax) / 2.0
            normalRandomInt(
                min = config.normalPaMin,
                max = config.normalPaMax,
                mean = mean,
                stdDev = config.normalPaStdDev.toDouble()
            )
        }

        return Pair(pa, isGenius)
    }

    /**
     * 初始 CA：任务要求 30-50，年龄越小 CA 越低。
     * 14-15 岁：30-40
     * 16-17 岁：35-45
     * 18+ 岁：40-50
     * CA 不能超过 PA - 10。
     */
    private fun generateInitialCa(age: Int, pa: Int): Int {
        val ageBase = when (age) {
            14, 15 -> Random.nextInt(30, 41)
            16, 17 -> Random.nextInt(35, 46)
            else -> Random.nextInt(40, 51)
        }
        // CA 不能超过 PA - 10（确保有成长空间）
        return ageBase.coerceAtMost(pa - 10).coerceIn(config.initialCaMin, config.initialCaMax)
    }

    /** 年龄抽取：14-17 岁占 92%，18 岁占 8%。 */
    private fun pickAge(): Int {
        val roll = Random.nextDouble()
        return when {
            roll < 0.30 -> 14
            roll < 0.55 -> 15
            roll < 0.80 -> 16
            roll < 0.92 -> 17
            else -> 18
        }
    }

    /**
     * 国籍抽取：受招募范围影响。
     * - LOCAL: 90% 本国 + 10% 邻国
     * - REGIONAL: 70% 本国 + 30% 区域
     * - NATIONAL: 80% 本国 + 20% 全国
     * - INTERNATIONAL: 50% 本国 + 50% 全球
     *
     * V1 简化：本国用俱乐部所在国家，外国家随机从池中选。
     */
    private fun pickNationality(academy: YouthAcademyStateEntity): String {
        val range = RecruitmentRange.fromNameOrDefault(academy.recruitmentRange)
        val localRatio = when (range) {
            RecruitmentRange.LOCAL -> 0.90
            RecruitmentRange.REGIONAL -> 0.70
            RecruitmentRange.NATIONAL -> 0.80
            RecruitmentRange.INTERNATIONAL -> 0.50
        }
        return if (Random.nextDouble() < localRatio) {
            // V1 简化：本国用国家人才池配置中随机一个高产国家
            val nations = config.nationTalentPoolBonus.keys.toList()
            nations.randomOrNull() ?: "ENG"
        } else {
            // 外国家从全球池随机选
            val foreignPool = listOf("BRA", "ARG", "FRA", "ESP", "GER", "ENG", "ITA", "POR", "NED", "URU")
            foreignPool.random()
        }
    }

    /**
     * 隐藏属性滚动（正态分布）。
     *
     * @param isGenius 天才球员（职业态度偏高、伤病倾向偏低）
     * @param weightedBias 普通球员均值偏向
     * @param reverse 反向（用于 injury_proneness：越低越好）
     */
    private fun rollAttribute(
        isGenius: Boolean,
        weightedBias: Int,
        reverse: Boolean
    ): Int {
        val base = if (isGenius) {
            // 天才球员职业态度偏高（避免天才态度差导致浪费）
            if (!reverse) normalRandomInt(60, 90, mean = 75.0, stdDev = 10.0)
            else normalRandomInt(20, 60, mean = 40.0, stdDev = 12.0)
        } else {
            // 普通球员正态分布
            if (!reverse) normalRandomInt(30, 90, mean = weightedBias.toDouble(), stdDev = 15.0)
            else normalRandomInt(10, 80, mean = weightedBias.toDouble(), stdDev = 15.0)
        }
        return base.coerceIn(1, 100)
    }

    /** 生成隐藏标签（天才 / 早慧 / 大器晚成 / 高职业态度 / 慵懒）。 */
    private fun generateHiddenTags(
        isGenius: Boolean,
        professionalism: Int,
        age: Int
    ): List<String> {
        val tags = mutableListOf<String>()
        if (isGenius) tags.add("GENIUS")
        if (professionalism >= 80) tags.add("HIGH_PROFESSIONALISM")
        if (professionalism < 30) tags.add("LAZY")
        if (age <= 15 && isGenius) tags.add("EARLY_BLOOMER")
        if (age >= 18 && !isGenius) tags.add("LATE_BLOOMER")
        return tags
    }

    /**
     * 按位置 + 风格生成属性 JSON（V1 简化版，存储为 JSON 字符串）。
     *
     * 位置决定属性分配侧重：
     * - ST: shooting / finishing / pace 偏高
     * - LW/RW: pace / dribbling / crossing 偏高
     * - AM: passing / vision / dribbling 偏高
     * - CM: passing / stamina / vision 偏高
     * - DM: defending / tackling / stamina 偏高
     * - CB: defending / tackling / strength 偏高
     * - LB/RB: pace / stamina / crossing 偏高
     * - GK: goalkeeping / reflexes / positioning 偏高
     */
    private fun generateAttributesByPosition(
        position: String,
        currentCa: Int,
        style: AcademyStyle
    ): String {
        // 基础属性值（基于 CA，浮动 ±5）
        val baseValue = currentCa.coerceIn(1, 99)
        val attributes = mutableMapOf<String, Int>()

        // 通用属性
        attributes["stamina"] = (baseValue + Random.nextInt(-3, 4)).coerceIn(1, 99)
        attributes["pace"] = (baseValue + Random.nextInt(-5, 6)).coerceIn(1, 99)
        attributes["acceleration"] = (baseValue + Random.nextInt(-5, 6)).coerceIn(1, 99)
        attributes["strength"] = (baseValue + Random.nextInt(-5, 6)).coerceIn(1, 99)
        attributes["agility"] = (baseValue + Random.nextInt(-3, 4)).coerceIn(1, 99)
        attributes["balance"] = (baseValue + Random.nextInt(-3, 4)).coerceIn(1, 99)

        // 位置专项属性
        when (position) {
            "ST", "CF" -> {
                attributes["shooting"] = (baseValue + 5).coerceIn(1, 99)
                attributes["finishing"] = (baseValue + 6).coerceIn(1, 99)
                attributes["heading"] = (baseValue + 3).coerceIn(1, 99)
                attributes["dribbling"] = (baseValue - 2).coerceIn(1, 99)
            }
            "LW", "RW" -> {
                attributes["pace"] = (baseValue + 8).coerceIn(1, 99)
                attributes["dribbling"] = (baseValue + 6).coerceIn(1, 99)
                attributes["crossing"] = (baseValue + 4).coerceIn(1, 99)
                attributes["shooting"] = (baseValue - 2).coerceIn(1, 99)
            }
            "AM" -> {
                attributes["passing"] = (baseValue + 6).coerceIn(1, 99)
                attributes["vision"] = (baseValue + 7).coerceIn(1, 99)
                attributes["dribbling"] = (baseValue + 4).coerceIn(1, 99)
                attributes["shooting"] = (baseValue + 2).coerceIn(1, 99)
            }
            "CM" -> {
                attributes["passing"] = (baseValue + 5).coerceIn(1, 99)
                attributes["vision"] = (baseValue + 5).coerceIn(1, 99)
                attributes["stamina"] = (baseValue + 4).coerceIn(1, 99)
                attributes["dribbling"] = (baseValue + 2).coerceIn(1, 99)
            }
            "DM" -> {
                attributes["defending"] = (baseValue + 6).coerceIn(1, 99)
                attributes["tackling"] = (baseValue + 6).coerceIn(1, 99)
                attributes["stamina"] = (baseValue + 5).coerceIn(1, 99)
                attributes["passing"] = (baseValue + 2).coerceIn(1, 99)
            }
            "CB" -> {
                attributes["defending"] = (baseValue + 7).coerceIn(1, 99)
                attributes["tackling"] = (baseValue + 7).coerceIn(1, 99)
                attributes["heading"] = (baseValue + 6).coerceIn(1, 99)
                attributes["strength"] = (baseValue + 4).coerceIn(1, 99)
            }
            "LB", "RB", "LWB", "RWB" -> {
                attributes["pace"] = (baseValue + 5).coerceIn(1, 99)
                attributes["stamina"] = (baseValue + 6).coerceIn(1, 99)
                attributes["crossing"] = (baseValue + 4).coerceIn(1, 99)
                attributes["defending"] = (baseValue + 2).coerceIn(1, 99)
            }
            "GK" -> {
                attributes["goalkeeping"] = (baseValue + 8).coerceIn(1, 99)
                attributes["reflexes"] = (baseValue + 7).coerceIn(1, 99)
                attributes["positioning"] = (baseValue + 6).coerceIn(1, 99)
                attributes["handling"] = (baseValue + 5).coerceIn(1, 99)
            }
        }

        // 风格加成
        when (style) {
            AcademyStyle.TECHNICAL, AcademyStyle.MIDFIELD_ORGANIZE -> {
                attributes["dribbling"] = (attributes["dribbling"] ?: 50) + 3
                attributes["passing"] = (attributes["passing"] ?: 50) + 3
            }
            AcademyStyle.POWER -> {
                attributes["strength"] = (attributes["strength"] ?: 50) + 4
                attributes["stamina"] = (attributes["stamina"] ?: 50) + 2
            }
            AcademyStyle.SPEED -> {
                attributes["pace"] = (attributes["pace"] ?: 50) + 4
                attributes["acceleration"] = (attributes["acceleration"] ?: 50) + 4
            }
            AcademyStyle.DEFENSIVE -> {
                attributes["defending"] = (attributes["defending"] ?: 50) + 3
                attributes["tackling"] = (attributes["tackling"] ?: 50) + 3
            }
            AcademyStyle.WING -> {
                attributes["crossing"] = (attributes["crossing"] ?: 50) + 3
                attributes["dribbling"] = (attributes["dribbling"] ?: 50) + 2
            }
            else -> { /* ALL_ROUND / FREE_STYLE: 不额外加成 */
            }
        }

        // 限制到 1-99
        attributes.keys.forEach { key ->
            attributes[key] = attributes[key]!!.coerceIn(1, 99)
        }

        // 序列化为简化 JSON（不依赖 Gson，避免引入新依赖）
        return buildJson(attributes)
    }

    /** 简易 JSON 序列化（不引入 Gson 依赖）。 */
    private fun buildJson(attributes: Map<String, Int>): String {
        if (attributes.isEmpty()) return "{}"
        return attributes.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ","
        ) { (k, v) -> "\"$k\":$v" }
    }

    /** V1 简化姓名生成：用 "青训#编号 + 国籍首字母" 兜底。 */
    private var playerNameCounter = 0
    private fun generatePlayerName(nationality: String): String {
        playerNameCounter += 1
        val prefix = nationality.firstOrNull() ?: "X"
        return "青训#$prefix${String.format("%03d", playerNameCounter)}"
    }

    /**
     * 同步青训球员至 save_player_state 表（用于比赛/统计/阵容页统一查询）。
     *
     * - 复用现有 SavePlayerStateEntity，避免重复造轮子
     * - player_id 用 youth_player_id 作为关联键
     * - current_club_id = 青训俱乐部 ID
     * - squad_role = "prospect"（青训球员）
     */
    private suspend fun syncToSavePlayerState(player: YouthPlayerEntity): Int {
        val savePlayerState = SavePlayerStateEntity(
            saveId = player.saveId,
            playerId = player.youthPlayerId, // 用 youth_player_id 作为关联
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
        val id = databaseManager.savePlayerStateDao().insert(savePlayerState)
        return id.toInt()
    }

    companion object {
        /** 年月格式化器（yyyy-MM）。 */
        val YEAR_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        /**
         * 正态分布随机整数（Box-Muller 变换）。
         *
         * @param min 最小值
         * @param max 最大值
         * @param mean 均值
         * @param stdDev 标准差
         * @return 限制在 [min, max] 区间的正态分布随机数
         */
        fun normalRandomInt(min: Int, max: Int, mean: Double, stdDev: Double): Int {
            val u1 = Random.nextDouble()
            val u2 = Random.nextDouble()
            val z = kotlin.math.sqrt(-2 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
            val value = (mean + z * stdDev).toInt()
            return value.coerceIn(min, max)
        }
    }
}
