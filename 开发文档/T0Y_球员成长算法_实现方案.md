# T0Y 球员成长算法 - 详细实现方案

> 任务编号：T0Y
> 周期：W13-W15（3 周）
> 优先级：P0（A 轨核心，依赖 T02 比赛引擎）
> 依据：V0.2 `08_球员成长_训练_伤病细化算法.md`
> 铁律：月度成长公式 10 因子 + 年龄表 7 档 + 重大伤病持续影响 + 潜力兑现率
> 定位：决定球员发展是否合理，影响长程模拟质量

---

## 一、整体架构

### 1. 成长流水线

```
PlayerGrowthService（入口）
    │
    ├── MonthlyGrowthCalculator          # 月度成长结算
    │   └── 10 因子公式
    │
    ├── AgeBasedGrowthTable              # 年龄基础成长表
    │   └── 7 档（14-34+）
    │
    ├── InjuryImpactCalculator           # 伤病影响
    │   └── ACL/重伤持续惩罚
    │
    ├── PotentialRealizationCalculator   # 潜力兑现率
    │   └── 8 因子
    │
    ├── TrainingSpecializationHandler    # 训练专项
    │   └── 射门/传球/体能等
    │
    ├── MentorInfluenceHandler           # 导师影响
    │   └── 老带新
    │
    └── PositionConversionHandler        # 位置改造
```

### 2. 包结构

```
com.greenDynasty.growth
├── api/
│   ├── PlayerGrowthService.kt          # 对外入口
│   └── GrowthResult.kt
├── monthly/
│   ├── MonthlyGrowthCalculator.kt      # 10 因子公式
│   └── GrowthFactor.kt                 # 因子枚举
├── age/
│   └── AgeBasedGrowthTable.kt          # 7 档年龄表
├── injury/
│   └── InjuryImpactCalculator.kt       # 伤病持续影响
├── potential/
│   └── PotentialRealizationCalculator.kt  # 潜力兑现率
├── training/
│   ├── TrainingSpecializationHandler.kt
│   └── TrainingProgram.kt
├── mentor/
│   └── MentorInfluenceHandler.kt
├── position/
│   └── PositionConversionHandler.kt
└── config/
    └── GrowthConfig.kt
```

---

## 二、核心数据类

```kotlin
// api/GrowthResult.kt
data class GrowthResult(
    val playerId: Int,
    val caBefore: Int,
    val caAfter: Int,
    val caDelta: Int,
    val attributeChanges: Map<String, Int>,   // 属性变化
    val factors: GrowthFactorBreakdown,        // 各因子贡献
    val realizationScore: Double,              // 兑现率
    val notes: List<String>                    // 成长日志
)

data class GrowthFactorBreakdown(
    val ageBase: Double,                       // 年龄基础
    val potentialGap: Double,                  // 潜力差距
    val professionalAttitude: Double,          // 职业态度
    val playingTime: Double,                   // 出场时间
    val trainingQuality: Double,               // 训练质量
    val coachingLevel: Double,                 // 教练水平
    val leagueIntensity: Double,               // 联赛强度
    val morale: Double,                        // 士气
    val tacticalFit: Double,                   // 战术适配
    val injuryImpact: Double                   // 伤病影响
)
```

---

## 三、月度成长公式（V0.2 §二）

