package com.greendynasty.football.youth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T16 青训球员表（save.db，V0.1 08 §二.3 + T16 方案 §三.3）
 *
 * 青训球员是存档动态生成的，不属于 history 库静态数据。
 * 生成时同时在 [com.greendynasty.football.data.save.entity.SavePlayerStateEntity] 表插入
 * 对应记录（playerId 字段关联），用于比赛 / 统计 / 阵容页统一查询。
 *
 * 状态机见 [YouthPlayerStatus]：
 * - 14-17 岁：YOUTH_CONTRACT / U18
 * - 18-21 岁：U21 / PROFESSIONAL_CONTRACT / LOANED_OUT
 * - 提拔一线队：FIRST_TEAM（保留青训身份标记）
 * - 放弃：LEAVING
 *
 * 异常保护：每月最多 1 名天才（hidden_tags 含 "GENIUS"），PA 分布见
 * [com.greendynasty.football.youth.generator.YouthPlayerGenerator]。
 *
 * @param playerId 关联 save_player_state.player_id（生成后回填）
 * @param initialPa 初始 PA，用于兑现率计算（currentPa 不变，potentialPa 用于追踪当前上限）
 */
@Entity(
    tableName = "youth_player",
    indices = [
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "club_id", "tier"]),
        Index(value = ["save_id", "status"]),
        Index(value = ["save_id", "academy_id"])
    ]
)
data class YouthPlayerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "youth_player_id")
    val youthPlayerId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 关联 youth_academy_state.academy_id。 */
    @ColumnInfo(name = "academy_id")
    val academyId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 关联 save_player_state.player_id（生成后回填，0 表示尚未同步）。 */
    @ColumnInfo(name = "player_id")
    val playerId: Int = 0,

    @ColumnInfo(name = "player_name")
    val playerName: String,

    @ColumnInfo(name = "nationality")
    val nationality: String,

    /** 出生日期（yyyy-MM-dd）。 */
    @ColumnInfo(name = "birth_date")
    val birthDate: String,

    /** 主位置代码：ST / LW / RW / AM / CM / DM / CB / LB / RB / GK。 */
    @ColumnInfo(name = "primary_position")
    val primaryPosition: String,

    /** 副位置列表（逗号分隔，例如 "CM,DM"）。 */
    @ColumnInfo(name = "alternative_positions")
    val alternativePositions: String? = null,

    /** 梯队：U18 / U21，见 [YouthTier]。 */
    @ColumnInfo(name = "tier")
    val tier: String,

    /** 状态：见 [YouthPlayerStatus]。 */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "current_ca")
    val currentCa: Int,

    /** 当前潜力上限（成长异常保护下不会突破 initialPa）。 */
    @ColumnInfo(name = "potential_pa")
    val potentialPa: Int,

    /** 初始 PA（用于兑现率计算与异常保护参考）。 */
    @ColumnInfo(name = "initial_pa")
    val initialPa: Int,

    /** 职业态度 1-100。 */
    @ColumnInfo(name = "professionalism")
    val professionalism: Int,

    /** 野心 1-100。 */
    @ColumnInfo(name = "ambition")
    val ambition: Int,

    /** 伤病倾向 1-100（越低越好）。 */
    @ColumnInfo(name = "injury_proneness")
    val injuryProneness: Int,

    /** 适应力 1-100。 */
    @ColumnInfo(name = "adaptability")
    val adaptability: Int,

    /** 是否为重点培养（成长加成）。 */
    @ColumnInfo(name = "is_key_prospect")
    val isKeyProspect: Int = 0,

    /** 训练方向：SHOOTING / PASSING / FITNESS / DEFENDING / BALANCED。 */
    @ColumnInfo(name = "training_focus")
    val trainingFocus: String = "BALANCED",

    /** 导师（一线队球员 ID），null 表示无导师。 */
    @ColumnInfo(name = "mentor_player_id")
    val mentorPlayerId: Int? = null,

    /** 合同类型：YOUTH / PROFESSIONAL。 */
    @ColumnInfo(name = "contract_type")
    val contractType: String = "YOUTH",

    @ColumnInfo(name = "contract_until")
    val contractUntil: String? = null,

    @ColumnInfo(name = "wage")
    val wage: Int = 0,

    /** 生成日期（yyyy-MM-dd）。 */
    @ColumnInfo(name = "generated_date")
    val generatedDate: String,

    @ColumnInfo(name = "promoted_date")
    val promotedDate: String? = null,

    @ColumnInfo(name = "released_date")
    val releasedDate: String? = null,

    /** 详细属性 JSON（继承 save_player_state.attributes_json 格式）。 */
    @ColumnInfo(name = "attributes_json")
    val attributesJson: String = "{}",

    /** 隐藏标签（逗号分隔：GENIUS / EARLY_BLOOMER / LATE_BLOOMER / HIGH_PROFESSIONALISM / LAZY）。 */
    @ColumnInfo(name = "hidden_tags")
    val hiddenTags: String? = null,

    /** 月度成长记录 JSON（最近 12 个月 CA 变化）。 */
    @ColumnInfo(name = "monthly_growth_log_json")
    val monthlyGrowthLogJson: String = "[]"
) {

    /** 副位置列表（解析 alternative_positions 字段）。 */
    val alternativePositionList: List<String>
        get() = alternativePositions
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /** 隐藏标签列表。 */
    val hiddenTagList: List<String>
        get() = hiddenTags
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /** 是否为天才球员（隐藏标签含 GENIUS）。 */
    val isGenius: Boolean
        get() = hiddenTagList.contains("GENIUS")

    /** 是否为重点培养。 */
    val keyProspect: Boolean
        get() = isKeyProspect == 1
}
