package com.greendynasty.football.dressingroom.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T23 更衣室模块实体集合（save.db）。
 *
 * 严格依据 `/Users/yi/Desktop/足球经理/开发文档/T23_更衣室_实现方案.md` §三 数据模型，
 * 并按 T23 任务要求 §二.6 聚焦 5 大核心系统（士气 / 化学反应 / 氛围 / 领袖 / 情绪事件）裁剪。
 *
 * 共 5 张表：
 * 1. [PlayerMoraleEntity] - 球员士气（4 因子分项 + 综合，含不满累积）
 * 2. [PlayerChemistryEntity] - 球员两两化学反应（国籍 / 语言 / 年龄 / 位置）
 * 3. [DressingRoomAtmosphereEntity] - 更衣室氛围快照（4 档 + 稳定指数）
 * 4. [DressingRoomLeaderEntity] - 更衣室领袖（队长 / 副队长 / 影响力球员）
 * 5. [PlayerEmotionEventEntity] - 球员情绪事件（不满 / 转会传闻 / 续约要求等）
 *
 * 三库归属：全部 save.db，每存档独立，与 T22 董事会表保持一致风格。
 * saveId 类型与 [com.greendynasty.football.data.save.entity.SaveClubStateEntity] 一致使用 Int。
 */

// ==================== 1. 球员士气 ====================

/**
 * 球员士气表（save.db）。
 *
 * 每球员每存档一条记录，记录综合士气 + 4 因子分项 + 不满累积。
 *
 * 4 因子（V0.2 + T23 任务要求 §二.1，简化为 4 因子而非 12 因子）：
 * - playing_time 上场时间（权重 0.40）
 * - match_result 战绩（权重 0.30）
 * - contract 合同状况（权重 0.20）
 * - personal_event 个人事件（权重 0.10）
 *
 * 5 档士气等级：EXTREME_HIGH(90+) / HIGH(75+) / MID(50+) / LOW(30+) / EXTREME_LOW(<30)。
 *
 * 不满累积：长期未上场 / 战绩差 → unrestAccumulator 累积，≥阈值触发情绪事件。
 */
@Entity(
    tableName = "player_morale",
    indices = [
        Index(value = ["save_id", "player_id"], unique = true),
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "club_id", "morale_level"])
    ]
)
data class PlayerMoraleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 综合士气 0-100。 */
    @ColumnInfo(name = "morale")
    val morale: Int,

    /** 士气等级：EXTREME_HIGH / HIGH / MID / LOW / EXTREME_LOW。 */
    @ColumnInfo(name = "morale_level")
    val moraleLevel: String,

    /** 上场时间因子 0-1（连续首发高 / 替补中 / 长期替补低）。 */
    @ColumnInfo(name = "playing_time_factor")
    val playingTimeFactor: Double,

    /** 战绩因子 0-1（连胜高 / 连败低）。 */
    @ColumnInfo(name = "match_result_factor")
    val matchResultFactor: Double,

    /** 合同因子 0-1（合同年限 + 工资水平）。 */
    @ColumnInfo(name = "contract_factor")
    val contractFactor: Double,

    /** 个人事件因子 0-1（家庭 / 媒体 / 个人荣誉）。 */
    @ColumnInfo(name = "personal_event_factor")
    val personalEventFactor: Double,

    /** 化学反应得分 0-1（冗余，来自 PlayerChemistryEntity 聚合）。 */
    @ColumnInfo(name = "chemistry_score")
    val chemistryScore: Double = 0.5,

    /** 不满累积计数（≥阈值触发情绪事件，每周一由 PlayerEmotionEventService 检查）。 */
    @ColumnInfo(name = "unrest_accumulator")
    val unrestAccumulator: Int = 0,

    /** 是否有待谈话（玩家可发起谈话清除）。 */
    @ColumnInfo(name = "pending_conversation")
    val pendingConversation: Boolean = false,

    /** 最近连续首发场数（连续首发 +3 触发阈值）。 */
    @ColumnInfo(name = "consecutive_starts")
    val consecutiveStarts: Int = 0,

    /** 最近连续替补场数（连续替补 -5 触发阈值）。 */
    @ColumnInfo(name = "consecutive_benched")
    val consecutiveBenched: Int = 0,

    @ColumnInfo(name = "last_updated_date")
    val lastUpdatedDate: String,

    @ColumnInfo(name = "last_updated_season")
    val lastUpdatedSeason: Int
)

