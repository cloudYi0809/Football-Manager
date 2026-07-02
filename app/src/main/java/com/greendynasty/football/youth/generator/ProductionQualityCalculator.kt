package com.greendynasty.football.youth.generator

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.entity.SaveClubStateEntity
import com.greendynasty.football.youth.model.RecruitmentRange
import com.greendynasty.football.youth.model.YouthAcademyConfig
import com.greendynasty.football.youth.model.YouthAcademyStateEntity
import kotlin.random.Random

/**
 * T16 青训产出质量计算器（V0.1 08 §二.3 + T16 方案 §四.3 + §五.1）
 *
 * 7 因子产出质量公式（权重和为 1.0）：
 * ```
 * production_score =
 *   youth_level            × 0.25   # 青训等级
 * + training_facility      × 0.20   # 训练设施
 * + academy_manager        × 0.15   # 青训主管（V1 用 U18+U21 教练质量均值兜底）
 * + recruitment_range      × 0.15   # 招募范围（LOCAL=25/REGIONAL=50/NATIONAL=75/INTERNATIONAL=100）
 * + club_reputation        × 0.10   # 俱乐部声望
 * + nation_talent_pool     × 0.10   # 国家人才池加成
 * + random_genius          × 0.05   # 随机扰动（含天才因子）
 * ```
 *
 * 返回 0-100 分数。分数越高，月度生成概率与球员 PA 上限越高。
 *
 * @param databaseManager 三库管理入口（用于读取 club 状态）
 * @param config 青训学院配置
 */
class ProductionQualityCalculator(
    private val databaseManager: DatabaseManager,
    private val config: YouthAcademyConfig = YouthAcademyConfig.getDefault()
) {

    /**
     * 计算 7 因子产出质量分数。
     *
     * @param academy 青训学院状态
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @return 0-100 分数
     */
    suspend fun calculate(
        academy: YouthAcademyStateEntity,
        saveId: Int,
        clubId: Int
    ): Double {
        // 1. 青训等级 (0-100)
        val youthLevel = academy.youthLevel.toDouble()

        // 2. 训练设施 (0-100)
        val trainingFacility = academy.trainingFacility.toDouble()

        // 3. 青训主管（V1 用 U18 + U21 教练质量均值兜底）
        val academyManager = (academy.u18CoachQuality + academy.u21CoachQuality) / 2.0

        // 4. 招募范围（LOCAL=25, REGIONAL=50, NATIONAL=75, INTERNATIONAL=100）
        val recruitmentRange = RecruitmentRange.fromNameOrDefault(academy.recruitmentRange).qualityScore

        // 5. 俱乐部声望 (0-100) - 从 save_club_state 读取，未找到用 academy_reputation 兜底
        val clubState = getClubState(saveId, clubId)
        val clubReputation = clubState?.reputation?.toDouble() ?: academy.academyReputation.toDouble()

        // 6. 国家人才池加成 (0-100)
        val nationTalentPool = academy.nationTalentPoolBonus.toDouble()

        // 7. 随机天才概率（0-1，乘以 100 转分）
        val randomGenius = Random.nextDouble() * 100.0

        // 加权求和
        val score = (
            youthLevel * config.weightYouthLevel +
                trainingFacility * config.weightTrainingFacility +
                academyManager * config.weightAcademyManager +
                recruitmentRange * config.weightRecruitmentRange +
                clubReputation * config.weightClubReputation +
                nationTalentPool * config.weightNationTalentPool +
                randomGenius * config.weightRandomGenius
            )

        return score.coerceIn(0.0, 100.0)
    }

    /** 安全读取俱乐部状态，未找到返回 null。 */
    private suspend fun getClubState(saveId: Int, clubId: Int): SaveClubStateEntity? {
        return runCatching {
            databaseManager.saveClubStateDao().getByClub(saveId, clubId)
        }.getOrNull()
    }
}
