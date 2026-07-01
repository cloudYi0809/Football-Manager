package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球员存档状态表（save.db）
 * 记录球员在存档中的当前状态：CA/PA、体能、士气、伤病、合同、身价等。
 * 每个球员在每个存档中只有一条状态记录。
 */
@Entity(
    tableName = "save_player_state",
    indices = [
        Index(value = ["save_id", "player_id"], unique = true),
        Index(value = ["save_id", "current_club_id"]),
        Index(value = ["save_id", "career_status"])
    ]
)
data class SavePlayerStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0, // 自增主键

    @ColumnInfo(name = "save_id")
    val saveId: Int, // 存档 ID（多存档隔离）

    @ColumnInfo(name = "player_id")
    val playerId: Int, // 球员 ID（关联 history.player）

    @ColumnInfo(name = "current_club_id")
    val currentClubId: Int?, // 当前所属俱乐部 ID

    @ColumnInfo(name = "loan_club_id")
    val loanClubId: Int?, // 租借所属俱乐部 ID（非空表示外借中）

    @ColumnInfo(name = "current_ca")
    val currentCa: Int = 50, // 当前能力值 Current Ability

    @ColumnInfo(name = "current_pa")
    val currentPa: Int = 50, // 潜力上限 Potential Ability

    @ColumnInfo(name = "condition")
    val condition: Int = 100, // 体能 0-100

    @ColumnInfo(name = "morale")
    val morale: Int = 50, // 士气 0-100

    @ColumnInfo(name = "injury_status")
    val injuryStatus: String = "healthy", // 伤病状态：healthy / injured

    @ColumnInfo(name = "injury_until")
    val injuryUntil: String?, // 伤愈预计日期

    @ColumnInfo(name = "contract_until")
    val contractUntil: String?, // 合同到期日期

    @ColumnInfo(name = "wage")
    val wage: Int = 0, // 周薪

    @ColumnInfo(name = "market_value")
    val marketValue: Int = 0, // 当前身价

    @ColumnInfo(name = "career_status")
    val careerStatus: String = "active", // 职业状态：active / retired / injured

    @ColumnInfo(name = "squad_role")
    val squadRole: String? // 队内角色：starter / backup / prospect
)
