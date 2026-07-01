package com.greendynasty.football.match.api

import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.core.CalibrationLayer
import com.greendynasty.football.match.core.EventLayer
import com.greendynasty.football.match.core.PoissonLayer
import com.greendynasty.football.match.core.RatingLayer
import com.greendynasty.football.match.core.XGLayer
import com.greendynasty.football.match.model.MatchResult
import com.greendynasty.football.match.model.MatchStatistics
import com.greendynasty.football.match.template.StarTemplateRegistry
import kotlin.random.Random

/**
 * 比赛模拟入口门面（T02 方案 §八）
 *
 * 串联四层流水线：
 *   RatingLayer → XGLayer → PoissonLayer → EventLayer → CalibrationLayer
 *
 * 可复现：使用 [MatchInput.randomSeed] 派生 [Random]，
 * 同一输入始终产出同一结果。
 *
 * 使用：
 * ```
 * val simulator = MatchSimulator()
 * val result = simulator.simulate(input)
 * ```
 */
class MatchSimulator(
    private val config: MatchConfig = MatchConfig.DEFAULT,
    private val starTemplateRegistry: StarTemplateRegistry = StarTemplateRegistry()
) {

    // 四层流水线（懒初始化，复用 config 与 registry）
    private val ratingLayer: RatingLayer by lazy { RatingLayer(config) }
    private val xgLayer: XGLayer by lazy { XGLayer(config) }
    private val poissonLayer: PoissonLayer by lazy { PoissonLayer(config) }
    private val eventLayer: EventLayer by lazy { EventLayer(config, starTemplateRegistry) }

    /**
     * 模拟一场比赛。
     *
     * @param input 比赛输入（双方阵容、战术、赛事上下文、随机种子）
     * @return 完整比赛结果（比分 / xG / 事件 / 评分 / 统计 / 伤病 / 牌）
     */
    suspend fun simulate(input: MatchInput): MatchResult {
        // 可复现随机源：从 randomSeed 派生
        val random = Random(input.randomSeed)

        // 1. 评分层：计算双方 attack / defense / control
        val (homeRating, awayRating) = ratingLayer.rate(input)

        // 2. xG 层：强弱映射为预期进球
        val xg = xgLayer.generate(homeRating, awayRating, input)

        // 3. 泊松进球层：生成进球数 + 极端比分抑制
        val score = poissonLayer.simulate(xg, random)

        // 4. 事件层：18 tick 事件流 + 球星模板
        val events = eventLayer.generate(
            input = input,
            xg = xg,
            score = score,
            random = random
        )

        // 5. 校准层：球员评分 + 统计 + 极端比分二次抑制
        val calibrationLayer = CalibrationLayer(
            config = config,
            homePlayers = input.homeTeam.starting11,
            awayPlayers = input.awayTeam.starting11
        )

        // 构建初步 MatchResult，交由校准层补全
        val preliminary = MatchResult(
            matchId = input.matchId,
            homeScore = score.first,
            awayScore = score.second,
            homeXg = xg.homeXg,
            awayXg = xg.awayXg,
            events = events,
            playerRatings = emptyMap(),
            homeStats = MatchStatistics(0.5, 0, 0, 0, 0, 0, 0, 0.75),
            awayStats = MatchStatistics(0.5, 0, 0, 0, 0, 0, 0, 0.75),
            injuries = emptyList(),
            cards = emptyList(),
            calibrated = false
        )

        return calibrationLayer.calibrate(preliminary, xg)
    }
}
