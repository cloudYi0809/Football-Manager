# T13 AI 基础转会 - 详细实现方案

> 任务编号：T13
> 周期：W47-W49（2.5 周）
> 优先级：P0（C 轨核心，依赖 T11 + T12，T18 的基础版）
> 依据：V0.2 `05_AI俱乐部决策模型.md`（基础版部分）+ V0.1 `09_转会_合同_经纪人系统.md`
> 铁律：基础版不区分画像类型 / 阵容短板识别 6 因子 / 转会目标 9 因子 / 防崩坏 3 条
> 定位：C 轨收尾，T18 的简化版，让 AI 俱乐部能买卖球员

---

## 一、整体架构

### 1. 基础转会流水线

```
AiTransferEngine（入口，由 T07 每日推进调用）
    │
    ├── SquadNeedsAnalyzer          # 阵容短板识别
    │   └── position_need_score（6 因子）
    │
    ├── TransferTargetScorer        # 转会目标评分
    │   └── target_score（9 因子，基础版不区分类型）
    │
    ├── OfferCalculator             # 报价计算
    │   └── max_offer + 预算限制
    │
    ├── SellDecisionMaker           # 卖人决策
    │   └── sell_score（6 因子）
    │
    └── ConstraintGuard             # 防崩坏（基础版 3 条）
        └── 窗口上限/预算/位置优先
```

### 2. 与 T18 的区别

| 维度 | T13 基础版 | T18 完整版 |
|------|-----------|-----------|
| 俱乐部画像 | 不区分类型 | 5 种画像 |
| 评分权重 | 固定权重 | 按类型动态调整 |
| 报价策略 | 统一 80% 市值 | 4 种策略 |
| 卖人决策 | 6 因子无特殊规则 | 6 因子 + 5 条特殊规则 |
| 续约 | 简化（合同到期放走） | 7 因子 + 3 档分级 |
| 换帅 | 不做 | 7 因子 + 耐心调整 |
| 防崩坏 | 3 条约束 | 7 条约束 |
| 频率调度 | 转会窗每日 | 日/周/月/赛季分批 |

### 3. 包结构

```
com.greenDynasty.ai.basic
├── api/
│   ├── AiTransferEngine.kt           # 入口
│   └── AiTransferResult.kt
├── needs/
│   └── BasicSquadNeedsAnalyzer.kt    # 短板识别
├── target/
│   └── BasicTransferTargetScorer.kt  # 目标评分
├── offer/
│   └── BasicOfferCalculator.kt       # 报价计算
├── sell/
│   └── BasicSellDecisionMaker.kt     # 卖人决策
├── guard/
│   └── BasicConstraintGuard.kt       # 防崩坏（3 条）
└── config/
    └── BasicAiConfig.kt
```

---

## 二、核心数据类

```kotlin
// api/AiTransferResult.kt
data class AiTransferResult(
    val clubId: Int,
    val actions: List<AiTransferAction>,
    val budgetUsed: Int,
    val budgetRemaining: Int
)

data class AiTransferAction(
    val type: AiActionType,
    val playerId: Int,
    val targetClubId: Int?,
    val fee: Int,
    val reason: String
)

enum class AiActionType {
    BUY,            // 买入
    SELL,           // 卖出
    LOAN_OUT,       // 外租
    RELEASE         // 释放
}
```

---

## 三、阵容短板识别（基础版）

