package com.greendynasty.football.data.importer.manifest

import com.greendynasty.football.data.importer.ImportException
import com.greendynasty.football.data.importer.parser.JsonParser
import java.io.InputStream

/**
 * manifest.json 加载与校验
 *
 * 负责从输入流读取 manifest.json，解析为 [DataPackManifest]，
 * 并执行三项基础校验：
 * 1. pack_id 非空
 * 2. schema_version 与 [EXPECTED_SCHEMA_VERSION] 匹配
 * 3. file_list 非空
 *
 * 校验失败时抛出 [ImportException]，由上层 DataImportManager 决定中止或记录。
 */
class ManifestLoader {

    /**
     * 从输入流加载并校验 manifest。
     *
     * @param inputStream manifest.json 输入流（将被消耗并关闭）
     * @return 解析后的 [DataPackManifest]
     * @throws ImportException 解析或校验失败时抛出
     */
    fun load(inputStream: InputStream): DataPackManifest {
        val jsonText = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText().removePrefix("\uFEFF")
        }
        val manifest = JsonParser.parseManifest(jsonText)

        // 校验 pack_id 非空
        if (manifest.pack_id.isBlank()) {
            throw ImportException("manifest 校验失败：pack_id 不能为空")
        }
        // 校验 schema_version 匹配
        if (manifest.schema_version != EXPECTED_SCHEMA_VERSION) {
            throw ImportException(
                "manifest 校验失败：schema_version 不匹配，期望 $EXPECTED_SCHEMA_VERSION，实际 ${manifest.schema_version}"
            )
        }
        // 校验 file_list 非空
        if (manifest.file_list.isEmpty()) {
            throw ImportException("manifest 校验失败：file_list 不能为空")
        }
        return manifest
    }

    companion object {
        /** 期望的 manifest schema 版本（与 V0.2 manifest.json 一致） */
        const val EXPECTED_SCHEMA_VERSION = 2
    }
}
