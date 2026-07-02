package com.greendynasty.football.scouting.core

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.data.SaveScoutHiredEntity
import com.greendynasty.football.scouting.data.SaveScoutReportEntity
import com.greendynasty.football.scouting.data.SaveScoutTaskEntity
import com.greendynasty.football.scouting.model.Discovery
import com.greendynasty.football.scouting.model.ScoutReportLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T14 球探报告生成器（V0.2 08 §四 5 级报告信息解锁）。
 *
 * 5 级报告升级阈值（按累计观察天数 + 球探能力）：
 * - L1 初次发现（默认）：姓名/年龄/地区/位置/初步特点
 * - L2 粗略报告（≥14 天）：CA/PA 区间 ±12 + 优势 + 风险
 * - L3 标准报告（≥35 天）：较窄 CA/PA ±7 + 性格 + 签约难度
 * - L4 深度报告（≥60 天）：成长速度 + 隐藏标签 + 适配战术
 * - L5 完全掌握（≥90 天 + 潜力判断 ≥15）：真实 PA + 伤病倾向 + 职业态度
 *
 * CA/PA 区间计算（V0.2 08 §四）：
 * ```
 * accuracy = scoutAbility / 20          # 0-1
 * actualSpread = max(2, spread × (2 - accuracy))
 * range = [max(1, real - actualSpread), min(200, real + actualSpread)]
 * ```
 *
 * @param databaseManager 三库管理入口
 * @param config 球探配置
 */
