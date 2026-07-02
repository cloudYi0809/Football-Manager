package com.greendynasty.football.prospect.model

/**
 * T15 历史新星状态枚举（V0.2 06 §二.1 + 08 §三）。
 *
 * 状态机：
 * ```
 * PENDING ──时间到──→ ACTIVE ──球探发现──→ DISCOVERED ──玩家签约──→ SIGNED_EARLY
 *                              │                          │
 *                              ├──默认路径──→ DEFAULT_PATH─┤
 *                              │                          │
 *                              └──AI 签约──→ SIGNED_EARLY
 * ```
 *
 * 持久化：以 [code] 字符串存入 prospect_state.status 列。
 */
enum class ProspectStatus(val code: String, val display: String) {
    /** 未到可发现日期（V1 简化：不持久化，仅在内存判定）。 */
    PENDING("PENDING", "未激活"),

    /** 已激活，可被球探发现。 */
    ACTIVE("ACTIVE", "可发现"),

    /** 已被球探发现（含或不含报告升级）。 */
    DISCOVERED("DISCOVERED", "已发现"),

    /** 被玩家提前签约（蝴蝶效应已触发）。 */
    SIGNED_EARLY("SIGNED_EARLY", "提前签约"),

    /** 按历史默认路径执行中（未被干预）。 */
    DEFAULT_PATH("DEFAULT_PATH", "默认路径"),

    /** 已完成职业路径（V1 简化：年龄超过 35 或路径全部执行完）。 */
    COMPLETED("COMPLETED", "路径完成");

    companion object {
        fun fromCode(code: String): ProspectStatus? = values().find { it.code == code }
    }
}

/**
 * T15 历史新星路径事件类型（V0.2 08 §三 + 06 §二.1）。
 *
 * 用于 prospect_path_event.event_type 列。
 */
enum class ProspectPathEventType(val code: String, val display: String) {
    ACTIVATED("ACTIVATED", "激活可发现"),
    DISCOVERED("DISCOVERED", "被球探发现"),
    TRANSFER("TRANSFER", "按历史路径转会"),
    CA_PA_PROGRESS("CA_PA_PROGRESS", "CA/PA 月度推进"),
    EARLY_SIGNED("EARLY_SIGNED", "被玩家提前签约"),
    AI_SIGNED("AI_SIGNED", "被 AI 俱乐部签约"),
    PATH_INTERRUPTED("PATH_INTERRUPTED", "默认路径被打断"),
    BUTTERFLY_TRIGGERED("BUTTERFLY_TRIGGERED", "触发蝴蝶效应");

    companion object {
        fun fromCode(code: String): ProspectPathEventType? = values().find { it.code == code }
    }
}

/**
 * 历史新星行动结果（V0.2 06 §二.1）。
 *
 * 由 [com.greendynasty.football.prospect.discovery.ProspectDiscoveryService] /
 * [com.greendynasty.football.prospect.path.ProspectPathSimulator] 产出，
 * 交由 T07 每日推进转换为新闻/待办。
 */
data class ProspectResult(
    val prospectId: Int,
    val action: ProspectAction,
    val playerId: Int,
    val clubId: Int?,
    val butterflyEventTriggered: Boolean,
    val message: String
)

/** 历史新星行动类型。 */
enum class ProspectAction {
    DISCOVERED,
    DEFAULT_PATH_EXECUTED,
    EARLY_SIGNED,
    AI_SIGNED,
    PATH_INTERRUPTED,
    CA_PA_PROGRESS
}

/**
 * 历史转会记录（V0.2 08 §三，从 history.historical_prospect_pool.default_transfer_path JSON 解析）。
 *
 * @param fromClubId 转出俱乐部 ID（首条记录可能为 null）
 * @param toClubId 转入俱乐部 ID
 * @param transferDate 转会日期（yyyy-MM-dd）
 * @param transferFee 转会费
 * @param isLoan 是否租借（0/1 → Boolean）
 */
data class DefaultTransferRecord(
    val fromClubId: Int?,
    val toClubId: Int,
    val transferDate: String,
    val transferFee: Int,
    val isLoan: Boolean = false
)

/**
 * 历史新星领域模型（聚合 history.historical_prospect_pool + history.player 信息）。
 *
 * 由 [com.greendynasty.football.prospect.repository.ProspectRepository] 构造，
 * 供 ViewModel / UI 使用。
 */
data class HistoricalProspect(
    val prospectId: Int,
    val playerId: Int,
    val playerName: String,
    val realName: String,
    val discoverableFrom: String,
    val defaultYouthClubId: Int?,
    val defaultFirstTeamClubId: Int?,
    val defaultBreakthroughYear: Int,
    val defaultTransferPath: List<DefaultTransferRecord>,
    val initialRegionCode: String,
    val hiddenUntilDiscovered: Boolean,
    val legendLevel: Int,
    val createdScenario: String?,
    val tags: List<String>,
    val nationality: String?,
    val birthDate: String?,
    val primaryPosition: String?
)

/**
 * 球员发现结果（V0.2 08 §三.5）。
 */
data class ProspectDiscovery(
    val prospectId: Int,
    val playerId: Int,
    val playerName: String,
    val playerPosition: String,
    val playerRegion: String,
    val probability: Double,
    val message: String
)