```kotlin
// needs/BasicSquadNeedsAnalyzer.kt
class BasicSquadNeedsAnalyzer(
    private val saveDb: SaveDatabase,
    private val config: BasicAiConfig
) {

    /**
     * V0.2 05 §四 位置需求评分（6 因子，基础版）
     * position_need_score =
     *   starter_gap * 0.40
     * + backup_gap * 0.20
     * + average_age_risk * 0.15
     * + injury_risk * 0.10
     * + contract_expiry_risk * 0.10
     * + tactical_importance * 0.05
     */
    fun analyze(saveId: Int, clubId: Int): List<PositionNeed> {
        val players = saveDb.savePlayerStateDao().getByClub(saveId, clubId)
        val positions = Position.values().toList()

        return positions.map { pos ->
            val posPlayers = players.filter { it.primaryPosition == pos }

            val starterGap = calculateStarterGap(posPlayers)
            val backupGap = calculateBackupGap(posPlayers)
            val ageRisk = calculateAgeRisk(posPlayers)
            val injuryRisk = calculateInjuryRisk(posPlayers)
            val contractRisk = calculateContractExpiryRisk(posPlayers)
            val tacticalImportance = 0.5  // 基础版固定 0.5，T18 才按战术区分

            val needScore = (starterGap * 0.40 +
                            backupGap * 0.20 +
                            ageRisk * 0.15 +
                            injuryRisk * 0.10 +
                            contractRisk * 0.10 +
                            tacticalImportance * 0.05) * 100

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
        }.filter { it.needScore > 30 }  // 只处理急需位置
         .sortedByDescending { it.needScore }
    }

    private fun calculateStarterGap(players: List<SavePlayerStateWithAttributes>): Double {
        val targetStarters = 1
        val actualStarters = players.count { it.squadRole == "starter" }
        val gap = targetStarters - actualStarters
        return when {
            gap > 0 -> gap.toDouble()
            gap == 0 -> 0.0
            else -> -1.0  // 多余可卖
        }
    }

    private fun calculateBackupGap(players: List<SavePlayerStateWithAttributes>): Double {
        val targetBackups = 1
        val actualBackups = players.count { it.squadRole in listOf("backup", "rotation") }
        val gap = targetBackups - actualBackups
        return when {
            gap > 0 -> gap.toDouble()
            gap == 0 -> 0.0
            else -> -1.0
        }
    }

    private fun calculateAgeRisk(players: List<SavePlayerStateWithAttributes>): Double {
        if (players.isEmpty()) return 1.0
        val avgAge = players.map { calculateAge(it.birthDate) }.average()
        return when {
            avgAge > 32 -> 0.9
            avgAge < 19 -> 0.7
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
        val now = LocalDate.now()
        val expiringCount = players.count { p ->
            p.contractUntil?.let { LocalDate.parse(it).isBefore(now.plusMonths(12)) } ?: true
        }
        return (expiringCount.toDouble() / players.size).coerceIn(0.0, 1.0)
    }
}
```

---

## 四、转会目标评分（基础版）

