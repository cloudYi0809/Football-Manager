package com.greendynasty.football.board.feedback

import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.board.model.BoardConfig
import com.greendynasty.football.board.model.BoardEventEntity
import com.greendynasty.football.board.model.BoardEventType
import com.greendynasty.football.board.model.BoardFeedback
import com.greendynasty.football.board.model.DismissalDecision
import com.greendynasty.football.board.model.DismissalLevel
import com.greendynasty.football.board.model.SatisfactionLevel
import com.greendynasty.football.board.model.SeasonTargetEvaluation
import com.greendynasty.football.data.api.DatabaseManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T22 董事会决策反馈服务（V0.2 11 §四 + T22 方案 §六 UI + 任务要求 §二.4）。
 *
 * 职责：
 * 1. 根据目标完成度给玩家反馈（满意 / 一般 / 不满 / 解雇警告）
 * 2. 生成董事会事件（8 类事件：赛季初会议 / 赛季中评估 / 赛季末总结 / 警告 / 威胁 / 换届 / 注资 / 收购）
 * 3. 写入 board_event 表供 UI 展示
 *
 * 反馈语气分档（V0.2 11 §四）：
 * - 满意（EXCELLENT/GOOD）：POSITIVE
 * - 一般（ACCEPTABLE）：NEUTRAL
 * - 不满（POOR）：NEGATIVE
 * - 解雇警告（CRITICAL 或 WARNING/ULTIMATUM/DISMISS）：CRITICAL
 *
 * @param databaseManager 三库管理入口
 * @param clubProfileRepository T18 俱乐部画像仓库
 * @param config 董事会配置
 */
