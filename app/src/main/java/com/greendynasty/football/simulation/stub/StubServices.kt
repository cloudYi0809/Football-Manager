package com.greendynasty.football.simulation.stub

import com.greendynasty.football.simulation.api.AdvanceContext
import java.time.LocalDate

/**
 * T08+ 未实现系统的 stub 接口集合
 *
 * 这些接口为 T07 每日推进提供占位实现，确保推进流程不被阻塞。
 * 后续 T08-T24 实现后替换为真实服务。
 *
 * 铁律：stub 只记录日志 + 返回空结果，不修改任何数据，不抛异常。
 */

// ==================== T18 AI 调度器 stub ====================

/**
 * AI 俱乐部调度器 stub（T18）
 * 活跃范围俱乐部的 AI 决策：转会/战术/阵容/训练计划
 */
class AiSchedulerStub {

    /**
     * 每日推进时 AI 俱乐部行动
     *
     * @param currentDate 当前日期
     * @param saveId 存档 ID
     */
    suspend fun onDailyAdvance(currentDate: LocalDate, saveId: Int) {
        // TODO: T18 接入后实现 AI 俱乐部决策（转会报价/战术调整/阵容轮换）
    }
}

// ==================== T20 蝴蝶事件服务 stub ====================

/**
 * 蝴蝶效应事件服务 stub（T20）
 * 检查历史事件是否已被蝴蝶效应改写
 */
class ButterflyEventServiceStub {

    /**
     * 检查指定历史事件是否已被蝴蝶效应改写
     *
     * @param eventId 历史事件 ID
     * @param saveId 存档 ID
     * @return true 表示已被改写（不再触发原始历史）
     */
    fun isEventRewritten(eventId: Int, saveId: Int): Boolean {
        // TODO: T20 接入后查询 butterfly_event 表
        return false
    }
}

// ==================== T09 球员成长服务 stub ====================

/**
 * 球员成长服务 stub（T09）
 * 每月成长结算：训练进度兑现为 CA 提升
 */
class PlayerGrowthServiceStub {

    /**
     * 每月成长结算
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param currentDate 当前日期
     */
    suspend fun monthlyGrowthSettlement(saveId: Int, clubId: Int, currentDate: LocalDate) {
        // TODO: T09 接入后实现月结成长（训练进度 → CA 提升 / PA 判断 / 位置成长）
    }
}

// ==================== T17 经济服务 stub ====================

/**
 * 经济服务 stub（T17）
 * 财政计算：工资/门票/赞助/通胀
 */
class EconomyServiceStub {

    /**
     * 计算月度工资支出
     *
     * @param ctx 推进上下文
     * @return 工资总额
     */
    suspend fun calculateMonthlyWage(ctx: AdvanceContext): Int {
        // TODO: T17 接入后实现通胀调整的工资计算
        return 0
    }

    /**
     * 计算月度门票收入
     */
    suspend fun calculateTicketRevenue(ctx: AdvanceContext): Int {
        // TODO: T17 接入后实现门票收入（基于上座率 × 票价 × 场次）
        return 0
    }

    /**
     * 计算月度赞助收入
     */
    suspend fun calculateSponsorRevenue(ctx: AdvanceContext): Int {
        // TODO: T17 接入后实现赞助收入
        return 0
    }
}

// ==================== T22 董事会服务 stub ====================

/**
 * 董事会月评结果
 */
data class BoardReviewResult(
    val satisfaction: Int,
    val summary: String
)

/**
 * 董事会服务 stub（T22）
 * 月度董事会评审：成绩/财政/球迷满意度综合评估
 */
class BoardServiceStub {

    /**
     * 执行月度董事会评审
     */
    suspend fun conductMonthlyReview(ctx: AdvanceContext): BoardReviewResult {
        // TODO: T22 接入后实现董事会月评（成绩/财政/转会/青训综合评分）
        return BoardReviewResult(
            satisfaction = 60,
            summary = "董事会月评：表现平稳（stub）"
        )
    }
}

// ==================== T19 赛季归档服务 stub ====================

/**
 * 赛季归档服务 stub（T19）
 * 赛季结束时归档：比赛记录/积分榜/球员统计/奖项
 */
class SeasonArchiverStub {

    /**
     * 归档赛季数据
     */
    suspend fun archiveSeason(ctx: AdvanceContext) {
        // TODO: T19 接入后实现赛季归档（写入 season_archive 表）
    }
}