```kotlin
// target/BasicTransferTargetScorer.kt
class BasicTransferTargetScorer(
    private val economyService: EconomyService,           // T17
    private val config: BasicAiConfig
) {

    /**
     * V0.2 05 §五 target_score（9 因子，基础版固定权重）
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
     *
     * 基础版不按俱乐部类型调整权重（T18 才做）
     */
    fun score(
        candidate: PlayerCandidate,
        positionNeed: PositionNeed,
        clubFinancial: ClubFinancialState
    ): TransferTarget {
        val positionNeedScore = positionNeed.needScore / 100.0
        val currentAbilityFit = calculateCurrentAbilityFit(candidate, clubFinancial)
        val potentialFit = calculatePotentialFit(candidate)
        val priceValue = calculatePriceValue(candidate, clubFinancial)
        val wageAffordability = calculateWageAffordability(candidate, clubFinancial)
        val ageFit = calculateAgeFit(candidate)
        val tacticalFit = 0.5  // 基础版固定 0.5
        val nationalityFit = 0.5  // 基础版固定 0.5
        val commercialValue = calculateCommercialValue(candidate)

        val w = config.targetScoreWeights
        val targetScore = (
            positionNeedScore * w.positionNeed +
            currentAbilityFit * w.currentAbilityFit +
            potentialFit * w.potentialFit +
            priceValue * w.priceValue +
            wageAffordability * w.wageAffordability +
            ageFit * w.ageFit +
            tacticalFit * w.tacticalFit +
            nationalityFit * w.nationalityFit +
            commercialValue * w.commercialValue
        ) * 100

        val estimatedValue = economyService.estimateMarketValue(candidate.toPlayerState())
        val maxOffer = calculateMaxOffer(candidate, estimatedValue, clubFinancial)
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
     * 基础版：期望 CA = 75（T18 才按俱乐部类型区分）
     */
    private fun calculateCurrentAbilityFit(c: PlayerCandidate, f: ClubFinancialState): Double {
        val expectedCa = 75
        return (1.0 - abs(c.currentCa - expectedCa) / 30.0).coerceIn(0.0, 1.0)
    }

    private fun calculatePotentialFit(c: PlayerCandidate): Double {
        val potentialFactor = (c.potentialPa - c.currentCa) / 30.0
        return potentialFactor.coerceIn(0.0, 1.0)
    }

    private fun calculatePriceValue(c: PlayerCandidate, f: ClubFinancialState): Double {
        val ratio = c.askingPrice.toDouble() / f.transferBudgetRemaining.coerceAtLeast(1)
        return (1.0 - ratio).coerceIn(0.0, 1.0)
    }

    private fun calculateWageAffordability(c: PlayerCandidate, f: ClubFinancialState): Double {
        val ratio = c.expectedWage.toDouble() / f.wageBudgetRemaining.coerceAtLeast(1)
        return (1.0 - ratio).coerceIn(0.0, 1.0)
    }

    private fun calculateAgeFit(c: PlayerCandidate): Double {
        return if (c.age in 20..28) 1.0 else 0.5
    }

    private fun calculateCommercialValue(c: PlayerCandidate): Double {
        return (c.reputation / 100.0).coerceIn(0.0, 1.0)
    }

    /**
     * 基础版：统一 80% 市值报价（T18 才按俱乐部类型区分）
     */
    private fun calculateMaxOffer(
        candidate: PlayerCandidate,
        marketValue: Int,
        financial: ClubFinancialState
    ): Int {
        var maxOffer = (marketValue * 0.80).toInt()  // 统一 80%

        // 预算限制
        if (maxOffer > financial.transferBudgetRemaining) {
            maxOffer = financial.transferBudgetRemaining
        }

        return maxOffer.coerceAtLeast(0)
    }
}
```

---

## 五、卖人决策（基础版）

```kotlin
// sell/BasicSellDecisionMaker.kt
class BasicSellDecisionMaker(
    private val economyService: EconomyService,
    private val config: BasicAiConfig
) {

    /**
     * V0.2 05 §七 卖人决策（6 因子，基础版无特殊规则）
     * sell_score =
     *   offer_value_ratio * 0.25
     * + player_unhappy * 0.20
     * + contract_expiry_risk * 0.15
     * + squad_depth_cover * 0.15
     * + financial_pressure * 0.15
     * + age_decline_risk * 0.10
     *
     * 基础版不包含队长保护/核心保护等特殊规则（T18 才做）
     */
    fun shouldSell(
        player: SavePlayerStateWithAttributes,
        offerAmount: Int,
        marketValue: Int,
        financial: ClubFinancialState,
        squadDepth: Map<Position, Int>
    ): SellDecision {
        val offerValueRatio = (offerAmount.toDouble() / marketValue.coerceAtLeast(1))
            .coerceIn(0.0, 2.0)

        val playerUnhappy = when {
            player.morale < 30 -> 1.0
            player.morale < 50 -> 0.5
            else -> 0.0
        }

        val contractExpiryRisk = calculateContractExpiryRisk(player)
        val squadDepthCover = squadDepth[player.primaryPosition]?.let {
            (it / 3.0).coerceIn(0.0, 1.0)
        } ?: 0.0

        val financialPressure = when {
            financial.wageToIncomeRatio > 0.85 -> 1.0
            financial.wageToIncomeRatio > 0.70 -> 0.5
            else -> 0.0
        }

        val ageDeclineRisk = calculateAgeDeclineRisk(player)

        val sellScore = (
            offerValueRatio * 0.25 +
            playerUnhappy * 0.20 +
            contractExpiryRisk * 0.15 +
            squadDepthCover * 0.15 +
            financialPressure * 0.15 +
            ageDeclineRisk * 0.10
        ) * 100

        val shouldSell = sellScore >= config.sellThreshold  // 默认 60

        return SellDecision(
            playerId = player.playerId,
            sellScore = sellScore,
            shouldSell = shouldSell,
            reason = "基础评分 ${sellScore}"
        )
    }

    private fun calculateContractExpiryRisk(player: SavePlayerStateWithAttributes): Double {
        val contractUntil = player.contractUntil ?: return 1.0
        val monthsLeft = java.time.Period.between(
            LocalDate.now(),
            LocalDate.parse(contractUntil)
        ).months
        return when {
            monthsLeft <= 0 -> 1.0
            monthsLeft <= 6 -> 0.8
            monthsLeft <= 12 -> 0.5
            else -> 0.1
        }
    }

    private fun calculateAgeDeclineRisk(player: SavePlayerStateWithAttributes): Double {
        val age = calculateAge(player.birthDate)
        return when {
            age > 33 -> 0.9
            age > 30 -> 0.5
            else -> 0.1
        }
    }
}
```

