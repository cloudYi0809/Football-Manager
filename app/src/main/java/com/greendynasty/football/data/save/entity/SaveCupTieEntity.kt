package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存档杯赛对阵表（save.db）
 *
 * 用于国内杯赛与欧战淘汰赛的单/双回合对阵记录。
 * 一条 [SaveCupTieEntity] 表示淘汰赛对阵树中的一个槽位（slot），可能包含 1 或 2 场比赛。
 *
 * 晋级路径通过 [nextTieId] 显式记录，比赛结束后由 ScheduleService 回填晋级方到下一轮 tie。
 *
 * T06 新增：T00-T05 中未创建，按 T06 实现方案 §二.2 新增。
 */
@Entity(
    tableName = "save_cup_tie",
    indices = [
        Index(value = ["save_id", "competition_id", "stage"]),
        Index(value = ["save_id", "tie_id"]),
        Index(value = ["save_id", "season_id", "competition_id"])
    ]
)
data class SaveCupTieEntity(
    /** 对阵 ID，如 "FA_2002_R16_T1"，作为主键 */
    @PrimaryKey
    @ColumnInfo(name = "tie_id")
    val tieId: String,

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "season_id")
    val seasonId: Int, // 赛季 ID

    @ColumnInfo(name = "competition_id")
    val competitionId: Int, // 赛事 ID

    /** 阶段标识：round_of_32 / round_of_16 / quarter / semi / final */
    @ColumnInfo(name = "stage")
    val stage: String,

    /** 阶段排序：1=首轮，2=次轮，依次类推至决赛 */
    @ColumnInfo(name = "stage_order")
    val stageOrder: Int,

    /** 主场俱乐部 ID（待定时为 null，由上一轮晋级回填） */
    @ColumnInfo(name = "home_club_id")
    val homeClubId: Int?,

    /** 客场俱乐部 ID（待定时为 null） */
    @ColumnInfo(name = "away_club_id")
    val awayClubId: Int?,

    /** 首回合对应的 save_match.match_id（比赛生成后回填） */
    @ColumnInfo(name = "first_leg_match_id")
    val firstLegMatchId: Int? = null,

    /** 次回合对应的 match_id（双回合制；单回合为 null） */
    @ColumnInfo(name = "second_leg_match_id")
    val secondLegMatchId: Int? = null,

    /** 总比分（主场方） */
    @ColumnInfo(name = "aggregate_home_score")
    val aggregateHomeScore: Int? = null,

    /** 总比分（客场方） */
    @ColumnInfo(name = "aggregate_away_score")
    val aggregateAwayScore: Int? = null,

    /** 晋级方 clubId；未决出时为 null */
    @ColumnInfo(name = "winner_club_id")
    val winnerClubId: Int? = null,

    /** 是否双回合：1=是，0=否 */
    @ColumnInfo(name = "is_two_legged")
    val isTwoLegged: Int = 0,

    /** 对阵树槽位序号（用于 UI 绘制 bracket） */
    @ColumnInfo(name = "slot_index")
    val slotIndex: Int = 0,

    /** 晋级后进入的下一轮 tie_id（决赛为 null） */
    @ColumnInfo(name = "next_tie_id")
    val nextTieId: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null
)
