package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.save.entity.EconomyIndexEntity
import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 经济指数映射器（object 单例，无状态）
 *
 * 将 economy_index.csv 行数据转换为 [EconomyIndexEntity]。
 * 按年份存储全球经济指数，用于转会费、工资的通胀计算。
 *
 * CSV 字段映射：
 * - year → year（年份，主键）
 * - global_index → globalIndex（全球经济指数基准，如 2002=1.00）
 * - transfer_fee_index → transferFeeIndex（转会费通胀指数）
 * - wage_index → wageIndex（工资通胀指数）
 * - commercial_index → commercialIndex（商业收入指数）
 *
 * 容错：Double 字段缺失时使用 0.0 作为默认值。
 * CsvRow 无原生 getDouble 方法，使用 get + toDoubleOrNull 内联转换。
 */
object EconomyIndexMapper : EntityMapper<EconomyIndexEntity> {

    override fun map(row: CsvRow): EconomyIndexEntity {
        return EconomyIndexEntity(
            year = row.getInt("year")
                ?: throw IllegalArgumentException("year 缺失，无法映射 EconomyIndexEntity"),
            globalIndex = getDoubleOrDefault(row, "global_index", 0.0),
            transferFeeIndex = getDoubleOrDefault(row, "transfer_fee_index", 0.0),
            wageIndex = getDoubleOrDefault(row, "wage_index", 0.0),
            commercialIndex = getDoubleOrDefault(row, "commercial_index", 0.0)
        )
    }

    /**
     * 从 CsvRow 读取 Double 字段，缺失或格式错误时返回默认值
     */
    private fun getDoubleOrDefault(row: CsvRow, column: String, default: Double): Double {
        return row.get(column)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: default
    }
}
