package com.greendynasty.football.economy.repository

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.economy.calculator.ClubFinancialPowerCalculator
import com.greendynasty.football.economy.calculator.FinancialHealthChecker
import com.greendynasty.football.economy.calculator.PlayerValueCalculator
import com.greendynasty.football.economy.calculator.WageCalculator
import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.index.EconomyIndexService
import com.greendynasty.football.economy.league.LeagueEconomyService
import com.greendynasty.football.economy.model.ClubFinancialState
import com.greendynasty.football.economy.model.EconomyContext
import com.greendynasty.football.economy.model.EconomyIndexSnapshot
import com.greendynasty.football.economy.model.FinancialHealthReport
import com.greendynasty.football.economy.model.LeagueEconomySnapshot
import com.greendynasty.football.economy.model.PlayerValuation
import java.time.LocalDate

/**
 * T17 经济通胀模型数据访问层（V0.2 §一 经济模型流水线）。
 *
 * 职责：
 * 1. 协调 [EconomyIndexService] / [LeagueEconomyService] / [PlayerValueCalculator] / [WageCalculator] 等组件
 * 2. 从 [DatabaseManager] 读取 save.db + history.db 数据，构造 [EconomyContext] 与 [ClubFinancialState]
 * 3. 对外暴露简洁 API：估算身价 / 计算工资 / 财政健康检查 / 联赛商业快照 / 通胀趋势
 *
 * 设计原则：
 * - Repository 是唯一的"DB 读 + 计算协调"入口，UI / ViewModel 不直接调用 DAO
 * - 所有 DB 查询使用 suspend，避免阻塞主线程
 * - 计算结果不可变（data class），便于 UI 缓存
 *
 * @property databaseManager 三库管理入口
 * @property indexService 时代通胀服务
 * @property leagueService 联赛商业服务
 * @property valueCalculator 球员身价计算器
 * @property wageCalculator 工资计算器
 * @property financialPowerCalculator 俱乐部财力计算器
 * @property healthChecker 财政健康检查器
 * @property config 经济配置
 */
