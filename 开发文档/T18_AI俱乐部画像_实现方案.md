# T18 AI 俱乐部画像 - 详细实现方案

> 任务编号：T18
> 周期：W77-W81（5 周）
> 优先级：P1（依赖 T13 基础版 + T17 经济模型）
> 依据：V0.2 `05_AI俱乐部决策模型.md`
> 铁律：5 种画像 + 短板识别 + 转会目标评分 + 续约/换帅 + 计算频率分批 + 防崩坏 7 约束
> 定位：Gate 3 长程模拟通过的关键，决定 20 年世界是否"像足球"

---

## 一、整体架构

### 1. AI 决策流水线

```
                ┌─────────────────────────────┐
                │  AiScheduler (频率分批触发)   │
                │  月度/转会窗/赛季分批         │
                └─────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ SquadNeeds   │    │ Transfer     │    │ Contract     │
│ Analyzer     │    │ Engine       │    │ Manager      │
│ (短板识别)    │    │ (买卖决策)    │    │ (续约/换帅)   │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ position_    │    │ target_score │    │ renew_       │
│ need_score   │    │ offer_logic  │    │ priority     │
│ age_risk     │    │ sell_score   │    │ sack_score   │
│ tactical_fit │    │ budget_check │    │              │
└──────────────┘    └──────────────┘    └──────────────┘
                              │
                              ▼
                ┌─────────────────────────────┐
                │  AiConstraintGuard (防崩坏)   │
                │  7 条约束全量校验             │
                └─────────────────────────────┘
                              │
                              ▼
                ┌─────────────────────────────┐
                │  AiDecisionLogger            │
                │  写入 ai_decision_log        │
                └─────────────────────────────┘
```

### 2. 包结构

```
com.greenDynasty.ai
├── api/                           # 对外接口
│   ├── AiSimulator.kt             # AI 模拟入口
│   └── AiDecisionBus.kt           # 决策事件总线
├── profile/                       # 俱乐部画像
│   ├── ClubAiProfile.kt           # 画像数据类
│   ├── ClubProfileResolver.kt     # 画像计算与类型判定
│   └── ClubProfileFactory.kt      # 5 种类型画像生成
├── squad/                         # 阵容分析
│   ├── SquadNeedsAnalyzer.kt      # 短板识别
│   ├── PositionDepthChecker.kt    # 位置深度
│   ├── AgeStructureChecker.kt     # 年龄结构
│   └── TacticalFitChecker.kt      # 战术适配
├── transfer/                      # 转会决策
│   ├── TransferTargetScorer.kt    # 目标评分
│   ├── OfferCalculator.kt         # 报价计算
│   ├── SellDecisionMaker.kt       # 卖人决策
│   └── TransferBudgetManager.kt   # 预算管理
├── contract/                      # 合同管理
│   ├── RenewalManager.kt          # 续约
│   └── ManagerSackDecider.kt      # 换帅
├── scheduler/                     # 频率调度
│   ├── AiScheduler.kt             # 调度入口
│   ├── AiTaskType.kt              # 任务类型枚举
│   └── AiFrequencyConfig.kt       # 频率配置
├── guard/                         # 防崩坏约束
│   ├── AiConstraintGuard.kt       # 约束守卫
│   └── ConstraintViolation.kt     # 违规报告
├── log/                           # 决策日志
│   └── AiDecisionLogger.kt
└── config/                        # 配置化参数
    ├── AiConfig.kt
    └── AiConfigLoader.kt
```

---

## 二、核心数据类

### 1. 俱乐部画像（V0.2 05 §二）

```kotlin
// profile/ClubAiProfile.kt
data class ClubAiProfile(
    val clubId: Int,
    val ambition: Int,                  // 野心 0-100
    val financialPower: Int,            // 财力 0-100
    val youthPreference: Int,           // 青训偏好 0-100
    val starPreference: Int,            // 巨星偏好 0-100
    val resalePreference: Int,          // 低买高卖偏好 0-100
    val domesticPreference: Int,        // 本土球员偏好 0-100
    val tacticalIdentity: String,       // 战术倾向 high_press / possession / counter_attack 等
    val riskTolerance: Int,             // 风险容忍 0-100
    val wageStrictness: Int,            // 工资控制 0-100
    val patienceWithManager: Int,       // 主帅耐心 0-100
    val clubType: ClubType              // 计算得出的类型
)

enum class ClubType {
    ELITE_CONTENDER,      // 豪门争冠型
    YOUTH_DEVELOPER,      // 青训培养型
    PROFIT_MAKER,         // 黑店型
    RELEGATION_FIGHTER,   // 保级务实型
    MONEY_EXPANSION       // 金元扩张型
}

// profile/ClubProfileFactory.kt
class ClubProfileFactory(private val config: AiConfig) {

    /**
     * 根据俱乐部基础属性推断画像类型
     * V0.2 05 §三 5 种类型的判定规则
     */
    fun create(club: ClubEntity, economy: ClubFinancialState): ClubAiProfile {
        val profile = ClubAiProfile(
            clubId = club.clubId,
            ambition = club.reputation + randomBias(10),
            financialPower = economy.financialPowerScore,
            youthPreference = club.youthLevel + randomBias(15),
            starPreference = if (club.reputation > 75) 70 + randomBias(15) else 30 + randomBias(20),
            resalePreference = if (club.reputation in 40..65) 65 + randomBias(15) else 30 + randomBias(20),
            domesticPreference = 40 + randomBias(30),
            tacticalIdentity = assignTacticalIdentity(club),
            riskTolerance = 30 + randomBias(40),
            wageStrictness = 100 - club.financeLevel + randomBias(20),
            patienceWithManager = if (club.reputation > 75) 30 + randomBias(20) else 50 + randomBias(30),
            clubType = ClubType.ELITE_CONTENDER  // 占位，下面计算
        )

        return profile.copy(clubType = resolveType(profile))
    }

    private fun resolveType(p: ClubAiProfile): ClubType {
        // 按 V0.2 05 §三 规则判定
        return when {
            p.ambition > 75 && p.starPreference > 65 && p.financialPower > 70
                -> ClubType.ELITE_CONTENDER
            p.youthPreference > 65 && p.resalePreference < 50
                -> ClubType.YOUTH_DEVELOPER
            p.resalePreference > 65 && p.youthPreference > 45
                -> ClubType.PROFIT_MAKER
            p.financialPower < 40 && p.wageStrictness > 60
                -> ClubType.RELEGATION_FIGHTER
            p.financialPower > 80 && p.starPreference > 70 && p.wageStrictness < 40
                -> ClubType.MONEY_EXPANSION
            else -> ClubType.RELEGATION_FIGHTER  // 默认保守
        }
    }

    private fun assignTacticalIdentity(club: ClubEntity): String {
        // 简化：随机分配主流战术
        return listOf("high_press", "possession", "counter_attack", "wing_cross").random()
    }

    private fun randomBias(range: Int): Int = (-range..range).random()
}
```

### 2. 阵容短板（V0.2 05 §四）

```kotlin
// squad/SquadNeedsAnalyzer.kt
data class SquadNeeds(
    val clubId: Int,
    val positionNeeds: List<PositionNeed>,
    val ageRisks: List<AgeRisk>,
    val tacticalGaps: List<TacticalGap>,
    val overallPriority: PositionNeed?
)

data class PositionNeed(
    val position: Position,                // GK/CB/LB/RB/DM/CM/AM/LW/RW/ST
    val needScore: Double,                 // 0-100，越高越急需
    val starterGap: Int,                   // 主力缺几人（-1 多余，0 刚好，1+ 缺）
    val backupGap: Int,                    // 替补缺口
    val averageAgeRisk: Double,            // 0-1，年龄风险
    val injuryRisk: Double,                // 0-1，伤病覆盖风险
    val contractExpiryRisk: Double,        // 0-1，合同到期风险
    val tacticalImportance: Double         // 0-1，战术重要性
)

data class AgeRisk(
    val position: Position,
    val avgAge: Double,
    val riskType: AgeRiskType,             // TOO_OLD / TOO_YOUNG / NO_SUCCESSOR
    val severity: Double                   // 0-1
)

enum class AgeRiskType { TOO_OLD, TOO_YOUNG, NO_SUCCESSOR }

data class TacticalGap(
    val attribute: String,                 // pressing / passing / pace 等
    val gapScore: Double,                  // 0-1，缺口大小
    val affectedPositions: List<Position>
)
```