```kotlin
// monthly/MonthlyGrowthCalculator.kt
class MonthlyGrowthCalculator(
    private val ageTable: AgeBasedGrowthTable,
    private val injuryCalculator: InjuryImpactCalculator,
    private val potentialCalculator: PotentialRealizationCalculator,
    private val config: GrowthConfig
) {

    /**
     * V0.2 §二 月度成长公式（10 因子）
     * monthly_growth =
     *   age_base * 0.20
     * + potential_gap * 0.15
     * + professional_attitude * 0.15
     * + playing_time * 0.15
     * + training_quality * 0.10
     * + coaching_level * 0.08
     * + league_intensity * 0.07
     * + morale * 0.05
     * + tactical_fit * 0.03
     * + injury_impact * 0.02
     */
    fun calculate(
        player: SavePlayerStateWithAttributes,
        monthlyStats: MonthlyPlayerStats,
        clubTrainingQuality: Double,
        clubCoachLevel: Int,
        leagueIntensity: Double,
        tacticalFitScore: Double
    ): GrowthResult {
        val caBefore = player.currentCa
        val age = calculateAge(player.birthDate)

        // 1. 年龄基础成长（V0.2 §三）
        val ageBase = ageTable.getGrowthFactor(age, player.potentialPa)

        // 2. 潜力差距
        val potentialGap = calculatePotentialGap(player.currentCa, player.potentialPa)

        // 3. 职业态度
        val professionalAttitude = player.professionalAttitude / 100.0

        // 4. 出场时间（V0.2 §四 minutes_ratio）
        val playingTime = calculatePlayingTimeFactor(monthlyStats.minutesPlayed, monthlyStats.maxPossibleMinutes)

        // 5. 训练质量
        val trainingQuality = clubTrainingQuality

        // 6. 教练水平
        val coachingLevel = clubCoachLevel / 100.0

        // 7. 联赛强度
        val leagueIntensityFactor = leagueIntensity

        // 8. 士气
        val moraleFactor = player.morale / 100.0

        // 9. 战术适配
        val tacticalFit = tacticalFitScore

        // 10. 伤病影响（V0.2 §六）
        val injuryImpact = injuryCalculator.calculateImpact(player)

        // 加权汇总
        val w = config.growthWeights
        val monthlyGrowth = (
            ageBase * w.ageBase +
            potentialGap * w.potentialGap +
            professionalAttitude * w.professionalAttitude +
            playingTime * w.playingTime +
            trainingQuality * w.trainingQuality +
            coachingLevel * w.coachingLevel +
            leagueIntensityFactor * w.leagueIntensity +
            moraleFactor * w.morale +
            tacticalFit * w.tacticalFit +
            injuryImpact * w.injuryImpact
        )

        // 计算实际 CA 变化
        val caDelta = calculateCaDelta(monthlyGrowth, player, age)
        val caAfter = (caBefore + caDelta).coerceIn(1, player.potentialPa)

        // 计算属性变化
        val attributeChanges = calculateAttributeChanges(player, monthlyGrowth, caDelta)

        // 潜力兑现率
        val realizationScore = potentialCalculator.calculate(player, monthlyStats, clubTrainingQuality)

        return GrowthResult(
            playerId = player.playerId,
            caBefore = caBefore,
            caAfter = caAfter,
            caDelta = caDelta,
            attributeChanges = attributeChanges,
            factors = GrowthFactorBreakdown(
                ageBase, potentialGap, professionalAttitude, playingTime,
                trainingQuality, coachingLevel, leagueIntensityFactor,
                moraleFactor, tacticalFit, injuryImpact
            ),
            realizationScore = realizationScore,
            notes = buildGrowthNotes(player, monthlyGrowth, caDelta)
        )
    }

    private fun calculatePotentialGap(currentCa: Int, potentialPa: Int): Double {
        val gap = potentialPa - currentCa
        // 潜力差距越大，成长空间越大（但有上限）
        return (gap / 30.0).coerceIn(0.0, 1.0)
    }

    /**
     * V0.2 §四 出场时间因子
     * minutes_ratio = actual_minutes / expected_minutes
     */
    private fun calculatePlayingTimeFactor(actualMinutes: Int, maxPossibleMinutes: Int): Double {
        if (maxPossibleMinutes == 0) return 0.0
        val ratio = actualMinutes.toDouble() / maxPossibleMinutes
        return when {
            ratio >= 0.8 -> 1.0      // 主力：满分
            ratio >= 0.5 -> 0.7      // 轮换
            ratio >= 0.2 -> 0.4      // 替补
            ratio > 0 -> 0.15        // 偶尔出场
            else -> 0.0              // 无出场（成长停滞）
        }
    }

    private fun calculateCaDelta(monthlyGrowth: Double, player: SavePlayerStateWithAttributes, age: Int): Int {
        // 成长率转 CA 变化（每月）
        // 年轻球员成长快，老球员可能负成长
        val baseGrowth = (monthlyGrowth * config.caGrowthMultiplier).toInt()

        // 接近潜力时成长放缓
        val gapToPotential = player.potentialPa - player.currentCa
        val potentialSlowdown = when {
            gapToPotential <= 0 -> -1  // 已达潜力，开始下滑
            gapToPotential <= 3 -> baseGrowth / 3
            gapToPotential <= 5 -> baseGrowth / 2
            else -> baseGrowth
        }

        return potentialSlowdown
    }
}
```

