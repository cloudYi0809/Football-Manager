package com.greendynasty.football.dressingroom.chemistry

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.dressingroom.model.ChemistryFactorScores
import com.greendynasty.football.dressingroom.model.ChemistryWeights
import com.greendynasty.football.dressingroom.model.DressingRoomConfig
import com.greendynasty.football.dressingroom.model.PlayerChemistryEntity
import com.greendynasty.football.dressingroom.model.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T23 球员化学反应计算器（V0.2 + T23 任务要求 §二.2 + 实现方案 §四.3）。
 *
 * 计算俱乐部内每对球员的化学反应得分（0-1），基于 4 因子加权：
 * - nationality 国籍契合度（权重 0.30）：同国籍=1.0 / 同语系=0.7 / 其他=0.4
 * - language 语言契合度（权重 0.20）：同语言=1.0 / 否则 0.4
 * - age 年龄契合度（权重 0.20）：年龄差 ≤2=1.0 / ≤5=0.7 / 其他=0.3
 * - position 位置契合度（权重 0.30）：互补位置=1.0 / 同位置=0.7 / 无关=0.4
 *
 * 持久化：每对球员（player_a_id < player_b_id）每存档一条记录。
 *
 * 触发时机：转会窗关闭 / 阵容大变动时全量重算；日常不重算（V1 简化）。
 *
 * @param databaseManager 三库管理入口
 * @param config 更衣室配置
 */