### 3. 转会目标（V0.2 05 §五）

```kotlin
// transfer/TransferTargetScorer.kt
data class TransferTarget(
    val playerId: Int,
    val targetScore: Double,               // 0-100
    val scoreBreakdown: TargetScoreBreakdown,
    val estimatedValue: Int,               // 估价
    val maxOffer: Int,                     // 最大报价
    val isAffordable: Boolean
)

data class TargetScoreBreakdown(
    val positionNeed: Double,              // 0.25 权重
    val currentAbilityFit: Double,         // 0.20
    val potentialFit: Double,              // 0.15
    val priceValue: Double,                // 0.15
    val wageAffordability: Double,         // 0.10
    val ageFit: Double,                    // 0.05
    val tacticalFit: Double,               // 0.05
    val nationalityFit: Double,            // 0.03
    val commercialValue: Double            // 0.02
)
```

---

## 三、阵容短板识别（V0.2 05 §四）

### 1. 位置深度检查

```kotlin
// squad/PositionDepthChecker.kt
class PositionDepthChecker(
    private val saveDb: SaveDatabase,
    private val historyDb: HistoryDatabase,
    private val config: AiConfig
) {

    /**
     * V0.2 05 §四.1 位置深度检查
     * position_need_score =
     *   starter_gap * 0.40
     * + backup_gap * 0.20
     * + average_age_risk * 0.15
     * + injury_risk * 0.10
     * + contract_expiry_risk * 0.10
     * + tactical_importance * 0.05
     */
    fun analyze(clubId: Int, saveId: Int, tacticalIdentity: String): List<PositionNeed> {
        val positions = Position.values().toList()
        return positions.map { pos ->
            val players = getPlayersByPosition(clubId, saveId, pos)

            val starterGap = calculateStarterGap(players)
            val backupGap = calculateBackupGap(players)
            val ageRisk = calculateAgeRisk(players)
            val injuryRisk = calculateInjuryRisk(players)
            val contractRisk = calculateContractExpiryRisk(players)
            val tacticalImportance = calculateTacticalImportance(pos, tacticalIdentity)

            val needScore = starterGap * 0.40 +
                            backupGap * 0.20 +
                            ageRisk * 0.15 +
                            injuryRisk * 0.10 +
                            contractRisk * 0.10 +
                            tacticalImportance * 0.05

            PositionNeed(
                position = pos,
                needScore = needScore,
                starterGap = starterGap,
                backupGap = backupGap,
                averageAgeRisk = ageRisk,
                injuryRisk = injuryRisk,
                contractExpiryRisk = contractRisk,
                tacticalImportance = tacticalImportance
            )
        }.sortedByDescending { it.needScore }
    }

    private fun calculateStarterGap(players: List<SavePlayerStateWithAttributes>): Int {
        // 主力数量应为 1-2（根据位置），替补 1-2
        val targetStarters = config.positionDepth.targetStarters  // 默认 1
        val actualStarters = players.count { it.squadRole == "starter" }
        return (targetStarters - actualStarters).coerceAtLeast(-1)  // -1 表示多余可卖
    }

    private fun calculateBackupGap(players: List<SavePlayerStateWithAttributes>): Int {
        val targetBackups = config.positionDepth.targetBackups  // 默认 1
        val actualBackups = players.count { it.squadRole in listOf("backup", "rotation") }
        return (targetBackups - actualBackups).coerceAtLeast(-1)
    }

    private fun calculateAgeRisk(players: List<SavePlayerStateWithAttributes>): Double {
        if (players.isEmpty()) return 1.0
        val avgAge = players.map { calculateAge(it.birthDate) }.average()
        return when {
            avgAge > 32 -> 0.9   // 主力偏老，急需接班人
            avgAge < 19 -> 0.7   // 全是年轻人，缺即战力
            avgAge in 30..32 -> 0.5
            else -> 0.1
        }
    }

    private fun calculateInjuryRisk(players: List<SavePlayerStateWithAttributes>): Double {
        if (players.isEmpty()) return 1.0
        val injuredCount = players.count { it.injuryStatus != "healthy" }
        val proneCount = players.count { it.injuryProneness > 70 }
        return (injuredCount * 0.3 + proneCount * 0.2).coerceIn(0.0, 1.0)
    }

    private fun calculateContractExpiryRisk(players: List<SavePlayerStateWithAttributes>): Double {
        if (players.isEmpty()) return 1.0
        val now = currentDate()
        val expiringCount = players.count { p ->
            p.contractUntil?.let { LocalDate.parse(it).let { d -> d.isBefore(now.plusMonths(12)) } } ?: true
        }
        return (expiringCount.toDouble() / players.size).coerceIn(0.0, 1.0)
    }

    /**
     * 战术重要性（V0.2 05 §四.3）
     * 高位压迫需要耐力/工作率/速度
     * 控球需要传球/技术/视野
     * 防守反击需要速度/强壮/门将
     */
    private fun calculateTacticalImportance(pos: Position, tacticalIdentity: String): Double {
        val weights = config.tacticalImportanceMatrix[tacticalIdentity] ?: return 0.5
        return weights[pos] ?: 0.5
    }
}
```

### 2. 年龄结构检查（V0.2 05 §四.2）

```kotlin
// squad/AgeStructureChecker.kt
class AgeStructureChecker {

    fun analyze(clubId: Int, saveId: Int, players: List<SavePlayerStateWithAttributes>): List<AgeRisk> {
        val risks = mutableListOf<AgeRisk>()
        val byPosition = players.groupBy { it.primaryPosition }

        for ((pos, posPlayers) in byPosition) {
            val avgAge = posPlayers.map { calculateAge(it.birthDate) }.average()

            when {
                // 主力平均年龄过高（V0.2 05 §四.2 "主力平均年龄过高"）
                avgAge > 31 && posPlayers.count { calculateAge(it.birthDate) > 30 } >= posPlayers.size / 2 -> {
                    risks.add(AgeRisk(pos, avgAge, AgeRiskType.TOO_OLD, 0.8))
                }
                // 某位置全是年轻人（V0.2 05 §四.2 "某位置全是年轻人"）
                avgAge < 20 && posPlayers.all { calculateAge(it.birthDate) < 21 } -> {
                    risks.add(AgeRisk(pos, avgAge, AgeRiskType.TOO_YOUNG, 0.6))
                }
                // 核心球员合同快到期且无替代者（V0.2 05 §四.2 "老将下滑无替代者"）
                posPlayers.any { calculateAge(it.birthDate) > 32 } &&
                posPlayers.none { calculateAge(it.birthDate) in 22..28 } -> {
                    risks.add(AgeRisk(pos, avgAge, AgeRiskType.NO_SUCCESSOR, 0.9))
                }
            }
        }

        return risks.sortedByDescending { it.severity }
    }
}
```

---

## 四、转会目标评分（V0.2 05 §五）

### 1. target_score 计算

