package com.greendynasty.football.simulation.monthly

import android.util.Log
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.simulation.api.AdvanceContext
import com.greendynasty.football.simulation.api.AdvanceEvent
import com.greendynasty.football.simulation.api.AdvanceEventType
import com.greendynasty.football.simulation.api.EventPriority
import com.greendynasty.football.simulation.config.ProgressionConfig
import com.greendynasty.football.simulation.stub.BoardServiceStub
import com.greendynasty.football.simulation.stub.EconomyServiceStub
import com.greendynasty.football.simulation.stub.PlayerGrowthServiceStub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 每月任务调度器（T07 方案 §六 MonthlyTaskExecutor）
 *
 * V0.1 11 §二.3 每月任务（月初执行），共 8 项：
 * 1. 工资支出（玩家俱乐部）
 * 2. 门票收入
 * 3. 赞助收入
 * 4. 青训投入
 * 5. 医疗投入
 * 6. 数据部门投入
 * 7. 董事会月评
 * 8. 球员成长月结（活跃范围全部俱乐部）
 *
 * 仅在月初（isMonthStart=true）执行。V1 简化实现：
 * - 财政：基于存档俱乐部状态计算，工资支出 = wageBudget，门票/赞助收入用 stub
 * - 投入扣除：V1 简化，因 SaveClubStateEntity 无 youthLevel/medicalLevel/dataLevel 字段，暂不扣除
 * - 董事会月评：调用 BoardServiceStub
 * - 球员成长月结：调用 PlayerGrowthServiceStub
 *
 * @param databaseManager 三库管理入口
 * @param config 推进配置
 * @param economyService 经济服务 stub（T17）
 * @param growthService 成长服务 stub（T09）
 * @param boardService 董事会服务 stub（T22）
 */
class MonthlyTaskScheduler(
    private val databaseManager: DatabaseManager,
    private val config: ProgressionConfig,
    private val economyService: EconomyServiceStub,
    private val growthService: PlayerGrowthServiceStub,
    private val boardService: BoardServiceStub
) {

    /**
     * 执行 8 项每月任务
     *
     * @param ctx 推进上下文
     * @return 每月任务产生的事件列表
     */
    suspend fun execute(ctx: AdvanceContext): List<AdvanceEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AdvanceEvent>()
        val saveDb = databaseManager.getSaveDatabaseOrNull() ?: return@withContext emptyList()

        // 仅玩家俱乐部处理财政（其他俱乐部 V1 简化）
        val managerClub = saveDb.saveClubStateDao().getByClub(ctx.saveId, ctx.managerClubId)
        if (managerClub != null) {
            try {
                // 1. 工资支出（月度工资 = wageBudget × 4 周）
                val totalWage = if (config.financialSettlement) {
                    economyService.calculateMonthlyWage(ctx).takeIf { it > 0 } ?: (managerClub.wageBudget * 4)
                } else {
                    0
                }

                // 2. 门票收入
                val ticketRevenue = if (config.financialSettlement) {
                    economyService.calculateTicketRevenue(ctx).takeIf { it > 0 } ?: 200_000
                } else {
                    0
                }

                // 3. 赞助收入
                val sponsorRevenue = if (config.financialSettlement) {
                    economyService.calculateSponsorRevenue(ctx).takeIf { it > 0 } ?: 500_000
                } else {
                    0
                }

                // 4-6. 投入扣除（V1 简化：SaveClubStateEntity 无 youthLevel/medicalLevel/dataLevel 字段）
                // TODO: T0M6 设施系统接入后，从 club_state 读取设施等级并扣除投入

                // 计算净收支
                val netIncome = ticketRevenue + sponsorRevenue - totalWage
                val newBalance = managerClub.balance + netIncome

                saveDb.saveClubStateDao().update(
                    managerClub.copy(balance = newBalance)
                )

                Log.i(TAG, "月度财政：工资=$totalWage, 门票=$ticketRevenue, 赞助=$sponsorRevenue, 净收支=$netIncome, 新余额=$newBalance")
            } catch (e: Exception) {
                Log.w(TAG, "月度财政计算失败: ${e.message}")
            }
        }

        // 7. 董事会月评
        if (config.boardReview) {
            try {
                val boardReview = boardService.conductMonthlyReview(ctx)
                // 更新董事会满意度
                val club = saveDb.saveClubStateDao().getByClub(ctx.saveId, ctx.managerClubId)
                if (club != null && club.boardSatisfaction != boardReview.satisfaction) {
                    saveDb.saveClubStateDao().update(
                        club.copy(boardSatisfaction = boardReview.satisfaction)
                    )
                }
                events.add(
                    AdvanceEvent(
                        type = AdvanceEventType.BOARD_REVIEW,
                        description = "董事会月评：满意度 ${boardReview.satisfaction}（${boardReview.summary}）",
                        clubId = ctx.managerClubId,
                        playerId = null,
                        priority = EventPriority.HIGH
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "董事会月评失败: ${e.message}")
            }
        }

        // 8. 球员成长月结（活跃范围全部俱乐部）
        if (config.growthSettlement) {
            try {
                val clubs = saveDb.saveClubStateDao().getAll(ctx.saveId)
                for (club in clubs) {
                    growthService.monthlyGrowthSettlement(ctx.saveId, club.clubId, ctx.currentDate)
                }
                Log.i(TAG, "球员成长月结完成：${clubs.size} 个俱乐部")
            } catch (e: Exception) {
                Log.w(TAG, "球员成长月结失败: ${e.message}")
            }
        }

        events
    }

    companion object {
        private const val TAG = "MonthlyTaskScheduler"
    }
}
