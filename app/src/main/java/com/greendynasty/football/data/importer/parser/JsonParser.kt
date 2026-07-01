package com.greendynasty.football.data.importer.parser

import com.greendynasty.football.data.importer.ImportException
import com.greendynasty.football.data.importer.config.ImportConfig
import com.greendynasty.football.data.importer.manifest.DataPackManifest
import kotlinx.serialization.json.Json

/**
 * JSON 解析器（基于 kotlinx.serialization）
 *
 * 专用于解析数据包 manifest.json 与导入配置 import_config.json。
 * 配置为宽松模式：忽略未知字段、容忍非严格 JSON、对非法枚举值回退默认值，
 * 以提升对历史版本配置文件的兼容性。
 */
object JsonParser {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * 解析数据包清单 manifest.json。
     * @param jsonString JSON 文本
     * @return 解析后的 [DataPackManifest]
     * @throws ImportException 解析失败时抛出
     */
    fun parseManifest(jsonString: String): DataPackManifest {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            throw ImportException("manifest.json 解析失败：${e.message}", e)
        }
    }

    /**
     * 解析导入配置 import_config.json。
     * @param jsonString JSON 文本
     * @return 解析后的 [ImportConfig]
     * @throws ImportException 解析失败时抛出
     */
    fun parseConfig(jsonString: String): ImportConfig {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            throw ImportException("import_config.json 解析失败：${e.message}", e)
        }
    }
}