---

## 四、年龄基础成长表（V0.2 §三）

```kotlin
// age/AgeBasedGrowthTable.kt
class AgeBasedGrowthTable(private val config: GrowthConfig) {

    /**
     * V0.2 §三 年龄基础成长表（7 档）
     * | 14-16 | 青少年期，成长最快 |
     * | 17-19 | 青年上升期 |
     * | 20-22 | 成长期，潜力兑现关键期 |
     * | 23-27 | 巅峰期，缓慢提升 |
     * | 28-30 | 巅峰维持期，基本稳定 |
     * | 31-33 | 下滑期 |
     * | 34+   | 快速下滑期 |
     */
    fun getGrowthFactor(age: Int, potentialPa: Int): Double {
        val baseFactor = when (age) {
            in 14..16 -> 1.50    // 青少年期，成长最快
            in 17..19 -> 1.30    // 青年上升期
            in 20..22 -> 1.10    // 成长期
            in 23..27 -> 0.60    // 巅峰期，缓慢提升
            in 28..30 -> 0.20    // 巅峰维持期
            in 31..33 -> -0.30   // 下滑期
            else -> -0.60        // 34+ 快速下滑
        }

        // 潜力高的球员成长更持久
        val potentialModifier = when {
            potentialPa >= 90 && age <= 24 -> 1.2
            potentialPa >= 80 && age <= 22 -> 1.1
            else -> 1.0
        }

        return baseFactor * potentialModifier
    }

    /**
     * 获取退役年龄（V0.2 §七）
     */
    fun getRetireAge(player: SavePlayerStateWithAttributes): Int {
        val baseRetireAge = when (player.primaryPosition) {
            Position.GK -> 38
            Position.CB -> 35
            Position.ST, Position.CF -> 34
            Position.LW, Position.RW -> 33  // 速度型球员退役早
            else -> 35
        }

        // 伤病频繁的球员退役更早
        val injuryAdjustment = if (player.injuryProneness > 70) -2 else 0

        // 职业态度好的球员退役更晚
        val attitudeAdjustment = if (player.professionalAttitude > 80) +1 else 0

        return baseRetireAge + injuryAdjustment + attitudeAdjustment
    }
}
```

---

## 五、伤病影响（V0.2 §六）

