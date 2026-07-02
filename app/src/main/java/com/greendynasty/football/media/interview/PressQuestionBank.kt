package com.greendynasty.football.media.interview

import com.greendynasty.football.media.model.AnswerOption
import com.greendynasty.football.media.model.AnswerStyle
import com.greendynasty.football.media.model.InterviewQuestion
import com.greendynasty.football.media.model.InterviewQuestionCategory
import com.greendynasty.football.media.model.InterviewScenario
import com.greendynasty.football.media.model.MediaConfig
import com.greendynasty.football.media.model.MediaImpact
import kotlin.random.Random

/**
 * T24 采访问题库（V0.2 + T24 实现方案 §四.3 PressQuestionBank）。
 *
 * 内置赛前 / 赛后采访问题，每个问题 4 个选项（5 种回答风格中的 4 种）。
 * 由 [InterviewService] 按场景 + 上下文抽取。
 *
 * 5 种回答风格（V0.2 + T24 实现方案 §五.2）：
 * - NEUTRAL 中立 / CONFIDENT 自信 / HUMBLE 谦虚 / AGGRESSIVE 激进 / DEFLECT 转移
 *
 * 每个选项的基础影响来自 [MediaConfig.answerImpactBase]，
 * 由 [InterviewService] 按上下文（危机 / 争议 / 赛前）进一步调整。
 *
 * @param config 媒体配置（用于读取 5 种风格基础影响）
 */
