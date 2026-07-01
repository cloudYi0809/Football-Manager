package com.greendynasty.football.match.api

import com.greendynasty.football.match.template.StarTemplateId

/**
 * 比赛输入数据类集合（V0.2 04 §二 + T02 方案 §二.1）
 *
 * 包含比赛全部输入：双方阵容、战术、球员状态、赛事上下文、天气、随机种子。
 * 所有数据类均为不可变 data class。
 */

/**
 * 比赛输入根对象
 */
data class MatchInput(
    /** 比赛 ID */
    val matchId: String,
    /** 主队阵容 */
    val homeTeam: TeamSheet,
    /** 客队阵容 */
    val awayTeam: TeamSheet,
    /** 赛事上下文（重要性、是否德比、是否淘汰赛） */
    val competition: CompetitionContext,
    /** 天气，V1 默认晴天 */
    val weather: Weather = Weather.CLEAR,
    /** 可复现的随机种子 */
    val randomSeed: Long
)

/**
 * 球队出场名单
 */
data class TeamSheet(
    val clubId: String,
    /** 阵型 433/442/352 等 */
    val formation: Formation,
    /** 战术指令 */
    val tactic: Tactic,
    /** 首发 11 人 */
    val starting11: List<PlayerState>,
    /** 替补席 */
    val substitutes: List<PlayerState>,
    /** 是否主队 */
    val isHome: Boolean
)

/**
 * 战术指令
 */
data class Tactic(
    /** 战术风格 */
    val style: TacticStyle,
    /** 压迫强度 1-10 */
    val pressingIntensity: Int,
    /** 防线高度 1-10（高位=10） */
    val defensiveLine: Int,
    /** 节奏 1-10 */
    val tempo: Int,
    /** 传球风格 */
    val passStyle: PassStyle,
    /** 心态 */
    val mentality: Mentality
)

/**
 * 球员状态
 */
data class PlayerState(
    val playerId: String,
    /** 场上位置 GK/CB/LB/RB/DM/CM/AM/LW/RW/ST/CF */
    val position: Position,
    /** 全部属性 */
    val attributes: PlayerAttributes,
    /** 当前能力 1-100 */
    val ca: Int,
    /** 体能 0-100 */
    val condition: Int,
    /** 士气 0-100 */
    val morale: Int,
    /** 伤病倾向 1-20 */
    val injuryProneness: Int,
    /** 球星模板（可为空） */
    val starTemplate: StarTemplateId?,
    /** 近期出场分钟（伤病用） */
    val minutesPlayedRecent: Int
)

/**
 * 球员属性（V0.2 04 §二.1）
 *
 * 分进攻 / 中场 / 防守 / 门将 / 心理五块。
 * 包含 heading 属性以支撑 V0.2 04 §三.3 set_piece_defense 计算。
 */
data class PlayerAttributes(
    // ===== 进攻 =====
    val finishing: Int,
    val shotPower: Int,
    val longShots: Int,
    val pace: Int,
    val acceleration: Int,
    val dribbling: Int,
    val heading: Int,
    // ===== 中场 =====
    val passing: Int,
    val technique: Int,
    val vision: Int,
    val workRate: Int,
    val pressing: Int,
    val teamwork: Int,
    // ===== 防守 =====
    val tackling: Int,
    val marking: Int,
    val interceptions: Int,
    val standingTackle: Int,
    val slidingTackle: Int,
    // ===== 门将 =====
    val gkDiving: Int,
    val gkHandling: Int,
    val gkKicking: Int,
    val gkPositioning: Int,
    val gkReflexes: Int,
    // ===== 心理 =====
    val aggression: Int,
    val composure: Int,
    val leadership: Int,
    val consistency: Int
)

/**
 * 赛事上下文
 */
data class CompetitionContext(
    /** 赛事 ID */
    val competitionId: String,
    /** 比赛重要性 */
    val importance: Importance,
    /** 是否德比 */
    val isDerby: Boolean,
    /** 是否淘汰赛 */
    val isKnockout: Boolean,
    /** 是否决赛 */
    val isFinal: Boolean,
    /** 是否保级战 */
    val isRelegationBattle: Boolean
)

// ==================== 枚举 ====================

/** 天气（V1 默认 CLEAR） */
enum class Weather {
    CLEAR, RAIN, SNOW, FOG
}

/** 阵型 */
enum class Formation {
    F442, F433, F4231, F4321, F352, F343, F451, F532, F541, F4141
}

/** 战术风格（V0.2 04 §四 共 8 类） */
enum class TacticStyle {
    /** 控球组织 */
    POSSESSION,
    /** 快速反击 */
    COUNTER_ATTACK,
    /** 高位压迫 */
    HIGH_PRESS,
    /** 防守反击 */
    DEFENSIVE_COUNTER,
    /** 边路传中 */
    WING_CROSS,
    /** 中路渗透 */
    CENTRAL_PENETRATION,
    /** 长传冲吊 */
    LONG_BALL,
    /** 巨星自由发挥 */
    STAR_FREE
}

/** 传球风格 */
enum class PassStyle {
    /** 短传 */
    SHORT,
    /** 直传 */
    DIRECT,
    /** 长传 */
    LONG
}

/** 心态 */
enum class Mentality {
    /** 全攻 */
    ALL_ATTACK,
    /** 平衡 */
    BALANCED,
    /** 全守 */
    ALL_DEFENSE
}

/** 场上位置 */
enum class Position {
    GK, CB, LB, RB, DM, CM, AM, LW, RW, ST, CF
}

/** 比赛重要性 */
enum class Importance {
    /** 常规 */
    NORMAL,
    /** 德比 */
    DERBY,
    /** 保级战 */
    RELEGATION_BATTLE,
    /** 决赛 */
    FINAL
}
