package com.greendynasty.football.data.history.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史新星池表（history.db 只读，V0.2）
 * 存储未来可被发现的历史新星，如年轻时的梅西、C罗等。
 * 玩家通过球探可在特定年份发现他们。
 */
@Entity(
    tableName = "historical_prospect_pool",
    indices = [Index(value = ["discoverable_from"])]
)
data class HistoricalProspectPoolEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "prospect_id")
    val prospectId: Int = 0,

    @ColumnInfo(name = "player_id")
    val playerId: Int,

    @ColumnInfo(name = "discoverable_from")
    val discoverableFrom: String,

    @ColumnInfo(name = "default_youth_club_id")
    val defaultYouthClubId: Int?,

    @ColumnInfo(name = "default_first_team_club_id")
    val defaultFirstTeamClubId: Int?,

    @ColumnInfo(name = "default_breakthrough_year")
    val defaultBreakthroughYear: Int,

    @ColumnInfo(name = "default_transfer_path")
    val defaultTransferPath: String?,

    @ColumnInfo(name = "initial_region_code")
    val initialRegionCode: String,

    @ColumnInfo(name = "hidden_until_discovered")
    val hiddenUntilDiscovered: Int = 1,

    @ColumnInfo(name = "legend_level")
    val legendLevel: Int = 0,

    @ColumnInfo(name = "created_scenario")
    val createdScenario: String?,

    @ColumnInfo(name = "tags")
    val tags: String?
)