class EconomyRepository(
    private val databaseManager: DatabaseManager,
    private val indexService: EconomyIndexService,
    private val leagueService: LeagueEconomyService,
    private val valueCalculator: PlayerValueCalculator,
    private val wageCalculator: WageCalculator,
    private val financialPowerCalculator: ClubFinancialPowerCalculator,
    private val healthChecker: FinancialHealthChecker,
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    // ==================== 经济指数 ====================

    /**
     * 获取当前年份的经济指数快照。
     */
    suspend fun getCurrentEconomyIndex(year: Int): EconomyIndexSnapshot =
        indexService.getSnapshot(year)

    /**
     * 获取 1992 至 [toYear] 的完整通胀趋势（DB + 固定表回退）。
     */
    suspend fun getEconomyTrend(toYear: Int): List<EconomyIndexSnapshot> =
        indexService.getTrend(toYear)

    /**
     * 年度推进：确保指定年份的经济指数已写入存档表（V0.2 §二 年度更新）。
     * 供 T07 赛季末经济结算调用。
     */
    suspend fun ensureYearIndex(year: Int) = indexService.ensureYearIndex(year)

    // ==================== 联赛商业 ====================

    /**
     * 获取联赛商业系数快照。
     */
    suspend fun getLeagueSnapshot(leagueId: String, year: Int): LeagueEconomySnapshot =
        leagueService.getSnapshot(leagueId, year)

    /**
     * 获取全部 9 联赛商业快照（按商业系数降序）。
     */
    suspend fun getAllLeagueSnapshots(year: Int): List<LeagueEconomySnapshot> =
        leagueService.getAllSnapshots(year)

    // ==================== 球员身价 / 工资 ====================

    /**
     * 估算球员市场身价（供 T13/T18 调用）。
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param currentDate 当前游戏日期
     * @param currentYear 当前年份
     * @return 球员身价估值（含 6 因子分解）
     */
    suspend fun estimatePlayerValue(
        saveId: Int,
        playerId: Int,
        currentDate: LocalDate,
        currentYear: Int
    ): PlayerValuation? {
        val playerState = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
            ?: return null
        val playerEntity = databaseManager.historyPlayerDao().getPlayer(playerId)

        val clubId = playerState.currentClubId
        val (clubLeagueId, clubReputation) = resolveClubLeagueAndReputation(clubId, saveId)

        val expectedWage = wageCalculator.calculate(
            player = playerState,
            clubReputation = clubReputation,
            clubLeagueId = clubLeagueId,
            currentYear = currentYear
        ).expectedWage

        return valueCalculator.calculate(
            player = playerState,
            birthDate = playerEntity?.birthDate,
            primaryPosition = playerEntity?.primaryPosition,
            reputation = derivePlayerReputation(playerState, playerEntity),
            clubLeagueId = clubLeagueId,
            currentDate = currentDate,
            currentYear = currentYear,
            expectedWage = expectedWage
        )
    }

    /**
     * 计算球员期望周薪（供 T12 续约 / T13 谈判调用）。
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param currentYear 当前年份
     * @return 期望周薪（整数），null 表示球员不存在
     */
    suspend fun calculateExpectedWage(
        saveId: Int,
        playerId: Int,
        currentYear: Int
    ): Int? {
        val playerState = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
            ?: return null
        val clubId = playerState.currentClubId
        val (clubLeagueId, clubReputation) = resolveClubLeagueAndReputation(clubId, saveId)

        return wageCalculator.calculate(
            player = playerState,
            clubReputation = clubReputation,
            clubLeagueId = clubLeagueId,
            currentYear = currentYear
        ).expectedWage
    }

    // ==================== 俱乐部财力 / 财政健康 ====================

    /**
     * 获取俱乐部财力分值（0-100，供 T18 AI 决策调用）。
     */
    suspend fun getClubFinancialPower(
        saveId: Int,
        clubId: Int,
        year: Int
    ): Int {
        val club = databaseManager.historyClubDao().getClub(clubId) ?: return 0
        val financial = buildFinancialState(saveId, clubId, year)
        val leagueId = resolveClubLeagueId(club)
        return financialPowerCalculator.calculate(club, financial, leagueId, year)
    }

    /**
     * 财政健康检查（供 T07 每月任务调用）。
     */
    suspend fun checkFinancialHealth(
        saveId: Int,
        clubId: Int,
        year: Int
    ): FinancialHealthReport {
        val financial = buildFinancialState(saveId, clubId, year)
        return healthChecker.check(financial)
    }

    /**
     * 构造俱乐部财政状态（V0.2 §四 输入）。
     *
     * V1 简化：
     * - stadiumIncome / commercialIncome / ownerInvestment 从 SaveClubStateEntity 的预算字段推导
     * - recentSuccess 默认 0.5（V2 接入近 3 年战绩）
     */
    suspend fun buildFinancialState(
        saveId: Int,
        clubId: Int,
        year: Int
    ): ClubFinancialState {
        val club = databaseManager.historyClubDao().getClub(clubId)
        val clubState = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        val leagueId = club?.let { resolveClubLeagueId(it) } ?: "UNKNOWN"
        val leagueEconomyMultiplier = leagueService.getMultiplier(leagueId, year)
        val clubReputation = clubState?.reputation ?: club?.reputation ?: 50

        // V1 简化：从预算字段推导收入项（V2 可接入独立财务表）
        val transferBudget = clubState?.transferBudget ?: 0
        val wageBudget = clubState?.wageBudget ?: 0
        val balance = clubState?.balance ?: 0
        // 估算年收入：转会预算 + 工资预算 × 52（年化） + 余额利息（10%）
        val totalIncome = transferBudget + wageBudget * 52 + (balance / 10)
        val totalWage = wageBudget * 52
        val wageToIncomeRatio = if (totalIncome > 0) {
            totalWage.toDouble() / totalIncome
        } else 0.0

        // 收入细分（V1 简化等比例拆分）
        val stadiumIncome = (totalIncome * 0.30).toInt()
        val commercialIncome = (totalIncome * 0.40).toInt()
        val ownerInvestment = (totalIncome * 0.20).toInt()
        val recentSuccess = 0.5

        // 财力分值（先置 0，由 ClubFinancialPowerCalculator 计算后回填）
        return ClubFinancialState(
            clubId = clubId,
            clubReputation = clubReputation,
            leagueId = leagueId,
            leagueEconomyMultiplier = leagueEconomyMultiplier,
            stadiumIncome = stadiumIncome,
            commercialIncome = commercialIncome,
            ownerInvestment = ownerInvestment,
            recentSuccess = recentSuccess,
            financialPowerScore = 0,
            transferBudget = transferBudget,
            wageBudget = wageBudget,
            balance = balance,
            totalWage = totalWage,
            totalIncome = totalIncome,
            wageToIncomeRatio = wageToIncomeRatio
        )
    }

    // ==================== 内部工具 ====================

    /** 解析俱乐部所在联赛标识 + 当前声望 */
    private suspend fun resolveClubLeagueAndReputation(
        clubId: Int?,
        saveId: Int
    ): Pair<String, Int> {
        if (clubId == null) return "UNKNOWN" to 50
        val club = databaseManager.historyClubDao().getClub(clubId)
        val leagueId = club?.let { resolveClubLeagueId(it) } ?: "UNKNOWN"
        val saveClubState = databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        val reputation = saveClubState?.reputation ?: club?.reputation ?: 50
        return leagueId to reputation
    }

    /**
     * 解析俱乐部所在联赛标识。
     *
     * V1 简化：根据俱乐部 country 字段映射到 9 大联赛（与 V0.2 §三 9 联赛对齐）。
     * V2 可接入 club_competition_season 表精确查询俱乐部所属联赛。
     */
    private fun resolveClubLeagueId(club: ClubEntity): String {
        val country = club.country?.trim()?.lowercase() ?: return "UNKNOWN"
        return when (country) {
            "england", "英格兰", "eng" -> "EPL"
            "spain", "西班牙", "esp" -> "LaLiga"
            "italy", "意大利", "ita" -> "SerieA"
            "germany", "德国", "ger", "deutschland" -> "Bundesliga"
            "france", "法国", "fra" -> "Ligue1"
            "netherlands", "荷兰", "ned" -> "Eredivisie"
            "portugal", "葡萄牙", "por" -> "PrimeiraLiga"
            "brazil", "巴西", "bra" -> "Brasileirao"
            "argentina", "阿根廷", "arg" -> "Argentino"
            else -> "UNKNOWN"
        }
    }

    /**
     * 推导球员声望 0-100（V1 简化：从 CA 推导）。
     *
     * V0.2 §五 reputation_multiplier 公式：0.5 + reputation / 100.0
     * V1 简化：reputation = (CA - 50) × 1.5，钳制在 0-100。
     * V2 可接入 playerEntity 的历史数据（国家队出场 / 进球 / 荣誉等）。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun derivePlayerReputation(
        playerState: SavePlayerStateEntity,
        playerEntity: com.greendynasty.football.data.history.entity.PlayerEntity?
    ): Int {
        val ca = playerState.currentCa
        return ((ca - 50) * 1.5).toInt().coerceIn(0, 100)
    }
}
