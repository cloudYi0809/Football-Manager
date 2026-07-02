package com.greendynasty.football.scouting.core

import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.history.entity.ScoutEntity
import com.greendynasty.football.scouting.config.ScoutConfig
import com.greendynasty.football.scouting.data.SaveScoutHiredEntity
import com.greendynasty.football.scouting.data.SaveScoutRegionKnowledgeEntity
import com.greendynasty.football.scouting.model.HireScoutResult
import com.greendynasty.football.scouting.model.ScoutRegionCode
import com.greendynasty.football.scouting.model.ScoutStatus
import com.greendynasty.football.scouting.model.ScoutWithKnowledge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * T14 球探地区知识管理器（V0.2 08 §三.1 + §三.2）。
 *
 * 职责：
 * 1. 球探雇佣（从 history.scout 复制雇佣记录到 save.scout_hired）
 * 2. 球探解雇（标记 RELEASED + 取消进行中任务 + 删除地区知识）
 * 3. 地区知识初始化（雇佣时为 15 地区生成初始知识值）
 * 4. 地区知识查询（单地区 / 全部 / 球探汇总）
 *
 * 三库分离：history.scout 只读，save.scout_hired + save.scout_region_knowledge 可写。
 *
 * V1 不做：球探地区知识随任务执行自动提升（仅按年资/事件缓慢提升，本类不实现）。
 *
 * @param databaseManager 三库管理入口
 * @param config 球探配置
 */
