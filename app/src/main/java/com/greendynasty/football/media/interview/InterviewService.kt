package com.greendynasty.football.media.interview

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.media.model.AnswerOption
import com.greendynasty.football.media.model.AnswerStyle
import com.greendynasty.football.media.model.InterviewAnswerResult
import com.greendynasty.football.media.model.InterviewQuestion
import com.greendynasty.football.media.model.InterviewScenario
import com.greendynasty.football.media.model.MediaConfig
import com.greendynasty.football.media.model.MediaImpact
import com.greendynasty.football.media.model.MediaInterviewAnswerEntity
import com.greendynasty.football.media.model.MediaInterviewEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * T24 采访系统主入口（V0.2 + T24 任务要求 §核心要点 3 + 实现方案 §四.3）。
 *
 * 严格依据 V0.2 算法文档 + T24 实现方案 §五.2 采访影响计算：
 * 1. 调度采访（赛前 1-3 天 / 赛后）
 * 2. 抽取 4 个问题（按场景 + 上下文）
 * 3. 玩家选择回答 → 计算影响（5 维度：士气 / 球迷 / 董事会 / 舆论 / 声望）
 * 4. 上下文调整（危机场景激进代价更高 / 争议话题谦虚收益更高 / 赛前自信收益更高）
 * 5. 应用影响 + 记录回答历史 + 更新采访进度
 *
 * 2 类采访场景（T24 任务要求：赛前 / 赛后）：
 * - PRE_MATCH 赛前采访：赛前 1-3 天触发
 * - POST_MATCH 赛后采访：当日比赛结束后触发
 *
 * 5 种回答风格（V0.2 + T24 实现方案 §五.2）：
 * - NEUTRAL 中立 / CONFIDENT 自信 / HUMBLE 谦虚 / AGGRESSIVE 激进 / DEFLECT 转移
 *
 * @param databaseManager 三库管理入口
 * @param questionBank 采访问题库
 * @param config 媒体配置
 */
