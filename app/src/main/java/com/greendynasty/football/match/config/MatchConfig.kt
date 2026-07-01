package com.greendynasty.football.match.config

import com.greendynasty.football.match.model.EventType

/**
 * 比赛引擎配置（V0.2 04 §九 match_config.json 的 Kotlin 映射）
 *
 * 集中持有四层流水线全部可调参数，所有 Layer 必须从此对象读取参数，
 * 严禁在算法内写死常量。默认值严格对齐 V0.2 文档。
 */
data class MatchConfig(
    /** 联赛平均 xG */
    val leagueAvgHomeXg: Double = 1.45,
    val leagueAvgAwayXg: Double = 1.15,

    /** xG 上下限 */
    val minXg: Double = 0.15,
    val maxRegularXg: Double = 4.20,
    val maxExtremeXg: Double = 7.0,
    val extremeMatchThreshold: Double = 30.0,

    /** xG 强弱修正 clamp 区间 */
    val attackDefRatioMin: Double = 0.55,
    val attackDefRatioMax: Double = 1.75,
    val controlRatioMin: Double = 0.80,
    val controlRatioMax: Double = 1.20,
    val controlRatioFactor: Double = 0.25,

    /** 主场优势 */
    val homeAdvantage: Double = 1.10,

    /** 进攻评分权重（V0.2 04 §三.2） */
    val attackWeights: AttackWeights = AttackWeights(),
    /** 防守评分权重（V0.2 04 §三.3） */
    val defenseWeights: DefenseWeights = DefenseWeights(),
    /** 中场控制权重（V0.2 04 §三.4） */
    val controlWeights: ControlWeights = ControlWeights(),

    /** 极端比分抑制（V0.2 04 §六） */
    val extremeDampen5: Double = 0.65,
    val extremeDampen6: Double = 0.40,
    val extremeDampen7Plus: Double = 0.20,
    /** 校准层极端比分硬上限（如 8-0 降级为 5-0） */
    val calibrationMaxGoals: Int = 7,

    /** 事件基础概率（V0.2 04 §七） */
    val eventBaseProbabilities: Map<EventType, Double> = DEFAULT_EVENT_PROBABILITIES,

    /** 阵型×战术适配矩阵，key 形如 "F433_HIGH_PRESS" */
    val formationTacticFitMatrix: Map<String, Double> = DEFAULT_FORMATION_FIT,

    /** 战术克制矩阵，key 形如 "HIGH_PRESS_POSSESSION" */
    val counterMatrix: Map<String, TacticalModifier> = DEFAULT_COUNTER_MATRIX,

    /** 球员评分参数（V0.2 04 §十一） */
    val playerRating: PlayerRatingConfig = PlayerRatingConfig(),

    /** 模拟参数 */
    val matchEventIntervalMinutes: Int = 5,
    val totalRegularTicks: Int = 18,
    val stoppageTimeTicksMin: Int = 1,
    val stoppageTimeTicksMax: Int = 2,

    /** 每个 tick 的基础 xG 贡献（V0.2 04 §五.1，事件层参考） */
    val baseXgPerTick: Double = 0.12,
    /** 单队极端比分阈值（进球数 ≥ 此值视为极端比分，Gate1 校验用） */
    val extremeScoreThreshold: Int = 6,
    /** 泊松采样单队进球硬上限（V0.2 04 §六） */
    val poissonMaxGoals: Int = 10,
    /** 球星模板每 tick 触发概率（V0.2 04 §九） */
    val starTemplateTriggerChance: Double = 0.15,
    /** 战术克制修正区间（V0.2 04 §四，0.85-1.15） */
    val counterModifierMin: Double = 0.85,
    val counterModifierMax: Double = 1.15,

    /** 位置属性权重表（V0.2 04 §三.1，RatingLayer 各位置聚合权重） */
    val positionAttributeWeights: Map<String, PositionAttributeWeight> = DEFAULT_POSITION_WEIGHTS
) {
    companion object {
        /** 默认事件基础概率（V0.2 04 §七 事件概率基础表） */
        val DEFAULT_EVENT_PROBABILITIES: Map<EventType, Double> = mapOf(
            EventType.POSSESSION to 0.35,
            EventType.NORMAL_ATTACK to 0.22,
            EventType.DANGEROUS_ATTACK to 0.10,
            EventType.SHOT to 0.08,
            EventType.CORNER to 0.05,
            EventType.FREE_KICK to 0.04,
            EventType.COUNTER_ATTACK to 0.04,
            EventType.OFFSIDE to 0.03,
            EventType.FOUL to 0.05,
            EventType.NOTHING to 0.04
        )

        /** 默认阵型×战术适配（V0.2 04 §九 formation_tactic_fit_matrix） */
        val DEFAULT_FORMATION_FIT: Map<String, Double> = mapOf(
            "F433_HIGH_PRESS" to 1.08,
            "F433_POSSESSION" to 1.05,
            "F442_DEFENSIVE_COUNTER" to 1.05,
            "F442_WING_CROSS" to 1.06,
            "F352_CENTRAL_PENETRATION" to 1.04
        )

        /** 默认战术克制矩阵（V0.2 04 §四 共 7 组） */
        val DEFAULT_COUNTER_MATRIX: Map<String, TacticalModifier> = mapOf(
            "HIGH_PRESS_POSSESSION" to TacticalModifier(myShotEventsDelta = 0.08, myConditionPenalty = 0.10),
            "COUNTER_ATTACK_HIGH_PRESS" to TacticalModifier(mySingleChanceDelta = 0.10),
            "DEFENSIVE_COUNTER_POSSESSION" to TacticalModifier(opponentControlDelta = 0.08, myCounterEventsDelta = 0.06),
            "WING_CROSS_NARROW_DEFENSE" to TacticalModifier(myCrossEventsDelta = 0.10),
            "CENTRAL_PENETRATION_DOUBLE_DM" to TacticalModifier(myCentralChanceDelta = -0.08),
            "LONG_BALL_SHORT_CB" to TacticalModifier(myCrossEventsDelta = 0.08),
            "STAR_FREE_MARKING" to TacticalModifier(opponentKeyPlayerLimited = 0.10)
        )

        /** 默认位置属性权重表（V0.2 04 §三.1，须在 DEFAULT 之前初始化） */
        val DEFAULT_POSITION_WEIGHTS: Map<String, PositionAttributeWeight> = mapOf(
            "ST" to PositionAttributeWeight(attack = 0.70, midfield = 0.10, defense = 0.05),
            "CF" to PositionAttributeWeight(attack = 0.70, midfield = 0.10, defense = 0.05),
            "LW" to PositionAttributeWeight(attack = 0.55, midfield = 0.20, defense = 0.10),
            "RW" to PositionAttributeWeight(attack = 0.55, midfield = 0.20, defense = 0.10),
            "AM" to PositionAttributeWeight(attack = 0.45, midfield = 0.35, defense = 0.05),
            "CM" to PositionAttributeWeight(attack = 0.25, midfield = 0.45, defense = 0.20),
            "DM" to PositionAttributeWeight(attack = 0.10, midfield = 0.35, defense = 0.40),
            "LB" to PositionAttributeWeight(attack = 0.20, midfield = 0.20, defense = 0.40),
            "RB" to PositionAttributeWeight(attack = 0.20, midfield = 0.20, defense = 0.40),
            "CB" to PositionAttributeWeight(attack = 0.05, midfield = 0.10, defense = 0.70),
            "GK" to PositionAttributeWeight(attack = 0.0, midfield = 0.0, defense = 0.85)
        )

        /** 默认配置单例（依赖上方全部默认表，须最后初始化） */
        val DEFAULT: MatchConfig = MatchConfig()
    }
}

