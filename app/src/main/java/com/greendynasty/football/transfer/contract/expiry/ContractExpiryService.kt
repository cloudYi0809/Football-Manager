package com.greendynasty.football.transfer.contract.expiry

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.transfer.contract.config.ContractRenewalConfig
import com.greendynasty.football.transfer.contract.model.ContractReminderEntity
import com.greendynasty.football.transfer.contract.model.RenewalContext
import com.greendynasty.football.transfer.contract.model.ReminderLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import kotlin.random.Random

/**
 * T12.3 合同到期处理服务（V0.1 `09_转会_合同_经纪人系统.md` §三 + §六）。
 *
 * 合并实现方案中的 4 个类（RenewalReminderService / ContractExpiryHandler /
 * PreContractHandler / FreeAgentReleaser），简化为一个服务，避免过度抽象。
 *
 * 职责：
 * 1. [checkReminders]：续约提醒检查（T07 每日推进调用）
 *    合同剩 12/6/1 个月时生成 [ContractReminderEntity]
 * 2. [processExpiry]：合同到期处理（T07 月度推进调用）
 *    - 合同剩 ≤6 个月：Bosman 预合同接触（其他俱乐部可接触，V1 仅生成新闻）
 *    - 合同已到期：球员变自由球员（currentClubId=null / wage=0 / squadRole="free_agent"）
 * 3. [releaseAsFreeAgent]：释放球员为自由球员（衔接 T11 自由签约）
 *
 * 与 T11 自由签约衔接：
 * - T11 [com.greendynasty.football.transfer.repository.TransferRepository] 通过
 *   `state.currentClubId == null` 识别自由球员，因此 T12.3 释放时只需将 currentClubId
 *   置 null 即可，无需单独的 free_agent_pool 表（V1 简化）
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 *
 * @property databaseManager 三库管理入口
 * @param saveId 当前存档 ID
 * @param clubId 玩家俱乐部 ID
 * @property config 续约配置
 */