class InterviewService(
    private val databaseManager: DatabaseManager,
    private val questionBank: PressQuestionBank = PressQuestionBank(),
    private val config: MediaConfig = MediaConfig.DEFAULT
) {
    private val logger = Logger.getLogger("InterviewService")
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ==================== 1. 采访调度 ====================

    /**
     * 调度采访（实现方案 §四.3 scheduleForToday 简化版）。
     *
     * - PRE_MATCH：由调用方判断"赛前 1-3 天"条件后调用本方法
     * - POST_MATCH：由调用方判断"当日比赛已结束"后调用本方法
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param scenario 采访场景
     * @param currentDate 当前日期
     * @param context 采访上下文（对手 / 比分 / 连败次数等，可空）
     * @return 创建的采访实体（已入库），失败返回 null
     */
    suspend fun scheduleInterview(
        saveId: Int,
        clubId: Int,
        scenario: InterviewScenario,
        currentDate: LocalDate,
        context: Map<String, String> = emptyMap()
    ): MediaInterviewEntity? = withContext(Dispatchers.IO) {
        try {
            // 防重：同场景同日已存在 pending 则不再创建
            val existing = databaseManager.mediaInterviewDao()
                .getByStatus(saveId, clubId, "pending")
                .firstOrNull {
                    it.scenario == scenario.name && it.scheduledDate == currentDate.format(dateFormatter)
                }
            if (existing != null) return@withContext null

            // 抽取 4 个问题
            val questions = questionBank.pickQuestions(scenario, config.interview.questionsPerInterview)
            if (questions.isEmpty()) {
                logger.warning("采访问题库为空：scenario=$scenario")
                return@withContext null
            }

            val contextJson = encodeContext(context)
            val interview = MediaInterviewEntity(
                saveId = saveId,
                clubId = clubId,
                scenario = scenario.name,
                scheduledDate = currentDate.format(dateFormatter),
                status = "pending",
                questionIds = questions.joinToString(",") { it.questionId },
                currentQuestionIndex = 0,
                contextJson = contextJson
            )
            val id = databaseManager.mediaInterviewDao().insert(interview)
            interview.copy(interviewId = id)
        } catch (e: Exception) {
            logger.warning("调度采访失败：${e.message}")
            null
        }
    }

    // ==================== 2. 采访交互 ====================

    /**
     * 开始采访（将 pending → in_progress）。
     *
     * @return 当前采访 + 第一个问题，若采访不存在或状态不对返回 null
     */
    suspend fun startInterview(interviewId: Long): Pair<MediaInterviewEntity, InterviewQuestion>?
        = withContext(Dispatchers.IO) {
        try {
            val interview = databaseManager.mediaInterviewDao().get(interviewId) ?: return@withContext null
            if (interview.status !in listOf("pending", "in_progress")) return@withContext null

            val updated = if (interview.status == "pending") {
                interview.copy(status = "in_progress", startedAt = interview.startedAt)
            } else {
                interview
            }
            if (updated.status != interview.status) {
                databaseManager.mediaInterviewDao().update(updated)
            }

            val question = getCurrentQuestion(updated) ?: return@withContext null
            updated to question
        } catch (e: Exception) {
            logger.warning("开始采访失败：${e.message}")
            null
        }
    }

    /**
     * 获取当前采访问题（不修改状态）。
     */
    suspend fun getCurrentQuestion(interview: MediaInterviewEntity): InterviewQuestion? {
        val questionIds = interview.questionIds.split(",").filter { it.isNotBlank() }
        if (interview.currentQuestionIndex >= questionIds.size) return null
        val questionId = questionIds[interview.currentQuestionIndex]
        return questionBank.getQuestion(questionId)
    }

    /**
     * 回答当前问题（实现方案 §五.2 calculateAnswerImpact）。
     *
     * 流程：
     * 1. 加载采访 + 当前问题 + 选项
     * 2. 计算回答影响（基础 + 上下文调整）
     * 3. 记录回答历史
     * 4. 更新采访进度（currentQuestionIndex++）
     * 5. 完成时设置 status=completed
     *
     * @param interviewId 采访 ID
     * @param selectedOptionId 玩家选择的选项 ID
     * @param currentDate 当前日期
     * @return 回答结果（含最终影响 + 更新后的采访 + 是否最后一题 + 是否完成）
     */
    suspend fun answerQuestion(
        interviewId: Long,
        selectedOptionId: String,
        currentDate: LocalDate
    ): InterviewAnswerResult? = withContext(Dispatchers.IO) {
        try {
            val interview = databaseManager.mediaInterviewDao().get(interviewId) ?: return@withContext null
            if (interview.status != "in_progress") return@withContext null

            val question = getCurrentQuestion(interview) ?: return@withContext null
            val option = question.options.firstOrNull { it.optionId == selectedOptionId }
                ?: return@withContext null

            // 1. 计算回答影响（含上下文调整）
            val finalImpact = calculateAnswerImpact(option, question, interview)

            // 2. 记录回答历史
            databaseManager.mediaInterviewAnswerDao().insert(
                MediaInterviewAnswerEntity(
                    saveId = interview.saveId,
                    interviewId = interviewId,
                    questionId = question.questionId,
                    selectedOptionId = selectedOptionId,
                    answerDate = currentDate.format(dateFormatter),
                    impactJson = encodeImpact(finalImpact)
                )
            )

            // 3. 更新采访进度
            val questionIds = interview.questionIds.split(",").filter { it.isNotBlank() }
            val newIndex = interview.currentQuestionIndex + 1
            val isLast = newIndex >= questionIds.size
            val isCompleted = isLast
            val updated = interview.copy(
                currentQuestionIndex = newIndex,
                status = if (isCompleted) "completed" else "in_progress",
                completedAt = if (isCompleted) currentDate.format(dateFormatter) else null
            )
            databaseManager.mediaInterviewDao().update(updated)

            InterviewAnswerResult(
                impact = finalImpact,
                conference = updated,
                isLastQuestion = isLast,
                isCompleted = isCompleted
            )
        } catch (e: Exception) {
            logger.warning("回答采访问题失败：${e.message}")
            null
        }
    }

    /**
     * 跳过采访（实现方案 §六.2 跳过惩罚）。
     *
     * 跳过惩罚：舆论值 -10（[MediaConfig.InterviewParams.skipPenaltyOpinionDelta]）。
     *
     * @return 跳过产生的舆论影响（用于调用方应用到 [com.greendynasty.football.media.opinion.PublicOpinionManager]），失败返回 null
     */
    suspend fun skipInterview(
        interviewId: Long,
        currentDate: LocalDate
    ): MediaImpact? = withContext(Dispatchers.IO) {
        try {
            val interview = databaseManager.mediaInterviewDao().get(interviewId) ?: return@withContext null
            if (interview.status == "completed" || interview.status == "skipped") return@withContext null

            val updated = interview.copy(
                status = "skipped",
                completedAt = currentDate.format(dateFormatter)
            )
            databaseManager.mediaInterviewDao().update(updated)

            // 跳过惩罚：舆论值 -10
            MediaImpact(opinionDelta = config.interview.skipPenaltyOpinionDelta)
        } catch (e: Exception) {
            logger.warning("跳过采访失败：${e.message}")
            null
        }
    }

    // ==================== 3. 影响计算 ====================

    /**
     * 计算回答影响（实现方案 §五.2 calculateAnswerImpact）。
     *
     * 1. 取基础影响（来自 config.answerImpactBase[style]）
     * 2. 上下文调整：
     *    - 危机场景（CRISIS_RESPONSE）+ AGGRESSIVE → 代价 ×1.5
     *    - 争议话题（CONTROVERSY）+ HUMBLE → 收益 ×1.3
     *    - 赛前采访（PRE_MATCH）+ CONFIDENT → 收益 ×1.2
     * 3. 钳制单维度在 ±5 内（避免媒体盖过比赛）
     */
    private fun calculateAnswerImpact(
        option: AnswerOption,
        question: InterviewQuestion,
        interview: MediaInterviewEntity
    ): MediaImpact {
        var impact = option.impact

        // 1. 危机场景激进代价更高
        if (question.category.name == "CRISIS_RESPONSE" && option.style == AnswerStyle.AGGRESSIVE) {
            impact = scaleImpact(impact, config.answerContextMultipliers.crisisAggressive)
        }

        // 2. 争议话题谦虚收益更高
        if (question.category.name == "CONTROVERSY" && option.style == AnswerStyle.HUMBLE) {
            impact = scaleImpact(impact, config.answerContextMultipliers.controversyHumble)
        }

        // 3. 赛前采访自信收益更高
        if (interview.scenario == InterviewScenario.PRE_MATCH.name && option.style == AnswerStyle.CONFIDENT) {
            impact = scaleImpact(impact, config.answerContextMultipliers.preMatchConfident)
        }

        // 4. 钳制单维度在 ±maxImpactPerNews 内
        return clampImpact(impact)
    }

    /** 按比例缩放影响（取整）。 */
    private fun scaleImpact(impact: MediaImpact, factor: Double): MediaImpact {
        return MediaImpact(
            moraleDelta = (impact.moraleDelta * factor).toInt(),
            fanSatisfactionDelta = (impact.fanSatisfactionDelta * factor).toInt(),
            boardSatisfactionDelta = (impact.boardSatisfactionDelta * factor).toInt(),
            opinionDelta = (impact.opinionDelta * factor).toInt(),
            reputationDelta = (impact.reputationDelta * factor).toInt()
        )
    }

    /** 钳制单维度在 ±maxImpact 内。 */
    private fun clampImpact(impact: MediaImpact): MediaImpact {
        val max = config.maxImpactPerNews
        return MediaImpact(
            moraleDelta = impact.moraleDelta.coerceIn(-max, max),
            fanSatisfactionDelta = impact.fanSatisfactionDelta.coerceIn(-max, max),
            boardSatisfactionDelta = impact.boardSatisfactionDelta.coerceIn(-max, max),
            opinionDelta = impact.opinionDelta.coerceIn(-max, max),
            reputationDelta = impact.reputationDelta.coerceIn(-max, max)
        )
    }

    // ==================== 4. 查询接口 ====================

    /** 查询俱乐部待处理采访（pending + in_progress）。 */
    suspend fun getActiveInterviews(saveId: Int, clubId: Int): List<MediaInterviewEntity> =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaInterviewDao().getActive(saveId, clubId)
            } catch (e: Exception) {
                logger.warning("查询待处理采访失败：${e.message}")
                emptyList()
            }
        }

    /** 查询下一条待处理采访（按调度日期升序）。 */
    suspend fun getNextPendingInterview(saveId: Int, clubId: Int): MediaInterviewEntity? =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaInterviewDao().getNextByStatus(saveId, clubId, "pending")
            } catch (e: Exception) {
                logger.warning("查询下一条采访失败：${e.message}")
                null
            }
        }

    /** 查询采访历史回答记录。 */
    suspend fun getInterviewAnswers(saveId: Int, interviewId: Long): List<MediaInterviewAnswerEntity> =
        withContext(Dispatchers.IO) {
            try {
                databaseManager.mediaInterviewAnswerDao().getByInterview(saveId, interviewId)
            } catch (e: Exception) {
                logger.warning("查询采访回答历史失败：${e.message}")
                emptyList()
            }
        }

    // ==================== 5. JSON 编解码 ====================

    /** 编码采访上下文为 JSON。 */
    private fun encodeContext(context: Map<String, String>): String {
        if (context.isEmpty()) return ""
        return try {
            val obj = buildJsonObject {
                context.forEach { (k, v) -> put(k, v) }
            }
            json.encodeToString(JsonObject.serializer(), obj)
        } catch (e: Exception) {
            ""
        }
    }

    /** 编码媒体影响为 JSON（用于持久化回答历史）。 */
    private fun encodeImpact(impact: MediaImpact): String {
        return try {
            val obj = buildJsonObject {
                put("moraleDelta", impact.moraleDelta)
                put("fanSatisfactionDelta", impact.fanSatisfactionDelta)
                put("boardSatisfactionDelta", impact.boardSatisfactionDelta)
                put("opinionDelta", impact.opinionDelta)
                put("reputationDelta", impact.reputationDelta)
            }
            json.encodeToString(JsonObject.serializer(), obj)
        } catch (e: Exception) {
            ""
        }
    }
}
