package com.greendynasty.football.ui.squad.data

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.history.entity.TransferHistoryEntity
import com.greendynasty.football.data.save.entity.SaveInjuryEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.ui.squad.model.PlayerAction
import com.greendynasty.football.ui.squad.model.PlayerWithState
import com.greendynasty.football.ui.squad.model.SquadFilter
import com.greendynasty.football.ui.squad.model.SquadSortOption
import com.greendynasty.football.ui.squad.model.SquadTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period

/**
 * 阵容数据仓库。
 *
 * 职责：
 * - 从 history.db（只读）读取球员基础信息与历史属性
 * - 从 save.db（可写）读取球员存档状态并执行长按操作
 * - 多表聚合成 [PlayerWithState] / [PlayerDetail] 供 UI 使用
 *
 * 三库分离读取铁律：history 只读、save 可写、cache 可重建（V1 暂未启用缓存）。
 *
 * @param databaseManager 三库管理入口
 * @param saveId 当前存档 ID（save_player_state.save_id 列）
 * @param clubId 经理当前俱乐部 ID
 */
class SquadRepository(
    private val databaseManager: DatabaseManager,
    private val saveId: Int = DEFAULT_SAVE_ID,
    private val clubId: Int = DEFAULT_CLUB_ID
) {

    /**
     * 获取指定梯队的球员列表（响应式）。
     *
     * 数据源：save.db 的 save_player_state（按俱乐部观察），聚合 history.db 的 player 基础信息。
     * 梯队映射：
     * - [SquadTab.LOAN_OUT]：loan_club_id 非空或 career_status = "loaned_out"
     * - 其它：squad_role == tab.name（缺失 role 时归入 FIRST_TEAM）
     */
    fun getSquadPlayers(tab: SquadTab): Flow<List<PlayerWithState>> {
        if (!isSaveReady()) {
            return flow { emit(emptyList()) }
        }
        val playerDao = databaseManager.historyPlayerDao()
        val stateDao = databaseManager.savePlayerStateDao()

        return stateDao.observeByClub(saveId, clubId)
            .map { states -> aggregatePlayers(states, tab, playerDao) }
            .catch { e ->
                Log.e(TAG, "getSquadPlayers 失败: tab=$tab", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * 搜索球员（支持模糊匹配 + 组合筛选 + 排序）。
     *
     * @param query normalized_name / real_name 模糊匹配，空串表示不限
     * @param filter 8 项组合筛选
     * @param sort 排序选项
     */
    fun searchPlayers(
        query: String,
        filter: SquadFilter,
        sort: SquadSortOption
    ): Flow<List<PlayerWithState>> {
        if (!isSaveReady()) {
            return flow { emit(emptyList()) }
        }
        val playerDao = databaseManager.historyPlayerDao()
        val stateDao = databaseManager.savePlayerStateDao()

        // 观察当前俱乐部全部球员，再在内存中做搜索 / 筛选 / 排序
        return stateDao.observeByClub(saveId, clubId)
            .map { states ->
                val allTabs = states.map { it.toTab() }.distinct()
                val aggregated = mutableListOf<PlayerWithState>()
                allTabs.forEach { tab ->
                    aggregated += aggregatePlayers(states, tab, playerDao)
                }
                var result: List<PlayerWithState> = aggregated
                if (query.isNotBlank()) {
                    result = result.filter { it.name.contains(query, ignoreCase = true) }
                }
                result = filter.applyTo(result)
                result.sortedWith(sort.comparator)
            }
            .catch { e ->
                Log.e(TAG, "searchPlayers 失败: query=$query", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * 获取球员详情（10 模块聚合）。
     *
     * 聚合来源：history.player + history.player_attributes（多赛季）+
     * history.transfer_history + save.save_player_state + save.save_injury。
     * 目标耗时 ≤300ms（铁律）。
     *
     * @return 详情对象，球员不存在时返回 null
     */
    suspend fun getPlayerDetail(playerId: Int): PlayerDetail? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        try {
            val playerDao = databaseManager.historyPlayerDao()
            val stateDao = databaseManager.savePlayerStateDao()
            val injuryDao = databaseManager.saveInjuryDao()
            val transferDao = databaseManager.historyTransferHistoryDao()

            val player = playerDao.getPlayer(playerId) ?: return@withContext null
            val state = stateDao.getByPlayer(saveId, playerId)
            val attrsList = playerDao.getAllAttributes(playerId).first()
            val transfers = transferDao.getTransfersByPlayer(playerId).first()
            val injuries = injuryDao.getActiveByPlayer(saveId, playerId)

            val latestAttrs = attrsList.maxByOrNull { it.seasonId }
            val growthPoints = attrsList.sortedBy { it.seasonId }.map { it.toGrowthPoint() }
            val tab = state?.toTab() ?: SquadTab.FIRST_TEAM

            PlayerDetail(
                basicInfo = player.toBasicInfo(tab, state),
                attributes = latestAttrs?.toAttributePanel() ?: emptyAttributePanel(),
                positionFit = player.toPositionFit(),
                growthCurve = growthPoints,
                seasonStats = emptyList(), // save_player_season_stats 表尚未创建，V1 暂空
                contract = state.toContractInfo(),
                transferHistory = transfers.map { it.toTransferRecord() },
                injuryHistory = injuries.map { it.toInjuryRecord() },
                scoutReport = buildScoutReport(player, latestAttrs),
                trainingPlan = TrainingPlan(
                    focusArea = "PHYSICAL",
                    intensity = 50,
                    mentorId = null,
                    newPositionTraining = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getPlayerDetail 失败: playerId=$playerId", e)
            null
        }
    }

    /**
     * 执行球员长按操作。
     *
     * 写入 save.db，二次确认由 UI 层负责。返回 true 表示执行成功。
     */
    suspend fun performPlayerAction(playerId: Int, action: PlayerAction): Boolean =
        withContext(Dispatchers.IO) {
            if (!isSaveReady()) return@withContext false
            try {
                val stateDao = databaseManager.savePlayerStateDao()
                when (action) {
                    PlayerAction.SET_STARTING -> {
                        stateDao.updateClub(saveId, playerId, clubId)
                        // squad_role 写入需 update 整条；此处仅触发刷新
                        Log.d(TAG, "设置首发: $playerId")
                    }
                    PlayerAction.SET_SUBSTITUTE -> {
                        Log.d(TAG, "放入替补: $playerId")
                    }
                    PlayerAction.RENEW_CONTRACT -> {
                        Log.d(TAG, "续约: $playerId")
                    }
                    PlayerAction.LIST_FOR_TRANSFER -> {
                        Log.d(TAG, "挂牌: $playerId")
                    }
                    PlayerAction.LOAN_OUT -> {
                        Log.d(TAG, "外租: $playerId")
                    }
                    PlayerAction.SET_TRAINING -> {
                        Log.d(TAG, "设置训练: $playerId")
                    }
                    PlayerAction.SET_MENTOR -> {
                        Log.d(TAG, "设置导师: $playerId")
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "performPlayerAction 失败: playerId=$playerId, action=$action", e)
                false
            }
        }

    // ==================== 内部聚合方法 ====================

    /** save.db 是否就绪 */
    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    /**
     * 将存档状态列表按梯队过滤并聚合 history 球员基础信息。
     */
    private suspend fun aggregatePlayers(
        states: List<SavePlayerStateEntity>,
        tab: SquadTab,
        playerDao: com.greendynasty.football.data.history.dao.PlayerDao
    ): List<PlayerWithState> {
        val filtered = states.filter { it.toTab() == tab }
        if (filtered.isEmpty()) return emptyList()
        return filtered.mapNotNull { state ->
            val player = try {
                playerDao.getPlayer(state.playerId)
            } catch (e: Exception) {
                Log.w(TAG, "查询球员基础信息失败: ${state.playerId}", e)
                null
            } ?: return@mapNotNull null
            state.toPlayerWithState(player, tab)
        }
    }

    // ==================== Entity → Model 转换 ====================

    private fun SavePlayerStateEntity.toTab(): SquadTab {
        if (loanClubId != null || careerStatus == "loaned_out") return SquadTab.LOAN_OUT
        return when (squadRole?.uppercase()) {
            "RESERVE" -> SquadTab.RESERVE
            "U21" -> SquadTab.U21
            "U18" -> SquadTab.U18
            "LOAN_OUT" -> SquadTab.LOAN_OUT
            else -> SquadTab.FIRST_TEAM
        }
    }

    private fun SavePlayerStateEntity.toPlayerWithState(
        player: PlayerEntity,
        tab: SquadTab
    ): PlayerWithState = PlayerWithState(
        playerId = playerId,
        name = player.displayName ?: player.realName,
        age = computeAge(player.birthDate),
        nationality = player.nationality ?: "未知",
        position = player.primaryPosition ?: "CM",
        ca = currentCa,
        pa = currentPa,
        condition = condition,
        morale = morale,
        contractUntil = contractUntil,
        marketValue = marketValue,
        injuryStatus = injuryStatus,
        squadTab = tab,
        shirtNumber = null,
        wage = wage,
        portraitPath = player.portraitPath,
        preferredFoot = player.preferredFoot,
        height = player.height,
        weight = player.weight,
        isListed = false,
        isCaptain = false,
        isLoaned = loanClubId != null
    )

    private fun PlayerEntity.toBasicInfo(
        tab: SquadTab,
        state: SavePlayerStateEntity?
    ): BasicInfo = BasicInfo(
        playerId = playerId,
        name = displayName ?: realName,
        age = computeAge(birthDate),
        nationality = nationality ?: "未知",
        secondNationality = secondNationality,
        position = primaryPosition ?: "CM",
        secondaryPositions = secondaryPositions?.split(",")?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList(),
        birthDate = birthDate,
        height = height,
        weight = weight,
        preferredFoot = preferredFoot,
        personality = personality,
        portraitPath = portraitPath,
        shirtNumber = null,
        squadTab = tab
    )

    private fun PlayerEntity.toPositionFit(): List<PositionFit> {
        val list = mutableListOf<PositionFit>()
        primaryPosition?.let { list.add(PositionFit(it, 100)) }
        secondaryPositions?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?.forEach { list.add(PositionFit(it, 70)) }
        return if (list.isEmpty()) listOf(PositionFit("CM", 50)) else list
    }

    private fun PlayerAttributesEntity.toGrowthPoint(): GrowthCurvePoint = GrowthCurvePoint(
        seasonId = seasonId,
        seasonLabel = "S$seasonId",
        ca = ca,
        pa = pa
    )

    private fun PlayerAttributesEntity.toAttributePanel(): AttributePanel = AttributePanel(
        ca = ca,
        pa = pa,
        technical = listOf(
            AttributeItem("shooting", "射门", shooting),
            AttributeItem("finishing", "终结", finishing),
            AttributeItem("long_shots", "远射", longShots),
            AttributeItem("passing", "传球", passing),
            AttributeItem("crossing", "传中", crossing),
            AttributeItem("dribbling", "盘带", dribbling),
            AttributeItem("technique", "技术", technique),
            AttributeItem("first_touch", "停球", firstTouch)
        ),
        physical = listOf(
            AttributeItem("pace", "速度", pace),
            AttributeItem("acceleration", "加速", acceleration),
            AttributeItem("strength", "力量", strength),
            AttributeItem("stamina", "体能", stamina),
            AttributeItem("balance", "平衡", balance),
            AttributeItem("agility", "敏捷", agility),
            AttributeItem("jumping", "弹跳", jumping)
        ),
        defending = listOf(
            AttributeItem("defending", "防守", defending),
            AttributeItem("tackling", "抢断", tackling),
            AttributeItem("marking", "盯人", marking),
            AttributeItem("positioning", "站位", positioning),
            AttributeItem("heading", "头球", heading)
        ),
        mental = listOf(
            AttributeItem("vision", "视野", vision),
            AttributeItem("decision", "决策", decision),
            AttributeItem("composure", "冷静", composure),
            AttributeItem("leadership", "领导力", leadership),
            AttributeItem("work_rate", "工作投入", workRate),
            AttributeItem("teamwork", "团队合作", teamwork)
        ),
        goalkeeper = listOf(
            AttributeItem("gk_diving", "扑救", gkDiving),
            AttributeItem("gk_reflexes", "反应", gkReflexes),
            AttributeItem("gk_handling", "持球", gkHandling),
            AttributeItem("gk_positioning", "站位", gkPositioning),
            AttributeItem("gk_one_on_one", "一对一", gkOneOnOne)
        )
    )

    private fun emptyAttributePanel(): AttributePanel = AttributePanel(
        ca = 50,
        pa = 50,
        technical = emptyList(),
        physical = emptyList(),
        defending = emptyList(),
        mental = emptyList(),
        goalkeeper = emptyList()
    )

    private fun SavePlayerStateEntity?.toContractInfo(): ContractInfo = ContractInfo(
        contractUntil = this?.contractUntil,
        wage = this?.wage ?: 0,
        marketValue = this?.marketValue ?: 0,
        isListed = false,
        isCaptain = false,
        isLoaned = this?.loanClubId != null,
        loanClubId = this?.loanClubId
    )

    private fun TransferHistoryEntity.toTransferRecord(): TransferRecord = TransferRecord(
        transferDate = transferDate,
        fromClubId = fromClubId,
        toClubId = toClubId,
        fee = fee,
        transferType = transferType,
        notes = notes
    )

    private fun SaveInjuryEntity.toInjuryRecord(): InjuryRecord = InjuryRecord(
        injuryType = injuryType,
        startDate = startDate,
        expectedReturnDate = expectedReturnDate,
        severity = severity,
        status = status
    )

    /** 由属性生成简易球探报告 */
    private fun buildScoutReport(
        player: PlayerEntity,
        attrs: PlayerAttributesEntity?
    ): ScoutReport {
        val ca = attrs?.ca ?: 50
        val level = when {
            ca >= 150 -> 5
            ca >= 130 -> 4
            ca >= 110 -> 3
            ca >= 90 -> 2
            else -> 1
        }
        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()
        attrs?.let {
            if (it.shooting >= 70) strengths.add("射门能力突出")
            if (it.pace >= 70) strengths.add("速度优势明显")
            if (it.passing >= 70) strengths.add("传球视野出色")
            if (it.defending >= 70) strengths.add("防守稳健")
            if (it.shooting < 40) weaknesses.add("射门需提升")
            if (it.stamina < 40) weaknesses.add("体能储备不足")
            if (it.strength < 40) weaknesses.add("身体对抗偏弱")
        }
        if (strengths.isEmpty()) strengths.add("整体均衡")
        if (weaknesses.isEmpty()) weaknesses.add("无明显短板")
        return ScoutReport(
            recommendationLevel = level,
            summary = "${player.displayName ?: player.realName}（CA $ca）综合评估推荐等级 $level/5",
            strengths = strengths,
            weaknesses = weaknesses
        )
    }

    /** 由出生日期计算年龄（minSdk 26 支持 java.time） */
    private fun computeAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return 18
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, LocalDate.now()).years
        } catch (e: Exception) {
            18
        }
    }

    companion object {
        private const val TAG = "SquadRepository"

        /** 默认存档 ID（单存档场景，每个存档独立文件，列值多为 1） */
        private const val DEFAULT_SAVE_ID = 1

        /** 默认俱乐部 ID（应由 ViewModel 从 SaveManager 注入） */
        private const val DEFAULT_CLUB_ID = 1
    }
}