```kotlin
// transfer/TransferTargetScorer.kt
class TransferTargetScorer(
    private val config: AiConfig,
    private val economyService: EconomyService  // T17 经济模型
) {

    /**
     * V0.2 05 §五 target_score 公式
     * target_score =
     *   position_need * 0.25
     * + current_ability_fit * 0.20
     * + potential_fit * 0.15
     * + price_value * 0.15
     * + wage_affordability * 0.10
     * + age_fit * 0.05
     * + tactical_fit * 0.05
     * + nationality_fit * 0.03
     * + commercial_value * 0.02
     */
    fun score(
        candidate: PlayerCandidate,
        clubProfile: ClubAiProfile,
        positionNeed: PositionNeed,
        clubFinancial: ClubFinancialState
    ): TransferTarget {
        val positionNeedScore = positionNeed.needScore / 100.0
        val currentAbilityFit = calculateCurrentAbilityFit(candidate, clubProfile)
        val potentialFit = calculatePotentialFit(candidate, clubProfile)
        val priceValue = calculatePriceValue(candidate, clubFinancial)
        val wageAffordability = calculateWageAffordability(candidate, clubFinancial)
        val ageFit = calculateAgeFit(candidate, clubProfile)
        val tacticalFit = calculateTacticalFit(candidate, clubProfile)
        val nationalityFit = calculateNationalityFit(candidate, clubProfile)
        val commercialValue = calculateCommercialValue(candidate, clubProfile)

        // 按俱乐部类型调整权重（V0.2 05 §五 "不同俱乐部类型调整权重"）
        val adjustedWeights = adjustWeightsByClubType(config.targetScoreWeights, clubProfile.clubType)

        val targetScore = (
            positionNeedScore * adjustedWeights.positionNeed +
            currentAbilityFit * adjustedWeights.currentAbilityFit +
            potentialFit * adjustedWeights.potentialFit +
            priceValue * adjustedWeights.priceValue +
            wageAffordability * adjustedWeights.wageAffordability +
            ageFit * adjustedWeights.ageFit +
            tacticalFit * adjustedWeights.tacticalFit +
            nationalityFit * adjustedWeights.nationalityFit +
            commercialValue * adjustedWeights.commercialValue
        ) * 100

        val estimatedValue = economyService.estimateMarketValue(candidate.toPlayerState())
        val maxOffer = OfferCalculator(config, economyService).calculateMaxOffer(
            candidate, clubProfile, clubFinancial, positionNeed
        )
        val isAffordable = maxOffer <= clubFinancial.transferBudgetRemaining

        return TransferTarget(
            playerId = candidate.playerId,
            targetScore = targetScore,
            scoreBreakdown = TargetScoreBreakdown(
                positionNeedScore, currentAbilityFit, potentialFit, priceValue,
                wageAffordability, ageFit, tacticalFit, nationalityFit, commercialValue
            ),
            estimatedValue = estimatedValue,
            maxOffer = maxOffer,
            isAffordable = isAffordable
        )
    }

    /**
     * 按俱乐部类型调整权重
     * V0.2 05 §五："不同俱乐部类型调整权重"
     * - 豪门：current_ability_fit 更高
     * - 青训型：potential_fit 更高
     * - 黑店型：price_value 和 resale 更高
     * - 保级型：wage_affordability 更高
     * - 金元型：commercial_value 和 reputation 更高
     */
    private fun adjustWeightsByClubType(
        base: TargetScoreWeights,
        type: ClubType
    ): TargetScoreWeights {
        return when (type) {
            ClubType.ELITE_CONTENDER -> base.copy(
                currentAbilityFit = base.currentAbilityFit + 0.10,
                potentialFit = base.potentialFit - 0.05,
                priceValue = base.priceValue - 0.05
            )
            ClubType.YOUTH_DEVELOPER -> base.copy(
                potentialFit = base.potentialFit + 0.10,
                currentAbilityFit = base.currentAbilityFit - 0.05,
                ageFit = base.ageFit + 0.05
            )
            ClubType.PROFIT_MAKER -> base.copy(
                priceValue = base.priceValue + 0.10,
                potentialFit = base.potentialFit + 0.05,
                currentAbilityFit = base.currentAbilityFit - 0.10,
                commercialValue = base.commercialValue + 0.03
            )
            ClubType.RELEGATION_FIGHTER -> base.copy(
                wageAffordability = base.wageAffordability + 0.10,
                priceValue = base.priceValue + 0.05,
                currentAbilityFit = base.currentAbilityFit + 0.05,
                potentialFit = base.potentialFit - 0.10
            )
            ClubType.MONEY_EXPANSION -> base.copy(
                commercialValue = base.commercialValue + 0.08,
                currentAbilityFit = base.currentAbilityFit + 0.05,
                priceValue = base.priceValue - 0.05,
                wageAffordability = base.wageAffordability - 0.05
            )
        }
    }

    // 各分项计算（简化）
    private fun calculateCurrentAbilityFit(c: PlayerCandidate, p: ClubAiProfile): Double {
        val expectedCa = when (p.clubType) {
            ClubType.ELITE_CONTENDER -> 80
            ClubType.YOUTH_DEVELOPER -> 60
            ClubType.PROFIT_MAKER -> 65
            ClubType.RELEGATION_FIGHTER -> 65
            ClubType.MONEY_EXPANSION -> 80
        }
        return clamp(abs(c.currentCa - expectedCa) / 30.0, 0.0, 1.0).let { 1.0 - it }
    }

    private fun calculatePotentialFit(c: PlayerCandidate, p: ClubAiProfile): Double {
        val potentialFactor = (c.potentialPa - c.currentCa) / 30.0
        return when (p.clubType) {
            ClubType.YOUTH_DEVELOPER, ClubType.PROFIT_MAKER -> clamp(potentialFactor, 0.0, 1.0)
            else -> clamp(0.5 + potentialFactor * 0.3, 0.0, 1.0)
        }
    }

    private fun calculatePriceValue(c: PlayerCandidate, f: ClubFinancialState): Double {
        val ratio = c.askingPrice.toDouble() / f.transferBudgetRemaining.coerceAtLeast(1)
        return clamp(1.0 - ratio, 0.0, 1.0)
    }

    private fun calculateWageAffordability(c: PlayerCandidate, f: ClubFinancialState): Double {
        val ratio = c.expectedWage.toDouble() / f.wageBudgetRemaining.coerceAtLeast(1)
        return clamp(1.0 - ratio, 0.0, 1.0)
    }

    private fun calculateAgeFit(c: PlayerCandidate, p: ClubAiProfile): Double {
        val idealAgeRange = when (p.clubType) {
            ClubType.YOUTH_DEVELOPER, ClubType.PROFIT_MAKER -> 17..22
            ClubType.ELITE_CONTENDER -> 24..29
            ClubType.RELEGATION_FIGHTER -> 25..31
            ClubType.MONEY_EXPANSION -> 24..30
        }
        return if (c.age in idealAgeRange) 1.0 else 0.5
    }

    private fun calculateTacticalFit(c: PlayerCandidate, p: ClubAiProfile): Double {
        // 球员属性与俱乐部战术倾向的匹配度
        return config.tacticalFitMatrix[p.tacticalIdentity]?.get(c.primaryPosition) ?: 0.5
    }

    private fun calculateNationalityFit(c: PlayerCandidate, p: ClubAiProfile): Double {
        // 本土球员偏好
        return if (c.nationality == p.preferredNationality) 1.0 else 0.3
    }

    private fun calculateCommercialValue(c: PlayerCandidate, p: ClubAiProfile): Double {
        return clamp(c.reputation / 100.0, 0.0, 1.0)
    }

    private fun clamp(v: Double, min: Double, max: Double): Double = v.coerceIn(min, max)
}

data class TargetScoreWeights(
    val positionNeed: Double,           // 0.25
    val currentAbilityFit: Double,      // 0.20
    val potentialFit: Double,           // 0.15
    val priceValue: Double,             // 0.15
    val wageAffordability: Double,      // 0.10
    val ageFit: Double,                 // 0.05
    val tacticalFit: Double,            // 0.05
    val nationalityFit: Double,         // 0.03
    val commercialValue: Double         // 0.02
)
```

### 2. 报价计算（V0.2 05 §六）

