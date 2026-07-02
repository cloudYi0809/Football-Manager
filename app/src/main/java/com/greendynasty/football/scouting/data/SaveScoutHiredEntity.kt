package com.greendynasty.football.scouting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * T14 球探雇佣记录表（save.db，V0.2 08 §三.1）。
 *
 * 与 history.scout（只读球探模板）独立：
 * - history.scout：球探的固有属性（姓名/国籍/能力值），由数据包提供，不可修改
 * - save.scout_hired：玩家雇佣记录（状态/工资/合同到期/士气），玩家存档私有
 *
 * 一个球探模板（scoutId）可被多个存档雇佣，但同一存档同一球探只能被一个俱乐部雇佣。
 *
 * 状态机：
 * - IDLE（空闲）→ 派遣任务 → ON_TASK
 * - ON_TASK → 任务完成/取消 → IDLE
 * - IDLE → 解雇 → RELEASED
 */
@Entity(
    tableName = "scout_hired",
    indices = [
        Index(value = ["save_id", "club_id"]),
        Index(value = ["save_id", "scout_id", "club_id"], unique = true),
        Index(value = ["save_id", "status"])
    ]
)
data class SaveScoutHiredEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "hired_id")
    val hiredId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    /** 雇佣该球探的俱乐部 ID。 */
    @ColumnInfo(name = "club_id")
    val clubId: Int,

    /** 关联 history.scout.scout_id（球探只读模板）。 */
    @ColumnInfo(name = "scout_id")
    val scoutId: Int,

    /** 雇佣状态：IDLE / ON_TASK / RESTING / RELEASED。 */
    @ColumnInfo(name = "status")
    val status: String = "IDLE",

    /** 雇佣日期（游戏内日期，yyyy-MM-dd）。 */
    @ColumnInfo(name = "hired_date")
    val hiredDate: String,

    /** 合同到期日期（默认 hired_date + 2 年）。 */
    @ColumnInfo(name = "contract_expire_date")
    val contractExpireDate: String,

    /** 周薪（财务系统扣款用）。 */
    @ColumnInfo(name = "wage")
    val wage: Int = 0,

    /** 球探士气（0-100，影响发现概率与升级速度）。 */
    @ColumnInfo(name = "morale")
    val morale: Int = 70
)
