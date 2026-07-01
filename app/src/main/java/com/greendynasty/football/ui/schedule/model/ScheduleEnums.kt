package com.greendynasty.football.ui.schedule.model

/**
 * 比赛状态
 */
enum class MatchStatus(val raw: String) {
    SCHEDULED("scheduled"),
    IN_PROGRESS("in_progress"),
    FINISHED("finished"),
    POSTPONED("postponed");

    companion object {
        fun fromRaw(raw: String?): MatchStatus =
            values().firstOrNull { it.raw == raw } ?: SCHEDULED
    }
}

/**
 * 杯赛阶段标识
 */
enum class CupStage(val raw: String, val order: Int, val displayName: String) {
    ROUND_OF_32("round_of_32", 1, "32强"),
    ROUND_OF_16("round_of_16", 2, "16强"),
    QUARTER("quarter", 3, "1/4决赛"),
    SEMI("semi", 4, "半决赛"),
    FINAL("final", 5, "决赛");

    companion object {
        fun fromRaw(raw: String?): CupStage? = values().firstOrNull { it.raw == raw }
    }
}

/**
 * 比赛列表 Tab
 */
enum class ScheduleTab(val displayName: String) {
    MY_SCHEDULE("我的赛程"),
    LEAGUE("联赛"),
    CUP("杯赛")
}