```kotlin
// transfer/OfferCalculator.kt
class OfferCalculator(
    private val config: AiConfig,
    private val economyService: EconomyService
) {

    /**
     * V0.2 05 §六.1 估价
     * max_offer = market_value
     *   * club_need_multiplier
     *   * player_reputation_multiplier
     *   * contract_remaining_multiplier
     *   * selling_club_pressure_multiplier
     *   * competition_multiplier
     */
    fun calculateMaxOffer(
        candidate: PlayerCandidate,
        clubProfile: ClubAiProfile,
        financial: ClubFinancialState,
        positionNeed: PositionNeed
    ): Int {
        val marketValue = economyService.estimateMarketValue(candidate.toPlayerState())

        val clubNeedMultiplier = 1.0 + (positionNeed.needScore / 100.0) * 0.5  // 1.0 - 1.5
        val reputationMultiplier = 1.0 + (candidate.reputation - 50) / 100.0   // 0.5 - 1.5
        val contractRemainingMultiplier = when {
            candidate.contractYearsLeft >= 3 -> 1.20
            candidate.contractYearsLeft == 2 -> 1.00
            candidate.contractYearsLeft == 1 -> 0.70
            candidate.contractYearsLeft <= 0 -> 0.40  // 自由球员转会费 0
            else -> 1.0
        }
        val sellingPressureMultiplier = if (candidate.isUnhappy) 0.85 else 1.0
        val competitionMultiplier = if (candidate.otherInterestedClubs > 2) 1.15 else 1.0

        var maxOffer = (marketValue *
            clubNeedMultiplier *
            reputationMultiplier *
            contractRemainingMultiplier *
            sellingPressureMultiplier *
            competitionMultiplier).toInt()

        // V0.2 05 §六.2 预算限制
        if (maxOffer > financial.transferBudgetRemaining) {
            maxOffer = (financial.transferBudgetRemaining * clubProfile.riskTolerance / 100.0).toInt()
        }

        return maxOffer.coerceAtLeast(0)
    }

    /**
     * V0.2 05 §六.3 报价策略
     * | 俱乐部类型 | 初始报价 | 加价次数 |
     * | 豪门      | 90% 市值  | 3       |
     * | 黑店      | 60% 市值  | 2       |
     * | 保级队    | 70% 市值  | 1       |
     * | 金元队    | 110% 市值 | 4       |
     */
    fun getOfferStrategy(clubType: ClubType, marketValue: Int): OfferStrategy {
        val (initialRatio, maxRounds) = when (clubType) {
            ClubType.ELITE_CONTENDER -> 0.90 to 3
            ClubType.PROFIT_MAKER -> 0.60 to 2
            ClubType.RELEGATION_FIGHTER -> 0.70 to 1
            ClubType.MONEY_EXPANSION -> 1.10 to 4
            ClubType.YOUTH_DEVELOPER -> 0.70 to 2
        }
        return OfferStrategy(
            initialOffer = (marketValue * initialRatio).toInt(),
            maxNegotiationRounds = maxRounds,
            incrementPerRound = (marketValue * 0.05).toInt()  // 每轮加 5%
            finalMaxOffer = (marketValue * (initialRatio + 0.15)).toInt()  // 最终不超过初始+15%
        )
    }
}

data class OfferStrategy(
    val initialOffer: Int,
    val maxNegotiationRounds: Int,
    val incrementPerRound: Int,
    val finalMaxOffer: Int
)
```

### 3. 卖人决策（V0.2 05 §七）

```kotlin
// transfer/SellDecisionMaker.kt
class SellDecisionMaker(private val config: AiConfig) {

    /**
     * V0.2 05 §七 卖人逻辑
     * sell_score =
     *   offer_value_ratio * 0.25
     * + player_unhappy * 0.20
     * + contract_expiry_risk * 0.15
     * + squad_depth_cover * 0.15
     * + financial_pressure * 0.15
     * + age_decline_risk * 0.10
     */
    fun shouldSell(
        player: SavePlayerStateWithAttributes,
        offerAmount: Int,
        marketValue: Int,
        clubProfile: ClubAiProfile,
        financial: ClubFinancialState,
        squadDepth: Map<Position, Int>  // 各位置替补数
    ): SellDecision {
        val offerValueRatio = (offerAmount.toDouble() / marketValue.coerceAtLeast(1)).let {
            clamp(it, 0.0, 2.0)
        }

        val playerUnhappy = if (player.morale < 30) 1.0 else if (player.morale < 50) 0.5 else 0.0

        val contractExpiryRisk = calculateContractExpiryRisk(player)

        val squadDepthCover = squadDepth[player.primaryPosition]?.let {
            clamp(it / 3.0, 0.0, 1.0)  // 替补越多越敢卖
        } ?: 0.0

        val financialPressure = if (financial.wageToIncomeRatio > 0.85) 1.0
                                else if (financial.wageToIncomeRatio > 0.70) 0.5
                                else 0.0

        val ageDeclineRisk = calculateAgeDeclineRisk(player)

        val sellScore = (
            offerValueRatio * 0.25 +
            playerUnhappy * 0.20 +
            contractExpiryRisk * 0.15 +
            squadDepthCover * 0.15 +
            financialPressure * 0.15 +
            ageDeclineRisk * 0.10
        ) * 100

        // V0.2 05 §七 特殊规则
        val isCaptain = player.squadRole == "captain"
        val isCorePlayer = player.squadRole in listOf("starter", "key_player")
        val isAcademyFlag = player.isAcademyProduct
        val isInFinancialCrisis = financial.wageToIncomeRatio > 0.85
        val isForcedTransfer = player.morale < 20

        val finalScore = when {
            isForcedTransfer -> sellScore + 30  // 强烈要求转会
            isInFinancialCrisis -> sellScore + 20
            isCaptain -> sellScore - 25
            isCorePlayer && !isAcademyFlag -> sellScore - 15
            isAcademyFlag -> sellScore - 10
            else -> sellScore
        }

        val shouldSell = finalScore >= config.sellThreshold  // 默认 60
        return SellDecision(
            playerId = player.playerId,
            sellScore = finalScore,
            shouldSell = shouldSell,
            reason = buildReason(finalScore, offerValueRatio, playerUnhappy, financialPressure)
        )
    }
}

data class SellDecision(
    val playerId: Int,
    val sellScore: Double,
    val shouldSell: Boolean,
    val reason: String
)
```

---

## 五、续约与换帅

### 1. 续约逻辑（V0.2 05 §八）

```kotlin
// contract/RenewalManager.kt
class RenewalManager(
    private val config: AiConfig,
    private val economyService: EconomyService
) {

    /**
     * V0.2 05 §八 续约逻辑
     * renew_priority =
     *   current_ability * 0.25
     * + potential * 0.20
     * + squad_importance * 0.20
     * + resale_value * 0.10
     * + loyalty * 0.10
     * + age_factor * 0.10
     * + wage_reasonableness * 0.05
     */
    fun evaluateRenewal(
        player: SavePlayerStateWithAttributes,
        squadImportance: Double,          // 0-1，主力=1，替补=0.5
        resaleValue: Int,
        loyalty: Int                      // 0-100
    ): RenewalDecision {
        val currentAbility = player.currentCa / 100.0
        val potential = player.potentialPa / 100.0
        val squadImport = squadImportance
        val resale = clamp(resaleValue / 50_000_000.0, 0.0, 1.0)  // 5000 万为满分
        val loyaltyFactor = loyalty / 100.0
        val ageFactor = calculateAgeFactor(player)
        val wageReasonableness = calculateWageReasonableness(player)

        val priority = (
            currentAbility * 0.25 +
            potential * 0.20 +
            squadImport * 0.20 +
            resale * 0.10 +
            loyaltyFactor * 0.10 +
            ageFactor * 0.10 +
            wageReasonableness * 0.05
        ) * 100

        // V0.2 05 §八 处理分级
        val (action, leadTime) = when {
            priority >= 75 -> RenewalAction.EARLY_RENEW to 18..24  // 提前 18-24 个月
            priority >= 55 -> RenewalAction.NORMAL_RENEW to 12..18
            priority >= 35 -> RenewalAction.LATE_DECISION to 6..12
            else -> RenewalAction.RELEASE_OR_LIST to 0..0
        }

        return RenewalDecision(
            playerId = player.playerId,
            priority = priority,
            action = action,
            leadTimeMonths = leadTime,
            offeredWage = economyService.calculateExpectedWage(player)
        )
    }
}

enum class RenewalAction {
    EARLY_RENEW,      // 高优先级：提前 18-24 个月续约
    NORMAL_RENEW,     // 中优先级：提前 12 个月续约
    LATE_DECISION,    // 低优先级：合同到期放走或挂牌
    RELEASE_OR_LIST
}

data class RenewalDecision(
    val playerId: Int,
    val priority: Double,
    val action: RenewalAction,
    val leadTimeMonths: IntRange,
    val offeredWage: Int
)
```

