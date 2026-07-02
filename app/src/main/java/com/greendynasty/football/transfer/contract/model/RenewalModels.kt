package com.greendynasty.football.transfer.contract.model

import java.time.LocalDate

/**
 * T12 合同续约模块枚举与上下文集合（V0.1 `09_转会_合同_经纪人系统.md` §六 + V0.2 `07_经济通胀_身价_工资模型.md`）。
 *
 * 包含：
 * - [InitiationType]：续约触发类型
 * - [RenewalStatus]：续约状态机
 * - [ReminderLevel]：续约提醒级别（12/6/1 个月）
 * - [RenewalContext]：续约上下文
 * - [RenewalSpecialTerms]：续约特有 3 项条款（涨薪 / 退役 / 青训保护）
 */

/**
 * 续约触发类型（V0.1 09 §六）。
 */
enum class InitiationType(val label: String) {
    /** 玩家主动发起 */
    PLAYER_INITIATED("玩家发起"),

    /** 球员主动要求续约（6 个月提醒触发） */
    PLAYER_DEMAND("球员要求"),

    /** 12 个月提醒触发 */
    REMINDER_12M("12 月提醒"),

    /** 6 个月提醒触发 */
    REMINDER_6M("6 月提醒"),

    /** 1 个月提醒触发 */
    REMINDER_1M("1 月提醒"),

    /** 青年球员转职业合同（T16 触发） */
    YOUTH_TO_PRO("青训转职业"),

    /** 外租球员续约 */
    LOANED_PLAYER("外租续约")
}

/**
 * 续约状态机（V0.1 09 §六）。
 */
enum class RenewalStatus(val label: String) {
    /** 草稿 */
    DRAFT("草稿"),

    /** 已提交报价 */
    SUBMITTED("已提交"),

    /** 球员评估中 */
    PLAYER_EVALUATING("评估中"),

    /** 球员接受 */
    PLAYER_ACCEPTED("已接受"),

    /** 球员拒绝 */
    PLAYER_REJECTED("已拒绝"),

    /** 球员还价 */
    PLAYER_COUNTERED("已还价"),

    /** 续约完成 */
    COMPLETED("已完成"),

    /** 谈判破裂 */
    COLLAPSED("已破裂"),

    /** 玩家撤回 */
    WITHDRAWN("已撤回"),

    /** 报价过期 */
    EXPIRED("已过期");

    companion object {
        /** 终态 */
        val TERMINAL = setOf(COMPLETED, COLLAPSED, WITHDRAWN, PLAYER_REJECTED, EXPIRED)
        /** 进行中 */
        val ACTIVE = setOf(SUBMITTED, PLAYER_EVALUATING, PLAYER_COUNTERED)
    }
}

/**
 * 续约提醒级别（V0.1 09 §六 三档提醒）。
 */
enum class ReminderLevel(val label: String, val urgency: Int) {
    /** 12 个月提醒（早期预警） */
    EARLY_WARNING("12 个月预警", 1),

    /** 6 个月提醒（紧急，Bosman 可接触） */
    URGENT("6 个月紧急", 2),

    /** 1 个月提醒（最后机会） */
    FINAL("1 个月最后", 3),

    /** 已到期（变自由球员） */
    EXPIRED("已到期", 4)
}

/**
 * 续约上下文（贯穿续约流程的会话级状态）。
 *
 * @property saveId 存档 ID
 * @property clubId 玩家俱乐部 ID
 * @property currentDate 当前游戏日期
 * @property seasonId 当前赛季 ID
 */
data class RenewalContext(
    val saveId: Int,
    val clubId: Int,
    val currentDate: LocalDate,
    val seasonId: Int
)

/**
 * 续约特有条款 3 项（V0.1 09 §六，与 T11 的 7 项特殊条款叠加使用）。
 *
 * - [PerformanceRaiseClause]：涨薪条款（表现达标自动涨薪，对球员有利）
 * - [VeteranClause]：退役条款（老将一年一签，对俱乐部有利）
 * - [AcademyProtectionClause]：青训保护条款（特殊解约金，对球员有利）
 */
data class RenewalSpecialTerms(
    val performanceRaiseClause: PerformanceRaiseClause? = null,
    val veteranClause: VeteranClause? = null,
    val academyProtectionClause: AcademyProtectionClause? = null
) {
    /** 是否含任一续约特有条款 */
    fun hasAny(): Boolean =
        performanceRaiseClause != null || veteranClause != null || academyProtectionClause != null
}

/** 涨薪条款（表现达标自动涨薪） */
data class PerformanceRaiseClause(
    /** 触发条件描述（如 "season_apps >= 30"） */
    val triggerCondition: String,
    /** 涨薪百分比 */
    val raisePercent: Int,
    /** 最大涨薪次数 */
    val maxRaises: Int
)

/** 退役条款（老将一年一签） */
data class VeteranClause(
    /** 表现达标自动续约一年 */
    val autoExtension: Boolean,
    /** 最低出场数 */
    val minAppearances: Int,
    /** 最低评分 */
    val minRating: Double
)

/** 青训保护条款 */
data class AcademyProtectionClause(
    /** 特殊解约金上限（低于市场价） */
    val maxReleaseClause: Int,
    /** 最短合同年限 */
    val minContractYears: Int,
    /** 培养补偿金 */
    val trainingCompensation: Int
)
