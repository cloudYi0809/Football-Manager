package com.greendynasty.football.ui.squad.model

/**
 * 阵容列表项聚合数据。
 *
 * 聚合 history.db 的 [com.greendynasty.football.data.history.entity.PlayerEntity]
 * 与 save.db 的 [com.greendynasty.football.data.save.entity.SavePlayerStateEntity]，
 * 供阵容列表行卡片展示：姓名 / 年龄 / 国籍 / 位置 / CA / PA / 状态 / 体能 / 士气 / 合同到期 / 身价。
 *
 * 该类为不可变快照，列表刷新时整体替换。
 */
data class PlayerWithState(

    /** 球员 ID（关联 history.player.player_id） */
    val playerId: Int,

    /** 展示姓名（优先 displayName，其次 realName） */
    val name: String,

    /** 年龄（由 birthDate 计算得出） */
    val age: Int,

    /** 国籍 */
    val nationality: String,

    /** 主要位置 GK/CB/LB/RB/DM/CM/AM/LW/RW/ST */
    val position: String,

    /** 当前能力值 CA */
    val ca: Int,

    /** 潜力值 PA */
    val pa: Int,

    /** 体能 0-100 */
    val condition: Int,

    /** 士气 0-100 */
    val morale: Int,

    /** 合同到期日期（ISO yyyy-MM-dd），null 表示未知 */
    val contractUntil: String?,

    /** 当前身价 */
    val marketValue: Int,

    /** 伤病状态：healthy / injured */
    val injuryStatus: String,

    /** 所属梯队 */
    val squadTab: SquadTab,

    /** 球衣号码（可空） */
    val shirtNumber: Int?,

    /** 周薪 */
    val wage: Int = 0,

    /** 头像路径（V1 仅占位） */
    val portraitPath: String? = null,

    /** 惯用脚 */
    val preferredFoot: String? = null,

    /** 身高 cm */
    val height: Int? = null,

    /** 体重 kg */
    val weight: Int? = null,

    /** 是否已挂牌出售（实体暂无此字段，默认 false） */
    val isListed: Boolean = false,

    /** 是否队长（实体暂无此字段，默认 false） */
    val isCaptain: Boolean = false,

    /** 是否外租中（由 loanClubId 非空判定） */
    val isLoaned: Boolean = false
) {
    /** 球员状态摘要文本，用于列表行展示 */
    val statusText: String
        get() = when {
            injuryStatus != "healthy" -> "伤"
            isListed -> "挂牌"
            isCaptain -> "队长"
            isLoaned -> "外租"
            else -> "正常"
        }
}
