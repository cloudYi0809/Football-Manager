package com.greendynasty.football.match.config

import android.content.Context
import com.greendynasty.football.match.model.EventType
import org.json.JSONObject

/**
 * 比赛配置加载器（V0.2 04 §九）
 *
 * 从 assets/match_config.json 加载 [MatchConfig]，缺失字段回退默认值。
 *
 * 使用 Android 内置 [org.json.JSONObject] 解析，无需为现有数据类添加 @Serializable 注解。
 *
 * 支持的字段（与 match_config.json 对应）：
 * - leagueAvgHomeXg / leagueAvgAwayXg
 * - homeAdvantage / baseXgPerTick / extremeScoreThreshold / poissonMaxGoals / starTemplateTriggerChance
 * - xG 上下限与 clamp 区间
 * - 极端比分抑制系数
 * - tick 参数
 * - counterModifierMin / counterModifierMax
 *
 * 使用：
 * ```
 * val config = MatchConfigLoader.load(context)   // 从 assets 读取
 * val config = MatchConfigLoader.default()       // 使用内置默认
 * ```
 */
object MatchConfigLoader {

    /** assets 中的配置文件名 */
    private const val CONFIG_FILE = "match_config.json"

    /**
     * 从 assets/match_config.json 加载配置。
     *
     * 文件不存在或解析失败时回退到 [default]，确保引擎始终可用。
     *
     * @param context Android Context（用于访问 assets）
     * @return 解析后的 MatchConfig，失败返回默认配置
     */
    fun load(context: Context): MatchConfig {
        return try {
            val json = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
            parse(JSONObject(json))
        } catch (e: Exception) {
            // 文件缺失或解析失败：回退默认配置，保证引擎可运行
            default()
        }
    }

    /**
     * 返回内置默认配置（不读 assets）。
     */
    fun default(): MatchConfig = MatchConfig.DEFAULT

    /**
     * 解析 JSON 为 [MatchConfig]，缺失字段使用默认值。
     */
    private fun parse(json: JSONObject): MatchConfig {
        val base = MatchConfig.DEFAULT
        return MatchConfig(
            // 联赛平均 xG
            leagueAvgHomeXg = json.optDouble("leagueAvgHomeXg", base.leagueAvgHomeXg),
            leagueAvgAwayXg = json.optDouble("leagueAvgAwayXg", base.leagueAvgAwayXg),
            // xG 上下限
            minXg = json.optDouble("minXg", base.minXg),
            maxRegularXg = json.optDouble("maxRegularXg", base.maxRegularXg),
            maxExtremeXg = json.optDouble("maxExtremeXg", base.maxExtremeXg),
            extremeMatchThreshold = json.optDouble("extremeMatchThreshold", base.extremeMatchThreshold),
            // 强弱修正 clamp
            attackDefRatioMin = json.optDouble("attackDefRatioMin", base.attackDefRatioMin),
            attackDefRatioMax = json.optDouble("attackDefRatioMax", base.attackDefRatioMax),
            controlRatioMin = json.optDouble("controlRatioMin", base.controlRatioMin),
            controlRatioMax = json.optDouble("controlRatioMax", base.controlRatioMax),
            controlRatioFactor = json.optDouble("controlRatioFactor", base.controlRatioFactor),
            // 主场优势
            homeAdvantage = json.optDouble("homeAdvantage", base.homeAdvantage),
            // 极端比分抑制
            extremeDampen5 = json.optDouble("extremeDampen5", base.extremeDampen5),
            extremeDampen6 = json.optDouble("extremeDampen6", base.extremeDampen6),
            extremeDampen7Plus = json.optDouble("extremeDampen7Plus", base.extremeDampen7Plus),
            calibrationMaxGoals = json.optInt("calibrationMaxGoals", base.calibrationMaxGoals),
            // 模拟参数
            matchEventIntervalMinutes = json.optInt("matchEventIntervalMinutes", base.matchEventIntervalMinutes),
            totalRegularTicks = json.optInt("totalRegularTicks", base.totalRegularTicks),
            stoppageTimeTicksMin = json.optInt("stoppageTimeTicksMin", base.stoppageTimeTicksMin),
            stoppageTimeTicksMax = json.optInt("stoppageTimeTicksMax", base.stoppageTimeTicksMax),
            // 新增参数
            baseXgPerTick = json.optDouble("baseXgPerTick", base.baseXgPerTick),
            extremeScoreThreshold = json.optInt("extremeScoreThreshold", base.extremeScoreThreshold),
            poissonMaxGoals = json.optInt("poissonMaxGoals", base.poissonMaxGoals),
            starTemplateTriggerChance = json.optDouble("starTemplateTriggerChance", base.starTemplateTriggerChance),
            counterModifierMin = json.optDouble("counterModifierMin", base.counterModifierMin),
            counterModifierMax = json.optDouble("counterModifierMax", base.counterModifierMax),
            // 事件基础概率（可选覆盖）
            eventBaseProbabilities = parseEventProbabilities(json.optJSONObject("eventBaseProbabilities"), base.eventBaseProbabilities)
        )
    }

    /** 解析事件基础概率，缺失时回退默认 */
    private fun parseEventProbabilities(
        json: JSONObject?,
        fallback: Map<EventType, Double>
    ): Map<EventType, Double> {
        if (json == null) return fallback
        val result = mutableMapOf<EventType, Double>()
        EventType.values().forEach { type ->
            val key = type.name.lowercase()
            if (json.has(key)) {
                result[type] = json.getDouble(key)
            } else {
                fallback[type]?.let { result[type] = it }
            }
        }
        return if (result.isEmpty()) fallback else result
    }
}
