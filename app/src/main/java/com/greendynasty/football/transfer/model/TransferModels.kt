package com.greendynasty.football.transfer.model

/**
 * T10 转会搜索模块数据模型集合。
 *
 * 包含：
 * - [TransferSearchFilter]：14 项筛选条件
 * - [TransferSearchResult]：搜索结果聚合
 * - [TransferSortBy] / [SortOrder]：排序枚举
 * - [TransferStatus]：可售/可租/自由球员状态
 * - [WatchlistEntry] / [SigningDifficulty]：观察名单
 * - [PlayerRecommendation]：推荐球员
 * - [PlayerCompareData] / [CompareResult]：球员对比
 */

/**
 * 转会市场筛选条件（V0.1 09 §二.1，14 项筛选）。
 *
 * 14 项：姓名 / 年龄范围 / 国籍 / 位置 / CA 范围 / PA 范围 /
 * 最大身价 / 最大工资 / 俱乐部 / 联赛 / 合同剩余年限 / 转会状态 / 排序字段 / 排序方向。
 *
 * 所有条件默认宽松（null/空），通过 [PlayerSearchEngine] 组合过滤。
 */
data class TransferSearchFilter(
    /** 姓名模糊匹配（不区分大小写），null 表示不限 */
    val name: String? = null,

    /** 年龄范围（含两端），null 表示不限 */
    val ageRange: IntRange? = null,

    /** 国籍列表（OR 关系），空表示不限 */
    val nationalities: List<String> = emptyList(),

    /** 主要位置列表（OR 关系，GK/CB/CM/ST...），空表示不限 */
    val positions: List<String> = emptyList(),

    /** 当前能力值 CA 范围（含两端），null 表示不限 */
    val caRange: IntRange? = null,

    /** 潜力值 PA 范围（含两端），null 表示不限 */
    val paRange: IntRange? = null,

    /** 最大身价（含），null 表示不限 */
    val maxMarketValue: Int? = null,

    /** 最大周薪（含），null 表示不限 */
    val maxWage: Int? = null,

    /** 俱乐部 ID 列表（OR 关系），空表示不限 */
    val clubIds: List<Int> = emptyList(),

    /** 联赛 ID 列表（OR 关系），空表示不限 */
    val leagueIds: List<String> = emptyList(),

    /** 合同剩余年限上限（含），null 表示不限 */
    val contractRemainingMax: Int? = null,

    /** 转会状态筛选，null 表示不限 */
    val transferStatus: TransferStatus? = null,

    /** 排序字段，默认按 CA 降序 */
    val sortBy: TransferSortBy = TransferSortBy.CA,

    /** 排序方向，默认降序 */
    val sortOrder: SortOrder = SortOrder.DESC
) {

    /** 是否为默认筛选（不限任何条件） */
    fun isDefault(): Boolean =
        name.isNullOrBlank() &&
            ageRange == null &&
            nationalities.isEmpty() &&
            positions.isEmpty() &&
            caRange == null &&
            paRange == null &&
            maxMarketValue == null &&
            maxWage == null &&
            clubIds.isEmpty() &&
            leagueIds.isEmpty() &&
            contractRemainingMax == null &&
            transferStatus == null

    /**
     * 判断是否为简单筛选（可走 cache.db 索引优化路径）。
     *
     * 简单筛选：不含姓名 / 国籍 / 俱乐部 / 联赛 / 合同年限 / 转会状态 / 工资
     * 仅包含位置 / CA / 年龄 / 身价，可命中 cache 索引。
     */
    fun isSimple(): Boolean =
        name.isNullOrBlank() &&
            nationalities.isEmpty() &&
            clubIds.isEmpty() &&
            leagueIds.isEmpty() &&
            contractRemainingMax == null &&
            transferStatus == null &&
            maxWage == null &&
            paRange == null

    companion object {
        /** 默认筛选（不限） */
        val DEFAULT = TransferSearchFilter()
    }
}

/**
 * 转会状态枚举（V0.1 09 §二.1）。
 */
enum class TransferStatus(val label: String) {
    /** 可出售 */
    TRANSFERABLE("可售"),

    /** 可租借 */
    LOANABLE("可租"),

    /** 自由球员 */
    FREE_AGENT("自由");

    companion object {
        /** 全部状态 */
        val ALL: List<TransferStatus> = values().toList()
    }
}

/**
 * 搜索排序字段（V0.1 09 §二.1，5 种排序）。
 */
