package com.greendynasty.football.match.model

/**
 * 比赛事件（V0.2 04 §七）
 *
 * 每个 tick 内由 EventLayer 抽样产出的事件实体，
 * 用于回放、统计与赛后评分。
 */
data class MatchEvent(
    /** 事件发生分钟（1-90+，含补时） */
    val minute: Int,
    /** 事件类型 */
    val type: EventType,
    /** 事件归属方：主队 / 客队 / 中立 */
    val teamSide: TeamSide,
    /** 主要参与球员 ID（进球者、扑救者等），可为空 */
    val playerId: String?,
    /** 次要参与球员 ID（助攻者、被侵犯者等），可为空 */
    val secondaryPlayerId: String?,
    /** 事件质量 0.0-1.0，影响后续转化与评分 */
    val quality: Double,
    /** 文字描述，便于调试与回放 */
    val description: String
)

/**
 * 事件类型枚举（V0.2 04 §七 事件概率基础表）
 *
 * 包含控球 / 进攻 / 射门 / 进球 / 定位球 / 牌 / 伤病等全部事件类别。
 */
enum class EventType {
    /** 普通控球推进 */
    POSSESSION,
    /** 普通进攻 */
    NORMAL_ATTACK,
    /** 危险进攻 */
    DANGEROUS_ATTACK,
    /** 射门（含偏出） */
    SHOT,
    /** 射正 */
    SHOT_ON_TARGET,
    /** 进球 */
    GOAL,
    /** 角球 */
    CORNER,
    /** 任意球 */
    FREE_KICK,
    /** 点球 */
    PENALTY,
    /** 黄牌 */
    YELLOW_CARD,
    /** 红牌 */
    RED_CARD,
    /** 伤病（被迫换人） */
    INJURY,
    /** 门将扑救 */
    SAVE,
    /** 后卫解围 */
    CLEARANCE,
    /** 越位 */
    OFFSIDE,
    /** 犯规 */
    FOUL,
    /** 反击事件 */
    COUNTER_ATTACK,
    /** 无明显事件 */
    NOTHING
}

/**
 * 事件归属方
 */
enum class TeamSide {
    /** 主队 */
    HOME,
    /** 客队 */
    AWAY,
    /** 中立（开场、天气等） */
    NEUTRAL
}
