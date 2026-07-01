package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球员属性表（history.db 只读）
 * 按赛季存储球员的完整 30+ 属性，包括技术、身体、精神、门将属性。
 * 复合主键 (player_id, season_id) 保证同一球员同一赛季只有一条记录。
 */
@Entity(
    tableName = "player_attributes",
    indices = [
        Index(value = ["player_id", "season_id"], unique = true)
    ]
)
data class PlayerAttributesEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    // 当前能力值与潜力值
    @ColumnInfo(name = "ca")
    val ca: Int = 50,

    @ColumnInfo(name = "pa")
    val pa: Int = 50,

    // 技术属性
    @ColumnInfo(name = "shooting")
    val shooting: Int = 50,

    @ColumnInfo(name = "finishing")
    val finishing: Int = 50,

    @ColumnInfo(name = "long_shots")
    val longShots: Int = 50,

    @ColumnInfo(name = "passing")
    val passing: Int = 50,

    @ColumnInfo(name = "crossing")
    val crossing: Int = 50,

    @ColumnInfo(name = "dribbling")
    val dribbling: Int = 50,

    @ColumnInfo(name = "technique")
    val technique: Int = 50,

    @ColumnInfo(name = "first_touch")
    val firstTouch: Int = 50,

    // 身体属性
    @ColumnInfo(name = "pace")
    val pace: Int = 50,

    @ColumnInfo(name = "acceleration")
    val acceleration: Int = 50,

    @ColumnInfo(name = "strength")
    val strength: Int = 50,

    @ColumnInfo(name = "stamina")
    val stamina: Int = 50,

    @ColumnInfo(name = "balance")
    val balance: Int = 50,

    @ColumnInfo(name = "agility")
    val agility: Int = 50,

    @ColumnInfo(name = "jumping")
    val jumping: Int = 50,

    // 防守属性
    @ColumnInfo(name = "defending")
    val defending: Int = 50,

    @ColumnInfo(name = "tackling")
    val tackling: Int = 50,

    @ColumnInfo(name = "marking")
    val marking: Int = 50,

    @ColumnInfo(name = "positioning")
    val positioning: Int = 50,

    @ColumnInfo(name = "heading")
    val heading: Int = 50,

    // 精神属性
    @ColumnInfo(name = "vision")
    val vision: Int = 50,

    @ColumnInfo(name = "decision")
    val decision: Int = 50,

    @ColumnInfo(name = "composure")
    val composure: Int = 50,

    @ColumnInfo(name = "leadership")
    val leadership: Int = 50,

    @ColumnInfo(name = "work_rate")
    val workRate: Int = 50,

    @ColumnInfo(name = "teamwork")
    val teamwork: Int = 50,

    // 隐藏/特殊属性
    @ColumnInfo(name = "injury_proneness")
    val injuryProneness: Int = 50,

    @ColumnInfo(name = "big_match")
    val bigMatch: Int = 50,

    @ColumnInfo(name = "consistency")
    val consistency: Int = 50,

    @ColumnInfo(name = "professionalism")
    val professionalism: Int = 50,

    @ColumnInfo(name = "ambition")
    val ambition: Int = 50,

    @ColumnInfo(name = "loyalty")
    val loyalty: Int = 50,

    // 门将专属属性
    @ColumnInfo(name = "gk_diving")
    val gkDiving: Int = 0,

    @ColumnInfo(name = "gk_reflexes")
    val gkReflexes: Int = 0,

    @ColumnInfo(name = "gk_handling")
    val gkHandling: Int = 0,

    @ColumnInfo(name = "gk_positioning")
    val gkPositioning: Int = 0,

    @ColumnInfo(name = "gk_one_on_one")
    val gkOneOnOne: Int = 0
)