### 2. 换帅逻辑（V0.2 05 §九）

```kotlin
// contract/ManagerSackDecider.kt
class ManagerSackDecider(private val config: AiConfig) {

    /**
     * V0.2 05 §九 换帅逻辑
     * manager_sack_score =
     *   board_expectation_gap * 0.35
     * + recent_form_bad * 0.20
     * + dressing_room_unrest * 0.15
     * + fan_pressure * 0.10
     * + financial_underperformance * 0.10
     * + derby_failure * 0.05
     * + european_failure * 0.05
     *
     * 董事会耐心越低，阈值越低
     */
    fun evaluate(
        clubProfile: ClubAiProfile,
        seasonRecord: SeasonRecord,
        dressingRoomMorale: Int,
        fanSatisfaction: Int,
        financial: ClubFinancialState,
        recentForm: RecentForm,
        derbyResults: List<MatchResult>,
        europeanResult: EuropeanResult?
    ): SackDecision {
        val boardExpectationGap = calculateExpectationGap(seasonRecord, clubProfile.ambition)
        val recentFormBad = calculateRecentFormBad(recentForm)
        val dressingRoomUnrest = 1.0 - (dressingRoomMorale / 100.0)
        val fanPressure = 1.0 - (fanSatisfaction / 100.0)
        val financialUnderperformance = if (financial.wageToIncomeRatio > 0.85) 1.0 else 0.0
        val derbyFailure = if (derbyResults.any { it.isLost }) 1.0 else 0.0
        val europeanFailure = europeanResult?.let { if (it.isGroupStageOut) 1.0 else 0.0 } ?: 0.0

        val sackScore = (
            boardExpectationGap * 0.35 +
            recentFormBad * 0.20 +
            dressingRoomUnrest * 0.15 +
            fanPressure * 0.10 +
            financialUnderperformance * 0.10 +
            derbyFailure * 0.05 +
            europeanFailure * 0.05
        ) * 100

        // 董事会耐心调整阈值（V0.2 05 §九 "董事会耐心越低，阈值越低"）
        val threshold = config.managerSackBaseThreshold - (clubProfile.patienceWithManager - 50) * 0.3
        // patience 50 = 阈值 70，patience 30 = 阈值 64，patience 70 = 阈值 76

        val shouldSack = sackScore >= threshold

        // 连败触发（V0.2 05 §十 "连败后触发"）
        val isLosingStreakTrigger = recentForm.consecutiveLosses >= config.losingStreakThreshold

        return SackDecision(
            clubId = clubProfile.clubId,
            sackScore = sackScore,
            threshold = threshold,
            shouldSack = shouldSack || isLosingStreakTrigger,
            reason = buildSackReason(boardExpectationGap, recentFormBad, dressingRoomUnrest)
        )
    }
}

data class SackDecision(
    val clubId: Int,
    val sackScore: Double,
    val threshold: Double,
    val shouldSack: Boolean,
    val reason: String
)
```

---

## 六、AI 计算频率优化（V0.2 05 §十）

### 1. 频率分批调度

```kotlin
// scheduler/AiScheduler.kt
class AiScheduler(
    private val saveDb: SaveDatabase,
    private val needsAnalyzer: SquadNeedsAnalyzer,
    private val transferScorer: TransferTargetScorer,
    private val renewalManager: RenewalManager,
    private val sackDecider: ManagerSackDecider,
    private val constraintGuard: AiConstraintGuard,
    private val logger: AiDecisionLogger,
    private val config: AiConfig
) {

    /**
     * V0.2 05 §十 AI 计算频率表
     * | 阵容短板分析   | 每月一次，转会窗每周一次 |
     * | 球员续约检查   | 每月一次               |
     * | 转会目标搜索   | 转会窗每周一次          |
     * | 报价谈判       | 转会窗每日处理少量      |
     * | 换帅检查       | 每月一次，连败后触发    |
     * | 青训提拔       | 每赛季 2 次            |
     * | 战术调整       | 每 5 场或换帅后        |
     */
    fun onDailyAdvance(currentDate: LocalDate, saveId: Int) {
        val allClubs = saveDb.saveClubStateDao().getAllBySave(saveId)

        for (club in allClubs) {
            val profile = saveDb.clubAiProfileDao().getByClub(club.clubId)
                ?: continue

            // 每日任务：转会窗报价谈判
            if (isTransferWindow(currentDate)) {
                processDailyTransferNegotiations(club, profile, saveId, currentDate)
            }

            // 每周任务：转会窗期间的目标搜索
            if (isTransferWindow(currentDate) && currentDate.dayOfWeek == DayOfWeek.MONDAY) {
                searchTransferTargets(club, profile, saveId, currentDate)
            }

            // 每月任务：阵容分析 + 续约检查 + 换帅检查
            if (currentDate.dayOfMonth == 1) {
                processMonthlyTasks(club, profile, saveId, currentDate)
            }

            // 每 5 场：战术调整
            if (shouldAdjustTactic(club, saveId)) {
                adjustTactic(club, profile, saveId)
            }

            // 每赛季 2 次：青训提拔
            if (isYouthPromotionTime(currentDate)) {
                promoteYouth(club, profile, saveId, currentDate)
            }
        }
    }

    private fun processMonthlyTasks(
        club: SaveClubStateEntity,
        profile: ClubAiProfile,
        saveId: Int,
        date: LocalDate
    ) {
        // 1. 阵容短板分析
        val needs = needsAnalyzer.analyze(club.clubId, saveId, profile.tacticalIdentity)

        // 2. 续约检查
        val players = saveDb.savePlayerStateDao().getByClub(saveId, club.clubId)
        for (player in players) {
            val renewal = renewalManager.evaluateRenewal(
                player,
                squadImportance = calculateSquadImportance(player, players),
                resaleValue = player.marketValue,
                loyalty = player.loyalty
            )
            if (renewal.action == RenewalAction.EARLY_RENEW || renewal.action == RenewalAction.NORMAL_RENEW) {
                executeRenewal(player, renewal, saveId, date)
            }
        }

        // 3. 换帅检查
        val sackDecision = sackDecider.evaluate(
            profile = profile,
            seasonRecord = getSeasonRecord(club.clubId, saveId),
            dressingRoomMorale = club.dressingRoomMorale,
            fanSatisfaction = club.fanSatisfaction,
            financial = getFinancialState(club),
            recentForm = getRecentForm(club.clubId, saveId),
            derbyResults = getDerbyResults(club.clubId, saveId),
            europeanResult = getEuropeanResult(club.clubId, saveId)
        )

        if (sackDecision.shouldSack) {
            executeManagerSack(club, profile, saveId, date, sackDecision)
        }
    }

    private fun searchTransferTargets(
        club: SaveClubStateEntity,
        profile: ClubAiProfile,
        saveId: Int,
        date: LocalDate
    ) {
        val needs = needsAnalyzer.analyze(club.clubId, saveId, profile.tacticalIdentity)
        val topNeed = needs.positionNeeds.firstOrNull() ?: return

        // 搜索候选（避免全量扫描，只查 top need 位置）
        val candidates = searchCandidatesByPosition(topNeed.position, saveId, profile)
        val financial = getFinancialState(club)

        // 评分 Top 10
        val scoredTargets = candidates.map { c ->
            transferScorer.score(c, profile, topNeed, financial)
        }.filter { it.isAffordable }.sortedByDescending { it.targetScore }.take(10)

        // 选择最高分目标发起报价
        scoredTargets.firstOrNull()?.let { target ->
            executeTransferOffer(target, club, profile, saveId, date)
        }
    }

    /**
     * V0.2 05 §十一 防止世界崩坏的约束
     */
    private fun executeTransferOffer(
        target: TransferTarget,
        club: SaveClubStateEntity,
        profile: ClubAiProfile,
        saveId: Int,
        date: LocalDate
    ) {
        // 1. 约束校验
        val violation = constraintGuard.checkBeforeTransfer(target, club, profile, saveId)
        if (violation != null) {
            logger.logDecision(
                saveId, club.clubId, date, "transfer_blocked",
                target.playerId, target.targetScore, violation.reason,
                club.transferBudget, club.transferBudget
            )
            return
        }

        // 2. 执行报价
        val offer = OfferCalculator(config, economyService).getOfferStrategy(
            profile.clubType, target.estimatedValue
        )
        // ... 发起报价流程

        // 3. 记录决策日志
        logger.logDecision(
            saveId, club.clubId, date, "transfer_offer",
            target.playerId, target.targetScore, "target_score=${target.targetScore}",
            club.transferBudget, club.transferBudget - offer.initialOffer
        )
    }
}
```

