package com.greendynasty.football.ui.schedule.generator

import com.greendynasty.football.data.save.entity.SaveCupTieEntity
import com.greendynasty.football.data.save.entity.SaveMatchEntity
import com.greendynasty.football.ui.schedule.model.CupStageConfig
import com.greendynasty.football.ui.schedule.model.ScheduleConfig
import java.time.LocalDate

/**
 * 杯赛对阵表生成器（单/双回合淘汰制）
 *
 * 算法依据：T06 实现方案 §三.2，V0.1 11 §九 杯赛赛程。
 *
 * 关键特性：
 * 1. 参赛俱乐部数补齐到 2 的幂（轮空 bye 自动晋级）
 * 2. 种子排位避免强强过早相遇（标准对阵表 seeding）
 * 3. 双回合支持（首回合 A 主、次回合 B 主）
 * 4. 后续轮次 ties 预创建，参赛方待定，由晋级回填
 *
 * 不变量：[nextTieId] 显式记录晋级路径，确保回填可追溯。
 */
class CupBracketGenerator(
    private val config: ScheduleConfig = ScheduleConfig.DEFAULT
) {

    /**
     * 生成完整杯赛对阵表。
     *
     * @param participants 参赛俱乐部 ID 列表（自动补齐到 2 的幂）
     * @param seedRanking 种子排名（clubId -> 种子分，数值越小种子越靠前）
     * @param stageConfig 各轮次赛制（按 stageOrder 升序）
     * @param competitionId 赛事 ID
     * @param seasonId 赛季 ID
     * @param startDate 首轮首回合日期
     * @return Pair(ties, matches)，ties 含所有轮次（首轮 + 后续预创建），matches 仅首轮非轮空 tie 的比赛
     */
    fun generateCupBracket(
        participants: List<Int>,
        seedRanking: Map<Int, Int>,
        stageConfig: List<CupStageConfig>,
        competitionId: Int,
        seasonId: Int,
        startDate: LocalDate
    ): Pair<List<SaveCupTieEntity>, List<SaveMatchEntity>> {
        require(participants.isNotEmpty()) { "参赛俱乐部列表不能为空" }
        require(stageConfig.isNotEmpty()) { "杯赛阶段配置不能为空" }

        // 1. 补齐到 2 的幂（轮空处理）：null 表示轮空
        val targetSize = nextPowerOfTwo(participants.size)
        val sortedBySeed = participants.sortedBy { seedRanking[it] ?: Int.MAX_VALUE }
        val slots = sortedBySeed.toMutableList()
        while (slots.size < targetSize) slots.add(BYE_CLUB_ID)

        // 2. 种子对阵排位（标准对阵表 seeding）
        val seededOrder = seedBracketSlots(targetSize)
        val firstRoundPairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < seededOrder.size) {
            val homeIdx = seededOrder[i]
            val awayIdx = seededOrder[i + 1]
            firstRoundPairs.add(slots[homeIdx] to slots[awayIdx])
            i += 2
        }

        // 3. 生成首轮 ties + 比赛
        val ties = mutableListOf<SaveCupTieEntity>()
        val matches = mutableListOf<SaveMatchEntity>()
        val firstStage = stageConfig.first()
        var currentDate = startDate

        firstRoundPairs.forEachIndexed { idx, (home, away) ->
            val tieId = buildTieId(competitionId, seasonId, firstStage.stageOrder, idx)
            // 轮空直接晋级
            val winner = when {
                home == BYE_CLUB_ID && away != BYE_CLUB_ID -> away
                away == BYE_CLUB_ID && home != BYE_CLUB_ID -> home
                home == BYE_CLUB_ID && away == BYE_CLUB_ID -> null
                else -> null  // 待比赛决出
            }
            val realHome = if (home == BYE_CLUB_ID) null else home
            val realAway = if (away == BYE_CLUB_ID) null else away
            ties.add(
                SaveCupTieEntity(
                    tieId = tieId,
                    saveId = 0,
                    seasonId = seasonId,
                    competitionId = competitionId,
                    stage = firstStage.stage,
                    stageOrder = firstStage.stageOrder,
                    homeClubId = realHome,
                    awayClubId = realAway,
                    winnerClubId = winner,
                    isTwoLegged = if (firstStage.isTwoLegged) 1 else 0,
                    slotIndex = idx,
                    nextTieId = computeNextTieId(
                        competitionId, seasonId, firstStage.stageOrder, idx, stageConfig
                    )
                )
            )
            // 轮空不生成比赛
            if (realHome != null && realAway != null && winner == null) {
                matches.addAll(
                    createTieMatches(
                        realHome, realAway, firstStage,
                        currentDate, competitionId, seasonId
                    )
                )
            }
        }

        // 4. 后续轮次 ties 预创建（参赛方待定，由晋级回填）
        var prevRoundCount = firstRoundPairs.size
        for (stageIndex in 1 until stageConfig.size) {
            val stage = stageConfig[stageIndex]
            val tieCount = prevRoundCount / 2
            currentDate = currentDate.plusDays(stage.intervalDays.toLong())
            for (slotIdx in 0 until tieCount) {
                ties.add(
                    SaveCupTieEntity(
                        tieId = buildTieId(competitionId, seasonId, stage.stageOrder, slotIdx),
                        saveId = 0,
                        seasonId = seasonId,
                        competitionId = competitionId,
                        stage = stage.stage,
                        stageOrder = stage.stageOrder,
                        homeClubId = null,    // 待回填
                        awayClubId = null,
                        isTwoLegged = if (stage.isTwoLegged) 1 else 0,
                        slotIndex = slotIdx,
                        nextTieId = computeNextTieId(
                            competitionId, seasonId, stage.stageOrder, slotIdx, stageConfig
                        )
                    )
                )
            }
            prevRoundCount = tieCount
        }

        return ties to matches
    }

    /**
     * 标准对阵表种子位（保证 1、2 种子分属两个半区）
     *
     * 8 队示例：[0,7,3,4,5,2,6,1] → 1v8,4v5,3v6,2v7
     *
     * 算法：从 [0, size-1] 开始，迭代地在新层中为每个已有槽位追加其镜像（size-1-slot），
     * 直到填满 size 个槽位。
     */
    internal fun seedBracketSlots(size: Int): List<Int> {
        require(size >= 2 && size and (size - 1) == 0) { "size 必须为 2 的幂: $size" }
        val result = mutableListOf(0, size - 1)
        while (result.size < size) {
            val newLayer = mutableListOf<Int>()
            for (slot in result) {
                newLayer.add(slot)
                newLayer.add(size - 1 - slot)
            }
            result.clear()
            result.addAll(newLayer.distinct())
        }
        return result.subList(0, size)
    }

    /** 取 ≥ n 的最小 2 的幂 */
    internal fun nextPowerOfTwo(n: Int): Int {
        if (n <= 2) return 2
        var p = 2
        while (p < n) p *= 2
        return p
    }

    /**
     * 创建一条对阵的比赛（1 或 2 场）
     *
     * 双回合：首回合 A 主、次回合 B 主（间隔 7 天）
     *
     * 注意：tieId 不直接写入 SaveMatchEntity（实体无该字段），由 Repository 通过
     * (home, away, round, date) 反查 matchId 后回填到 SaveCupTieEntity.firstLegMatchId。
     */
    private fun createTieMatches(
        home: Int,
        away: Int,
        stage: CupStageConfig,
        date: LocalDate,
        competitionId: Int,
        seasonId: Int
    ): List<SaveMatchEntity> = if (stage.isTwoLegged) {
        listOf(
            SaveMatchEntity(
                saveId = 0,
                seasonId = seasonId,
                competitionId = competitionId,
                matchDate = date.toString(),
                matchRound = stage.stageOrder,
                homeClubId = home,
                awayClubId = away,
                status = "scheduled",
                isPlayerMatch = 0
            ),
            SaveMatchEntity(
                saveId = 0,
                seasonId = seasonId,
                competitionId = competitionId,
                matchDate = date.plusDays(7).toString(),
                matchRound = stage.stageOrder,
                homeClubId = away,
                awayClubId = home,
                status = "scheduled",
                isPlayerMatch = 0
            )
        )
    } else {
        listOf(
            SaveMatchEntity(
                saveId = 0,
                seasonId = seasonId,
                competitionId = competitionId,
                matchDate = date.toString(),
                matchRound = stage.stageOrder,
                homeClubId = home,
                awayClubId = away,
                status = "scheduled",
                isPlayerMatch = 0
            )
        )
    }

    /** 构造 tie_id：competitionId_seasonId_R{stageOrder}_T{slotIndex} */
    private fun buildTieId(
        competitionId: Int,
        seasonId: Int,
        stageOrder: Int,
        slotIndex: Int
    ): String = "${competitionId}_${seasonId}_R${stageOrder}_T${slotIndex}"

    /**
     * 计算晋级后进入的下一轮 tie_id。
     *
     * 同一轮的相邻两个 slot 晋级到下一轮同一 slot（slotIndex / 2）。
     * 决赛（最后一轮）的 nextTieId 为 null。
     */
    private fun computeNextTieId(
        competitionId: Int,
        seasonId: Int,
        currentStageOrder: Int,
        currentSlotIndex: Int,
        stageConfig: List<CupStageConfig>
    ): String? {
        val nextStage = stageConfig.firstOrNull { it.stageOrder == currentStageOrder + 1 }
            ?: return null  // 决赛无下一轮
        val nextSlot = currentSlotIndex / 2
        return buildTieId(competitionId, seasonId, nextStage.stageOrder, nextSlot)
    }

    companion object {
        /** 轮空占位 ID（内部使用，不会写入 Entity） */
        const val BYE_CLUB_ID = -1
    }
}
