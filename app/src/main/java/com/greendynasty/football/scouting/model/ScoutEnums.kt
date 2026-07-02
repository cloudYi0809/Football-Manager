package com.greendynasty.football.scouting.model

/**
 * T14 球探系统枚举集合（V0.2 `08_青训_球探_历史新星系统.md` §三 + T14 方案 §三.3）。
 *
 * 包含 15 个地理区域、8 种球探任务类型、3 档预算、5 级报告等级、5 项青年赛事、4 类事件。
 * 所有枚举均带 code/displayName，便于持久化为字符串列与 UI 国际化。
 */

/**
 * 15 个球探地理区域（V0.2 08 §三.2 全球球探地区）。
 *
 * 区域代码与 [com.greendynasty.football.scouting.config.ScoutConfig.regionTalentDensity] 一致。
 */
enum class ScoutRegionCode(val code: String, val displayName: String) {
    ENGLAND("ENG", "英格兰"),
    SPAIN("ESP", "西班牙"),
    ITALY("ITA", "意大利"),
    GERMANY("GER", "德国"),
    FRANCE("FRA", "法国"),
    NETHERLANDS("NED", "荷兰"),
    PORTUGAL("POR", "葡萄牙"),
    BRAZIL("BRA", "巴西"),
    ARGENTINA("ARG", "阿根廷"),
    URUGUAY("URU", "乌拉圭"),
    EASTERN_EUROPE("EEU", "东欧"),
    NORDIC("NOR", "北欧"),
    WEST_AFRICA("WAF", "非洲西部"),
    ASIA("ASI", "亚洲"),
    NORTH_AMERICA("NAM", "北美");

    companion object {
        /** 按 code 反查枚举（数据库读取用）。 */
        fun fromCode(code: String): ScoutRegionCode? = values().find { it.code == code }

        /** 全部区域代码列表（供青年赛事默认地区用）。 */
        val allCodes: List<String> = values().map { it.code }
    }
}

/**
 * 8 种球探任务类型（V0.2 08 §三.4 任务派遣 + T14 方案 §三.3）。
 *
 * 每种任务在 [com.greendynasty.football.scouting.config.ScoutConfig.taskTypeParams] 中有
 * 独立的 discoveryBonus / reportUpgradeBonus 参数。
 */
enum class ScoutTaskType(val displayName: String, val description: String) {
    REGION_SEARCH("地区搜索", "在指定地区广撒网，发现概率基础加成 +10%"),
    POSITION_SEARCH("位置搜索", "指定位置（如 ST/CM）筛选，位置匹配高加成"),
    AGE_GROUP_SEARCH("年龄段搜索", "指定年龄段（如 U19）筛选"),
    CLUB_OBSERVATION("俱乐部观察", "指定俱乐部深度观察，报告升级加成 +10%"),
    YOUTH_TOURNAMENT("青年赛事观察", "5 项青年赛事扫描，可触发小妖事件"),
    CONTRACT_OPPORTUNITY("合同机会搜索", "合同剩余少/自由球员筛选"),
    LOWER_LEAGUE("低级别联赛搜索", "低级别联赛淘宝"),
    STAR_TRACKING("明星跟踪", "已成名明星深度报告，升级加成 +20%")
}

/**
 * 3 档预算等级（V0.2 08 §三.4）。
 *
 * - costMultiplier: 任务每日成本倍率（财务系统扣款用）
 * - displayFactor: 发现概率中的预算因子（高预算提升发现概率）
 */
enum class BudgetLevel(val costMultiplier: Double, val displayFactor: Double, val displayName: String) {
    LOW(0.5, 0.6, "低预算"),
    MEDIUM(1.0, 0.8, "中预算"),
    HIGH(1.8, 1.0, "高预算")
}

/**
 * 5 级球探报告等级（V0.2 08 §四 球探报告信息解锁）。
 *
 * 升级阈值见 [com.greendynasty.football.scouting.config.ScoutConfig]：
 * - L1: 初次发现（默认）
 * - L2: 粗略报告（观察 ≥14 天）→ CA/PA 区间 ±12
 * - L3: 标准报告（观察 ≥35 天）→ 较窄 CA/PA ±7 + 性格 + 签约难度
 * - L4: 深度报告（观察 ≥60 天）→ 成长速度 + 隐藏标签 + 适配战术
 * - L5: 完全掌握（观察 ≥90 天 + 球探潜力判断 ≥15）→ 真实 PA + 伤病倾向 + 职业态度
 */
enum class ScoutReportLevel(val level: Int, val displayName: String) {
    INITIAL_DISCOVERY(1, "初次发现"),
    ROUGH_REPORT(2, "粗略报告"),
    STANDARD_REPORT(3, "标准报告"),
    DEEP_REPORT(4, "深度报告"),
    FULL_MASTERY(5, "完全掌握");

    companion object {
        fun fromLevel(level: Int): ScoutReportLevel? = values().find { it.level == level }
    }
}

/**
 * 5 项青年赛事（V0.2 08 §七 青年赛事发现）。
 *
 * 每项赛事在举办月份触发，覆盖特定区域。
 * 举办月份见 [com.greendynasty.football.scouting.config.ScoutConfig.tournamentSchedule]。
 */
enum class YouthTournament(val id: String, val displayName: String, val regionCodes: List<String>) {
    U17_WORLD_CUP("U17WC", "U17 世界杯", ScoutRegionCode.allCodes),
    U20_WORLD_CUP("U20WC", "U20 世界杯", ScoutRegionCode.allCodes),
    UEFA_YOUTH("UEFA_Y", "欧洲青年锦标赛",
        listOf("ENG", "ESP", "ITA", "GER", "FRA", "NED", "POR", "EEU", "NOR")),
    CONMEBOL_YOUTH("CONMEBOL_Y", "南美青年锦标赛", listOf("BRA", "ARG", "URU")),
    YOUTH_UCL("YOUTH_UCL", "青年欧冠",
        listOf("ENG", "ESP", "ITA", "GER", "FRA", "NED", "POR"));

    companion object {
        fun fromId(id: String): YouthTournament? = values().find { it.id == id }
    }
}

/**
 * 4 类青年赛事事件（V0.2 08 §七）。
 *
 * 触发概率见 [com.greendynasty.football.scouting.config.ScoutConfig.youthEventProbabilities]。
 */
enum class ScoutEventType(val code: String, val display: String) {
    YOUTH_HAT_TRICK("YOUTH_HAT_TRICK", "小妖帽子戏法"),
    BIG_CLUB_RUSH("BIG_CLUB_RUSH", "豪门争夺"),
    VALUE_SURGE("VALUE_SURGE", "身价暴涨"),
    SCOUT_STRONG_RECOMMEND("SCOUT_STRONG_RECOMMEND", "球探强烈推荐")
}

/**
 * 球探雇佣状态机（V0.2 08 §三.1）。
 *
 * IDLE ──派遣──→ ON_TASK ──完成/取消──→ IDLE
 * IDLE ──解雇──→ RELEASED
 */
enum class ScoutStatus(val code: String, val display: String) {
    IDLE("IDLE", "空闲"),
    ON_TASK("ON_TASK", "执行任务中"),
    RESTING("RESTING", "休整中"),
    RELEASED("RELEASED", "已解雇")
}

/**
 * 球探任务状态机（V0.2 08 §三.4）。
 *
 * IN_PROGRESS ──到期──→ COMPLETED
 * IN_PROGRESS ──取消──→ CANCELLED
 */
enum class ScoutTaskStatus(val code: String, val display: String) {
    PENDING("PENDING", "待开始"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消")
}
