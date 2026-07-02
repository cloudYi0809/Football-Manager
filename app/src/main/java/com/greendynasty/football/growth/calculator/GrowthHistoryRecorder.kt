package com.greendynasty.football.growth.calculator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.growth.model.AppliedGrowth
import com.greendynasty.football.growth.model.GrowthConfig
import com.greendynasty.football.growth.model.GrowthSnapshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * 成长记录持久化器（T09.3）
 *
 * 负责：
 * 1. 批量写入月度成长快照（事务化，REPLACE 策略保证幂等）
 * 2. 快照保留策略（超过保留期的旧快照按月聚合后删除明细）
 * 3. 属性快照 JSON 序列化（避免 30+ 列宽表）
 * 4. 成长轨迹查询（供 T04 成长曲线 / T19 赛季归档）
 *
 * @param databaseManager 三库入口
 * @param config 成长配置
 */
class GrowthHistoryRecorder(
    private val databaseManager: DatabaseManager,
    private val config: GrowthConfig
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 批量写入月度成长快照（事务化）。
     *
     * @param applied 已应用的成长列表
     * @param saveId 存档 ID
     * @param seasonId 赛季 ID
     * @param executionDate 月结日期
     * @return 写入的快照列表
     */
    suspend fun writeBatch(
        applied: List<AppliedGrowth>,
        saveId: Int,
        seasonId: Int,
        executionDate: LocalDate
    ): List<GrowthSnapshotEntity> = withContext(Dispatchers.IO) {
        val dateStr = executionDate.toString()
        val snapshots = applied.map { ag -> buildSnapshot(ag, saveId, seasonId, dateStr) }

        val snapshotDao = databaseManager.growthSnapshotDao()
        if (snapshots.isNotEmpty()) {
            snapshotDao.insertAll(snapshots)
        }

        // 快照保留策略：删除超期旧快照
        applyRetentionPolicy(saveId, executionDate)

        snapshots
    }

    /**
     * 构造单条成长快照。
     */
    private fun buildSnapshot(
        ag: AppliedGrowth,
        saveId: Int,
        seasonId: Int,
        dateStr: String
    ): GrowthSnapshotEntity {
        val input = ag.input
        val result = ag.result
        val potential = ag.potentialUpdate

        return GrowthSnapshotEntity(
            snapshotId = 0,
            saveId = saveId,
            playerId = input.player.playerId,
            clubId = input.club.clubId,
            seasonId = seasonId,
            snapshotDate = dateStr,
            age = input.age,
            caBefore = result.caBefore,
            caAfter = result.caAfter,
            caDelta = result.caDelta,
            paBefore = potential.paBefore,
            paAfter = potential.paAfter,
            paDelta = potential.paDelta,
            realizationScore = potential.realizationScore,
            rangeTier = input.rangeTier.name,
            growthPhase = input.growthPhase.name,
            factorTrainingQuality = result.factors.trainingQuality,
            factorPlayingTime = result.factors.playingTime,
            factorMentor = result.factors.mentor,
            factorClubFacility = result.factors.clubFacility,
            factorTalent = result.factors.talent,
            factorAge = result.factors.age,
            factorInjury = result.factors.injury,
            factorMorale = result.factors.morale,
            factorRandom = result.factors.random,
            factorNationalPool = result.factors.nationalPool,
            attributesJson = serializeAttributes(input.attributes),
            attributeChangesJson = serializeChanges(result.attributeChanges),
            notes = result.notes.joinToString("; ").ifEmpty { null }
        )
    }

    /**
     * V0.2 §七 快照保留策略：
     * - 当赛季：保留全部月度快照
     * - 超过保留期：删除明细（T19 归档后已写入 history.db）
     */
    private suspend fun applyRetentionPolicy(saveId: Int, currentDate: LocalDate) {
        val retainBeforeDate = currentDate.minusMonths(config.snapshotMonthlyRetainMonths.toLong())
        databaseManager.growthSnapshotDao().deleteBefore(saveId, retainBeforeDate.toString())
    }

    /**
     * 序列化属性快照为 JSON 字符串。
     */
    private fun serializeAttributes(attrs: PlayerAttributesEntity): String {
        return buildJsonObject {
            put("ca", attrs.ca)
            put("pa", attrs.pa)
            put("shooting", attrs.shooting)
            put("finishing", attrs.finishing)
            put("passing", attrs.passing)
            put("dribbling", attrs.dribbling)
            put("technique", attrs.technique)
            put("pace", attrs.pace)
            put("acceleration", attrs.acceleration)
            put("strength", attrs.strength)
            put("stamina", attrs.stamina)
            put("defending", attrs.defending)
            put("tackling", attrs.tackling)
            put("vision", attrs.vision)
            put("decision", attrs.decision)
            put("leadership", attrs.leadership)
            put("professionalism", attrs.professionalism)
        }.toString()
    }

    /**
     * 序列化属性变化为 JSON 字符串。
     */
    private fun serializeChanges(changes: Map<String, Int>): String {
        if (changes.isEmpty()) return "{}"
        val obj = buildJsonObject {
            changes.forEach { (k, v) -> put(k, v) }
        }
        return obj.toString()
    }
}
