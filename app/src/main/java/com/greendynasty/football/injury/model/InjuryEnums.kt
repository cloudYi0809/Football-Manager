package com.greendynasty.football.injury.model

/**
 * 伤病严重度（T08 方案 §三.1）
 *
 * 对应 V0.1 07 §六 与 V0.2 08 §十 的 4 档伤病类型：
 * - [MINOR]：轻伤 1-7 天
 * - [MODERATE]：中等伤 1-4 周
 * - [MAJOR]：重伤 1-6 个月（含 ACL/骨折/半月板等 4 类重伤）
 * - [CAREER_THREATENING]：职业威胁伤 6 月+（跟腱断裂 / 多次重伤复发）
 *
 * [code] 为存入 save_injury.severity 的 Int 编码（1-4），与 V0.1 schema 兼容。
 */
enum class InjurySeverity(val code: Int, val displayName: String) {
    MINOR(1, "轻伤"),
    MODERATE(2, "中等伤"),
    MAJOR(3, "重伤"),
    CAREER_THREATENING(4, "职业威胁伤");

    companion object {
        fun fromCode(code: Int): InjurySeverity =
            values().firstOrNull { it.code == code } ?: MINOR

        /** 从字符串严重度（V0.2 文档使用 MINOR/MODERATE/MAJOR/CAREER_THREATENING）解析 */
        fun fromName(name: String): InjurySeverity =
            values().firstOrNull { it.name == name } ?: MINOR
    }
}

/**
 * 伤病来源（T08 方案 §三.1）
 */
enum class InjurySource(val displayName: String) {
    MATCH_CONTACT("比赛接触"),
    MATCH_NON_CONTACT("比赛非接触"),
    TRAINING("训练"),
    FATIGUE("疲劳"),
    RECURRENCE("复发");

    companion object {
        fun fromName(name: String): InjurySource =
            values().firstOrNull { it.name == name } ?: MATCH_CONTACT
    }
}

/**
 * 伤病状态（T08 方案 §三.1）
 */
enum class InjuryStatus {
    active,           // 活跃伤病（未开始恢复或恢复中）
    recovering,       // 恢复中
    returned_early,   // 强行复出
    recovered,        // 已恢复
    recurred,         // 复发（原伤病重新激活）
    archived          // 归档
}

/**
 * 治疗方案（T08 方案 §八 treatment）
 *
 * 不同方案对恢复速度 / 复发风险 / 永久影响有不同修正：
 * - [CONSERVATIVE]：保守治疗，速度 0.9x，复发 -10%，免费
 * - [STANDARD]：标准治疗，速度 1.0x，无修正，免费
 * - [SURGERY]：手术，速度 0.7x，复发 -30% / 永久 -20%，200k
 * - [EXTERNAL_EXPERT]：外部专家，速度 1.3x，复发 -15% / 永久 +5%，500k（仅重伤可用）
 */
enum class TreatmentType(val displayName: String) {
    CONSERVATIVE("保守治疗"),
    STANDARD("标准治疗"),
    SURGERY("手术"),
    EXTERNAL_EXPERT("外部专家");

    companion object {
        fun fromName(name: String): TreatmentType =
            values().firstOrNull { it.name == name } ?: STANDARD
    }
}

/**
 * 风险等级（医疗中心"疲劳风险"模块分级）
 */
enum class RiskLevel(val displayName: String) {
    NONE("无风险"),
    LOW("低风险"),
    MEDIUM("中风险"),
    HIGH("高风险"),
    CRITICAL("极高风险")
}

/**
 * 比赛事件类型（用于比赛伤病概率的事件强度修正）
 */
enum class MatchEventType {
    NORMAL,          // 常规事件
    TACKLE_HARD,     // 凶狠铲断
    HEADER_DUEL,     // 头球争顶
    COLLISION,       // 冲撞
    FATIGUE_LATE     // 比赛末段疲劳
}