---

## 七、防崩坏约束（V0.2 05 §十一）

```kotlin
// guard/AiConstraintGuard.kt
class AiConstraintGuard(
    private val saveDb: SaveDatabase,
    private val config: AiConfig
) {

    /**
     * V0.2 05 §十一 防止世界崩坏的 7 条约束
     * 1. 每个转会窗口限制 AI 俱乐部最大交易数量
     * 2. 俱乐部工资不得长期超过收入安全线
     * 3. 豪门不会无理由卖出全部核心
     * 4. 保级队不会买入远超预算的球星
     * 5. 年轻天才不会全部流向同一家豪门
     * 6. 位置短板优先级必须高于囤积同类球员
     * 7. AI 每次大额交易后重新计算预算
     */
    fun checkBeforeTransfer(
        target: TransferTarget,
        club: SaveClubStateEntity,
        profile: ClubAiProfile,
        saveId: Int
    ): ConstraintViolation? {

        // 1. 每窗交易数量限制
        val windowTxCount = saveDb.saveTransferOfferDao()
            .countWindowTransactions(saveId, club.clubId, currentTransferWindow())
        if (windowTxCount >= config.maxTransfersPerWindow) {  // 默认 5
            return ConstraintViolation("WINDOW_LIMIT_REACHED", "本窗已交易 $windowTxCount 次，达到上限")
        }

        // 2. 工资安全线（V0.2 05 §十一.2）
        val financial = getFinancialState(club)
        if (financial.wageToIncomeRatio > 0.85 && profile.wageStrictness > 60) {
            return ConstraintViolation("WAGE_OVER_LIMIT", "工资/收入比 ${financial.wageToIncomeRatio} 超过 85%")
        }

        // 3. 豪门不卖全部核心（V0.2 05 §十一.3）
        // 此约束在卖人决策时校验，买入时不触发

        // 4. 保级队不买远超预算球星（V0.2 05 §十一.4）
        if (profile.clubType == ClubType.RELEGATION_FIGHTER &&
            target.maxOffer > financial.transferBudgetRemaining * 0.5) {
            return ConstraintViolation("BUDGET_EXCEED", "保级队买入金额超过预算 50%")
        }

        // 5. 年轻天才不全部流向同一家豪门（V0.2 05 §十一.5）
        val youngTalentCount = saveDb.saveTransferOfferDao()
            .countYoungTalentSignedByClub(saveId, club.clubId, ageMax = 21, minPa = 80)
        if (profile.clubType == ClubType.ELITE_CONTENDER &&
            youngTalentCount >= config.maxYoungTalentPerClubPerWindow) {  // 默认 2
            return ConstraintViolation("TALENT_HOARDING", "豪门本窗已签 ${youngTalentCount} 名 21 岁以下高潜球员")
        }

        // 6. 位置短板优先级（V0.2 05 §十一.6）
        val needs = saveDb.squadNeedsCacheDao().getLatest(saveId, club.clubId)
        if (needs != null) {
            val topNeedPos = needs.positionNeeds.firstOrNull()?.position
            val targetPos = getTargetPosition(target.playerId)
            if (topNeedPos != null && targetPos != topNeedPos) {
                // 如果不是最急需位置，需要 target_score 显著更高才允许
                val topNeedScore = needs.positionNeeds.first().needScore
                if (topNeedScore > 70 && target.targetScore < 80) {
                    return ConstraintViolation(
                        "POSITION_PRIORITY",
                        "急需位置 $topNeedPos 未补强，不应购买 $targetPos"
                    )
                }
            }
        }

        // 7. 大额交易后重算预算（V0.2 05 §十一.7）
        // 此约束在交易完成后触发，买入前检查预算即可
        if (target.maxOffer > club.transferBudget) {
            return ConstraintViolation("BUDGET_INSUFFICIENT", "报价超过预算")
        }

        return null  // 通过所有约束
    }

    /**
     * 卖人特殊约束（V0.2 05 §十一.3 豪门不卖全部核心）
     */
    fun checkBeforeSell(
        player: SavePlayerStateWithAttributes,
        club: SaveClubStateEntity,
        profile: ClubAiProfile,
        saveId: Int
    ): ConstraintViolation? {
        if (profile.clubType == ClubType.ELITE_CONTENDER && player.squadRole == "key_player") {
            val corePlayersSold = saveDb.saveTransferOfferDao()
                .countCorePlayersSoldThisSeason(saveId, club.clubId)
            if (corePlayersSold >= config.maxCorePlayersSoldPerSeason) {  // 默认 2
                return ConstraintViolation("CORE_PLAYER_PROTECTION", "豪门本季已卖 $corePlayersSold 名核心球员")
            }
        }
        return null
    }
}

data class ConstraintViolation(
    val code: String,
    val reason: String
)
```

---

## 八、决策日志（V0.2 05 §十二）

```kotlin
// log/AiDecisionLogger.kt
class AiDecisionLogger(private val saveDb: SaveDatabase) {

    /**
     * V0.2 05 §十二 调试输出
     * 每笔 AI 转会后记录：
     * - 购买原因
     * - 目标评分
     * - 预算状态
     * - 位置需求
     * - 替代目标
     * - 失败原因
     */
    fun logDecision(
        saveId: Int,
        clubId: Int,
        date: LocalDate,
        decisionType: String,
        targetPlayerId: Int?,
        score: Double,
        reason: String,
        budgetBefore: Int,
        budgetAfter: Int
    ) {
        saveDb.aiDecisionLogDao().insert(AiDecisionLogEntity(
            id = 0,
            saveId = saveId.toString(),
            clubId = clubId,
            decisionDate = date.format(ISO_LOCAL_DATE),
            decisionType = decisionType,    // transfer_offer / transfer_blocked / renewal / sack
            targetPlayerId = targetPlayerId,
            score = score,
            reason = reason,
            budgetBefore = budgetBefore,
            budgetAfter = budgetAfter,
            result = "logged"
        ))
    }

    /**
     * 调试用：查询某俱乐部的决策历史
     */
    fun getDecisionHistory(saveId: Int, clubId: Int, limit: Int = 50): List<AiDecisionLogEntity> {
        return saveDb.aiDecisionLogDao().getByClub(saveId.toString(), clubId, limit)
    }
}
```

