package com.greendynasty.football.data.importer.api

import android.content.Context
import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.importer.ImportException
import com.greendynasty.football.data.importer.config.ImportConfig
import com.greendynasty.football.data.importer.importer.AgentImporter
import com.greendynasty.football.data.importer.importer.BaseImporter
import com.greendynasty.football.data.importer.importer.ClubImporter
import com.greendynasty.football.data.importer.importer.EconomyConfigImporter
import com.greendynasty.football.data.importer.importer.LeagueImporter
import com.greendynasty.football.data.importer.importer.PlayerImporter
import com.greendynasty.football.data.importer.importer.ProspectImporter
import com.greendynasty.football.data.importer.importer.ScoutImporter
import com.greendynasty.football.data.importer.importer.ScheduleImporter
import com.greendynasty.football.data.importer.importer.SquadImporter
import com.greendynasty.football.data.importer.importer.StaffImporter
import com.greendynasty.football.data.importer.importer.TransferImporter
import com.greendynasty.football.data.importer.manifest.ChecksumUtil
import com.greendynasty.football.data.importer.manifest.DataPackManifest
import com.greendynasty.football.data.importer.manifest.ManifestLoader
import com.greendynasty.football.data.importer.validator.DataValidator
import com.greendynasty.football.data.importer.validator.Severity
import com.greendynasty.football.data.importer.validator.ValidationError
import com.greendynasty.football.data.importer.writer.HistoryDbWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据导入管理器（整个数据导入的入口）
 *
 * 负责编排完整的数据包导入流程：
 * 1. 解析数据包路径（assets 内置虚构包或本地目录）
 * 2. 加载并校验 manifest.json（schema 版本、checksum）
 * 3. 临时关闭 history.db 只读约束
 * 4. 写入 manifest 元信息（便于断点识别与版本追溯）
 * 5. 按依赖顺序执行 Importer 链（单个失败不影响其他）
 * 6. 恢复只读模式
 * 7. 返回 [ImportReport]
 *
 * 依赖顺序（基础表 → 关系表 → 派生表）：
 * clubs → seasons/competitions → players → squads → transfers →
 * matches → prospects → scouts → agents → staff → economy
 *
 * 错误隔离：单个 Importer 抛异常时记录错误并继续后续 Importer，
 * 由 [ImportConfig.strict_mode] 控制是否在首个错误时中止。
 *
 * 用法：
 * ```
 * val manager = DataImportManager(context, historyDb, ImportConfig())
 * val report = manager.importPack(packPath = null) { current, total, stage ->
 *     // 更新 UI 进度
 * }
 * ```
 *
 * @param context       Android 上下文（用于 assets 访问）
 * @param historyDb     history.db 实例（导入时临时关闭只读）
 * @param config        导入配置
 * @param ioDispatcher  IO 调度器（默认 [Dispatchers.IO]）
 */
