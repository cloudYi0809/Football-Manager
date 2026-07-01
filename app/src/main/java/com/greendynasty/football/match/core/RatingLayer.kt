package com.greendynasty.football.match.core

import com.greendynasty.football.match.api.MatchInput
import com.greendynasty.football.match.api.PlayerState
import com.greendynasty.football.match.api.Position
import com.greendynasty.football.match.api.TeamSheet
import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.model.RatingBreakdown
import com.greendynasty.football.match.model.TeamRating

/**
 * Layer 1 评分层（V0.2 04 §三）
 *
 * 计算双方 attack / defense / control 综合评分，
 * 严格按 V0.2 §三.2/三.3/三.4 公式实现。
 *
 * 球员属性按位置聚合，权重从 [MatchConfig] 读取。
 */
class RatingLayer(private val config: MatchConfig = MatchConfig.DEFAULT) {

    /**
     * 评分入口：同时计算主客队评分。
     */
    fun rate(input: MatchInput): Pair<TeamRating, TeamRating> {
        val home = rateTeam(input.homeTeam)
        val away = rateTeam(input.awayTeam)
        return home to away
    }

    /**
     * 单队评分
     */
    private fun rateTeam(sheet: TeamSheet): TeamRating {
        val moraleFactor = calculateMoraleFactor(sheet)
        val conditionFactor = calculateConditionFactor(sheet)
        val tacticalFit = calculateTacticalFit(sheet)

        val attackComponents = calculateAttackComponents(sheet, moraleFactor, tacticalFit)
        val defenseComponents = calculateDefenseComponents(sheet, conditionFactor, tacticalFit)
        val controlComponents = calculateControlComponents(sheet, tacticalFit)

        val w = config
        val attack = attackComponents.values.zip(
            listOf(
                w.attackWeights.forwardFinish,
                w.attackWeights.chanceCreation,
                w.attackWeights.wingThreat,
                w.attackWeights.midfieldSupply,
                w.attackWeights.setPieceAttack,
                w.attackWeights.morale,
                w.attackWeights.tacticalFit
            )
        ).sumOf { (value, weight) -> value * weight }

        val defense = defenseComponents.values.zip(
            listOf(
                w.defenseWeights.centerBack,
                w.defenseWeights.goalkeeper,
                w.defenseWeights.defensiveMid,
                w.defenseWeights.fullback,
                w.defenseWeights.defensiveShape,
                w.defenseWeights.setPieceDefense,
                w.defenseWeights.tacticalFit,
                w.defenseWeights.condition
            )
        ).sumOf { (value, weight) -> value * weight }

        val control = controlComponents.values.zip(
            listOf(
                w.controlWeights.passing,
                w.controlWeights.technique,
                w.controlWeights.vision,
                w.controlWeights.workRate,
                w.controlWeights.pressing,
                w.controlWeights.teamwork,
                w.controlWeights.tacticalFit
            )
        ).sumOf { (value, weight) -> value * weight }

        return TeamRating(
            attackScore = attack,
            defenseScore = defense,
            controlScore = control,
            moraleFactor = moraleFactor,
            conditionFactor = conditionFactor,
            tacticalFit = tacticalFit,
            breakdown = RatingBreakdown(
                attackComponents = attackComponents,
                defenseComponents = defenseComponents,
                controlComponents = controlComponents
            )
        )
    }

    // ==================== 进攻评分（V0.2 04 §三.2） ====================

    /**
     * 进攻评分各子项（顺序对应 AttackWeights）：
     * forwardFinish / chanceCreation / wingThreat / midfieldSupply / setPieceAttack / morale / tacticalFit
     */
    private fun calculateAttackComponents(
        sheet: TeamSheet,
        moraleFactor: Double,
        tacticalFit: Double
    ): Map<String, Double> {
        val starters = sheet.starting11

        // 前锋射门均值：finishing*0.6 + shotPower*0.2 + composure*0.2
        val forwardFinish = avgByPosition(starters, setOf(Position.ST, Position.CF, Position.LW, Position.RW)) {
            it.attributes.finishing * 0.6 + it.attributes.shotPower * 0.2 + it.attributes.composure * 0.2
        }

        // 创造力：vision*0.5 + passing*0.3 + technique*0.2
        val chanceCreation = avgByPosition(starters, setOf(Position.AM, Position.CM)) {
            it.attributes.vision * 0.5 + it.attributes.passing * 0.3 + it.attributes.technique * 0.2
        }

        // 边路威胁：dribbling*0.5 + pace*0.3 + technique*0.2
        val wingThreat = avgByPosition(starters, setOf(Position.LW, Position.RW, Position.LB, Position.RB)) {
            it.attributes.dribbling * 0.5 + it.attributes.pace * 0.3 + it.attributes.technique * 0.2
        }

        // 中场输送：passing*0.6 + vision*0.4
        val midfieldSupply = avgByPosition(starters, setOf(Position.CM, Position.DM)) {
            it.attributes.passing * 0.6 + it.attributes.vision * 0.4
        }

        // 定位球进攻：technique*0.5 + vision*0.5
        val setPieceAttack = avg(starters) {
            it.attributes.technique * 0.5 + it.attributes.vision * 0.5
        }

        return linkedMapOf(
            "forwardFinish" to forwardFinish,
            "chanceCreation" to chanceCreation,
            "wingThreat" to wingThreat,
            "midfieldSupply" to midfieldSupply,
            "setPieceAttack" to setPieceAttack,
            "morale" to moraleFactor,
            "tacticalFit" to tacticalFit
        )
    }

