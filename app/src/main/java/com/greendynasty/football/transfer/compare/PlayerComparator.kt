package com.greendynasty.football.transfer.compare

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.model.CompareResult
import com.greendynasty.football.transfer.model.PlayerCompareData
import com.greendynasty.football.transfer.model.RadarDimension
import com.greendynasty.football.transfer.search.EconomyEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * 球员对比服务（V0.1 03 阵容页：球员对比功能）。
 *
 * 支持 2-3 名球员的属性对比，输出：
 * - 各球员完整属性
 * - 每个属性的最佳 playerId（bestInCategory，用于 UI 高亮）
 * - 各球员的 6 维雷达值（进攻/中场/防守/身体/心理/门将）
 *
 * @param databaseManager 三库管理入口
 * @param economyEstimator 经济估算器
 */
class PlayerComparator(
    private val databaseManager: DatabaseManager,
    private val economyEstimator: EconomyEstimator
) {

    /**
     * 对比 2-3 名球员。
     *
     * @param playerIds 球员 ID 列表（2-3 人）
     * @param saveId 当前存档 ID
     * @param currentYear 当前年份（身价估算用）
     * @return 对比结果
     * @throws IllegalArgumentException 球员数量不在 2-3 范围内
     */
    suspend fun compare(
        playerIds: List<Int>,
        saveId: Int,
        currentYear: Int
    ): CompareResult = withContext(Dispatchers.IO) {
        require(playerIds.size in 2..3) { "对比球员数量必须 2-3 人，当前 ${playerIds.size}" }

        val playerDao = databaseManager.historyPlayerDao()
        val stateDao = databaseManager.savePlayerStateDao()

        // 批量查 history.player + save.save_player_state
        val players = playerDao.getPlayersByIds(playerIds).associateBy { it.playerId }
        val states = mutableMapOf<Int, SavePlayerStateEntity>()
        playerIds.forEach { pid ->
            stateDao.getByPlayer(saveId, pid)?.let { states[pid] = it }
        }

        // 各球员的最新赛季属性
        val latestAttributes = mutableMapOf<Int, PlayerAttributesEntity?>()
        playerIds.forEach { pid ->
            latestAttributes[pid] = runCatching { playerDao.getLatestAttributes(pid) }.getOrNull()
        }

        val compareDataList = playerIds.mapNotNull { pid ->
            val player = players[pid] ?: return@mapNotNull null
            val state = states[pid]
            val attrs = latestAttributes[pid]
            buildCompareData(player, state, attrs, currentYear)
        }

        if (compareDataList.size < 2) {
            // 至少 2 名球员有数据才能对比，否则返回空结果
            return@withContext CompareResult(
                players = compareDataList,
                bestInCategory = emptyMap(),
                radarValues = emptyMap()
            )
        }

        val bestInCategory = calculateBestInCategory(compareDataList)
        val radarValues = compareDataList.associate { it.playerId to computeRadarValues(it) }

        CompareResult(
            players = compareDataList,
            bestInCategory = bestInCategory,
            radarValues = radarValues
        )
    }

    /** 构建单球员对比数据 */
    private fun buildCompareData(
        player: PlayerEntity,
        state: SavePlayerStateEntity?,
        attrs: PlayerAttributesEntity?,
        currentYear: Int
    ): PlayerCompareData {
        val ca = state?.currentCa ?: attrs?.ca ?: 50
        val pa = state?.currentPa ?: attrs?.pa ?: ca
        val age = computeAge(player.birthDate)
        val position = player.primaryPosition ?: "CM"
        val contractUntil = state?.contractUntil
        val marketValue = if (state?.marketValue != null && state.marketValue > 0) {
            state.marketValue
        } else {
            economyEstimator.estimateMarketValue(ca, pa, age, position, contractUntil, currentYear)
        }
        val wage = if (state?.wage != null && state.wage > 0) {
            state.wage
        } else {
            economyEstimator.estimateExpectedWage(ca, position, state?.squadRole, currentYear)
        }
        val attributesMap = attrs?.let { attributesToMap(it) } ?: emptyMap()

        return PlayerCompareData(
            playerId = player.playerId,
            playerName = player.displayName ?: player.realName,
            age = age,
            position = position,
            currentCa = ca,
            potentialPa = pa,
            marketValue = marketValue,
            wage = wage,
            attributes = attributesMap,
            contractUntil = contractUntil
        )
    }

    /** 属性实体转 Map（属性名 -> 值） */
    private fun attributesToMap(attrs: PlayerAttributesEntity): Map<String, Int> = mapOf(
        "shooting" to attrs.shooting,
        "finishing" to attrs.finishing,
        "long_shots" to attrs.longShots,
        "passing" to attrs.passing,
        "crossing" to attrs.crossing,
        "dribbling" to attrs.dribbling,
        "technique" to attrs.technique,
        "first_touch" to attrs.firstTouch,
        "pace" to attrs.pace,
        "acceleration" to attrs.acceleration,
        "strength" to attrs.strength,
        "stamina" to attrs.stamina,
        "balance" to attrs.balance,
        "agility" to attrs.agility,
        "jumping" to attrs.jumping,
        "defending" to attrs.defending,
        "tackling" to attrs.tackling,
        "marking" to attrs.marking,
        "positioning" to attrs.positioning,
        "heading" to attrs.heading,
        "vision" to attrs.vision,
        "decision" to attrs.decision,
        "composure" to attrs.composure,
        "leadership" to attrs.leadership,
        "work_rate" to attrs.workRate,
        "teamwork" to attrs.teamwork,
        "injury_proneness" to attrs.injuryProneness,
        "big_match" to attrs.bigMatch,
        "consistency" to attrs.consistency,
        "professionalism" to attrs.professionalism,
        "ambition" to attrs.ambition,
        "loyalty" to attrs.loyalty,
        "gk_diving" to attrs.gkDiving,
        "gk_reflexes" to attrs.gkReflexes,
        "gk_handling" to attrs.gkHandling,
        "gk_positioning" to attrs.gkPositioning,
        "gk_one_on_one" to attrs.gkOneOnOne
    )

    /** 计算每个属性的最佳球员 ID */
    private fun calculateBestInCategory(players: List<PlayerCompareData>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        if (players.isEmpty()) return result

        // 取第一个球员的属性 key 集合作为基准
        val attributeKeys = players.first().attributes.keys
        attributeKeys.forEach { key ->
            val bestPlayer = players.maxByOrNull { it.attributes[key] ?: 0 }
            if (bestPlayer != null) {
                result[key] = bestPlayer.playerId
            }
        }
        return result
    }

    /** 计算 6 维雷达值（与阵容页 PlayerAttributeRadar 一致） */
    private fun computeRadarValues(data: PlayerCompareData): List<RadarDimension> {
        val attrs = data.attributes
        val attack = listOf("shooting", "finishing", "long_shots", "crossing")
            .map { attrs[it] ?: 0 }.average().toInt()
        val midfield = listOf("passing", "technique", "first_touch", "dribbling")
            .map { attrs[it] ?: 0 }.average().toInt()
        val defending = listOf("defending", "tackling", "marking", "positioning")
            .map { attrs[it] ?: 0 }.average().toInt()
        val physical = listOf("pace", "acceleration", "strength", "stamina")
            .map { attrs[it] ?: 0 }.average().toInt()
        val mental = listOf("vision", "decision", "composure", "leadership")
            .map { attrs[it] ?: 0 }.average().toInt()
        val gk = listOf("gk_diving", "gk_reflexes", "gk_handling", "gk_positioning")
            .map { attrs[it] ?: 0 }.average().toInt()

        return listOf(
            RadarDimension("进攻", attack),
            RadarDimension("中场", midfield),
            RadarDimension("防守", defending),
            RadarDimension("身体", physical),
            RadarDimension("心理", mental),
            RadarDimension("门将", gk)
        )
    }

    /** 由出生日期计算年龄 */
    private fun computeAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return 18
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, LocalDate.now()).years
        } catch (e: Exception) {
            18
        }
    }
}
