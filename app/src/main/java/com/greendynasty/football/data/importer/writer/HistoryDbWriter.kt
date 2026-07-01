package com.greendynasty.football.data.importer.writer

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.entity.AgentEntity
import com.greendynasty.football.data.history.entity.ClubCompetitionSeasonEntity
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.CompetitionEntity
import com.greendynasty.football.data.history.entity.HistoricalProspectPoolEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.history.entity.ScoutEntity
import com.greendynasty.football.data.history.entity.SeasonEntity
import com.greendynasty.football.data.history.entity.SquadMembershipEntity
import com.greendynasty.football.data.history.entity.StaffEntity
import com.greendynasty.football.data.history.entity.TransferHistoryEntity
import com.greendynasty.football.data.history.entity.YouthAcademyEntity
import com.greendynasty.football.data.save.entity.EconomyIndexEntity
import com.greendynasty.football.data.save.entity.LeagueEconomyProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 球探区域知识行（scout_region_knowledge 表的导入数据载体）
 *
 * 该表无 Room Entity（仅作为球探能力的扩展数据），导入时直接用 ContentValues 写入。
 *
 * @property scoutId      球探 ID（外键 → scout）
 * @property regionCode   区域代码
 * @property knowledgeLevel 知识等级（0-100，默认 50）
 */
data class ScoutRegionKnowledgeRow(
    val scoutId: Int,
    val regionCode: String,
    val knowledgeLevel: Int = 50
)

/**
 * 球员-经纪人关联行（player_agent 表的导入数据载体）
 *
 * 该表无 Room Entity（多对多关系表），导入时直接用 ContentValues 写入。
 *
 * @property playerId 球员 ID（外键 → player）
 * @property agentId  经纪人 ID（外键 → agent）
 */
data class PlayerAgentRow(
    val playerId: Int,
    val agentId: Int
)

/**
 * history.db 专用写入器（导入时绕过只读约束）
 *
 * history.db 在 [HistoryDatabase.create] 中通过 `onOpen` 设置了 `PRAGMA query_only = ON`，
 * 其 Room DAO 仅暴露 @Query 方法（无 @Insert）。导入时需要：
 * 1. 调用 [openForWrite] 临时关闭 query_only（由 [com.greendynasty.football.data.importer.api.DataImportManager] 在导入前后管理）
 * 2. 直接通过 [SupportSQLiteDatabase.insert] 执行 INSERT（绕过只读 DAO）
 *
 * 所有 insert* 方法内部用 `withTransaction` 包裹，保证单表原子性；
 * 与 [BatchWriter] 的外层事务嵌套时，Room 会自动合并为同一事务。
 *
 * @param database   history.db 实例
 * @param batchSize  内部分批大小（默认 500，用于控内存与单次事务体积）
 */
