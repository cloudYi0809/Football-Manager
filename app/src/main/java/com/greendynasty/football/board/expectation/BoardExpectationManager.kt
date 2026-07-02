package com.greendynasty.football.board.expectation

import com.greendynasty.football.ai.profile.model.ClubType
import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.board.model.BoardConfig
import com.greendynasty.football.board.model.BoardExpectationSummary
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.economy.repository.EconomyRepository
import java.time.LocalDate

/**
 * T22 董事会期望管理器（V0.2 05 §九 + 11 §四 + T22 方案 §一.1）。
 *
 * 职责：
 * 1. 根据俱乐部声望 / 财政 / 历史成绩设定赛季期望
 * 2. 与 T18 俱乐部画像协调：董事会野心 = ClubProfile.ambition（误差 ≤ 10，铁律）
 * 3. 与 T17 经济模型协调：财政目标 = wageToIncomeRatio ≤ 阈值
 *
 * 期望管理是目标设定的输入：[BoardExpectationSummary] 提供给
 * [com.greendynasty.football.board.objective.SeasonObjectiveSetter] 生成具体赛季目标。
 *
 * @param databaseManager 三库管理入口
 * @param clubProfileRepository T18 俱乐部画像仓库
 * @param economyRepository T17 经济仓库
 * @param config 董事会配置
 */
