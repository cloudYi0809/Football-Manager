package com.greendynasty.football.transfer.search

import com.greendynasty.football.transfer.config.EconomyParams
import kotlin.math.pow

/**
 * 转会经济估算器（V0.2 `07_经济通胀_身价_工资模型.md` 简化版）。
 *
 * 严格依据 V0.2 §五/六/七/九：
 * ```
 * base_value = ability_value_curve(CA) * age_multiplier(age)
 *            * potential_multiplier(PA, age) * position_multiplier(position)
 *            * contract_multiplier(remainingYears) * economy_index(year)
 *
 * expected_wage = wage_base_by_CA(CA) * squad_role_multiplier
 *               * economy_index(year)
 * ```
 *
 * V0.2 文档未提供完整公式细节时使用本简化模型（铁律：调参不改架构）。
 *
 * @param params 经济模型参数
 */
class EconomyEstimator(
    private val params: EconomyParams = EconomyParams()
) {

    /**
     * 估算球员市场身价（V0.2 §五）。
     *
     * @param ca 当前能力值
     * @param pa 潜力值
     * @param age 年龄
     * @param position 主要位置
     * @param contractUntil 合同到期日期（ISO yyyy-MM-dd），null 视为自由球员
     * @param currentYear 当前年份（用于经济指数）
     * @return 市场身价（整数）
     */
    fun estimateMarketValue(
        ca: Int,
        pa: Int,
        age: Int,
        position: String,
        contractUntil: String?,
        currentYear: Int
    ): Int {
        val abilityCurve = abilityValueCurve(ca)
        val ageMul = ageMultiplier(age)
        val potMul = potentialMultiplier(pa, age, ca)
        val posMul = positionMultiplier(position)
        val contractMul = contractMultiplier(contractUntil, currentYear)
        val ecoIdx = economyIndex(currentYear)

        val value = abilityCurve * ageMul * potMul * posMul * contractMul * ecoIdx
        return value.toInt().coerceAtLeast(50_000)
    }

    /**
     * 估算球员期望周薪（V0.2 §九）。
     *
     * @param ca 当前能力值
     * @param position 主要位置
     * @param squadRole 队内角色（starter / backup / prospect）
     * @param currentYear 当前年份
     * @return 期望周薪（整数）
     */
    fun estimateExpectedWage(
        ca: Int,
        position: String,
        squadRole: String?,
        currentYear: Int
    ): Int {
        val wageBase = wageBaseByCa(ca)
        val roleMul = squadRoleMultiplier(squadRole)
        val posMul = wagePositionMultiplier(position)
        val ecoIdx = economyIndex(currentYear)

        val wage = wageBase * roleMul * posMul * ecoIdx
        return wage.toInt().coerceAtLeast(1_000)
    }

    /**
     * CA 价值曲线（V0.2 §五）。
     *
     * `ability_value_curve = base_amount * pow(1.075, CA - 50)`
     */
    fun abilityValueCurve(ca: Int): Double {
        val normalizedCa = ca.coerceIn(0, 200)
        return params.abilityBaseAmount * params.abilityGrowthRate.pow(normalizedCa - 50)
    }

    /**
     * 年龄系数（V0.2 §六）。
     *
     * | 年龄 | 系数 |
     * |---|---|
     * | 16-18 | 0.60-1.20 |
     * | 19-22 | 1.10-1.50 |
     * | 23-27 | 1.20-1.60 |
     * | 28-30 | 1.00-1.30 |
     * | 31-33 | 0.65-0.95 |
     * | 34+   | 0.20-0.60 |
     */
    fun ageMultiplier(age: Int): Double = when {
        age <= 17 -> 0.60
        age == 18 -> 0.90
        age in 19..22 -> 1.30
        age in 23..27 -> 1.50
        age in 28..30 -> 1.15
        age in 31..33 -> 0.80
        age in 34..35 -> 0.50
        age in 36..37 -> 0.35
        else -> 0.20
    }

    /**
     * 潜力乘数（V0.2 §五/六，结合 PA 与年龄）。
     *
     * 年轻球员（≤23）若 PA 远大于 CA，则有"成长溢价"。
     */
    fun potentialMultiplier(pa: Int, age: Int, ca: Int): Double {
        val growthRoom = (pa - ca).coerceAtLeast(0)
        if (growthRoom == 0) return 1.0
        return when {
            age <= 19 -> 1.0 + growthRoom * 0.012 // 最高约 +60%
            age in 20..22 -> 1.0 + growthRoom * 0.008
            age in 23..25 -> 1.0 + growthRoom * 0.004
            age in 26..28 -> 1.0 + growthRoom * 0.002
            else -> 1.0
        }
    }

    /**
     * 位置身价修正（V0.2 §五 position_multiplier）。
     *
     * 前锋 / 攻击中场溢价，门将折价。
     */
    fun positionMultiplier(position: String): Double = when (position) {
        "ST", "CF" -> 1.20
        "LW", "RW" -> 1.15
        "AM" -> 1.10
        "CM" -> 1.00
        "DM" -> 0.95
        "CB" -> 0.90
        "LB", "RB" -> 0.85
        "GK" -> 0.75
        else -> 1.00
    }

    /**
     * 合同剩余身价修正（V0.2 §七）。
     *
     * | 合同剩余 | 修正 |
     * |---|---|
     * | 4 年以上 | 1.20 |
     * | 3 年 | 1.10 |
     * | 2 年 | 1.00 |
     * | 1 年 | 0.70 |
     * | 6 个月 | 0.40 |
     * | 自由球员 | 0.30（转会费清零，签字费上升） |
     */
    fun contractMultiplier(contractUntil: String?, currentYear: Int): Double {
        if (contractUntil.isNullOrBlank()) return 0.30
        val untilYear = runCatching {
            contractUntil.take(4).toInt()
        }.getOrNull() ?: return 1.00

        val remaining = untilYear - currentYear
        return when {
            remaining >= 4 -> 1.20
            remaining == 3 -> 1.10
            remaining == 2 -> 1.00
            remaining == 1 -> 0.70
            remaining <= 0 -> 0.40
            else -> 1.00
        }
    }

    /**
     * 经济指数（V0.2 §二 时代通胀系数）。
     *
     * 2002 基准 = 1.00；2030 后按年增长 3%。
     */
    fun economyIndex(year: Int): Double {
        val baseYear = 2002
        return when {
            year <= 1992 -> 0.35
            year in 1993..1994 -> 0.40
            year in 1995..1997 -> 0.55
            year in 1998..2001 -> 0.80
            year == 2002 -> params.economyIndexBase
            year in 2003..2005 -> 1.10
            year in 2006..2009 -> 1.40
            year in 2010..2013 -> 1.90
            year in 2014..2016 -> 2.70
            year in 2017..2019 -> 3.40
            year in 2020..2023 -> 3.90
            year in 2024..2029 -> 4.50
            year == 2030 -> 5.00
            else -> {
                // 2030 后按年增长 3%
                val yearsAfter2030 = year - 2030
                5.00 * (1.03).pow(yearsAfter2030)
            }
        }
    }

    /** 工资基准：CA=50 时周薪 */
    fun wageBaseByCa(ca: Int): Double {
        val normalizedCa = ca.coerceIn(0, 200)
        return params.wageBaseAmount * 1.05.pow(normalizedCa - 50)
    }

    /** 队内角色工资修正（V0.2 §九 表） */
    fun squadRoleMultiplier(role: String?): Double = when (role?.lowercase()) {
        "starter", "key", "core" -> 1.40
        "first_team" -> 1.10
        "backup", "rotation" -> 0.85
        "substitute" -> 0.60
        "prospect", "youth" -> 0.25
        null -> 1.10
        else -> 1.00
    }

    /** 位置工资修正（与身价类似但更平缓） */
    fun wagePositionMultiplier(position: String): Double = when (position) {
        "ST", "CF", "LW", "RW" -> 1.10
        "AM", "CM" -> 1.05
        "DM", "CB" -> 0.95
        "LB", "RB" -> 0.92
        "GK" -> 0.85
        else -> 1.00
    }
}
