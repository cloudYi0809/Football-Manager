package com.greendynasty.football.data.importer.parser

import java.time.LocalDate

/**
 * CSV 行数据载体
 *
 * 由 [CsvParser.readRow] 产出，封装一行 CSV 数据为 header → value 的映射，
 * 并提供常用类型的便捷取值方法。所有类型转换委托 [FieldConverter] 完成。
 *
 * @property rowIndex 行号（从 1 开始，0 表示表头行）
 * @property values 字段映射（列名 → 字符串值）
 */
class CsvRow(
    val rowIndex: Int,
    val values: Map<String, String>
) {

    /**
     * 获取原始字符串值。
     * @param name 列名
     * @return 字段值；字段不存在时返回 null
     */
    fun getString(name: String): String? = values[name]

    /** [getString] 的简写别名（与 T01 文档 CsvRow API 对齐） */
    fun get(name: String): String? = values[name]

    /** 获取字符串值，字段不存在或 null 时返回空字符串（与 T01 文档 CsvRow API 对齐） */
    fun getOrEmpty(name: String): String = values[name] ?: ""

    /**
     * 获取 Int 值。
     * @return 解析成功返回 Int；空值、字段不存在或非法返回 null
     */
    fun getInt(name: String): Int? = FieldConverter.toInt(values[name])

    /**
     * 获取 Int 值，空或非法时返回默认值。
     */
    fun getIntOrDefault(name: String, default: Int): Int = getInt(name) ?: default

    /**
     * 获取 Double 值。
     * @return 解析成功返回 Double；空值、字段不存在或非法返回 null
     */
    fun getDouble(name: String): Double? = FieldConverter.toDouble(values[name])

    /**
     * 获取日期值。
     * @return 解析成功返回 LocalDate；空值、字段不存在或非法返回 null
     */
    fun getDate(name: String): LocalDate? = FieldConverter.toDate(values[name])

    /**
     * 获取 Boolean 值。
     * "1"/"true"/"yes"/"y"/"t"（大小写不敏感）视为 true，其余视为 false。
     * 字段不存在时返回 false。
     */
    fun getBoolean(name: String): Boolean = FieldConverter.toBoolean(values[name])
}
