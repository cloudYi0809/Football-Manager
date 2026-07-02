package com.greendynasty.football.media.news

import com.greendynasty.football.media.model.NewsCategory
import com.greendynasty.football.media.model.NewsImportance

/**
 * T24 新闻模板数据类（V0.2 + T24 实现方案 §四.2 NewsTemplate）。
 *
 * 模板含占位符（`{player_name}` / `{club_name}` / `{opponent_name}` / `{score}` / `{date}` / `{outlet_name}`），
 * 由 [NewsGenerator.renderTemplate] 替换为最终文案。
 *
 * @property templateId 模板 ID（如 "match_report_win_1"）
 * @property category 新闻分类
 * @property titleTemplate 标题模板
 * @property bodyTemplate 正文模板
 * @property importanceBase 基础重要性 1-5
 * @property applicableEvents 适用事件类型列表（用于匹配 [com.greendynasty.football.media.model.NewsEventInput.eventType]）
 */
data class NewsTemplate(
    val templateId: String,
    val category: NewsCategory,
    val titleTemplate: String,
    val bodyTemplate: String,
    val importanceBase: Int,
    val applicableEvents: List<String>
)

/**
 * T24 新闻模板注册表（V0.2 + T24 实现方案 §四.2 NewsTemplateRegistry）。
 *
 * 内置 6 类新闻模板（每类 ≥5 个），按事件类型索引。
 * 在游戏启动时一次性加载到内存（性能优化策略 §十.6）。
 *
 * 6 类新闻模板：
 * - MATCH 比赛类（赛前前瞻 / 赛后报道）
 * - TRANSFER 转会类（转会传闻 / 转会完成）
 * - INJURY 伤病类
 * - HONOR 荣誉类（夺冠 / 个人奖项 / 里程碑）
 * - BUTTERFLY 蝴蝶类（联动 T20 历史回顾）
 * - GOSSIP 八卦类（球员场外 / 经理传闻）
 */
object NewsTemplateRegistry {

    /** 全部模板（按 category 分组）。 */
    private val allTemplates: Map<NewsCategory, List<NewsTemplate>> = buildAllTemplates()

    /** 事件类型 → 模板列表索引（启动时构建）。 */
    private val eventIndex: Map<String, List<NewsTemplate>> = buildEventIndex()

    /** 背景新闻模板（无事件时使用，1 星）。 */
    private val backgroundTemplates: List<NewsTemplate> = buildBackgroundTemplates()

    /**
     * 按事件类型查询模板列表。
     * @param eventType 事件类型（如 "MATCH_FINISHED" / "TRANSFER_COMPLETED"）
     * @return 适用模板列表（可能为空）
     */
    fun getTemplatesForEvent(eventType: String): List<NewsTemplate> {
        return eventIndex[eventType] ?: emptyList()
    }

    /** 获取背景新闻模板（1 星）。 */
    fun getBackgroundTemplates(): List<NewsTemplate> = backgroundTemplates

    /** 获取所有模板（用于调试 / 测试）。 */
    fun getAllTemplates(): Map<NewsCategory, List<NewsTemplate>> = allTemplates

    // ==================== 模板构建 ====================

    private fun buildAllTemplates(): Map<NewsCategory, List<NewsTemplate>> {
        return mapOf(
            NewsCategory.MATCH to buildMatchTemplates(),
            NewsCategory.TRANSFER to buildTransferTemplates(),
            NewsCategory.INJURY to buildInjuryTemplates(),
            NewsCategory.HONOR to buildHonorTemplates(),
            NewsCategory.BUTTERFLY to buildButterflyTemplates(),
            NewsCategory.GOSSIP to buildGossipTemplates()
        )
    }

