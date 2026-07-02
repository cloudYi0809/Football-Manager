package com.greendynasty.football.transfer.ai.executor

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.data.save.entity.SaveNewsEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.data.save.entity.SaveTransferOfferEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T13.5 AI 转会执行器（更新 save.db）。
 *
 * 负责将 AI 决策落库：
 * 1. [executeBuy]：买入球员（更新球员俱乐部/合同/工资，扣减买方预算，增加卖方预算，记录报价，生成新闻）
 * 2. [executeSell]：卖出球员（更新球员俱乐部为 null，增加卖方预算，记录报价，生成新闻）
 * 3. [countWindowTransactions]：统计本窗已完成交易数（用于防崩坏约束）
 *
 * 三库分离铁律：history 只读、save 可写、cache 可重建。
 * 与 T11 [com.greendynasty.football.transfer.negotiation.repository.NegotiationRepository.completeTransfer]
 * 保持一致的数据更新模式（球员俱乐部/合同/预算/新闻/报价记录）。
 *
 * @param databaseManager 三库管理入口
 */
class AiTransferExecutor(
    private val databaseManager: DatabaseManager
) {

    /**
     * 执行买入转会。
     *
     * 1. 更新球员俱乐部、工资、合同、队内角色
     * 2. 扣减买方转会预算 + 余额
     * 3. 增加卖方转会预算 + 余额（有卖方时）
     * 4. 记录报价单（status=completed）
     * 5. 生成转会新闻
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param fromClubId 卖方俱乐部 ID（null 表示自由签约）
     * @param toClubId 买方俱乐部 ID
     * @param fee 转会费
     * @param wage 周薪
     * @param contractYears 合同年限
     * @param squadRole 队内角色
     * @param currentDate 当前游戏日期
     * @return true 表示执行成功
     */
    suspend fun executeBuy(
        saveId: Int,
        playerId: Int,
        fromClubId: Int?,
        toClubId: Int,
        fee: Int,
        wage: Int,
        contractYears: Int,
        squadRole: String,
        currentDate: LocalDate
    ): Boolean = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext false
        try {
            val state = saveDb.savePlayerStateDao().getByPlayer(saveId, playerId)
                ?: return@withContext false
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)
            val contractUntil = currentDate.plusYears(contractYears.toLong()).toString()

            // 1. 更新球员俱乐部与合同
            saveDb.savePlayerStateDao().update(
                state.copy(
                    currentClubId = toClubId,
                    wage = wage,
                    contractUntil = contractUntil,
                    squadRole = squadRole
                )
            )

            // 2. 扣减买方预算 + 余额
            val buyerClub = saveDb.saveClubStateDao().getByClub(saveId, toClubId)
            if (buyerClub != null) {
                saveDb.saveClubStateDao().updateTransferBudget(
                    saveId, toClubId, buyerClub.transferBudget - fee
                )
                saveDb.saveClubStateDao().updateBalance(
                    saveId, toClubId, buyerClub.balance - fee
                )
            }

            // 3. 增加卖方预算 + 余额（有卖方时）
            if (fromClubId != null && fromClubId != toClubId) {
                val sellerClub = saveDb.saveClubStateDao().getByClub(saveId, fromClubId)
                if (sellerClub != null) {
                    saveDb.saveClubStateDao().updateTransferBudget(
                        saveId, fromClubId, sellerClub.transferBudget + fee
                    )
                    saveDb.saveClubStateDao().updateBalance(
                        saveId, fromClubId, sellerClub.balance + fee
                    )
                }
            }

            // 4. 记录报价单
            saveDb.saveTransferOfferDao().insert(
                SaveTransferOfferEntity(
                    offerId = 0,
                    saveId = saveId,
                    playerId = playerId,
                    fromClubId = fromClubId,
                    toClubId = toClubId,
                    offerType = "transfer",
                    fee = fee,
                    wageOffer = wage,
                    contractYears = contractYears,
                    status = "completed",
                    createdDate = currentDate.toString(),
                    expiresDate = null,
                    negotiationType = "PERMANENT",
                    psychologicalPrice = fee
                )
            )

            // 5. 生成新闻
            val playerName = player?.displayName ?: player?.realName ?: "球员#$playerId"
            val fromClubName = fromClubId?.let {
                databaseManager.historyClubDao().getClub(it)?.clubName
            } ?: "自由身"
            val toClubName = databaseManager.historyClubDao().getClub(toClubId)?.clubName ?: "未知俱乐部"
            saveDb.saveNewsDao().insert(
                SaveNewsEntity(
                    saveId = saveId,
                    newsDate = currentDate.toString(),
                    title = "$playerName 加盟 $toClubName",
                    body = "$playerName 从 $fromClubName 转会至 $toClubName，转会费 ${formatMoney(fee)}。" +
                        "合同为期 $contractYears 年，周薪 ${formatMoney(wage)}。",
                    newsType = "transfer",
                    relatedPlayerId = playerId,
                    relatedClubId = toClubId,
                    isRead = 0
                )
            )

            Log.d(TAG, "AI 买入完成: playerId=$playerId, from=$fromClubId, to=$toClubId, fee=$fee")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AI 买入失败: playerId=$playerId", e)
            false
        }
    }

    /**
     * 执行卖出转会。
     *
     * 基础版简化：将球员从卖方阵容移出（currentClubId 设为 null），
     * 增加卖方预算。买方为外部俱乐部（不具体追踪）。
     *
     * T18 完整版将实现买方匹配（寻找需要该位置的 AI 俱乐部）。
     *
     * 1. 更新球员俱乐部为 null（离开卖方）
     * 2. 增加卖方转会预算 + 余额
     * 3. 记录报价单（status=completed）
     * 4. 生成转会新闻
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param fromClubId 卖方俱乐部 ID
     * @param fee 转会费
     * @param currentDate 当前游戏日期
     * @return true 表示执行成功
     */
    suspend fun executeSell(
        saveId: Int,
        playerId: Int,
        fromClubId: Int,
        fee: Int,
        currentDate: LocalDate
    ): Boolean = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext false
        try {
            val state = saveDb.savePlayerStateDao().getByPlayer(saveId, playerId)
                ?: return@withContext false
            val player = databaseManager.historyPlayerDao().getPlayer(playerId)

            // 1. 更新球员俱乐部为 null（离开卖方，基础版不追踪买方）
            saveDb.savePlayerStateDao().update(
                state.copy(currentClubId = null)
            )

            // 2. 增加卖方预算 + 余额
            val sellerClub = saveDb.saveClubStateDao().getByClub(saveId, fromClubId)
            if (sellerClub != null) {
                saveDb.saveClubStateDao().updateTransferBudget(
                    saveId, fromClubId, sellerClub.transferBudget + fee
                )
                saveDb.saveClubStateDao().updateBalance(
                    saveId, fromClubId, sellerClub.balance + fee
                )
            }

            // 3. 记录报价单
            saveDb.saveTransferOfferDao().insert(
                SaveTransferOfferEntity(
                    offerId = 0,
                    saveId = saveId,
                    playerId = playerId,
                    fromClubId = fromClubId,
                    toClubId = null, // 基础版买方不追踪
                    offerType = "transfer",
                    fee = fee,
                    wageOffer = 0,
                    contractYears = null,
                    status = "completed",
                    createdDate = currentDate.toString(),
                    expiresDate = null,
                    negotiationType = "PERMANENT",
                    psychologicalPrice = fee
                )
            )

            // 4. 生成新闻
            val playerName = player?.displayName ?: player?.realName ?: "球员#$playerId"
            val fromClubName = databaseManager.historyClubDao().getClub(fromClubId)?.clubName ?: "未知俱乐部"
            saveDb.saveNewsDao().insert(
                SaveNewsEntity(
                    saveId = saveId,
                    newsDate = currentDate.toString(),
                    title = "$playerName 离开 $fromClubName",
                    body = "$playerName 从 $fromClubName 转出，转会费 ${formatMoney(fee)}。",
                    newsType = "transfer",
                    relatedPlayerId = playerId,
                    relatedClubId = fromClubId,
                    isRead = 0
                )
            )

            Log.d(TAG, "AI 卖出完成: playerId=$playerId, from=$fromClubId, fee=$fee")
            true
        } catch (e: Exception) {
            Log.e(TAG, "AI 卖出失败: playerId=$playerId", e)
            false
        }
    }

    /**
     * 统计本窗已完成交易数（用于防崩坏约束）。
     *
     * 统计该俱乐部在当前转会窗期间已完成的转会数量（买入 + 卖出）。
     * 转会窗期间通过 [TransferWindowManager] 的窗口起止日期判定。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentDate 当前游戏日期
     * @return 本窗已完成交易数
     */
    suspend fun countWindowTransactions(
        saveId: Int,
        clubId: Int,
        currentDate: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext 0
        try {
            // 计算当前转会窗起始日期
            val windowStart = computeWindowStartDate(currentDate)

            // 查询该俱乐部全部报价记录
            val offers = saveDb.saveTransferOfferDao().getByClub(saveId, clubId)

            // 统计本窗已完成交易
            offers.count { offer ->
                offer.status == "completed" &&
                    runCatching {
                        LocalDate.parse(offer.createdDate.take(10)).isAfter(windowStart.minusDays(1))
                    }.getOrElse { false }
            }
        } catch (e: Exception) {
            Log.w(TAG, "统计窗口交易数失败: clubId=$clubId", e)
            0
        }
    }

    /**
     * 计算当前转会窗的起始日期。
     *
     * - 夏窗：7 月 1 日 - 8 月 31 日 → 起始为当年 7 月 1 日
     * - 冬窗：1 月 1 日 - 1 月 31 日 → 起始为当年 1 月 1 日
     * - 关窗期：返回最近一个窗口的起始日期
     *
     * @param currentDate 当前日期
     * @return 转会窗起始日期
     */
    private fun computeWindowStartDate(currentDate: LocalDate): LocalDate {
        val month = currentDate.monthValue
        return when {
            // 冬窗期（1 月）
            month == 1 -> LocalDate.of(currentDate.year, 1, 1)
            // 夏窗期（7-8 月）
            month in 7..8 -> LocalDate.of(currentDate.year, 7, 1)
            // 关窗期：上半年（2-6 月）返回当年冬窗起始，下半年（9-12 月）返回当年夏窗起始
            month in 2..6 -> LocalDate.of(currentDate.year, 1, 1)
            else -> LocalDate.of(currentDate.year, 7, 1) // 9-12 月
        }
    }

    /** 格式化金额 */
    private fun formatMoney(amount: Int): String {
        return when {
            amount >= 100_000_000 -> String.format("%.2f 亿", amount / 100_000_000.0)
            amount >= 10_000 -> String.format("%.1f 万", amount / 10_000.0)
            else -> amount.toString()
        }
    }

    companion object {
        private const val TAG = "AiTransferExecutor"
    }
}
