package com.greendynasty.football.youth.model

/**
 * T16 青训风格枚举（V0.1 08 §二.2 + T16 方案 §三.2）
 *
 * 8 种风格决定青训球员产出的位置分布权重，体现俱乐部差异化打法。
 * 切换风格有 3 个月冷却期，避免玩家反复刷位置。
 *
 * 任务要求 8 种风格：技术流 / 力量型 / 速度型 / 防守型 / 全能型 / 边路型 / 中场组织型 + 自由发挥。
 *
 * @param displayName 中文显示名
 * @param description 风格说明
 * @param positionWeights 位置产出权重（位置代码 → 权重，权重越大产出概率越高）
 */
enum class AcademyStyle(
    val displayName: String,
    val description: String,
    val positionWeights: Map<String, Double>
) {
    /** 技术流：前腰、中场、边锋 */
    TECHNICAL(
        "技术流",
        "前腰、中场、边锋为主，强调传控与脚下技术",
        mapOf("AM" to 1.6, "CM" to 1.4, "LW" to 1.3, "RW" to 1.3, "DM" to 0.8, "CB" to 0.6)
    ),

    /** 力量型：中锋、中卫、B2B 中场 */
    POWER(
        "力量型",
        "中锋、中卫、B2B 中场为主，强调身体对抗",
        mapOf("ST" to 1.5, "CB" to 1.4, "CM" to 1.3, "DM" to 1.1, "LW" to 0.7, "RW" to 0.7)
    ),

    /** 速度型：边锋、前锋、边卫 */
    SPEED(
        "速度型",
        "边锋、前锋、边卫为主，强调冲刺与反击",
        mapOf("LW" to 1.6, "RW" to 1.6, "ST" to 1.3, "LB" to 1.1, "RB" to 1.1, "AM" to 0.9)
    ),

    /** 防守型：中卫、门将、后腰 */
    DEFENSIVE(
        "防守型",
        "中卫、门将、后腰为主，强调防守体系",
        mapOf("CB" to 1.6, "GK" to 1.4, "DM" to 1.4, "CM" to 0.9, "ST" to 0.7, "LW" to 0.6)
    ),

    /** 全能型：位置分布均匀 */
    ALL_ROUND(
        "全能型",
        "位置分布均匀，培养多面手",
        mapOf(
            "CM" to 1.2, "CB" to 1.2, "ST" to 1.2, "LW" to 1.2,
            "RW" to 1.2, "DM" to 1.1, "AM" to 1.0, "GK" to 0.9
        )
    ),

    /** 边路型：边锋、边卫 */
    WING(
        "边路型",
        "边锋、边卫为主，强调边路突破与传中",
        mapOf("LW" to 1.5, "RW" to 1.5, "LB" to 1.3, "RB" to 1.3, "AM" to 1.0, "ST" to 0.9)
    ),

    /** 中场组织型：前腰、中场、后腰 */
    MIDFIELD_ORGANIZE(
        "中场组织型",
        "前腰、中场、后腰为主，强调传控组织",
        mapOf("AM" to 1.5, "CM" to 1.5, "DM" to 1.3, "LW" to 0.9, "RW" to 0.9, "CB" to 0.8)
    ),

    /** 自由发挥：完全随机，无固定偏好 */
    FREE_STYLE(
        "自由发挥",
        "不设固定方向，让天赋自由成长",
        mapOf(
            "ST" to 1.0, "LW" to 1.0, "RW" to 1.0, "AM" to 1.0,
            "CM" to 1.0, "DM" to 1.0, "CB" to 1.0, "GK" to 0.8
        )
    );

    companion object {
        /** 由名称安全解析，未匹配返回 null。 */
        fun fromName(name: String?): AcademyStyle? =
            name?.let { entries.firstOrNull { it.name.equals(it.name, ignoreCase = true) } }

        /** 由名称安全解析，未匹配返回默认 [TECHNICAL]。 */
        fun fromNameOrDefault(name: String?): AcademyStyle =
            fromName(name) ?: TECHNICAL
    }
}
