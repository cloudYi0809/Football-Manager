package com.greendynasty.football.scouting.core

import com.greendynasty.football.scouting.config.ScoutConfig

/**
 * T14 地区人才密度提供者（V0.2 08 §三.5）。
 *
 * 各地区历史产出天才的密度（0-1），影响球探发现概率（7 因子之一，权重 0.10）。
 * 巴西/阿根廷/法国 等高产地区密度高；亚洲/北美密度低。
 *
 * 静态配置表见 [ScoutConfig.regionTalentDensity]；调参只需修改 [ScoutConfig]。
 */
class RegionTalentDensityProvider(private val config: ScoutConfig = ScoutConfig.DEFAULT) {

    /**
     * 获取指定地区的人才密度。
     *
     * @param regionCode 地区代码（见 ScoutRegionCode.code）
     * @return 0-1 之间的密度值（未配置地区返回 [ScoutConfig.defaultDensity]）
     */
    fun getDensity(regionCode: String): Double {
        return config.regionTalentDensity[regionCode] ?: config.defaultDensity
    }
}