```kotlin
// injury/InjuryImpactCalculator.kt
class InjuryImpactCalculator(private val config: GrowthConfig) {

    /**
     * V0.2 §六 伤病影响
     * - ACL 等重伤：降身体属性 + 降 PA + 升伤病倾向
     * - 频繁小伤：成长停滞
     * - 长期伤停：CA 下降
     */
    fun calculateImpact(player: SavePlayerStateWithAttributes): Double {
        var impact = 0.0  // 0-1，越低越差

        // 1. 当前伤病状态
        if (player.injuryStatus != "healthy") {
            impact -= 0.3  // 伤病中成长停滞
        }

        // 2. 近期伤病频率
        val recentInjuries = player.recentInjuryCount  // 近 6 个月伤病次数
        impact -= recentInjuries * 0.1

        // 3. 伤病倾向属性
        if (player.injuryProneness > 70) {
            impact -= 0.2
        }

        // 4. 重大伤病历史（ACL 等）
        if (player.hasMajorInjuryHistory) {
            impact -= 0.4  // 重伤持续影响
        }

        return impact.coerceIn(-1.0, 1.0)
    }

    /**
     * V0.2 §六 重大伤病处理
     * ACL：降身体属性 + 降 PA + 升伤病倾向
     */
    fun applyMajorInjury(
        player: SavePlayerStateWithAttributes,
        injuryType: MajorInjuryType
    ): MajorInjuryImpact {
        return when (injuryType) {
            MajorInjuryType.ACL -> MajorInjuryImpact(
                paceDelta = -5,
                accelerationDelta = -5,
                paDelta = -3,
                injuryPronenessDelta = +5,
                recoveryMonths = 9
            )
            MajorInjuryType.ACHILLES -> MajorInjuryImpact(
                paceDelta = -8,
                accelerationDelta = -6,
                paDelta = -5,
                injuryPronenessDelta = +8,
                recoveryMonths = 12
            )
            MajorInjuryType.MENISCUS -> MajorInjuryImpact(
                paceDelta = -3,
                paDelta = -2,
                injuryPronenessDelta = +3,
                recoveryMonths = 4
            )
            MajorInjuryType.FRACTURE -> MajorInjuryImpact(
                paDelta = -1,
                injuryPronenessDelta = +2,
                recoveryMonths = 3
            )
        }
    }
}

enum class MajorInjuryType {
    ACL,        // 前交叉韧带（最严重）
    ACHILLES,   // 跟腱
    MENISCUS,   // 半月板
    FRACTURE    // 骨折
}

data class MajorInjuryImpact(
    val paceDelta: Int = 0,
    val accelerationDelta: Int = 0,
    val paDelta: Int = 0,
    val injuryPronenessDelta: Int = 0,
    val recoveryMonths: Int
)
```

---

## 六、潜力兑现率（V0.2 §五）

```kotlin
// potential/PotentialRealizationCalculator.kt
class PotentialRealizationCalculator(private val config: GrowthConfig) {

    /**
     * V0.2 §五 潜力兑现率（8 因子）
     * realization_score =
     *   professional_attitude * 0.25
     * + playing_time * 0.20
     * + training_quality * 0.15
     * + coaching_level * 0.10
     * + league_intensity * 0.10
     * + morale * 0.08
     * + injury_free * 0.07
     * + tactical_fit * 0.05
     */
    fun calculate(
        player: SavePlayerStateWithAttributes,
        stats: MonthlyPlayerStats,
        trainingQuality: Double
    ): Double {
        val professionalAttitude = player.professionalAttitude / 100.0
        val playingTime = calculatePlayingTimeRatio(stats)
        val trainingQualityScore = trainingQuality
        val coachingLevel = player.coachingLevel / 100.0
        val leagueIntensity = player.leagueIntensity
        val morale = player.morale / 100.0
        val injuryFree = if (player.injuryStatus == "healthy") 1.0 else 0.3
        val tacticalFit = player.tacticalFitScore

        val w = config.realizationWeights
        val score = (
            professionalAttitude * w.professionalAttitude +
            playingTime * w.playingTime +
            trainingQualityScore * w.trainingQuality +
            coachingLevel * w.coachingLevel +
            leagueIntensity * w.leagueIntensity +
            morale * w.morale +
            injuryFree * w.injuryFree +
            tacticalFit * w.tacticalFit
        )

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 根据兑现率调整实际 PA
     * V0.2 §五："兑现率低的球员实际 PA 会低于初始 PA"
     */
    fun adjustPotentialPa(player: SavePlayerStateWithAttributes, realizationScore: Double): Int {
        val initialPa = player.initialPotentialPa
        val adjustedPa = (initialPa * (0.7 + realizationScore * 0.3)).toInt()
        // 兑现率 1.0 → 100% PA
        // 兑现率 0.5 → 85% PA
        // 兑现率 0.0 → 70% PA
        return adjustedPa.coerceAtLeast(player.currentCa)
    }
}
```

---

## 七、训练专项（V0.2 §八）

