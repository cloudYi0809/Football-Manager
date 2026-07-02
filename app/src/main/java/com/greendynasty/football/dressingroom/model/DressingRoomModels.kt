package com.greendynasty.football.dressingroom.model

/**
 * T23 更衣室模块领域模型 + 枚举集合。
 *
 * 与 Entity 区别：Entity 是 Room 持久化层，这里是业务计算的不可变 data class / enum。
 * 严格依据 V0.2 算法文档 + T23 任务要求，所有参数配置化（[DressingRoomConfig]）。
 */

// ==================== 1. 士气枚举 ====================

/**
 * 5 档士气等级（V0.2 + T23 任务要求 §二.1）。
 *
 * - EXTREME_HIGH 极高：超常发挥 +10%
 * - HIGH 高：正常发挥
 * - MID 中：略低发挥 -5%
 * - LOW 低：发挥下降 -15%
 * - EXTREME_LOW 极低：严重影响 -30%，可能要求离队
 */
enum class MoraleLevel(val range: IntRange, val performanceModifier: Double, val label: String) {
    EXTREME_HIGH(90..100, 1.10, "极高"),
    HIGH(75..89, 1.00, "高"),
    MID(50..74, 0.95, "中"),
    LOW(30..49, 0.85, "低"),
    EXTREME_LOW(0..29, 0.70, "极低");

    companion object {
        /** 根据综合士气分值判定等级。 */
        fun fromScore(score: Int): MoraleLevel = values().first { score in it.range }
    }
}

/**
 * 4 因子士气权重（V0.2 + T23 任务要求 §二.1，简化为 4 因子）。
 *
 * 总和 1.0：playing_time 0.40 + match_result 0.30 + contract 0.20 + personal_event 0.10。
 */
data class MoraleFactorWeights(
    val playingTime: Double = 0.40,
    val matchResult: Double = 0.30,
    val contract: Double = 0.20,
    val personalEvent: Double = 0.10
)

/**
 * 球员士气 4 因子评分（每个 0-1）。
 */
data class MoraleFactorScores(
    val playingTime: Double,
    val matchResult: Double,
    val contract: Double,
    val personalEvent: Double
) {
    /** 按 [MoraleFactorWeights] 加权求和得到综合士气 0-100。 */
    fun applyWeights(weights: MoraleFactorWeights): Int {
        val morale = (
            playingTime * weights.playingTime +
                matchResult * weights.matchResult +
                contract * weights.contract +
                personalEvent * weights.personalEvent
            ) * 100.0
        return morale.toInt().coerceIn(0, 100)
    }
}

// ==================== 2. 化学反应模型 ====================

/**
 * 球员化学反应权重（V0.2 + T23 任务要求 §二.2）。
 *
 * 总和 1.0：nationality 0.30 + language 0.20 + age 0.20 + position 0.30。
 */
data class ChemistryWeights(
    val nationality: Double = 0.30,
    val language: Double = 0.20,
    val age: Double = 0.20,
    val position: Double = 0.30
)

/**
 * 一对球员的化学反应因子评分（每个 0-1）。
 */
data class ChemistryFactorScores(
    val nationalityScore: Double,
    val languageScore: Double,
    val ageScore: Double,
    val positionScore: Double
) {
    /** 按 [ChemistryWeights] 加权求和得到综合化学反应 0-1。 */
    fun applyWeights(weights: ChemistryWeights): Double {
        return (
            nationalityScore * weights.nationality +
                languageScore * weights.language +
                ageScore * weights.age +
                positionScore * weights.position
            ).coerceIn(0.0, 1.0)
    }
}

/**
 * 球员画像（用于化学反应计算的输入）。
 *
 * V1 简化：从 history.player + player_attributes 读取必要字段。
 */
data class PlayerProfile(
    val playerId: Int,
    val name: String,
    val nationality: String,
    val language: String,
    val age: Int,
    val primaryPosition: String,
    val secondaryPositions: List<String>,
    val leadership: Int = 50,
    val professionalism: Int = 50,
    val temperament: Int = 50,
    val squadRole: String = "backup"
)

