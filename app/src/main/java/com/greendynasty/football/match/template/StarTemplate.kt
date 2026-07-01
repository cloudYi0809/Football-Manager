package com.greendynasty.football.match.template

import com.greendynasty.football.match.model.EventType

/**
 * 球星模板 ID（V0.2 04 §九）
 *
 * 6 类历史球星模板，对应不同位置/风格的核心球员。
 */
enum class StarTemplateId {
    /** 大罗型前锋 */
    RONALDO_R9,
    /** 卡卡型前腰 */
    KAKA,
    /** 小罗型自由人 */
    RONALDINHO,
    /** 皮尔洛型后腰 */
    PIRLO,
    /** 马尔蒂尼型后卫 */
    MALDINI,
    /** 卡恩型门将 */
    KAHN
}

/**
 * 球星模板（V0.2 04 §九）
 *
 * 严格遵循 V0.2 §九铁律：球星模板不直接加进球，
 * 只改变事件权重与少量转化率微调。
 */
data class StarTemplate(
    val id: StarTemplateId,
    /** 事件概率修正：EventType -> 偏移量（正值增加，负值减少） */
    val modifiers: Map<EventType, Double>,
    /** 射门转化率微调（仅小幅） */
    val shotConversionBonus: Double = 0.0,
    /** 伤病风险加成 */
    val injuryRiskBonus: Double = 0.0,
    /** 防守贡献偏移（可负） */
    val defensiveContributionDelta: Double = 0.0
)

/**
 * 球星模板注册表
 *
 * 持有全部 6 类内置模板，EventLayer 通过模板 ID 查询修正项。
 */
class StarTemplateRegistry {

    private val templates: Map<StarTemplateId, StarTemplate> = mapOf(
        StarTemplateId.RONALDO_R9 to StarTemplate(
            id = StarTemplateId.RONALDO_R9,
            // 大罗型：shot_conversion +8%, high_quality_chance_creation_self +6%
            modifiers = mapOf(
                EventType.SHOT to 0.08,
                EventType.DANGEROUS_ATTACK to 0.06
            ),
            shotConversionBonus = 0.08,
            injuryRiskBonus = 0.03,
            defensiveContributionDelta = -0.04
        ),
        StarTemplateId.KAKA to StarTemplate(
            id = StarTemplateId.KAKA,
            // 卡卡型：central_progression +8%, through_ball_event +7%
            modifiers = mapOf(
                EventType.DANGEROUS_ATTACK to 0.08,
                EventType.SHOT to 0.07
            ),
            shotConversionBonus = 0.05
        ),
        StarTemplateId.RONALDINHO to StarTemplate(
            id = StarTemplateId.RONALDINHO,
            // 小罗型：creative_event +10%, key_pass +8%
            modifiers = mapOf(
                EventType.DANGEROUS_ATTACK to 0.10,
                EventType.SHOT to 0.08
            ),
            defensiveContributionDelta = -0.04
        ),
        StarTemplateId.PIRLO to StarTemplate(
            id = StarTemplateId.PIRLO,
            // 皮尔洛型：control_score +7%, long_pass_event +8%
            modifiers = mapOf(
                EventType.POSSESSION to 0.07,
                EventType.NORMAL_ATTACK to 0.08
            )
        ),
        StarTemplateId.MALDINI to StarTemplate(
            id = StarTemplateId.MALDINI,
            // 马尔蒂尼型：opponent_high_quality_chance -8%, defensive_error -6%
            modifiers = mapOf(
                EventType.DANGEROUS_ATTACK to -0.08,
                EventType.CLEARANCE to 0.05
            )
        ),
        StarTemplateId.KAHN to StarTemplate(
            id = StarTemplateId.KAHN,
            // 卡恩型：save_quality +8%, penalty_save +6%
            modifiers = mapOf(
                EventType.SAVE to 0.08,
                EventType.PENALTY to 0.06
            )
        )
    )

    /** 查询模板，未注册时抛 IllegalArgumentException */
    fun get(id: StarTemplateId): StarTemplate =
        templates[id] ?: throw IllegalArgumentException("未注册的球星模板: $id")

    /** 安全查询，未注册返回 null */
    fun find(id: StarTemplateId): StarTemplate? = templates[id]
}

// ==================== 6 类球星模板（V0.2 04 §九 抽象类别） ====================

/**
 * 球星模板触发条件（V0.2 04 §九）
 *
 * 定义模板在比赛中触发所需满足的条件，由 EventLayer 在关键 tick 判定。
 */
data class TriggerConditions(
    /** 触发的最低比赛分钟 */
    val minMinute: Int = 0,
    /** 触发的最高比赛分钟（含补时） */
    val maxMinute: Int = 94,
    /** 是否仅在落后时触发（大心脏类） */
    val onlyWhenTrailing: Boolean = false,
    /** 是否仅在领先时触发（领袖稳节奏类） */
    val onlyWhenLeading: Boolean = false,
    /** 触发概率（0.0-1.0，叠加 config.starTemplateTriggerChance） */
    val triggerChance: Double = 1.0
)

/**
 * 球星模板效果（V0.2 04 §九）
 *
 * 严格遵循铁律：不直接加进球，只改事件权重与少量转化率微调。
 */
data class TemplateEffects(
    /** 事件概率修正：EventType -> 偏移量（正值增加，负值减少） */
    val eventModifiers: Map<EventType, Double> = emptyMap(),
    /** 射门转化率微调（仅小幅，0.0-0.15） */
    val shotConversionBonus: Double = 0.0,
    /** 防守贡献偏移（可负，-0.10-0.10） */
    val defensiveContributionDelta: Double = 0.0,
    /** 伤病风险加成 */
    val injuryRiskBonus: Double = 0.0,
    /** 控球率微调 */
    val possessionDelta: Double = 0.0
)

/**
 * 球星模板类别（V0.2 04 §九 共 6 类）
 *
 * 抽象的球星行为类别，与 [StarTemplateId] 的历史球星模板互补：
 * 历史球星模板描述"像谁"，类别模板描述"在场上发挥什么作用"。
 *
 * 6 类：clutch_player / goal_machine / playmaker / wall / wonderkid / leader
 */
data class StarTemplateCategory(
    /** 模板 ID（clutch_player / goal_machine / playmaker / wall / wonderkid / leader） */
    val id: String,
    /** 模板名称 */
    val name: String,
    /** 触发条件 */
    val triggerConditions: TriggerConditions,
    /** 模板效果 */
    val effects: TemplateEffects
)