// ==================== 2. 球员化学反应 ====================

/**
 * 球员化学反应表（save.db）。
 *
 * 记录俱乐部内每对球员的化学反应得分（基于国籍 / 语言 / 年龄 / 位置组合）。
 * 由 [com.greendynasty.football.dressingroom.chemistry.ChemistryCalculator] 计算并写入。
 *
 * 一对球员（player_a_id < player_b_id）每存档一条记录。
 *
 * @property chemistryScore 综合化学反应 0-1
 */
@Entity(
    tableName = "player_chemistry",
    indices = [
        Index(value = ["save_id", "player_a_id", "player_b_id"], unique = true),
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "player_a_id"])
    ]
)
data class PlayerChemistryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 球员 A ID（约定 A < B 避免重复）。 */
    @ColumnInfo(name = "player_a_id")
    val playerAId: Int,

    /** 球员 B ID。 */
    @ColumnInfo(name = "player_b_id")
    val playerBId: Int,

    /** 国籍契合度 0-1（同国籍=1.0，同语系=0.7，其他=0.4）。 */
    @ColumnInfo(name = "nationality_score")
    val nationalityScore: Double,

    /** 语言契合度 0-1（同语言=1.0，否则 0.4）。 */
    @ColumnInfo(name = "language_score")
    val languageScore: Double,

    /** 年龄契合度 0-1（年龄差 ≤2=1.0，≤5=0.7，其他=0.3）。 */
    @ColumnInfo(name = "age_score")
    val ageScore: Double,

    /** 位置契合度 0-1（互补位置=1.0，同位置=0.7，无关=0.4）。 */
    @ColumnInfo(name = "position_score")
    val positionScore: Double,

    /** 综合化学反应 0-1。 */
    @ColumnInfo(name = "chemistry_score")
    val chemistryScore: Double,

    @ColumnInfo(name = "last_updated_date")
    val lastUpdatedDate: String
)

// ==================== 3. 更衣室氛围 ====================

/**
 * 更衣室氛围快照表（save.db）。
 *
 * 每俱乐部按月度 + 事件触发写入，记录氛围等级 + 稳定指数。
 * 由 [com.greendynasty.football.dressingroom.atmosphere.AtmosphereEvaluator] 计算。
 *
 * 4 档氛围：HARMONIOUS 和谐 / NORMAL 一般 / TENSE 紧张 / SPLIT 分裂。
 */
@Entity(
    tableName = "dressing_room_atmosphere",
    indices = [
        Index(value = ["save_id", "club_id", "snapshot_date"]),
        Index(value = ["save_id", "club_id", "atmosphere_level"])
    ]
)
data class DressingRoomAtmosphereEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "snapshot_date")
    val snapshotDate: String,

    /** 氛围等级：HARMONIOUS / NORMAL / TENSE / SPLIT。 */
    @ColumnInfo(name = "atmosphere_level")
    val atmosphereLevel: String,

    /** 球队平均士气 0-100。 */
    @ColumnInfo(name = "team_morale")
    val teamMorale: Int,

    /** 化学反应指数 0-1（全队平均）。 */
    @ColumnInfo(name = "chemistry_index")
    val chemistryIndex: Double,

    /** 领袖影响力 0-100。 */
    @ColumnInfo(name = "leader_influence")
    val leaderInfluence: Int,

    /** 不满球员数。 */
    @ColumnInfo(name = "unrest_count")
    val unrestCount: Int,

    /** 稳定指数 0-100（综合士气 + 化学反应 + 领袖 + 不满数，提供给 T22 董事会）。 */
    @ColumnInfo(name = "stability_index")
    val stabilityIndex: Int,

    @ColumnInfo(name = "snapshot_season")
    val snapshotSeason: Int
)

// ==================== 4. 更衣室领袖 ====================

