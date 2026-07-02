package com.greendynasty.football.transfer.ai.target

import com.greendynasty.football.transfer.ai.config.BasicAiConfig
import com.greendynasty.football.transfer.ai.model.PlayerCandidate
import com.greendynasty.football.transfer.model.TransferSearchFilter
import com.greendynasty.football.transfer.model.TransferSortBy
import com.greendynasty.football.transfer.model.SortOrder
import com.greendynasty.football.transfer.search.PlayerSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T13.2 AI 转会目标搜索器（复用 T10 [PlayerSearchEngine]）。
 *
 * 根据阵容短板位置搜索候选球员，按 CA 降序返回，限制候选数量。
 *
 * 策略：
 * 1. 按 [PositionNeed.position] 构造 [TransferSearchFilter]（位置筛选 + CA 范围）
 * 2. 调用 [PlayerSearchEngine.search] 执行搜索
 * 3. 将 [TransferSearchResult] 转换为轻量 [PlayerCandidate]
 *
 * CA 范围策略：以配置中的 [BasicAiConfig.expectedCa] 为中心 ±20，
 * 确保候选球员能力与俱乐部期望匹配（避免保级队搜到顶级球星）。
 *
 * @param searchEngine T10 球员搜索引擎
 * @param config AI 转会配置
 */
class AiTargetFinder(
    private val searchEngine: PlayerSearchEngine,
    private val config: BasicAiConfig = BasicAiConfig.DEFAULT
) {

    /**
     * 按位置搜索候选球员。
     *
     * @param saveId 存档 ID
     * @param excludeClubId 排除的俱乐部 ID（避免搜到自己的球员）
     * @param position 目标位置代码（GK/CB/CM/ST...）
     * @param currentYear 当前年份（经济指数计算）
     * @return 候选球员列表（按 CA 降序，最多 [SearchParams.maxCandidatesPerPosition] 条）
     */
    suspend fun findCandidates(
        saveId: Int,
        excludeClubId: Int,
        position: String,
        currentYear: Int
    ): List<PlayerCandidate> = withContext(Dispatchers.IO) {
        // 1. 构造位置筛选条件
        val expectedCa = config.expectedCa
        val caRange = (expectedCa - 20).coerceAtLeast(1)..(expectedCa + 20)
        val filter = TransferSearchFilter(
            positions = listOf(position),
            caRange = caRange,
            sortBy = TransferSortBy.CA,
            sortOrder = SortOrder.DESC
        )

        // 2. 调用 T10 搜索引擎
        val results = searchEngine.search(
            saveId = saveId,
            excludeClubId = excludeClubId,
            filter = filter,
            pageSize = config.search.maxCandidatesPerPosition,
            currentYear = currentYear
        )

        // 3. 转换为轻量候选模型
        results.map { r ->
            PlayerCandidate(
                playerId = r.playerId,
                playerName = r.playerName,
                position = r.position,
                age = r.age,
                nationality = r.nationality,
                currentCa = r.currentCa,
                potentialPa = r.potentialPa,
                currentClubId = r.clubId,
                marketValue = r.marketValue,
                wage = r.wage,
                contractUntil = r.contractUntil,
                reputation = computeReputationFromCa(r.currentCa)
            )
        }
    }

    /**
     * 由 CA 推算球员声望 0-100（V1 简化）。
     *
     * CA ≥ 150 → 90+，CA 130-149 → 70-89，依次递减。
     */
    private fun computeReputationFromCa(ca: Int): Int = when {
        ca >= 150 -> 95
        ca >= 130 -> 75
        ca >= 110 -> 55
        ca >= 90 -> 35
        ca >= 70 -> 20
        else -> 10
    }
}
