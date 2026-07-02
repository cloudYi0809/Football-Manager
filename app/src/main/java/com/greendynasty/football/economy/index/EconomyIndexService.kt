package com.greendynasty.football.economy.index

import com.greendynasty.football.data.save.dao.EconomyIndexDao
import com.greendynasty.football.data.save.entity.EconomyIndexEntity
import com.greendynasty.football.economy.config.EconomyConfig
import com.greendynasty.football.economy.model.EconomyIndexSnapshot
import java.time.LocalDate

/**
 * T17 时代通胀系数服务（V0.2 §二）。
 *
 * 职责：
 * 1. 从 save.db 的 economy_index 表读取年度通胀指数（global_index / transfer_fee_index / wage_index / commercial_index）
 * 2. 当存档表无对应年份时，回退到 [EconomyIndexTable] 固定表（1992-2030）+ [FutureGrowthProjector]（2030+）
 * 3. 管理当前经济状态：每年推进时更新当前经济指数，影响所有身价/工资计算
 *
 * 与 T10 [com.greendynasty.football.transfer.search.EconomyEstimator.economyIndex] 的关系：
 * - T10 使用硬编码分段表，仅返回 globalIndex
 * - T17 优先读存档表（支持数据包覆盖），返回完整 4 字段快照
 * - T17 作为权威实现（任务约束："T17 作为权威实现"）
 *
 * 协程规范：所有 DB 查询使用 suspend，避免阻塞主线程。
 *
 * @property dao 经济指数 DAO（save.db）
 * @property table 固定表回退
 * @property futureProjector 2030+ 架空增长投影器
 * @property config 经济配置
 */
