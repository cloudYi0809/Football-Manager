package com.greendynasty.football.data.importer.parser

import com.greendynasty.football.data.importer.ImportException
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.LinkedHashMap

/**
 * 流式 CSV 解析器
 *
 * 基于 [BufferedReader] 逐行读取，控内存（不会一次性载入全部行），
 * 适用于 10000+ 行的大数据量 CSV 文件解析。
 *
 * 支持的 CSV 边界情况：
 * 1. 双引号包围字段（引号内的逗号不视为分隔符）
 * 2. 逗号转义（引号内逗号原样保留）
 * 3. 字段内换行（引号未闭合时自动拼接下一物理行）
 * 4. 引号转义（`""` 转义为单个 `"`）
 * 5. UTF-8 BOM 自动剥离
 *
 * 协程友好：所有方法为阻塞式同步实现，应在 [kotlinx.coroutines.Dispatchers.IO] 上调用。
 *
 * 用法：
 * ```
 * CsvParser(inputStream).use { parser ->
 *     val headers = parser.readHeader()
 *     var row = parser.readRow()
 *     while (row != null) {
 *         // 处理 row
 *         row = parser.readRow()
 *     }
 * }
 * ```
 *
 * 或直接 for-each 遍历（自动读取表头）：
 * ```
 * CsvParser(inputStream).use { parser ->
 *     for (row in parser) { /* 处理 row */ }
 * }
 * ```
 */
class CsvParser(
    private val inputStream: InputStream,
    private val charset: Charset = Charsets.UTF_8,
    private val delimiter: Char = ',',
    private val quote: Char = '"'
) : Closeable, Iterable<CsvRow> {

    private val reader: BufferedReader = BufferedReader(InputStreamReader(inputStream, charset))
    private var headers: List<String>? = null
    private var rowIndex = 0
    private var closed = false

    /**
     * 读取表头行，建立列名列表。
     * 必须在 [readRow] 之前调用一次。
     *
     * @return 表头列表（已 trim，并剥离 UTF-8 BOM）
     * @throws ImportException 文件为空时抛出
     */
    fun readHeader(): List<String> {
        ensureOpen()
        val firstLine = readLogicalLine() ?: throw ImportException("CSV 文件为空，无法读取表头")
        // 剥离 UTF-8 BOM
        val cleaned = firstLine.removePrefix("\uFEFF")
        val parsed = parseLine(cleaned).map { it.trim() }
        headers = parsed
        return parsed
    }

    /**
     * 读取一行数据。
     *
     * @return 行数据；无更多数据返回 null
     * @throws ImportException 未先调用 [readHeader] 时抛出
     */
    fun readRow(): CsvRow? {
        ensureOpen()
        val h = headers ?: throw ImportException("请先调用 readHeader() 读取表头")
        val line = readLogicalLine() ?: return null
        rowIndex++
        val values = parseLine(line)
        // 列名 → 值映射；字段缺失时填空字符串，多余字段忽略
        val map = LinkedHashMap<String, String>(h.size)
        for (i in h.indices) {
            map[h[i]] = if (i < values.size) values[i] else ""
        }
        return CsvRow(rowIndex, map)
    }

    /**
     * 读取一个逻辑行。
     *
     * 若一行内引号未闭合（即字段内嵌换行），则继续读取下一物理行拼接，
     * 直到引号平衡或文件结束。
     */
    private fun readLogicalLine(): String? {
        val first = reader.readLine() ?: return null
        val sb = StringBuilder(first)
        while (!isQuoteBalanced(sb)) {
            val next = reader.readLine() ?: break
            sb.append('\n').append(next)
        }
        return sb.toString()
    }

    /**
     * 检查引号是否平衡（即当前文本不在引号字段内部）。
     * 处理 `""` 转义：连续两个引号视为一个字面引号，不改变引号状态。
     */
    private fun isQuoteBalanced(s: CharSequence): Boolean {
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == quote) {
                if (inQuotes && i + 1 < s.length && s[i + 1] == quote) {
                    i++ // 转义引号，跳过下一个
                } else {
                    inQuotes = !inQuotes
                }
            }
            i++
        }
        return !inQuotes
    }

    /**
     * 解析单行 CSV 为字段列表。
     *
     * 支持：双引号包围字段、引号内分隔符保留、`""` 转义为 `"`。
     * 字段内换行已由 [readLogicalLine] 拼接为单行传入。
     */
    private fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == quote -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == quote) {
                        // 转义引号："" → "
                        current.append(quote)
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun ensureOpen() {
        check(!closed) { "CsvParser 已关闭" }
    }

    override fun close() {
        if (!closed) {
            closed = true
            reader.close()
        }
    }

    /**
     * 返回行迭代器，支持 for-each 遍历。
     * 首次调用时自动读取表头。
     */
    override fun iterator(): Iterator<CsvRow> = object : Iterator<CsvRow> {
        private var nextRow: CsvRow? = null
        private var headerInitialized = false
        private var done = false

        override fun hasNext(): Boolean {
            if (done) return false
            if (!headerInitialized) {
                headerInitialized = true
                readHeader()
            }
            nextRow = readRow()
            if (nextRow == null) done = true
            return nextRow != null
        }

        override fun next(): CsvRow {
            return nextRow ?: throw NoSuchElementException("CSV 已无更多行")
        }
    }
}