class ChemistryCalculator(
    private val databaseManager: DatabaseManager,
    private val config: DressingRoomConfig = DressingRoomConfig.DEFAULT
) {

    // ==================== 1. 因子计算 ====================

    /**
     * 计算国籍契合度 0-1。
     *
     * V0.2 + T23 实现方案：
     * - 同国籍=1.0
     * - 同语系（西语 / 英语 / 阿语 / 法语 等）=0.7
     * - 其他=0.4
     */
    fun computeNationalityScore(nationalityA: String?, nationalityB: String?): Double {
        if (nationalityA.isNullOrBlank() || nationalityB.isNullOrBlank()) return 0.4
        if (nationalityA.equals(nationalityB, ignoreCase = true)) return 1.0
        // 同语系判定（V1 简化：常见语系映射）
        val langA = languageFamily(nationalityA)
        val langB = languageFamily(nationalityB)
        return if (langA == langB) 0.7 else 0.4
    }

    /**
     * 计算语言契合度 0-1。
     *
     * V1 简化：基于国籍推导主要语言，同语言=1.0 / 否则 0.4。
     */
    fun computeLanguageScore(languageA: String?, languageB: String?): Double {
        if (languageA.isNullOrBlank() || languageB.isNullOrBlank()) return 0.4
        if (languageA.equals(languageB, ignoreCase = true)) return 1.0
        return 0.4
    }

    /**
     * 计算年龄契合度 0-1。
     *
     * V0.2 + T23 实现方案：
     * - 年龄差 ≤2=1.0
     * - ≤5=0.7
     * - 其他=0.3
     */
    fun computeAgeScore(ageA: Int, ageB: Int): Double {
        val diff = kotlin.math.abs(ageA - ageB)
        return when {
            diff <= 2 -> 1.0
            diff <= 5 -> 0.7
            else -> 0.3
        }
    }

    /**
     * 计算位置契合度 0-1。
     *
     * V0.2 + T23 实现方案：
     * - 互补位置（如中后卫 + 后腰）=1.0
     * - 同位置=0.7（竞争关系但有默契）
     * - 无关位置=0.4
     */
    fun computePositionScore(positionA: String?, positionB: String?): Double {
        if (positionA.isNullOrBlank() || positionB.isNullOrBlank()) return 0.4
        if (positionA.equals(positionB, ignoreCase = true)) return 0.7
        // 同区域判定（V1 简化：4 大区域 GK/DEF/MID/FWD）
        val zoneA = positionZone(positionA)
        val zoneB = positionZone(positionB)
        // 相邻区域视为互补
        val isComplementary = when {
            zoneA == "DEF" && zoneB == "MID" -> true
            zoneA == "MID" && zoneB == "DEF" -> true
            zoneA == "MID" && zoneB == "FWD" -> true
            zoneA == "FWD" && zoneB == "MID" -> true
            else -> false
        }
        return if (isComplementary) 1.0 else 0.4
    }

    /**
     * 计算一对球员的 4 因子化学反应评分。
     */
    fun computeFactorScores(a: PlayerProfile, b: PlayerProfile): ChemistryFactorScores {
        return ChemistryFactorScores(
            nationalityScore = computeNationalityScore(a.nationality, b.nationality),
            languageScore = computeLanguageScore(a.language, b.language),
            ageScore = computeAgeScore(a.age, b.age),
            positionScore = computePositionScore(a.primaryPosition, b.primaryPosition)
        )
    }

    /**
     * 按 [ChemistryWeights] 加权求和得到综合化学反应 0-1。
     */
    fun computeChemistry(scores: ChemistryFactorScores): Double {
        return scores.applyWeights(config.chemistryWeights)
    }

    // ==================== 2. 全队计算 + 持久化 ====================

    /**
     * 计算俱乐部内所有球员两两化学反应并持久化（V0.2 + 实现方案 §四.3）。
     *
     * 触发时机：转会窗关闭 / 阵容大变动时调用。
     *
     * 注意：N 名球员需计算 C(N,2) = N×(N-1)/2 对，球员数过多时性能下降。
     * V1 限制：仅当球员数 ≤50 时全量计算，否则仅计算首发 11 人 + 替补 7 人。
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param profiles 俱乐部内所有球员画像
     * @param currentDate 当前游戏日期
     * @return 写入的化学反应记录数
     */
    suspend fun computeAndPersistForClub(
        saveId: Int,
        clubId: Int,
        profiles: List<PlayerProfile>,
        currentDate: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        if (profiles.size < 2) return@withContext 0

        // 性能保护：N>50 时仅取前 50（V1 简化）
        val targetProfiles = if (profiles.size > 50) profiles.take(50) else profiles
        val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dao = databaseManager.playerChemistryDao()

        // 先清除旧记录（阵容变动后重算）
        dao.deleteByClub(saveId, clubId)

        val entities = mutableListOf<PlayerChemistryEntity>()
        for (i in targetProfiles.indices) {
            for (j in (i + 1) until targetProfiles.size) {
                val a = targetProfiles[i]
                val b = targetProfiles[j]
                // 约定 playerAId < playerBId 避免重复
                val (low, high) = if (a.playerId < b.playerId) a to b else b to a
                val scores = computeFactorScores(low, high)
                val chemistry = computeChemistry(scores)
                entities.add(
                    PlayerChemistryEntity(
                        saveId = saveId,
                        clubId = clubId,
                        playerAId = low.playerId,
                        playerBId = high.playerId,
                        nationalityScore = scores.nationalityScore,
                        languageScore = scores.languageScore,
                        ageScore = scores.ageScore,
                        positionScore = scores.positionScore,
                        chemistryScore = chemistry,
                        lastUpdatedDate = dateStr
                    )
                )
            }
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
        }
        entities.size
    }

    /**
     * 计算俱乐部化学反应指数（全队平均，0-1）。
     *
     * 用于 [com.greendynasty.football.dressingroom.atmosphere.AtmosphereEvaluator] 输入。
     */
    suspend fun getClubChemistryIndex(saveId: Int, clubId: Int): Double = withContext(Dispatchers.IO) {
        databaseManager.playerChemistryDao().getClubChemistryAverage(saveId, clubId) ?: 0.5
    }

    // ==================== 3. 辅助方法 ====================

    /**
     * 推导国籍对应的主要语系（V1 简化）。
     *
     * 用于判定"同语系不同国籍"的化学反应（如阿根廷 + 西班牙 = 西语系）。
     */
    private fun languageFamily(nationality: String): String {
        val upper = nationality.uppercase()
        return when {
            // 西语系
            upper in listOf("ARG", "ESP", "MEX", "COL", "URU", "CHI", "PER", "PAR", "ECU", "VEN") -> "LATIN"
            // 葡语系
            upper in listOf("BRA", "POR", "ANG", "CPV") -> "PORTUGUESE"
            // 英语系
            upper in listOf("ENG", "SCO", "WAL", "IRL", "USA", "AUS", "CAN", "NGA", "GHA") -> "ENGLISH"
            // 法语系
            upper in listOf("FRA", "BEL", "SUI", "SEN", "CIV", "CMR", "MAR", "ALG", "TUN") -> "FRENCH"
            // 德语系
            upper in listOf("GER", "AUT", "SUI") -> "GERMAN"
            // 阿语系
            upper in listOf("KSA", "EGY", "ALG", "MAR", "TUN", "QAT", "UAE", "IRQ", "IRN") -> "ARABIC"
            else -> upper // 默认独立语系
        }
    }

    /**
     * 推导位置所属区域（V1 简化：4 大区域）。
     *
     * 用于判定位置契合度（同区域 = 竞争关系 = 0.7，相邻区域 = 互补 = 1.0）。
     */
    private fun positionZone(position: String): String {
        val upper = position.uppercase()
        return when {
            // 门将
            upper.startsWith("GK") || upper.contains("GOALKEEPER") -> "GK"
            // 后卫
            upper.startsWith("D") || upper.contains("DEF") || upper.contains("BACK") ||
                upper in listOf("CB", "LB", "RB", "WB", "LWB", "RWB") -> "DEF"
            // 中场
            upper.startsWith("M") || upper.contains("MID") ||
                upper in listOf("DM", "CM", "AM", "LM", "RM") -> "MID"
            // 前锋
            upper.startsWith("F") || upper.contains("FORWARD") || upper.contains("STRIKER") ||
                upper in listOf("ST", "CF", "LW", "RW", "WF") -> "FWD"
            else -> "MID" // 默认按中场处理
        }
    }
}