    /** 比赛类模板（≥5 个）。 */
    private fun buildMatchTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "match_preview_1",
            category = NewsCategory.MATCH,
            titleTemplate = "{club_name} 备战 {opponent_name}：主帅信心十足",
            bodyTemplate = "{outlet_name} 报道，{club_name} 即将在 {date} 迎战 {opponent_name}，赛前训练气氛紧张而专注。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("MATCH_SCHEDULED", "PRE_MATCH")
        ),
        NewsTemplate(
            templateId = "match_preview_2",
            category = NewsCategory.MATCH,
            titleTemplate = "前瞻：{club_name} vs {opponent_name}，谁能笑到最后？",
            bodyTemplate = "本周焦点战役即将打响，{club_name} 与 {opponent_name} 的对决吸引了全场目光。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("MATCH_SCHEDULED", "PRE_MATCH")
        ),
        NewsTemplate(
            templateId = "match_report_win_1",
            category = NewsCategory.MATCH,
            titleTemplate = "{club_name} {score} 击败 {opponent_name}，全取三分",
            bodyTemplate = "在 {date} 的比赛中，{club_name} 凭借出色发挥以 {score} 战胜 {opponent_name}，赢得关键胜利。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("MATCH_FINISHED", "MATCH_WIN")
        ),
        NewsTemplate(
            templateId = "match_report_win_2",
            category = NewsCategory.MATCH,
            titleTemplate = "完胜！{club_name} 主场 {score} 大胜 {opponent_name}",
            bodyTemplate = "{club_name} 主场作战，{score} 的比分让主场球迷欣喜若狂，{opponent_name} 全场被动。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("MATCH_FINISHED", "MATCH_WIN")
        ),
        NewsTemplate(
            templateId = "match_report_loss_1",
            category = NewsCategory.MATCH,
            titleTemplate = "{club_name} {score} 不敌 {opponent_name}，主场饮恨",
            bodyTemplate = "{date} 的比赛中，{club_name} 状态低迷，{score} 输给 {opponent_name}，主帅面临压力。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("MATCH_FINISHED", "MATCH_LOSS")
        ),
        NewsTemplate(
            templateId = "match_report_draw_1",
            category = NewsCategory.MATCH,
            titleTemplate = "{club_name} {score} 战平 {opponent_name}，各取一分",
            bodyTemplate = "一场势均力敌的较量，{club_name} 与 {opponent_name} 最终 {score} 握手言和。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("MATCH_FINISHED", "MATCH_DRAW")
        ),
        NewsTemplate(
            templateId = "match_report_derby_1",
            category = NewsCategory.MATCH,
            titleTemplate = "德比战火！{club_name} {score} {opponent_name}，激情碰撞",
            bodyTemplate = "万众瞩目的德比大战在 {date} 上演，{club_name} 与 {opponent_name} 为球迷奉献了一场 {score} 的精彩对决。",
            importanceBase = NewsImportance.CRITICAL.stars,
            applicableEvents = listOf("DERBY_MATCH", "MATCH_FINISHED")
        )
    )

    /** 转会类模板（≥5 个）。 */
    private fun buildTransferTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "transfer_in_1",
            category = NewsCategory.TRANSFER,
            titleTemplate = "官方：{player_name} 加盟 {club_name}",
            bodyTemplate = "{club_name} 官方宣布，{player_name} 正式加盟球队，俱乐部对此笔签约充满期待。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("TRANSFER_COMPLETED", "TRANSFER_COMPLETED_TOP")
        ),
        NewsTemplate(
            templateId = "transfer_in_2",
            category = NewsCategory.TRANSFER,
            titleTemplate = "重磅！{player_name} 转会 {club_name} 落定",
            bodyTemplate = "{outlet_name} 消息，{player_name} 的转会传闻终于尘埃落定，他将披上 {club_name} 战袍。",
            importanceBase = NewsImportance.CRITICAL.stars,
            applicableEvents = listOf("TRANSFER_COMPLETED", "TRANSFER_COMPLETED_TOP")
        ),
        NewsTemplate(
            templateId = "transfer_out_1",
            category = NewsCategory.TRANSFER,
            titleTemplate = "{player_name} 离队！{club_name} 与其告别",
            bodyTemplate = "{club_name} 官方确认，{player_name} 已完成转会离队，俱乐部感谢其贡献。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("TRANSFER_OUT", "TRANSFER_COMPLETED")
        ),
        NewsTemplate(
            templateId = "transfer_rumor_1",
            category = NewsCategory.TRANSFER,
            titleTemplate = "传 {player_name} 或将加盟 {club_name}",
            bodyTemplate = "{outlet_name} 爆料，{player_name} 与 {club_name} 走得很近，转会可能在窗口期内完成。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("TRANSFER_RUMOR", "TRANSFER_TALK")
        ),
        NewsTemplate(
            templateId = "transfer_rumor_2",
            category = NewsCategory.TRANSFER,
            titleTemplate = "{club_name} 盯上 {player_name}？球探多次现身看台",
            bodyTemplate = "据 {outlet_name} 报道，{club_name} 球探近期多次考察 {player_name}，转会传闻持续发酵。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("TRANSFER_RUMOR", "SCOUT_REPORT")
        )
    )

    /** 伤病类模板（≥5 个）。 */
    private fun buildInjuryTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "injury_report_1",
            category = NewsCategory.INJURY,
            titleTemplate = "噩耗：{player_name} 受伤将缺阵数周",
            bodyTemplate = "{club_name} 官方确认，{player_name} 在训练中受伤，预计将缺席数周比赛。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("INJURY_OCCURRED")
        ),
        NewsTemplate(
            templateId = "injury_report_2",
            category = NewsCategory.INJURY,
            titleTemplate = "{player_name} 伤情更新：{club_name} 主帅深感担忧",
            bodyTemplate = "{outlet_name} 报道，{player_name} 的伤势比预期更严重，{club_name} 主帅对此深感担忧。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("INJURY_OCCURRED", "INJURY_UPDATE")
        ),
        NewsTemplate(
            templateId = "injury_report_3",
            category = NewsCategory.INJURY,
            titleTemplate = "{club_name} 阵中再伤一员，{player_name} 进入伤病名单",
            bodyTemplate = "伤病潮袭来，{club_name} 又一名主力 {player_name} 倒下，球队阵容深度面临考验。",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("INJURY_OCCURRED")
        ),
        NewsTemplate(
            templateId = "injury_comeback_1",
            category = NewsCategory.INJURY,
            titleTemplate = "利好：{player_name} 伤愈复出在即",
            bodyTemplate = "{club_name} 收到好消息，{player_name} 已恢复训练，复出在望。",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("INJURY_RECOVERED", "PLAYER_RETURN")
        ),
        NewsTemplate(
            templateId = "injury_comeback_2",
            category = NewsCategory.INJURY,
            titleTemplate = "{player_name} 重返赛场，{club_name} 如虎添翼",
            bodyTemplate = "经过长期康复，{player_name} 终于重返赛场，{club_name} 实力得到补强。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("INJURY_RECOVERED", "PLAYER_RETURN")
        )
    )

    /** 荣誉类模板（≥5 个）。 */
    private fun buildHonorTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "honor_title_1",
            category = NewsCategory.HONOR,
            titleTemplate = "冠军！{club_name} 加冕联赛之王",
            bodyTemplate = "{date} 见证历史，{club_name} 提前锁定联赛冠军，全队欢庆这一荣耀时刻。",
            importanceBase = NewsImportance.CRITICAL.stars,
            applicableEvents = listOf("TITLE_WON", "LEAGUE_WON")
        ),
        NewsTemplate(
            templateId = "honor_title_2",
            category = NewsCategory.HONOR,
            titleTemplate = "{club_name} 夺冠！球迷涌入球场庆祝",
            bodyTemplate = "{club_name} 时隔多年再次夺冠，球迷涌入球场狂欢，整座城市为之沸腾。",
            importanceBase = NewsImportance.CRITICAL.stars,
            applicableEvents = listOf("TITLE_WON", "CUP_WON")
        ),
        NewsTemplate(
            templateId = "honor_individual_1",
            category = NewsCategory.HONOR,
            titleTemplate = "{player_name} 当选年度最佳球员",
            bodyTemplate = "{player_name} 凭借出色表现当选年度最佳球员，{club_name} 全队为其喝彩。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("INDIVIDUAL_AWARD", "PLAYER_OF_YEAR")
        ),
        NewsTemplate(
            templateId = "honor_milestone_1",
            category = NewsCategory.HONOR,
            titleTemplate = "里程碑！{player_name} 达成百场成就",
            bodyTemplate = "{player_name} 在 {club_name} 达成代表球队出战百场的里程碑，俱乐部为其颁发了纪念奖杯。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("MILESTONE", "PLAYER_MILESTONE")
        ),
        NewsTemplate(
            templateId = "honor_promotion_1",
            category = NewsCategory.HONOR,
            titleTemplate = "{club_name} 成功升级，下赛季征战更高舞台",
            bodyTemplate = "{club_name} 在 {date} 锁定升级名额，下赛季将征战更高级别联赛。",
            importanceBase = NewsImportance.HIGH.stars,
            applicableEvents = listOf("PROMOTION", "TITLE_WON")
        )
    )

    /** 蝴蝶类模板（≥5 个，联动 T20 历史回顾）。 */
    private fun buildButterflyTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "butterfly_recall_1",
            category = NewsCategory.BUTTERFLY,
            titleTemplate = "历史回眸：{player_name} 浮现，未来之星或被发掘",
            bodyTemplate = "{outlet_name} 专栏回顾，{player_name} 在青训营中的表现引发关注，他或许就是下一个传奇。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("BUTTERFLY_TRIGGERED", "PROSPECT_DISCOVERED")
        ),
        NewsTemplate(
            templateId = "butterfly_recall_2",
            category = NewsCategory.BUTTERFLY,
            titleTemplate = "十年前的今天：{club_name} 那场经典战役",
            bodyTemplate = "{outlet_name} 历史专栏回顾了 {club_name} 十年前的经典战役，那场比赛改变了无数人的命运。",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("BUTTERFLY_TRIGGERED", "ANNIVERSARY")
        ),
        NewsTemplate(
            templateId = "butterfly_recall_3",
            category = NewsCategory.BUTTERFLY,
            titleTemplate = "如果当年...{player_name} 的另一条职业路径",
            bodyTemplate = "{outlet_name} 假想专栏：如果 {player_name} 当年选择了不同的俱乐部，他的职业生涯会如何改写？",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("BUTTERFLY_TRIGGERED", "ALTERNATE_PATH")
        ),
        NewsTemplate(
            templateId = "butterfly_prospect_1",
            category = NewsCategory.BUTTERFLY,
            titleTemplate = "球探报告：{player_name} 是下一个巨星吗？",
            bodyTemplate = "{outlet_name} 获得独家球探报告，{player_name} 的天赋被高度评价，他可能成为下一位世界级球星。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("PROSPECT_DISCOVERED", "BUTTERFLY_TRIGGERED")
        ),
        NewsTemplate(
            templateId = "butterfly_anniversary_1",
            category = NewsCategory.BUTTERFLY,
            titleTemplate = "周年纪念：{player_name} 转会 {club_name} 已满 N 年",
            bodyTemplate = "{date} 是 {player_name} 转会 {club_name} 的周年纪念日，回顾这笔交易，它对双方都产生了深远影响。",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("ANNIVERSARY", "BUTTERFLY_TRIGGERED")
        )
    )

    /** 八卦类模板（≥5 个）。 */
    private fun buildGossipTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "gossip_player_1",
            category = NewsCategory.GOSSIP,
            titleTemplate = "{player_name} 场外风波，{club_name} 暂未回应",
            bodyTemplate = "{outlet_name} 爆料，{player_name} 卷入一场场外风波，{club_name} 俱乐部暂未对此事作出回应。",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("SCANDAL", "PLAYER_OFFFIELD")
        ),
        NewsTemplate(
            templateId = "gossip_player_2",
            category = NewsCategory.GOSSIP,
            titleTemplate = "{player_name} 夜店照流出，球迷热议",
            bodyTemplate = "{player_name} 在夜店的影像在社交媒体流传，引发球迷热议，{club_name} 内部据说已介入处理。",
            importanceBase = NewsImportance.LOW.stars,
            applicableEvents = listOf("SCANDAL", "PLAYER_OFFFIELD")
        ),
        NewsTemplate(
            templateId = "gossip_manager_1",
            category = NewsCategory.GOSSIP,
            titleTemplate = "{club_name} 主帅下课传闻四起",
            bodyTemplate = "近期战绩不佳，{club_name} 主帅下课的传闻开始蔓延，俱乐部高层尚未表态。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("SACK_RUMOR", "MANAGER_CRISIS")
        ),
        NewsTemplate(
            templateId = "gossip_dressing_1",
            category = NewsCategory.GOSSIP,
            titleTemplate = "更衣室裂痕？{club_name} 内部矛盾曝光",
            bodyTemplate = "{outlet_name} 报道，{club_name} 更衣室疑似出现裂痕，多名主力对主帅的战术安排表达不满。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("DRESSING_ROOM_CONFLICT", "SQUAD_UNREST")
        ),
        NewsTemplate(
            templateId = "gossip_board_1",
            category = NewsCategory.GOSSIP,
            titleTemplate = "{club_name} 易主在即？神秘买家接触俱乐部",
            bodyTemplate = "传闻有神秘买家正在接触 {club_name}，俱乐部易主的消息在 {date} 引发关注。",
            importanceBase = NewsImportance.NORMAL.stars,
            applicableEvents = listOf("CAPITAL_TAKEOVER", "OWNERSHIP_CHANGE")
        )
    )

    /** 背景新闻模板（1 星，无事件时填充用）。 */
    private fun buildBackgroundTemplates(): List<NewsTemplate> = listOf(
        NewsTemplate(
            templateId = "background_league_1",
            category = NewsCategory.MATCH,
            titleTemplate = "联赛综述：本轮多场比赛结果出炉",
            bodyTemplate = "{date} 联赛多场比赛战罢，积分榜形势出现新的变化。",
            importanceBase = NewsImportance.BACKGROUND.stars,
            applicableEvents = emptyList()
        ),
        NewsTemplate(
            templateId = "background_player_1",
            category = NewsCategory.GOSSIP,
            titleTemplate = "球员观察：谁是本轮最值得关注的球员？",
            bodyTemplate = "{outlet_name} 球员观察专栏，{date} 推荐一位本轮表现值得关注的球员。",
            importanceBase = NewsImportance.BACKGROUND.stars,
            applicableEvents = emptyList()
        ),
        NewsTemplate(
            templateId = "background_history_1",
            category = NewsCategory.BUTTERFLY,
            titleTemplate = "历史上的今天：足坛经典时刻",
            bodyTemplate = "{outlet_name} 历史专栏，回顾 {date} 这一天发生的足坛经典时刻。",
            importanceBase = NewsImportance.BACKGROUND.stars,
            applicableEvents = emptyList()
        )
    )

    /** 构建事件类型 → 模板列表索引。 */
    private fun buildEventIndex(): Map<String, List<NewsTemplate>> {
        val index = mutableMapOf<String, MutableList<NewsTemplate>>()
        for (templates in allTemplates.values) {
            for (template in templates) {
                for (eventType in template.applicableEvents) {
                    index.getOrPut(eventType) { mutableListOf() }.add(template)
                }
            }
        }
        return index
    }
}