// ==================== 3. 更衣室氛围枚举 ====================

/**
 * 4 档更衣室氛围等级（V0.2 + T23 任务要求 §二.3）。
 *
 * - HARMONIOUS 和谐：士气 ≥75 + 化学反应 ≥0.7 + 无不满
 * - NORMAL 一般：士气 ≥50 + 不满 ≤2
 * - TENSE 紧张：士气 ≥30 或 不满 ≥3
 * - SPLIT 分裂：士气 <30 或 不满 ≥5
 */
enum class AtmosphereLevel(val label: String, val performanceModifier: Double) {
    HARMONIOUS("和谐", 1.05),
    NORMAL("一般", 1.00),
    TENSE("紧张", 0.92),
    SPLIT("分裂", 0.80);

    companion object {
        /** 根据综合指标判定氛围等级。 */
        fun fromMetrics(
            teamMorale: Int,
            chemistryIndex: Double,
            unrestCount: Int
        ): AtmosphereLevel = when {
            teamMorale >= 75 && chemistryIndex >= 0.7 && unrestCount == 0 -> HARMONIOUS
            teamMorale >= 50 && unrestCount <= 2 -> NORMAL
            teamMorale >= 30 || unrestCount >= 3 -> TENSE
            else -> SPLIT
        }
    }
}

/**
 * 更衣室氛围评估结果（[com.greendynasty.football.dressingroom.atmosphere.AtmosphereEvaluator] 输出）。
 */
data class AtmosphereEvaluation(
    val level: AtmosphereLevel,
    val teamMorale: Int,
    val chemistryIndex: Double,
    val leaderInfluence: Int,
    val unrestCount: Int,
    val stabilityIndex: Int
)

// ==================== 4. 更衣室领袖枚举 ====================

/**
 * 更衣室领袖角色（V0.2 + T23 任务要求 §二.4）。
 */
enum class LeaderRole(val label: String) {
    CAPTAIN("队长"),
    VICE_CAPTAIN("副队长"),
    INFLUENTIAL("影响力球员")
}

/**
 * 领袖识别结果（[com.greendynasty.football.dressingroom.leader.DressingRoomLeaderDetector] 输出）。
 */
data class LeaderCandidate(
    val playerId: Int,
    val leadership: Int,
    val influence: Int,
    val recommendedRole: LeaderRole
)

// ==================== 5. 球员情绪事件枚举 ====================

/**
 * 6 类球员情绪事件（V0.2 + T23 任务要求 §二.5）。
 */
enum class PlayerEmotionEventType(val label: String) {
    UNHAPPY("不满"),
    TRANSFER_RUMOR("转会传闻"),
    RENEWAL_REQUEST("续约要求"),
    CONFLICT("球员冲突"),
    NEW_SIGNING_STRUGGLE("新援融入困难"),
    VETERAN_FAREWELL("老将告别")
}

/** 事件严重等级。 */
enum class EventSeverity(val label: String) {
    MINOR("轻微"),
    MODERATE("中等"),
    MAJOR("严重"),
    CRITICAL("危急")
}

/**
 * 情绪事件触发结果。
 */
data class EmotionEventTriggerResult(
    val eventType: PlayerEmotionEventType,
    val severity: EventSeverity,
    val playerId: Int,
    val description: String,
    val moraleImpact: Int,
    val triggered: Boolean
)

// ==================== 6. 更衣室快照 ====================

/**
 * 更衣室快照（UI 一次性消费，由 [com.greendynasty.football.dressingroom.repository.DressingRoomRepository] 聚合）。
 */
data class DressingRoomSnapshot(
    val saveId: Int,
    val clubId: Int,
    val captain: DressingRoomLeaderEntity?,
    val leaders: List<DressingRoomLeaderEntity>,
    val playerMorales: List<PlayerMoraleEntity>,
    val unhappyPlayers: List<PlayerMoraleEntity>,
    val teamMorale: Int,
    val chemistryIndex: Double,
    val atmosphere: AtmosphereEvaluation?,
    val recentEvents: List<PlayerEmotionEventEntity>
)
