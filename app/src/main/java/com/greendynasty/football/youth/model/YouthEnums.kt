package com.greendynasty.football.youth.model

/**
 * T16 青训学院枚举集合（V0.1 08 §二 + T16 方案 §三）
 *
 * 包含：青训梯队 / 球员状态 / 招募范围 / 训练方向 / 事件类型 / 事件状态 / 投资字段。
 * 所有枚举在 Room Entity 中以字符串形式持久化，与现有 [com.greendynasty.football.prospect.model.ProspectStatus]
 * 风格保持一致，避免引入 TypeConverter。
 */

/**
 * 青训梯队分档。
 * - U18：18 岁以下（14-17 岁）
 * - U21：18-21 岁
 */
enum class YouthTier(val displayName: String) {
    U18("U18 梯队"),
    U21("U21 梯队");

    companion object {
        fun fromName(name: String?): YouthTier? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        fun fromNameOrDefault(name: String?): YouthTier = fromName(name) ?: U18
    }
}

/**
 * 青训球员状态机（6 种）。
 *
 * 状态流转：
 * ```
 * YOUTH_CONTRACT ──17 岁+──→ PROFESSIONAL_CONTRACT ──提拔──→ FIRST_TEAM
 *      │                              │
 *      ├── U18 梯队 ──18 岁──→ U21 梯队
 *      │
 *      └── 18 岁+ 外租 ──→ LOANED_OUT
 *      │
 *      └── 放弃 ──→ LEAVING
 * ```
 */
enum class YouthPlayerStatus(val displayName: String) {
    /** 青年合同（14-17 岁自动签）。 */
    YOUTH_CONTRACT("青年合同"),

    /** 职业合同（17 岁+ 主动签）。 */
    PROFESSIONAL_CONTRACT("职业合同"),

    /** 在 U18 梯队训练中。 */
    U18("U18 梯队"),

    /** 在 U21 梯队训练中。 */
    U21("U21 梯队"),

    /** 已提拔至一线队（保留青训身份标记用于本队青训成就）。 */
    FIRST_TEAM("已提拔一线队"),

    /** 外租培养中。 */
    LOANED_OUT("外租培养"),

    /** 即将离队 / 已放弃。 */
    LEAVING("离队");

    companion object {
        fun fromName(name: String?): YouthPlayerStatus? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        fun fromNameOrDefault(name: String?): YouthPlayerStatus =
            fromName(name) ?: YOUTH_CONTRACT
    }
}

/**
 * 招募范围（影响国籍分布与产出质量因子）。
 */
enum class RecruitmentRange(val displayName: String, val qualityScore: Double) {
    /** 本地（默认）。 */
    LOCAL("本地", 25.0),

    /** 区域。 */
    REGIONAL("区域", 50.0),

    /** 全国。 */
    NATIONAL("全国", 75.0),

    /** 国际（含海外）。 */
    INTERNATIONAL("国际", 100.0);

    companion object {
        fun fromName(name: String?): RecruitmentRange? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        fun fromNameOrDefault(name: String?): RecruitmentRange =
            fromName(name) ?: LOCAL
    }
}

/**
 * 青训训练方向（5 种专项）。
 *
 * 复用 T09 [com.greendynasty.football.growth.model.TrainingFocus] 概念，
 * 青训球员的属性增长侧重。
 */
enum class YouthTrainingFocus(val displayName: String) {
    SHOOTING("射门专项"),
    PASSING("传球专项"),
    FITNESS("体能专项"),
    DEFENDING("防守专项"),
    BALANCED("均衡训练");

    companion object {
        fun fromName(name: String?): YouthTrainingFocus? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        fun fromNameOrDefault(name: String?): YouthTrainingFocus =
            fromName(name) ?: BALANCED
    }
}

/**
 * 青训事件类型（6 种）。
 */
enum class YouthEventType(val displayName: String) {
    /** 青训黄金一代。 */
    GOLDEN_GENERATION("黄金一代"),

    /** 青训球员被豪门挖角。 */
    POACHED_BY_GIANT("豪门挖角"),

    /** 青训教练强烈推荐。 */
    COACH_RECOMMENDATION("教练推荐"),

    /** 训练态度下降。 */
    ATTITUDE_DROP("态度下降"),

    /** 国家青年队入选。 */
    YOUTH_NATIONAL_TEAM("国青入选"),

    /** 要求职业合同。 */
    PRO_CONTRACT_REQUEST("要求职业合同");

    companion object {
        fun fromName(name: String?): YouthEventType? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    }
}

/**
 * 青训事件状态。
 */
enum class YouthEventStatus {
    PENDING,
    RESOLVED_ACCEPTED,
    RESOLVED_REJECTED,
    EXPIRED;

    companion object {
        fun fromName(name: String?): YouthEventStatus? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        fun fromNameOrDefault(name: String?): YouthEventStatus =
            fromName(name) ?: PENDING
    }
}

/**
 * 青训投资升级字段（5 项）。
 */
enum class InvestmentField(val displayName: String) {
    YOUTH_LEVEL("青训等级"),
    TRAINING_FACILITY("训练设施"),
    RECRUITMENT_RANGE("招募范围"),
    U18_COACH("U18 教练"),
    U21_COACH("U21 教练");

    companion object {
        fun fromName(name: String?): InvestmentField? =
            name?.let { entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    }
}
