package com.greendynasty.football.prospect.path

import com.greendynasty.football.prospect.model.DefaultTransferRecord

/**
 * T15 历史新星默认路径 JSON 解析器（V0.2 08 §三）。
 *
 * history.historical_prospect_pool.default_transfer_path 字段为 JSON 数组字符串，格式：
 * ```json
 * [
 *   {"from_club_id": 1, "to_club_id": 2, "transfer_date": "2003-08-01", "transfer_fee": 1500000, "is_loan": 0},
 *   {"from_club_id": 2, "to_club_id": 3, "transfer_date": "2009-07-01", "transfer_fee": 94000000, "is_loan": 0}
 * ]
 * ```
 *
 * V1 简化：手写 JSON 解析避免引入依赖（项目已有 org.json 由 Android SDK 提供）。
 */
object DefaultTransferPathParser {

    /**
     * 解析默认转会路径 JSON。
     *
     * @param json default_transfer_path 字段值，nullable
     * @return 转会记录列表（按时间顺序）；输入为空返回空列表
     */
    fun parse(json: String?): List<DefaultTransferRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val records = mutableListOf<DefaultTransferRecord>()
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()

            // 提取所有 {...} 对象
            val objects = extractObjects(trimmed)
            for (obj in objects) {
                val record = parseObject(obj) ?: continue
                records.add(record)
            }
            // 按日期排序
            records.sortedBy { it.transferDate }
        }.getOrDefault(emptyList())
    }

    /** 反向序列化（V1 暂未使用，预留）。 */
    fun serialize(records: List<DefaultTransferRecord>): String {
        if (records.isEmpty()) return "[]"
        return records.joinToString(prefix = "[", postfix = "]", separator = ",") { rec ->
            val loan = if (rec.isLoan) 1 else 0
            val fromId = rec.fromClubId?.toString() ?: "null"
            "{\"from_club_id\":$fromId,\"to_club_id\":${rec.toClubId}," +
                "\"transfer_date\":\"${rec.transferDate}\"," +
                "\"transfer_fee\":${rec.transferFee},\"is_loan\":$loan}"
        }
    }

    /** 提取字符串中所有顶层 {...} 对象（不处理嵌套，路径 JSON 无嵌套）。 */
    private fun extractObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in json.indices) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    /** 解析单个 {...} 对象为 [DefaultTransferRecord]。 */
    private fun parseObject(obj: String): DefaultTransferRecord? {
        val fromClubId = extractInt(obj, "from_club_id")
        val toClubId = extractInt(obj, "to_club_id") ?: return null
        val transferDate = extractString(obj, "transfer_date") ?: return null
        val transferFee = extractInt(obj, "transfer_fee") ?: 0
        val isLoan = extractInt(obj, "is_loan") == 1
        return DefaultTransferRecord(
            fromClubId = fromClubId,
            toClubId = toClubId,
            transferDate = transferDate,
            transferFee = transferFee,
            isLoan = isLoan
        )
    }

    /** 从 JSON 对象字符串中提取字符串字段值。 */
    private fun extractString(obj: String, key: String): String? {
        val pattern = "\"$key\""
        val keyIdx = obj.indexOf(pattern)
        if (keyIdx < 0) return null
        // 找到冒号后的值
        var i = keyIdx + pattern.length
        while (i < obj.length && obj[i] != ':') i++
        if (i >= obj.length) return null
        i++ // 跳过冒号
        // 跳过空格
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length || obj[i] != '"') return null
        i++ // 跳过起始引号
        val sb = StringBuilder()
        while (i < obj.length && obj[i] != '"') {
            sb.append(obj[i])
            i++
        }
        return sb.toString()
    }

    /** 从 JSON 对象字符串中提取整数字段值。 */
    private fun extractInt(obj: String, key: String): Int? {
        val pattern = "\"$key\""
        val keyIdx = obj.indexOf(pattern)
        if (keyIdx < 0) return null
        var i = keyIdx + pattern.length
        while (i < obj.length && obj[i] != ':') i++
        if (i >= obj.length) return null
        i++ // 跳过冒号
        while (i < obj.length && obj[i].isWhitespace()) i++
        // 允许 null
        if (i + 3 < obj.length && obj.substring(i, i + 4) == "null") return null
        // 提取数字
        val sb = StringBuilder()
        if (i < obj.length && (obj[i] == '-' || obj[i] == '+')) {
            sb.append(obj[i])
            i++
        }
        while (i < obj.length && obj[i].isDigit()) {
            sb.append(obj[i])
            i++
        }
        return if (sb.isEmpty()) null else sb.toString().toIntOrNull()
    }
}
