package com.greendynasty.football.scouting.model

import com.greendynasty.football.data.history.entity.ScoutEntity
import com.greendynasty.football.data.save.entity.SavePlayerStateEntity
import java.time.LocalDate

/**
 * T14 球探系统领域模型集合（V0.2 08 §三 + §四 + §七 + T14 方案 §四）。
 *
 * 这里集中定义不持久化但用于服务/ViewModel 间传递的领域对象。
 * Entity 类（持久化）定义在 [com.greendynasty.football.scouting.data] 包。
 */

/**
 * 已雇佣球探的视图模型（球探基础信息 + 雇佣状态 + 地区知识汇总）。
 *
 * @param scout history.db 的球探只读模板
 * @param hired save.db 的雇佣记录（含状态/工资/合同到期等）
 * @param regionKnowledge 该球探对 15 地区的知识值列表
 */
data class ScoutWithKnowledge(
    val scout: ScoutEntity,
    val hired: com.greendynasty.football.scouting.data.SaveScoutHiredEntity,
    val regionKnowledge: List<com.greendynasty.football.scouting.data.SaveScoutRegionKnowledgeEntity>
) {
    /** 球探对指定地区的知识值（无记录返回基础值，由调用方配置决定）。 */
    fun knowledgeOf(regionCode: String): Int =
        regionKnowledge.find { it.regionCode == regionCode }?.knowledgeValue ?: 0

    /** 平均地区知识（用于球探总体评价）。 */
    val averageKnowledge: Int
        get() = if (regionKnowledge.isEmpty()) 0 else regionKnowledge.sumOf { it.knowledgeValue } / regionKnowledge.size
}

/**
 * 候选球员（V0.2 08 §三.5 候选池元素）。
 *
 * 由 [com.greendynasty.football.scouting.core.PlayerDiscoveryEngine] 构造，
 * 包含发现概率计算所需的全部字段。
 */
data class CandidatePlayer(
    val playerId: Int,
    val name: String,
    val age: Int,
    val position: String,
    val regionCode: String,
    val clubId: Int?,
    val leagueTier: Int,
    val monthsLeft: Int,
    val reputation: Int,
    val currentAbility: Int,
    val potentialAbility: Int,
    val isHistoricalProspect: Boolean = false
)

/**
 * 球探发现结果（V0.2 08 §三.5）。
 *
 * 由 [com.greendynasty.football.scouting.core.PlayerDiscoveryEngine.tryDiscover] 产出，
 * 交由 [com.greendynasty.football.scouting.core.ScoutReportGenerator] 生成初次报告。
 */
data class Discovery(
    val playerId: Int,
    val playerName: String,
    val playerAge: Int,
    val playerPosition: String,
    val playerRegion: String,
    val isHistoricalProspect: Boolean,
    val probability: Double
)

/**
 * 球探任务派遣请求（V0.2 08 §三.4 派遣参数）。
 *
 * 由 UI 弹窗构造，传入 [com.greendynasty.football.scouting.ScoutingService.dispatchTask]。
 */
data class DispatchTaskRequest(
    val saveId: Int,
    val clubId: Int,
    val scoutId: Int,
    val taskType: ScoutTaskType,
    val regionCode: String,
    val targetPosition: String? = null,
    val ageMin: Int = 15,
    val ageMax: Int = 35,
    val durationDays: Int = 30,
    val budgetLevel: BudgetLevel = BudgetLevel.MEDIUM,
    val startDate: LocalDate,
    val targetClubId: Int? = null,
    val youthTournamentId: String? = null
)

/**
 * 任务派遣结果。
 */
data class DispatchResult(
    val success: Boolean,
    val message: String,
    val taskId: Long = 0L
)

/**
 * 雇佣球探结果。
 */
data class HireScoutResult(
    val success: Boolean,
    val message: String,
    val hiredId: Long = 0L
)

/**
 * 球探推进结果（V0.2 08 §三.5 + §七）。
 *
 * 由 [com.greendynasty.football.scouting.ScoutingService.advanceDaily] 返回，
 * 供 T07 每日推进转换为新闻/待办事项。
 */
sealed class ScoutingAdvanceResult {
    /** 发现新球员（含初次报告）。 */
    data class Discovered(
        val discovery: Discovery,
        val report: com.greendynasty.football.scouting.data.SaveScoutReportEntity
    ) : ScoutingAdvanceResult()

    /** 任务完成。 */
    data class TaskCompleted(
        val task: com.greendynasty.football.scouting.data.SaveScoutTaskEntity
    ) : ScoutingAdvanceResult()

    /** 青年赛事事件触发。 */
    data class YouthEvent(
        val event: com.greendynasty.football.scouting.data.SaveScoutEventEntity
    ) : ScoutingAdvanceResult()

    /** 报告升级（达到新等级）。 */
    data class ReportUpgraded(
        val report: com.greendynasty.football.scouting.data.SaveScoutReportEntity,
        val oldLevel: Int,
        val newLevel: Int
    ) : ScoutingAdvanceResult()
}

/**
 * 报告详情视图模型（报告 + 球员基础信息 + 升级进度）。
 */
data class ScoutReportDetail(
    val report: com.greendynasty.football.scouting.data.SaveScoutReportEntity,
    val player: com.greendynasty.football.data.history.entity.PlayerEntity?,
    val playerState: SavePlayerStateEntity?,
    val scoutName: String,
    val nextLevelThreshold: Int, // 升级到下一级所需观察天数
    val currentLevelDisplay: String
)

/**
 * 球探任务视图模型（任务 + 球探姓名 + 进度信息）。
 */
data class ScoutTaskItem(
    val task: com.greendynasty.football.scouting.data.SaveScoutTaskEntity,
    val scoutName: String,
    val taskTypeDisplay: String,
    val regionDisplay: String,
    val progressPercent: Int, // 0-100
    val remainingDays: Int,
    val discoveredCount: Int
)

/**
 * 球探事件视图模型。
 */
data class ScoutEventItem(
    val event: com.greendynasty.football.scouting.data.SaveScoutEventEntity,
    val eventTypeDisplay: String,
    val playerName: String?
)