class BoardFeedbackService(
    private val databaseManager: DatabaseManager,
    private val clubProfileRepository: ClubProfileRepository,
    private val config: BoardConfig = BoardConfig.DEFAULT
) {

    /**
     * 根据综合满意度生成董事会反馈。
     *
     * @param overallSatisfaction 综合满意度 0-100
     * @return 反馈对象（含标题、正文、语气）
     */
    fun generateFeedback(overallSatisfaction: Double): BoardFeedback {
        val level = SatisfactionLevel.fromScore(overallSatisfaction, config.satisfactionLevels)
        return when (level) {
            SatisfactionLevel.EXCELLENT -> BoardFeedback(
                level = "EXCELLENT",
                title = "董事会：非常满意",
                body = "董事会对球队当前的表现非常满意，认为你正带领球队走在正确的方向上。请继续保持。",
                tone = "POSITIVE"
            )
            SatisfactionLevel.GOOD -> BoardFeedback(
                level = "GOOD",
                title = "董事会：基本满意",
                body = "董事会对球队当前的表现基本满意，希望球队能在后续比赛中保持稳定发挥。",
                tone = "POSITIVE"
            )
            SatisfactionLevel.ACCEPTABLE -> BoardFeedback(
                level = "ACCEPTABLE",
                title = "董事会：尚可接受",
                body = "董事会对球队当前的表现尚可接受，但希望看到更多进步。请努力提升球队战绩。",
                tone = "NEUTRAL"
            )
            SatisfactionLevel.POOR -> BoardFeedback(
                level = "POOR",
                title = "董事会：表达不满",
                body = "董事会对球队当前的表现表达不满，战绩未达预期。请尽快扭转局势，否则将考虑调整帅位。",
                tone = "NEGATIVE"
            )
            SatisfactionLevel.CRITICAL -> BoardFeedback(
                level = "CRITICAL",
                title = "董事会：严重警告",
                body = "董事会对球队当前的表现极为不满，已经接近解雇边缘。立即改善战绩，否则将面临解雇。",
                tone = "CRITICAL"
            )
        }
    }

    /**
     * 根据赛季末评估 + 解雇判定生成反馈。
     *
     * @param evaluation 赛季目标评估结果
     * @param dismissal 解雇判定结果
     * @return 反馈对象
     */
    fun generateSeasonEndFeedback(
        evaluation: SeasonTargetEvaluation,
        dismissal: DismissalDecision
    ): BoardFeedback {
        return when (dismissal.warningLevel) {
            DismissalLevel.NONE -> when (evaluation.status) {
                "ACHIEVED" -> BoardFeedback(
                    level = "EXCELLENT",
                    title = "赛季结束：达成目标",
                    body = "董事会对本赛季的目标达成情况表示满意，期待下赛季继续努力。",
                    tone = "POSITIVE"
                )
                "PARTIALLY" -> BoardFeedback(
                    level = "ACCEPTABLE",
                    title = "赛季结束：部分达成",
                    body = "本赛季部分目标达成，董事会认为表现尚可接受，但仍有提升空间。",
                    tone = "NEUTRAL"
                )
                else -> BoardFeedback(
                    level = "POOR",
                    title = "赛季结束：未达预期",
                    body = "本赛季目标未达成，董事会对表现不满意，希望你下赛季能有所改进。",
                    tone = "NEGATIVE"
                )
            }
            DismissalLevel.WARNING -> BoardFeedback(
                level = "POOR",
                title = "董事会警告：连续未达成核心目标",
                body = dismissal.reason,
                tone = "NEGATIVE"
            )
            DismissalLevel.ULTIMATUM -> BoardFeedback(
                level = "CRITICAL",
                title = "最后通牒：本赛季为最终考验",
                body = dismissal.reason,
                tone = "CRITICAL"
            )
            DismissalLevel.DISMISS -> BoardFeedback(
                level = "CRITICAL",
                title = "解雇决定：董事会解除主帅职务",
                body = dismissal.reason,
                tone = "CRITICAL"
            )
        }
    }

    // ==================== 董事会事件生成 ====================

    /**
     * 赛季初：目标设定会议事件（V0.2 + T22 方案 §四.5）。
     */
    suspend fun triggerSeasonStartMeeting(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): BoardEventEntity {
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.SEASON_START_MEETING.name,
            title = "赛季初董事会会议：${seasonId} 赛季目标设定",
            body = "董事会召开了赛季初会议，请你确认本赛季目标。如不满意可申请降低（仅赛季前 1/3 阶段允许）。",
            relatedSeasonId = seasonId,
            severity = "INFO",
            playerActionRequired = true
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 赛季中评估事件（V0.2 + T22 方案 §四.5）。
     *
     * @param evaluationScore 赛季中评估综合分
     */
    suspend fun triggerMidSeasonReview(
        saveId: Int, clubId: Int, seasonId: Int,
        evaluationScore: Double, currentDate: LocalDate
    ): BoardEventEntity {
        val severity = if (evaluationScore < 40) "WARNING" else "INFO"
        val title = if (evaluationScore < 40)
            "赛季中评估：董事会表达担忧"
        else "赛季中评估：进展平稳"
        val body = buildMidSeasonBody(evaluationScore)

        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.MID_SEASON_REVIEW.name,
            title = title,
            body = body,
            relatedSeasonId = seasonId,
            severity = severity,
            playerActionRequired = evaluationScore < 40
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 赛季末总结事件（V0.2 + T22 方案 §四.5）。
     */
    suspend fun triggerSeasonEndSummary(
        saveId: Int, clubId: Int, seasonId: Int,
        evaluation: SeasonTargetEvaluation,
        dismissal: DismissalDecision,
        currentDate: LocalDate
    ): BoardEventEntity {
        val severity = when {
            dismissal.shouldDismiss -> "CRITICAL"
            evaluation.status == "FAILED" -> "WARNING"
            else -> "INFO"
        }
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.SEASON_END_SUMMARY.name,
            title = "赛季末总结：${seasonId} 赛季",
            body = buildSeasonEndBody(evaluation, dismissal),
            relatedSeasonId = seasonId,
            severity = severity,
            playerActionRequired = dismissal.warningLevel != DismissalLevel.NONE,
            impactSummary = "综合评分 ${evaluation.overallScore.toInt()}/100，状态：${evaluation.status}"
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 目标未达成警告事件（连续 2 赛季核心目标未达成）。
     */
    suspend fun triggerTargetMissedWarning(
        saveId: Int, clubId: Int, seasonId: Int,
        consecutiveFailed: Int, currentDate: LocalDate
    ): BoardEventEntity? {
        if (consecutiveFailed < config.dismissal.warningTriggerSeasons) return null
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.TARGET_MISSED_WARNING.name,
            title = "董事会警告：连续 $consecutiveFailed 赛季未达成核心目标",
            body = "董事会已经对连续的糟糕表现失去耐心。下赛季必须达成核心目标，否则将考虑解除你的职务。",
            relatedSeasonId = seasonId,
            severity = "WARNING",
            playerActionRequired = true
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 解雇威胁事件（最后通牒）。
     */
    suspend fun triggerDismissalThreat(
        saveId: Int, clubId: Int, seasonId: Int, currentDate: LocalDate
    ): BoardEventEntity {
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.DISMISSAL_THREAT.name,
            title = "最后通牒：本赛季为最终考验",
            body = "董事会主席召集了一次紧急会议，明确告知这是你最后的机会。本赛季若未能达成核心目标，将立即解除你的主帅职务。",
            relatedSeasonId = seasonId,
            severity = "CRITICAL",
            playerActionRequired = true
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 董事会换届事件（V0.2 + T22 方案 §四.5）。
     */
    suspend fun triggerBoardReshuffle(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): BoardEventEntity {
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.BOARD_RESHUFFLE.name,
            title = "董事会换届：新成员加入",
            body = "俱乐部董事会迎来了换届，部分成员离任，新成员加入。董事会的野心与耐心可能有所调整。",
            severity = "INFO",
            playerActionRequired = false
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 老板注资事件（V0.2 + T22 方案 §四.5）。
     */
    suspend fun triggerCapitalInjection(
        saveId: Int, clubId: Int, injectionAmount: Int, chairmanName: String,
        currentDate: LocalDate
    ): BoardEventEntity {
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.CAPITAL_INJECTION.name,
            title = "老板注资：$chairmanName 注入 ${injectionAmount / 1_000_000}M 欧元",
            body = "俱乐部老板 $chairmanName 宣布向俱乐部注资 ${injectionAmount / 1_000_000}M 欧元，用于强化阵容和改善财政状况。",
            severity = "INFO",
            playerActionRequired = false,
            impactSummary = "转会预算 +$injectionAmount"
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    /**
     * 被收购传闻事件（V0.2 + T22 方案 §四.5）。
     */
    suspend fun triggerTakeoverRumor(
        saveId: Int, clubId: Int, currentDate: LocalDate
    ): BoardEventEntity {
        val event = BoardEventEntity(
            saveId = saveId,
            clubId = clubId,
            eventDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            eventType = BoardEventType.TAKEOVER_RUMOR.name,
            title = "收购传闻：神秘财团有意收购俱乐部",
            body = "据媒体报道，一个神秘财团正在与俱乐部老板接触，有意完成收购。这可能改变董事会的野心与长期规划。",
            severity = "INFO",
            playerActionRequired = false,
            impactSummary = "未来可能改变董事会野心 +5-15"
        )
        databaseManager.getSaveDatabase().boardEventDao().insert(event)
        return event
    }

    // ==================== 文案构建 ====================

    private fun buildMidSeasonBody(evaluationScore: Double): String {
        return when {
            evaluationScore >= 80 -> "赛季过半，董事会认为球队表现优秀，目标达成在望。请继续保持。"
            evaluationScore >= 60 -> "赛季过半，球队表现基本符合预期，但仍有提升空间。"
            evaluationScore >= 40 -> "赛季过半，球队表现未达预期，董事会表达了担忧。希望下半赛季能有所改善。"
            else -> "赛季过半，球队表现令人失望，董事会已经对你表达强烈不满。如不迅速改善，将面临解雇风险。"
        }
    }

    private fun buildSeasonEndBody(
        evaluation: SeasonTargetEvaluation,
        dismissal: DismissalDecision
    ): String {
        val sb = StringBuilder()
        sb.appendLine("赛季评估完成：综合评分 ${evaluation.overallScore.toInt()}/100，状态 ${evaluation.status}。")
        sb.appendLine("联赛目标评分：${evaluation.leagueScore.toInt()}")
        sb.appendLine("杯赛目标评分：${evaluation.cupScore.toInt()}")
        sb.appendLine("欧战目标评分：${evaluation.europeanScore.toInt()}")
        sb.appendLine("财政目标评分：${evaluation.financialScore.toInt()}")
        sb.appendLine("青训目标评分：${evaluation.youthScore.toInt()}")
        if (dismissal.warningLevel != DismissalLevel.NONE) {
            sb.appendLine()
            sb.appendLine("董事会决议：${dismissal.reason}")
        }
        return sb.toString()
    }
}