```kotlin
// training/TrainingSpecializationHandler.kt
class TrainingSpecializationHandler(private val config: GrowthConfig) {

    /**
     * V0.2 §八 训练专项
     * 球员可专注训练某类属性：
     * - 射门专项：finishing/shotPower/longShots
     * - 传球专项：passing/technique/vision
     * - 体能专项：pace/stamina/strength
     * - 防守专项：tackling/marking/interceptions
     */
    fun applySpecialization(
        player: SavePlayerStateWithAttributes,
        program: TrainingProgram,
        trainingQuality: Double
    ): Map<String, Int> {
        val changes = mutableMapOf<String, Int>()
        val growthFactor = trainingQuality * config.specializationGrowthRate  // 默认 0.5

        when (program.focus) {
            TrainingFocus.SHOOTING -> {
                changes["finishing"] = (growthFactor * 1.5).toInt()
                changes["shotPower"] = (growthFactor * 1.0).toInt()
                changes["longShots"] = (growthFactor * 0.8).toInt()
            }
            TrainingFocus.PASSING -> {
                changes["passing"] = (growthFactor * 1.5).toInt()
                changes["technique"] = (growthFactor * 1.2).toInt()
                changes["vision"] = (growthFactor * 1.0).toInt()
            }
            TrainingFocus.FITNESS -> {
                changes["pace"] = (growthFactor * 1.0).toInt()
                changes["stamina"] = (growthFactor * 1.5).toInt()
                changes["strength"] = (growthFactor * 1.0).toInt()
            }
            TrainingFocus.DEFENDING -> {
                changes["tackling"] = (growthFactor * 1.5).toInt()
                changes["marking"] = (growthFactor * 1.3).toInt()
                changes["interceptions"] = (growthFactor * 1.0).toInt()
            }
            TrainingFocus.BALANCED -> {
                // 均衡训练，小幅提升所有属性
                changes["finishing"] = (growthFactor * 0.3).toInt()
                changes["passing"] = (growthFactor * 0.3).toInt()
                changes["tackling"] = (growthFactor * 0.3).toInt()
            }
        }

        return changes
    }
}

data class TrainingProgram(
    val focus: TrainingFocus,
    val intensity: Int  // 1-10
)

enum class TrainingFocus {
    SHOOTING,       // 射门专项
    PASSING,        // 传球专项
    FITNESS,        // 体能专项
    DEFENDING,      // 防守专项
    BALANCED        // 均衡训练
}
```

---

## 八、导师影响（V0.2 §九）

```kotlin
// mentor/MentorInfluenceHandler.kt
class MentorInfluenceHandler {

    /**
     * V0.2 §九 导师影响
     * 老将带新人，影响：
     * - 成长加成（mentor_effect 4 因子）
     * - 可降属性也可升属性
     */
    fun calculateMentorEffect(
        mentee: SavePlayerStateWithAttributes,
        mentor: SavePlayerStateWithAttributes
    ): MentorEffect {
        // V0.2 §九 mentor_effect 4 因子
        val mentorAbility = mentor.currentCa / 100.0
        val mentorLeadership = mentor.leadership / 100.0
        val mentorProfessionalAttitude = mentor.professionalAttitude / 100.0
        val ageGap = calculateAgeGap(mentor, mentee)

        // 导师效果 = 4 因子加权
        val mentorEffect = (
            mentorAbility * 0.30 +
            mentorLeadership * 0.30 +
            mentorProfessionalAttitude * 0.25 +
            ageGap * 0.15
        )

        // 判断正向还是负向影响
        val isPositive = mentor.professionalAttitude > 60 && mentor.leadership > 50

        return MentorEffect(
            growthBonus = if (isPositive) mentorEffect * 0.1 else 0.0,  // 最高 +10%
            attitudeDelta = if (isPositive) (mentorEffect * 5).toInt() else -(mentorEffect * 3).toInt(),
            isPositive = isPositive
        )
    }
}

data class MentorEffect(
    val growthBonus: Double,     // 成长加成 0-0.1
    val attitudeDelta: Int,      // 职业态度变化
    val isPositive: Boolean
)
```

