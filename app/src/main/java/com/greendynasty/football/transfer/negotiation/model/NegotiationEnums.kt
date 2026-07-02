package com.greendynasty.football.transfer.negotiation.model

/**
 * T11 报价谈判模块枚举集合（V0.1 `09_转会_合同_经纪人系统.md`）。
 *
 * 包含：
 * - [OfferType]：5 种报价类型
 * - [OfferStatus]：报价状态机
 * - [RolePromise]：5 档角色承诺
 * - [NegotiationStage]：谈判阶段
 * - [SellerReaction] / [PlayerReaction]：谈判反应
 * - [PlayerImportance]：5 档球员重要性
 */

/**
 * 5 种报价类型（V0.1 09 §三 第 3 步）。
 */
enum class OfferType(val label: String) {
    /** 永久转会 */
    PERMANENT("永久转会"),

    /** 租借 */
    LOAN("租借"),

    /** 租借带买断 */
    LOAN_WITH_BUYOUT("租借带买断"),

    /** 自由签约（无主球员） */
    FREE_SIGNING("自由签约"),

    /** 预合同（合同剩 ≤6 个月接触） */
    PRE_CONTRACT("预合同");

    companion object {
        /** 是否需要卖方俱乐部评估 */
        fun needsSeller(type: OfferType): Boolean = when (type) {
            PERMANENT, LOAN, LOAN_WITH_BUYOUT -> true
            FREE_SIGNING, PRE_CONTRACT -> false
        }
    }
}

/**
 * 报价状态机（V0.1 09 §三 12 步流程）。
 */
enum class OfferStatus(val label: String) {
    DRAFT("草稿"),
    SUBMITTED("已提交"),
    SELLER_EVALUATING("卖方评估中"),
    SELLER_ACCEPTED("卖方接受"),
    SELLER_REJECTED("卖方拒绝"),
    SELLER_COUNTERED("卖方还价"),
    PLAYER_NEGOTIATING("球员谈判中"),
    PLAYER_ACCEPTED("球员接受"),
    PLAYER_REJECTED("球员拒绝"),
    MEDICAL_PENDING("体检待进行"),
    MEDICAL_PASSED("体检通过"),
    MEDICAL_FAILED("体检失败"),
    COMPLETED("转会完成"),
    COLLAPSED("谈判破裂"),
    WITHDRAWN("已撤回");

    companion object {
        /** 终态（不再变化） */
        val TERMINAL = setOf(COMPLETED, COLLAPSED, WITHDRAWN, SELLER_REJECTED, PLAYER_REJECTED, MEDICAL_FAILED)
    }
}

/**
 * 5 档角色承诺（V0.1 09 §六）。
 */
enum class RolePromise(val label: String, val squadRole: String) {
    KEY_PLAYER("关键球员", "key_player"),
    STARTER("主力", "starter"),
    ROTATION("轮换", "rotation"),
    BACKUP("替补", "backup"),
    ACADEMY_DEV("青训培养", "prospect")
}

/**
 * 谈判阶段（V0.1 09 §三）。
 */
enum class NegotiationStage(val label: String) {
    SELLER_EVALUATION("卖方评估"),
    SELLER_NEGOTIATION("卖方谈判"),
    PLAYER_WILLINGNESS("加盟意愿"),
    PLAYER_NEGOTIATION("球员谈判"),
    MEDICAL("体检"),
    COMPLETION("完成")
}

/**
 * 谈判反应（V0.1 09 §四/§五）。
 */
enum class Reaction(val label: String) {
    ACCEPT("接受"),
    REJECT("拒绝"),
    COUNTER("还价"),
    WALK_AWAY("破裂")
}

/**
 * 5 档球员重要性（V0.1 09 §四.2）。
 *
 * 决定心理价位系数与卖方接受概率。
 *
 * @property reverseScore 反向归一：越重要越不想卖（用于接受概率计算）
 */
enum class PlayerImportance(
    val label: String,
    val priceMultiplierKey: String,
    val reverseScore: Double
) {
    KEY("关键球员", "KEY", 0.0),
    STARTER("主力", "STARTER", 0.2),
    ROTATION("轮换", "ROTATION", 0.5),
    BACKUP("替补", "BACKUP", 0.8),
    LISTED("挂牌", "LISTED", 1.0)
}

/**
 * 体检结果（V0.1 09 §三 第 9 步）。
 */
enum class MedicalResult(val label: String) {
    PASSED("通过"),
    PASSED_WITH_CONDITIONS("带条件通过"),
    FAILED("未通过")
}