class DataImportManager(
    private val context: Context,
    private val historyDb: HistoryDatabase,
    private val config: ImportConfig = ImportConfig(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {

    /** assets 内置虚构数据包路径 */
    private val assetsPackPath = "packs/fictional_default"

    /** assets 解压目标目录 */
    private val extractedPackDir: File by lazy {
        File(context.filesDir, "packs/fictional_default")
    }

    /**
     * 入口：执行完整数据包导入。
     *
     * @param packPath 数据包目录绝对路径；null 表示使用 assets 内置虚构包
     * @param callback 进度回调（UI 订阅，可空）
     * @return 完整导入报告
     */
    suspend fun importPack(
        packPath: String? = null,
        callback: ImportProgressCallback? = null
    ): ImportReport = withContext(ioDispatcher) {
        val startMs = System.currentTimeMillis()
        val allResults = mutableListOf<ImportResult>()
        val allErrors = mutableListOf<ValidationError>()
        var totalRows = 0
        var successRows = 0
        var failedRows = 0
        var packId = "unknown"

        try {
            // 1. 解析数据包路径
            val packDir = resolvePackDir(packPath)
            callback?.onStage("加载数据包目录：${packDir.name}")

            // 2. 加载并校验 manifest.json
            val manifestFile = File(packDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw ImportException("manifest.json 不存在：${manifestFile.absolutePath}")
            }
            val manifest = manifestFile.inputStream().use { ManifestLoader().load(it) }
            packId = manifest.pack_id
            callback?.onStage("manifest 校验通过：$packId")

            // 3. 可选：checksum 校验
            if (config.enable_checksum && manifest.checksum.isNotBlank()) {
                callback?.onStage("校验数据包 checksum")
                if (!verifyPackChecksum(packDir, manifest)) {
                    throw ImportException("数据包 checksum 校验失败")
                }
                callback?.onStage("checksum 校验通过")
            }

            // 4. 准备 history.db（关闭只读约束以写入）
            val writer = HistoryDbWriter(historyDb, config.batch_size)
            writer.openForWrite()
            callback?.onStage("history.db 已进入写入模式")

            try {
                // 5. 写入 manifest 元信息（先写，便于断点识别）
                writer.insertDataPackManifest(
                    packId = manifest.pack_id,
                    packName = manifest.pack_name,
                    packType = manifest.pack_type,
                    schemaVersion = manifest.schema_version,
                    dataVersion = manifest.data_version,
                    distribution = manifest.distribution,
                    containsRealNames = manifest.contains_real_names,
                    containsRealLogos = manifest.contains_real_logos,
                    containsRealFaces = manifest.contains_real_faces,
                    licenseNote = manifest.license_note.takeIf { it.isNotBlank() },
                    checksum = manifest.checksum.takeIf { it.isNotBlank() },
                    createdAt = manifest.created_at.takeIf { it.isNotBlank() },
                    updatedAt = null
                )

                // 6. 构建 Importer 链（按依赖顺序）
                val importers = buildImporterChain(packDir, writer)
                val totalCount = importers.size

                // 7. 逐 Importer 执行（单个失败不影响其他）
                for ((index, importer) in importers.withIndex()) {
                    callback?.onStage("(${index + 1}/$totalCount) ${importer.stageName}")
                    try {
                        val result = importer.import(callback)
                        allResults.add(result)
                        totalRows += result.totalRows
                        successRows += result.successRows
                        failedRows += result.failedRows
                        allErrors.addAll(result.errors)

                        // 严格模式：首个错误即中止
                        if (config.strict_mode && !result.isSuccess) {
                            callback?.onError("strict_mode=true，导入中止：${importer.stageName}")
                            break
                        }
                    } catch (e: Exception) {
                        // 单个 Importer 异常：记录错误，继续后续
                        val error = ValidationError(
                            rowIndex = 0,
                            field = "_importer",
                            message = "${importer.stageName} 异常：${e.message}",
                            severity = Severity.ERROR
                        )
                        allErrors.add(error)
                        allResults.add(
                            ImportResult(
                                importerName = importer.stageName,
                                totalRows = 0,
                                successRows = 0,
                                failedRows = 0,
                                errors = listOf(error),
                                durationMs = 0L
                            )
                        )
                        callback?.onError("${importer.stageName} 异常：${e.message}")
                        if (config.strict_mode) break
                    }
                }
            } finally {
                // 8. 恢复只读模式（无论成功失败都恢复）
                writer.closeAndRestoreReadOnly()
                callback?.onStage("history.db 已恢复只读模式")
            }

            // 9. 可选：VACUUM 优化
            if (allErrors.isEmpty()) {
                try {
                    callback?.onStage("VACUUM 优化数据库")
                    historyDb.openHelper.writableDatabase.execSQL("VACUUM")
                } catch (e: Exception) {
                    // VACUUM 失败不影响导入结果
                    callback?.onError("VACUUM 失败（不影响导入）：${e.message}")
                }
            }
        } catch (e: ImportException) {
            allErrors.add(
                ValidationError(
                    rowIndex = 0,
                    field = "_fatal",
                    message = e.message ?: "导入流程异常",
                    severity = Severity.ERROR
                )
            )
            callback?.onError("导入中止：${e.message}")
        } catch (e: Exception) {
            allErrors.add(
                ValidationError(
                    rowIndex = 0,
                    field = "_fatal",
                    message = "未预期异常：${e.message}",
                    severity = Severity.ERROR
                )
            )
            callback?.onError("导入中止（未预期异常）：${e.message}")
        }

        val endMs = System.currentTimeMillis()
        ImportReport(
            packId = packId,
            startTime = startMs,
            endTime = endMs,
            results = allResults,
            totalRows = totalRows,
            successRows = successRows,
            failedRows = failedRows,
            errors = allErrors
        )
    }

    /**
     * 解析数据包目录。
     *
     * - packPath 非空：使用指定本地路径
     * - packPath 为空：从 assets 解压内置虚构包到 filesDir/packs/fictional_default
     */
    private fun resolvePackDir(packPath: String?): File {
        return if (packPath != null) {
            val dir = File(packPath)
            if (!dir.exists() || !dir.isDirectory) {
                throw ImportException("数据包目录不存在或非目录：$packPath")
            }
            dir
        } else {
            // 从 assets 解压到 filesDir
            if (!extractedPackDir.exists() || !File(extractedPackDir, "manifest.json").exists()) {
                extractAssetsPack()
            }
            extractedPackDir
        }
    }

    /**
     * 从 assets 解压内置虚构数据包到 filesDir/packs/fictional_default。
     */
    private fun extractAssetsPack() {
        extractedPackDir.mkdirs()
        val assetFiles = context.assets.list(assetsPackPath) ?: emptyArray()
        if (assetFiles.isEmpty()) {
            throw ImportException("assets 内无内置数据包：$assetsPackPath")
        }
        for (name in assetFiles) {
            context.assets.open("$assetsPackPath/$name").use { input ->
                File(extractedPackDir, name).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * 校验数据包内所有文件的 SHA-256 checksum。
     *
     * 仅校验 manifest.file_list 中声明了 checksum 的文件。
     *
     * @return true 表示全部校验通过
     */
    private fun verifyPackChecksum(packDir: File, manifest: DataPackManifest): Boolean {
        for (entry in manifest.file_list) {
            if (entry.checksum.isBlank()) continue
            val file = File(packDir, entry.path)
            if (!file.exists()) continue
            val matched = file.inputStream().use { stream ->
                ChecksumUtil.verifyChecksum(stream, entry.checksum)
            }
            if (!matched) return false
        }
        return true
    }

    /**
     * 构建 Importer 链（按依赖顺序）。
     *
     * 顺序错误会导致外键校验失败：
     * 1. ClubImporter     - clubs + youth_academy（基础表）
     * 2. LeagueImporter   - seasons + competitions + club_competition_season（依赖 clubs）
     * 3. PlayerImporter   - players + player_attributes（基础表）
     * 4. SquadImporter    - squads（依赖 seasons/clubs/players）
     * 5. TransferImporter - transfers（依赖 players/clubs/seasons）
     * 6. ScheduleImporter - matches（依赖 seasons/competitions/clubs）
     * 7. ProspectImporter - historical_prospects（依赖 players）
     * 8. ScoutImporter    - scouts + scout_region_knowledge（依赖 clubs）
     * 9. AgentImporter    - agents + player_agent（依赖 players）
     * 10. StaffImporter   - staff（依赖 clubs）
     * 11. EconomyConfigImporter - economy_index + league_economy_profile（依赖 competitions）
     */
    private fun buildImporterChain(packDir: File, writer: HistoryDbWriter): List<BaseImporter> {
        val validator = DataValidator()
        val batchSize = config.batch_size
        val maxErrors = config.max_errors
        val csv = { name: String -> File(packDir, name) }

        return listOf(
            ClubImporter(
                clubsCsv = csv("clubs.csv"),
                youthAcademyCsv = csv("youth_academy.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            LeagueImporter(
                seasonsCsv = csv("seasons.csv"),
                competitionsCsv = csv("competitions.csv"),
                clubCompetitionSeasonCsv = csv("club_competition_season.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            PlayerImporter(
                playersCsv = csv("players.csv"),
                attributesCsv = csv("player_attributes.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            SquadImporter(
                squadsCsv = csv("squads.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            TransferImporter(
                transfersCsv = csv("transfers.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            ScheduleImporter(
                matchesCsv = csv("matches.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            ProspectImporter(
                prospectsCsv = csv("historical_prospects.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            ScoutImporter(
                scoutsCsv = csv("scouts.csv"),
                regionKnowledgeCsv = csv("scout_region_knowledge.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            AgentImporter(
                agentsCsv = csv("agents.csv"),
                playerAgentCsv = csv("player_agent.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            StaffImporter(
                staffCsv = csv("staff.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            ),
            EconomyConfigImporter(
                economyIndexCsv = csv("economy_index.csv"),
                leagueProfileCsv = csv("league_economy_profile.csv"),
                validator = validator,
                writer = writer,
                batchSize = batchSize,
                maxErrors = maxErrors
            )
        )
    }
}