    // ==================== 防守评分（V0.2 04 §三.3） ====================

    /**
     * 防守评分各子项（顺序对应 DefenseWeights）：
     * centerBack / goalkeeper / defensiveMid / fullback / defensiveShape / setPieceDefense / tacticalFit / condition
     */
    private fun calculateDefenseComponents(
        sheet: TeamSheet,
        conditionFactor: Double,
        tacticalFit: Double
    ): Map<String, Double> {
        val starters = sheet.starting11

        // 中卫：marking*0.4 + tackling*0.3 + interceptions*0.3
        val centerBack = avgByPosition(starters, setOf(Position.CB)) {
            it.attributes.marking * 0.4 + it.attributes.tackling * 0.3 + it.attributes.interceptions * 0.3
        }

        // 门将：gkDiving*0.3 + gkPositioning*0.3 + gkReflexes*0.2 + gkHandling*0.2
        val goalkeeper = avgByPosition(starters, setOf(Position.GK)) {
            it.attributes.gkDiving * 0.3 + it.attributes.gkPositioning * 0.3 +
                it.attributes.gkReflexes * 0.2 + it.attributes.gkHandling * 0.2
        }

        // 后腰：interceptions*0.5 + tackling*0.3 + standingTackle*0.2
        val defensiveMid = avgByPosition(starters, setOf(Position.DM)) {
            it.attributes.interceptions * 0.5 + it.attributes.tackling * 0.3 + it.attributes.standingTackle * 0.2
        }

        // 边后卫：tackling*0.4 + marking*0.3 + pace*0.3
        val fullback = avgByPosition(starters, setOf(Position.LB, Position.RB)) {
            it.attributes.tackling * 0.4 + it.attributes.marking * 0.3 + it.attributes.pace * 0.3
        }

        // 防守阵型：teamwork*0.4 + workRate*0.3 + 防线高度修正*0.3
        val defensiveShape = calculateDefensiveShape(sheet)

        // 定位球防守：marking*0.6 + heading*0.4
        val setPieceDefense = avg(starters) {
            it.attributes.marking * 0.6 + it.attributes.heading * 0.4
        }

        return linkedMapOf(
            "centerBack" to centerBack,
            "goalkeeper" to goalkeeper,
            "defensiveMid" to defensiveMid,
            "fullback" to fullback,
            "defensiveShape" to defensiveShape,
            "setPieceDefense" to setPieceDefense,
            "tacticalFit" to tacticalFit,
            "condition" to conditionFactor
        )
    }

    /**
     * 防守阵型评分
     *
     * 综合 teamwork / workRate 与防线高度、阵型后卫数。
     */
    private fun calculateDefensiveShape(sheet: TeamSheet): Double {
        val starters = sheet.starting11
        val base = avg(starters) {
            it.attributes.teamwork * 0.4 + it.attributes.workRate * 0.3
        }
        // 防线高度修正：低位防守(1-3) +5，高位(8-10) -3
        val lineBonus = when (sheet.tactic.defensiveLine) {
            in 1..3 -> 5.0
            in 8..10 -> -3.0
            else -> 0.0
        }
        // 后卫人数加成
        val defCount = starters.count {
            it.position in setOf(Position.CB, Position.LB, Position.RB)
        }
        val defBonus = (defCount - 4).coerceIn(-1, 1) * 2.0
        return (base + lineBonus + defBonus).coerceIn(0.0, 100.0)
    }

    // ==================== 中场控制评分（V0.2 04 §三.4） ====================

