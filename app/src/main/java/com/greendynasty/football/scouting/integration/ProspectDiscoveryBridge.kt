package com.greendynasty.football.scouting.integration

import com.greendynasty.football.scouting.model.CandidatePlayer
import java.time.LocalDate

/**
 * T14 历史新星发现桥接接口（V0.2 08 §六 + T15 历史新星池衔接）。
 *
 * 职责：在球探发现流程中接入 T15 历史新星池（年轻时的梅西/C罗等），
 * 让球探可在特定年份发现历史新星，并通知 T15 更新星状态。
 *
 * T15 实现前使用 [NoOpProspectDiscoveryBridge] 占位（返回空列表），
 * T15 完成后注入真实实现即可，无需改动球探代码。
 *
 * 集成点：
 * - [PlayerDiscoveryEngine.getCandidates] 调用 [getDiscoverableProspectsInRegion] 拉取候选
 * - 发现后调用 [onProspectDiscovered] 通知 T15 标记 DISCOVERED
 * - 时序保证：T15 每日推进先于球探推进，星状态已 ACTIVE
 */
interface ProspectDiscoveryBridge {

    /**
     * 获取指定地区内可发现的历史新星（V0.2 08 §六）。
     *
     * @param saveId 存档 ID
     * @param regionCode 地区代码
     * @param currentDate 当前游戏内日期（仅返回 discoverable_from ≤ 当前日期的新星）
     * @return 可发现的历史新星候选列表（已转为 CandidatePlayer）
     */
    suspend fun getDiscoverableProspectsInRegion(
        saveId: Int,
        regionCode: String,
        currentDate: LocalDate
    ): List<CandidatePlayer>

    /**
     * 球探发现历史新星后通知 T15 更新星状态（V0.2 08 §六）。
     *
     * @param saveId 存档 ID
     * @param playerId 球员 ID
     * @param scoutId 球探 ID
     * @param hiredId 雇佣记录 ID
     * @param currentDate 当前游戏内日期
     */
    suspend fun onProspectDiscovered(
        saveId: Int,
        playerId: Int,
        scoutId: Int,
        hiredId: Int,
        currentDate: LocalDate
    )
}

/**
 * 默认空实现（T15 未完成时使用）。
 *
 * 行为：所有方法返回空结果/no-op，不影响球探主流程。
 * T15 完成后替换为真实实现即可。
 */
class NoOpProspectDiscoveryBridge : ProspectDiscoveryBridge {
    override suspend fun getDiscoverableProspectsInRegion(
        saveId: Int,
        regionCode: String,
        currentDate: LocalDate
    ): List<CandidatePlayer> = emptyList()

    override suspend fun onProspectDiscovered(
        saveId: Int,
        playerId: Int,
        scoutId: Int,
        hiredId: Int,
        currentDate: LocalDate
    ) {
        // no-op：T15 未实现，仅占位
    }
}