class ContractExpiryService(
    private val databaseManager: DatabaseManager,
    private val saveId: Int,
    private val clubId: Int,
    private val config: ContractRenewalConfig = ContractRenewalConfig.DEFAULT
) {

    // ==================== 1. 续约提醒检查 ====================

    /**
     * 续约提醒检查（V0.1 09 §六，T07 每日推进调用）。
     *
     * 全队球员合同剩余 ≤12/6/1 个月时生成 [ContractReminderEntity]：
     * - EARLY_WARNING（12 个月）：早期预警，建议续约
     * - URGENT（6 个月）：紧急，Bosman 可接触，需尽快续约
     * - FINAL（1 个月）：最后机会
     *
     * 每个球员每档提醒只生成一次（通过 [ContractReminderDao.getActiveByPlayerAndLevel] 去重）。
     *
     * @param ctx 续约上下文
     * @return 新生成的提醒列表
     */
    suspend fun checkReminders(ctx: RenewalContext): List<ContractReminderEntity> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()

        val newReminders = mutableListOf<ContractReminderEntity>()
        try {
            // 1. 获取本俱乐部所有球员
            val players = databaseManager.savePlayerStateDao().getByClub(saveId, clubId)

            for (state in players) {
                // 跳过无合同球员（自由球员 / 已退役）
                if (state.contractUntil.isNullOrBlank()) continue

                // 2. 计算合同剩余月数
                val monthsLeft = monthsUntilContractEnd(state.contractUntil, ctx.currentDate)
                if (monthsLeft > config.expiry.earlyWarningMonths) continue

                // 3. 确定提醒级别
                val level = when {
                    monthsLeft <= config.expiry.finalMonths -> ReminderLevel.FINAL
                    monthsLeft <= config.expiry.urgentMonths -> ReminderLevel.URGENT
                    monthsLeft <= config.expiry.earlyWarningMonths -> ReminderLevel.EARLY_WARNING
                    else -> continue
                }

                // 4. 同级别去重：该球员该级别未处理提醒已存在则跳过
                val existing = databaseManager.contractReminderDao()
                    .getActiveByPlayerAndLevel(saveId, state.playerId, level.name)
                if (existing != null) continue

                // 5. 生成建议动作
                val player = databaseManager.historyPlayerDao().getPlayer(state.playerId)
                val recommendedAction = generateRecommendedAction(state, player, monthsLeft)

                // 6. 6 个月紧急提醒：球员可能主动要求续约
                val playerDemandTriggered = if (level == ReminderLevel.URGENT) {
                    val prob = calculatePlayerDemandProbability(state, player)
                    Random.nextDouble() < prob
                } else false

                // 7. 创建提醒
                val reminder = ContractReminderEntity(
                    reminderId = 0,
                    saveId = saveId,
                    playerId = state.playerId,
                    clubId = clubId,
                    level = level.name,
                    monthsRemaining = monthsLeft,
                    triggerDate = ctx.currentDate.toString(),
                    isHandled = false,
                    recommendedAction = recommendedAction,
                    playerDemandTriggered = playerDemandTriggered
                )
                val reminderId = databaseManager.contractReminderDao().insert(reminder).toInt()
                newReminders.add(reminder.copy(reminderId = reminderId))

                // 8. 球员主动要求续约 → 生成新闻
                if (playerDemandTriggered) {
                    generateNews(
                        ctx = ctx,
                        playerId = state.playerId,
                        title = "${player?.realName ?: "球员"} 希望尽快续约",
                        body = "${player?.realName ?: "球员"} 合同即将进入最后 6 个月，" +
                            "通过经纪人表达了续约意愿，希望俱乐部尽快启动谈判。",
                        type = "RENEWAL_DEMAND"
                    )
                }

                // 9. 1 个月最后提醒：高野心高能力球员可能威胁离队
                if (level == ReminderLevel.FINAL) {
                    val threatProb = calculateThreatProbability(state, player)
                    if (Random.nextDouble() < threatProb) {
                        generateNews(
                            ctx = ctx,
                            playerId = state.playerId,
                            title = "${player?.realName ?: "球员"} 暗示离队可能",
                            body = "${player?.realName ?: "球员"} 合同仅剩 1 个月，" +
                                "球员暗示若续约无进展将考虑自由转会。",
                            type = "RENEWAL_THREAT"
                        )
                    }
                }
            }

            Log.d(TAG, "续约提醒检查: 生成 ${newReminders.size} 条新提醒")
        } catch (e: Exception) {
            Log.e(TAG, "续约提醒检查失败", e)
        }
        newReminders
    }

    // ==================== 2. 合同到期处理 ====================

    /**
     * 合同到期处理（V0.1 09 §三 + §六，T07 月度推进调用）。
     *
     * 处理流程：
     * 1. 合同剩 ≤6 个月：Bosman 预合同接触（其他俱乐部可接触，V1 仅生成新闻通知）
     * 2. 合同已到期：球员变自由球员（衔接 T11 自由签约池）
     *
     * @param ctx 续约上下文
     * @return 到期处理结果列表
     */
    suspend fun processExpiry(ctx: RenewalContext): List<ExpiryResult> = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext emptyList()

        val results = mutableListOf<ExpiryResult>()
        try {
            val players = databaseManager.savePlayerStateDao().getByClub(saveId, clubId)

            for (state in players) {
                if (state.contractUntil.isNullOrBlank()) continue

                // 1. Bosman 预合同接触：剩 6 个月时其他俱乐部可接触
                val monthsLeft = monthsUntilContractEnd(state.contractUntil, ctx.currentDate)
                if (monthsLeft in 1..config.expiry.preContractContactMonths) {
                    val preContractResult = tryPreContractContact(ctx, state, monthsLeft)
                    if (preContractResult != null) results.add(preContractResult)
                }

                // 2. 合同已到期：球员变自由球员
                val isExpired = isContractExpired(state.contractUntil, ctx.currentDate)
                if (isExpired) {
                    val releaseResult = releaseAsFreeAgent(ctx, state.playerId)
                    if (releaseResult != null) results.add(releaseResult)
                }
            }

            Log.d(TAG, "合同到期处理: 处理 ${results.size} 条结果")
        } catch (e: Exception) {
            Log.e(TAG, "合同到期处理失败", e)
        }
        results
    }

    // ==================== 3. 自由球员释放 ====================

    /**
     * 释放球员为自由球员（V0.1 09 §六，衔接 T11 自由签约池）。
     *
     * 流程：
     * 1. 更新球员状态：currentClubId=null / wage=0 / contractUntil=null / squadRole="free_agent"
     * 2. 标记该球员的所有续约提醒为已处理
     * 3. 生成新闻（通知玩家）
     *
     * T11 自由签约衔接：T11 [com.greendynasty.football.transfer.repository.TransferRepository]
     * 通过 `state.currentClubId == null` 识别自由球员，无需单独的 free_agent_pool 表（V1 简化）。
     *
     * @param ctx 续约上下文
     * @param playerId 球员 ID
     * @return 释放结果（失败返回 null）
     */
    suspend fun releaseAsFreeAgent(ctx: RenewalContext, playerId: Int): ExpiryResult.Released? = withContext(Dispatchers.IO) {
        if (!isSaveReady()) return@withContext null
        try {
            val state = databaseManager.savePlayerStateDao().getByPlayer(saveId, playerId)
                ?: return@withContext null

            // 1. 更新球员状态：变自由球员
            databaseManager.savePlayerStateDao().update(
                state.copy(
                    currentClubId = null,
                    wage = 0,
                    contractUntil = null,
                    squadRole = "free_agent"
                )
            )

            // 2. 标记续约提醒已处理
            databaseManager.contractReminderDao().markHandledByPlayer(saveId, playerId)

            // 3. 生成新闻（通知玩家）
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)
            val clubName = databaseManager.historyClubDao().getClub(clubId)?.clubName ?: "俱乐部"
            generateNews(
                ctx = ctx,
                playerId = playerId,
                title = "${player?.realName ?: "球员"} 合同到期离队",
                body = "${player?.realName ?: "球员"} 与 $clubName 的合同到期，" +
                    "已成为自由球员。其他俱乐部可通过自由签约引进。",
                type = "CONTRACT_EXPIRED"
            )

            Log.d(TAG, "球员释放为自由球员: playerId=$playerId")
            ExpiryResult.Released(playerId = playerId)
        } catch (e: Exception) {
            Log.e(TAG, "释放球员失败: playerId=$playerId", e)
            null
        }
    }

    // ==================== 内部工具 ====================

    /**
     * Bosman 预合同接触（V0.1 09 §三）。
     *
     * 规则：合同剩 ≤6 个月时，其他俱乐部可接触球员签订预合同。
     * V1 简化：仅生成新闻通知玩家，不实际生成预合同记录（避免引入新表）。
     *
     * @param ctx 续约上下文
     * @param state 球员状态
     * @param monthsLeft 合同剩余月数
     * @return 预合同接触结果（未触发返回 null）
     */
    private suspend fun tryPreContractContact(
        ctx: RenewalContext,
        state: SavePlayerStateEntity,
        monthsLeft: Int
    ): ExpiryResult.PreContractContact? {
        // 仅在剩 6 个月整时触发一次（避免每月重复）
        if (monthsLeft != config.expiry.preContractContactMonths) return null

        // 1. 计算接触概率
        val player = databaseManager.historyPlayerDao().getPlayer(state.playerId)
        val attributes = databaseManager.historyPlayerDao().getLatestAttributes(state.playerId)
        val contactProb = calculateContactProbability(state, player, attributes)
        if (Random.nextDouble() > contactProb) return null

        // 2. 查找感兴趣俱乐部（V1 简化：从声望 >50 的俱乐部中随机选 1-3 家）
        val interestedClubs = findInterestedClubs(state, player)
        if (interestedClubs.isEmpty()) return null

        // 3. 生成新闻（提醒玩家）
        val clubNames = interestedClubs.joinToString("、") { it.clubName ?: "未知俱乐部" }
        generateNews(
            ctx = ctx,
            playerId = state.playerId,
            title = "${player?.realName ?: "球员"} 受到其他俱乐部关注",
            body = "${player?.realName ?: "球员"} 合同即将到期，已收到来自 $clubNames 的预合同接触意向。" +
                "建议尽快续约，否则球员可能以自由身离队。",
            type = "PRE_CONTRACT_CONTACT"
        )

        Log.d(TAG, "预合同接触: playerId=${state.playerId}, clubs=${interestedClubs.map { it.clubId }}")
        return ExpiryResult.PreContractContact(
            playerId = state.playerId,
            contactingClubs = interestedClubs.map { it.clubId }
        )
    }

    /**
     * 生成建议动作（V0.1 09 §六）。
     *
     * 按角色重要性 + 年龄给出建议：
     * - 关键球员/主力 + 当打 → RENEW_IMMEDIATELY（立即续约）
     * - 合同剩 ≤1 个月 → RENEW_OR_RELEASE（续约或释放）
     * - 老将 → RELEASE_OR_SHORT_TERM（释放或短约）
     * - 替补/青年 → EVALUATE（评估）
     * - 其他 → RENEW（续约）
     */
    private fun generateRecommendedAction(
        state: SavePlayerStateEntity,
        player: PlayerEntity?,
        monthsLeft: Int
    ): String {
        val role = state.squadRole?.lowercase()
        val age = player?.birthDate?.let { computeAge(it, LocalDate.now()) } ?: 25
        return when {
            role in listOf("key_player", "key", "core") && age < 30 -> "RENEW_IMMEDIATELY"
            role in listOf("starter", "first_team") && age < 32 -> "RENEW_IMMEDIATELY"
            monthsLeft <= 1 -> "RENEW_OR_RELEASE"
            age >= 34 -> "RELEASE_OR_SHORT_TERM"
            role in listOf("backup", "prospect", "youth") -> "EVALUATE"
            else -> "RENEW"
        }
    }

    /**
     * 球员主动要求续约概率（V0.1 09 §六）。
     *
     * 关键球员 + 高野心 → 更可能主动要求续约。
     * 公式：baseProb + roleBonus + ambitionBonus
     */
    private fun calculatePlayerDemandProbability(
        state: SavePlayerStateEntity,
        player: PlayerEntity?
    ): Double {
        val baseProb = 0.30
        val roleBonus = when (state.squadRole?.lowercase()) {
            "key_player", "key", "core" -> 0.20
            "starter", "first_team" -> 0.10
            else -> 0.0
        }
        // V1 简化：野心暂取 50（无字段暴露），实际应由 attributes.ambition 提供
        val ambitionBonus = 0.0
        return (baseProb + roleBonus + ambitionBonus).coerceIn(0.10, 0.80)
    }

    /**
     * 威胁离队概率（V0.1 09 §六，1 个月最后提醒时）。
     *
     * 高野心 + 高能力 → 更可能威胁离队。
     */
    private fun calculateThreatProbability(
        state: SavePlayerStateEntity,
        player: PlayerEntity?
    ): Double {
        val baseProb = 0.20
        val caBonus = (state.currentCa - 70) / 200.0
        return (baseProb + caBonus).coerceIn(0.05, 0.60)
    }

    /**
     * 预合同接触概率（V0.1 09 §三 Bosman 规则）。
     *
     * CA 越高、年龄越当打 → 越可能被接触。
     * 公式：baseProb + caBonus + ageBonus
     */
    private fun calculateContactProbability(
        state: SavePlayerStateEntity,
        player: PlayerEntity?,
        attributes: PlayerAttributesEntity?
    ): Double {
        val baseProb = config.expiry.preContractContactProbabilityBase
        val caBonus = (state.currentCa - 70) / 100.0
        val age = player?.birthDate?.let { computeAge(it, LocalDate.now()) } ?: 25
        val ageBonus = when (age) {
            in 24..29 -> 0.20
            in 30..32 -> 0.10
            else -> 0.0
        }
        return (baseProb + caBonus + ageBonus).coerceIn(0.10, 0.85)
    }

    /**
     * 查找感兴趣的俱乐部（V1 简化：按声望筛选，随机选 1-3 家）。
     *
     * 规则：
     * - 排除当前俱乐部
     * - 声望 >50（有一定吸引力）
     * - 按声望降序取前 20，再随机选 1-3 家
     */
    private suspend fun findInterestedClubs(
        state: SavePlayerStateEntity,
        player: PlayerEntity?
    ): List<com.greendynasty.football.data.history.entity.ClubEntity> {
        return runCatching {
            // V1 简化：使用 historyClubDao().getTopClubs 取声望最高的若干家
            // Flow 取首条值
            val topClubs = databaseManager.historyClubDao().getTopClubs(20).first()
            topClubs
                .filter { it.clubId != clubId && it.reputation > 50 }
                .shuffled()
                .take((1..3).random())
        }.getOrElse { emptyList() }
    }

    /** 计算合同剩余月数 */
    private fun monthsUntilContractEnd(contractUntil: String?, currentDate: LocalDate): Int {
        if (contractUntil.isNullOrBlank()) return 0
        return runCatching {
            val end = LocalDate.parse(contractUntil.take(10))
            Period.between(currentDate, end).let {
                it.years * 12 + it.months
            }.coerceAtLeast(0)
        }.getOrElse { 0 }
    }

    /** 判断合同是否已到期 */
    private fun isContractExpired(contractUntil: String?, currentDate: LocalDate): Boolean {
        if (contractUntil.isNullOrBlank()) return false
        return runCatching {
            val end = LocalDate.parse(contractUntil.take(10))
            !end.isAfter(currentDate)
        }.getOrElse { false }
    }

    /** 计算年龄 */
    private fun computeAge(birthDate: String, currentDate: LocalDate): Int {
        if (birthDate.isBlank()) return 18
        return runCatching {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, currentDate).years
        }.getOrElse { 18 }
    }

    /** 生成新闻 */
    private suspend fun generateNews(
        ctx: RenewalContext,
        playerId: Int,
        title: String,
        body: String,
        type: String
    ) {
        databaseManager.saveNewsDao().insert(
            SaveNewsEntity(
                saveId = saveId,
                newsDate = ctx.currentDate.toString(),
                title = title,
                body = body,
                newsType = type,
                relatedPlayerId = playerId,
                relatedClubId = clubId
            )
        )
    }

    /** save.db 是否就绪 */
    private fun isSaveReady(): Boolean = databaseManager.getSaveDatabaseOrNull() != null

    companion object {
        private const val TAG = "ContractExpiryService"
    }
}

/**
 * 合同到期处理结果（V0.1 09 §三 + §六）。
 */
sealed class ExpiryResult {
    /** 预合同接触（其他俱乐部接触球员） */
    data class PreContractContact(
        val playerId: Int,
        val contactingClubs: List<Int>
    ) : ExpiryResult()

    /** 球员被释放为自由球员 */
    data class Released(val playerId: Int) : ExpiryResult()

    /** 球员签订预合同（V1 简化暂不实际签约，仅保留枚举） */
    data class SignedPreContract(
        val playerId: Int,
        val newClubId: Int
    ) : ExpiryResult()
}