    /**
     * 中场控制各子项（顺序对应 ControlWeights）：
     * passing / technique / vision / workRate / pressing / teamwork / tacticalFit
     */
    private fun calculateControlComponents(
        sheet: TeamSheet,
        tacticalFit: Double
    ): Map<String, Double> {
        val starters = sheet.starting11
        return linkedMapOf(
            "passing" to avg(starters) { it.attributes.passing.toDouble() },
            "technique" to avg(starters) { it.attributes.technique.toDouble() },
            "vision" to avg(starters) { it.attributes.vision.toDouble() },
            "workRate" to avg(starters) { it.attributes.workRate.toDouble() },
            "pressing" to avg(starters) { it.attributes.pressing.toDouble() },
            "teamwork" to avg(starters) { it.attributes.teamwork.toDouble() },
            "tacticalFit" to tacticalFit
        )
    }

    // ==================== 修正系数 ====================

    /**
     * 士气修正系数 V0.2 0.85-1.15
     * morale=50 → 1.0，morale=100 → 1.15，morale=0 → 0.85
     */
    private fun calculateMoraleFactor(sheet: TeamSheet): Double {
        val avgMorale = avg(sheet.starting11) { it.morale.toDouble() }
        return (0.85 + (avgMorale / 100.0) * 0.30).coerceIn(0.85, 1.15)
    }

    /**
     * 体能修正系数 V0.2 0.85-1.10
     * condition=100 → 1.10，condition=0 → 0.85
     */
    private fun calculateConditionFactor(sheet: TeamSheet): Double {
        val avgCond = avg(sheet.starting11) { it.condition.toDouble() }
        return (0.85 + (avgCond / 100.0) * 0.25).coerceIn(0.85, 1.10)
    }

    /**
     * 战术适配度 0.90-1.10
     * 从 config.formationTacticFitMatrix 查找，key 形如 "F433_HIGH_PRESS"
     */
    private fun calculateTacticalFit(sheet: TeamSheet): Double {
        val key = "${sheet.formation}_${sheet.tactic.style}"
        return config.formationTacticFitMatrix.getOrDefault(key, 1.0).coerceIn(0.90, 1.10)
    }

    // ==================== 位置权重表（V0.2 04 §三.1） ====================

    /**
     * 球员位置权重表（V0.2 04 §三.1）
     *
     * 定义每个位置对进攻/中场/防守三块的贡献权重。
     * ST/CF：进攻 0.70，中场 0.10，防守 0.05
     * LW/RW：进攻 0.55，中场 0.20，防守 0.10
     * AM：进攻 0.45，中场 0.35，防守 0.05
     * CM：进攻 0.25，中场 0.45，防守 0.20
     * DM：进攻 0.10，中场 0.35，防守 0.40
     * LB/RB：进攻 0.20，中场 0.20，防守 0.40
     * CB：进攻 0.05，中场 0.10，防守 0.70
     * GK：进攻 0.00，中场 0.00，防守 0.85
     */
    fun getPositionWeight(pos: Position): PositionWeights = when (pos) {
        Position.ST, Position.CF -> PositionWeights(attack = 0.70, midfield = 0.10, defense = 0.05)
        Position.LW, Position.RW -> PositionWeights(attack = 0.55, midfield = 0.20, defense = 0.10)
        Position.AM -> PositionWeights(attack = 0.45, midfield = 0.35, defense = 0.05)
        Position.CM -> PositionWeights(attack = 0.25, midfield = 0.45, defense = 0.20)
        Position.DM -> PositionWeights(attack = 0.10, midfield = 0.35, defense = 0.40)
        Position.LB, Position.RB -> PositionWeights(attack = 0.20, midfield = 0.20, defense = 0.40)
        Position.CB -> PositionWeights(attack = 0.05, midfield = 0.10, defense = 0.70)
        Position.GK -> PositionWeights(attack = 0.0, midfield = 0.0, defense = 0.85)
    }

    /** 位置权重三元组 */
    data class PositionWeights(
        val attack: Double,
        val midfield: Double,
        val defense: Double
    )

    // ==================== 工具方法 ====================

    /**
     * 按位置过滤后取均值，无匹配位置时返回 50.0（默认值）
     */
    private fun avgByPosition(
        players: List<PlayerState>,
        positions: Set<Position>,
        selector: (PlayerState) -> Double
    ): Double {
        val filtered = players.filter { it.position in positions }
        if (filtered.isEmpty()) return 50.0
        return filtered.sumOf(selector) / filtered.size
    }

    /** 全队均值 */
    private fun avg(players: List<PlayerState>, selector: (PlayerState) -> Double): Double {
        if (players.isEmpty()) return 50.0
        return players.sumOf(selector) / players.size
    }
}