class EconomyIndexService(
    private val dao: EconomyIndexDao,
    private val table: EconomyIndexTable = EconomyIndexTable(),
    private val futureProjector: FutureGrowthProjector = FutureGrowthProjector(),
    private val config: EconomyConfig = EconomyConfig.DEFAULT
) {

    /**
     * 获取指定年份的经济指数快照。
     *
     * 优先级：
     * 1. save.db economy_index 表（数据包覆盖）
     * 2. 固定表（1992-2030，线性插值）
     * 3. 架空增长（2030+，年增 3%）
     *
     * @param year 年份
     * @return 经济指数快照
     */
    suspend fun getSnapshot(year: Int): EconomyIndexSnapshot {
        // 1. 优先读存档表
        val entity = runCatching { dao.get(year) }.getOrNull()
        if (entity != null) {
            return entity.toSnapshot(source = "db")
        }

        // 2. 回退到固定表 / 架空增长
        val globalIndex = getIndex(year)
        // 推导细分指数（V0.2 §二：四指数同向变化，细分指数按比例缩放）
        val ratio = globalIndex / table.getBaseYearIndex().coerceAtLeast(0.01)
        return EconomyIndexSnapshot(
            year = year,
            globalIndex = globalIndex,
            transferFeeIndex = round(globalIndex * ratio * TRANSFER_FEE_RATIO),
            wageIndex = round(globalIndex * ratio * WAGE_RATIO),
            commercialIndex = round(globalIndex * ratio * COMMERCIAL_RATIO),
            source = if (year > 2030) "projected" else "fixed"
        )
    }

    /**
     * 获取指定年份的全球经济指数（V0.2 §二 时代系数，2002 基准 = 1.00）。
     *
     * 这是身价/工资公式中的"economy_index"因子。
     *
     * @param year 年份
     * @return 全球经济指数
     */
    suspend fun getIndex(year: Int): Double {
        // 1. 优先读存档表
        val entity = runCatching { dao.get(year) }.getOrNull()
        if (entity != null && entity.globalIndex > 0.0) {
            return entity.globalIndex
        }
        // 2. 回退到固定表 / 架空增长
        return getFixedOrProjectedIndex(year)
    }

    /**
     * 同步版本（不读 DB）：仅使用固定表 + 架空增长，用于无存档上下文的场景（如 UI 预览、单测）。
     */
    fun getIndexSync(year: Int): Double = getFixedOrProjectedIndex(year)

    /**
     * 获取指定日期的经济指数。
     */
    suspend fun getIndexForDate(date: LocalDate): Double = getIndex(date.year)

    /**
     * 同步版本：获取指定日期的经济指数（不读 DB）。
     */
    fun getIndexForDateSync(date: LocalDate): Double = getFixedOrProjectedIndex(date.year)

    /**
     * 获取基准年份（2002）的指数，用于联赛系数归一化。
     */
    suspend fun getBaseYearIndex(): Double = getIndex(config.economyIndex.baseYear)

    /**
     * 获取所有已存档的经济指数（按年份升序）。
     *
     * 用于 UI 通胀趋势图展示。
     *
     * @return 经济指数快照列表
     */
    suspend fun getAllSnapshots(): List<EconomyIndexSnapshot> {
        val entities = runCatching { dao.getAll() }.getOrDefault(emptyList())
        return entities.map { it.toSnapshot("db") }
    }

    /**
     * 生成 1992 至 [toYear] 的完整通胀趋势（DB 数据 + 固定表回退）。
     *
     * 用于 UI 通胀趋势图：确保即使 DB 缺失某些年份也能画出连续曲线。
     *
     * @param toYear 截止年份（含）
     * @return 完整趋势列表（按年份升序）
     */
    suspend fun getTrend(toYear: Int): List<EconomyIndexSnapshot> {
        val dbEntities = runCatching { dao.getAll() }.getOrDefault(emptyList())
        val dbByYear = dbEntities.associateBy { it.year }
        val fromYear = config.economyIndex.fixedTable.keys.minOrNull() ?: 1992
        return (fromYear..toYear).map { year ->
            dbByYear[year]?.toSnapshot("db") ?: fallbackSnapshot(year)
        }
    }

    /**
     * 年度推进：确保指定年份的经济指数已写入存档表。
     *
     * 若 DB 中无该年份记录，则用固定表 / 架空增长值写入，避免后续查询回退。
     * 供 T07 赛季末经济结算调用。
     *
     * @param year 目标年份
     */
    suspend fun ensureYearIndex(year: Int) {
        val existing = runCatching { dao.get(year) }.getOrNull()
        if (existing != null) return

        val snapshot = fallbackSnapshot(year)
        runCatching {
            dao.insert(
                EconomyIndexEntity(
                    year = snapshot.year,
                    globalIndex = snapshot.globalIndex,
                    transferFeeIndex = snapshot.transferFeeIndex,
                    wageIndex = snapshot.wageIndex,
                    commercialIndex = snapshot.commercialIndex
                )
            )
        }
    }

    // ==================== 内部工具 ====================

    /** 固定表 / 架空增长回退（不读 DB） */
    private fun getFixedOrProjectedIndex(year: Int): Double {
        return if (year <= 2030) {
            table.getFixedIndex(year)
        } else {
            futureProjector.project(2030, year)
        }
    }

    /** 构造回退快照（DB 缺失时使用） */
    private fun fallbackSnapshot(year: Int): EconomyIndexSnapshot {
        val globalIndex = getFixedOrProjectedIndex(year)
        val ratio = globalIndex / table.getBaseYearIndex().coerceAtLeast(0.01)
        return EconomyIndexSnapshot(
            year = year,
            globalIndex = globalIndex,
            transferFeeIndex = round(globalIndex * ratio * TRANSFER_FEE_RATIO),
            wageIndex = round(globalIndex * ratio * WAGE_RATIO),
            commercialIndex = round(globalIndex * ratio * COMMERCIAL_RATIO),
            source = if (year > 2030) "projected" else "fixed"
        )
    }

    /** EconomyIndexEntity → EconomyIndexSnapshot */
    private fun EconomyIndexEntity.toSnapshot(source: String): EconomyIndexSnapshot =
        EconomyIndexSnapshot(
            year = year,
            globalIndex = globalIndex,
            transferFeeIndex = transferFeeIndex,
            wageIndex = wageIndex,
            commercialIndex = commercialIndex,
            source = source
        )

    /** 保留 4 位小数 */
    private fun round(value: Double): Double =
        Math.round(value * 10_000) / 10_000.0

    companion object {
        // V0.2 §二 细分指数相对 globalIndex 的比例（转会费涨得快 / 工资次之 / 商业稍慢）
        private const val TRANSFER_FEE_RATIO = 1.10
        private const val WAGE_RATIO = 1.00
        private const val COMMERCIAL_RATIO = 0.90
    }
}
