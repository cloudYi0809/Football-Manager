package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 球队成员关系表（history.db 只读）
 * 记录某赛季某球员效力于某俱乐部，包括号码、合同、薪资、是否租借等。
 */
@Entity(
    tableName = "squad_membership",
    indices = [
        Index(value = ["season_id", "club_id"]),
        Index(value = ["player_id", "season_id"])
    ]
)
data class SquadMembershipEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "season_id")
    val seasonId: Int,

    @ColumnInfo(name = "club_id")
    val clubId: Int,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "squad_number")
    val squadNumber: Int?,

    @ColumnInfo(name = "joined_date")
    val joinedDate: String?,

    @ColumnInfo(name = "contract_until")
    val contractUntil: String?,

    @ColumnInfo(name = "wage")
    val wage: Int = 0,

    @ColumnInfo(name = "market_value")
    val marketValue: Int = 0,

    @ColumnInfo(name = "is_loan")
    val isLoan: Int = 0,

    @ColumnInfo(name = "loan_from_club_id")
    val loanFromClubId: Int?,

    @ColumnInfo(name = "squad_role")
    val squadRole: String?
)