---

## 九、位置改造（V0.2 §十）

```kotlin
// position/PositionConversionHandler.kt
class PositionConversionHandler {

    /**
     * V0.2 §十 位置改造
     * 球员可改造为新位置，需要时间适应
     */
    fun calculateConversionProgress(
        player: SavePlayerStateWithAttributes,
        targetPosition: Position,
        trainingMonths: Int
    ): PositionConversionProgress {
        val currentPosition = player.primaryPosition
        val compatibility = calculatePositionCompatibility(currentPosition, targetPosition)

        // 改造进度（每月提升）
        val monthlyProgress = compatibility * 10  // 0-10 per month
        val totalProgress = (monthlyProgress * trainingMonths).coerceAtMost(100)

        // 改造期间 CA 临时下降（适应期）
        val temporaryCaPenalty = when {
            totalProgress < 30 -> -5   // 初期不适应
            totalProgress < 70 -> -2   // 中期部分适应
            else -> 0                  // 完全适应
        }

        return PositionConversionProgress(
            targetPosition = targetPosition,
            progress = totalProgress,
            temporaryCaPenalty = temporaryCaPenalty,
            isCompleted = totalProgress >= 100
        )
    }

    /**
     * 位置兼容性（0-1）
     */
    private fun calculatePositionCompatibility(from: Position, to: Position): Double {
        // 同类位置兼容性高
        return when {
            from == to -> 1.0
            // 边路位置互转
            from in listOf(Position.LW, Position.RW) && to in listOf(Position.LW, Position.RW) -> 0.8
            // 中路到边路
            from == Position.CM && to in listOf(Position.LW, Position.RW) -> 0.6
            // 后卫互转
            from in listOf(Position.CB, Position.LB, Position.RB) && to in listOf(Position.CB, Position.LB, Position.RB) -> 0.7
            // 中场到后卫
            from == Position.DM && to == Position.CB -> 0.7
            // 其他
            else -> 0.4
        }
    }
}

data class PositionConversionProgress(
    val targetPosition: Position,
    val progress: Int,           // 0-100
    val temporaryCaPenalty: Int,
    val isCompleted: Boolean
)
```

---

## 十、入口服务

```kotlin
// api/PlayerGrowthService.kt
class PlayerGrowthService(
    private val monthlyCalculator: MonthlyGrowthCalculator,
    private val injuryCalculator: InjuryImpactCalculator,
    private val specializationHandler: TrainingSpecializationHandler,
    private val mentorHandler: MentorInfluenceHandler,
    private val positionHandler: PositionConversionHandler,
    private val saveDb: SaveDatabase
) {

    /**
     * 月度成长结算（供 T07 MonthlyTaskExecutor 调用）
     */
    suspend fun monthlyGrowthSettlement(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ) {
        val players = saveDb.savePlayerStateDao().getByClub(saveId, clubId)
        val club = saveDb.saveClubStateDao().getByClub(saveId, clubId)!!
        val trainingQuality = calculateClubTrainingQuality(club)

        for (player in players) {
            if (player.careerStatus != "active") continue

            val monthlyStats = getMonthlyStats(saveId, player.playerId, currentDate)
            val tacticalFit = calculateTacticalFit(player, club)

            val result = monthlyCalculator.calculate(
                player, monthlyStats, trainingQuality, club.coachLevel,
                club.leagueIntensity, tacticalFit
            )

            // 应用成长
            applyGrowth(player, result)

            // 记录成长历史
            saveGrowthHistory(player, result, currentDate)
        }
    }

    /**
     * 应用成长（V0.2 §十一 成长异常保护）
     */
    private suspend fun applyGrowth(player: SavePlayerStateWithAttributes, result: GrowthResult) {
        // 异常保护：防止小妖全部满潜
        val protectedDelta = applyAnomalyProtection(result, player)

        saveDb.savePlayerStateDao().updateCa(player.id, protectedDelta.caAfter)

        // 更新属性
        protectedDelta.attributeChanges.forEach { (attr, delta) ->
            saveDb.savePlayerStateDao().updateAttribute(player.id, attr, delta)
        }
    }

    /**
     * V0.2 §十一 成长异常保护
     * 防止小妖全部满潜、防止老将不衰退
     */
    private fun applyAnomalyProtection(result: GrowthResult, player: SavePlayerStateWithAttributes): GrowthResult {
        val age = calculateAge(player.birthDate)

        // 1. 防止小妖成长过快
        if (age <= 19 && result.caDelta > 3) {
            return result.copy(caDelta = 3)  // 单月最多 +3
        }

        // 2. 防止老将不衰退
        if (age >= 31 && result.caDelta > 0) {
            return result.copy(caDelta = 0)  // 31+ 不再成长
        }

        // 3. 防止已达潜力的球员继续成长
        if (player.currentCa >= player.potentialPa && result.caDelta > 0) {
            return result.copy(caDelta = 0)
        }

        return result
    }

    /**
     * 蝴蝶效应调用：应用成长路径修正
     */
    fun applyCareerPathModifier(playerId: Int, growthModifier: Double, impactStrength: Double) {
        val player = saveDb.savePlayerStateDao().getById(playerId) ?: return
        // 调整职业态度或训练质量因子
        val adjustedAttitude = (player.professionalAttitude * growthModifier).toInt().coerceIn(1, 100)
        saveDb.savePlayerStateDao().updateAttitude(playerId, adjustedAttitude)
    }
}
```

