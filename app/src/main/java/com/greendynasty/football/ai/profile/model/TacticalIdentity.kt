package com.greendynasty.football.ai.profile.model

/**
 * T18 战术风格倾向枚举（V0.2 05 §四.3 战术重要性矩阵）。
 *
 * 每家俱乐部有偏好的战术风格，影响：
 * - 阵容短板识别时的 tactical_importance（[com.greendynasty.football.transfer.ai.config.BasicAiConfig]）
 * - 转会目标评分的 tactical_fit 因子
 * - 球员属性筛选（如高位压迫需要耐力/工作率）
 *
 * 铁律：8 种战术风格覆盖现代足球主流体系，每种风格对应不同的关键位置权重。
 *
 * @property label 中文标签
 * @property description 风格描述（用于 UI 展示）
 * @property keyPositions 该风格的关键位置列表（这些位置的战术重要性高）
 * @property preferredPersonalities 倾向搭配的俱乐部性格（用于 generator 自动分配）
 */
enum class TacticalIdentity(
    val label: String,
    val description: String,
    val keyPositions: List<String>,
    val preferredPersonalities: List<ClubPersonality>
) {
    /** 高位压迫（Gegenpressing，如利物浦 / 多特蒙德） */
    HIGH_PRESS(
        label = "高位压迫",
        description = "前场高位逼抢，断球后快速反击，需要耐力与工作率",
        keyPositions = listOf("DM", "CM", "ST", "LW", "RW"),
        preferredPersonalities = listOf(ClubPersonality.AGGRESSIVE, ClubPersonality.PRAGMATIC)
    ),

    /** 控球渗透（Tiki-taka，如巴萨 / 曼城） */
    POSSESSION(
        label = "控球渗透",
        description = "传控渗透，短传配合，需要传球/技术/视野",
        keyPositions = listOf("CM", "AM", "DM", "ST"),
        preferredPersonalities = listOf(ClubPersonality.IDEALIST, ClubPersonality.AGGRESSIVE)
    ),

    /** 防守反击（Counter-attack，如马竞 / 皇马） */
    COUNTER_ATTACK(
        label = "防守反击",
        description = "低位防守，断球后快速反击，需要速度/强壮/门将",
        keyPositions = listOf("CB", "LB", "RB", "ST", "LW", "RW", "GK"),
        preferredPersonalities = listOf(ClubPersonality.CONSERVATIVE, ClubPersonality.PRAGMATIC)
    ),

    /** 边路传中（Wing play，如传统英式打法） */
    WING_CROSS(
        label = "边路传中",
        description = "边路突破传中，禁区内抢点，需要边锋速度与中锋头球",
        keyPositions = listOf("LW", "RW", "ST", "LB", "RB"),
        preferredPersonalities = listOf(ClubPersonality.CONSERVATIVE, ClubPersonality.PRAGMATIC)
    ),

    /** 防守稳固（Catenaccio，如意大利传统） */
    DEEP_DEFENSE(
        label = "防守稳固",
        description = "低位密集防守，重视定位球，需要中卫/后腰硬度和门将扑救",
        keyPositions = listOf("CB", "DM", "GK", "LB", "RB"),
        preferredPersonalities = listOf(ClubPersonality.CONSERVATIVE)
    ),

    /** 全场紧逼（全场 Man-marking，如莱比锡） */
    FULL_PRESS(
        label = "全场紧逼",
        description = "全场人盯人紧逼，体能消耗大但压迫力强",
        keyPositions = listOf("CM", "DM", "AM", "LB", "RB"),
        preferredPersonalities = listOf(ClubPersonality.AGGRESSIVE)
    ),

    /** 自由流动（Total football，如荷兰传统 / 拜仁） */
    TOTAL_FOOTBALL(
        label = "自由流动",
        description = "全攻全守，球员位置互换，需要技术全面的多面手",
        keyPositions = listOf("CM", "AM", "DM", "LW", "RW", "ST"),
        preferredPersonalities = listOf(ClubPersonality.IDEALIST, ClubPersonality.AGGRESSIVE)
    ),

    /** 长传冲吊（Direct play，如传统英式保级队） */
    DIRECT_PLAY(
        label = "长传冲吊",
        description = "直接长传找中锋，规避中场组织，适合保级队",
        keyPositions = listOf("ST", "CB", "GK", "CM"),
        preferredPersonalities = listOf(ClubPersonality.PRAGMATIC, ClubPersonality.CONSERVATIVE)
    );

    companion object {
        /** 由字符串安全解析（DB 字段 → 枚举），未匹配返回 null。 */
        fun fromString(value: String?): TacticalIdentity? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
