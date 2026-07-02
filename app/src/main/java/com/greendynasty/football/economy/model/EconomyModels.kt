package com.greendynasty.football.economy.model

import java.time.LocalDate

/**
 * T17 经济上下文（V0.2 §一 经济模型流水线入口）。
 *
 * 携带当前存档 / 日期 / 赛季信息，供经济服务在计算身价/工资/财力时统一获取"当前年份"。
 *
 * @param saveId 存档 ID
 * @param currentDate 当前游戏日期
 * @param currentYear 当前年份（用于经济指数查询）
 * @param currentSeasonId 当前赛季 ID
 */
data class EconomyContext(
    val saveId: Int,
    val currentDate: LocalDate,
    val currentYear: Int,
    val currentSeasonId: Int
)

/**
 * V0.2 §二 年度经济指数快照。
 *
 * 来源：save.db 的 economy_index 表（T00 已定义 [com.greendynasty.football.data.save.entity.EconomyIndexEntity]）。
 *
 * - globalIndex：全球经济指数基准（2002 = 1.00）
 * - transferFeeIndex：转会费通胀指数
 * - wageIndex：工资通胀指数
 * - commercialIndex：商业收入指数
 *
 * T17 默认使用 [globalIndex] 作为身价/工资计算的"时代系数"，
 * transferFeeIndex / wageIndex / commercialIndex 用于细分场景（T18+ 可调用）。
 *
 * @property year 年份
 * @property globalIndex 全球经济指数基准
 * @property transferFeeIndex 转会费通胀指数
 * @property wageIndex 工资通胀指数
 * @property commercialIndex 商业收入指数
 * @property source 数据来源（"db" = 来自存档表 / "fixed" = 来自固定表回退 / "projected" = 2030+ 架空增长）
 */
data class EconomyIndexSnapshot(
    val year: Int,
    val globalIndex: Double,
    val transferFeeIndex: Double,
    val wageIndex: Double,
    val commercialIndex: Double,
    val source: String = "db"
)

/**
 * V0.2 §三 联赛商业系数快照。
 *
 * 来源：save.db 的 league_economy_profile 表（T00 已定义 [com.greendynasty.football.data.save.entity.LeagueEconomyProfileEntity]）。
 *
 * 联赛系数 = baseMultiplier × growthCurve(year) × (economyIndex / economyIndex(2002))
 *
 * @property leagueId 联赛标识（如 "EPL"）
 * @property leagueName 联赛名称
 * @property baseMultiplier 基础系数（2002 基准）
 * @property growthRate 年增长率
 * @property growthType 增长曲线类型（用于 V0.2 §三 差异化曲线）
 * @property multiplier 当前年份的最终商业系数
 * @property source 数据来源（"db" / "fixed"）
 */
data class LeagueEconomySnapshot(
    val leagueId: String,
    val leagueName: String,
    val baseMultiplier: Double,
    val growthRate: Double,
    val growthType: String,
    val multiplier: Double,
    val source: String = "db"
)

/**
 * V0.2 §四 俱乐部财政状态（财力计算输入）。
 *
 * @property clubId 俱乐部 ID
 * @property clubReputation 俱乐部声望 0-100
 * @property leagueId 所在联赛标识
 * @property leagueEconomyMultiplier 联赛商业系数（来自 LeagueEconomyService）
 * @property stadiumIncome 年门票收入
 * @property commercialIncome 年赞助收入
 * @property ownerInvestment 年老板投入
 * @property recentSuccess 近 3 年战绩 0-1
 * @property financialPowerScore 计算得出的财力 0-100
 * @property transferBudget 转会预算
 * @property wageBudget 工资预算（周薪上限）
 * @property balance 当前余额
 * @property totalWage 年工资总支出
 * @property totalIncome 年总收入
 * @property wageToIncomeRatio 工资/收入比
 */
data class ClubFinancialState(
    val clubId: Int,
    val clubReputation: Int,
    val leagueId: String,
    val leagueEconomyMultiplier: Double,
    val stadiumIncome: Int,
    val commercialIncome: Int,
    val ownerInvestment: Int,
    val recentSuccess: Double,
    val financialPowerScore: Int,
    val transferBudget: Int,
    val wageBudget: Int,
    val balance: Int,
    val totalWage: Int,
    val totalIncome: Int,
    val wageToIncomeRatio: Double
)