---

## 十一、配置化参数

### growth_config.json

```json
{
  "growth_weights": {
    "ageBase": 0.20,
    "potentialGap": 0.15,
    "professionalAttitude": 0.15,
    "playingTime": 0.15,
    "trainingQuality": 0.10,
    "coachingLevel": 0.08,
    "leagueIntensity": 0.07,
    "morale": 0.05,
    "tacticalFit": 0.03,
    "injuryImpact": 0.02
  },
  "age_table": {
    "14-16": 1.50,
    "17-19": 1.30,
    "20-22": 1.10,
    "23-27": 0.60,
    "28-30": 0.20,
    "31-33": -0.30,
    "34+": -0.60
  },
  "realization_weights": {
    "professionalAttitude": 0.25,
    "playingTime": 0.20,
    "trainingQuality": 0.15,
    "coachingLevel": 0.10,
    "leagueIntensity": 0.10,
    "morale": 0.08,
    "injuryFree": 0.07,
    "tacticalFit": 0.05
  },
  "ca_growth": {
    "ca_growth_multiplier": 1.0,
    "max_monthly_growth_age_under_19": 3,
    "potential_slowdown_threshold_3": 3,
    "potential_slowdown_threshold_5": 5
  },
  "specialization": {
    "specialization_growth_rate": 0.5
  },
  "anomaly_protection": {
    "max_monthly_growth_young": 3,
    "no_growth_age_threshold": 31,
    "injury_proneness_threshold": 70
  },
  "major_injury": {
    "ACL": { "pace_delta": -5, "pa_delta": -3, "injury_proneness_delta": 5, "recovery_months": 9 },
    "ACHILLES": { "pace_delta": -8, "pa_delta": -5, "injury_proneness_delta": 8, "recovery_months": 12 },
    "MENISCUS": { "pace_delta": -3, "pa_delta": -2, "injury_proneness_delta": 3, "recovery_months": 4 },
    "FRACTURE": { "pa_delta": -1, "injury_proneness_delta": 2, "recovery_months": 3 }
  },
  "retire_age": {
    "GK": 38, "CB": 35, "ST": 34, "LW": 33, "RW": 33,
    "injury_adjustment": -2,
    "attitude_adjustment": 1
  }
}
```

---

## 十二、3 周实现计划（W13-W15）