enum class TransferSortBy(val label: String) {
    CA("当前能力"),
    PA("潜力"),
    AGE("年龄"),
    MARKET_VALUE("身价"),
    WAGE("工资")
}

/**
 * 排序方向。
 */
enum class SortOrder {
    ASC,
    DESC
}

/**
 * 搜索结果聚合数据。
 *
 * 由 [com.greendynasty.football.transfer.search.PlayerSearchEngine] 多表聚合而来，
 * 包含球员基础信息 + 存档状态 + 经济估值 + 转会状态 + 球探报告等级 + 观察名单标记。
 */
data class TransferSearchResult(
    val playerId: Int,
    val playerName: String,
    val age: Int,
    val nationality: String,
    val position: String,
    val secondaryPositions: List<String>,
    val currentCa: Int,
    val potentialPa: Int,
    val clubId: Int?,
    val clubName: String?,
    val leagueId: String?,
    val marketValue: Int,
    val wage: Int,
    val contractUntil: String?,
    val transferStatus: TransferStatus,
    val scoutingReportLevel: Int,
    val isOnWatchlist: Boolean,
    val preferredFoot: String?,
    val portraitPath: String?
)

/**
 * 观察名单条目（V0.1 09 §二.2）。
 *
 * 显示信息：基本信息 / 报告等级 / 预计身价 / 签约难度 / 竞争球队 / 球探建议。
 *
 * 注意：持久化由 T14 球探任务统一接入 save.db，T10 阶段使用内存缓存。
 */
data class WatchlistEntry(
    val entryId: Int,
    val saveId: Int,
    val playerId: Int,
    val addedDate: String,
    val reportLevel: Int,
    val estimatedValue: Int,
    val signingDifficulty: SigningDifficulty,
    val competitorClubs: List<Int>,
    val scoutRecommendation: String?
)

/**
 * 签约难度枚举（V0.1 09 §二.2）。
 */
enum class SigningDifficulty(val label: String, val color: Long) {
    EASY("容易", 0xFF2E7D32),
    NORMAL("普通", 0xFF1565C0),
    HARD("困难", 0xFFEF6C00),
    VERY_HARD("极难", 0xFFC62828);

    companion object {
        /** 由身价推算签约难度（V0.2 经济模型简化阈值） */
        fun fromMarketValue(marketValue: Int, isFreeAgent: Boolean): SigningDifficulty = when {
            isFreeAgent -> EASY
            marketValue > 50_000_000 -> VERY_HARD
            marketValue > 20_000_000 -> HARD
            marketValue > 5_000_000 -> NORMAL
            else -> EASY
        }
    }
}

/**
 * 推荐球员（V0.2 §四，按需求匹配度排序）。
 *
 * 推荐度 = 位置匹配 + 年龄 + 能力 + 潜力 + 身价合理性 + 战术匹配 + 薄弱位置加成。
 *
 * @property result 搜索结果
 * @property matchScore 综合匹配度 0-100
 * @property weakPositionBonus 薄弱位置加成（0 或 [TransferConfig.recommend.weakPositionBonus]）
 * @property styleMatchScore 战术风格匹配分 0-100
 * @property reasons 推荐理由（用于 UI 展示）
 */
data class PlayerRecommendation(
    val result: TransferSearchResult,
    val matchScore: Double,
    val weakPositionBonus: Double,
    val styleMatchScore: Double,
    val reasons: List<String>
)

/**
 * 单个球员对比数据（V0.1 03 阵容页：球员对比）。
 */
data class PlayerCompareData(
    val playerId: Int,
    val playerName: String,
    val age: Int,
    val position: String,
    val currentCa: Int,
    val potentialPa: Int,
    val marketValue: Int,
    val wage: Int,
    val attributes: Map<String, Int>,
    val contractUntil: String?
)

/**
 * 球员对比结果（2-3 名球员）。
 *
 * @property players 对比球员列表
 * @property bestInCategory 每个属性的最佳 playerId（属性名 -> playerId）
 * @property radarValues 各球员的 6 维雷达值（进攻/中场/防守/身体/心理/门将）
 */
data class CompareResult(
    val players: List<PlayerCompareData>,
    val bestInCategory: Map<String, Int>,
    val radarValues: Map<Int, List<RadarDimension>>
)

/** 雷达图单维度值 */
data class RadarDimension(val label: String, val value: Int)