---

## 六、防崩坏约束（基础版 3 条）

```kotlin
// guard/BasicConstraintGuard.kt
class BasicConstraintGuard(
    private val saveDb: SaveDatabase,
    private val config: BasicAiConfig
) {

    /**
     * 基础版 3 条约束（T18 扩展到 7 条）
     * 1. 每窗交易数量限制
     * 2. 预算限制
     * 3. 位置短板优先级
     */
    fun checkBeforeTransfer(
        target: TransferTarget,
        club: SaveClubStateEntity,
        saveId: Int,
        topNeedPosition: Position?
    ): ConstraintViolation? {

        // 1. 窗口交易数量限制
        val windowTxCount = saveDb.saveTransferOfferDao()
            .countWindowTransactions(saveId, club.clubId, currentTransferWindow())
        if (windowTxCount >= config.maxTransfersPerWindow) {  // 默认 5
            return ConstraintViolation("WINDOW_LIMIT", "本窗已交易 $windowTxCount 次")
        }

        // 2. 预算限制
        if (target.maxOffer > club.transferBudget) {
            return ConstraintViolation("BUDGET", "报价超过预算")
        }

        // 3. 位置优先级（基础版简化：只检查是否是最急需位置）
        val targetPosition = getTargetPosition(target.playerId)
        if (topNeedPosition != null && targetPosition != topNeedPosition) {
            // 非急需位置，要求 target_score 更高
            if (target.targetScore < 75) {
                return ConstraintViolation("POSITION_PRIORITY", "非急需位置，评分不足")
            }
        }

        return null
    }
}
```

---

## 七、入口引擎

