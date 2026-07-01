package com.greendynasty.football.match.template

import com.greendynasty.football.match.model.EventType

/**
 * 球星模板类别注册表（V0.2 04 §九 共 6 类）
 *
 * 持有 6 类抽象球星模板，提供按 ID 查询与按球员查询接口。
 *
 * 6 类严格按 V0.2 §九：
 * - clutch_player：大心脏，落后时提升危险进攻与射门转化
 * - goal_machine：进球机器，全场提升射门事件与转化率
 * - playmaker：组织核心，提升控球与创造类事件
 * - wall：后防中坚，削弱对手危险进攻、提升解围
 * - wonderkid：天才少年，小幅全面提升但伤病风险略高
 * - leader：领袖，领先时稳节奏、提升防守贡献
 *
 * 严格遵循铁律：模板不直接加进球，只改事件权重与少量转化率微调。
 */
class StarTemplateCategoryRegistry {

    private val templates: Map<String, StarTemplateCategory> = mapOf(
        "clutch_player" to StarTemplateCategory(
            id = "clutch_player",
            name = "大心脏",
            triggerConditions = TriggerConditions(
                minMinute = 60,
                maxMinute = 94,
                onlyWhenTrailing = true,
                triggerChance = 1.0
            ),
            effects = TemplateEffects(
                eventModifiers = mapOf(
                    EventType.DANGEROUS_ATTACK to 0.10,
                    EventType.SHOT to 0.08,
                    EventType.PENALTY to 0.06
                ),
                shotConversionBonus = 0.10
            )
        ),
        "goal_machine" to StarTemplateCategory(
            id = "goal_machine",
            name = "进球机器",
            triggerConditions = TriggerConditions(
                minMinute = 0,
                maxMinute = 94,
                triggerChance = 1.0
            ),
            effects = TemplateEffects(
                eventModifiers = mapOf(
                    EventType.SHOT to 0.08,
                    EventType.SHOT_ON_TARGET to 0.08,
                    EventType.DANGEROUS_ATTACK to 0.06
                ),
                shotConversionBonus = 0.08,
                defensiveContributionDelta = -0.04
            )
        ),
        "playmaker" to StarTemplateCategory(
            id = "playmaker",
            name = "组织核心",
            triggerConditions = TriggerConditions(
                minMinute = 0,
                maxMinute = 94,
                triggerChance = 1.0
            ),
            effects = TemplateEffects(
                eventModifiers = mapOf(
                    EventType.POSSESSION to 0.10,
                    EventType.NORMAL_ATTACK to 0.08,
                    EventType.DANGEROUS_ATTACK to 0.06
                ),
                possessionDelta = 0.03
            )
        ),
        "wall" to StarTemplateCategory(
            id = "wall",
            name = "后防中坚",
            triggerConditions = TriggerConditions(
                minMinute = 0,
                maxMinute = 94,
                triggerChance = 1.0
            ),
            effects = TemplateEffects(
                eventModifiers = mapOf(
                    EventType.DANGEROUS_ATTACK to -0.08,
                    EventType.CLEARANCE to 0.08,
                    EventType.SAVE to 0.05
                ),
                defensiveContributionDelta = 0.08
            )
        ),
        "wonderkid" to StarTemplateCategory(
            id = "wonderkid",
            name = "天才少年",
            triggerConditions = TriggerConditions(
                minMinute = 0,
                maxMinute = 94,
                triggerChance = 1.0
            ),
            effects = TemplateEffects(
                eventModifiers = mapOf(
                    EventType.DANGEROUS_ATTACK to 0.06,
                    EventType.SHOT to 0.05,
                    EventType.POSSESSION to 0.04
                ),
                shotConversionBonus = 0.05,
                injuryRiskBonus = 0.03
            )
        ),
        "leader" to StarTemplateCategory(
            id = "leader",
            name = "领袖",
            triggerConditions = TriggerConditions(
                minMinute = 0,
                maxMinute = 94,
                onlyWhenLeading = true,
                triggerChance = 1.0
            ),
            effects = TemplateEffects(
                eventModifiers = mapOf(
                    EventType.POSSESSION to 0.06,
                    EventType.CLEARANCE to 0.05
                ),
                defensiveContributionDelta = 0.05,
                possessionDelta = 0.02
            )
        )
    )

    /** 按模板 ID 查询，未注册时抛 IllegalArgumentException */
    fun getTemplate(id: String): StarTemplateCategory =
        templates[id] ?: throw IllegalArgumentException("未注册的球星模板类别: $id")

    /** 安全查询，未注册返回 null */
    fun findTemplate(id: String): StarTemplateCategory? = templates[id]

    /**
     * 查询球员可用的全部模板类别。
     *
     * 当前实现：返回全部 6 类，由调用方结合球员位置 / 属性 / 历史模板筛选。
     * 后续可扩展为按球员标签（如 PlayerState.starTemplate）映射到类别。
     *
     * @param playerId 球员 ID
     * @return 该球员适用的模板类别列表（默认全部）
     */
    fun getTemplatesForPlayer(playerId: String): List<StarTemplateCategory> =
        templates.values.toList()

    /** 全部模板类别 */
    fun all(): Collection<StarTemplateCategory> = templates.values
}