class BoardExpectationManager(
    private val databaseManager: DatabaseManager,
    private val clubProfileRepository: ClubProfileRepository,
    private val economyRepository: EconomyRepository,
    private val config: BoardConfig = BoardConfig.DEFAULT
) {

    /**
     * 计算俱乐部董事会的期望摘要。
     *
     * 规则（V0.2 11 §四）：
     * - ambition = ClubProfile.ambition（董事会野心 = 俱乐部画像野心）
     * - patience = ClubProfile.patienceWithManager
     * - financialStyle 由 wageStrictness 决定（>70 CONSERVATIVE，<40 AGGRESSIVE，否则 BALANCED）
     * - wageRatioTarget 由 ClubType 决定（豪门 0.70 / 黑店 0.65 / 青训型 0.75 / 金元 0.80 / 保级 0.90）
     * - expectedLeaguePosition 由 ambition + 上赛季排名推导
     * - expectedCupRound 由 ambition 决定（>75 WIN，>60 FINAL，>45 SEMI_FINAL，否则 QUARTER_FINAL）
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentSeasonId 当前赛季 ID（用于查询上赛季排名）
     * @param currentDate 当前游戏日期（用于经济指数查询）
     * @return 期望摘要，俱乐部画像不存在时返回 null
     */
    suspend fun computeExpectation(
        saveId: Int,
        clubId: Int,
        currentSeasonId: Int,
        currentDate: LocalDate
    ): BoardExpectationSummary? {
        val profile = clubProfileRepository.getProfile(clubId) ?: return null

        // 财政目标：根据 ClubType 决定工资/收入比上限
        val wageRatioTarget = calculateWageRatioTarget(profile.clubType)

        // 财政风格：由 wageStrictness 决定
        val financialStyle = when {
            profile.wageStrictness > 70 -> "CONSERVATIVE"
            profile.wageStrictness < 40 -> "AGGRESSIVE"
            else -> "BALANCED"
        }

        // 期望联赛排名：由 ambition + 上赛季排名推导
        val lastSeasonPosition = getLastSeasonPosition(saveId, clubId, currentSeasonId - 1)
        val expectedLeaguePosition = calculateExpectedLeaguePosition(profile.ambition, lastSeasonPosition)

        // 期望杯赛轮次：由 ambition 决定
        val expectedCupRound = when {
            profile.ambition > 75 -> "WIN"
            profile.ambition > 60 -> "FINAL"
            profile.ambition > 45 -> "SEMI_FINAL"
            else -> "QUARTER_FINAL"
        }

        return BoardExpectationSummary(
            clubId = clubId,
            ambition = profile.ambition,
            patience = profile.patienceWithManager,
            financialStyle = financialStyle,
            wageRatioTarget = wageRatioTarget,
            expectedLeaguePosition = expectedLeaguePosition,
            expectedCupRound = expectedCupRound
        )
    }

    /**
     * 根据 ClubType 计算工资/收入比上限（V0.2 + T22 方案 §四.2）。
     *
     * | ClubType | wageRatioTarget |
     * | ELITE_CONTENDER | 0.70 |
     * | MONEY_EXPANSION | 0.80 |
     * | YOUTH_DEVELOPER | 0.75 |
     * | PROFIT_MAKER | 0.65 |
     * | RELEGATION_FIGHTER | 0.90 |
     */
    private fun calculateWageRatioTarget(clubType: ClubType): Double = when (clubType) {
        ClubType.ELITE_CONTENDER -> 0.70
        ClubType.MONEY_EXPANSION -> 0.80
        ClubType.YOUTH_DEVELOPER -> 0.75
        ClubType.PROFIT_MAKER -> 0.65
        ClubType.RELEGATION_FIGHTER -> 0.90
    }

    /**
     * 计算期望联赛排名（V0.2 + T22 方案 §四.2 calculateLeaguePositionTarget）。
     *
     * | 上赛季排名 | 野心 >75 | 野心 50-75 | 野心 <50 |
     * | 1-4       | 提升 1 位 | 维持     | 维持    |
     * | 5-10      | 前 4    | 前 6     | 前 10   |
     * | 11-17     | 前 10   | 前 12    | 保级（17）|
     * | 18-20     | 前 12   | 前 15    | 保级（17）|
     * | null      | 前 4    | 前 8     | 前 15   |
     */
    private fun calculateExpectedLeaguePosition(ambition: Int, lastPosition: Int?): Int {
        return when {
            lastPosition == null -> when { // 新赛季首季
                ambition > 75 -> 4
                ambition > 50 -> 8
                else -> 15
            }
            lastPosition <= 4 -> when {
                ambition > 75 -> (lastPosition - 1).coerceAtLeast(1)
                ambition > 50 -> lastPosition
                else -> lastPosition
            }
            lastPosition in 5..10 -> when {
                ambition > 75 -> 4
                ambition > 50 -> 6
                else -> 10
            }
            lastPosition in 11..17 -> when {
                ambition > 75 -> 10
                ambition > 50 -> 12
                else -> 17
            }
            else -> when { // 降级队
                ambition > 75 -> 12
                ambition > 50 -> 15
                else -> 17
            }
        }
    }

    /**
     * 查询上赛季最终联赛排名。
     *
     * V1 简化：从 save_league_table 表查询 (saveId, lastSeasonId) 下俱乐部的 position 字段。
     * V2 可接入 T19 赛季归档的精确数据。
     *
     * @return 上赛季排名（1-20），未找到返回 null
     */
    private suspend fun getLastSeasonPosition(saveId: Int, clubId: Int, lastSeasonId: Int): Int? {
        if (lastSeasonId <= 0) return null
        return try {
            // V1 简化：查询所有赛事积分榜，取第一个 position > 0 的记录
            // 默认查询 competitionId = 1（联赛）
            val leagueEntry = databaseManager.saveLeagueTableDao()
                .getByClub(saveId, lastSeasonId, 1, clubId)
            leagueEntry?.position?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 检查俱乐部上赛季是否获得欧战资格（V0.2 + T22 方案 §四.2）。
     *
     * V1 简化：联赛排名前 4 视为获得欧冠资格，5-6 名获得欧联资格。
     *
     * @return true 表示有欧战资格
     */
    suspend fun hasEuropeanQualification(saveId: Int, clubId: Int, lastSeasonId: Int): Boolean {
        val lastPosition = getLastSeasonPosition(saveId, clubId, lastSeasonId) ?: return false
        return lastPosition <= 6
    }
}