```kotlin
// api/AiTransferEngine.kt
class AiTransferEngine(
    private val needsAnalyzer: BasicSquadNeedsAnalyzer,
    private val targetScorer: BasicTransferTargetScorer,
    private val offerCalculator: BasicOfferCalculator,
    private val sellDecisionMaker: BasicSellDecisionMaker,
    private val constraintGuard: BasicConstraintGuard,
    private val saveDb: SaveDatabase,
    private val economyService: EconomyService,
    private val config: BasicAiConfig
) {

    /**
     * 转会窗期间每日执行（由 T07 AiClubTask 调用）
     */
    suspend fun processDailyTransfers(ctx: AdvanceContext) {
        if (!ctx.isTransferWindowOpen) return

        val activeClubs = saveDb.saveClubStateDao().getByLeagues(ctx.saveId, ctx.activeLeagueIds)

        for (club in activeClubs) {
            // 玩家俱乐部不自动操作
            if (club.clubId == ctx.managerClubId) continue

            processClubTransfers(club, ctx)
        }
    }

    private suspend fun processClubTransfers(
        club: SaveClubStateEntity,
        ctx: AdvanceContext
    ): AiTransferResult {
        val actions = mutableListOf<AiTransferAction>()
        val financial = buildFinancialState(club, ctx)

        // 1. 阵容短板分析
        val needs = needsAnalyzer.analyze(ctx.saveId, club.clubId)
        val topNeed = needs.firstOrNull()

        // 2. 买入决策（如有短板）
        if (topNeed != null && topNeed.needScore > 50) {
            val buyAction = tryBuyPlayer(club, topNeed, financial, ctx)
            if (buyAction != null) actions.add(buyAction)
        }

        // 3. 卖人决策（检查合同到期/高薪/老将）
        val sellActions = trySellPlayers(club, financial, ctx)
        actions.addAll(sellActions)

        // 4. 更新预算
        val totalSpent = actions.filter { it.type == AiActionType.BUY }.sumOf { it.fee }
        val totalEarned = actions.filter { it.type == AiActionType.SELL }.sumOf { it.fee }
        val updatedBudget = club.transferBudget - totalSpent + totalEarned
        saveDb.saveClubStateDao().updateTransferBudget(club.clubId, ctx.saveId, updatedBudget)

        return AiTransferResult(
            clubId = club.clubId,
            actions = actions,
            budgetUsed = totalSpent,
            budgetRemaining = updatedBudget
        )
    }

    private suspend fun tryBuyPlayer(
        club: SaveClubStateEntity,
        topNeed: PositionNeed,
        financial: ClubFinancialState,
        ctx: AdvanceContext
    ): AiTransferAction? {
        // 1. 搜索候选
        val candidates = searchCandidatesByPosition(topNeed.position, ctx.saveId)
        if (candidates.isEmpty()) return null

        // 2. 评分
        val scoredTargets = candidates.map { c ->
            targetScorer.score(c, topNeed, financial)
        }.filter { it.isAffordable }
         .sortedByDescending { it.targetScore }

        // 3. 选择最高分
        val target = scoredTargets.firstOrNull() ?: return null

        // 4. 约束检查
        val violation = constraintGuard.checkBeforeTransfer(
            target, club, ctx.saveId, topNeed.position
        )
        if (violation != null) return null

        // 5. 执行转会
        executeTransfer(target, club, ctx)

        return AiTransferAction(
            type = AiActionType.BUY,
            playerId = target.playerId,
            targetClubId = club.clubId,
            fee = target.maxOffer,
            reason = "补强 ${topNeed.position}（评分 ${target.targetScore}）"
        )
    }

    private suspend fun trySellPlayers(
        club: SaveClubStateEntity,
        financial: ClubFinancialState,
        ctx: AdvanceContext
    ): List<AiTransferAction> {
        val actions = mutableListOf<AiTransferAction>()
        val players = saveDb.savePlayerStateDao().getByClub(ctx.saveId, club.clubId)
        val squadDepth = calculateSquadDepth(players)

        for (player in players) {
            // 检查是否应该卖
            val marketValue = economyService.estimateMarketValue(player)
            val offerAmount = (marketValue * 0.8).toInt()  // 模拟报价

            val decision = sellDecisionMaker.shouldSell(
                player, offerAmount, marketValue, financial, squadDepth
            )

            if (decision.shouldSell) {
                executeSell(player, club, offerAmount, ctx)
                actions.add(AiTransferAction(
                    type = AiActionType.SELL,
                    playerId = player.playerId,
                    targetClubId = null,  // 买方不确定
                    fee = offerAmount,
                    reason = decision.reason
                ))
            }
        }

        return actions
    }

    private suspend fun executeTransfer(
        target: TransferTarget,
        club: SaveClubStateEntity,
        ctx: AdvanceContext
    ) {
        // 更新球员俱乐部
        saveDb.savePlayerStateDao().updateClub(target.playerId, ctx.saveId, club.clubId)

        // 记录转会
        saveDb.saveTransferOfferDao().insert(SaveTransferOfferEntity(
            offerId = 0,
            saveId = ctx.saveId,
            playerId = target.playerId,
            fromClubId = null,
            toClubId = club.clubId,
            offerType = "transfer",
            fee = target.maxOffer,
            wageOffer = 0,
            contractYears = 4,
            status = "completed",
            createdDate = ctx.currentDate.toString(),
            expiresDate = null
        ))
    }
}
```

---

## 八、配置化参数

