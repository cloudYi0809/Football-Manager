package com.greendynasty.football.simulation.matchday

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.match.api.Formation
import com.greendynasty.football.match.api.Mentality
import com.greendynasty.football.match.api.PassStyle
import com.greendynasty.football.match.api.PlayerAttributes
import com.greendynasty.football.match.api.PlayerState
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.api.Tactic
import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.match.api.TeamSheet
import com.greendynasty.football.simulation.api.AdvanceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 球队出场名单构建器（T07 方案 §四.1 buildTeamSheet）
 *
 * 从 save.db（球员存档状态）+ history.db（球员基础信息+属性）聚合成 MatchInput 所需的 TeamSheet。
 *
 * V1 简化：
 * - 阵型默认 433（若球员不足则填充默认球员保证 11 人）
 * - 战术默认平衡战术
 * - 位置映射由 PlayerEntity.primaryPosition 决定
 *
 * @param databaseManager 三库管理入口
 */
class TeamSheetBuilder(
    private val databaseManager: DatabaseManager
) {

    /**
     * 构建球队出场名单
     *
     * @param ctx 推进上下文
     * @param clubId 俱乐部 ID
     * @param isHome 是否主队
     * @return TeamSheet 或 null（构建失败）
     */
    suspend fun build(
        ctx: AdvanceContext,
        clubId: Int,
        isHome: Boolean
    ): TeamSheet? = withContext(Dispatchers.IO) {
        try {
            val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext null
            val playerDao = databaseManager.historyPlayerDao()

            // 读取俱乐部全部球员存档状态
            val states = saveDb.savePlayerStateDao().getByClub(ctx.saveId, clubId)
            if (states.isEmpty()) {
                Log.w(TAG, "俱乐部 $clubId 无球员数据，使用默认名单")
                return@withContext buildDefaultTeamSheet(clubId, isHome)
            }

            // 过滤伤病球员，按 CA 降序排列
            val available = states
                .filter { it.injuryStatus == "healthy" }
                .sortedByDescending { it.currentCa }

            if (available.isEmpty()) {
                Log.w(TAG, "俱乐部 $clubId 无可用健康球员，使用默认名单")
                return@withContext buildDefaultTeamSheet(clubId, isHome)
            }

            // 构建球员状态（聚合 history 属性）
            val playerStates = available.mapNotNull { state ->
                val player = playerDao.getPlayer(state.playerId) ?: return@mapNotNull null
                val attrs = playerDao.getAttributes(state.playerId, ctx.currentSeasonId)
                    ?: playerDao.getAttributes(state.playerId, ctx.currentSeasonId - 1)
                buildPlayerState(player, attrs, state)
            }

            if (playerStates.size < 11) {
                // 不足 11 人，用默认球员补齐
                val needed = 11 - playerStates.size
                val fillers = (1..needed).map { idx ->
                    buildDefaultPlayerState("filler_${clubId}_$idx", clubId)
                }
                val all = playerStates + fillers
                val starting = all.take(11)
                val subs = if (all.size > 11) all.drop(11).take(7) else emptyList()
                buildTeamSheet(clubId, starting, subs, isHome)
            } else {
                val starting = playerStates.take(11)
                val subs = playerStates.drop(11).take(7)
                buildTeamSheet(clubId, starting, subs, isHome)
            }
        } catch (e: Exception) {
            Log.e(TAG, "构建球队名单失败: clubId=$clubId", e)
            buildDefaultTeamSheet(clubId, isHome)
        }
    }

    /**
     * 构建 PlayerState（聚合 history 球员基础信息 + 属性 + save 状态）
     */
    private fun buildPlayerState(
        player: PlayerEntity,
        attrs: PlayerAttributesEntity?,
        state: SavePlayerStateEntity
    ): PlayerState {
        val position = parsePosition(player.primaryPosition)
        return PlayerState(
            playerId = player.playerId.toString(),
            position = position,
            attributes = mapAttributes(attrs),
            ca = state.currentCa,
            condition = state.condition,
            morale = state.morale,
            injuryProneness = attrs?.injuryProneness ?: 10,
            starTemplate = null,
            minutesPlayedRecent = 0
        )
    }

    /**
     * 映射 PlayerAttributesEntity → PlayerAttributes
     * 缺失字段使用默认值 50
     */
    private fun mapAttributes(attrs: PlayerAttributesEntity?): PlayerAttributes {
        if (attrs == null) return defaultAttributes()
        return PlayerAttributes(
            finishing = attrs.finishing,
            shotPower = attrs.strength, // 映射：力量 → 射门力量
            longShots = attrs.longShots,
            pace = attrs.pace,
            acceleration = attrs.acceleration,
            dribbling = attrs.dribbling,
            heading = attrs.heading,
            passing = attrs.passing,
            technique = attrs.technique,
            vision = attrs.vision,
            workRate = attrs.workRate,
            pressing = attrs.workRate, // 映射：工作投入 → 压迫
            teamwork = attrs.teamwork,
            tackling = attrs.tackling,
            marking = attrs.marking,
            interceptions = attrs.positioning, // 映射：站位 → 拦截
            standingTackle = attrs.tackling,
            slidingTackle = attrs.tackling,
            gkDiving = attrs.gkDiving,
            gkHandling = attrs.gkHandling,
            gkKicking = 50, // PlayerAttributesEntity 无此字段
            gkPositioning = attrs.gkPositioning,
            gkReflexes = attrs.gkReflexes,
            aggression = attrs.ambition, // 映射：野心 → 侵略性
            composure = attrs.composure,
            leadership = attrs.leadership,
            consistency = attrs.consistency
        )
    }

    private fun defaultAttributes() = PlayerAttributes(
        finishing = 50, shotPower = 50, longShots = 50, pace = 50, acceleration = 50,
        dribbling = 50, heading = 50, passing = 50, technique = 50, vision = 50,
        workRate = 50, pressing = 50, teamwork = 50, tackling = 50, marking = 50,
        interceptions = 50, standingTackle = 50, slidingTackle = 50,
        gkDiving = 0, gkHandling = 0, gkKicking = 0, gkPositioning = 0, gkReflexes = 0,
        aggression = 50, composure = 50, leadership = 50, consistency = 50
    )

    /**
     * 解析位置字符串为 Position 枚举
     */
    private fun parsePosition(posStr: String?): Position {
        if (posStr.isNullOrBlank()) return Position.CM
        return try {
            Position.valueOf(posStr.uppercase().trim())
        } catch (e: IllegalArgumentException) {
            // 兼容常见位置缩写
            when (posStr.uppercase().trim().substring(0, minOf(2, posStr.length))) {
                "GK" -> Position.GK
                "CB" -> Position.CB
                "LB", "DL" -> Position.LB
                "RB", "DR" -> Position.RB
                "DM", "CD" -> Position.DM
                "CM" -> Position.CM
                "AM", "AC" -> Position.AM
                "LW", "WL" -> Position.LW
                "RW", "WR" -> Position.RW
                "ST", "CF" -> Position.ST
                else -> Position.CM
            }
        }
    }

    /**
     * 构建默认球员状态（球员数据缺失时使用）
     */
    private fun buildDefaultPlayerState(playerId: String, clubId: Int): PlayerState {
        return PlayerState(
            playerId = playerId,
            position = Position.CM,
            attributes = defaultAttributes(),
            ca = 50,
            condition = 100,
            morale = 50,
            injuryProneness = 10,
            starTemplate = null,
            minutesPlayedRecent = 0
        )
    }

    /**
     * 构建 TeamSheet
     */
    private fun buildTeamSheet(
        clubId: Int,
        starting11: List<PlayerState>,
        substitutes: List<PlayerState>,
        isHome: Boolean
    ): TeamSheet {
        return TeamSheet(
            clubId = clubId.toString(),
            formation = Formation.F433,
            tactic = Tactic(
                style = TacticStyle.POSSESSION,
                pressingIntensity = 5,
                defensiveLine = 5,
                tempo = 5,
                passStyle = PassStyle.SHORT,
                mentality = Mentality.BALANCED
            ),
            starting11 = starting11,
            substitutes = substitutes,
            isHome = isHome
        )
    }

    /**
     * 构建默认球队名单（数据完全缺失时的兜底）
     */
    private fun buildDefaultTeamSheet(clubId: Int, isHome: Boolean): TeamSheet {
        val starting = (1..11).map { idx ->
            buildDefaultPlayerState("default_${clubId}_$idx", clubId)
        }
        val subs = (12..18).map { idx ->
            buildDefaultPlayerState("default_${clubId}_$idx", clubId)
        }
        return buildTeamSheet(clubId, starting, subs, isHome)
    }

    companion object {
        private const val TAG = "TeamSheetBuilder"
    }
}