class ScoutReportGenerator(
    private val databaseManager: DatabaseManager,
    private val config: ScoutConfig = ScoutConfig.DEFAULT
) {

    /**
     * 生成初次发现报告（等级 1，V0.2 08 §四.1）。
     *
     * 字段：姓名 / 年龄 / 地区 / 位置 / 初步特点（2-3 条）。
     *
     * @param task 触发发现的任务
     * @param discovery 发现结果
     * @param hired 雇佣球探
     * @param currentDate 当前日期
     */
    suspend fun generateInitialReport(
        task: SaveScoutTaskEntity,
        discovery: Discovery,
        hired: SaveScoutHiredEntity,
        currentDate: LocalDate
    ): SaveScoutReportEntity = withContext(Dispatchers.IO) {
        val traits = deriveInitialTraits(discovery)

        SaveScoutReportEntity(
            saveId = task.saveId,
            playerId = discovery.playerId,
            hiredId = task.hiredId,
            scoutId = task.scoutId,
            taskId = task.taskId,
            clubId = task.clubId,
            reportLevel = ScoutReportLevel.INITIAL_DISCOVERY.level,
            regionCode = task.regionCode,
            playerName = discovery.playerName,
            playerAge = discovery.playerAge,
            playerPosition = discovery.playerPosition,
            playerRegion = discovery.playerRegion,
            initialTraits = traits,
            isHistoricalProspect = if (discovery.isHistoricalProspect) 1 else 0,
            scoutRecommendation = 0,
            createdDate = currentDate.toString(),
            lastUpdatedDate = currentDate.toString(),
            observationDays = 1
        )
    }

    /**
     * 报告升级判定（V0.2 08 §四）。
     *
     * 由 [com.greendynasty.football.scouting.ScoutingService.advanceDaily] 在每日推进时调用。
     * 遍历任务关联的所有报告，按 observationDays 判定是否可升级，更新对应字段。
     *
     * @return 升级了的报告列表（oldLevel → newLevel）
     */
    suspend fun tryUpgradeReports(
        task: SaveScoutTaskEntity,
        currentDate: LocalDate
    ): List<Pair<SaveScoutReportEntity, Int>> = withContext(Dispatchers.IO) {
        val upgraded = mutableListOf<Pair<SaveScoutReportEntity, Int>>()
        val reportDao = databaseManager.saveScoutReportDao()

        val reports = reportDao.getByTask(task.saveId, task.taskId)
        if (reports.isEmpty()) return@withContext upgraded

        // 任务类型升级加成（如 STAR_TRACKING +20%）
        val upgradeBonus = config.taskTypeParams[task.taskType]?.reportUpgradeBonus ?: 0.0

        for (report in reports) {
            val oldLevel = report.reportLevel
            val newObservationDays = report.observationDays + 1

            // 应用任务类型升级加成（实际观察天数 × (1 + bonus)）
            val effectiveDays = (newObservationDays * (1.0 + upgradeBonus)).toInt()
            val targetLevel = calculateTargetLevel(effectiveDays, task)

            if (targetLevel > oldLevel) {
                val upgradedReport = upgradeReport(report, targetLevel, task, newObservationDays, currentDate)
                reportDao.update(upgradedReport)
                upgraded.add(upgradedReport to oldLevel)
            } else {
                // 仅更新观察天数
                reportDao.incrementObservationDays(
                    task.saveId, report.reportId, newObservationDays, currentDate.toString()
                )
            }
        }

        upgraded
    }

    /**
     * 计算目标报告等级（V0.2 08 §四）。
     *
     * 升级阈值：
     * - L1 → L2：观察 ≥ [ScoutConfig.upgradeThresholdL2] 天
     * - L2 → L3：观察 ≥ [ScoutConfig.upgradeThresholdL3] 天
     * - L3 → L4：观察 ≥ [ScoutConfig.upgradeThresholdL4] 天
     * - L4 → L5：观察 ≥ [ScoutConfig.upgradeThresholdL5] 天 + 球探潜力判断 ≥ [ScoutConfig.upgradeL5PotentialJudgmentMin]
     */
    private suspend fun calculateTargetLevel(
        effectiveDays: Int,
        task: SaveScoutTaskEntity
    ): Int {
        var level = 1
        if (effectiveDays >= config.upgradeThresholdL2) level = 2
        if (effectiveDays >= config.upgradeThresholdL3) level = 3
        if (effectiveDays >= config.upgradeThresholdL4) level = 4

        // 等级 5 需要球探潜力判断达标
        if (effectiveDays >= config.upgradeThresholdL5) {
            val scout = databaseManager.historyScoutDao().getScout(task.scoutId)
            if (scout != null && scout.judgingPotential >= config.upgradeL5PotentialJudgmentMin) {
                level = 5
            }
        }
        return level
    }

    /**
     * 升级报告（V0.2 08 §四）。
     *
     * 按目标等级解锁对应字段：
     * - L2：CA/PA 区间（spread ±12/±15）+ 优势 + 风险
     * - L3：较窄 CA/PA（spread ±7/±9）+ 性格 + 签约难度
     * - L4：成长速度 + 隐藏标签 + 适配战术
     * - L5：真实 PA + 伤病倾向 + 职业态度
     */
    private suspend fun upgradeReport(
        report: SaveScoutReportEntity,
        targetLevel: Int,
        task: SaveScoutTaskEntity,
        newObservationDays: Int,
        currentDate: LocalDate
    ): SaveScoutReportEntity {
        val scout = databaseManager.historyScoutDao().getScout(task.scoutId)
        val player = databaseManager.historyPlayerDao().getPlayer(report.playerId)
        val playerState = databaseManager.savePlayerStateDao().getByPlayer(task.saveId, report.playerId)

        val builder = report.copy(
            reportLevel = targetLevel,
            lastUpdatedDate = currentDate.toString(),
            observationDays = newObservationDays
        )

        if (scout == null || player == null || playerState == null) return builder

        return when (targetLevel) {
            2 -> {
                val (caLow, caHigh) = computeRange(playerState.currentCa, scout.judgingCurrentAbility, config.caSpreadL2)
                val (paLow, paHigh) = computeRange(playerState.currentPa, scout.judgingPotential, config.paSpreadL2)
                builder.copy(
                    caRangeLow = caLow, caRangeHigh = caHigh,
                    paRangeLow = paLow, paRangeHigh = paHigh,
                    strengths = deriveStrengths(player, playerState),
                    risks = deriveRisks(player, playerState, scout)
                )
            }
            3 -> {
                val (caLow, caHigh) = computeRange(playerState.currentCa, scout.judgingCurrentAbility, config.caSpreadL3)
                val (paLow, paHigh) = computeRange(playerState.currentPa, scout.judgingPotential, config.paSpreadL3)
                builder.copy(
                    caNarrowLow = caLow, caNarrowHigh = caHigh,
                    paNarrowLow = paLow, paNarrowHigh = paHigh,
                    personality = player.personality,
                    signDifficulty = computeSignDifficulty(player, playerState, task)
                )
            }
            4 -> {
                builder.copy(
                    growthSpeed = deriveGrowthSpeed(playerState),
                    hiddenTags = deriveHiddenTags(player, playerState),
                    tacticalFit = deriveTacticalFit(player)
                )
            }
            5 -> {
                builder.copy(
                    realPa = playerState.currentPa,
                    injuryProneness = (50..80).random(), // V1 简化：随机 50-80
                    professionalism = (60..90).random()  // V1 简化：随机 60-90
                )
            }
            else -> builder
        }
    }

    /**
     * CA/PA 区间计算（V0.2 08 §四）。
     *
     * 球探能力越低，spread 越大：
     * ```
     * accuracy = scoutAbility / 20                      # 0-1
     * actualSpread = max(2, spread × (2 - accuracy))
     * return (max(1, realValue - actualSpread), min(200, realValue + actualSpread))
     * ```
     */
    private fun computeRange(realValue: Int, scoutAbility: Int, spread: Int): Pair<Int, Int> {
        val accuracy = scoutAbility.coerceIn(0, 20) / 20.0
        val actualSpread = (spread * (2.0 - accuracy)).toInt().coerceAtLeast(2)
        val low = (realValue - actualSpread).coerceAtLeast(1)
        val high = (realValue + actualSpread).coerceAtMost(200)
        return Pair(low, high)
    }

    /** 签约难度计算（V0.2 08 §四，简化版：声望 + 合同剩余 + 俱乐部意愿）。 */
    private fun computeSignDifficulty(
        player: PlayerEntity,
        state: SavePlayerStateEntity,
        task: SaveScoutTaskEntity
    ): Int {
        val base = (state.marketValue / 1_000_000).coerceIn(0, 100)
        return base.coerceIn(1, 100)
    }

    /** 初步特点推导（V0.2 08 §四.1，简化版）。 */
    private fun deriveInitialTraits(discovery: Discovery): String {
        val traits = mutableListOf<String>()
        // 位置相关特点
        when (discovery.playerPosition.uppercase()) {
            "ST", "CF" -> traits.add("得分型")
            "LW", "RW" -> traits.add("边路快马")
            "CM", "AM" -> traits.add("组织型")
            "DM" -> traits.add("防守型")
            "CB" -> traits.add("防空型")
            "LB", "RB" -> traits.add("助攻型边卫")
            "GK" -> traits.add("门线型")
        }
        // 年龄相关特点
        if (discovery.playerAge <= 18) traits.add("年轻小将")
        else if (discovery.playerAge >= 30) traits.add("经验丰富")
        // 历史新星标记
        if (discovery.isHistoricalProspect) traits.add("潜力新星")
        return toJsonArrayString(traits)
    }

    /** 优势推导（V0.2 08 §四.2，简化版）。 */
    private fun deriveStrengths(player: PlayerEntity, state: SavePlayerStateEntity): String {
        val strengths = mutableListOf<String>()
        if (state.currentCa >= 80) strengths.add("顶级即战力")
        else if (state.currentCa >= 60) strengths.add("实力派")
        if (state.currentPa - state.currentCa >= 30) strengths.add("高成长空间")
        if (player.preferredFoot == "左") strengths.add("左脚将")
        return toJsonArrayString(if (strengths.isEmpty()) listOf("无明显突出项") else strengths)
    }

    /** 风险推导（V0.2 08 §四.2，简化版）。 */
    private fun deriveRisks(player: PlayerEntity, state: SavePlayerStateEntity, scout: com.greendynasty.football.data.history.entity.ScoutEntity): String {
        val risks = mutableListOf<String>()
        if (state.currentCa - state.currentPa >= -5) risks.add("潜力已耗尽")
        if (state.injuryStatus == "injured") risks.add("当前受伤中")
        // 注：PlayerEntity 仅有 birthDate，无 age 字段；年龄检查由调用方按 currentDate 推导
        if (state.morale < 40) risks.add("士气低落")
        return toJsonArrayString(if (risks.isEmpty()) listOf("无明显风险") else risks)
    }

    /** 成长速度推导（V0.2 08 §四.4，简化版）。 */
    private fun deriveGrowthSpeed(state: SavePlayerStateEntity): String {
        val paCaDiff = state.currentPa - state.currentCa
        return when {
            paCaDiff >= 50 -> "极快"
            paCaDiff >= 30 -> "快"
            paCaDiff >= 15 -> "中"
            paCaDiff >= 5 -> "慢"
            else -> "极慢"
        }
    }

    /** 隐藏标签推导（V0.2 08 §四.4，简化版）。 */
    private fun deriveHiddenTags(player: PlayerEntity, state: SavePlayerStateEntity): String {
        val tags = mutableListOf<String>()
        if (state.currentCa >= 85) tags.add("大赛型")
        if (state.morale >= 80) tags.add("领袖")
        if (player.preferredFoot == "左") tags.add("稀缺左脚")
        return toJsonArrayString(tags)
    }

    /** 适配战术推导（V0.2 08 §四.4，简化版）。 */
    private fun deriveTacticalFit(player: PlayerEntity): String {
        val pos = player.primaryPosition?.uppercase() ?: "CM"
        val fit = when {
            pos in listOf("ST", "CF", "LW", "RW") -> mapOf("进攻型" to 0.9, "防守反击" to 0.7, "控球" to 0.6)
            pos in listOf("CM", "AM") -> mapOf("控球" to 0.9, "进攻型" to 0.7, "防守型" to 0.5)
            pos in listOf("DM") -> mapOf("防守型" to 0.9, "防守反击" to 0.8, "控球" to 0.6)
            pos in listOf("CB", "LB", "RB") -> mapOf("防守型" to 0.9, "防守反击" to 0.7, "进攻型" to 0.4)
            else -> mapOf("控球" to 0.5)
        }
        // 简单 JSON 字符串
        return fit.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\":${it.value}" }
    }

    /** 简易 JSON 数组序列化（V0.2 08 §四，避免引入 JSON 库依赖）。 */
    private fun toJsonArrayString(items: List<String>): String {
        return items.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
    }
}