---

## 九、配置化参数清单

### 1. ai_config.json

```json
{
  "target_score_weights": {
    "positionNeed": 0.25,
    "currentAbilityFit": 0.20,
    "potentialFit": 0.15,
    "priceValue": 0.15,
    "wageAffordability": 0.10,
    "ageFit": 0.05,
    "tacticalFit": 0.05,
    "nationalityFit": 0.03,
    "commercialValue": 0.02
  },
  "sell_score_weights": {
    "offerValueRatio": 0.25,
    "playerUnhappy": 0.20,
    "contractExpiryRisk": 0.15,
    "squadDepthCover": 0.15,
    "financialPressure": 0.15,
    "ageDeclineRisk": 0.10
  },
  "renew_priority_weights": {
    "currentAbility": 0.25,
    "potential": 0.20,
    "squadImportance": 0.20,
    "resaleValue": 0.10,
    "loyalty": 0.10,
    "ageFactor": 0.10,
    "wageReasonableness": 0.05
  },
  "manager_sack_weights": {
    "boardExpectationGap": 0.35,
    "recentFormBad": 0.20,
    "dressingRoomUnrest": 0.15,
    "fanPressure": 0.10,
    "financialUnderperformance": 0.10,
    "derbyFailure": 0.05,
    "europeanFailure": 0.05
  },
  "club_type_modifiers": {
    "ELITE_CONTENDER": {
      "currentAbilityFit_delta": 0.10,
      "potentialFit_delta": -0.05,
      "priceValue_delta": -0.05
    },
    "YOUTH_DEVELOPER": {
      "potentialFit_delta": 0.10,
      "currentAbilityFit_delta": -0.05,
      "ageFit_delta": 0.05
    },
    "PROFIT_MAKER": {
      "priceValue_delta": 0.10,
      "potentialFit_delta": 0.05,
      "currentAbilityFit_delta": -0.10,
      "commercialValue_delta": 0.03
    },
    "RELEGATION_FIGHTER": {
      "wageAffordability_delta": 0.10,
      "priceValue_delta": 0.05,
      "currentAbilityFit_delta": 0.05,
      "potentialFit_delta": -0.10
    },
    "MONEY_EXPANSION": {
      "commercialValue_delta": 0.08,
      "currentAbilityFit_delta": 0.05,
      "priceValue_delta": -0.05,
      "wageAffordability_delta": -0.05
    }
  },
  "offer_strategy": {
    "ELITE_CONTENDER": { "initial_ratio": 0.90, "max_rounds": 3 },
    "PROFIT_MAKER": { "initial_ratio": 0.60, "max_rounds": 2 },
    "RELEGATION_FIGHTER": { "initial_ratio": 0.70, "max_rounds": 1 },
    "MONEY_EXPANSION": { "initial_ratio": 1.10, "max_rounds": 4 },
    "YOUTH_DEVELOPER": { "initial_ratio": 0.70, "max_rounds": 2 }
  },
  "position_depth": {
    "target_starters": 1,
    "target_backups": 1
  },
  "tactical_importance_matrix": {
    "high_press": {
      "GK": 0.7, "CB": 0.8, "LB": 0.7, "RB": 0.7,
      "DM": 0.9, "CM": 0.8, "AM": 0.6,
      "LW": 0.7, "RW": 0.7, "ST": 0.7
    },
    "possession": {
      "GK": 0.5, "CB": 0.7, "LB": 0.6, "RB": 0.6,
      "DM": 0.7, "CM": 0.9, "AM": 0.8,
      "LW": 0.6, "RW": 0.6, "ST": 0.7
    }
  },
  "constraints": {
    "maxTransfersPerWindow": 5,
    "maxYoungTalentPerClubPerWindow": 2,
    "maxCorePlayersSoldPerSeason": 2,
    "wageToIncomeDangerThreshold": 0.85
  },
  "thresholds": {
    "sellThreshold": 60,
    "managerSackBaseThreshold": 70,
    "losingStreakThreshold": 5,
    "earlyRenewPriority": 75,
    "normalRenewPriority": 55
  },
  "frequency": {
    "squad_needs_analysis": "monthly_or_weekly_in_window",
    "contract_renewal_check": "monthly",
    "transfer_target_search": "weekly_in_window",
    "offer_negotiation": "daily_in_window",
    "manager_sack_check": "monthly_or_losing_streak",
    "youth_promotion": "twice_per_season",
    "tactic_adjustment": "every_5_matches"
  }
}
```

---

## 十、5 周实现计划（W77-W81）

### W77：画像 + 短板识别

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | ClubAiProfile + ClubType + ClubProfileFactory | 5 种画像可生成 |
| D2 | ClubProfileResolver 类型判定逻辑 | 40 队画像初始化 |
| D3 | PositionDepthChecker 位置深度（6 因子） | position_need_score 可计算 |
| D4 | AgeStructureChecker + TacticalFitChecker | 年龄/战术短板可识别 |
| D5 | SquadNeedsAnalyzer 集成 + 单测 | 短板分析完整 |

**W77 验收**：豪门短板在 CB/ST，保级队短板覆盖多位置。

### W78：转会目标评分 + 报价

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | TransferTargetScorer 主公式（9 因子） | target_score 可计算 |
| D2 | 按俱乐部类型调整权重 | 5 种类型差异化 |
| D3 | OfferCalculator 估价 + 预算限制 | max_offer 可计算 |
| D4 | OfferStrategy 报价策略（4 种类型） | 豪门/黑店/保级/金元策略不同 |
| D5 | SellDecisionMaker 卖人决策（6 因子 + 特殊规则） | 卖人逻辑可用 |

**W78 验收**：豪门偏好高 CA，黑店偏好高 PA + 低身价。

### W79：续约 + 换帅

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | RenewalManager 续约评分（7 因子） | renew_priority 可计算 |
| D2 | 续约分级处理（高中低三档 + lead time） | 续约流程完整 |
| D3 | ManagerSackDecider 换帅评分（7 因子） | sack_score 可计算 |
| D4 | 董事会耐心调整阈值 + 连败触发 | 换帅逻辑可用 |
| D5 | 单测：豪门欧冠出局 + 连败触发换帅 | 换帅场景验证 |

**W79 验收**：耐心低的俱乐部换帅阈值低，连败触发换帅。

### W80：频率调度 + 防崩坏约束

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | AiScheduler 日/周/月/赛季任务调度 | 频率分批生效 |
| D2 | 转会窗期间每周目标搜索 + 每日报价 | 转会窗流程跑通 |
| D3 | AiConstraintGuard 7 条约束 | 防崩坏守卫完整 |
| D4 | 约束违规报告 + 决策日志记录 | 决策可追溯 |
| D5 | 单测：豪门不会买 6 人/窗，不会卖 3 核心 | 约束生效验证 |

**W80 验收**：每窗交易 ≤5，豪门不卖全部核心，保级队不买远超预算球星。

### W81：集成测试 + 调参

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | AiSimulator 入口门面 + 与 T17 经济模型集成 | AI 全流程跑通 |
| D2 | 跑 5 赛季模拟测试 | 无崩坏 |
| D3 | 检查豪门/保级队/黑店行为差异 | 5 种画像行为可区分 |
| D4 | 调参：解决马太效应/均质化 | 联赛格局合理 |
| D5 | 集成 T0D-1 长程测试脚本 | Gate 3 预备 |

**W81 验收**：豪门和保级队行为明显不同，无马太效应（联赛冠军不连续 5 年同一队）。

---

## 十一、测试策略

### 1. 单元测试

