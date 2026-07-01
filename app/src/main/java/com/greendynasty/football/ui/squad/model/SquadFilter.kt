package com.greendynasty.football.ui.squad.model

/**
 * 阵容筛选条件（8 项组合筛选）。
 *
 * 8 项：位置 / 年龄 / 能力(CA) / 潜力(PA) / 合同 / 伤病 / 国籍 / 状态。
 * 所有条件默认宽松（不限），通过 [applyTo] 对球员列表进行组合过滤。
 *
 * 用法：
 * ```
 * val filtered = SquadFilter(position = "ST", caMin = 80).applyTo(players)
 * ```
 */
data class SquadFilter(

    /** 位置筛选，null 表示不限（GK/CB/CM/ST...） */
    val position: String? = null,

    /** 年龄下限（含） */
    val ageMin: Int = 16,

    /** 年龄上限（含） */
    val ageMax: Int = 45,

    /** 当前能力值下限（含） */
    val caMin: Int = 0,

    /** 当前能力值上限（含） */
    val caMax: Int = 200,

    /** 潜力值下限（含） */
    val paMin: Int = 0,

    /** 潜力值上限（含） */
    val paMax: Int = 200,

    /** 合同到期年份，null 表示不限；匹配 contractUntil 的年份 */
    val contractUntilYear: Int? = null,

    /** 伤病状态筛选，null 表示不限（healthy / injured） */
    val injuryStatus: String? = null,

    /** 国籍筛选，null 表示不限 */
    val nationality: String? = null,

    /** 球员状态筛选，null 表示不限（listed / captain / loaned） */
    val playerStatus: String? = null
) {

    /** 是否为默认（不限任何条件） */
    fun isDefault(): Boolean =
        position == null &&
            ageMin == 16 && ageMax == 45 &&
            caMin == 0 && caMax == 200 &&
            paMin == 0 && paMax == 200 &&
            contractUntilYear == null &&
            injuryStatus == null &&
            nationality == null &&
            playerStatus == null

    /**
     * 对球员列表应用组合筛选。
     * 所有条件取交集（AND）。
     */
    fun applyTo(players: List<PlayerWithState>): List<PlayerWithState> =
        players.filter { p ->
            (position == null || p.position == position) &&
                p.age in ageMin..ageMax &&
                p.ca in caMin..caMax &&
                p.pa in paMin..paMax &&
                matchesContract(p) &&
                (injuryStatus == null || p.injuryStatus == injuryStatus) &&
                (nationality == null || p.nationality == nationality) &&
                matchesStatus(p)
        }

    /** 合同年份匹配：contractUntil 末 4 位等于指定年份 */
    private fun matchesContract(p: PlayerWithState): Boolean {
        val year = contractUntilYear ?: return true
        val until = p.contractUntil ?: return false
        return until.length >= 4 && until.substring(0, 4) == year.toString()
    }

    /** 球员状态匹配 */
    private fun matchesStatus(p: PlayerWithState): Boolean {
        val status = playerStatus ?: return true
        return when (status) {
            "listed" -> p.isListed
            "captain" -> p.isCaptain
            "loaned" -> p.isLoaned
            else -> true
        }
    }

    companion object {
        /** 默认筛选（不限） */
        val DEFAULT = SquadFilter()
    }
}