/**
 * V0.2 §五-§八 球员身价估值结果。
 *
 * @property playerId 球员 ID
 * @property baseValue 基础身价（6 因子乘积，未含表现修正）
 * @property currentValue 当前身价（含表现修正）
 * @property expectedWage 期望周薪
 * @property breakdown 估值明细（每个因子系数）
 */
data class PlayerValuation(
    val playerId: Int,
    val baseValue: Int,
    val currentValue: Int,
    val expectedWage: Int,
    val breakdown: ValuationBreakdown
)

/**
 * V0.2 §五-§八 身价 6 因子分解（用于 UI 展示与单测）。
 *
 * 6 因子：
 * 1. abilityValue（CA 价值曲线，含 base_amount × pow(1.075, CA-50)）
 * 2. ageMultiplier（年龄系数）
 * 3. contractMultiplier（合同剩余影响）
 * 4. leagueVisibilityMultiplier（联赛可见度 = 联赛商业系数）
 * 5. economyIndex（时代系数）
 * 6. compositeModifier（位置 × 声望 × 潜力 复合修正）
 *
 * 表现修正（performanceMultiplier）单独作为 currentValue 的调整项，不计入 6 因子。
 *
 * @property abilityValue CA 价值曲线（base_amount × pow(growthRate, CA-50)）
 * @property ageMultiplier 年龄系数
 * @property contractMultiplier 合同剩余影响
 * @property leagueVisibilityMultiplier 联赛可见度（联赛商业系数）
 * @property economyIndex 时代系数
 * @property positionMultiplier 位置系数
 * @property reputationMultiplier 声望系数
 * @property potentialMultiplier 潜力乘数
 * @property performanceMultiplier 表现修正（currentValue 调整项）
 */
data class ValuationBreakdown(
    val abilityValue: Double,
    val ageMultiplier: Double,
    val contractMultiplier: Double,
    val leagueVisibilityMultiplier: Double,
    val economyIndex: Double,
    val positionMultiplier: Double,
    val reputationMultiplier: Double,
    val potentialMultiplier: Double,
    val performanceMultiplier: Double
)

/**
 * V0.2 §九 工资计算明细（6 因子分解）。
 *
 * 与 T12 [com.greendynasty.football.transfer.contract.wage.WageBreakdown] 对齐，
 * T17 在其基础上增加 leagueEconomyMultiplier（联赛商业系数）与 economyIndex 的拆分。
 *
 * @property expectedWage 期望周薪
 * @property wageBase CA 工资基础（来自 T12 WageCalculator.wageBaseByCa）
 * @property clubReputationFactor 俱乐部声望系数
 * @property leagueEconomyMultiplier 联赛商业系数
 * @property economyFactor 经济指数系数（T17 提供）
 * @property agentGreedMultiplier 经纪人贪婪系数
 * @property squadRoleFactor 队内角色系数
 */
data class WageBreakdown(
    val expectedWage: Int,
    val wageBase: Double,
    val clubReputationFactor: Double,
    val leagueEconomyMultiplier: Double,
    val economyFactor: Double,
    val agentGreedMultiplier: Double,
    val squadRoleFactor: Double
)

/**
 * V0.2 §十 财政健康等级。
 */
enum class FinancialWarningLevel {
    /** < 55%：健康 */
    HEALTHY,
    /** 55%-70%：可接受 */
    ACCEPTABLE,
    /** 70%-85%：风险 */
    RISK,
    /** > 85%：高危 */
    HIGH_RISK;

    /** 中文展示名 */
    val displayName: String
        get() = when (this) {
            HEALTHY -> "健康"
            ACCEPTABLE -> "可接受"
            RISK -> "风险"
            HIGH_RISK -> "高危"
        }
}

/**
 * V0.2 §十 财政健康报告。
 *
 * @property clubId 俱乐部 ID
 * @property wageToIncomeRatio 工资/收入比
 * @property level 预警等级
 * @property recommendations 建议措施列表
 */
data class FinancialHealthReport(
    val clubId: Int,
    val wageToIncomeRatio: Double,
    val level: FinancialWarningLevel,
    val recommendations: List<String>
)
