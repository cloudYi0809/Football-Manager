package com.greendynasty.football.growth.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 月度训练记录表（save.db，T09 方案 §三.3）
 *
 * T07 每日训练任务每日积累，月结时由聚合器汇总为本条记录。
 * 若月结时发现本月无记录（球员整月未训练），用默认值填充。
 */
@Entity(
    tableName = "monthly_training_record",
    indices = [
        Index(value = ["save_id", "player_id", "month"], unique = true),
        Index(value = ["save_id", "club_id", "month"])
    ]
)
data class MonthlyTrainingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "record_id")
    val recordId: Int = 0,

    @ColumnInfo(name = "save_id")
    val saveId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "month")
    val month: String, // yyyy-MM

    @ColumnInfo(name = "training_focus")
    val trainingFocus: String, // SHOOTING/PASSING/FITNESS/DEFENDING/BALANCED

    @ColumnInfo(name = "intensity_avg")
    val intensityAvg: Int, // 1-10 月均训练强度

    @ColumnInfo(name = "training_days")
    val trainingDays: Int, // 本月实际训练天数

    @ColumnInfo(name = "missed_days_injury")
    val missedDaysInjury: Int, // 因伤缺训天数

    @ColumnInfo(name = "missed_days_match")
    val missedDaysMatch: Int, // 比赛日未训练天数

    @ColumnInfo(name = "training_quality_score")
    val trainingQualityScore: Double, // 0-1 月均训练质量

    @ColumnInfo(name = "mentor_id")
    val mentorId: Int? = null, // 导师球员 ID（若有）

    @ColumnInfo(name = "mentor_effect_score")
    val mentorEffectScore: Double = 0.0, // 导师加成 0-0.1

    @ColumnInfo(name = "position_training_target")
    val positionTrainingTarget: String? = null // 位置改造目标
)