| 模块 | 测试用例 | 数量 |
|------|----------|------|
| ClubProfileFactory | 5 种类型判定正确、字段范围合法 | 10 |
| PositionDepthChecker | 缺主力时 need_score 高、年龄风险识别 | 12 |
| TransferTargetScorer | 9 因子权重正确、5 种类型权重调整 | 15 |
| OfferCalculator | 估价公式、预算限制、4 种报价策略 | 10 |
| SellDecisionMaker | 6 因子、队长保护、财政危机加成 | 10 |
| RenewalManager | 7 因子、3 档分级、lead time | 8 |
| ManagerSackDecider | 7 因子、耐心调整、连败触发 | 8 |
| AiConstraintGuard | 7 条约束全部覆盖 | 14 |

### 2. 集成测试（W81）

```kotlin
class AiSimulatorIntegrationTest {

    @Test
    fun `5 赛季模拟无马太效应`() {
        val simulator = createAiSimulator()
        val saveId = createTestSave()

        // 模拟 5 赛季
        repeat(5) { season ->
            simulator.simulateFullSeason(saveId, season)
        }

        // 检查：联赛冠军不连续 5 年同一队
        val champions = getChampionsBySeason(saveId, 5)
        val maxConsecutive = countMaxConsecutive(champions)
        assertTrue(maxConsecutive <= 3, "冠军连续 $maxConsecutive 年同一队，存在马太效应")
    }

    @Test
    fun `豪门不卖全部核心`() {
        val simulator = createAiSimulator()
        val saveId = createTestSave()
        val eliteClub = getEliteClub(saveId)

        simulator.simulateOneSeason(saveId)

        val corePlayersSold = getCorePlayersSold(saveId, eliteClub)
        assertTrue(corePlayersSold <= 2, "豪门卖了 $corePlayersSold 名核心，超过限制")
    }

    @Test
    fun `5 种画像行为可区分`() {
        val simulator = createAiSimulator()
        val saveId = createTestSave()

        simulator.simulateOneSeason(saveId)

        val eliteTransfers = getTransferBehavior(saveId, ClubType.ELITE_CONTENDER)
        val profitMakerTransfers = getTransferBehavior(saveId, ClubType.PROFIT_MAKER)
        val relegationTransfers = getTransferBehavior(saveId, ClubType.RELEGATION_FIGHTER)

        // 豪门买高 CA（>80），黑店买低年龄高 PA，保级队买低工资
        assertTrue(eliteTransfers.all { it.ca > 80 })
        assertTrue(profitMakerTransfers.all { it.age < 22 && it.pa > 80 })
        assertTrue(relegationTransfers.all { it.wage < 50000 })
    }
}
```

### 3. Gate 3 长程测试（W85-W86 完善）

```kotlin
class Gate3AiCriteria {

    fun verify(simulator: AiSimulator, saveId: Int): Gate3AiReport {
        // 模拟 20 年
        repeat(20) { season ->
            simulator.simulateFullSeason(saveId, season)
        }

        return Gate3AiReport(
            // 无马太效应：联赛冠军不连续 5 年同一队（允许 3 年）
            maxConsecutiveChampions = calculateMaxConsecutiveChampions(saveId),

            // 无均质化：联赛前 6 和后 6 评分差 ≥ 8
            topBottomRatingGap = calculateTopBottomGap(saveId),

            // 经济不失控：2030 年顶级转会费在 1-3 亿欧区间
            maxTransferFee2030 = getMaxTransferFeeByYear(saveId, 2030),

            // 工资/收入比 > 85% 的俱乐部 < 15%
            clubsWithHighWageRatio = countClubsAboveWageRatio(saveId, 0.85),

            // 年轻天才不全部流向同一家豪门
            maxYoungTalentPerClub = getMaxYoungTalentPerClub(saveId),

            // 豪门不卖全部核心
            maxCorePlayersSold = getMaxCorePlayersSoldPerSeason(saveId)
        )
    }
}
```

---

## 十二、关键风险与应对

### 1. 马太效应（最强队持续垄断）

- **表现**：豪门连续 5+ 年夺冠，弱队无机会
- **应对**：
  - 调 `club_type_modifiers` 让弱队更积极买人
  - 提高 `maxYoungTalentPerClubPerWindow` 限制（豪门囤才受限）
  - 给保级队增加 `wageAffordability` 权重，鼓励捡漏
- **红线**：不直接给弱队加成，通过 AI 决策权重调整

### 2. 均质化（所有球队实力接近）

- **表现**：联赛前 6 和后 6 评分差 < 8
- **应对**：
  - 检查豪门是否在卖核心（约束 3 失效）
  - 提高豪门的 `starPreference`，鼓励买球星
  - 降低保级队的 `potentialFit`，避免捡到小妖变强

### 3. 经济失控

- **表现**：2030 年转会费 > 3 亿或 < 1 亿
- **应对**：
  - 联动 T17 经济模型，调 `football_economy_index` 年增长率
  - 加严 `wageToIncomeDangerThreshold` 到 0.80
  - 限制豪门每窗交易数到 3

### 4. AI 决策卡顿

- **表现**：每日推进 P95 > 3 秒
- **应对**：
  - 严格按频率分批（月度/周度/每日），不全量计算
  - 转会目标搜索只查 top need 位置，不全位置扫描
  - 候选球员缓存到 cache.db，避免每次全表扫描
  - 非玩家关注联赛用简化 AI（只看预算和位置缺口）

### 5. 蝴蝶效应级联崩塌

- **表现**：一次关键转会打断后，AI 决策连锁触发无数交易
- **应对**：
  - 联动 T20 蝴蝶效应，影响预算 ≤200 节点/赛季
  - `AiConstraintGuard` 每笔交易后重算预算
  - 失败回退：维持现有阵容，不强造替代

---

## 十三、与其他任务的衔接

| 上游任务 | 依赖点 | 衔接方式 |
|----------|--------|----------|
| T13 AI 基础转会 | 基础买卖逻辑 | T18 在此基础上加画像 + 短板 + 约束 |
| T17 经济模型 | 身价/工资计算 | economyService.estimateMarketValue / calculateExpectedWage |
| T03 存档系统 | save.db 读写 | ClubAiProfileDao / AiDecisionLogDao |
| T07 每日推进 | 日期推进触发 | AiScheduler.onDailyAdvance 由 T07 调用 |

| 下游任务 | 依赖点 | 衔接方式 |
|----------|--------|----------|
| T19 赛季归档 | AI 决策日志归档 | season_archive 包含 ai_decision_log 摘要 |
| T0D-1 长程测试 | 20 年模拟 | AiSimulator.simulateFullSeason |
| Gate 3 | 全部 AI 约束生效 | Gate3AiReport 校验所有指标 |
| T20 蝴蝶效应 | AI 决策可被打断 | butterfly_event 触发后 AiScheduler 响应 |

---

## 十四、V1 范围明确（不做的）

**V1 做**：
- 5 种俱乐部画像 + 10 字段
- 阵容短板识别（位置/年龄/战术）
- 转会目标评分（9 因子 + 类型调整）
- 报价策略（4 种类型）
- 卖人决策（6 因子 + 特殊规则）
- 续约（7 因子 + 3 档分级）
- 换帅（7 因子 + 耐心调整 + 连败触发）
- 频率分批调度（日/周/月/赛季）
- 防崩坏 7 条约束
- 决策日志记录

**V1 不做**（推迟到 M7+）：
- AI 学习玩家行为
- AI 战术针对性调整（只做每 5 场通用调整）
- AI 青训学院的详细培养路径决策
- AI 球探网络建设（只做基础搜索）
- AI 媒体发言（T24 媒体系统独立做）
- AI 长期战略规划（5 年计划等）

---

*本方案严格遵循 V0.2 `05_AI俱乐部决策模型.md`，5 种画像 + 9 因子评分 + 7 条防崩坏约束全部配置化，确保 20 年长程模拟后世界仍然"像足球"。*