/**
 * 位置属性权重（V0.2 04 §三.1）
 *
 * 定义每个位置对进攻 / 中场 / 防守三块的贡献权重。
 */
data class PositionAttributeWeight(
    /** 进攻权重 */
    val attack: Double,
    /** 中场权重 */
    val midfield: Double,
    /** 防守权重 */
    val defense: Double
)

/** 进攻评分权重（V0.2 04 §三.2） */
data class AttackWeights(
    val forwardFinish: Double = 0.28,
    val chanceCreation: Double = 0.24,
    val wingThreat: Double = 0.14,
    val midfieldSupply: Double = 0.14,
    val setPieceAttack: Double = 0.08,
    val morale: Double = 0.05,
    val tacticalFit: Double = 0.07
)

/** 防守评分权重（V0.2 04 §三.3） */
data class DefenseWeights(
    val centerBack: Double = 0.26,
    val goalkeeper: Double = 0.22,
    val defensiveMid: Double = 0.16,
    val fullback: Double = 0.10,
    val defensiveShape: Double = 0.10,
    val setPieceDefense: Double = 0.06,
    val tacticalFit: Double = 0.05,
    val condition: Double = 0.05
)

/** 中场控制权重（V0.2 04 §三.4） */
data class ControlWeights(
    val passing: Double = 0.24,
    val technique: Double = 0.18,
    val vision: Double = 0.16,
    val workRate: Double = 0.14,
    val pressing: Double = 0.12,
    val teamwork: Double = 0.10,
    val tacticalFit: Double = 0.06
)

/**
 * 战术克制修正（V0.2 04 §四）
 *
 * 表示我方战术对对方战术的各项事件修正。
 */
data class TacticalModifier(
    /** 我方射门事件加成 */
    val myShotEventsDelta: Double = 0.0,
    /** 我方反击事件加成 */
    val myCounterEventsDelta: Double = 0.0,
    /** 我方体能消耗加成 */
    val myConditionPenalty: Double = 0.0,
    /** 我方单刀事件加成 */
    val mySingleChanceDelta: Double = 0.0,
    /** 我方传中事件加成 */
    val myCrossEventsDelta: Double = 0.0,
    /** 对方控球加成 */
    val opponentControlDelta: Double = 0.0,
    /** 我方中路机会加成 */
    val myCentralChanceDelta: Double = 0.0,
    /** 对方核心被限制概率 */
    val opponentKeyPlayerLimited: Double = 0.0
) {
    companion object {
        val NEUTRAL = TacticalModifier()
    }
}

/** 球员评分参数（V0.2 04 §十一） */
data class PlayerRatingConfig(
    val baseRating: Double = 6.5,
    val minRating: Double = 4.0,
    val maxRating: Double = 10.0,
    val assistWeight: Double = 0.6,
    val keyPassWeight: Double = 0.08,
    val tackleWeight: Double = 0.05,
    val interceptionWeight: Double = 0.05,
    val saveWeight: Double = 0.10,
    val errorPenalty: Double = 0.8,
    val redCardPenalty: Double = 1.5,
    val ownGoalPenalty: Double = 1.0,
    /** 位置→进球权重 */
    val goalWeightByPosition: Map<String, Double> = mapOf(
        "ST" to 1.0, "CF" to 1.0, "LW" to 1.0, "RW" to 1.0,
        "AM" to 0.7, "CM" to 0.7,
        "DM" to 0.4,
        "LB" to 0.2, "RB" to 0.2, "CB" to 0.2,
        "GK" to 0.0
    ),
    /** 球队结果加分：胜 */
    val teamWinBonus: Double = 0.5,
    /** 球队结果加分：平 */
    val teamDrawBonus: Double = 0.2,
    /** 球队结果加分：负 */
    val teamLossBonus: Double = -0.3
)