class HistoryDbWriter(
    val database: HistoryDatabase,
    val batchSize: Int = 500
) {

    private fun db(): SupportSQLiteDatabase = database.openHelper.writableDatabase

    /**
     * 临时关闭只读约束，进入写入模式。
     *
     * 必须在所有 insert* 方法之前调用。导入期间关闭 journal/synchronous 提速，
     * 但保留 foreign_keys = ON 以保证引用完整性。
     */
    fun openForWrite() {
        val db = db()
        db.execSQL("PRAGMA query_only = OFF")
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("PRAGMA journal_mode = OFF")
        db.execSQL("PRAGMA synchronous = OFF")
    }

    /**
     * 导入完成后恢复只读模式与默认 PRAGMA。
     *
     * 必须在所有 insert* 方法完成后调用，避免运行时误写。
     */
    fun closeAndRestoreReadOnly() {
        val db = db()
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA journal_mode = TRUNCATE")
        db.execSQL("PRAGMA query_only = ON")
    }

    // ==================== 球员 ====================

    /** 批量插入球员基础信息（player 表） */
    suspend fun insertPlayers(players: List<PlayerEntity>) = withContext(Dispatchers.IO) {
        if (players.isEmpty()) return@withContext
        database.withTransaction {
            players.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("player_id", entity.playerId)
                        put("source_id", entity.sourceId)
                        put("real_name", entity.realName)
                        put("display_name", entity.displayName)
                        put("birth_date", entity.birthDate)
                        put("nationality", entity.nationality)
                        put("second_nationality", entity.secondNationality)
                        put("height", entity.height)
                        put("weight", entity.weight)
                        put("preferred_foot", entity.preferredFoot)
                        put("primary_position", entity.primaryPosition)
                        put("secondary_positions", entity.secondaryPositions)
                        put("personality", entity.personality)
                        put("retire_age_base", entity.retireAgeBase)
                        put("portrait_path", entity.portraitPath)
                        put("created_at", entity.createdAt)
                        put("updated_at", entity.updatedAt)
                    }
                    db().insert("player", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    /** 批量插入球员属性（player_attributes 表，按赛季） */
    suspend fun insertPlayerAttributes(attrs: List<PlayerAttributesEntity>) = withContext(Dispatchers.IO) {
        if (attrs.isEmpty()) return@withContext
        database.withTransaction {
            attrs.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("player_id", entity.playerId)
                        put("season_id", entity.seasonId)
                        put("ca", entity.ca)
                        put("pa", entity.pa)
                        put("shooting", entity.shooting)
                        put("finishing", entity.finishing)
                        put("long_shots", entity.longShots)
                        put("passing", entity.passing)
                        put("crossing", entity.crossing)
                        put("dribbling", entity.dribbling)
                        put("technique", entity.technique)
                        put("first_touch", entity.firstTouch)
                        put("pace", entity.pace)
                        put("acceleration", entity.acceleration)
                        put("strength", entity.strength)
                        put("stamina", entity.stamina)
                        put("balance", entity.balance)
                        put("agility", entity.agility)
                        put("jumping", entity.jumping)
                        put("defending", entity.defending)
                        put("tackling", entity.tackling)
                        put("marking", entity.marking)
                        put("positioning", entity.positioning)
                        put("heading", entity.heading)
                        put("vision", entity.vision)
                        put("decision", entity.decision)
                        put("composure", entity.composure)
                        put("leadership", entity.leadership)
                        put("work_rate", entity.workRate)
                        put("teamwork", entity.teamwork)
                        put("injury_proneness", entity.injuryProneness)
                        put("big_match", entity.bigMatch)
                        put("consistency", entity.consistency)
                        put("professionalism", entity.professionalism)
                        put("ambition", entity.ambition)
                        put("loyalty", entity.loyalty)
                        put("gk_diving", entity.gkDiving)
                        put("gk_reflexes", entity.gkReflexes)
                        put("gk_handling", entity.gkHandling)
                        put("gk_positioning", entity.gkPositioning)
                        put("gk_one_on_one", entity.gkOneOnOne)
                    }
                    db().insert("player_attributes", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 俱乐部 ====================

    /** 批量插入俱乐部（club 表） */
    suspend fun insertClubs(clubs: List<ClubEntity>) = withContext(Dispatchers.IO) {
        if (clubs.isEmpty()) return@withContext
        database.withTransaction {
            clubs.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("club_id", entity.clubId)
                        put("source_id", entity.sourceId)
                        put("club_name", entity.clubName)
                        put("country", entity.country)
                        put("city", entity.city)
                        put("founded_year", entity.foundedYear)
                        put("reputation", entity.reputation)
                        put("stadium_name", entity.stadiumName)
                        put("stadium_capacity", entity.stadiumCapacity)
                        put("training_level", entity.trainingLevel)
                        put("youth_level", entity.youthLevel)
                        put("finance_level", entity.financeLevel)
                        put("logo_path", entity.logoPath)
                        put("kit_path", entity.kitPath)
                        put("created_at", entity.createdAt)
                        put("updated_at", entity.updatedAt)
                    }
                    db().insert("club", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 阵容 ====================

    /** 批量插入球队成员关系（squad_membership 表） */
    suspend fun insertSquads(squads: List<SquadMembershipEntity>) = withContext(Dispatchers.IO) {
        if (squads.isEmpty()) return@withContext
        database.withTransaction {
            squads.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("season_id", entity.seasonId)
                        put("club_id", entity.clubId)
                        put("player_id", entity.playerId)
                        put("squad_number", entity.squadNumber)
                        put("joined_date", entity.joinedDate)
                        put("contract_until", entity.contractUntil)
                        put("wage", entity.wage)
                        put("market_value", entity.marketValue)
                        put("is_loan", entity.isLoan)
                        put("loan_from_club_id", entity.loanFromClubId)
                        put("squad_role", entity.squadRole)
                    }
                    db().insert("squad_membership", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 转会 ====================

    /** 批量插入历史转会记录（transfer_history 表） */
    suspend fun insertTransfers(transfers: List<TransferHistoryEntity>) = withContext(Dispatchers.IO) {
        if (transfers.isEmpty()) return@withContext
        database.withTransaction {
            transfers.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("player_id", entity.playerId)
                        put("from_club_id", entity.fromClubId)
                        put("to_club_id", entity.toClubId)
                        put("transfer_date", entity.transferDate)
                        put("fee", entity.fee)
                        put("transfer_type", entity.transferType)
                        put("season_id", entity.seasonId)
                        put("is_historical", entity.isHistorical)
                        put("was_interrupted", entity.wasInterrupted)
                        put("notes", entity.notes)
                    }
                    db().insert("transfer_history", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 比赛 ====================

    /** 批量插入历史比赛（match 表） */
    suspend fun insertMatches(matches: List<MatchEntity>) = withContext(Dispatchers.IO) {
        if (matches.isEmpty()) return@withContext
        database.withTransaction {
            matches.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("season_id", entity.seasonId)
                        put("competition_id", entity.competitionId)
                        put("match_date", entity.matchDate)
                        put("home_club_id", entity.homeClubId)
                        put("away_club_id", entity.awayClubId)
                        put("home_score_real", entity.homeScoreReal)
                        put("away_score_real", entity.awayScoreReal)
                        put("home_score_sim", entity.homeScoreSim)
                        put("away_score_sim", entity.awayScoreSim)
                        put("status", entity.status)
                        put("is_historical", entity.isHistorical)
                        put("match_stats_json", entity.matchStatsJson)
                    }
                    db().insert("match", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 历史新星 ====================

    /** 批量插入历史新星池配置（historical_prospect_pool 表） */
    suspend fun insertProspects(prospects: List<HistoricalProspectPoolEntity>) = withContext(Dispatchers.IO) {
        if (prospects.isEmpty()) return@withContext
        database.withTransaction {
            prospects.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("player_id", entity.playerId)
                        put("discoverable_from", entity.discoverableFrom)
                        put("default_youth_club_id", entity.defaultYouthClubId)
                        put("default_first_team_club_id", entity.defaultFirstTeamClubId)
                        put("default_breakthrough_year", entity.defaultBreakthroughYear)
                        put("default_transfer_path", entity.defaultTransferPath)
                        put("initial_region_code", entity.initialRegionCode)
                        put("hidden_until_discovered", entity.hiddenUntilDiscovered)
                        put("legend_level", entity.legendLevel)
                        put("created_scenario", entity.createdScenario)
                        put("tags", entity.tags)
                    }
                    db().insert("historical_prospect_pool", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 球探 ====================

    /** 批量插入球探（scout 表） */
    suspend fun insertScouts(scouts: List<ScoutEntity>) = withContext(Dispatchers.IO) {
        if (scouts.isEmpty()) return@withContext
        database.withTransaction {
            scouts.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("name", entity.name)
                        put("nationality", entity.nationality)
                        put("age", entity.age)
                        put("current_club_id", entity.currentClubId)
                        put("judging_current_ability", entity.judgingCurrentAbility)
                        put("judging_potential", entity.judgingPotential)
                        put("adaptability", entity.adaptability)
                        put("negotiation", entity.negotiation)
                        put("network_level", entity.networkLevel)
                        put("reputation", entity.reputation)
                        put("salary", entity.salary)
                    }
                    db().insert("scout", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 经纪人 ====================

    /** 批量插入经纪人（agent 表） */
    suspend fun insertAgents(agents: List<AgentEntity>) = withContext(Dispatchers.IO) {
        if (agents.isEmpty()) return@withContext
        database.withTransaction {
            agents.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("name", entity.name)
                        put("nationality", entity.nationality)
                        put("greed", entity.greed)
                        put("negotiation", entity.negotiation)
                        put("media_influence", entity.mediaInfluence)
                        put("relationship_level", entity.relationshipLevel)
                        put("style", entity.style)
                    }
                    db().insert("agent", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 教练/员工 ====================

    /** 批量插入教练/员工（staff 表） */
    suspend fun insertStaff(staff: List<StaffEntity>) = withContext(Dispatchers.IO) {
        if (staff.isEmpty()) return@withContext
        database.withTransaction {
            staff.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("name", entity.name)
                        put("role", entity.role)
                        put("nationality", entity.nationality)
                        put("age", entity.age)
                        put("current_club_id", entity.currentClubId)
                        put("ability", entity.ability)
                        put("potential", entity.potential)
                        put("reputation", entity.reputation)
                        put("salary", entity.salary)
                        put("contract_until", entity.contractUntil)
                        put("attributes_json", entity.attributesJson)
                    }
                    db().insert("staff", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 青训学院 ====================

    /** 批量插入青训学院配置（youth_academy 表） */
    suspend fun insertYouthAcademies(academies: List<YouthAcademyEntity>) = withContext(Dispatchers.IO) {
        if (academies.isEmpty()) return@withContext
        database.withTransaction {
            academies.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("club_id", entity.clubId)
                        put("youth_level", entity.youthLevel)
                        put("training_level", entity.trainingLevel)
                        put("recruitment_range", entity.recruitmentRange)
                        put("academy_reputation", entity.academyReputation)
                        put("academy_style", entity.academyStyle)
                        put("monthly_cost", entity.monthlyCost)
                        put("u18_coach_quality", entity.u18CoachQuality)
                        put("u21_coach_quality", entity.u21CoachQuality)
                    }
                    db().insert("youth_academy", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 经济配置（V0.2，归属 history.db） ====================

    /** 批量插入经济指数（economy_index 表，按年份） */
    suspend fun insertEconomyIndices(indices: List<EconomyIndexEntity>) = withContext(Dispatchers.IO) {
        if (indices.isEmpty()) return@withContext
        database.withTransaction {
            indices.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("year", entity.year)
                        put("global_index", entity.globalIndex)
                        put("transfer_fee_index", entity.transferFeeIndex)
                        put("wage_index", entity.wageIndex)
                        put("commercial_index", entity.commercialIndex)
                    }
                    db().insert("economy_index", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    /** 批量插入联赛经济画像（league_economy_profile 表） */
    suspend fun insertLeagueProfiles(profiles: List<LeagueEconomyProfileEntity>) = withContext(Dispatchers.IO) {
        if (profiles.isEmpty()) return@withContext
        database.withTransaction {
            profiles.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("league_id", entity.leagueId)
                        put("base_multiplier", entity.baseMultiplier)
                        put("growth_rate", entity.growthRate)
                        put("volatility", entity.volatility)
                        put("notes", entity.notes)
                    }
                    db().insert("league_economy_profile", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 赛季 / 赛事 / 关联（LeagueImporter 使用） ====================

    /** 批量插入赛季（season 表） */
    suspend fun insertSeasons(seasons: List<SeasonEntity>) = withContext(Dispatchers.IO) {
        if (seasons.isEmpty()) return@withContext
        database.withTransaction {
            seasons.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("season_id", entity.seasonId)
                        put("year_start", entity.yearStart)
                        put("year_end", entity.yearEnd)
                        put("label", entity.label)
                        put("start_date", entity.startDate)
                        put("end_date", entity.endDate)
                        put("is_historical", entity.isHistorical)
                    }
                    db().insert("season", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    /** 批量插入赛事（competition 表） */
    suspend fun insertCompetitions(competitions: List<CompetitionEntity>) = withContext(Dispatchers.IO) {
        if (competitions.isEmpty()) return@withContext
        database.withTransaction {
            competitions.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        put("competition_id", entity.competitionId)
                        put("name", entity.name)
                        put("country", entity.country)
                        put("type", entity.type)
                        put("reputation", entity.reputation)
                        put("level", entity.level)
                        put("rules_json", entity.rulesJson)
                    }
                    db().insert("competition", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    /** 批量插入俱乐部-赛事-赛季关联（club_competition_season 表） */
    suspend fun insertClubCompetitionSeasons(links: List<ClubCompetitionSeasonEntity>) = withContext(Dispatchers.IO) {
        if (links.isEmpty()) return@withContext
        database.withTransaction {
            links.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        // id 自增，不设置
                        put("season_id", entity.seasonId)
                        put("competition_id", entity.competitionId)
                        put("club_id", entity.clubId)
                    }
                    db().insert("club_competition_season", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 球探区域知识 / 球员经纪人（无 Entity，直接 ContentValues） ====================

    /** 批量插入球探区域知识（scout_region_knowledge 表，无 Room Entity） */
    suspend fun insertScoutRegionKnowledge(rows: List<ScoutRegionKnowledgeRow>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        database.withTransaction {
            rows.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        // id 自增，不设置
                        put("scout_id", entity.scoutId)
                        put("region_code", entity.regionCode)
                        put("knowledge_level", entity.knowledgeLevel)
                    }
                    db().insert("scout_region_knowledge", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    /** 批量插入球员-经纪人关联（player_agent 表，无 Room Entity） */
    suspend fun insertPlayerAgents(rows: List<PlayerAgentRow>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        database.withTransaction {
            rows.chunked(batchSize).forEach { batch ->
                batch.forEach { entity ->
                    val cv = ContentValues().apply {
                        // id 自增，不设置
                        put("player_id", entity.playerId)
                        put("agent_id", entity.agentId)
                    }
                    db().insert("player_agent", SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    // ==================== 数据包清单（manifest 元信息） ====================

    /**
     * 写入数据包清单元信息（data_pack_manifest 表）。
     *
     * 导入开始时先写入 manifest，便于断点识别与版本追溯。
     */
    suspend fun insertDataPackManifest(
        packId: String,
        packName: String,
        packType: String,
        schemaVersion: Int,
        dataVersion: String,
        distribution: String,
        containsRealNames: Boolean,
        containsRealLogos: Boolean,
        containsRealFaces: Boolean,
        licenseNote: String?,
        checksum: String?,
        createdAt: String?,
        updatedAt: String?
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val cv = ContentValues().apply {
                put("pack_id", packId)
                put("pack_name", packName)
                put("pack_type", packType)
                put("schema_version", schemaVersion)
                put("data_version", dataVersion)
                put("distribution", distribution)
                put("contains_real_names", if (containsRealNames) 1 else 0)
                put("contains_real_logos", if (containsRealLogos) 1 else 0)
                put("contains_real_faces", if (containsRealFaces) 1 else 0)
                put("license_note", licenseNote)
                put("checksum", checksum)
                put("created_at", createdAt)
                put("updated_at", updatedAt)
            }
            db().insert("data_pack_manifest", SQLiteDatabase.CONFLICT_REPLACE, cv)
        }
    }
}
