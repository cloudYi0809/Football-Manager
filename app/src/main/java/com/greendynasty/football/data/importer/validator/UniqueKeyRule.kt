package com.greendynasty.football.data.importer.validator

import com.greendynasty.football.data.importer.parser.CsvRow

/**
 * 唯一性校验：字段组合值必须唯一
 *
 * 内部用 [MutableSet] 记录已见过的联合 key，重复即报错。
 * 单字段唯一性时传入一个字段即可。
 *
 * 注意：该规则为有状态规则，一个实例对应一次完整导入流，不可跨导入复用。
 *
 * @property fields 联合唯一字段列表
 */
class UniqueKeyRule(val fields: List<String>) : ValidationRule<CsvRow> {

    /** 便捷构造：可变参数 */
    constructor(vararg fields: String) : this(fields.toList())

    /** 已见过的 key 集合（用 \u0001 分隔避免与字段值冲突） */
    private val seen: MutableSet<String> = mutableSetOf()

    override fun validate(row: CsvRow, rowIndex: Int): ValidationResult {
        val result = ValidationResult()
        val key = fields.joinToString("\u0001") { row.getString(it) ?: "" }
        if (!seen.add(key)) {
            val displayKey = fields.joinToString(", ") { f -> "$f=${row.getString(f) ?: ""}" }
            result.addError(rowIndex, fields.joinToString(","), "唯一键重复：$displayKey")
        }
        return result
    }
}
