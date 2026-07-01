package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * Entity 转换器接口
 *
 * 负责将 [CsvRow] 转换为目标 Entity 实例。
 * 实现类按表定制（PlayerMapper / ClubMapper / ...），由各 Importer 持有。
 *
 * @param T 目标 Entity 类型
 */
interface EntityMapper<T> {

    /**
     * 将一行 CSV 数据映射为 Entity 实例。
     * @param row CSV 行
     * @return Entity 实例
     * @throws IllegalArgumentException 必填字段缺失或类型转换失败时抛出
     */
    fun map(row: CsvRow): T
}