### W13：月度成长公式 + 年龄表

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | GrowthResult + GrowthFactorBreakdown 数据类 | 数据结构完整 |
| D2 | AgeBasedGrowthTable 7 档 + 退役年龄 | 年龄表可用 |
| D3 | MonthlyGrowthCalculator 10 因子公式 | 核心公式完成 |
| D4 | calculatePlayingTimeFactor + calculatePotentialGap | 关键因子实现 |
| D5 | 单测：不同年龄球员成长曲线合理 | 成长曲线验收 |

**W13 验收**：17 岁小妖成长快，33 岁老将下滑。

### W14：伤病影响 + 潜力兑现率

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | InjuryImpactCalculator 基础影响 | 伤病影响成长 |
| D2 | applyMajorInjury（ACL/跟腱/半月板/骨折） | 重伤持续惩罚 |
| D3 | PotentialRealizationCalculator 8 因子 | 兑现率可计算 |
| D4 | adjustPotentialPa（兑现率调整 PA） | 低兑现率降 PA |
| D5 | 单测：ACL 后 PA 下降，低职业态度降兑现率 | 伤病/潜力验收 |

**W14 验收**：ACL 重伤后 PA 降 3，职业态度 30 的球员兑现率 < 0.5。

### W15：训练专项 + 导师 + 位置改造 + 集成

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | TrainingSpecializationHandler 5 种专项 | 训练可定制 |
| D2 | MentorInfluenceHandler 4 因子 | 老带新生效 |
| D3 | PositionConversionHandler 兼容性 | 位置改造可用 |
| D4 | PlayerGrowthService 集成 + 异常保护 | 全流程跑通 |
| D5 | 集成测试：1 赛季成长模拟 | 成长系统验收 |

**W15 验收**：1 赛季成长合理，小妖不会全部满潜，老将衰退。

---

## 十三、关键风险与应对

### 1. 小妖全部满潜

- **表现**：年轻球员普遍达到 PA 上限
- **应对**：
  - 异常保护：单月成长 ≤3
  - 兑现率调整 PA（低职业态度降 PA）
  - 接近潜力时成长放缓
- **红线**：单月成长不超过 3

### 2. 老将不衰退

- **表现**：33+ 球员仍保持高 CA
- **应对**：
  - 年龄表 31+ 强制 0 成长
  - 34+ 每月 -0.6 成长
  - 退役年龄强制退役

### 3. 重伤影响不足

- **表现**：ACL 后球员仍正常成长
- **应对**：
  - ACL 降 PA 3 点 + 降速度 5 点
  - 重伤后 6 个月成长停滞
  - 伤病倾向提升影响后续

---

## 十四、与其他任务的衔接

| 上游任务 | 依赖点 | 衔接方式 |
|----------|--------|----------|
| T02 比赛引擎 | 出场时间统计 | MonthlyPlayerStats.minutesPlayed |
| T08 伤病 | 伤病状态 | InjuryImpactCalculator 读取伤病 |
| T03 存档 | 球员状态读写 | save_player_state 读写 |

| 下游任务 | 依赖点 | 衔接方式 |
|----------|--------|----------|
| T07 每月任务 | 月度成长结算 | monthlyGrowthSettlement |
| T09 成长月结 | 成长记录 | GrowthResult 持久化 |
| T20 蝴蝶效应 | 成长路径修正 | applyCareerPathModifier |
| Gate 1 | 成长曲线合理 | 10000 场后年轻球员成长 |

---

## 十五、V1 范围明确（不做的）

**V1 做**：
- 月度成长 10 因子公式
- 年龄表 7 档 + 退役年龄
- 伤病影响（含 4 类重伤）
- 潜力兑现率 8 因子
- 训练专项 5 种
- 导师影响 4 因子
- 位置改造
- 异常保护

**V1 不做**（推迟）：
- 训练营/海外拉练
- 个性化训练计划
- 心理辅导系统
- 营养师影响
- 详细伤病康复计划

---

*本方案严格遵循 V0.2 `08_球员成长_训练_伤病细化算法.md`，10 因子月度成长 + 7 档年龄表 + 4 类重伤 + 8 因子兑现率，确保球员发展合理且不失控。*
