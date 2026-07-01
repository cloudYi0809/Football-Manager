package com.greendynasty.football.data.importer.parser

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * 字段类型转换工具
 *
 * 提供 CSV 字符串到目标类型的统一转换，并内置位置/惯用脚枚举校验、
 * 日期格式归一化与 FM CA/PA（1-200）到游戏 CA（1-100）的换算。
 *
 * 所有方法对 null / 空白字符串安全：返回 null 或默认值，不抛异常。
 */
object FieldConverter {

    /** 合法位置枚举（10 种） */
    private val VALID_POSITIONS = setOf("GK", "RB", "LB", "CB", "DM", "CM", "AM", "RW", "LW", "ST")

    /** 合法惯用脚枚举 */
    private val VALID_FOOTS = setOf("Left", "Right", "Both")

    /** 支持的日期格式（按优先级匹配） */
    private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    )

    private val OUTPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 转 Int，空或非法返回 null */
    fun toInt(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return value.trim().toIntOrNull()
    }

    /** 转 Double，空或非法返回 null */
    fun toDouble(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return value.trim().toDoubleOrNull()
    }

    /** 转 LocalDate，支持多种常见格式，非法返回 null */
    fun toDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        for (fmt in DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, fmt)
            } catch (_: DateTimeParseException) {
                // 尝试下一个格式
            }
        }
        return null
    }

    /** 转 Boolean，"1"/"true"/"yes"/"y"/"t"（大小写不敏感）为 true，其余为 false */
    fun toBoolean(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "y", "t" -> true
            else -> false
        }
    }

    /**
     * 校验位置枚举值（GK/RB/LB/CB/DM/CM/AM/RW/LW/ST）。
     * @return 合法返回大写枚举值；非法或空返回空字符串
     */
    fun toPositionEnum(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val upper = value.trim().uppercase()
        return if (upper in VALID_POSITIONS) upper else ""
    }

    /**
     * 规范化位置枚举值（[toPositionEnum] 的可空变体）。
     *
     * 供 Entity 映射层使用：合法时返回大写枚举值；非法或空返回 null，
     * 以便调用方通过 Elvis (`?:`) 回退到原始字段值或其他默认策略。
     *
     * @param value 原始位置字符串（可为 null）
     * @return 合法返回大写枚举值；非法或空返回 null
     */
    fun normalizePosition(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val upper = value.trim().uppercase()
        return if (upper in VALID_POSITIONS) upper else null
    }

    /**
     * 校验惯用脚枚举值（Left/Right/Both）。
     * @return 合法返回首字母大写形式；非法或空返回空字符串
     */
    fun toFootEnum(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val normalized = value.trim().lowercase().replaceFirstChar { it.uppercase() }
        return if (normalized in VALID_FOOTS) normalized else ""
    }

    /**
     * 统一日期格式为 YYYY-MM-DD。
     * 无法解析时原样返回，便于后续错误定位。
     */
    fun normalizeDate(value: String): String {
        val date = toDate(value) ?: return value
        return date.format(OUTPUT_DATE_FORMAT)
    }

    /**
     * 将日期字符串规范化为 ISO 格式（YYYY-MM-DD），null 安全。
     *
     * 供 Entity 映射层将 CSV 日期字段转换为 Entity 的可空日期字符串字段。
     *
     * @param value 原始日期字符串（可为 null）
     * @return 规范化后的 ISO 日期字符串；输入为空或无法解析时返回 null
     */
    fun toIsoDate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val date = toDate(value) ?: return null
        return date.format(OUTPUT_DATE_FORMAT)
    }

    /**
     * FM CA/PA 转换：1-200 → 1-100，公式 round(raw/2)。
     *
     * 接受可空输入，输入为 null 时返回 null，以便调用方通过 Elvis (`?:`)
     * 提供默认值（如 `fmCaToGameCa(row.getInt("ca")) ?: 50`）。
     *
     * @param fmCa FM 原始能力值（1-200），可为 null
     * @return 游戏内能力值（钳制到 [1, 100]）；输入 null 时返回 null
     */
    fun fmCaToGameCa(fmCa: Int?): Int? {
        if (fmCa == null) return null
        return (fmCa / 2.0).roundToInt().coerceIn(1, 100)
    }
}