class PressQuestionBank(
    private val config: MediaConfig = MediaConfig.DEFAULT
) {

    /** 全部问题（按 scenario 分组）。 */
    private val allQuestions: Map<InterviewScenario, List<InterviewQuestion>> = buildAllQuestions()

    /**
     * 按场景抽取 N 个问题。
     *
     * @param scenario 采访场景（PRE_MATCH / POST_MATCH）
     * @param count 抽取问题数（默认来自 config.interview.questionsPerInterview）
     * @return 抽取的问题列表（随机洗牌后取前 N 个）
     */
    fun pickQuestions(
        scenario: InterviewScenario,
        count: Int = config.interview.questionsPerInterview
    ): List<InterviewQuestion> {
        val pool = allQuestions[scenario] ?: return emptyList()
        return pool.shuffled(Random).take(count.coerceAtMost(pool.size))
    }

    /** 根据 ID 查询问题（用于回答时还原问题上下文）。 */
    fun getQuestion(questionId: String): InterviewQuestion? {
        for (questions in allQuestions.values) {
            val q = questions.firstOrNull { it.questionId == questionId }
            if (q != null) return q
        }
        return null
    }

    /** 获取全部问题（用于调试 / 测试）。 */
    fun getAllQuestions(): Map<InterviewScenario, List<InterviewQuestion>> = allQuestions

    // ==================== 问题构建 ====================

    private fun buildAllQuestions(): Map<InterviewScenario, List<InterviewQuestion>> {
        return mapOf(
            InterviewScenario.PRE_MATCH to buildPreMatchQuestions(),
            InterviewScenario.POST_MATCH to buildPostMatchQuestions()
        )
    }

    /** 赛前采访问题（≥6 题，覆盖不同分类）。 */
    private fun buildPreMatchQuestions(): List<InterviewQuestion> = listOf(
        buildQuestion(
            questionId = "pre_match_opponent_1",
            scenario = InterviewScenario.PRE_MATCH,
            category = InterviewQuestionCategory.OPPONENT,
            questionText = "下一场对阵 {opponent_name}，您对对手如何评价？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "他们是一支强队，我们会认真对待。",
                AnswerStyle.CONFIDENT to "我们不怕任何对手，目标是全取三分。",
                AnswerStyle.HUMBLE to "对手实力很强，我们需要发挥出最佳水平。",
                AnswerStyle.DEFLECT to "我们更关注自己的备战。"
            )
        ),
        buildQuestion(
            questionId = "pre_match_tactics_1",
            scenario = InterviewScenario.PRE_MATCH,
            category = InterviewQuestionCategory.TACTICS,
            questionText = "本场比赛的战术安排是否有特别之处？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "我们会沿用之前的战术框架。",
                AnswerStyle.CONFIDENT to "我们准备了针对性的战术，会让对手措手不及。",
                AnswerStyle.HUMBLE to "战术细节不便透露，但球队已经做好了准备。",
                AnswerStyle.AGGRESSIVE to "战术不是关键，关键是球员有没有斗志！"
            )
        ),
        buildQuestion(
            questionId = "pre_match_season_goal_1",
            scenario = InterviewScenario.PRE_MATCH,
            category = InterviewQuestionCategory.SEASON_GOAL,
            questionText = "球队本赛季的目标是什么？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "我们的目标是稳步前进，争取每场比赛都有进步。",
                AnswerStyle.CONFIDENT to "目标是冠军，我们有实力冲击最高荣誉。",
                AnswerStyle.HUMBLE to "我们会一场一场拼，赛季末再看结果。",
                AnswerStyle.DEFLECT to "目标由俱乐部高层决定，我专注于下一场。"
            )
        ),
        buildQuestion(
            questionId = "pre_match_player_perf_1",
            scenario = InterviewScenario.PRE_MATCH,
            category = InterviewQuestionCategory.PLAYER_PERFORMANCE,
            questionText = "{player_name} 最近状态火热，您如何评价他的表现？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "他训练刻苦，状态确实不错。",
                AnswerStyle.CONFIDENT to "他是球队的核心，本场比赛他会再次证明自己。",
                AnswerStyle.HUMBLE to "足球是团队运动，他能有现在的表现离不开队友支持。",
                AnswerStyle.AGGRESSIVE to "媒体太吹捧他了，他还有很大提升空间！"
            )
        ),
        buildQuestion(
            questionId = "pre_match_transfer_plan_1",
            scenario = InterviewScenario.PRE_MATCH,
            category = InterviewQuestionCategory.TRANSFER_PLAN,
            questionText = "转会窗即将开启，球队有引援计划吗？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "俱乐部一直在关注合适的引援目标。",
                AnswerStyle.CONFIDENT to "我们已经在运作几笔重磅签约，敬请期待。",
                AnswerStyle.HUMBLE to "引援需要谨慎，我们会按需补强。",
                AnswerStyle.DEFLECT to "这是俱乐部高层的事，我只负责现有阵容。"
            )
        ),
        buildQuestion(
            questionId = "pre_match_crisis_1",
            scenario = InterviewScenario.PRE_MATCH,
            category = InterviewQuestionCategory.CRISIS_RESPONSE,
            questionText = "球队近期战绩不佳，您是否感到压力？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "压力是教练工作的一部分，我会坦然面对。",
                AnswerStyle.CONFIDENT to "我不担心，球队很快会回到正轨。",
                AnswerStyle.HUMBLE to "确实有压力，我会和球员一起努力走出困境。",
                AnswerStyle.AGGRESSIVE to "媒体的质疑不会影响我，我们的方向是对的！"
            )
        )
    )

    /** 赛后采访问题（≥6 题，覆盖不同分类）。 */
    private fun buildPostMatchQuestions(): List<InterviewQuestion> = listOf(
        buildQuestion(
            questionId = "post_match_result_1",
            scenario = InterviewScenario.POST_MATCH,
            category = InterviewQuestionCategory.MATCH_RESULT,
            questionText = "今天 {score} 的结果，您如何评价本场比赛？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "比赛已经结束，我们接受结果。",
                AnswerStyle.CONFIDENT to "球队踢得不错，胜利实至名归。",
                AnswerStyle.HUMBLE to "对手表现出色，我们能在他们身上拿到分数已经不易。",
                AnswerStyle.AGGRESSIVE to "裁判的判罚影响了比赛，否则我们会赢得更轻松！"
            )
        ),
        buildQuestion(
            questionId = "post_match_result_2",
            scenario = InterviewScenario.POST_MATCH,
            category = InterviewQuestionCategory.MATCH_RESULT,
            questionText = "对今天球员们的表现满意吗？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "球员们都尽力了，整体表现可以接受。",
                AnswerStyle.CONFIDENT to "球员们非常出色，他们的努力得到了回报。",
                AnswerStyle.HUMBLE to "还有提升空间，我们会在训练中继续打磨。",
                AnswerStyle.AGGRESSIVE to "部分球员的态度让我失望，我会单独和他们谈！"
            )
        ),
        buildQuestion(
            questionId = "post_match_player_perf_1",
            scenario = InterviewScenario.POST_MATCH,
            category = InterviewQuestionCategory.PLAYER_PERFORMANCE,
            questionText = "{player_name} 本场表现亮眼，是否是取胜关键？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "他发挥了重要作用，但胜利是全队的功劳。",
                AnswerStyle.CONFIDENT to "他就是我们的核心，本场再次证明了自己的价值。",
                AnswerStyle.HUMBLE to "他确实踢得不错，但足球是团队运动。",
                AnswerStyle.DEFLECT to "我希望大家关注全队的表现，而不是某一个人。"
            )
        ),
        buildQuestion(
            questionId = "post_match_tactics_1",
            scenario = InterviewScenario.POST_MATCH,
            category = InterviewQuestionCategory.TACTICS,
            questionText = "本场战术安排效果如何？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "战术执行基本到位，效果符合预期。",
                AnswerStyle.CONFIDENT to "战术安排非常成功，对手完全被我们压制。",
                AnswerStyle.HUMBLE to "战术上还有改进空间，我们会在复盘后调整。",
                AnswerStyle.AGGRESSIVE to "战术没问题，是球员执行出了偏差！"
            )
        ),
        buildQuestion(
            questionId = "post_match_controversy_1",
            scenario = InterviewScenario.POST_MATCH,
            category = InterviewQuestionCategory.CONTROVERSY,
            questionText = "比赛中出现了争议判罚，您怎么看？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "裁判的判罚已经做出，我们尊重决定。",
                AnswerStyle.CONFIDENT to "那个判罚改变了比赛，但我们依然展现了实力。",
                AnswerStyle.HUMBLE to "我不想评论裁判，希望未来比赛能更公平。",
                AnswerStyle.AGGRESSIVE to "那是个明显的误判！裁判应该被追责！"
            )
        ),
        buildQuestion(
            questionId = "post_match_next_1",
            scenario = InterviewScenario.POST_MATCH,
            category = InterviewQuestionCategory.SEASON_GOAL,
            questionText = "下一场比赛球队会如何调整？",
            optionStyles = listOf(
                AnswerStyle.NEUTRAL to "我们会按部就班备战下一场。",
                AnswerStyle.CONFIDENT to "下一场我们会继续赢下去。",
                AnswerStyle.HUMBLE to "下一场的对手很强，我们会认真准备。",
                AnswerStyle.DEFLECT to "现在讨论下一场还太早，先享受今天的胜利。"
            )
        )
    )

    /**
     * 构建单个采访问题（含 4 个选项）。
     *
     * @param questionId 问题 ID
     * @param scenario 采访场景
     * @param category 问题分类
     * @param questionText 问题文案（含占位符）
     * @param optionStyles 4 个选项的风格 + 文本
     * @return 完整的 [InterviewQuestion]
     */
    private fun buildQuestion(
        questionId: String,
        scenario: InterviewScenario,
        category: InterviewQuestionCategory,
        questionText: String,
        optionStyles: List<Pair<AnswerStyle, String>>
    ): InterviewQuestion {
        val options = optionStyles.mapIndexed { index, (style, text) ->
            AnswerOption(
                optionId = "${style.name.lowercase()}_${index + 1}",
                style = style,
                text = text,
                impact = config.answerImpactBase[style] ?: MediaImpact()
            )
        }
        return InterviewQuestion(
            questionId = questionId,
            scenario = scenario,
            category = category,
            questionText = questionText,
            options = options
        )
    }
}
