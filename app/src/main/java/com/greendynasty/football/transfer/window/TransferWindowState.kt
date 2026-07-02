package com.greendynasty.football.transfer.window

import com.greendynasty.football.transfer.config.WindowParams
import java.time.LocalDate
import java.time.Period

/**
 * 转会窗口状态机（V0.1 09 §六 转会窗管理）。
 *
 * 三种状态：
 * - [OPEN]：开窗，可正常报价
 * - [CLOSING_SOON]：截止日临近（剩余天数 ≤ 阈值），UI 显示倒计时警告
 * - [CLOSED]：关窗，禁止新报价（已存在的报价继续处理）
 *
 * 夏窗：7 月 1 日 - 8 月 31 日
 * 冬窗：1 月 1 日 - 1 月 31 日
 *
 * 状态机转换：
 * ```
 * CLOSED --(进入窗口期)--> OPEN
 * OPEN   --(剩余 ≤ 阈值)--> CLOSING_SOON
 * OPEN / CLOSING_SOON --(窗口结束)--> CLOSED
 * ```
 */
enum class TransferWindowStatus(val label: String) {
    OPEN("转会窗开启"),
    CLOSING_SOON("截止日临近"),
    CLOSED("转会窗关闭")
}

/**
 * 转会窗口状态详情。
 *
 * @property status 当前状态
 * @property windowType 当前窗口类型（夏窗 / 冬窗 / 关窗期）
 * @property endDate 窗口结束日期（CLOSED 时为 null）
 * @property daysToDeadline 距离截止日天数（CLOSED 时为 null）
 */
data class TransferWindowState(
    val status: TransferWindowStatus,
    val windowType: WindowType,
    val endDate: LocalDate?,
    val daysToDeadline: Int?
) {

    /** 是否允许新报价（仅 OPEN / CLOSING_SOON 允许） */
    val canMakeOffer: Boolean
        get() = status == TransferWindowStatus.OPEN || status == TransferWindowStatus.CLOSING_SOON

    /** UI 显示的简短描述 */
    val displayText: String
        get() = when (status) {
            TransferWindowStatus.OPEN ->
                if (daysToDeadline != null) "${windowType.label} · 剩 ${daysToDeadline} 天"
                else windowType.label
            TransferWindowStatus.CLOSING_SOON ->
                if (daysToDeadline != null) "${windowType.label}截止 · 剩 ${daysToDeadline} 天"
                else "${windowType.label}截止日"
            TransferWindowStatus.CLOSED -> "转会窗关闭"
        }

    companion object {
        /** 关窗期默认状态 */
        val CLOSED = TransferWindowState(
            status = TransferWindowStatus.CLOSED,
            windowType = WindowType.NONE,
            endDate = null,
            daysToDeadline = null
        )
    }
}

/** 转会窗口类型 */
enum class WindowType(val label: String) {
    SUMMER("夏窗"),
    WINTER("冬窗"),
    NONE("关窗期")
}

/**
 * 转会窗口状态机管理器（V0.1 09 §六）。
 *
 * 根据当前游戏日期计算转会窗口状态。所有阈值参数在 [WindowParams] 中配置。
 *
 * @param params 窗口参数
 */
class TransferWindowManager(private val params: WindowParams = WindowParams()) {

    /**
     * 计算指定日期的转会窗口状态。
     *
     * @param currentDate 当前游戏日期
     * @return 转会窗口状态详情
     */
    fun getState(currentDate: LocalDate): TransferWindowState {
        val summer = summerWindow(currentDate)
        val winter = winterWindow(currentDate)

        return when {
            summer != null -> summer
            winter != null -> winter
            else -> TransferWindowState.CLOSED
        }
    }

    /** 计算夏窗状态（如果当前在夏窗期内） */
    private fun summerWindow(date: LocalDate): TransferWindowState? {
        val startMd = params.summerStartMonth * 100 + params.summerStartDay
        val endMd = params.summerEndMonth * 100 + params.summerEndDay
        val curMd = date.monthValue * 100 + date.dayOfMonth
        if (curMd < startMd || curMd > endMd) return null

        val endDate = LocalDate.of(date.year, params.summerEndMonth, params.summerEndDay)
        val days = Period.between(date, endDate).days
        val status = if (days <= params.summerClosingDays) {
            TransferWindowStatus.CLOSING_SOON
        } else {
            TransferWindowStatus.OPEN
        }
        return TransferWindowState(
            status = status,
            windowType = WindowType.SUMMER,
            endDate = endDate,
            daysToDeadline = days
        )
    }

    /** 计算冬窗状态（如果当前在冬窗期内） */
    private fun winterWindow(date: LocalDate): TransferWindowState? {
        val startMd = params.winterStartMonth * 100 + params.winterStartDay
        val endMd = params.winterEndMonth * 100 + params.winterEndDay
        val curMd = date.monthValue * 100 + date.dayOfMonth
        if (curMd < startMd || curMd > endMd) return null

        val endDate = LocalDate.of(date.year, params.winterEndMonth, params.winterEndDay)
        val days = Period.between(date, endDate).days
        val status = if (days <= params.winterClosingDays) {
            TransferWindowStatus.CLOSING_SOON
        } else {
            TransferWindowStatus.OPEN
        }
        return TransferWindowState(
            status = status,
            windowType = WindowType.WINTER,
            endDate = endDate,
            daysToDeadline = days
        )
    }
}
