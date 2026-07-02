package com.greendynasty.football.transfer.recommend

import com.greendynasty.football.match.api.TacticStyle
import com.greendynasty.football.transfer.config.RecommendParams
import com.greendynasty.football.transfer.model.PlayerRecommendation
import com.greendynasty.football.transfer.model.TransferSearchResult

/**
 * 球员推荐算法（V0.2 §四，按需求匹配度排序）。
 *
 * 推荐度 = w_pos * 位置匹配 + w_age * 年龄 + w_ca * 能力 + w_pa * 潜力
 *        + w_value * 身价合理性 + w_style * 战术匹配
 *        + 薄弱位置加成
 *
 * 评分维度（归一化到 0-100）：
 * - 位置匹配：球员位置在需求位置列表中得 100，否则按位置族相似度给分
 * - 年龄：黄金年龄（23-27）=100，越偏离越低
 * - 能力：CA / 200 * 100
 * - 潜力：PA / 200 * 100
 * - 身价合理性：身价 / 预算 * 100（不超过预算且接近预算 = 高分）
 * - 战术匹配：球员位置匹配战术偏好位置 = 100，否则按位置族给分
 *
 * @param params 推荐算法参数
 */
class PlayerRecommender(
    private val params: RecommendParams = RecommendParams()
) {

    /**
     * 对搜索结果按推荐度排序并返回推荐列表。
     *
     * @param candidates 搜索结果列表
     * @param weakPositions 球队薄弱位置集合（由 Repository 根据 squad 分析得出）
     * @param tacticalStyle 当前战术风格，null 表示不评估战术匹配
     * @param transferBudget 转会预算（用于身价合理性评估），null 表示不限
     * @return 推荐球员列表，按 matchScore 降序
     */
    fun recommend(
        candidates: List<TransferSearchResult>,
        weakPositions: Set<String> = emptySet(),
        tacticalStyle: TacticStyle? = null,
        transferBudget: Int? = null
    ): List<PlayerRecommendation> {
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .map { result -> scorePlayer(result, weakPositions, tacticalStyle, transferBudget) }
            .sortedByDescending { it.matchScore }
            .take(params.maxRecommendCount)
    }

    /** 单球员评分 */
    private fun scorePlayer(
        result: TransferSearchResult,
        weakPositions: Set<String>,
        tacticalStyle: TacticStyle?,
        transferBudget: Int?
    ): PlayerRecommendation {
        val reasons = mutableListOf<String>()

        // 1. 位置匹配（0-100）
        val positionScore = computePositionScore(result, weakPositions)
        if (result.position in weakPositions) reasons.add("补强薄弱位置 ${result.position}")
        if (result.secondaryPositions.intersect(weakPositions).isNotEmpty()) {
            reasons.add("可踢副位置补强")
        }

        // 2. 年龄评分（0-100）：黄金年龄 23-27 = 100
        val ageScore = computeAgeScore(result.age)
        if (result.age in 19..25 && result.potentialPa > result.currentCa + 20) {
            reasons.add("年轻高潜（${result.age}岁，PA ${result.potentialPa}）")
        }

        // 3. 能力评分（0-100）
        val caScore = (result.currentCa / 200.0 * 100).coerceIn(0.0, 100.0)
        if (result.currentCa >= 130) reasons.add("即战力（CA ${result.currentCa}）")

        // 4. 潜力评分（0-100）
        val paScore = (result.potentialPa / 200.0 * 100).coerceIn(0.0, 100.0)

        // 5. 身价合理性（0-100）
        val valueScore = computeValueScore(result.marketValue, transferBudget)
        if (transferBudget != null && result.marketValue <= transferBudget * 0.6) {
            reasons.add("身价合理（${formatValue(result.marketValue)}）")
        }

        // 6. 战术匹配（0-100）
        val styleScore = if (tacticalStyle != null) {
            computeStyleMatchScore(result, tacticalStyle).also {
                if (it >= 80) reasons.add("契合${tacticalStyleLabel(tacticalStyle)}战术")
            }
        } else 50.0

        // 综合匹配度
        val baseScore = params.weightPosition * positionScore +
            params.weightAge * ageScore +
            params.weightCa * caScore +
            params.weightPa * paScore +
            params.weightValue * valueScore +
            params.weightStyle * styleScore

        // 薄弱位置加成（绝对加分）
        val weakBonus = if (result.position in weakPositions) params.weakPositionBonus else 0.0

        val matchScore = (baseScore + weakBonus).coerceIn(0.0, 100.0)

        if (reasons.isEmpty()) {
            reasons.add("综合匹配 ${matchScore.toInt()}/100")
        }

        return PlayerRecommendation(
            result = result,
            matchScore = matchScore,
            weakPositionBonus = weakBonus,
            styleMatchScore = styleScore,
            reasons = reasons
        )
    }

    /** 位置匹配评分 */
    private fun computePositionScore(
        result: TransferSearchResult,
        weakPositions: Set<String>
    ): Double {
        // 主位置匹配薄弱位置
        if (result.position in weakPositions) return 100.0
        // 副位置匹配薄弱位置
        if (result.secondaryPositions.intersect(weakPositions).isNotEmpty()) return 85.0
        // 无薄弱位置需求时，按位置族给分
        if (weakPositions.isEmpty()) return 70.0
        // 同位置族（如 CB 与 LB 同为后卫族）
        val posFamily = positionFamily(result.position)
        val weakFamilies = weakPositions.map { positionFamily(it) }.toSet()
        return if (posFamily in weakFamilies) 60.0 else 40.0
    }

    /** 年龄评分：黄金年龄 23-27 = 100 */
    private fun computeAgeScore(age: Int): Double = when {
        age in 23..27 -> 100.0
        age in 19..22 -> 90.0
        age in 28..30 -> 85.0
        age == 18 -> 80.0
        age in 31..32 -> 65.0
        age in 33..34 -> 45.0
        age in 16..17 -> 60.0
        else -> 25.0
    }

    /** 身价合理性评分 */
    private fun computeValueScore(marketValue: Int, transferBudget: Int?): Double {
        if (transferBudget == null || transferBudget <= 0) return 60.0
        if (marketValue > transferBudget) return 20.0 // 超预算
        // 越接近预算（但不超）得分越高，预留 20% 缓冲
        val ratio = marketValue.toDouble() / transferBudget
        return when {
            ratio <= 0.3 -> 95.0 // 物超所值
            ratio <= 0.6 -> 85.0
            ratio <= 0.8 -> 70.0
            else -> 55.0 // 接近预算上限
        }
    }

    /** 战术匹配评分（基于位置族 + 战术偏好位置） */
    private fun computeStyleMatchScore(
        result: TransferSearchResult,
        style: TacticStyle
    ): Double {
        val preferredPositions = stylePreferredPositions(style)
        val allPositions = listOf(result.position) + result.secondaryPositions
        return when {
            result.position in preferredPositions -> 100.0
            allPositions.any { it in preferredPositions } -> 85.0
            // 同位置族
            positionFamily(result.position) in preferredPositions.map { positionFamily(it) }.toSet() -> 65.0
            else -> 40.0
        }
    }

    /** 战术风格偏好的位置列表 */
    private fun stylePreferredPositions(style: TacticStyle): Set<String> = when (style) {
        TacticStyle.POSSESSION -> setOf("CM", "AM", "DM", "LW", "RW")
        TacticStyle.COUNTER_ATTACK -> setOf("LW", "RW", "ST", "CM")
        TacticStyle.HIGH_PRESS -> setOf("CM", "AM", "ST", "LW", "RW")
        TacticStyle.DEFENSIVE_COUNTER -> setOf("CB", "DM", "CM", "ST")
        TacticStyle.WING_CROSS -> setOf("LB", "RB", "LW", "RW", "ST")
        TacticStyle.CENTRAL_PENETRATION -> setOf("CM", "AM", "ST", "CF")
        TacticStyle.LONG_BALL -> setOf("CB", "ST", "CF", "DM")
        TacticStyle.STAR_FREE -> setOf("AM", "ST", "LW", "RW", "CF")
    }

    /** 位置族（用于相似度评估） */
    private fun positionFamily(position: String): String = when (position) {
        "GK" -> "GK"
        "CB", "LB", "RB" -> "DEF"
        "DM", "CM", "AM" -> "MID"
        "LW", "RW", "ST", "CF" -> "ATT"
        else -> "OTHER"
    }

    /** 战术风格中文标签 */
    private fun tacticalStyleLabel(style: TacticStyle): String = when (style) {
        TacticStyle.POSSESSION -> "控球组织"
        TacticStyle.COUNTER_ATTACK -> "快速反击"
        TacticStyle.HIGH_PRESS -> "高位压迫"
        TacticStyle.DEFENSIVE_COUNTER -> "防守反击"
        TacticStyle.WING_CROSS -> "边路传中"
        TacticStyle.CENTRAL_PENETRATION -> "中路渗透"
        TacticStyle.LONG_BALL -> "长传冲吊"
        TacticStyle.STAR_FREE -> "巨星自由发挥"
    }

    /** 身价格式化 */
    private fun formatValue(value: Int): String = when {
        value >= 100_000_000 -> "%.2f亿".format(value / 100_000_000.0)
        value >= 10_000 -> "%.0f万".format(value / 10_000.0)
        else -> "$value"
    }
}