/**
 * 更衣室领袖表（save.db）。
 *
 * 记录俱乐部现任领袖（队长 / 副队长 / 影响力球员）。
 * 由 [com.greendynasty.football.dressingroom.leader.DressingRoomLeaderDetector] 识别。
 *
 * @property leaderRole CAPTAIN 队长 / VICE_CAPTAIN 副队长 / INFLUENTIAL 影响力球员
 * @property influence 影响力 0-100（leadership * 0.6 + reputation * 0.2 + age_factor * 0.2）
 */
@Entity(
    tableName = "dressing_room_leader",
    indices = [
        Index(value = ["save_id", "club_id", "status"]),
        Index(value = ["save_id", "club_id", "leader_role"])
    ]
)
data class DressingRoomLeaderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    /** 领袖角色：CAPTAIN / VICE_CAPTAIN / INFLUENTIAL。 */
    @ColumnInfo(name = "leader_role")
    val leaderRole: String,

    /** 任命时领导力属性快照（来自 player_attributes.leadership）。 */
    @ColumnInfo(name = "leadership")
    val leadership: Int,

    /** 影响力 0-100。 */
    @ColumnInfo(name = "influence")
    val influence: Int,

    @ColumnInfo(name = "appointed_date")
    val appointedDate: String,

    @ColumnInfo(name = "appointed_season")
    val appointedSeason: Int,

    /** 任命方式：MANAGER / AUTO。 */
    @ColumnInfo(name = "appointed_by")
    val appointedBy: String = "AUTO",

    /** 状态：ACTIVE / REVOKED。 */
    @ColumnInfo(name = "status")
    val status: String = "ACTIVE",

    @ColumnInfo(name = "revoked_date")
    val revokedDate: String? = null,

    @ColumnInfo(name = "revoked_reason")
    val revokedReason: String? = null
)

// ==================== 5. 球员情绪事件 ====================

/**
 * 球员情绪事件表（save.db）。
 *
 * 由 [com.greendynasty.football.dressingroom.event.PlayerEmotionEventService] 触发写入。
 *
 * 6 类事件（V0.2 + T23 任务要求 §二.5）：
 * - UNHAPPY 不满（士气 <30 触发）
 * - TRANSFER_RUMOR 转会传闻（士气 <20 + 连败触发）
 * - RENEWAL_REQUEST 续约要求（合同 <1 年触发）
 * - CONFLICT 球员冲突（2 名球员均士气 <30 触发）
 * - NEW_SIGNING_STRUGGLE 新援融入困难（加入 30 天内士气 <40）
 * - VETERAN_FAREWELL 老将告别（≥33 岁宣布赛季末退役）
 *
 * @property severity 严重等级：MINOR / MODERATE / MAJOR / CRITICAL
 */
@Entity(
    tableName = "player_emotion_event",
    indices = [
        Index(value = ["save_id", "club_id", "event_date"]),
        Index(value = ["save_id", "club_id", "event_type"]),
        Index(value = ["save_id", "event_season"])
    ]
)
data class PlayerEmotionEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "event_date")
    val eventDate: String,

    @ColumnInfo(name = "event_season")
    val eventSeason: Int,

    /** 事件类型：UNHAPPY / TRANSFER_RUMOR / RENEWAL_REQUEST / CONFLICT / NEW_SIGNING_STRUGGLE / VETERAN_FAREWELL。 */
    @ColumnInfo(name = "event_type")
    val eventType: String,

    /** 严重等级：MINOR / MODERATE / MAJOR / CRITICAL。 */
    @ColumnInfo(name = "severity")
    val severity: String,

    /** 涉及的其他球员 ID 列表（CONFLICT 时有值，逗号分隔）。 */
    @ColumnInfo(name = "involved_player_ids")
    val involvedPlayerIds: String = "",

    @ColumnInfo(name = "description")
    val description: String,

    /** 全队士气影响（如 -3 / +2 / 0）。 */
    @ColumnInfo(name = "morale_impact")
    val moraleImpact: Int = 0,

    @ColumnInfo(name = "resolved")
    val resolved: Boolean = false,

    @ColumnInfo(name = "resolution")
    val resolution: String? = null,

    @ColumnInfo(name = "resolved_date")
    val resolvedDate: String? = null
)