class ScoutRegionKnowledgeManager(
    private val databaseManager: DatabaseManager,
    private val config: ScoutConfig = ScoutConfig.DEFAULT
) {

    /**
     * 雇佣球探（V0.2 08 §三.1）。
     *
     * 流程：
     * 1. 校验俱乐部球探数 ≤ [ScoutConfig.maxScoutsPerClub]
     * 2. 校验球探未被该俱乐部雇佣
     * 3. 创建 save.scout_hired 记录（合同 = 当前日期 + [ScoutConfig.defaultContractYears] 年）
     * 4. 初始化 15 地区知识（基础值 [ScoutConfig.baseRegionKnowledge]，V1 不按国籍差异化）
     *
     * @param saveId 存档 ID
     * @param clubId 俱乐部 ID
     * @param scoutId 球探 ID（history.scout.scout_id）
     * @param currentDate 当前游戏内日期
     * @return 雇佣结果（含 hiredId）
     */
    suspend fun hireScout(
        saveId: Int,
        clubId: Int,
        scoutId: Int,
        currentDate: LocalDate
    ): HireScoutResult = withContext(Dispatchers.IO) {
        val hiredDao = databaseManager.saveScoutHiredDao()
        val knowledgeDao = databaseManager.saveScoutRegionKnowledgeDao()

        // 1. 校验俱乐部球探数上限
        val currentCount = hiredDao.countByClub(saveId, clubId)
        if (currentCount >= config.maxScoutsPerClub) {
            return@withContext HireScoutResult(
                success = false,
                message = "球探数量已达上限 ${config.maxScoutsPerClub}"
            )
        }

        // 2. 校验球探未被雇佣
        val existing = hiredDao.getByScout(saveId, scoutId, clubId)
        if (existing != null) {
            return@withContext HireScoutResult(
                success = false,
                message = "该球探已被雇佣"
            )
        }

        // 3. 读取 history.scout 模板
        val scout = databaseManager.historyScoutDao().getScout(scoutId)
            ?: return@withContext HireScoutResult(
                success = false,
                message = "球探模板不存在"
            )

        // 4. 创建雇佣记录
        val expireDate = currentDate.plusYears(config.defaultContractYears.toLong()).toString()
        val hired = SaveScoutHiredEntity(
            saveId = saveId,
            clubId = clubId,
            scoutId = scoutId,
            status = ScoutStatus.IDLE.code,
            hiredDate = currentDate.toString(),
            contractExpireDate = expireDate,
            wage = scout.salary,
            morale = config.defaultScoutMorale
        )
        val hiredId = hiredDao.insert(hired)

        // 5. 初始化 15 地区知识（V1 全部使用基础值，不按球探国籍差异化）
        val knowledgeList = ScoutRegionCode.values().map { region ->
            SaveScoutRegionKnowledgeEntity(
                saveId = saveId,
                hiredId = hiredId.toInt(),
                scoutId = scoutId,
                regionCode = region.code,
                knowledgeValue = config.baseRegionKnowledge
            )
        }
        knowledgeDao.insertAll(knowledgeList)

        HireScoutResult(
            success = true,
            message = "成功雇佣 ${scout.name}",
            hiredId = hiredId
        )
    }

    /**
     * 解雇球探（V0.2 08 §三.1）。
     *
     * 流程：
     * 1. 取消该球探所有进行中任务（状态 → CANCELLED）
     * 2. 删除该球探的地区知识记录
     * 3. 标记球探状态为 RELEASED
     *
     * @param saveId 存档 ID
     * @param hiredId 雇佣记录 ID
     */
    suspend fun releaseScout(saveId: Int, hiredId: Int) = withContext(Dispatchers.IO) {
        val hiredDao = databaseManager.saveScoutHiredDao()
        val knowledgeDao = databaseManager.saveScoutRegionKnowledgeDao()
        val taskDao = databaseManager.saveScoutTaskDao()

        // 1. 取消进行中任务
        taskDao.cancelTasksByHired(saveId, hiredId)
        // 2. 删除地区知识
        knowledgeDao.deleteByHired(saveId, hiredId)
        // 3. 标记 RELEASED
        hiredDao.updateStatus(saveId, hiredId, ScoutStatus.RELEASED.code)
    }

    /**
     * 获取球探对指定地区的知识值（无记录返回 [ScoutConfig.baseRegionKnowledge]）。
     */
    suspend fun getRegionKnowledge(saveId: Int, hiredId: Int, regionCode: String): Int =
        withContext(Dispatchers.IO) {
            databaseManager.saveScoutRegionKnowledgeDao()
                .get(saveId, hiredId, regionCode)?.knowledgeValue
                ?: config.baseRegionKnowledge
        }

    /**
     * 列出某俱乐部所有可用球探（含地区知识汇总，V0.2 08 §三.1）。
     */
    suspend fun listScouts(saveId: Int, clubId: Int): List<ScoutWithKnowledge> =
        withContext(Dispatchers.IO) {
            val hiredDao = databaseManager.saveScoutHiredDao()
            val knowledgeDao = databaseManager.saveScoutRegionKnowledgeDao()
            val historyScoutDao = databaseManager.historyScoutDao()

            val hiredList = hiredDao.getByClub(saveId, clubId)
            hiredList.mapNotNull { hired ->
                val scout = historyScoutDao.getScout(hired.scoutId) ?: return@mapNotNull null
                val knowledge = knowledgeDao.getByHired(saveId, hired.hiredId)
                ScoutWithKnowledge(scout = scout, hired = hired, regionKnowledge = knowledge)
            }
        }

    /**
     * 更新球探状态（任务派遣/完成时调用）。
     */
    suspend fun updateScoutStatus(saveId: Int, hiredId: Int, status: ScoutStatus) =
        withContext(Dispatchers.IO) {
            databaseManager.saveScoutHiredDao().updateStatus(saveId, hiredId, status.code)
        }

    /**
     * 续约球探（更新工资 + 合同到期日）。
     */
    suspend fun renewContract(
        saveId: Int,
        hiredId: Int,
        newWage: Int,
        newExpireDate: LocalDate
    ) = withContext(Dispatchers.IO) {
        databaseManager.saveScoutHiredDao()
            .renewContract(saveId, hiredId, newWage, newExpireDate.toString())
    }
}
