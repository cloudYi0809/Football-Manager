package com.greendynasty.football.season.compress

import kotlinx.serialization.Serializable

/**
 * T19 压缩后的比赛事件（V0.2 §七.2）
 *
 * 赛季归档时，将详细比赛事件（控球/射门/角球/犯规等）压缩为：
 * - 比分 + xG
 * - 进球（仅 minute + scorer + assist）
 * - 红黄牌（仅 minute + player + type）
 * - 关键评分 Top N
 *
 * 通过 [CompressedMatchEventJson] 序列化为 JSON 字符串存入 `compressed_match.summary_json`，
 * 同时清空 `save_match.match_stats_json` 详细统计字段以回收空间。
 */
@Serializable
data class CompressedMatchEvent(
    val matchId: Int,
    val homeScore: Int,
    val awayScore: Int,
    val homeXg: Double = 0.0,
    val awayXg: Double = 0.0,
    val goals: List<CompressedGoal>,
    val cards: List<CompressedCard>,
    val topRatedPlayers: List<CompressedRating>
)

/** 进球事件（仅保留分钟 / 进球球员 / 助攻球员） */
@Serializable
data class CompressedGoal(
    val minute: Int,
    val scorerId: Int? = null,
    val assistId: Int? = null
)

/** 红黄牌事件（仅保留分钟 / 球员 / 牌类型） */
@Serializable
data class CompressedCard(
    val minute: Int,
    val playerId: Int,
    val cardType: String // yellow / red
)

/** 关键评分（仅保留 Top N 球员评分） */
@Serializable
data class CompressedRating(
    val playerId: Int,
    val rating: Double
)