### basic_ai_config.json

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
  "offer": {
    "initial_offer_ratio": 0.80,
    "max_negotiation_rounds": 2
  },
  "constraints": {
    "max_transfers_per_window": 5
  },
  "thresholds": {
    "sell_threshold": 60,
    "need_score_action_threshold": 50,
    "target_score_action_threshold": 75
  },
  "search": {
    "max_candidates_per_position": 20
  }
}
```

---

## 九、2.5 周实现计划（W47-W49）

### W47：短板识别 + 目标评分

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | BasicSquadNeedsAnalyzer（6 因子） | 短板可识别 |
| D2 | BasicTransferTargetScorer（9 因子） | 目标可评分 |
| D3 | 搜索候选球员 | 候选可搜索 |
| D4 | 单测：短板识别 + 目标评分 | 评分验收 |
| D5 | 集成 T17 经济模型 | 身价/工资可用 |

**W47 验收**：能识别阵容短板并评分候选球员。

### W48：报价 + 卖人 + 约束

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | BasicOfferCalculator（80% 市值） | 报价可计算 |
| D2 | BasicSellDecisionMaker（6 因子） | 卖人可决策 |
| D3 | BasicConstraintGuard（3 条约束） | 防崩坏生效 |
| D4 | 执行转会/卖人流程 | 转会可执行 |
| D5 | 单测：约束拦截 + 转会完成 | 约束验收 |

**W48 验收**：AI 能完成买卖球员全流程，约束生效。

### W49：集成 + 测试

| 天 | 任务 | 产出 |
|----|------|------|
| D1 | AiTransferEngine 入口 + 集成 T07 | 转会窗每日执行 |
| D2 | 集成测试：1 转会窗模拟 | 无荒谬交易 |
| D3 | 调参：解决交易过频/过少 | 交易量合理 |
| D4 | 测试 10 俱乐部转会窗 | 行为可区分 |
| D5 | Gate 2 预备 | 赛季闭环 |

**W49 验收**：1 转会窗 10 俱乐部交易量合理（每队 0-3 笔），无荒谬大规模交易。

---

## 十、关键风险与应对

### 1. 交易过频

- **表现**：每队每窗交易 10+ 笔
- **应对**：
  - 严格 max_transfers_per_window=5
  - 提高 need_score_action_threshold 到 60
  - 检查约束是否生效

### 2. 交易过少

- **表现**：转会窗无人交易
- **应对**：
  - 降低 need_score_action_threshold 到 40
  - 检查候选搜索是否返回结果
  - 检查预算是否足够

### 3. 荒谬交易

- **表现**：保级队买 C 罗
- **应对**：
  - 检查预算限制
  - 提高 target_score_action_threshold
  - T18 阶段引入俱乐部类型区分

---

## 十一、与其他任务的衔接

| 上游任务 | 依赖点 | 衔接方式 |
|----------|--------|----------|
| T11 报价谈判 | 报价逻辑 | BasicOfferCalculator 基础 |
| T12 合同续约 | 工资计算 | economyService.calculateExpectedWage |
| T17 经济模型 | 身价/工资 | economyService.estimateMarketValue |
| T07 每日推进 | 转会窗触发 | AiClubTask 调用 processDailyTransfers |

| 下游任务 | 依赖点 | 衔接方式 |
|----------|--------|----------|
| T18 AI 完整版 | 基础逻辑 | T18 在此基础上加画像+约束 |
| Gate 2 | 转会窗可用 | 1 赛季转会闭环 |

---

## 十二、V1 范围明确（不做的）

**V1 做**：
- 阵容短板识别（6 因子，固定战术重要性）
- 转会目标评分（9 因子，固定权重）
- 报价计算（统一 80% 市值）
- 卖人决策（6 因子，无特殊规则）
- 防崩坏 3 条约束
- 转会窗每日执行

**V1 不做**（T18 做）：
- 5 种俱乐部画像
- 按类型调整权重
- 4 种报价策略
- 卖人 5 条特殊规则
- 续约 3 档分级
- 换帅逻辑
- 防崩坏 7 条约束
- 频率分批调度

---

*本方案是 T18 的简化版，提供基础 AI 转会能力，让 Gate 2 赛季闭环时 AI 俱乐部能买卖球员。T18 在此基础上扩展画像和约束。*
